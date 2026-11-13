// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.adapter.persistence

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class DatabaseConnectionSettings {
    companion object {
        @Bean
        @JvmStatic
        fun databaseTimeouts(): BeanPostProcessor = object : BeanPostProcessor {
            override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
                if (bean is HikariDataSource) {
                    val url = checkNotNull(bean.jdbcUrl) { "A PostgreSQL or MariaDB JDBC URL is required." }
                    val urlOptions = url.substringAfter('?', "").split('&')
                        .map { it.substringBefore('=').lowercase() }.toSet()
                    fun defaultProperty(name: String, value: Any) {
                        if (name.lowercase() !in urlOptions && !bean.dataSourceProperties.containsKey(name)) {
                            bean.addDataSourceProperty(name, value)
                        }
                    }
                    when {
                        url.startsWith("jdbc:postgresql:") -> {
                            defaultProperty("connectTimeout", 3)
                            defaultProperty("socketTimeout", 10)
                            defaultProperty("options", "-c statement_timeout=5000 -c lock_timeout=4000")
                        }
                        url.startsWith("jdbc:mariadb:") -> {
                            defaultProperty("connectTimeout", 3000)
                            defaultProperty("socketTimeout", 10000)
                            defaultProperty("connectionTimeZone", "UTC")
                            defaultProperty("forceConnectionTimeZoneToSession", true)
                            if (bean.connectionInitSql == null) {
                                bean.connectionInitSql = "SET SESSION innodb_lock_wait_timeout=4, max_statement_time=5, " +
                                    "sql_mode=CONCAT_WS(',', @@SESSION.sql_mode, 'STRICT_ALL_TABLES')"
                            }
                        }
                        else -> error("Unsupported JDBC URL: expected PostgreSQL or MariaDB.")
                    }
                }
                return bean
            }
        }
    }
}
