plugins {
    application
    kotlin("jvm") version "2.2.21"
    id("io.ktor.plugin") version "2.3.11"
}

group = "uk.ac.leeds.comp2850"
version = "1.1-SNAPSHOT"

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.11"
val logbackVersion = "1.4.14"
val pebbleVersion = "3.2.2"

dependencies {
    // Ktor server core
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-core:2.3.12")  
    implementation("io.ktor:ktor-server-netty:2.3.12")
    implementation("io.pebbletemplates:pebble:3.2.2")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // Pebble templating
    implementation("io.pebbletemplates:pebble:$pebbleVersion")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // CSV handling
    implementation("org.apache.commons:commons-csv:1.10.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.2.21") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-test-junit")
    }
}

application {
    mainClass.set("MainKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaExec> {
    // Enable development mode for hot reload
    systemProperty("io.ktor.development", "true")
}

kotlin {
    jvmToolchain(21)
}
