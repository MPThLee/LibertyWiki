// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.contract

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.io.File
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors

@Tag("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "wiki.pages.anonymous-read=false",
    "wiki.pages.anonymous-edit=true",
    "wiki.pages.anonymous-create=true",
    "wiki.http.operator-token=$operatorToken",
])
class PagePostgresTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var jdbc: JdbcTemplate
    private val http get() = ContractHttp("http://127.0.0.1:$port")
    private val native = "/api/v1/pages"
    private val source = "== Source ==\r\n<script>literal</script>\n"

    @Test
    fun `every creation surface persists the same first revision and events`() {
        val nativeTitle = "Élan ${UUID.randomUUID()}"
        val restTitle = "Rest ${UUID.randomUUID()}"
        val actionTitle = "Action ${UUID.randomUUID()}"
        val nativeResult = http.nativeCreate(nativeTitle.replace("Élan ", "e\u0301lan__"), source)
        assertThat(nativeResult.status).isEqualTo(201)
        assertSchema("/paths/~1pages/post/responses/201", nativeResult.json)
        assertThat(nativeResult.header("Location")).isEqualTo("$native/${nativeResult.json["id"].asText()}")

        val restResult = http.restCreate(restTitle, source, auth = operatorToken)
        assertThat(restResult.status).isEqualTo(201)
        val actionResult = http.actionCreate(actionTitle, source, auth = operatorToken)
        assertThat(actionResult.json.at("/edit/result").asText()).isEqualTo("Success")

        for ((title, result) in listOf(nativeTitle to nativeResult, restTitle to restResult, actionTitle to actionResult)) {
            val key = title.replace(' ', '_')
            val byTitle = http.get("$native/by-title/$key", operatorToken)
            assertThat(byTitle.status).isEqualTo(200)
            assertSchema("/paths/~1pages~1by-title~1{title}/get/responses/200", byTitle.json)
            val byId = http.get("$native/${byTitle.json["id"].asText()}", operatorToken)
            assertSchema("/paths/~1pages~1{pageId}/get/responses/200", byId.json)
            assertThat(byId.json).isEqualTo(byTitle.json)
            assertThat(byTitle.json["source"].asText()).isEqualTo(source)
            assertThat(byTitle.json["normalizedTitle"].asText()).isEqualTo(title)

            val rest = http.restRead(key, operatorToken).json
            val action = http.actionRead("pageids" to rest["id"].asText(), operatorToken).json
            assertThat(action.at("/query/pages/0/revisions/0/revid")).isEqualTo(rest.at("/latest/id"))
            assertThat(action.at("/query/pages/0/revisions/0/parentid").asInt()).isZero()
            assertThat(action.at("/query/pages/0/revisions/0/slots/main/content").asText()).isEqualTo(source)
            assertThat(rest["source"].asText()).isEqualTo(source)
            val info = http.actionQuery("prop" to "info", "titles" to key, auth = operatorToken).json
            assertThat(info.at("/query/pages/0/lastrevid")).isEqualTo(rest.at("/latest/id"))
            assertState(title, byTitle.json, result.header("X-Request-Id"))
        }
    }

    @Test
    fun `invalid missing and private requests preserve state across surfaces`() {
        val missing = "Missing_${UUID.randomUUID()}"
        val secret = "Secret_${UUID.randomUUID()}"
        assertThat(http.nativeCreate(secret, source).status).isEqualTo(201)
        val before = rowCounts()

        for (key in listOf(secret, missing)) {
            val denied = http.get("$native/by-title/$key")
            assertThat(denied.status).isEqualTo(403)
            assertSchema("/paths/~1pages~1by-title~1{title}/get/responses/403", denied.json)
            assertThat(http.restRead(key).status).isEqualTo(403)
            assertThat(http.actionRead("titles" to key).json.at("/error/code").asText()).isEqualTo("readapidenied")
        }
        val absent = http.get("$native/by-title/$missing", operatorToken)
        assertThat(absent.status).isEqualTo(404)
        assertSchema("/paths/~1pages~1by-title~1{title}/get/responses/404", absent.json)
        assertThat(http.restRead(missing, operatorToken).status).isEqualTo(404)
        assertThat(http.actionRead("titles" to missing, operatorToken).json.at("/query/pages/0/missing").asBoolean()).isTrue()

        assertNativeError(http.nativeCreate(missing, source, null), 403)
        assertThat(http.restCreate(missing, source).status).isEqualTo(403)
        assertThat(http.actionCreate(missing, source).json.at("/error/code").asText()).isEqualTo("permissiondenied")
        val unauthorized = http.get("$native/by-title/$secret", "incorrect-token")
        assertThat(unauthorized.status).isEqualTo(401)
        assertThat(unauthorized.header("WWW-Authenticate")).isEqualTo("Bearer")
        assertSchema("/paths/~1pages~1by-title~1{title}/get/responses/401", unauthorized.json)
        assertNativeError(http.nativeCreate("[bad]", source), 422)
        assertNativeError(http.postJson(native, mapOf("title" to missing, "source" to source), operatorToken), 400)
        assertNativeError(http.postJson(native, mapOf("title" to missing, "namespaceId" to 0, "source" to true), operatorToken), 400)
        assertNativeError(http.postText(native, source, operatorToken), 415)
        assertNativeError(http.nativeCreate(secret, source), 409)
        assertThat(http.restCreate("[bad]", source, auth = operatorToken).status).isEqualTo(400)
        assertThat(http.actionCreate("[bad]", source, auth = operatorToken).json.at("/error/code").asText()).isEqualTo("invalidtitle")
        assertThat(http.actionCreate(missing, source, "invalid", operatorToken).json.at("/error/code").asText()).isEqualTo("badtoken")
        assertThat(rowCounts()).isEqualTo(before)
    }

    @Test
    fun `encoded title punctuation cannot be mistaken for a path parameter`() {
        val base = "Punctuation-${UUID.randomUUID()}"
        assertThat(http.nativeCreate(base, "base").status).isEqualTo(201)
        assertThat(http.nativeCreate("$base;part", source).status).isEqualTo(201)
        assertThat(http.get("$native/by-title/$base%3Bpart", operatorToken).json["source"].asText()).isEqualTo(source)
        assertThat(http.restRead("$base;part", operatorToken).json["source"].asText()).isEqualTo(source)
        assertThat(http.get("$native/by-title/$base;part", operatorToken).status).isEqualTo(422)
        assertThat(http.get("/w/rest.php/v1/page/$base;part", operatorToken).status).isEqualTo(404)
    }

    @Test
    fun `concurrent normalized creations have a single commit across all surfaces`() {
        val id = UUID.randomUUID()
        val title = "Race $id"
        val before = rowCounts()
        val barrier = CyclicBarrier(3)
        val calls = listOf(
            { http.nativeCreate(title, source) },
            { http.restCreate("race__$id", source, auth = operatorToken) },
            { http.actionCreate(" Race_$id ", source, auth = operatorToken) },
        )
        val results = Executors.newFixedThreadPool(calls.size).use { executor ->
            executor.invokeAll(calls.map { call -> Callable { barrier.await(); call() } }).map { it.get() }
        }
        val wins = listOf(results[0].status == 201, results[1].status == 201, results[2].json.has("edit"))
        assertThat(wins.count { it }).isEqualTo(1)
        if (!wins[0]) assertNativeError(results[0], 409)
        if (!wins[1]) assertThat(results[1].status).isEqualTo(409)
        if (!wins[2]) assertThat(results[2].json.at("/error/code").asText()).isEqualTo("articleexists")
        assertThat(rowCounts()).containsExactlyElementsOf(before.map { it + 1 })
        val page = http.get("$native/by-title/Race_$id", operatorToken).json
        assertState(title, page, results[wins.indexOf(true)].header("X-Request-Id"))
    }

    @Test
    fun `a failure after the audit insert rolls back every write`() {
        val before = rowCounts()
        val title = "Rollback ${UUID.randomUUID()}"
        jdbc.execute("ALTER TABLE wiki_outbox ADD CONSTRAINT contract_reject_outbox CHECK (false) NOT VALID")
        try {
            assertThat(http.nativeCreate(title, source).status).isEqualTo(500)
            assertThat(http.get("$native/by-title/${title.replace(' ', '_')}", operatorToken).status).isEqualTo(404)
            assertThat(rowCounts()).isEqualTo(before)
        } finally {
            jdbc.execute("ALTER TABLE wiki_outbox DROP CONSTRAINT contract_reject_outbox")
        }
    }

    private fun assertState(title: String, nativePage: JsonNode, requestId: String) {
        val rows = jdbc.queryForList(
            """
            SELECT p.id, r.id AS revision_id, r.parent_revision_id, r.actor, r.source,
                   a.actor AS audit_actor, o.actor AS outbox_actor, a.request_id, o.published_at,
                   a.event_type
            FROM wiki_page p
            JOIN wiki_revision r ON r.page_id = p.id
            JOIN wiki_audit a ON a.page_id = p.id AND a.revision_id = r.id
            JOIN wiki_outbox o ON o.page_id = p.id AND o.revision_id = r.id
                              AND o.request_id = a.request_id AND o.event_type = a.event_type
            WHERE p.normalized_title = ? AND p.current_revision_id = r.id
            """.trimIndent(),
            title,
        )
        val row = rows.single()
        assertThat(row["id"].toString()).isEqualTo(nativePage["id"].asText())
        assertThat(row["revision_id"].toString()).isEqualTo(nativePage["currentRevisionId"].asText())
        assertThat(row["parent_revision_id"]).isNull()
        assertThat(row["source"]).isEqualTo(source)
        assertThat(row["actor"]).isEqualTo("operator")
        assertThat(row["audit_actor"]).isEqualTo(row["actor"])
        assertThat(row["outbox_actor"]).isEqualTo(row["actor"])
        assertThat(row["event_type"]).isEqualTo("page.created")
        assertThat(row["request_id"].toString()).isEqualTo(requestId)
        assertThat(row["published_at"]).isNull()
    }

    private fun rowCounts() = listOf("wiki_page", "wiki_revision", "wiki_audit", "wiki_outbox").map { table ->
        checkNotNull(jdbc.queryForObject("SELECT count(*) FROM $table", Long::class.javaObjectType))
    }

    private fun assertSchema(pointer: String, body: JsonNode) {
        val response = spec.at(pointer)
        val resolved = if (response.has("\$ref")) spec.at(response["\$ref"].asText().removePrefix("#")) else response
        val schema = yaml.createObjectNode()
        schema.set<JsonNode>("components", spec["components"])
        schema.set<JsonNode>("allOf", yaml.createArrayNode().add(resolved.at("/content/application~1json/schema")))
        assertThat(schemaFactory.getSchema(schema).validate(body)).isEmpty()
    }

    private fun assertNativeError(response: ContractHttp.Result, status: Int) {
        assertThat(response.status).isEqualTo(status)
        assertSchema("/paths/~1pages/post/responses/$status", response.json)
        assertThat(response.json["requestId"].asText()).isEqualTo(response.header("X-Request-Id"))
    }

    companion object {
        private val yaml = ObjectMapper(YAMLFactory())
        private val spec = yaml.readTree(File("wiki_design/specs/openapi.yaml"))
        private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)

        @JvmStatic
        @DynamicPropertySource
        fun database(properties: DynamicPropertyRegistry) = postgresProperties(properties)
    }
}
