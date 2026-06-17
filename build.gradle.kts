plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}

group = "com.localgemma"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:5.0.3")
    implementation("io.ktor:ktor-server-core:3.1.1")
    implementation("io.ktor:ktor-server-netty:3.1.1")
    implementation("io.ktor:ktor-server-content-negotiation:3.1.1")
    implementation("io.ktor:ktor-server-cors:3.1.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("org.slf4j:slf4j-api:2.0.16")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "com.localgemma.cli.LocalGemmaKt"
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.localgemma.cli.LocalGemmaKt"
    }
}
