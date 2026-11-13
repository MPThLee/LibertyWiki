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

@Tag("database")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "wiki.pages.anonymous-read=true",
    "wiki.pages.anonymous-edit=true",
    "wiki.pages.anonymous-create=false",
    "wiki.http.operator-token=$operatorToken",
])
class PageWritePolicyTest {
    @LocalServerPort private var port: Int = 0

    @Test
    fun `anonymous edit permission alone cannot create a page through any surface`() {
        val http = ContractHttp("http://127.0.0.1:$port")
        val existing = "Public-${UUID.randomUUID()}"
        val missing = "Denied-${UUID.randomUUID()}"
        val created = http.nativeCreate(existing, "readable")
        assertThat(created.status).isEqualTo(201)
        assertThat(http.get("/api/v1/pages/by-title/$existing").status).isEqualTo(200)
        assertThat(http.nativeCreate(missing, "source", null).status).isEqualTo(403)
        assertThat(http.restCreate(missing, "source").status).isEqualTo(403)
        assertThat(http.actionCreate(missing, "source").json.at("/error/code").asText()).isEqualTo("permissiondenied")
        assertThat(http.get("/api/v1/pages/by-title/$missing").status).isEqualTo(404)

        val path = "/api/v1/pages/${created.json["id"].asText()}/revisions"
        val edit = http.postJson(path,
            mapOf("baseRevisionId" to created.json["currentRevisionId"].asText(), "source" to "Anonymous edit"))
        assertThat(edit.status).isEqualTo(201)
        val compatBase = http.restRead(existing).json.at("/latest/id").asLong()
        val restEdit = http.restEdit(existing, "Anonymous REST edit", compatBase)
        assertThat(restEdit.status).isEqualTo(200)
        val actionEdit = http.actionEdit("title" to existing, "Anonymous Action edit", restEdit.json.at("/latest/id").asLong())
        assertThat(actionEdit.json.at("/edit/result").asText()).isEqualTo("Success")
        assertThat(http.restRead(existing).json["source"].asText()).isEqualTo("Anonymous Action edit")
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun database(properties: DynamicPropertyRegistry) = databaseProperties(properties)
    }
}

@Tag("database")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "wiki.pages.anonymous-read=true",
    "wiki.pages.anonymous-edit=false",
    "wiki.pages.anonymous-create=true",
    "wiki.http.operator-token=$operatorToken",
])
class PageEditDeniedTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `anonymous users who can read cannot edit through any surface`() {
        val http = ContractHttp("http://127.0.0.1:$port")
        val title = "Read-only-${UUID.randomUUID()}"
        val created = http.nativeCreate(title, "Public source").json
        val path = "/api/v1/pages/${created["id"].asText()}"
        val compatBase = http.restRead(title).json.at("/latest/id").asLong()
        assertThat(compatBase).isPositive()
        assertThat(http.get(path).json).isEqualTo(created)
        val before = counts()
        val native = http.postJson("$path/revisions",
            mapOf("baseRevisionId" to created["currentRevisionId"].asText(), "source" to "Anonymous edit"))
        assertThat(native.status).isEqualTo(403)
        assertThat(http.restEdit(title, "Anonymous REST edit", compatBase).status).isEqualTo(403)
        assertThat(http.actionEdit("title" to title, "Anonymous Action edit", compatBase)
            .json.at("/error/code").asText()).isEqualTo("permissiondenied")
        assertThat(http.get(path).json).isEqualTo(created)
        assertThat(counts()).isEqualTo(before)
    }

    @Test
    fun `anonymous readers cannot undo through native or action`() {
        val http = ContractHttp("http://127.0.0.1:$port")
        val title = "Undo-read-only-${UUID.randomUUID()}"
        val page = http.nativeCreate(title, "Parent source").json
        val path = "/api/v1/pages/${page["id"].asText()}"
        val edit = http.postJson("$path/revisions",
            mapOf("baseRevisionId" to page["currentRevisionId"].asText(), "source" to "New source"), operatorToken)
        assertThat(edit.status).isEqualTo(201)
        val edited = edit.json
        val target = http.restRead(title).json.at("/latest/id").asLong()
        val before = counts()
        val native = http.postJson("$path/undos", mapOf("revisionId" to edited["currentRevisionId"].asText()))
        assertThat(native.status).isEqualTo(403)
        assertThat(native.json["code"].asText()).isEqualTo("FORBIDDEN")
        assertThat(http.actionUndo("title" to title, target).json.at("/error/code").asText()).isEqualTo("permissiondenied")
        assertThat(http.get(path).json).isEqualTo(edited)
        assertThat(counts()).isEqualTo(before)
    }

    private fun counts() = listOf("wiki_revision", "wiki_audit", "wiki_outbox").map { table ->
        jdbc.queryForObject("SELECT count(*) FROM $table", Long::class.javaObjectType)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun database(properties: DynamicPropertyRegistry) = databaseProperties(properties)
    }
}
