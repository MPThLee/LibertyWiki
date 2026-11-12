// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.application

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import wiki.liberty.domain.Page
import wiki.liberty.domain.PageError
import wiki.liberty.domain.PageFailure
import wiki.liberty.domain.PageLookup
import wiki.liberty.domain.PageTitle
import wiki.liberty.domain.RevisionLookup
import wiki.liberty.domain.RevisionMetadata
import wiki.liberty.domain.RevisionPage
import wiki.liberty.domain.RevisionSource
import wiki.liberty.domain.validUnicode
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.HexFormat
import java.util.UUID

enum class PageActor { ANONYMOUS, OPERATOR }

@ConfigurationProperties("wiki.pages")
data class PagePolicy(
    val anonymousRead: Boolean = true,
    val anonymousEdit: Boolean = false,
    val anonymousCreate: Boolean = false,
)

data class CreatePage(val title: String, val namespaceId: Int, val source: String, val summary: String)
data class EditPage(val lookup: PageLookup, val base: RevisionBase, val source: String, val summary: String)
sealed interface RevisionBase {
    data class Native(val id: UUID) : RevisionBase
    data class Compat(val id: Long) : RevisionBase
}

data class PageRead(val page: Page?, val normalizedTitle: PageTitle? = null)
data class HistoryRequest(
    val page: PageLookup,
    val limit: Int,
    val anchor: RevisionLookup? = null,
    val direction: HistoryDirection = HistoryDirection.OLDER,
    val inclusive: Boolean = false,
    val anchorTime: Instant? = null,
    val includeCurrent: Boolean = false,
)

data class RevisionHistory(
    val page: RevisionPage,
    val revisions: List<RevisionMetadata>,
    val next: RevisionMetadata?,
    val hasNewer: Boolean,
    val hasOlder: Boolean,
    val current: RevisionMetadata?,
)

@Service
class PageApplicationService(private val repository: PageRepository, private val policy: PagePolicy) {
    fun read(actor: PageActor, lookup: PageLookup): PageRead {
        requireRead(actor)
        return find(lookup)
    }

    fun readRevision(actor: PageActor, lookup: RevisionLookup): RevisionSource {
        requireRead(actor)
        return repository.findRevision(lookup) ?: throw PageFailure(PageError.REVISION_NOT_FOUND)
    }

    fun revisionPage(actor: PageActor, lookup: PageLookup): RevisionPage {
        requireRead(actor)
        val normalized = if (lookup is PageLookup.Title) PageLookup.Title(parseTitle(lookup.input).value) else lookup
        return repository.findRevisionPage(normalized) ?: throw PageFailure(PageError.PAGE_NOT_FOUND)
    }

    @Transactional(readOnly = true, timeout = 8)
    fun history(actor: PageActor, request: HistoryRequest): RevisionHistory {
        requireRead(actor)
        if (request.limit !in 1..50) throw PageFailure(PageError.INVALID_LIMIT)
        val page = revisionPage(actor, request.page)
        val anchor = request.anchor?.let {
            repository.findHistoryAnchor(page, it) ?: throw PageFailure(PageError.INVALID_CURSOR)
        }
        if (request.anchorTime != null && request.anchorTime != anchor?.createdAt) {
            throw PageFailure(PageError.INVALID_CURSOR)
        }
        val matches = repository.findHistory(
            page, HistoryWindow(anchor, request.direction, request.inclusive), request.limit + 1,
        )
        val next = matches.getOrNull(request.limit)
        val revisions = matches.take(request.limit)
        val older = request.direction == HistoryDirection.OLDER
        val current = if (request.includeCurrent) {
            revisions.find { it.id == page.currentRevisionId }
                ?: repository.findHistoryAnchor(page, RevisionLookup.Id(page.currentRevisionId))
        } else null
        return RevisionHistory(
            page, if (older) revisions else revisions.asReversed(), next,
            hasNewer = if (older) anchor != null && !request.inclusive else next != null,
            hasOlder = if (older) next != null else anchor != null,
            current = current,
        )
    }

    private fun find(lookup: PageLookup): PageRead {
        return when (lookup) {
            is PageLookup.Title -> {
                val title = parseTitle(lookup.input)
                PageRead(repository.findByTitle(title), title)
            }
            is PageLookup.Id -> PageRead(repository.findById(lookup))
            is PageLookup.CompatId -> PageRead(repository.findByCompatId(lookup))
        }
    }

    @Transactional(timeout = 8)
    fun create(actor: PageActor, requestId: UUID, command: CreatePage): Page {
        requireRead(actor)
        if (actor == PageActor.ANONYMOUS && !(policy.anonymousEdit && policy.anonymousCreate)) {
            throw PageFailure(PageError.FORBIDDEN)
        }
        if (command.namespaceId != 0) throw PageFailure(PageError.INVALID_NAMESPACE)
        val title = parseTitle(command.title)
        val result = repository.create(
            FirstRevision(title, revision(actor, requestId, command.source, command.summary)),
        )
        return when (result) {
            is PageInsert.Created -> result.page
            PageInsert.DuplicateTitle -> throw PageFailure(PageError.EXISTS)
        }
    }

    @Transactional(timeout = 8)
    fun edit(actor: PageActor, requestId: UUID, command: EditPage): Page {
        requireRead(actor)
        if (actor == PageActor.ANONYMOUS && !policy.anonymousEdit) throw PageFailure(PageError.FORBIDDEN)
        val revision = revision(actor, requestId, command.source, command.summary)
        val current = find(command.lookup).page ?: throw PageFailure(PageError.PAGE_NOT_FOUND)
        val currentBase = when (val base = command.base) {
            is RevisionBase.Native -> base.id == current.revisionId
            is RevisionBase.Compat -> base.id == current.compatRevisionId
        }
        if (!currentBase) throw PageFailure(PageError.EDIT_CONFLICT)
        return when (val result = repository.append(current, revision)) {
            is RevisionAppend.Committed -> result.page
            RevisionAppend.StaleBase -> throw PageFailure(PageError.EDIT_CONFLICT)
        }
    }

    private fun revision(actor: PageActor, requestId: UUID, source: String, summary: String): RevisionWrite {
        val bytes = source.toByteArray()
        if (bytes.size > 1_048_576 || !validUnicode(source)) throw PageFailure(PageError.INVALID_SOURCE)
        if (summary.codePointCount(0, summary.length) > 500 || !validUnicode(summary)) {
            throw PageFailure(PageError.INVALID_SUMMARY)
        }
        val sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
        return RevisionWrite(source, sha256, summary, Instant.now().truncatedTo(ChronoUnit.SECONDS), actor, requestId)
    }

    fun requireRead(actor: PageActor) {
        if (actor == PageActor.ANONYMOUS && !policy.anonymousRead) throw PageFailure(PageError.FORBIDDEN)
    }

    private fun parseTitle(input: String): PageTitle =
        PageTitle.parse(input) ?: throw PageFailure(PageError.INVALID_TITLE)
}
