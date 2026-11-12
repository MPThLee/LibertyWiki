// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.adapter.api.compat.action

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import wiki.liberty.adapter.api.InvalidCredentials
import wiki.liberty.adapter.api.PageHttp
import wiki.liberty.adapter.api.requestId
import wiki.liberty.application.CreatePage
import wiki.liberty.application.EditPage
import wiki.liberty.application.PageActor
import wiki.liberty.application.PageApplicationService
import wiki.liberty.application.RevisionBase
import wiki.liberty.domain.Page
import wiki.liberty.domain.PageError
import wiki.liberty.domain.PageFailure
import wiki.liberty.domain.PageLookup

class ActionFailure(val code: String, message: String) : RuntimeException(message)
data class ActionErrorEnvelope(val error: ActionError)
data class ActionError(val code: String, val info: String)

@RestController
class MediaWikiAction(private val pages: PageApplicationService, private val http: PageHttp) {
    @RequestMapping("/w/api.php", method = [RequestMethod.GET, RequestMethod.POST], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun action(request: HttpServletRequest): Map<String, Any> {
        val actor = http.actor(request)
        pages.requireRead(actor)
        val params = ActionParams(request.parameterMap)
        if (params.get("format") !in listOf(null, "json") || params.get("formatversion") !in listOf(null, "2")) {
            unsupported("Only format=json and formatversion=2 are supported.")
        }
        return when (params.get("action")) {
            "query" -> query(actor, params)
            "edit" -> edit(request, actor, params)
            else -> throw ActionFailure("badaction", "The requested action is unsupported.")
        }
    }

    private fun edit(request: HttpServletRequest, actor: PageActor, params: ActionParams): Map<String, Any> {
        if (request.method != "POST") throw ActionFailure("mustbeposted", "The edit action requires POST.")
        if (request.contentType?.substringBefore(';') != MediaType.APPLICATION_FORM_URLENCODED_VALUE ||
            request.queryString != null
        ) unsupported("Send edit parameters in an application/x-www-form-urlencoded POST body.")
        val create = params.get("createonly") != null
        if (create) {
            params.only("title", "text", "token", "summary", "createonly")
        } else {
            params.only("title", "pageid", "text", "token", "summary", "baserevid", "nocreate")
        }
        if (params.required("token") != "+\\" || request.getHeader("Sec-Fetch-Site") == "cross-site") {
            throw ActionFailure("badtoken", "Invalid CSRF token.")
        }
        val text = params.required("text")
        val summary = params.get("summary").orEmpty()
        val page = if (create) {
            pages.create(actor, request.requestId(), CreatePage(params.required("title"), 0, text, summary))
        } else {
            val lookup = lookup(params.get("title"), params.get("pageid"))
            val base = RevisionBase.Compat(positiveInteger(params.required("baserevid"), "baserevid"))
            pages.edit(actor, request.requestId(), EditPage(lookup, base, text, summary))
        }
        return mapOf("edit" to buildMap {
            if (create) put("new", true)
            putAll(mapOf(
                "result" to "Success", "pageid" to page.compatId, "title" to page.title.value,
                "contentmodel" to "wikitext", "oldrevid" to (page.compatParentRevisionId ?: 0L),
                "newrevid" to page.compatRevisionId, "newtimestamp" to page.createdAt,
            ))
        })
    }

    private fun query(actor: PageActor, params: ActionParams): Map<String, Any> {
        if (params.get("meta") == "tokens") {
            params.only("meta", "type")
            if (params.get("type") !in listOf(null, "csrf")) unsupported("Only CSRF tokens are supported.")
            return envelope(mapOf("tokens" to mapOf("csrftoken" to "+\\")))
        }
        params.only("prop", "titles", "pageids", "rvprop", "rvslots", "rvlimit")
        val props = params.required("prop").split('|').toSet()
        if (!setOf("info", "revisions").containsAll(props)) unsupported("Only info and revisions are supported.")
        val fields = (params.get("rvprop") ?: "ids|timestamp").split('|').toSet()
        if (!setOf("ids", "timestamp", "content", "contentmodel").containsAll(fields) ||
            params.get("rvslots") !in listOf(null, "main") || params.get("rvlimit") !in listOf(null, "1") ||
            (("content" in fields || "contentmodel" in fields) && params.get("rvslots") != "main")
        ) unsupported("Only the current revision and explicit main slot are supported.")

        val title = params.get("titles")
        val lookup = lookup(title, params.get("pageids"))
        val result = try {
            pages.read(actor, lookup)
        } catch (failure: PageFailure) {
            if (failure.error != PageError.INVALID_TITLE) throw failure
            return envelope(mapOf("pages" to listOf(mapOf(
                "title" to title.orEmpty(), "invalid" to true, "invalidreason" to "The title is invalid.",
            ))))
        }
        val normalized = result.normalizedTitle?.value
        val query = mutableMapOf<String, Any>()
        if (title != null && normalized != null && normalized != title) {
            query["normalized"] = listOf(mapOf("fromencoded" to false, "from" to title, "to" to normalized))
        }
        val entry = result.page?.let { page ->
            mutableMapOf<String, Any>("pageid" to page.compatId, "ns" to 0, "title" to page.title.value).apply {
                if ("info" in props) {
                    putAll(info())
                    putAll(mapOf("touched" to page.createdAt, "lastrevid" to page.compatRevisionId,
                        "length" to page.source.toByteArray().size))
                    if (page.compatParentRevisionId == null) put("new", true)
                }
                if ("revisions" in props) put("revisions", listOf(revision(page, fields)))
            }
        } ?: if (lookup is PageLookup.CompatId) {
            mapOf("pageid" to lookup.value, "missing" to true)
        } else {
            mutableMapOf<String, Any>("ns" to 0, "title" to checkNotNull(normalized), "missing" to true).apply {
                if ("info" in props) putAll(info())
            }
        }
        query["pages"] = listOf(entry)
        return envelope(query)
    }

    private fun revision(page: Page, fields: Set<String>): Map<String, Any> = buildMap {
        if ("ids" in fields) putAll(mapOf("revid" to page.compatRevisionId, "parentid" to (page.compatParentRevisionId ?: 0L)))
        if ("timestamp" in fields) put("timestamp", page.createdAt)
        val slot = buildMap {
            if ("contentmodel" in fields) put("contentmodel", "wikitext")
            if ("content" in fields) {
                put("contentformat", "text/x-wiki")
                put("content", page.source)
            }
        }
        if (slot.isNotEmpty()) put("slots", mapOf("main" to slot))
    }

    private fun info() = mapOf("contentmodel" to "wikitext", "pagelanguage" to "en",
        "pagelanguagehtmlcode" to "en", "pagelanguagedir" to "ltr")

    private fun envelope(query: Map<String, Any>) = mapOf("batchcomplete" to true, "query" to query)

    private fun lookup(title: String?, id: String?): PageLookup {
        if ((title == null) == (id == null) || title?.contains('|') == true) {
            throw ActionFailure("invalidparammix", "Specify a single title or page ID.")
        }
        return if (title != null) PageLookup.Title(title) else PageLookup.CompatId(positiveInteger(checkNotNull(id), "pageid"))
    }

    private fun positiveInteger(value: String, name: String): Long =
        value.toLongOrNull()?.takeIf { it > 0 } ?: throw ActionFailure("badinteger", "Invalid $name.")
}

private class ActionParams(private val parameters: Map<String, Array<String>>) {
    fun get(name: String) = parameters[name]?.singleOrNull()
    fun required(name: String) = get(name) ?: throw ActionFailure("missingparam", "The $name parameter must be set.")
    fun only(vararg names: String) {
        val allowed = names.toSet() + setOf("action", "format", "formatversion")
        if (parameters.any { (key, values) -> key !in allowed || values.size != 1 }) {
            unsupported("A parameter is unsupported or repeated.")
        }
    }
}

private fun unsupported(message: String): Nothing = throw ActionFailure("unsupported", message)

@RestControllerAdvice(assignableTypes = [MediaWikiAction::class])
class MediaWikiActionErrors {
    @ExceptionHandler(ActionFailure::class)
    fun transport(failure: ActionFailure) = error(failure.code, failure.message.orEmpty())

    @ExceptionHandler(PageFailure::class)
    fun domain(failure: PageFailure, request: HttpServletRequest) = when (failure.error) {
        PageError.EXISTS -> error("articleexists", "The page you tried to create has been created already.")
        PageError.PAGE_NOT_FOUND -> if (request.getParameter("pageid") == null) {
            error("missingtitle", "The page you specified doesn't exist.")
        } else {
            error("nosuchpageid", "There is no page with the specified ID.")
        }
        PageError.EDIT_CONFLICT -> error("editconflict", "Edit conflict.")
        PageError.FORBIDDEN -> if (request.getParameter("action") == "edit") {
            error("permissiondenied", "The page operation is forbidden.")
        } else {
            error("readapidenied", "Read permission is required.")
        }
        PageError.INVALID_TITLE -> error("invalidtitle", "The title is invalid.")
        else -> error("invalidparam", "The request is invalid.")
    }

    @ExceptionHandler(InvalidCredentials::class)
    fun credentials() = error("permissiondenied", "Invalid Bearer credential.")

    private fun error(code: String, info: String) = ActionErrorEnvelope(ActionError(code, info))
}
