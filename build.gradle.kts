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
    implementation("org.apache.kafka:kafka-streams:3.6.1")
    implementation("io.confluent:kafka-avro-serializer:7.4.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    // Jackson Kotlin module to support `jacksonObjectMapper()` and Kotlin generic readValue<T>()
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    // Reflection is required by some Jackson Kotlin features
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.22")
    // Simple SLF4J binding for development / readable logs
    implementation("org.slf4j:slf4j-simple:1.7.36")

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

// Test producers removed from main workflow. Use admin tools or tools/test folder.

// NOTE: Test-only runner tasks that previously sent synthetic or hardcoded
// events have been removed from the main workflow to keep the project
// event-driven with real ingestion. Move any Test*Producer utilities to
// a `tools/` or `admin/` folder if interactive runners are required.

tasks.register<JavaExec>("runPurchaseConsumer") {
    group = "application"
    description = "Run PurchaseEventConsumer to consume purchase.events"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.consumer.PlayerConsumerApp")
    dependsOn("generateAvroJava", "classes")
}

tasks.register<JavaExec>("runUserLibraryStreams") {
    group = "application"
    description = "Run Kafka Streams app for user-library projection"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.streams.UserLibraryStreams")
    dependsOn("generateAvroJava", "classes")
}

tasks.register<JavaExec>("runPublisherConsumer") {
    group = "application"
    description = "Run PublisherConsumer to consume game released events"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.consumer.PublisherConsumerApp")
    dependsOn("generateAvroJava", "classes")
}

tasks.register<JavaExec>("runPlatformConsumer") {
    group = "application"
    description = "Run PlatformConsumer to consume platform catalog events"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.consumer.PlatformConsumerApp")
    dependsOn("generateAvroJava", "classes")
}

// Test producers removed from main workflow. Use admin tools or tools/test folder.

tasks.register<JavaExec>("runEnsurePlayer") {
    group = "application"
    description = "Send PlayerCreatedEvent for a given player id (or default)"
    classpath = sourceSets["main"].runtimeClasspath
    // Admin tool moved to tools/admin package to keep admin utilities out of main artifact
    // Use the EnsurePlayerProducer present in main sources so task works in all workspaces
    mainClass.set("org.steamproject.infra.kafka.producer.EnsurePlayerProducer")
    dependsOn("generateAvroJava", "classes")
}

tasks.register<JavaExec>("runStreamsRest") {
    group = "application"
    description = "Run minimal REST service exposing the user-library projection"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.streams.StreamsRestService")
    dependsOn("generateAvroJava", "classes")
}

// Run the small Purchase REST service (uses Jackson on the runtime classpath)
tasks.register<JavaExec>("runPurchaseRest") {
    group = "application"
    description = "Run PurchaseRestService (HTTP endpoint for player library)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.consumer.PurchaseRestService")
    dependsOn("generateAvroJava", "classes")
}

// Convenience tasks for test producers located in tools/test
// Test producer tasks removed to enforce ingestion-driven data.

// Real producer runners
tasks.register<JavaExec>("runPublishGame") {
    group = "application"
    description = "Run real publisher producer app to emit GameReleasedEvent"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.producer.PublisherProducerApp")
    // Allow overriding target game via -PgameId when invoking Gradle
    val extra: Map<String, Any> = if (project.hasProperty("gameId")) mapOf("game.id" to project.property("gameId").toString()) else mapOf()
    systemProperties = extra
    dependsOn("generateAvroJava", "classes")
}

tasks.register<JavaExec>("runPublishEvent") {
    group = "application"
    description = "Run publisher events producer app to emit various publisher events (use -Devent.type)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.producer.PublisherEventsProducerApp")
    dependsOn("generateAvroJava", "classes")
}

// Convenience tasks to emit single event types to their dedicated topics
tasks.register<JavaExec>("runPublishGamePublished") {
    group = "application"
    description = "Publish a GamePublishedEvent (to game-published-events)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.producer.PublisherEventsProducerApp")
    systemProperties = mapOf("event.type" to "published")
    dependsOn("generateAvroJava", "classes")
}

tasks.register<JavaExec>("runPublishGameUpdated") {
    group = "application"
    description = "Publish a GameUpdatedEvent (to game-updated-events)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.producer.PublisherEventsProducerApp")
    systemProperties = mapOf("event.type" to "updated")
    dependsOn("generateAvroJava", "classes")
}

tasks.register<JavaExec>("runPublishPatch") {
    group = "application"
    description = "Publish a PatchPublishedEvent (to patch-published-events)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.producer.PublisherEventsProducerApp")
    // Allow overriding the target game id via -PgameId=... when invoking Gradle
    val extra: Map<String, Any> = if (project.hasProperty("gameId")) mapOf("game.id" to project.property("gameId").toString()) else mapOf()
    systemProperties = mapOf("event.type" to "patch") + extra
    dependsOn("generateAvroJava", "classes")
}

tasks.register<JavaExec>("runPublishDlc") {
    group = "application"
    description = "Publish a DlcPublishedEvent (to dlc-published-events)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.producer.PublisherEventsProducerApp")
    val extraDlc: Map<String, Any> = if (project.hasProperty("gameId")) mapOf("game.id" to project.property("gameId").toString()) else mapOf()
    systemProperties = mapOf("event.type" to "dlc") + extraDlc
    dependsOn("generateAvroJava", "classes")
}

tasks.register<JavaExec>("runPublishVersionDeprecated") {
    group = "application"
    description = "Publish a GameVersionDeprecatedEvent (to game-version-deprecated-events)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.producer.PublisherEventsProducerApp")
    val extraDep: Map<String, Any> = if (project.hasProperty("gameId")) mapOf("game.id" to project.property("gameId").toString()) else mapOf()
    systemProperties = mapOf("event.type" to "deprecate") + extraDep
    dependsOn("generateAvroJava", "classes")
}

tasks.register<JavaExec>("runPublishEditorResponded") {
    group = "application"
    description = "Publish an EditorRespondedToIncidentEvent (to editor-responded-events)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.producer.PublisherEventsProducerApp")
    val extraResp: Map<String, Any> = if (project.hasProperty("gameId")) mapOf("game.id" to project.property("gameId").toString()) else mapOf()
    systemProperties = mapOf("event.type" to "respond") + extraResp
    dependsOn("generateAvroJava", "classes")
}

tasks.register<JavaExec>("runSendPurchase") {
    group = "application"
    description = "Run real GamePurchaseProducerApp to emit GamePurchaseEvent"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.producer.GamePurchaseProducerApp")
    dependsOn("generateAvroJava", "classes")
}
