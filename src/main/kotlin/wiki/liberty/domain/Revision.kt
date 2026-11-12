// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.domain

import java.time.Instant
import java.util.UUID

sealed interface RevisionLookup {
    data class Id(val value: UUID) : RevisionLookup
    data class CompatId(val value: Long) : RevisionLookup
}

data class RevisionPage(val id: UUID, val compatId: Long, val title: PageTitle, val currentRevisionId: UUID)

data class RevisionMetadata(
    val id: UUID,
    val compatId: Long,
    val parentId: UUID?,
    val compatParentId: Long?,
    val createdAt: Instant,
    val summary: String,
    val actor: String,
    val size: Int,
    val delta: Int?,
)

data class RevisionSource(val page: RevisionPage, val revision: RevisionMetadata, val source: String)
