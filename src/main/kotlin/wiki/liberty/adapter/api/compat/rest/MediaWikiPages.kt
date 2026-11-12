// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.adapter.api.compat.rest

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.annotation.*
import org.springframework.web.util.UriUtils
import wiki.liberty.adapter.api.InvalidCredentials
import wiki.liberty.adapter.api.PageHttp
import wiki.liberty.adapter.api.PageHttpSettings
import wiki.liberty.adapter.api.requestId
import wiki.liberty.application.CreatePage
import wiki.liberty.application.EditPage
import wiki.liberty.application.HistoryDirection
import wiki.liberty.application.HistoryRequest
import wiki.liberty.application.PageActor
import wiki.liberty.application.PageApplicationService
import wiki.liberty.application.RevisionBase
import wiki.liberty.domain.Page
import wiki.liberty.domain.PageError
import wiki.liberty.domain.PageFailure
import wiki.liberty.domain.PageLookup
import wiki.liberty.domain.PageTitle
import wiki.liberty.domain.RevisionLookup
import wiki.liberty.domain.RevisionMetadata
import java.net.URI
import java.time.Instant

data class RestCreatePage(
    val title: String,
    val source: String,
    val comment: String,
    @JsonProperty("content_model") val contentModel: String = "wikitext",
    val token: String? = null,
)

data class RestLatest(val id: Long, val timestamp: Instant)
data class RestEditBase(val id: Long)
data class RestEditPage(
    val source: String,
    val comment: String,
    val latest: RestEditBase,
    @JsonProperty("content_model") val contentModel: String = "wikitext",
    val token: String? = null,
)

data class RestLicense(val url: String, val title: String)
data class RestSourcePage(
    val id: Long,
    val key: String,
    val title: String,
    val latest: RestLatest,
    @get:JsonProperty("content_model") val contentModel: String,
    val license: RestLicense,
    val source: String,
)

data class RestRevisionUser(val id: Long?, val name: String)
data class RestRevisionPage(val id: Long, val key: String, val title: String)
data class RestHistoryEntry(
    val id: Long,
    val timestamp: Instant,
    val minor: Boolean,
    val size: Int,
    val comment: String,
    val user: RestRevisionUser,
    val delta: Int?,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RestHistory(val revisions: List<RestHistoryEntry>, val latest: String, val older: String?, val newer: String?)
data class RestRevisionSource(
    val id: Long,
    val size: Int,
    val minor: Boolean,
    val timestamp: Instant,
    @get:JsonProperty("content_model") val contentModel: String,
    val page: RestRevisionPage,
    val license: RestLicense,
    val user: RestRevisionUser,
    val comment: String,
    val delta: Int?,
    val source: String,
)

data class RestError(
    val errorKey: String,
    val messageTranslations: Map<String, String>,
    val httpCode: Int,
    val httpReason: String,
)

data class RestParameterError(
    val name: String,
    val value: String,
    val messageTranslations: Map<String, String>,
    val error: String = "parameter-validation-failed",
    val failureCode: String = "badinteger",
    val failureData: Nothing? = null,
    val errorKey: String = failureCode,
    val httpCode: Int = 400,
    val httpReason: String = "Bad Request",
)

class RestFailure(val status: HttpStatus, val key: String, message: String) : RuntimeException(message)
class InvalidRestInteger(val parameter: String, val value: String) : RuntimeException()

@RestController
@RequestMapping("/w/rest.php/v1", produces = [MediaType.APPLICATION_JSON_VALUE])
class MediaWikiPages(
    private val pages: PageApplicationService,
    private val http: PageHttp,
    private val settings: PageHttpSettings,
) {
    @PostMapping("/page")
    fun create(@RequestBody body: RestCreatePage, request: HttpServletRequest): ResponseEntity<RestSourcePage> {
        val actor = authorizeWrite(request, body.token, body.contentModel)
        val page = pages.create(actor, request.requestId(), CreatePage(body.title, 0, body.source, body.comment))
        return ResponseEntity.created(location(page)).body(page.rest())
    }

    @PutMapping("/page/{title}")
    fun edit(
        @PathVariable title: String, @RequestBody body: RestEditPage, request: HttpServletRequest,
    ): RestSourcePage {
        val actor = authorizeWrite(request, body.token, body.contentModel)
        if (';' in request.requestURI) throw PageFailure(PageError.INVALID_TITLE)
        if (body.latest.id <= 0) {
            throw RestFailure(HttpStatus.BAD_REQUEST, "rest-body-validation-error", "latest.id must be positive.")
        }
        val command = EditPage(PageLookup.Title(title), RevisionBase.Compat(body.latest.id), body.source, body.comment)
        return pages.edit(actor, request.requestId(), command).rest()
    }

    private fun authorizeWrite(request: HttpServletRequest, token: String?, contentModel: String): PageActor {
        if (request.contentType?.substringBefore(';')?.equals(MediaType.APPLICATION_JSON_VALUE, ignoreCase = true) != true) {
            throw HttpMediaTypeNotSupportedException("Expected application/json.")
        }
        val actor = http.actor(request)
        pages.requireRead(actor)
        if (actor == PageActor.ANONYMOUS && (token != "+\\" || request.getHeader("Sec-Fetch-Site") == "cross-site")) {
            throw RestFailure(HttpStatus.FORBIDDEN, "rest-badtoken", "A valid token is required for anonymous writes.")
        }
        if (contentModel != "wikitext") {
            throw RestFailure(HttpStatus.BAD_REQUEST, "rest-bad-content-model", "Only Wikitext is supported.")
        }
        return actor
    }

    @GetMapping("/page/{title}")
    fun read(@PathVariable title: String, request: HttpServletRequest): ResponseEntity<*> {
        val actor = http.actor(request)
        pages.requireRead(actor)
        val page = try {
            if (';' in request.requestURI) throw PageFailure(PageError.INVALID_TITLE)
            pages.read(actor, PageLookup.Title(title)).page
        } catch (failure: PageFailure) {
            if (failure.error != PageError.INVALID_TITLE) throw failure
            throw RestFailure(HttpStatus.NOT_FOUND, "rest-invalid-title", "The specified title ($title) does not refer to a wiki page.")
        } ?: throw RestFailure(HttpStatus.NOT_FOUND, "rest-nonexistent-title", "The specified page ($title) does not exist")
        if (title != page.title.key) return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY).location(location(page)).build<Void>()
        return ResponseEntity.ok(page.rest())
    }

    @GetMapping("/page/{title}/history")
    fun history(
        @PathVariable title: String,
        @RequestParam("older_than", required = false) older: String?,
        @RequestParam("newer_than", required = false) newer: String?,
        @RequestParam(required = false) filter: String?,
        request: HttpServletRequest,
    ): ResponseEntity<*> {
        val actor = http.actor(request)
        pages.requireRead(actor)
        val olderAnchor = older?.let { revisionId("older_than", it) }
        val newerAnchor = newer?.let { revisionId("newer_than", it) }
        if (olderAnchor != null && newerAnchor != null) throw RestFailure(
            HttpStatus.BAD_REQUEST, "rest-pagehistory-incompatible-params",
            "Parameters \"older_than\" and \"newer_than\" cannot both be specified",
        )
        if (filter != null) throw RestFailure(
            HttpStatus.BAD_REQUEST, "rest-unsupported-parameter", "History filters are unsupported.",
        )
        if (';' in request.requestURI) throw invalidTitle(title)
        val anchor = olderAnchor ?: newerAnchor
        if (anchor != null && anchor.value <= 0) throw RestFailure(
            HttpStatus.BAD_REQUEST, "rest-pagehistory-param-range-error", "Revision id must be greater than 0",
        )
        val direction = if (newerAnchor == null) HistoryDirection.OLDER else HistoryDirection.NEWER
        val lookup = PageLookup.Title(title)
        val history = try {
            if (PageTitle.parse(title)?.key != title) {
                val canonical = pages.revisionPage(actor, lookup).title.key
                val path = "/w/rest.php/v1/page/" + UriUtils.encode(canonical, Charsets.UTF_8) + "/history"
                val query = when {
                    olderAnchor != null -> "?older_than=${olderAnchor.value}"
                    newerAnchor != null -> "?newer_than=${newerAnchor.value}"
                    else -> ""
                }
                return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY).location(URI.create(path + query)).build<Void>()
            }
            pages.history(actor, HistoryRequest(lookup, 20, anchor, direction))
        } catch (failure: PageFailure) {
            when (failure.error) {
                PageError.INVALID_TITLE -> throw invalidTitle(title)
                PageError.PAGE_NOT_FOUND -> throw RestFailure(
                    HttpStatus.NOT_FOUND, "rest-nonexistent-title", "The specified page ($title) does not exist",
                )
                PageError.INVALID_CURSOR -> throw RestFailure(
                    HttpStatus.NOT_FOUND, "rest-nonexistent-title-revision",
                    "The specified revision (${anchor?.value}) does not exist for the specified page ($title)",
                )
                else -> throw failure
            }
        }
        val latest = request.requestURL.toString()
        val revisions = history.revisions
        return ResponseEntity.ok(RestHistory(
            revisions.map { RestHistoryEntry(it.compatId, it.createdAt, false, it.size, it.summary, it.user(), it.delta) },
            latest,
            revisions.lastOrNull()?.takeIf { history.hasOlder }?.let { "$latest?older_than=${it.compatId}" },
            revisions.firstOrNull()?.takeIf { history.hasNewer }?.let { "$latest?newer_than=${it.compatId}" },
        ))
    }

    @GetMapping("/revision/{id}")
    fun revision(@PathVariable id: String, request: HttpServletRequest): RestRevisionSource {
        val actor = http.actor(request)
        pages.requireRead(actor)
        val lookup = revisionId("id", id)
        val source = try {
            pages.readRevision(actor, lookup)
        } catch (failure: PageFailure) {
            if (failure.error != PageError.REVISION_NOT_FOUND) throw failure
            throw RestFailure(
                HttpStatus.NOT_FOUND, "rest-nonexistent-revision", "The specified revision (${lookup.value}) does not exist",
            )
        }
        val revision = source.revision
        val page = source.page
        return RestRevisionSource(
            revision.compatId, revision.size, false, revision.createdAt, "wikitext",
            RestRevisionPage(page.compatId, page.title.key, page.title.value),
            RestLicense(settings.licenseUrl, settings.licenseTitle), revision.user(), revision.summary,
            revision.delta, source.source,
        )
    }

    private fun RevisionMetadata.user() = RestRevisionUser(if (actor == "anonymous") null else 1L, actor)

    private fun revisionId(parameter: String, value: String): RevisionLookup.CompatId =
        value.toLongOrNull()?.let { RevisionLookup.CompatId(it) } ?: throw InvalidRestInteger(parameter, value)

    private fun invalidTitle(title: String) =
        RestFailure(HttpStatus.NOT_FOUND, "rest-invalid-title", "The specified title ($title) does not refer to a wiki page.")

    private fun Page.rest() = RestSourcePage(
        compatId, title.key, title.value, RestLatest(compatRevisionId, createdAt),
        "wikitext", RestLicense(settings.licenseUrl, settings.licenseTitle), source,
    )

    private fun location(page: Page) = URI.create("/w/rest.php/v1/page/" + UriUtils.encode(page.title.key, Charsets.UTF_8))
}

@RestControllerAdvice(assignableTypes = [MediaWikiPages::class])
class MediaWikiRestErrors {
    @ExceptionHandler(RestFailure::class)
    fun transport(failure: RestFailure) = error(failure.status, failure.key, failure.message.orEmpty())

    @ExceptionHandler(InvalidRestInteger::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun invalidInteger(failure: InvalidRestInteger) = RestParameterError(
        failure.parameter, failure.value,
        mapOf("en" to "Invalid value \"${failure.value}\" for integer parameter \"${failure.parameter}\"."),
    )

    @ExceptionHandler(PageFailure::class)
    fun domain(failure: PageFailure): ResponseEntity<RestError> = when (failure.error) {
        PageError.EXISTS -> error(HttpStatus.CONFLICT, "apierror-articleexists", "The page you tried to create has been created already.")
        PageError.PAGE_NOT_FOUND -> error(HttpStatus.NOT_FOUND, "apierror-missingtitle", "The page you specified doesn't exist.")
        PageError.EDIT_CONFLICT -> error(HttpStatus.CONFLICT, "editconflict", "Edit conflict.")
        PageError.FORBIDDEN -> error(HttpStatus.FORBIDDEN, "rest-permission-denied", "The page operation is forbidden.")
        PageError.INVALID_TITLE -> error(HttpStatus.BAD_REQUEST, "apierror-invalidtitle", "The title is invalid.")
        else -> error(HttpStatus.BAD_REQUEST, "rest-body-validation-error", "The request body is invalid.")
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun invalidBody() = error(HttpStatus.BAD_REQUEST, "rest-body-validation-error", "The request body is invalid.")

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun mediaType() = error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "rest-unsupported-content-type", "Expected application/json.")

    @ExceptionHandler(InvalidCredentials::class)
    fun credentials() = error(HttpStatus.UNAUTHORIZED, "rest-invalid-credentials", "Invalid Bearer credential.")

    private fun error(status: HttpStatus, key: String, message: String) =
        ResponseEntity.status(status).body(RestError(key, mapOf("en" to message), status.value(), status.reasonPhrase))
}
