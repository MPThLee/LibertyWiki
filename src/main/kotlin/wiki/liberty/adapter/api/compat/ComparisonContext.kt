// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.adapter.api.compat

import wiki.liberty.domain.DiffLine
import wiki.liberty.domain.DiffLineType
import wiki.liberty.domain.RevisionComparison

internal fun RevisionComparison.compatHunks(): List<List<DiffLine>> {
    // A shared terminal LF produces an empty logical line for the Native view.
    val rows = if (lines.lastOrNull()?.type == DiffLineType.CONTEXT &&
        from.source.endsWith('\n') && to.source.endsWith('\n') &&
        lines.last().from.all { it.text.isEmpty() }
    ) lines.dropLast(1) else lines
    val hunks = mutableListOf<List<DiffLine>>()
    var start = 0
    var end = -1
    for ((index, line) in rows.withIndex()) {
        if (line.type == DiffLineType.CONTEXT) continue
        val nextStart = (index - 2).coerceAtLeast(0)
        if (nextStart > end + 1) {
            if (end >= start) hunks.add(rows.subList(start, end + 1))
            start = nextStart
        }
        end = (index + 2).coerceAtMost(rows.lastIndex)
    }
    if (end >= start) hunks.add(rows.subList(start, end + 1))
    return hunks
}
