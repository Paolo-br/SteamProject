# 🎮 Steam Project - Plateforme de Gestion de Jeux Vidéo

### 🏃 Lancer le projet
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
# depuis la racine du projet
.\\gradlew.bat run
```
- Sur macOS / Linux:
```bash
./gradlew run
```
- Le wrapper Gradle télécharge les dépendances et compile le projet automatiquement (pas besoin d'installer Gradle globalement).

6) Construire un artefact exécutable
- Build standard (JAR + tests):
```bash
./gradlew build
```
7) Dépannage courant
- Erreur "Unsupported class file major version" → mauvaise version de Java (installer JDK 21).
- Build bloqué sur le téléchargement de dépendances → vérifier connexion réseau / proxy et dépôts configurés dans `build.gradle.kts`.
- Problèmes avec OneDrive (chemins synchronisés) → déplacer le projet hors de dossiers synchronisés (OneDrive) si vous obtenez des erreurs de fichier verrouillé.
- Docker UI inaccessible depuis conteneur (Windows) → exécuter l'UI localement via `./gradlew run` ; exécution GUI dans Docker nécessite WSL2+X server ou VNC (non recommandée pour la majorité des utilisateurs).


**Kafka local (test rapide)**

- Démarrer la stack Kafka (Zookeeper, Kafka, Schema Registry) :

```powershell
docker-compose up -d
```

- Générer les classes Avro (SpecificRecord) et compiler le projet :

```powershell
./gradlew.bat generateAvroJava classes --no-daemon
```

- (Optionnel) Créer le topic de test `purchase.events` :

```powershell
# exécuter dans le conteneur Kafka
# Option A: créer depuis l'hôte via le conteneur (recommandé)
docker-compose exec kafka \
	kafka-topics --create --topic purchase.events \
	--partitions 3 --replication-factor 1 --if-not-exists \
	--bootstrap-server localhost:9092

# Option B: ouvrir un shell dans le conteneur et exécuter la commande (si nécessaire)
docker exec -it <kafka_container_name> bash
# puis dans le conteneur :
/usr/bin/kafka-topics --create --topic purchase.events --bootstrap-server kafka:29092 --replication-factor 1 --partitions 3
```

- Lancer le consumer (écoute en continu) :

```powershell
./gradlew.bat runPurchaseConsumer --no-daemon
```

- Dans un autre terminal, envoyer un événement de test :

- Dans un autre terminal, pour des tests contrôlés utilisez un utilitaire d'administration :

```powershell
# Exemple : créer un joueur via l'utilitaire admin (contrôlé)
./gradlew.bat runEnsurePlayer --no-daemon
# Pour la publication de jeux, utilisez les workflows d'édition ou l'outil d'administration dédié.
```

- Variables d'environnement :
	- `KAFKA_BOOTSTRAP_SERVERS` (par défaut `localhost:9092`)
	- `SCHEMA_REGISTRY_URL` (par défaut `http://localhost:8081`)

Ces étapes permettent à un évaluateur de démarrer rapidement l'infrastructure et de vérifier l'envoi/réception d'événements métier.

**Flux validé (Kafka → Streams projection → REST → UI)**

Ces étapes reproduisent la procédure que nous avons validée localement — elles démarrent la stack, génèrent les classes Avro, lancent l'application Streams qui expose un endpoint REST, puis l'UI Compose Desktop qui interroge cet endpoint.

1) Démarrer Docker et vérifier les services

```powershell
docker-compose up -d
docker-compose ps
docker-compose logs -f kafka
```

2) Générer Avro et compiler

```powershell
./gradlew.bat generateAvroJava classes --no-daemon
```

3) Créer le topic (si non créé)

```powershell
docker-compose exec kafka \
	kafka-topics --create --topic purchase.events \
	--partitions 3 --replication-factor 1 --if-not-exists \
	--bootstrap-server localhost:9092
```

4) Lancer l'application Streams + REST (garder le terminal ouvert)

```powershell
./gradlew.bat runStreamsRest --no-daemon
```

5) Dans un autre terminal, lancer l'UI (Compose Desktop)

```powershell
./gradlew.bat run --no-daemon
```

6) Ouvrir l'UI, sélectionner un joueur, puis envoyer événements de test depuis un 3ème terminal :

```powershell
# Pour générer des événements contrôlés en local, utilisez les utilitaires d'administration
# (par ex. `runEnsurePlayer`) ou importez des flux réels depuis les pipelines d'ingestion.
./gradlew.bat runEnsurePlayer --no-daemon
```

7) Vérifier la projection depuis la ligne de commande (ou via l'UI) :

```powershell
curl.exe http://localhost:8080/api/players/player-1/library

# ou en PowerShell:
Invoke-RestMethod -Uri 'http://localhost:8080/api/players/player-1/library'
```

Remarques:
- Gardez le terminal où `runStreamsRest` tourne ouvert — il expose le endpoint REST sur le port `8080`.
- L'UI interroge automatiquement le REST pour remplir la bibliothèque du joueur.






