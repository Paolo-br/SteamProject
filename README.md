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




