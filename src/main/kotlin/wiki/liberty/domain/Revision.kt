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
    val size: Int?,
    val delta: Int?,
    val sourceRestricted: Boolean = false,
    val parentSourceRestricted: Boolean = false,
)

data class RevisionSource(val page: RevisionPage, val revision: RevisionMetadata, val source: String)

enum class ComparisonSide { FROM, TO }

class ComparisonRevisionHidden(val side: ComparisonSide, val lookup: RevisionLookup) :
    PageFailure(PageError.REVISION_SOURCE_HIDDEN)

class ComparisonRevisionMissing(val side: ComparisonSide, val lookup: RevisionLookup) :
    PageFailure(PageError.REVISION_NOT_FOUND)

data class RevisionComparison(val from: RevisionSource, val to: RevisionSource, val lines: List<DiffLine>)

class EditConflict(val title: PageTitle, val currentRevisionId: UUID?, val comparison: RevisionComparison?) :
    PageFailure(PageError.EDIT_CONFLICT)
