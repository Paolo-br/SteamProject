# 🎮 Steam Project - Plateforme de Gestion de Jeux Vidéo

> **Projet académique 4A - JVM & Data Streaming**  
> Architecture Event-Driven avec Kafka, Avro, et Compose Desktop

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Compose%20Desktop-1.6.11-green.svg)](https://www.jetbrains.com/lp/compose-desktop/)
[![Kafka](https://img.shields.io/badge/Kafka-3.7.0-red.svg)](https://kafka.apache.org/)
[![Avro](https://img.shields.io/badge/Avro-1.11.3-orange.svg)](https://avro.apache.org/)

---

## 📋 Vue d'ensemble

Application complète de gestion de plateforme de jeux vidéo (type Steam) avec :
- 🎨 **Frontend** : Interface desktop moderne en Compose
- 📊 **Event Streaming** : Architecture événementielle avec Kafka
- 💾 **Persistance** : Agrégation de données en temps réel
- 📈 **Analytics** : Tendances, statistiques, et alertes

---

## 🚀 Démarrage Rapide

### 📖 Documentation
👉 **START ICI** : [`QUICK_RECAP.md`](QUICK_RECAP.md) - Récapitulatif en 3 points

**Guides complets** :
- 📚 [`INDEX.md`](INDEX.md) - Navigation dans toute la documentation
- 📋 [`PROJECT_RECAP.md`](PROJECT_RECAP.md) - État complet du projet
- 📘 [`PABLO_SUMMARY.md`](PABLO_SUMMARY.md) - Schémas Avro (pour équipe)

### 🏃 Lancer le projet

#### Frontend (Compose Desktop)
```bash
# Compiler et lancer l'interface
./gradlew run
```

#### Infrastructure (Docker)
```bash
# Lancer Kafka + Schema Registry + Postgres
docker-compose up -d

# Vérifier les services
docker-compose ps
```

---

## 🏗️ Architecture

```
┌──────────────────┐
│  Frontend (UI)   │  Compose Desktop - 8 écrans
└────────┬─────────┘
         │ HTTP REST
         ↓
┌──────────────────┐
│   Backend API    │  Expose données agrégées
└────────┬─────────┘
         │
         ↓
┌──────────────────┐
│    Postgres DB   │  Stockage persistant
└────────┬─────────┘
         ↑
┌────────┴─────────┐
│  Consommateurs   │  7 Aggregators (Kafka Streams)
└────────┬─────────┘
         ↑
┌────────┴─────────┐
│   Kafka Topics   │  12 topics événementiels
└────────┬─────────┘
         ↑
┌────────┴─────────┐
│   Producteurs    │  3 micro-services
└──────────────────┘
```

**Plus de détails** : [`PABLO_VISUAL_GUIDE.md`](PABLO_VISUAL_GUIDE.md)

---

## 📊 État du Projet

### ✅ Fait (100%)
- [x] Frontend Compose Desktop (8 écrans, 25+ composants)
- [x] Modèles de données Kotlin (15 événements)
- [x] Schémas Avro (12 events + 3 models)
- [x] Documentation complète
- [x] Configuration Docker

### 🚧 En Cours
- [ ] Infrastructure Kafka (Raphaël)
- [ ] Publication Schema Registry (Pablo + Raphaël)

### ⏳ À Faire
- [ ] Producteurs Kafka (Julien)
- [ ] Consommateurs & Kafka Streams (Anas)
- [ ] Backend REST API
- [ ] Intégration Frontend ↔ Backend

---

## 👥 Équipe

| Membre | Rôle | Responsabilités |
|--------|------|-----------------|
| **Raphaël** | Architecte Kafka | Infrastructure Kafka + Schema Registry |
| **Pablo** | Modélisation | Schémas Avro (✅ Fait) |
| **Julien** | Producteurs | Micro-services émetteurs + VGSales |
| **Anas** | Consommateurs | Aggregators + Kafka Streams + Persistance |

**Voir besoins détaillés** : [`QUICK_RECAP.md`](QUICK_RECAP.md)

---

## 📁 Structure du Projet

```
SteamProject/
├── 📖 README.md                   Ce fichier
├── 📖 INDEX.md                    Navigation documentation
├── 📖 QUICK_RECAP.md              Récap rapide (START ICI)
├── 📋 PROJECT_RECAP.md            État complet
│
├── 📘 PABLO_SUMMARY.md            Guide Pablo (schémas)
├── 📘 PABLO_SCHEMAS_GUIDE.md      Guide détaillé schémas
├── 📘 PABLO_VISUAL_GUIDE.md       Guide visuel + exemples
│
├── schemas/                       ⭐ Schémas Avro
│   ├── events/                    12 événements Kafka
│   │   ├── game-released.avsc
│   │   ├── patch-published.avsc
│   │   ├── new-rating.avsc
│   │   └── ... (9 autres)
│   ├── models/                    3 modèles de données
│   │   ├── game.avsc
│   │   ├── player.avsc
│   │   └── publisher.avsc
│   └── README.md
│
├── src/main/kotlin/
│   ├── Main.kt                    Point d'entrée
│   ├── model/                     Modèles Kotlin
│   │   ├── Events.kt              15 événements
│   │   ├── Game.kt
│   │   ├── Player.kt
│   │   └── ...
│   ├── ui/
│   │   ├── screens/               8 écrans
│   │   ├── components/            25+ composants
│   │   └── viewmodel/             ViewModels MVVM
│   └── services/                  ServiceLocator + Mocks
│
├── docker-compose.yml             Infrastructure
├── build.gradle.kts               Configuration Gradle
└── ...
```

---

## 🎨 Frontend - Écrans Disponibles

| Écran | Description | Statut |
|-------|-------------|--------|
| 🏠 **Home** | Dashboard KPIs + Activité temps réel | ✅ |
| 📚 **Catalog** | Catalogue jeux avec recherche/filtres | ✅ |
| 🎮 **Game Detail** | Détails complets d'un jeu | ✅ |
| ⭐ **Ratings** | Évaluations et notes joueurs | ✅ |
| 🔧 **Patches** | Historique mises à jour | ✅ |
| 💥 **Incidents** | Rapports crashs et incidents | ✅ |
| 👤 **Players** | Profils et statistiques joueurs | ✅ |
| 🏢 **Editors** | Profils éditeurs et leurs jeux | ✅ |

---

## 📊 Événements Kafka

### 📤 Produits par les micro-services (9)
- `GameReleasedEvent` - Nouveau jeu publié
- `PatchPublishedEvent` - Patch déployé
- `PriceUpdateEvent` - Changement de prix
- `NewRatingEvent` - Évaluation joueur
- `GamePurchaseEvent` - Achat d'un jeu
- `GameSessionEvent` - Session de jeu
- `CrashReportEvent` - Crash/incident
- `PlayerPeakEvent` - Pic de joueurs
- `PublisherActivityEvent` - Activité éditeur

### 📥 Produits par les aggregators (3)
- `GameTrendingEvent` - Tendance détectée
- `SalesMilestoneEvent` - Palier de ventes
- `IncidentAggregatedEvent` - Stats incidents

**Détails** : [`schemas/README.md`](schemas/README.md)

---

## 🛠️ Technologies

### Frontend
- **Kotlin** 2.0.21
- **Compose Desktop** 1.6.11
- **Kotlinx Coroutines** 1.8.1
- **Kotlinx Serialization** 1.7.3
- **Ktor Client** 2.3.9

### Backend & Streaming
- **Apache Kafka** 3.7.0
- **Apache Avro** 1.11.3
- **Confluent Schema Registry** 7.5.0
- **PostgreSQL** (via Docker)

### Build & Infra
- **Gradle** 8.5 (Kotlin DSL)
- **Docker** & Docker Compose
- **Java** 21 (JVM Target)

---

## 📚 Documentation

### 🌟 Pour Démarrer
- [`QUICK_RECAP.md`](QUICK_RECAP.md) - Récap en 3 points (5 min)
- [`INDEX.md`](INDEX.md) - Navigation complète

### 📘 Guides Techniques
- [`PABLO_SUMMARY.md`](PABLO_SUMMARY.md) - Schémas Avro + Besoins équipe
- [`PABLO_VISUAL_GUIDE.md`](PABLO_VISUAL_GUIDE.md) - Architecture + Exemples code
- [`PABLO_SCHEMAS_GUIDE.md`](PABLO_SCHEMAS_GUIDE.md) - Référence complète schémas
- [`schemas/README.md`](schemas/README.md) - Documentation schémas .avsc

### 📋 Gestion de Projet
- [`PROJECT_RECAP.md`](PROJECT_RECAP.md) - État complet du projet

---

## 🚀 Workflow Développement

### 1️⃣ Infrastructure (Raphaël)
```bash
# Lancer Kafka + Schema Registry
docker-compose up -d kafka schema-registry

# Créer les topics
# (Voir PABLO_SUMMARY.md pour la liste)
```

### 2️⃣ Schémas Avro (Pablo + Raphaël)
```bash
# Publier les schémas dans Schema Registry
# (Scripts à venir)
```

### 3️⃣ Producteurs (Julien)
```kotlin
// Générer les classes Avro
./gradlew generateAvroJava

// Implémenter les services émetteurs
// (Voir PABLO_VISUAL_GUIDE.md pour exemples)
```

### 4️⃣ Consommateurs (Anas)
```kotlin
// Générer les classes Avro
./gradlew generateAvroJava

// Implémenter les aggregators
// (Voir PABLO_VISUAL_GUIDE.md pour exemples)
```

---

## 🧪 Tests

```bash
# Tests unitaires
./gradlew test

# Lancer le frontend en mode dev
./gradlew run

# Vérifier les services Docker
docker-compose ps
docker-compose logs -f kafka
```

---

## 📦 Build & Packaging

```bash
# Compiler le projet
./gradlew build

# Créer le JAR
./gradlew jar

# Build distributable (Windows MSI)
./gradlew packageMsi

# Build distributable (Linux DEB)
./gradlew packageDeb

# Build distributable (macOS DMG)
./gradlew packageDmg
```

---

## 🐳 Docker

### Services disponibles
```yaml
- kafka:9092           # Kafka broker
- zookeeper:2181       # Kafka Zookeeper
- schema-registry:8081 # Confluent Schema Registry
- postgres:5432        # PostgreSQL
```

### Commandes utiles
```bash
# Lancer tous les services
docker-compose up -d

# Voir les logs
docker-compose logs -f

# Arrêter les services
docker-compose down

# Supprimer les volumes (ATTENTION: perte de données)
docker-compose down -v
```

---

## 📖 Ressources

### Documentation officielle
- [Kotlin](https://kotlinlang.org/docs/)
- [Compose Desktop](https://www.jetbrains.com/lp/compose-desktop/)
- [Apache Kafka](https://kafka.apache.org/documentation/)
- [Apache Avro](https://avro.apache.org/docs/current/)
- [Confluent Platform](https://docs.confluent.io/)

### Tutoriels
- [Kafka Quickstart](https://kafka.apache.org/quickstart)
- [Avro Tutorial](https://avro.apache.org/docs/current/gettingstartedjava.html)
- [Compose Desktop Tutorial](https://github.com/JetBrains/compose-jb/tree/master/tutorials)

---

## 🤝 Contribution

### Workflow Git (suggéré)
```bash
# Branches par fonctionnalité
git checkout -b feature/nom-fonctionnalite

# Commit avec convention
git commit -m "feat: description de la feature"
git commit -m "fix: correction du bug X"
git commit -m "docs: mise à jour documentation"

# Push et PR
git push origin feature/nom-fonctionnalite
```

### Convention de commits
- `feat:` Nouvelle fonctionnalité
- `fix:` Correction de bug
- `docs:` Documentation
- `refactor:` Refactoring
- `test:` Tests
- `chore:` Tâches diverses

---

## 📝 License

Projet académique - École d'Ingénieurs - 2026

---

## 📞 Contact

**Équipe Projet** :
- Raphaël (Kafka)
- Pablo (Schémas Avro)
- Julien (Producteurs)
- Anas (Consommateurs)

---

## ✨ Status

![Status](https://img.shields.io/badge/Frontend-100%25-green)
![Status](https://img.shields.io/badge/Schemas-100%25-green)
![Status](https://img.shields.io/badge/Infrastructure-60%25-yellow)
![Status](https://img.shields.io/badge/Producteurs-0%25-red)
![Status](https://img.shields.io/badge/Consommateurs-0%25-red)

**Dernière mise à jour** : 7 janvier 2026

---

**🎉 Le projet avance bien ! 15 schémas Avro créés, frontend complet, infrastructure en cours.**

👉 Consulter [`QUICK_RECAP.md`](QUICK_RECAP.md) pour l'état détaillé.

