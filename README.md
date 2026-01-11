# 🎮 Steam Project - Plateforme de Gestion de Jeux Vidéo

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

## 🧩 Exécution du build via Docker 

- But: fournir une façon reproductible de compiler le projet (artefacts) sans installer Gradle/Java localement.
- Remarque importante: l'application est une **Compose Desktop** (interface graphique). Docker ne permet pas d'afficher directement l'UI sur Windows sans configuration spécifique (X server / VNC). Voir "Caveats GUI" ci-dessous.

1) Construire les artefacts (via Docker Compose)

```bash
# Lance un conteneur Gradle qui compile le projet et copie le dossier `build` dans `./docker-build-output`
docker-compose run --rm builder
```

2) Récupérer l'artefact

- Les fichiers compilés seront disponibles localement dans `./docker-build-output/build`.
- Exemple: lancer le JAR (si un JAR exécutable est produit) :

```bash
# depuis la racine du projet (ou depuis docker-build-output/build/libs)
java -jar docker-build-output/build/libs/<nom-du-jar>.jar
```

3) Alternatives et caveats GUI

- Pour le développement quotidien et pour exécuter l'interface graphique, il est **préférable** que vos collègues lancent l'application localement avec Gradle :

```bash
./gradlew run
```

- Si vous voulez tenter d'exécuter l'UI depuis un conteneur Docker (avancé) :
    - Sous Linux : vous pouvez mapper le serveur X (ex. `-e DISPLAY=$DISPLAY -v /tmp/.X11-unix:/tmp/.X11-unix`) et démarrer le conteneur avec OpenJDK. Cela nécessite que l'hôte autorise la connexion X (ex. `xhost +local:docker`).
    - Sous Windows : utilisez WSL2 + un serveur X (VcXsrv, Xming) ou configurez une image avec VNC/noVNC — solution plus lourde et à réserver si nécessaire.

Résumé : le `docker-compose run --rm builder` aide vos collègues à **compiler** de manière reproductible. Pour afficher l'UI, préférez `./gradlew run` localement ou suivez les solutions avancées X11/VNC.


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


