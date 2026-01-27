# 🎮 Steam Project - Plateforme de Gestion de Jeux Vidéo

### Lancer le projet
# Steam Project — Guide d'installation et lancement (pas-à-pas)

Ce document explique comment configurer une machine de développement et lancer l'interface graphique (Compose Desktop) ainsi que l'infrastructure dépendante (Kafka, Schema Registry, Postgres).

Prérequis rapides
- Git (pour cloner le dépôt)
- Java 21 (JDK) — obligatoire pour compiler et exécuter
- Docker & Docker Compose (pour lancer Kafka / Schema Registry / Postgres localement)
- Windows: utilisez `gradlew.bat`; Unix/macOS: `./gradlew`

Table des matières
- **Installation JDK**
- **Cloner le projet**
- **Vérifications rapides**
- **Démarrer l'infrastructure Docker**
- **Lancer l'interface (dev)**
- **Construire un artefact**
- **Dépannage & conseils**
- **CI / Distribution (suggestions)**

1) Installation JDK 21
-- Vérifier la version installée:
```bash
java -version
```
-- Vous devez voir `java 21` ou équivalent. Si non installé :
- Windows: installer Temurin/Adoptium, Azul ou Oracle JDK 21 et définir `JAVA_HOME`.
- macOS: `brew install --cask temurin` ou installer via l'installateur officiel.
- Linux: utiliser votre gestionnaire de paquets ou SDKMAN (`sdk install java 21-open`).

2) Cloner le dépôt
```bash
git clone <url-du-repo>
cd <nom-du-repo>
```

3) Vérifications rapides dans le repo
- Vérifier la présence du wrapper Gradle (`gradlew`, `gradlew.bat`) et du fichier `build.gradle.kts`.
- Confirmer le point d'entrée de l'application: `src/main/kotlin/Main.kt` (mainClass = `org.example.MainKt`).

4) Démarrer l'infrastructure 
- Démarrer les services Docker requis (Kafka, Schema Registry, Postgres):
```bash
docker-compose up -d
docker-compose ps
```
- Vérifier que les ports sont ouverts (`9092` pour Kafka, `8081` pour Schema Registry, `5432` pour Postgres).


5) Lancer l'interface en mode développement
- Sur Windows (PowerShell):
```powershell
# SteamProject — Démarrage et exécution

Ce README explique comment préparer la machine, démarrer l'infrastructure (Kafka / Schema Registry), lancer les services de projection, produire des événements et exécuter l'UI.

**Prérequis**

- JDK 17+ (ou la version requise par le projet) disponible dans `PATH`.
- Docker & Docker Compose (recommandé pour Kafka + Schema Registry).
- Utiliser le wrapper Gradle fourni (`gradlew` / `gradlew.bat`).

Ports par défaut

- Kafka: `localhost:9092`
- Schema Registry: `http://localhost:8081`
- Services de projection REST: `http://localhost:8080`

1) Démarrer l'infrastructure (Docker)

```bash
docker-compose up -d
docker-compose ps
```

2) Compiler / générer les classes Avro (si nécessaire)

```powershell
.\gradlew.bat generateAvroJava classes --no-daemon
# ou Unix/macOS
./gradlew generateAvroJava classes --no-daemon
```

3) Lancer les services de projection (Kafka Streams + REST)

```powershell
.\gradlew.bat runPurchaseRest --no-daemon
# ou, selon la tâche exposée dans votre build:
.\gradlew.bat runStreamsRest --no-daemon
```

Le service REST expose par défaut les endpoints suivants :

- `GET /api/players`
- `GET /api/players/{playerId}/library`
- `GET /api/catalog`
- `GET /api/publishers-list`

Test rapide de l'API :

```powershell
curl.exe -sS "http://localhost:8080/api/players"
curl.exe -sS "http://localhost:8080/api/players/player-001/library"
```

4) Produire des événements via les outils d'administration

Les outils sont organisés dans :

- `tools/admin/` — utilitaires d'admin (conservés pour usage opérationnel)
- `tools/test/` — producteurs de test (copies conservées pour tests manuels)

Exemple — créer un joueur de test :

```powershell
$Env:TEST_PLAYER_ID = "player-001"
.\gradlew.bat runCreatePlayer --no-daemon
```

Exemple — envoyer un achat de test :

```powershell
$Env:TEST_PLAYER_ID = "player-001"
.\gradlew.bat runTestPurchaseForPlayer --no-daemon
```

5) Lancer l'UI (Compose Desktop)

```powershell
.\gradlew.bat run --no-daemon
```

L'UI interroge par défaut le service de projection (`http://localhost:8080`). Pour forcer l'utilisation des mocks, passez la propriété `force.mock=true` ou `-Pforce.mock=true`.

6) Exécuter la suite de tests

```powershell
.\gradlew.bat test
```

7) Configuration avancée / variables d'environnement

Vous pouvez surcharger les endpoints Kafka / Schema Registry via des variables d'environnement ou propriétés Gradle/JVM :

```powershell
$Env:KAFKA_BOOTSTRAP_SERVERS = "broker:9092"
$Env:SCHEMA_REGISTRY_URL = "http://schema-registry:8081"
.\gradlew.bat runPurchaseRest
```

ou via options Gradle :

```powershell
.\gradlew.bat runPurchaseRest -Pkafka.bootstrap.servers=broker:9092 -Pschema.registry.url=http://schema-registry:8081
```

8) Emplacements utiles

- Schémas Avro: `src/main/avro/`
- Services de projection / REST: `src/main/java/org/steamproject/infra/kafka/streams` et `src/main/java/org/steamproject/infra/kafka/consumer`
- UI Kotlin: `src/main/kotlin/`
- Outils d'admin / test: `tools/admin/` et `tools/test/`

9) Dépannage rapide

- L'UI est vide → vérifier `http://localhost:8080/api/players` et que les events arrivent sur Kafka.
- Erreurs Avro → vérifier que le Schema Registry est accessible et que les schémas ont été générés.
- Problèmes de build liés à OneDrive → déplacer le projet hors des dossiers synchronisés si vous rencontrez des erreurs de fichier verrouillé.

Besoin d'automatiser le démarrage complet (Docker + services + UI) ? Je peux ajouter des scripts PowerShell/Batch pour lancer tout en une commande.
```



7) Vérifier la projection depuis la ligne de commande (ou via l'UI) :



```powershell

curl.exe http://localhost:8080/api/players/player-1/library


