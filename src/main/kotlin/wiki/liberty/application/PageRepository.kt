// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.application

import wiki.liberty.domain.Page
import wiki.liberty.domain.PageLookup
import wiki.liberty.domain.PageTitle
import java.time.Instant
import java.util.UUID

interface PageRepository {
    fun findByTitle(title: PageTitle): Page?
    fun findById(id: PageLookup.Id): Page?
    fun findByCompatId(id: PageLookup.CompatId): Page?
    fun create(page: FirstRevision): PageInsert
    fun append(base: Page, revision: RevisionWrite): RevisionAppend
}

sealed interface PageInsert {
    data class Created(val page: Page) : PageInsert
    data object DuplicateTitle : PageInsert
}

sealed interface RevisionAppend {
    data class Committed(val page: Page) : RevisionAppend
    data object StaleBase : RevisionAppend
}

data class FirstRevision(
    val title: PageTitle,
    val revision: RevisionWrite,
)

data class RevisionWrite(
    val source: String,
    val sha256: String,
    val summary: String,
    val createdAt: Instant,
    val actor: PageActor,
    val requestId: UUID,
)
