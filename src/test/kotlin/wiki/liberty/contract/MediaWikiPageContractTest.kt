// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.contract

import com.fasterxml.jackson.databind.JsonNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.URI
import java.time.Instant
import java.util.UUID

@Tag("mediawiki")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "wiki.pages.anonymous-read=true",
    "wiki.pages.anonymous-edit=true",
    "wiki.pages.anonymous-create=true",
])
class MediaWikiPageContractTest {
    @LocalServerPort private var port: Int = 0
    private val source = "== Example ==\nRaw [[Wikitext]]"

    @Test
    fun `compares documented creation and source contracts against MediaWiki 1_46_2`() {
        val baseline = ContractHttp(
            checkNotNull(System.getenv("WIKI_MW_BASE_URL")) { "Set WIKI_MW_BASE_URL to a disposable MediaWiki 1.46.2 server." }
                .trimEnd('/'),
            System.getenv("WIKI_MW_SCRIPT_PATH") ?: "/w",
        )
        val general = baseline.actionQuery("meta" to "siteinfo").json.at("/query/general")
        assertThat(general["generator"].asText()).isEqualTo("MediaWiki 1.46.2")
        assertThat(general["lang"].asText()).isEqualTo("en")
        assertThat(general["case"].asText()).isEqualTo("first-letter")
        val local = ContractHttp("http://127.0.0.1:$port")

        val suffix = UUID.randomUUID().toString()
        val actionTitle = "Action $suffix"
        val restTitle = "Rest $suffix"
        val missingTitle = "Missing $suffix"
        val expected = observe(baseline, actionTitle, restTitle, missingTitle)
        val actual = observe(local, actionTitle, restTitle, missingTitle)
        assertThat(actual).isEqualTo(expected)
    }

    private fun observe(http: ContractHttp, actionTitle: String, restTitle: String, missingTitle: String): Map<String, Any> {
        val token = http.actionQuery("meta" to "tokens", "type" to "csrf").json.at("/query/tokens/csrftoken").asText()
        assertThat(token).isEqualTo(csrfToken)

        val edit = http.actionCreate(actionTitle, source, token)
        val saved = edit.json["edit"]
        assertThat(edit.status).isEqualTo(200)
        assertThat(saved["result"].asText()).isEqualTo("Success")
        assertThat(saved["new"].asBoolean()).isTrue()
        assertThat(saved["oldrevid"].asLong()).isZero()
        val actionPage = readAction(http, actionTitle)
        val revision = actionPage.at("/query/pages/0/revisions/0")
        assertThat(revision["revid"]).isEqualTo(saved["newrevid"])
        assertThat(revision["timestamp"]).isEqualTo(saved["newtimestamp"])
        Instant.parse(revision["timestamp"].asText())
        assertThat(actionPage.at("/query/pages/0/pageid")).isEqualTo(saved["pageid"])
        assertThat(revision["parentid"].asLong()).isZero()
        val slot = revision.at("/slots/main")
        assertThat(slot["content"].asText()).isEqualTo(source)
        val emptyTitle = "Empty $actionTitle"
        assertThat(http.actionCreate(emptyTitle, "", token).json.at("/edit/result").asText()).isEqualTo("Success")
        assertThat(readAction(http, emptyTitle).at("/query/pages/0/revisions/0/slots/main/content").textValue()).isEmpty()

        val restCreate = http.restCreate(restTitle, source, token)
        assertThat(restCreate.status).isEqualTo(201)
        val restKey = restTitle.replace(' ', '_')
        assertThat(URI.create(restCreate.header("Location")).path).endsWith("/rest.php/v1/page/$restKey")
        val restRead = http.restRead(restKey)
        assertThat(restRead.status).isEqualTo(200)
        assertThat(restRead.json).isEqualTo(restCreate.json)
        assertThat(restRead.json["source"].asText()).isEqualTo(source)
        val restRevision = readAction(http, restTitle).at("/query/pages/0/revisions/0")
        assertThat(restRead.json.at("/latest/id")).isEqualTo(restRevision["revid"])
        assertThat(restRead.json.at("/latest/timestamp")).isEqualTo(restRevision["timestamp"])
        val redirect = http.restRead(restKey.replaceFirstChar { it.lowercaseChar() })
        assertThat(redirect.status).isEqualTo(301)
        assertThat(URI.create(redirect.header("Location")).path).endsWith("/rest.php/v1/page/$restKey")

        val restConflict = http.restCreate(restTitle, source, token)
        val restMissing = http.restRead(missingTitle)
        val restInvalid = http.restRead("[Invalid]")
        val restBadToken = http.restCreate(missingTitle, source, token = null)
        val nullCommentTitle = "Summary $restTitle"
        val nullComment = http.restCreate(nullCommentTitle, source, token, comment = null)
        assertThat(nullComment.status).isEqualTo(400)
        assertThat(http.restRead(nullCommentTitle).status).isEqualTo(404)
        val projectedPage = restRead.json.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().apply {
            remove(listOf("id", "latest", "license"))
        }
        return mapOf(
            "actionCreate" to saved.select("new", "result", "title", "contentmodel", "oldrevid"),
            "actionSlot" to slot,
            "actionNormalization" to actionPage.at("/query/normalized"),
            "actionMissing" to readAction(http, missingTitle).at("/query/pages/0"),
            "actionConflict" to http.actionCreate(actionTitle, source, token).json.at("/error/code").asText(),
            "actionInvalid" to http.actionCreate("[Invalid]", source, token).json.at("/error/code").asText(),
            "actionBadToken" to http.actionCreate(missingTitle, source, "wrong").json.at("/error/code").asText(),
            "restPage" to projectedPage,
            "restConflict" to restError(restConflict),
            "restMissing" to restError(restMissing),
            "restInvalid" to restError(restInvalid),
            "restBadToken" to restError(restBadToken),
            "restNullComment" to restError(nullComment),
        )
    }

    private fun readAction(http: ContractHttp, title: String) =
        http.actionRead("titles" to title.replace(' ', '_').replaceFirstChar { it.lowercaseChar() }).json

    private fun JsonNode.select(vararg names: String) = names.associateWith { this[it] }

    private fun restError(response: ContractHttp.Result) =
        response.status to response.json["errorKey"].asText()

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun database(properties: DynamicPropertyRegistry) = postgresProperties(properties)
    }
}
