// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.contract

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID

@Tag("postgres")
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
        assertThat(http.nativeCreate(existing, "readable").status).isEqualTo(201)
        assertThat(http.get("/api/v1/pages/by-title/$existing").status).isEqualTo(200)
        assertThat(http.nativeCreate(missing, "source", null).status).isEqualTo(403)
        assertThat(http.restCreate(missing, "source").status).isEqualTo(403)
        assertThat(http.actionCreate(missing, "source").json.at("/error/code").asText()).isEqualTo("permissiondenied")
        assertThat(http.get("/api/v1/pages/by-title/$missing").status).isEqualTo(404)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun database(properties: DynamicPropertyRegistry) = postgresProperties(properties)
    }
}
