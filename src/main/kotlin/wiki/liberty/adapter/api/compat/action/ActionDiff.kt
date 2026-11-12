// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.adapter.api.compat.action

import org.springframework.web.util.HtmlUtils
import wiki.liberty.adapter.api.compat.compatHunks
import wiki.liberty.domain.DiffFragment
import wiki.liberty.domain.DiffLineType
import wiki.liberty.domain.RevisionComparison

internal fun actionDiff(comparison: RevisionComparison): String = buildString {
    for (hunk in comparison.compatHunks()) {
        val from = hunk.firstNotNullOfOrNull { it.fromLine } ?: 1
        val to = hunk.firstNotNullOfOrNull { it.toLine } ?: 1
        append("<tr>\n  <td colspan=\"2\" class=\"diff-lineno\">Line $from:</td>\n")
        append("  <td colspan=\"2\" class=\"diff-lineno\">Line $to:</td>\n</tr>\n")
        for (line in hunk) {
            append("<tr>\n")
            when (line.type) {
                DiffLineType.CONTEXT -> {
                    diffCell(line.from, "diff-context diff-side-deleted", "", "del", false)
                    diffCell(line.to, "diff-context diff-side-added", "", "ins", false)
                }
                DiffLineType.ADDED -> {
                    emptyDiffCell("deleted")
                    diffCell(line.to, "diff-addedline diff-side-added", "+", "ins", false)
                }
                DiffLineType.DELETED -> {
                    diffCell(line.from, "diff-deletedline diff-side-deleted", "−", "del", false)
                    emptyDiffCell("added")
                }
                DiffLineType.CHANGED -> {
                    diffCell(line.from, "diff-deletedline diff-side-deleted", "−", "del", true)
                    diffCell(line.to, "diff-addedline diff-side-added", "+", "ins", true)
                }
            }
            append("</tr>\n")
        }
    }
}

private fun StringBuilder.emptyDiffCell(side: String) {
    append("  <td colspan=\"2\" class=\"diff-empty diff-side-$side\"></td>\n")
}

private fun StringBuilder.diffCell(
    fragments: List<DiffFragment>, css: String, marker: String, tag: String, inline: Boolean,
) {
    append("  <td class=\"diff-marker\"")
    if (marker.isNotEmpty()) append(" data-marker=\"$marker\"")
    append("></td>\n  <td class=\"$css\"><div>")
    for (fragment in fragments) {
        val text = HtmlUtils.htmlEscape(fragment.text, "UTF-8")
        if (inline && fragment.changed) append("<$tag class=\"diffchange diffchange-inline\">$text</$tag>")
        else append(text)
    }
    append("</div></td>\n")
}
