// SPDX-License-Identifier: AGPL-3.0-only
plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21"
    id("org.springframework.boot") version "3.5.16"
}

repositories { mavenCentral() }

kotlin { jvmToolchain(21) }

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client")
    runtimeOnly("org.flywaydb:flyway-mysql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.networknt:json-schema-validator:1.5.9")
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    from("web/dist") { into("static") }
}

tasks.test {
    useJUnitPlatform { excludeTags("database", "mediawiki") }
}

fun registerContractTest(taskName: String, tag: String) = tasks.register<Test>(taskName) {
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags(tag) }
    maxParallelForks = 1
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
    doNotTrackState("The contract test depends on a live test instance.")
    shouldRunAfter(tasks.test)
}

registerContractTest("postgresTest", "database")
registerContractTest("mariaDbTest", "database")
registerContractTest("mediaWikiTest", "mediawiki")

listOf("postgresTest" to "jdbc:postgresql:", "mariaDbTest" to "jdbc:mariadb:").forEach { (task, prefix) ->
    tasks.named<Test>(task) {
        doFirst {
            check(System.getenv("WIKI_TEST_DB_URL")?.startsWith(prefix) == true) {
                "$task requires WIKI_TEST_DB_URL to start with $prefix and point to a disposable database."
            }
        }
    }
}

tasks.bootJar {
    doFirst {
        check(file("web/dist/index.html").isFile) { "Build the web client with npm run build --prefix web first." }
    }
}
