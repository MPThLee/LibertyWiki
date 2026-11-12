// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class LibertyWiki

fun main(args: Array<String>) {
    runApplication<LibertyWiki>(*args)
}
