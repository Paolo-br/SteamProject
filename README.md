# 🎮 Steam Project - Plateforme de Gestion de Jeux Vidéo

> Application desktop de gestion d'une plateforme de jeux vidéo style Steam, construite avec **Kotlin**, **Compose Desktop**, **Apache Kafka** et **Avro**.

---

## 📋 Table des matières

1. [Prérequis](#-prérequis)
2. [Installation rapide](#-installation-rapide)
3. [Lancement du projet](#️-lancement-du-projet)
4. [Architecture du projet](#-architecture-du-projet)
5. [Commandes Gradle disponibles](#️-commandes-gradle-disponibles)
6. [Dépannage](#-dépannage)

---

## 🔧 Prérequis

Avant de commencer, assurez-vous d'avoir installé les outils suivants :

| Outil | Version requise | Vérification |
|-------|-----------------|--------------|
| **JDK** | 21+ | `java -version` |
| **Docker** | Dernière version | `docker --version` |
| **Docker Compose** | Dernière version | `docker-compose --version` |
| **Git** | Dernière version | `git --version` |

### Installation du JDK 21

- **Windows** : Télécharger [Adoptium Temurin 21](https://adoptium.net/) et définir `JAVA_HOME`
- **macOS** : `brew install --cask temurin@21`
- **Linux** : `sdk install java 21-open` (via SDKMAN)

> ⚠️ **Important** : Utilisez `gradlew.bat` sur Windows et `./gradlew` sur macOS/Linux.

---

## 🚀 Installation rapide

### 1. Cloner le dépôt

```bash
git clone <url-du-repo>
cd SteamProject
```

### 2. Vérifier l'installation

```powershell
# Windows (PowerShell)
java -version            # Doit afficher Java 21+
docker --version         # Doit afficher Docker installé
.\gradlew.bat --version  # Doit afficher Gradle 8.x
```

---

## ▶️ Lancement du projet

### Étape 1 : Démarrer l'infrastructure Docker

Lancez les services Kafka, Zookeeper et Schema Registry :

```powershell
docker-compose up -d
```

Vérifiez que tous les services sont en cours d'exécution :

```powershell
docker-compose ps
```

**Services démarrés :**

| Service | Port | Description |
|---------|------|-------------|
| Zookeeper | 2181 | Coordination Kafka |
| Kafka | 9092 | Broker de messages |
| Schema Registry | 8081 | Registre des schémas Avro |

### Étape 2 : Compiler le projet et générer les classes Avro

```powershell
Perso j'ai dû ajouter cela dans le fichier build.gardle.kts (à la place de kotlin{jvmToolchain}) :
// Explicitly set Java and Kotlin targets to 21 for compatibility
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "21"
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}
Et tout ça dans le fichier gradle.properties :
# Enable Gradle toolchain auto-download
org.gradle.java.installations.auto-download=false

# Use specific JAVA_HOME
org.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-23.0.2

# Daemon configuration
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
.\gradlew.bat generateAvroJava classes --no-daemon
```

### Étape 3 : Lancer le service REST (projection Kafka Streams)

Dans un **premier terminal**, lancez le service backend :

```powershell
.\gradlew.bat runPurchaseRest --no-daemon
```

> ✅ Le service REST sera accessible sur `http://localhost:8080`

**Endpoints disponibles :**

| Endpoint | Description |
|----------|-------------|
| `GET /api/players` | Liste des joueurs |
| `GET /api/players/{playerId}/library` | Bibliothèque d'un joueur |
| `GET /api/catalog` | Catalogue des jeux |
| `GET /api/publishers-list` | Liste des éditeurs |

### Étape 4 : Lancer l'interface graphique (UI)

Dans un **second terminal**, lancez l'application desktop :

```powershell
.\gradlew.bat run --no-daemon
```

> 🎉 **L'application Steam Project s'ouvre !**

---

## 📁 Architecture du projet

```
SteamProject/
├── src/
│   ├── main/
│   │   ├── avro/                    # Schémas Avro (événements Kafka)
│   │   ├── java/                    # Services Kafka (consumers, producers, streams)
│   │   │   └── org/steamproject/
│   │   │       └── infra/kafka/
│   │   ├── kotlin/                  # Application UI (Compose Desktop)
│   │   │   ├── Main.kt              # Point d'entrée
│   │   │   ├── model/               # Modèles de données
│   │   │   ├── services/            # Couche services
│   │   │   ├── ui/                  # Composants UI
│   │   │   │   ├── components/      # Composants réutilisables
│   │   │   │   ├── screens/         # Écrans de l'application
│   │   │   │   └── navigation/      # Gestion de la navigation
│   │   │   └── state/               # Gestion d'état
│   │   └── resources/               # Ressources (données CSV, etc.)
├── build.gradle.kts                 # Configuration Gradle
├── docker-compose.yml               # Infrastructure Docker
└── README.md                        # Ce fichier
```

---

## 🛠️ Commandes Gradle disponibles

### Commandes principales

| Commande | Description |
|----------|-------------|
| `.\gradlew.bat run` | 🖥️ Lancer l'UI Compose Desktop |
| `.\gradlew.bat runPurchaseRest` | 🌐 Lancer le service REST |
| `.\gradlew.bat generateAvroJava` | 📦 Générer les classes Avro |
| `.\gradlew.bat build` | 🔨 Compiler le projet |
| `.\gradlew.bat clean` | 🧹 Nettoyer le projet |

### Commandes de production d'événements

| Commande | Description |
|----------|-------------|
| `.\gradlew.bat -Pmode=create runPlayerProducer` | Produire des événements joueur |
| `.\gradlew.bat runPublishGame` | Publier un jeu |
| `.\gradlew.bat runPublishPatch` | Publier un patch |
| `.\gradlew.bat runPublishDlc` | Publier un DLC |
| `.\gradlew.bat -Pmode=purchase runPlayerProducer` | Simuler un achat |
| `.\gradlew.bat -Pmode=crash runPlayerProducer` | Simuler un crash parmi les jeux |
| `.\gradlew.bat -Pmode=playsession runPlayerProducer` | Simuler une session de jeux |
| `.\gradlew.bat -Pmode=dlc_purchase runPlayerProducer` | Simuler un achat de DLC |
| `.\gradlew.bat -Pmode=rate runPlayerProducer`| Simuler une note |

---

## 🐛 Dépannage

### L'UI ne s'affiche pas ou reste vide

1. **Vérifiez que Docker est en cours d'exécution :**
   ```powershell
   docker-compose ps
   ```

2. **Vérifiez que le service REST répond :**
   ```powershell
   curl.exe http://localhost:8080/api/players
   ```

3. **Redémarrez l'infrastructure :**
   ```powershell
   docker-compose down
   docker-compose up -d
   ```

### Erreurs de compilation Avro

Régénérez les classes Avro :

```powershell
.\gradlew.bat clean generateAvroJava classes --no-daemon
```

### Erreurs liées à OneDrive (Windows)

Si vous rencontrez des erreurs de fichiers verrouillés, déplacez le projet hors du dossier OneDrive synchronisé.

### Ports déjà utilisés

Vérifiez les ports utilisés et arrêtez les processus conflictuels :

```powershell
netstat -ano | findstr :9092   # Kafka
netstat -ano | findstr :8081   # Schema Registry
netstat -ano | findstr :8080   # Service REST
```

### Réinitialisation complète

Si rien ne fonctionne, effectuez une réinitialisation complète :

```powershell
# 1. Arrêter et supprimer les volumes Docker
docker-compose down -v

# 2. Nettoyer le build Gradle
.\gradlew.bat clean

# 3. Redémarrer l'infrastructure
docker-compose up -d

# 4. Régénérer les classes Avro
.\gradlew.bat generateAvroJava classes --no-daemon

# 5. Lancer le service REST (Terminal 1)
.\gradlew.bat runPurchaseRest --no-daemon

# 6. Lancer l'UI (Terminal 2)
.\gradlew.bat run --no-daemon
```

---

## 👨‍💻 Technologies utilisées

| Technologie | Utilisation |
|-------------|-------------|
| **Kotlin** | Langage principal |
| **Compose Desktop** | Interface graphique |
| **Apache Kafka** | Streaming d'événements |
| **Avro** | Sérialisation des événements |
| **Schema Registry** | Gestion des schémas |
| **Gradle** | Build et dépendances |
| **Docker** | Conteneurisation |

---

## 📝 Résumé des commandes

```powershell
# 1. Démarrer Docker
docker-compose up -d

# 2. Compiler le projet
.\gradlew.bat generateAvroJava classes --no-daemon

# 3. Lancer le backend (Terminal 1)
.\gradlew.bat runPurchaseRest --no-daemon

# 4. Lancer l'UI (Terminal 2)
.\gradlew.bat run --no-daemon
```

---



