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
data class PageRead(val page: Page?, val normalizedTitle: PageTitle? = null)

@Service
class PageApplicationService(private val repository: PageRepository, private val policy: PagePolicy) {
    fun read(actor: PageActor, lookup: PageLookup): PageRead {
        requireRead(actor)
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
        val source = command.source.toByteArray()
        if (source.size > 1_048_576 || !validUnicode(command.source)) throw PageFailure(PageError.INVALID_SOURCE)
        if (command.summary.codePointCount(0, command.summary.length) > 500 || !validUnicode(command.summary)) {
            throw PageFailure(PageError.INVALID_SUMMARY)
        }
        val sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source))
        val result = repository.create(
            FirstRevision(title, command.source, sha256, command.summary, Instant.now().truncatedTo(ChronoUnit.SECONDS),
                actor, requestId),
        )
        return when (result) {
            is PageInsert.Created -> result.page
            PageInsert.DuplicateTitle -> throw PageFailure(PageError.EXISTS)
        }
    }

    fun requireRead(actor: PageActor) {
        if (actor == PageActor.ANONYMOUS && !policy.anonymousRead) throw PageFailure(PageError.FORBIDDEN)
    }

    private fun parseTitle(input: String): PageTitle =
        PageTitle.parse(input) ?: throw PageFailure(PageError.INVALID_TITLE)
}
