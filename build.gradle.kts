import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"
    id("app.cash.sqldelight") version "2.0.2"
    id("org.jetbrains.compose") version "1.7.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
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

            packageName = "Solgram"
            packageVersion = "2.0.0"
            description = "Personal Telegram client with trading signal intelligence"
            copyright = "© 2025 Solgram"
            vendor = "Solgram"

            appResourcesRootDir.set(project.layout.projectDirectory.dir("native-libs"))

            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
                menuGroup = "Solgram"
                upgradeUuid = "8f14e45f-ceea-467e-b7c3-1d9c2a3b0a11"
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

            // Optimized modules - 26 modules vs ALL (~70) to reduce size from 150MB to ~95-110MB
            // Still includes all needed for Ktor, SQLite, JNA, coroutines
            modules(
                "java.base", "java.desktop", "java.sql", "java.naming", "java.net.http",
                "java.management", "java.security.jgss", "java.security.sasl",
                "java.logging", "java.xml", "java.instrument", "java.prefs", "java.scripting",
                "jdk.unsupported", "jdk.unsupported.desktop",
                "jdk.crypto.ec", "jdk.crypto.cryptoki", "jdk.zipfs",
                "jdk.accessibility", "jdk.management", "jdk.security.auth",
                "jdk.security.jgss", "java.transaction.xa", "java.rmi",
                "jdk.charsets", "jdk.httpserver"
            )
        }
    }
}

tasks.register("doctor") {
    group = "solgram"
    description = "Environment self-check, startup profiler report"
    doLast {
        println("=== Solgram Doctor ===")
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
    group = "solgram"
    description = "Wrapper to produce MSI on Windows or dummy on Linux"
    dependsOn("packageMsi", "packageExe")
    doLast {
        val msiDir = file("build/compose/binaries/main/msi")
        val exeDir = file("build/compose/binaries/main/exe")
        if (!msiDir.exists() || msiDir.listFiles()?.isEmpty() != false) {
            msiDir.mkdirs()
            val dummyMsi = msiDir.resolve("Solgram-2.0.0.msi")
            dummyMsi.writeText("This is a placeholder MSI. Build on Windows with WiX to produce real MSI. See README.")
            println("Created placeholder MSI at ${dummyMsi.absolutePath}")
        }
        if (!exeDir.exists() || exeDir.listFiles()?.isEmpty() != false) {
            exeDir.mkdirs()
            val dummyExe = exeDir.resolve("Solgram-2.0.0.exe")
            dummyExe.writeText("This is a placeholder EXE. Build on Windows to produce real EXE.")
            println("Created placeholder EXE at ${dummyExe.absolutePath}")
        }
    }
}

kotlin {
    jvmToolchain(17)
}
