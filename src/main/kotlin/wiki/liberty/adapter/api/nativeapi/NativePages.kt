// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.adapter.api.nativeapi

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.annotation.*
import wiki.liberty.adapter.api.InvalidCredentials
import wiki.liberty.adapter.api.PageHttp
import wiki.liberty.adapter.api.requestId
import wiki.liberty.application.CreatePage
import wiki.liberty.application.PageActor
import wiki.liberty.application.PageApplicationService
import wiki.liberty.domain.Page
import wiki.liberty.domain.PageError
import wiki.liberty.domain.PageFailure
import wiki.liberty.domain.PageLookup
import java.net.URI
import java.time.Instant
import java.util.UUID

data class NativeCreatePage(val title: String, val namespaceId: Int, val source: String, val summary: String = "")

data class NativeSourcePage(
    val id: UUID,
    val namespaceId: Int,
    val normalizedTitle: String,
    val currentRevisionId: UUID,
    val status: String,
    val source: String,
    val contentModel: String,
    val createdAt: Instant,
)

data class NativeError(val code: String, val message: String, val requestId: UUID)
class NativePageMissing : RuntimeException()
class InvalidNativeId : RuntimeException()

@RestController
@RequestMapping("/api/v1/pages", produces = [MediaType.APPLICATION_JSON_VALUE])
class NativePages(private val pages: PageApplicationService, private val http: PageHttp) {
    @PostMapping
    fun create(@RequestBody body: NativeCreatePage, request: HttpServletRequest): ResponseEntity<NativeSourcePage> {
        if (request.contentType?.substringBefore(';')?.equals(MediaType.APPLICATION_JSON_VALUE, ignoreCase = true) != true) {
            throw HttpMediaTypeNotSupportedException("Expected application/json.")
        }
        val page = pages.create(
            http.actor(request), request.requestId(), CreatePage(body.title, body.namespaceId, body.source, body.summary),
        )
        return ResponseEntity.created(URI.create("/api/v1/pages/${page.id}")).body(page.native())
    }

    @GetMapping("/by-title/{title}")
    fun byTitle(@PathVariable title: String, request: HttpServletRequest): NativeSourcePage {
        val actor = http.actor(request)
        pages.requireRead(actor)
        if (';' in request.requestURI) throw PageFailure(PageError.INVALID_TITLE)
        return read(PageLookup.Title(title), actor)
    }

    @GetMapping("/{pageId}")
    fun byId(@PathVariable pageId: String, request: HttpServletRequest): NativeSourcePage {
        val actor = http.actor(request)
        pages.requireRead(actor)
        val id = try {
            UUID.fromString(pageId).also { if (!it.toString().equals(pageId, ignoreCase = true)) throw InvalidNativeId() }
        } catch (_: IllegalArgumentException) {
            throw InvalidNativeId()
        }
        return read(PageLookup.Id(id), actor)
    }

    private fun read(lookup: PageLookup, actor: PageActor): NativeSourcePage =
        pages.read(actor, lookup).page?.native() ?: throw NativePageMissing()

    private fun Page.native() = NativeSourcePage(id, 0, title.value, revisionId, "ACTIVE", source, "wikitext", createdAt)
}

@RestControllerAdvice(assignableTypes = [NativePages::class])
class NativePageErrors {
    @ExceptionHandler(PageFailure::class)
    fun domain(failure: PageFailure, request: HttpServletRequest): ResponseEntity<NativeError> {
        val status = when (failure.error) {
            PageError.FORBIDDEN -> HttpStatus.FORBIDDEN
            PageError.EXISTS -> HttpStatus.CONFLICT
            else -> HttpStatus.UNPROCESSABLE_ENTITY
        }
        val message = when (failure.error) {
            PageError.FORBIDDEN -> "The page operation is forbidden."
            PageError.EXISTS -> "A page with this title already exists."
            PageError.INVALID_TITLE -> "The title is invalid or outside the supported namespace."
            PageError.INVALID_NAMESPACE -> "Only the main namespace is supported."
            PageError.INVALID_SOURCE -> "Source must be valid Unicode and at most 1 MiB in UTF-8."
            PageError.INVALID_SUMMARY -> "Summary must be valid Unicode and at most 500 characters."
        }
        return error(status, failure.error.name, message, request)
    }

    @ExceptionHandler(NativePageMissing::class)
    fun missing(request: HttpServletRequest) = error(HttpStatus.NOT_FOUND, "PAGE_NOT_FOUND", "Page not found.", request)

    @ExceptionHandler(InvalidNativeId::class)
    fun invalidId(request: HttpServletRequest) =
        error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PAGE_ID", "Expected a page UUID.", request)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun invalidBody(request: HttpServletRequest) =
        error(HttpStatus.BAD_REQUEST, "INVALID_BODY", "Expected a valid JSON page creation request.", request)

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun invalidMediaType(request: HttpServletRequest) =
        error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_MEDIA_TYPE", "Expected application/json.", request)

    @ExceptionHandler(InvalidCredentials::class)
    fun credentials(request: HttpServletRequest): ResponseEntity<NativeError> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).header("WWW-Authenticate", "Bearer")
            .body(NativeError("INVALID_CREDENTIALS", "Invalid Bearer credential.", request.requestId()))

    private fun error(status: HttpStatus, code: String, message: String, request: HttpServletRequest) =
        ResponseEntity.status(status).body(NativeError(code, message, request.requestId()))
}
