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

@Service
class PageApplicationService(private val repository: PageRepository, private val policy: PagePolicy) {
    fun read(actor: PageActor, lookup: PageLookup): PageRead {
        requireRead(actor)
        return find(lookup)
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
