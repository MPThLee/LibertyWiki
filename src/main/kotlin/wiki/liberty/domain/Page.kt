// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.domain

import java.time.Instant
import java.util.UUID

data class Page(
    val id: UUID,
    val compatId: Long,
    val title: PageTitle,
    val revisionId: UUID,
    val compatRevisionId: Long,
    val source: String,
    val createdAt: Instant,
    val compatParentRevisionId: Long? = null,
)

sealed interface PageLookup {
    data class Title(val input: String) : PageLookup
    data class Id(val value: UUID) : PageLookup
    data class CompatId(val value: Long) : PageLookup
}

enum class PageError {
    FORBIDDEN, INVALID_TITLE, INVALID_NAMESPACE, INVALID_SOURCE, INVALID_SUMMARY, EXISTS,
    PAGE_NOT_FOUND, REVISION_NOT_FOUND, INVALID_CURSOR, INVALID_LIMIT, EDIT_CONFLICT
}

class PageFailure(val error: PageError) : RuntimeException(error.name)
