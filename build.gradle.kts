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

val cmakeConfigure by tasks.registering(Exec::class) {
    group = "native"
    description = "Configure CMake build for the JNI bridge"
    inputs.file("CMakeLists.txt")
    inputs.file("src/main/cpp/llama_jni.cpp")
    outputs.dir("build/cmake")

    commandLine("cmake", "-S", ".", "-B", "build/cmake", "-DCMAKE_BUILD_TYPE=Release")
}

val cmakeBuild by tasks.registering(Exec::class) {
    group = "native"
    description = "Build the JNI bridge shared library"
    dependsOn(cmakeConfigure)
    inputs.dir("src/main/cpp")
    outputs.dir("build/cmake")

    commandLine("cmake", "--build", "build/cmake", "--parallel")

    doLast {
        val osName = System.getProperty("os.name").lowercase()
        val libFileName = when {
            osName.contains("win") -> "localgemma.dll"
            osName.contains("mac") -> "liblocalgemma.dylib"
            else -> "liblocalgemma.so"
        }

        val source = file("build/cmake/$libFileName")
        val destDir = file("build/resources/main/native")
        destDir.mkdirs()
        val dest = File(destDir, libFileName)

        if (source.exists()) {
            source.copyTo(dest, overwrite = true)
            println("Copied native library to ${dest.absolutePath}")
        } else {
            // Some platforms may put it in a sub-directory
            val found = fileTree("build/cmake").matching {
                include("**/$libFileName")
            }.files.firstOrNull()
            if (found != null) {
                found.copyTo(dest, overwrite = true)
                println("Copied native library from ${found.absolutePath} to ${dest.absolutePath}")
            } else {
                throw GradleException("Native library $libFileName not found after CMake build")
            }
        }
    }
}

tasks.processResources {
    dependsOn(cmakeBuild)
}
