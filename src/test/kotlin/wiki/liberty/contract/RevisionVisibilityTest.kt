// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.contract

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID

@Tag("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "wiki.pages.anonymous-read=true",
    "wiki.pages.anonymous-edit=true",
    "wiki.http.operator-token=$operatorToken",
])
class RevisionVisibilityTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `each source restriction is enforced by every comparison and source route`() {
        val http = ContractHttp("http://127.0.0.1:$port")
        val title = "Restricted-${UUID.randomUUID()}"
        val secret = "== Private heading ==\nSecret source"
        val created = http.nativeCreate(title, secret).json
        val pageId = created["id"].asText()
        val restricted = created["currentRevisionId"].asText()
        val first = http.restRead(title).json.at("/latest/id").asLong()
        val edited = http.postJson("/api/v1/pages/$pageId/revisions",
            mapOf("baseRevisionId" to restricted, "source" to "Visible source"), operatorToken)
        assertThat(edited.status).isEqualTo(201)
        val visible = edited.json["currentRevisionId"].asText()
        val second = http.restRead(title).json.at("/latest/id").asLong()
        assertThat(first).isPositive()
        assertThat(second).isGreaterThan(first)
        jdbc.update("INSERT INTO wiki_revision_source_restriction (revision_id) VALUES (?)", UUID.fromString(restricted))
        val before = counts()

        for ((from, to, side) in listOf(Triple(restricted, visible, "from"), Triple(visible, restricted, "to"))) {
            val path = "/api/v1/revisions/$from/compare/$to"
            val denied = http.get(path)
            assertThat(denied.status).isEqualTo(403)
            assertThat(denied.json["code"].asText()).isEqualTo("REVISION_SOURCE_HIDDEN")
            assertThat(denied.json.toString()).doesNotContain("Private heading", "Secret source", "Visible source")
            assertThat(http.get(path, operatorToken).status).isEqualTo(200)

            val fromId = if (side == "from") first else second
            val toId = if (side == "from") second else first
            val rest = http.restCompare(fromId, toId)
            assertThat(rest.status).isEqualTo(403)
            assertThat(rest.json["errorKey"].asText()).isEqualTo("rest-compare-inaccessible")
            assertThat(rest.json.at("/messageTranslations/en").asText()).contains("$side parameter")
            assertThat(rest.json.toString()).doesNotContain("Private heading", "Secret source", "Visible source")
            assertThat(http.restCompare(fromId, toId, operatorToken).status).isEqualTo(200)

            val action = http.actionCompare(fromId, toId)
            assertThat(action.status).isEqualTo(200)
            assertThat(action.json.at("/error/code").asText()).isEqualTo("missingcontent")
            assertThat(action.json.toString()).doesNotContain("Private heading", "Secret source", "Visible source")
            assertThat(http.actionCompare(fromId, toId, operatorToken).json.at("/compare/${side}texthidden").asBoolean()).isTrue()
        }

        assertThat(http.get("/api/v1/revisions/$restricted").status).isEqualTo(403)
        assertThat(http.restRevision(first).status).isEqualTo(403)
        assertThat(http.actionRead("revids" to "$first").json.at("/error/code").asText()).isEqualTo("missingcontent")
        val history = http.restHistory(title).json["revisions"]
        assertThat(history[0]["delta"].isNull).isTrue()
        assertThat(history[1].has("size")).isFalse()
        assertThat(http.restRead(title).json["source"].asText()).isEqualTo("Visible source")
        assertThat(counts()).isEqualTo(before)

        jdbc.update("INSERT INTO wiki_revision_source_restriction (revision_id) VALUES (?)", UUID.fromString(visible))
        assertThat(http.get("/api/v1/pages/$pageId").status).isEqualTo(403)
        assertThat(http.restRead(title).status).isEqualTo(403)
        assertThat(http.postJson("/api/v1/pages/$pageId/revisions",
            mapOf("baseRevisionId" to visible, "source" to "Attempted edit")).status).isEqualTo(403)
        assertThat(http.get("/api/v1/pages/$pageId", operatorToken).json).isEqualTo(edited.json)
    }

    private fun counts() = listOf("wiki_page", "wiki_revision", "wiki_audit", "wiki_outbox", "wiki_revision_source_restriction")
        .map { jdbc.queryForObject("SELECT count(*) FROM $it", Long::class.javaObjectType) }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun database(properties: DynamicPropertyRegistry) = postgresProperties(properties)
    }
}
