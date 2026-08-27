import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.jvm.tasks.Jar
import org.gradle.api.tasks.bundling.Zip

plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"
    id("app.cash.sqldelight") version "2.0.2"
    id("org.jetbrains.compose") version "1.7.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("app.cash.sqldelight:sqlite-driver:2.0.2")
    implementation("app.cash.sqldelight:coroutines-extensions:2.0.2")
    implementation("io.ktor:ktor-server-core:2.3.12")
    implementation("io.ktor:ktor-server-cio:2.3.12")
    implementation("io.ktor:ktor-server-websockets:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-server-cors:2.3.12")
    implementation("io.ktor:ktor-server-auth:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("org.slf4j:slf4j-simple:2.0.12")
    implementation("com.github.kwhat:jnativehook:2.2.2")
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-property:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("io.ktor:ktor-server-test-host:2.3.12")
}

sqldelight {
    databases {
        create("SolgramDb") {
            packageName.set("com.solgram.db")
        }
    }
}

detekt {
    config.setFrom(files("detekt-config.yml"))
    buildUponDefaultConfig = true
}

compose.desktop {
    application {
        mainClass = "com.solgram.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb, TargetFormat.AppImage)

            packageName = "EVMGRAM"
            packageVersion = "2.0.0"
            description = "EVM-focused Telegram client with trading signal intelligence"
            copyright = "© 2025 EVMGRAM"
            vendor = "EVMGRAM"

            appResourcesRootDir.set(project.layout.projectDirectory.dir("native-libs"))

            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
                menuGroup = "EVMGRAM"
                upgradeUuid = "9f25f56f-dffb-578f-c8d4-2e0d3b4c1b22" // New UUID for EVMGRAM
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = false
            }

            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
            }

            macOS {
                iconFile.set(project.file("src/main/resources/icon.icns"))
                bundleID = "com.solgram.app"
            }

            // MINIMAL modules for smallest possible size (~70-85 MB)
            // Tested minimal that still launches: 13 modules
            // If launch fails, add back: java.naming, jdk.crypto.cryptoki, java.management etc
            modules(
                "java.base", "java.desktop", "java.sql", "java.logging", "java.xml",
                "java.net.http", "java.naming", "java.prefs",
                "jdk.unsupported", "jdk.crypto.ec", "jdk.zipfs", "jdk.charsets",
                "java.management"
            )
        }
    }
}

tasks.register("doctor") {
    group = "evmgram"
    description = "Environment self-check, startup profiler report"
    doLast {
        println("=== EVMGRAM Doctor ===")
        println("Java: ${System.getProperty("java.version")} / ${System.getProperty("java.vendor")}")
        println("OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}")
        println("Gradle: ${gradle.gradleVersion}")
        println("Compose: 1.7.0")
        println("TDLib: checking native-libs/")
        val nativeLibs = file("native-libs").listFiles()?.map { it.name } ?: emptyList()
        println("Native libs: $nativeLibs")
        println("SQLite: ${file("src/main/sqldelight/com/solgram/db/SolgramDb.sq").exists()}")
        println("Doctor check complete.")
    }
}

tasks.register("packageMsiWrapper") {
    group = "evmgram"
    description = "Wrapper to produce MSI on Windows or dummy on Linux"
    dependsOn("packageMsi", "packageExe")
    doLast {
        val msiDir = file("build/compose/binaries/main/msi")
        val exeDir = file("build/compose/binaries/main/exe")
        if (!msiDir.exists() || msiDir.listFiles()?.isEmpty() != false) {
            msiDir.mkdirs()
            val dummyMsi = msiDir.resolve("EVMGRAM-2.0.0.msi")
            dummyMsi.writeText("This is a placeholder MSI. Build on Windows with WiX to produce real MSI. See README.")
            println("Created placeholder MSI at ${dummyMsi.absolutePath}")
        }
        if (!exeDir.exists() || exeDir.listFiles()?.isEmpty() != false) {
            exeDir.mkdirs()
            val dummyExe = exeDir.resolve("EVMGRAM-2.0.0.exe")
            dummyExe.writeText("This is a placeholder EXE. Build on Windows to produce real EXE.")
            println("Created placeholder EXE at ${dummyExe.absolutePath}")
        }
    }
}

// Ultra-portable: fat jar + run scripts, requires Java 17 installed, ~35-50 MB
tasks.register<Jar>("ultraPortableJar") {
    group = "evmgram"
    description = "Create ultra-portable fat JAR (requires Java 17 installed) - smallest size ~35-50 MB"
    archiveBaseName.set("EVMGRAM")
    archiveVersion.set("2.0.0-ultra-portable")
    archiveClassifier.set("")
    from(sourceSets["main"].output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.solgram.app.MainKt"
    }
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.register<Zip>("ultraPortableZip") {
    group = "evmgram"
    description = "Create ultra-portable ZIP (jar + launchers) - smallest, requires Java 17"
    archiveBaseName.set("EVMGRAM")
    archiveVersion.set("2.0.0-ultra-portable")
    archiveClassifier.set("")
    destinationDirectory.set(file("build"))
    
    dependsOn("ultraPortableJar")
    
    from(tasks.named("ultraPortableJar")) {
        into("")
    }
    from(file("native-libs")) {
        into("native-libs")
    }
    
    doLast {
        val buildDir = file("build")
        val batFile = buildDir.resolve("run.bat")
        batFile.writeText("""@echo off
echo Starting EVMGRAM 2.0.0 Ultra-Portable...
echo Requires Java 17 installed (java -version)
java -jar EVMGRAM-2.0.0-ultra-portable.jar --debug
pause
""")
        val shFile = buildDir.resolve("run.sh")
        shFile.writeText("""#!/bin/bash
echo "Starting EVMGRAM 2.0.0 Ultra-Portable..."
java -jar EVMGRAM-2.0.0-ultra-portable.jar --debug
""")
        val readmeFile = buildDir.resolve("README-PORTABLE.txt")
        readmeFile.writeText("""
EVMGRAM 2.0.0 Ultra-Portable (~35-50 MB)
========================================
Smallest portable version - requires Java 17 installed!

Requirements:
- Java 17 installed (https://adoptium.net/)
- Check: java -version should show 17+

How to run:
- Windows: Double-click run.bat or run: java -jar EVMGRAM-2.0.0-ultra-portable.jar
- Linux/Mac: ./run.sh or java -jar EVMGRAM-2.0.0-ultra-portable.jar

Logs: %APPDATA%/EVMGRAM/evmgram.log

For full portable with bundled JVM (~70-85 MB):
Use EVMGRAM-2.0.0-portable.zip

For installer:
Use EVMGRAM-2.0.0.msi
""".trimIndent())
    }
    
    from(file("build/run.bat")) { into("") }
    from(file("build/run.sh")) { into("") }
    from(file("build/README-PORTABLE.txt")) { into("") }
}

kotlin {
    jvmToolchain(17)
}
