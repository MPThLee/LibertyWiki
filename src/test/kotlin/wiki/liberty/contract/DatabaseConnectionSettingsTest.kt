// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.contract

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.sql.Connection
import wiki.liberty.adapter.persistence.DatabaseConnectionSettings
import wiki.liberty.LibertyWiki

@Tag("database")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DatabaseConnectionSettingsTest {
    @Autowired private lateinit var dataSource: HikariDataSource

    @Test
    fun `database sessions use bounded timeouts and required invariants by default`() {
        val maria = dataSource.jdbcUrl.startsWith("jdbc:mariadb:")
        assertThat(dataSource.dataSourceProperties["connectTimeout"].toString()).isEqualTo(if (maria) "3000" else "3")
        assertThat(dataSource.dataSourceProperties["socketTimeout"].toString()).isEqualTo(if (maria) "10000" else "10")
        dataSource.connection.use { connection ->
            assertThat(connection.transactionIsolation).isEqualTo(Connection.TRANSACTION_READ_COMMITTED)
            if (connection.metaData.databaseProductName == "MariaDB") {
                assertThat(connection.scalar("SELECT @@session.innodb_lock_wait_timeout").toInt()).isEqualTo(4)
                assertThat(connection.scalar("SELECT @@session.max_statement_time").toDouble()).isEqualTo(5.0)
                assertThat(connection.scalar("SELECT @@session.time_zone")).isEqualTo("+00:00")
                val modes = connection.scalar("SELECT @@session.sql_mode").split(',')
                assertThat(modes).containsAnyOf("STRICT_TRANS_TABLES", "STRICT_ALL_TABLES")
            } else {
                assertThat(connection.scalar("SHOW statement_timeout")).isEqualTo("5s")
                assertThat(connection.scalar("SHOW lock_timeout")).isEqualTo("4s")
            }
        }
    }

    @Test
    fun `MariaDB migration preserves text charset title collation and length constraints`() {
        dataSource.connection.use { connection ->
            assumeTrue(connection.metaData.databaseProductName == "MariaDB")
            connection.createStatement().use { statement ->
                statement.executeQuery("""
                    SELECT table_name, column_name, character_set_name
                    FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name IN ('wiki_page', 'wiki_revision', 'wiki_audit', 'wiki_outbox',
                                         'wiki_revision_source_restriction')
                      AND character_set_name IS NOT NULL
                """.trimIndent()).use { rows ->
                    val charsets = mutableMapOf<String, String>()
                    while (rows.next()) charsets["${rows.getString(1)}.${rows.getString(2)}"] = rows.getString(3)
                    assertThat(charsets.keys).contains(
                        "wiki_page.normalized_title", "wiki_revision.source", "wiki_revision.source_sha256",
                        "wiki_revision.summary", "wiki_revision.actor", "wiki_audit.event_type", "wiki_audit.actor",
                        "wiki_outbox.event_type", "wiki_outbox.actor",
                    )
                    assertThat(charsets.values).containsOnly("utf8mb4")
                }
            }
            assertThat(connection.scalar("""
                SELECT collation_name FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'wiki_page' AND column_name = 'normalized_title'
            """.trimIndent())).isEqualTo("utf8mb4_nopad_bin")
            val summaryCheck = connection.scalar("""
                SELECT check_clause FROM information_schema.check_constraints
                WHERE constraint_schema = DATABASE() AND constraint_name = 'wiki_revision_summary_length'
            """.trimIndent()).lowercase()
            assertThat(summaryCheck).contains("char_length", "summary", "500")
            val hashCheck = connection.scalar("""
                SELECT check_clause FROM information_schema.check_constraints
                WHERE constraint_schema = DATABASE() AND constraint_name = 'wiki_revision_sha256_length'
            """.trimIndent()).lowercase()
            assertThat(hashCheck).contains("char_length", "source_sha256", "64")
        }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun database(properties: DynamicPropertyRegistry) = databaseProperties(properties)
    }
}

@Tag("database")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DatabaseConnectionOverridesTest {
    @Autowired private lateinit var dataSource: HikariDataSource

    @Test
    fun `explicit operator settings are applied to real sessions`() {
        val maria = dataSource.jdbcUrl.startsWith("jdbc:mariadb:")
        assertThat(dataSource.dataSourceProperties.getProperty("connectTimeout")).isEqualTo(if (maria) "8000" else "8")
        assertThat(dataSource.dataSourceProperties.getProperty("socketTimeout")).isEqualTo(if (maria) "12000" else "12")
        dataSource.connection.use { connection ->
            assertThat(connection.transactionIsolation).isEqualTo(Connection.TRANSACTION_READ_COMMITTED)
            if (connection.metaData.databaseProductName == "MariaDB") {
                assertThat(connection.scalar("SELECT @@session.innodb_lock_wait_timeout").toInt()).isEqualTo(6)
                assertThat(connection.scalar("SELECT @@session.max_statement_time").toDouble()).isEqualTo(7.0)
                assertThat(connection.scalar("SELECT @@session.time_zone")).isEqualTo("+00:00")
                assertThat(connection.scalar("SELECT @@session.sql_mode").split(','))
                    .containsAnyOf("STRICT_TRANS_TABLES", "STRICT_ALL_TABLES")
            } else {
                assertThat(connection.scalar("SHOW statement_timeout")).isEqualTo("7s")
                assertThat(connection.scalar("SHOW lock_timeout")).isEqualTo("6s")
            }
        }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun database(properties: DynamicPropertyRegistry) {
            databaseProperties(properties)
            val maria = System.getenv("WIKI_TEST_DB_URL").startsWith("jdbc:mariadb:")
            properties.add("spring.datasource.hikari.data-source-properties.connectTimeout") { if (maria) "8000" else "8" }
            properties.add("spring.datasource.hikari.data-source-properties.socketTimeout") { if (maria) "12000" else "12" }
            if (maria) {
                properties.add("spring.datasource.hikari.connection-init-sql") {
                    "SET SESSION innodb_lock_wait_timeout=6, max_statement_time=7, sql_mode='STRICT_TRANS_TABLES'"
                }
            } else {
                properties.add("spring.datasource.hikari.data-source-properties.options") {
                    "-c statement_timeout=7000 -c lock_timeout=6000"
                }
            }
        }
    }
}

private fun Connection.scalar(sql: String): String = createStatement().use { statement ->
    statement.executeQuery(sql).use { result ->
        check(result.next())
        result.getString(1)
    }
}

class UnsupportedDatabaseConnectionSettingsTest {
    @Test
    fun `unsupported JDBC URL fails explicitly before a connection is attempted`() {
        val dataSource = HikariDataSource().apply { jdbcUrl = "jdbc:mysql://localhost/wiki" }
        assertThatThrownBy { DatabaseConnectionSettings.databaseTimeouts().postProcessBeforeInitialization(dataSource, "dataSource") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Unsupported JDBC URL")
    }
}

@Tag("database")
class MariaDbUnsafeSessionTest {
    @Test
    fun `startup rejects a non-strict MariaDB session`() {
        val url = System.getenv("WIKI_TEST_DB_URL")
        assumeTrue(url != null && url.startsWith("jdbc:mariadb:"))
        assertThatThrownBy {
            SpringApplicationBuilder(LibertyWiki::class.java).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=$url",
                "--spring.datasource.username=${System.getenv("WIKI_TEST_DB_USER") ?: "libertywiki"}",
                "--spring.datasource.password=${System.getenv("WIKI_TEST_DB_PASSWORD") ?: ""}",
                "--spring.datasource.hikari.connection-init-sql=SET SESSION sql_mode=''",
            ).close()
        }.hasRootCauseMessage("MariaDB requires a strict SQL mode for transactional tables.")
    }
}
