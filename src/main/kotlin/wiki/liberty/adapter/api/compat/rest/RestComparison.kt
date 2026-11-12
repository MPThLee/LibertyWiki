// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.adapter.api.compat.rest

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import wiki.liberty.adapter.api.compat.compatHunks
import wiki.liberty.domain.DiffFragment
import wiki.liberty.domain.DiffLineType
import wiki.liberty.domain.RevisionComparison

data class RestComparison(val from: RestComparisonRevision, val to: RestComparisonRevision, val diff: List<RestDiffLine>)
data class RestComparisonRevision(val id: Long, @get:JsonProperty("slot_role") val slotRole: String, val sections: List<RestSection>)
data class RestSection(val level: Int, val heading: String, val offset: Int)
data class RestDiffOffset(val from: Int?, val to: Int?)
data class RestDiffHighlight(val start: Int, val length: Int, val type: Int)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RestDiffLine(
    val type: Int,
    val lineNumber: Int?,
    val text: String,
    val offset: RestDiffOffset,
    val highlightRanges: List<RestDiffHighlight>? = null,
)

internal fun RevisionComparison.restComparison(): RestComparison {
    val left = RestSourceLocations(from.source)
    val right = RestSourceLocations(to.source)
    return RestComparison(
        RestComparisonRevision(from.revision.compatId, "main", left.sections),
        RestComparisonRevision(to.revision.compatId, "main", right.sections),
        compatHunks().flatten().map { row ->
            val offset = RestDiffOffset(row.fromLine?.let(left::offset), row.toLine?.let(right::offset))
            when (row.type) {
                DiffLineType.CONTEXT -> RestDiffLine(0, row.toLine, row.to.raw(), offset)
                DiffLineType.ADDED -> RestDiffLine(1, row.toLine, row.to.raw(), offset)
                DiffLineType.DELETED -> RestDiffLine(2, null, row.from.raw(), offset)
                DiffLineType.CHANGED -> {
                    val (text, highlights) = merge(row.from, row.to)
                    RestDiffLine(3, row.toLine, text, offset, highlights)
                }
            }
        },
    )
}

private fun List<DiffFragment>.raw() = joinToString("") { it.text }

private class RestSourceLocations(source: String) {
    private val offsets = mutableListOf<Int>()
    val sections = buildList {
        var offset = 0
        for (line in source.split('\n')) {
            offsets.add(offset)
            val level = headingLevel(line)
            if (level > 0) add(RestSection(level, line, offset))
            offset += line.toByteArray().size + 1
        }
    }

    fun offset(line: Int) = offsets[line - 1]

    private fun headingLevel(line: String): Int {
        val leading = line.takeWhile { it == '=' }.length
        if (leading == 0) return 0
        var end = line.length
        while (end > leading) {
            while (end > leading && line[end - 1].isWhitespace()) end--
            if (end < 3 || !line.regionMatches(end - 3, "-->", 0, 3)) break
            val comment = line.lastIndexOf("<!--", end - 3)
            if (comment < leading) break
            end = comment
        }
        var closing = 0
        while (end - closing > leading && line[end - closing - 1] == '=') closing++
        return if (end - closing > leading) minOf(leading, closing, 6) else 0
    }
}

private fun merge(from: List<DiffFragment>, to: List<DiffFragment>): Pair<String, List<RestDiffHighlight>> {
    val left = Fragments(from)
    val right = Fragments(to)
    val highlights = mutableListOf<RestDiffHighlight>()
    val text = StringBuilder()
    var bytes = 0
    fun append(value: String, type: Int? = null) {
        val size = value.toByteArray().size
        if (type != null) highlights.add(RestDiffHighlight(bytes, size, type))
        text.append(value)
        bytes += size
    }
    while (left.remaining > 0 || right.remaining > 0) {
        if (left.changed) append(left.take(left.remaining), 1)
        if (right.changed) append(right.take(right.remaining), 0)
        val common = minOf(left.remaining, right.remaining)
        if (common > 0) {
            append(left.take(common))
            right.take(common)
        } else {
            check(left.remaining == 0 && right.remaining == 0) { "Unpaired unchanged diff text" }
        }
    }
    return text.toString() to highlights
}

private class Fragments(private val parts: List<DiffFragment>) {
    private var index = 0
    private var position = 0
    private val part get() = parts.getOrNull(index)
    val changed get() = part?.changed == true
    val remaining get() = (part?.text?.length ?: 0) - position

    fun take(count: Int): String {
        val text = checkNotNull(part).text.substring(position, position + count)
        position += count
        if (remaining == 0) {
            index++
            position = 0
        }
        return text
    }
}
