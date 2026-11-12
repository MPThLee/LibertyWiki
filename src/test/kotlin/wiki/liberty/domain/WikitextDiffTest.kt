// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WikitextDiffTest {
    private fun compare(a: String, b: String): List<DiffLine> =
        (WikitextDiff().compare(a, b) as DiffResult.Success).lines

    @Test
    fun `identical sources produce no changes`() {
        assertThat(compare("같음\n", "같음\n")).isEmpty()
    }

    @Test
    fun `preserves source lines and inline Unicode in both directions`() {
        val a = "{|\r\n| {{틀|😀가}}\r\n\n끝\n"
        val b = "{|\r\n| {{틀|😀나}}\r\n\n추가\n끝\n"
        for ((old, new) in listOf(a to b, b to a)) {
            val lines = compare(old, new)
            assertThat(lines.filter { it.fromLine != null }.joinToString("\n") { row -> row.from.joinToString("") { it.text } }).isEqualTo(old)
            assertThat(lines.filter { it.toLine != null }.joinToString("\n") { row -> row.to.joinToString("") { it.text } }).isEqualTo(new)
            val changed = lines.single { it.type == DiffLineType.CHANGED }
            assertThat(changed.from.filter { it.changed }.joinToString("") { it.text }).isIn("가", "나")
            assertThat(changed.to.filter { it.changed }.joinToString("") { it.text }).isIn("가", "나")
            assertThat(changed.from.filterNot { it.changed }.joinToString("") { it.text }).contains("😀")
        }
    }

    @Test
    fun `bounds input output and comparison work`() {
        assertThat(WikitextDiff(WikitextDiffLimits(maxInputBytes = 3)).compare("가", "나")).isEqualTo(DiffResult.TooLarge)
        assertThat(WikitextDiff(WikitextDiffLimits(maxInputLines = 2)).compare("a\nb", "c\nd")).isEqualTo(DiffResult.TooLarge)
        assertThat(WikitextDiff(WikitextDiffLimits(maxOutputRows = 1)).compare("a\nb", "c\nb")).isEqualTo(DiffResult.TooLarge)
        assertThat(WikitextDiff(WikitextDiffLimits(maxOutputBytes = 10)).compare("a", "b")).isEqualTo(DiffResult.TooLarge)
        assertThat(WikitextDiff(WikitextDiffLimits(maxOutputBytes = 1)).compare("a", "a")).isEqualTo(DiffResult.TooLarge)
        assertThat(WikitextDiff(WikitextDiffLimits(maxOutputBytes = 2)).compare("a", "a")).isEqualTo(DiffResult.Success(emptyList()))
        assertThat(WikitextDiff(WikitextDiffLimits(maxWork = 2)).compare("a", "b")).isEqualTo(DiffResult.TooLarge)
    }

    @Test
    fun `line additions deletions and empty lines have one based positions`() {
        val rows = compare("a\n\nb", "a\n\nb\nc")
        assertThat(rows.map { it.type }).containsExactly(DiffLineType.CONTEXT, DiffLineType.CONTEXT, DiffLineType.CONTEXT, DiffLineType.ADDED)
        assertThat(rows.last().fromLine).isNull()
        assertThat(rows.last().toLine).isEqualTo(4)
        assertThat(rows[1].from.single().text).isEmpty()
        assertThat(compare("a\n\nb\nc", "a\n\nb").last().type).isEqualTo(DiffLineType.DELETED)
    }
}
