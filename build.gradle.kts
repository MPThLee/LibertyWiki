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
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.networknt:json-schema-validator:1.5.9")
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    from("web/dist") { into("static") }
}

tasks.test {
    useJUnitPlatform { excludeTags("postgres", "mediawiki") }
}

fun registerContractTest(taskName: String, tag: String) = tasks.register<Test>(taskName) {
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags(tag) }
    doNotTrackState("The contract test depends on a live test instance.")
    shouldRunAfter(tasks.test)
}

registerContractTest("postgresTest", "postgres")
registerContractTest("mediaWikiTest", "mediawiki")

tasks.bootJar {
    doFirst {
        check(file("web/dist/index.html").isFile) { "Build the web client with npm run build --prefix web first." }
    }
}
