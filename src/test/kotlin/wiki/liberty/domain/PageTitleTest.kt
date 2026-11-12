// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PageTitleTest {
    @Test
    fun `canonical title merges decomposed Unicode spaces and underscores`() {
        assertThat(PageTitle.parse(" _e\u0301lan__vital_ ")?.value).isEqualTo("Élan vital")
        assertThat(PageTitle.parse("Élan Vital")?.value).isEqualTo("Élan Vital")
    }

    @Test
    fun `rejects unsupported syntax and oversize UTF-8 keys`() {
        for (invalid in listOf("", "..", "a~~~", "Talk:Foo", "A/B", "a%2fb", "a&#48;", "a\n", "가".repeat(86))) {
            assertThat(PageTitle.parse(invalid)).describedAs(invalid).isNull()
        }
        assertThat(PageTitle.parse("가".repeat(85))).isNotNull()
    }
}
