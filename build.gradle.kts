import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.compose") version "1.6.11"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    // Dépôt nécessaire pour certains artefacts de Compose Multiplatform / Compose Desktop
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    // Compose Desktop (version fournie par le plugin)
    implementation(compose.desktop.currentOs)

    // Coroutines Swing pour le Main dispatcher sur Desktop
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")

    // KotlinX Serialization JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        // Main class doit être le FQCN (Main.kt est dans le package org.example)
        mainClass = "org.example.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "MyApp"
            packageVersion = "1.0.0"    // version stable obligatoire pour DMG/MSI/DEB
        }
    }
}
