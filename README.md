# 🎮 Steam Project

Application desktop de gestion d'une plateforme de jeux vidéo style Steam.

**Technologies :** Kotlin, Compose Desktop, Apache Kafka, Avro

---

## 🔧 Prérequis

- **JDK 21+** 
- **Docker** et **Docker Compose**

---

## 🚀 Lancement du projet

### 1. Démarrer l'infrastructure Docker

```powershell
docker-compose up -d
```

> Démarre Kafka, Zookeeper et Schema Registry

### 2. Générer les classes Avro (première fois uniquement)

```powershell
.\gradlew.bat generateAvroJava classes --no-daemon
```

> Génère les classes Java à partir des schémas Avro

### 3. Lancer le service REST (Terminal 1)

```powershell
.\gradlew.bat runPurchaseRest --no-daemon
```

> Backend accessible sur `http://localhost:8080`

### 4. Lancer le Scheduler (Terminal 2)

```powershell
.\gradlew.bat runScheduler --no-daemon
```

> Produit automatiquement des événements Kafka

### 5. Lancer l'interface graphique (Terminal 3)

```powershell
.\gradlew.bat run --no-daemon
```

> L'application desktop s'ouvre

---

## 📝 Résumé rapide

```powershell
docker-compose up -d                              # 1. Infrastructure
.\gradlew.bat generateAvroJava classes --no-daemon # 2. Avro (1ère fois)
.\gradlew.bat runPurchaseRest --no-daemon          # 3. Backend (Terminal 1)
.\gradlew.bat runScheduler --no-daemon             # 4. Scheduler (Terminal 2)
.\gradlew.bat run --no-daemon                      # 5. UI (Terminal 3)
```

---

## 🛠️ Commandes utiles

| Commande | Description |
|----------|-------------|
| `.\gradlew.bat run` | Lancer l'UI |
| `.\gradlew.bat runPurchaseRest` | Lancer le backend REST |
| `.\gradlew.bat runScheduler` | Lancer le scheduler |
| `.\gradlew.bat generateAvroJava` | Générer les classes Avro |
| `docker-compose down` | Arrêter l'infrastructure |

---

## 🐛 En cas de problème

```powershell
# Réinitialisation complète
docker-compose down -v
.\gradlew.bat clean
docker-compose up -d
.\gradlew.bat runPurchaseRest --no-daemon  # Terminal 1
.\gradlew.bat run --no-daemon               # Terminal 2
```



