import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import com.github.davidmc24.gradle.plugin.avro.GenerateAvroJavaTask

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.compose") version "1.6.11"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    // Plugin Avro pour générer les classes Java/POJO à partir des .avsc
    id("com.github.davidmc24.gradle.plugin.avro") version "1.5.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    // Dépôt nécessaire pour certains artefacts de Compose Multiplatform / Compose Desktop
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    // Confluent packages (si besoin) - on laisse mavenCentral en tête, mais on peut ajouter Confluent si nécessaire
    maven("https://packages.confluent.io/maven/")
}

dependencies {
    // Compose Desktop (version fournie par le plugin)
    implementation(compose.desktop.currentOs)

    // Coroutines Swing pour le Main dispatcher sur Desktop
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")

    // KotlinX Serialization JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Kafka / Confluent / Jackson nécessaires pour le backend
    implementation("org.apache.kafka:kafka-clients:3.6.1")
    implementation("io.confluent:kafka-avro-serializer:7.4.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")

    testImplementation(kotlin("test"))
    // Data faker for generating fake players/publishers in Java service
    implementation("net.datafaker:datafaker:1.7.0")
}

// Configuration du plugin Avro : (utilise la valeur par défaut du plugin)
// Si vous souhaitez utiliser le dossier racine `/avro`, on peut le reconfigurer
// via l'extension du plugin au lieu de manipuler directement la tâche.

// S'assurer que le dossier généré par Avro est inclus dans les sources Java/Kotlin
// Le plugin génère habituellement dans build/generated-main-avro-java ou similaire. On ajoute
// quelques dossiers communs pour être sûrs qu'ils sont inclus.
sourceSets {
    val main by getting {
        java.srcDir("build/generated-main-avro-java")
        java.srcDir("build/generated-src/avro/main/java")
    }
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
