# 📅 Scheduler d'Événements Kafka

## 🎯 Objectif

Générateur automatique d'événements Kafka pour peupler et simuler une plateforme de jeux vidéo avec des données réalistes.

## 📦 Architecture

### Fichiers créés

**Package `org.steamproject.scheduler`** :
- `ScheduledEventOrchestrator.java` - Orchestrateur principal avec phases d'initialisation et mode continu
- `FakeDataGenerator.java` - Génération de données réalistes via DataFaker
- `InMemoryDataStore.java` - Stockage thread-safe des entités créées
- `SchedulerConfig.java` - Configuration des intervalles et paramètres

### Gradle Tasks

```bash
.\gradlew.bat runEventOrchestrator    # Lance le scheduler complet
.\gradlew.bat runScheduler            # Alias du scheduler
```

## 🔄 Fonctionnement

### Phase 0 : Initialisation
Création rapide de données de base :
- **5 éditeurs** (réutilisés pour tous les jeux)
- **10 jeux** (liés aux éditeurs existants)
- **15 joueurs**
- **25 achats**
- **20 sessions de jeu**
- **15 évaluations**
- **8 rapports de crash**
- **5 patches**
- **5 DLCs**
- **10 avis détaillés**

### Mode Continu
Génération planifiée d'événements espacés :
- **Jeux** : toutes les 15s
- **Joueurs** : toutes les 20s
- **Achats** : toutes les 8s
- **Sessions** : toutes les 10s
- **Ratings** : toutes les 12s
- **Crashs** : toutes les 25s
- **Patches** : toutes les 60s
- **DLCs** : toutes les 90s

## 🔧 Corrections Apportées

### 1. Topics Kafka corrigés
Les topics du scheduler correspondent maintenant aux consumers existants :
- `purchase-events` → `game-purchase-events`
- `session-events` → `game-session-events`
- `rating-events` → `new-rating-events`
- `crash-events` → `crash-report-events`

### 2. DLC : Ajout du champ `sizeInMB`
- **Schéma Avro** : Ajout du champ `sizeInMB` dans `dlc-published.avsc`
- **Génération** : Taille entre 100 MB et 5 GB
- **Consumer** : `PublisherConsumer.handleDlcPublished()` lit et transmet la taille
- **Projection** : `GameProjection.addDlc()` stocke le `sizeInMB`

### 3. Éditeurs : Système de réutilisation
**Problème** : Chaque jeu créait un nouvel éditeur → éditeurs affichaient 0 jeux publiés

**Solution** :
- Phase 0 crée d'abord 5 éditeurs fixes
- `InMemoryDataStore` stocke les éditeurs (`PublisherInfo`)
- `FakeDataGenerator` réutilise un éditeur aléatoire existant pour chaque nouveau jeu
- Les éditeurs accumulent maintenant plusieurs jeux publiés

### 4. Sessions/Ratings/Crashs basés sur les achats
**Problème** : Les événements utilisaient joueur + jeu aléatoires → le consumer les ignorait car le joueur ne possédait pas le jeu

**Solution** :
- Les méthodes `produceSession()`, `produceRating()`, `produceCrash()` utilisent maintenant un `PurchaseInfo` aléatoire
- Garantit que le joueur possède le jeu avant de générer l'événement
- Les temps de jeu et crashs sont maintenant correctement enregistrés

### 5. Quantités et intervalles optimisés
**Avant** : Spam de données (30 jeux, 50 joueurs, intervalles de 1-2s)

**Après** :
- Quantités initiales réduites (10 jeux, 15 joueurs)
- Intervalles augmentés (8-90s selon le type d'événement)
- Génération plus réaliste et observable

## 🚀 Utilisation

### Démarrage complet

```bash
# Terminal 1 : Kafka + Schema Registry
docker-compose up

# Terminal 2 : Backend (consumers REST)
.\gradlew.bat runPurchaseRest

# Terminal 3 : Scheduler (ce module)
.\gradlew.bat runEventOrchestrator

# Terminal 4 : Interface graphique
.\gradlew.bat run
```

### Configuration (optionnel)

Modifier `SchedulerConfig.java` pour ajuster :
- Intervalles de génération
- Serveurs Kafka/Schema Registry
- Quantités initiales

## 📊 Événements Générés

| Type | Topic | Dépendances |
|------|-------|-------------|
| Game Released | `game-released-events` | Éditeur |
| Player Created | `player-created-events` | - |
| Game Purchase | `game-purchase-events` | Jeu + Joueur |
| Game Session | `game-session-events` | Achat existant |
| New Rating | `new-rating-events` | Achat existant |
| Crash Report | `crash-report-events` | Achat existant |
| Patch Published | `patch-published-events` | Jeu |
| DLC Published | `dlc-published-events` | Jeu |
| Review Published | `review-published-events` | Jeu + Joueur |

## ✅ Résultat

- ✅ Base de données peuplée automatiquement
- ✅ Éditeurs avec plusieurs jeux publiés
- ✅ DLCs avec taille affichée correctement
- ✅ Sessions et temps de jeu fonctionnels
- ✅ Crashs enregistrés et visibles
- ✅ Génération continue et réaliste
