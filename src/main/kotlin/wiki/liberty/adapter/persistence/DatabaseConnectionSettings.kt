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
                    when {
                        bean.jdbcUrl.startsWith("jdbc:postgresql:") -> {
                            bean.addDataSourceProperty("connectTimeout", 3)
                            bean.addDataSourceProperty("socketTimeout", 10)
                            bean.addDataSourceProperty("options", "-c statement_timeout=5000 -c lock_timeout=4000")
                        }
                        bean.jdbcUrl.startsWith("jdbc:mariadb:") -> {
                            bean.addDataSourceProperty("connectTimeout", 3000)
                            bean.addDataSourceProperty("socketTimeout", 10000)
                            bean.addDataSourceProperty("connectionTimeZone", "UTC")
                            bean.addDataSourceProperty("forceConnectionTimeZoneToSession", true)
                            bean.connectionInitSql = "SET SESSION innodb_lock_wait_timeout=4, max_statement_time=5"
                        }
                    }
                }
                return bean
            }
        }
    }
}
