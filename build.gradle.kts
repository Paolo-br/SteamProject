import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import com.github.davidmc24.gradle.plugin.avro.GenerateAvroJavaTask

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.compose") version "1.6.11"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("com.github.davidmc24.gradle.plugin.avro") version "1.5.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://packages.confluent.io/maven/")
}

dependencies {
    implementation(compose.desktop.currentOs)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("org.apache.kafka:kafka-clients:3.6.1")
    implementation("org.apache.kafka:kafka-streams:3.6.1")
    implementation("io.confluent:kafka-avro-serializer:7.4.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.22")
    implementation("org.slf4j:slf4j-simple:1.7.36")

    testImplementation(kotlin("test"))
    implementation("net.datafaker:datafaker:1.7.0")
}


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
        mainClass = "org.example.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "MyApp"
            packageVersion = "1.0.0"    
        }
    }
}



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


tasks.register<JavaExec>("runCreatePlayer") {
    group = "application"
    description = "Send PlayerCreatedEvent for a given player id (or default)"
    classpath = sourceSets["main"].runtimeClasspath
    // Admin tool moved to tools/admin; use the combined `runPlayerProducer` task.
    mainClass.set("org.steamproject.infra.kafka.producer.PlayerProducerApp")
    dependsOn("generateAvroJava", "classes")
}

tasks.register<JavaExec>("runStreamsRest") {
    group = "application"
    description = "Run minimal REST service exposing the user-library projection"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.streams.StreamsRestService")
    dependsOn("generateAvroJava", "classes")
}

tasks.register<JavaExec>("runPurchaseRest") {
    group = "application"
    description = "Run PurchaseRestService (HTTP endpoint for player library)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.consumer.PurchaseRestService")
    dependsOn("generateAvroJava", "classes")
}

tasks.register<JavaExec>("runPublishGame") {
    group = "application"
    description = "Run real publisher producer app to emit GameReleasedEvent"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.producer.PublisherProducerApp")
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


tasks.register<JavaExec>("runPlayerProducer") {
    group = "application"
    description = "Run PlayerProducerApp (mode=create|purchase via -Dmode)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.producer.PlayerProducerApp")
    // Allow passing `-Pmode=...` to forward the mode into the forked JVM
    val props = mutableMapOf<String, Any>()
    if (project.hasProperty("mode")) props["mode"] = project.property("mode").toString()
    if (project.hasProperty("test.player.id")) props["test.player.id"] = project.property("test.player.id").toString()
    if (project.hasProperty("test.game.id")) props["test.game.id"] = project.property("test.game.id").toString()
    if (project.hasProperty("test.player.username")) props["test.player.username"] = project.property("test.player.username").toString()
    if (project.hasProperty("test.game.name")) props["test.game.name"] = project.property("test.game.name").toString()
    systemProperties = props
    dependsOn("generateAvroJava", "classes")
}

tasks.register<JavaExec>("runPlayerEventsConsumer") {
    group = "application"
    description = "Run PlayerEventsConsumer to consume player-generated events"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.steamproject.infra.kafka.consumer.PlayerEventsConsumerApp")
    dependsOn("generateAvroJava", "classes")
}

