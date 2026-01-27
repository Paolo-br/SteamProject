# Guide pour ajouter de nouveaux événements

Ce document explique comment ajouter proprement de nouveaux événements dans le projet sans casser le code existant. Suivez les étapes et les règles ci-dessous pour garantir compatibilité, lisibilité et tests.

## Principes généraux

- **Définir un contrat clair** : utilisez Avro (schemas dans `src/main/avro`) pour les événements partagés entre producteurs/consommateurs.
- **Compatibilité ascendante/descendante** : ne supprimez pas de champs existants ; ajoutez des champs avec des valeurs par défaut ou optionnels.
- **Nommage clair** : utiliser le suffixe `Event` (ex: `GameReleasedEvent`), noms CamelCase, champ `timestamp` obligatoire.
- **Types primitives stables** : préférer `string`, `int`, `long`, `double`, `boolean` et énumérations versionnées.
- **Validation minimale** : valider les champs critiques côté producteur (ids non-vides, prix >= 0, etc.).

## Étapes pour ajouter un nouvel événement

1. Rédiger le schéma Avro
   - Créez un fichier `src/main/avro/<nom>-event.avsc`.
   - Incluez une `namespace` cohérente (`org.steamproject.events` ou celle du projet).
   - Ajoutez un champ `timestamp` de type `long` et des champs avec des valeurs par défaut si nécessaire.
   - Exemple minimal :

```json
{
  "type": "record",
  "name": "MyNewEvent",
  "namespace": "org.steamproject.events",
  "fields": [
    {"name": "id", "type": "string"},
    {"name": "timestamp", "type": "long", "default": 0}
  ]
}
```

2. Regénérer les classes Avro
   - Lancez la tâche Gradle qui génère les classes Avro (ex: `./gradlew generateAvroJava` ou la tâche du projet).
   - Vérifiez que les fichiers générés apparaissent sous `build`/`generated` ou `generated-main-avro-java`.

3. Ajouter/mettre à jour le modèle Kotlin
   - Si vous utilisez des data classes Kotlin sérialisables (ex: `src/main/kotlin/model/Events.kt`), ajoutez une data class équivalente annotée `@Serializable`.
   - Conserver la compatibilité des noms/champs avec le schéma Avro si vous sérialisez/désérialisez entre Avro et Kotlin.

4. Producteur : envoyer l'événement
   - Ajoutez/mettre à jour la logique de production (Kafka, REST, etc.) pour construire l'événement à partir du modèle et le sérialiser selon le format attendu.
   - Loggez l'événement au niveau `INFO` avec un identifiant de corrélation pour faciliter le debug.

5. Consommateur : gérer l'événement
   - Ajoutez un handler dédié (ex: `onMyNewEvent(event)`) dans la couche de consommation / routing d'événements.
   - Validez les données reçues et traitez les erreurs sans faire planter le consommateur (graceful degradation).

6. Tests
   - Unit tests : tester la transformation et la validation des événements.
   - Integration tests : si possible, tester la production/consommation via un broker de test (embedded Kafka) ou mocks.

7. Versioning & migration
   - Si vous changez un schéma existant : appliquez les règles de compatibilité Avro (backward/forward/compatible).

## Bonnes pratiques et règles à respecter

- Toujours ajouter des valeurs par défaut pour les nouveaux champs.
- Éviter renommer/supprimer des champs sans procédure de migration.
- Prévoir des `enum` explicitement versionnés ; ajoutez de nouvelles valeurs à la fin.
- Garder les événements petits et focus sur un seul but (single responsibility).
- Documenter chaque événement dans le repo (ce fichier) et dans l'API publique si nécessaire.
- Assurer des logs structurés (JSON) contenant au minimum : `eventType`, `eventId`, `timestamp`, `traceId`.
- Ne pas mettre de logique métier lourde directement dans le producteur d'événements.

## Précisions : Producer, Consumer, fichiers `app` et `projection`

- **Producer (producteur)** : code qui *crée* et *envoie* un événement. Il doit :
   - Construire l'objet événement (valider les champs critiques).
   - Sérialiser selon le schéma (Avro/JSON) et publier vers le broker ou l'API.
   - Ne pas exécuter de traitement long ou irréversible pendant la production (préférer déléguer).


- **Consumer (consommateur)** : code qui *reçoit* et *traite* les événements. Il doit :
   - Être résilient (ne pas planter si un événement est malformé).
   - Être idempotent (traitements répétables sans effets secondaires non voulus).
   - Gérer les erreurs avec des retries et des dead-letter queues si nécessaire.
   - Mettre à jour les projections ou déclencher des actions métier asynchrones.

- **Fichiers `app`** : ce sont généralement des entrypoints ou de petits programmes (runners) qui orchestrent une suite d'actions (ex : utilitaires pour produire des événements, scripts de debug, ou petits services exécutables). Ils :
   - Contiennent `main()` et configurent le contexte d'exécution (broker, sérialiseurs, logs).
   - Sont utiles pour démarrer des producteurs/consommateurs isolés en local (tests manuels).

- **Fichiers `projection`** : une projection est la transformation d'événements en un modèle de lecture (read model). Les fichiers `projection` :
   - Écoutent les événements et mettent à jour une représentation optimisée pour la lecture (BD, cache, index).
   - Doivent être idempotents et persister un offset/position pour garantir au moins une fois/au mieux une fois selon la stratégie.
   - Sont souvent séparés de la logique métier : gardez les projections simples et dédiées à la structure des vues.

Conseils pratiques liés à ces rôles :

- Gardez une séparation claire : producteurs = écriture d'événements, consommateurs = lecture/traitement.
- Testez producers et consumers indépendamment : unit tests pour la construction, integration tests pour la chaîne complète.
- Documentez où se trouvent les producers et projections dans le repo (README ou ce fichier).


## Check-list avant PR

- [ ] Schéma Avro ajouté et valide.
- [ ] Classes générées compilent.
- [ ] Data class Kotlin ajoutée / mise à jour si nécessaire.
- [ ] Handlers ajoutés pour la consommation.
- [ ] Tests unitaires et d'intégration ajoutés et passent.
- [ ] Documenter le schéma et mentionner les équipes impactées.
- [ ] Versionner le schéma si incompatible.

---

## Résumé des événements déjà intégrés

Basé sur les schémas Avro présents dans `src/main/avro` et les data classes dans `src/main/kotlin/model/Events.kt`, voici les événements actuellement intégrés :

- `crash-report.avsc` / `CrashReportEvent` : rapport détaillé de crash/incident (sévérité, type d'erreur, message, version du jeu).
- `dlc-published.avsc` : événement de publication d'un DLC (contenu téléchargeable).
- `editor-responded-to-incident.avsc` : réponse d'un éditeur à un incident signalé.
- `game-published.avsc` : (publishing) publication d'un jeu (métadonnées initiales).
- `game-purchase.avsc` / `GamePurchaseEvent` : achat d'un jeu par un joueur (prix payé, joueur, plateforme).
- `game-released.avsc` / `GameReleasedEvent` : nouveau jeu publié sur la plateforme.
- `game-session.avsc` / `GameSessionEvent` : session de jeu d'un joueur (durée, type de session).
- `game-trending.avsc` / `GameTrendingEvent` : tendance/popularité d'un jeu (metrics, pour alertes).
- `game-updated.avsc` : mise à jour générale d'un jeu (métadonnées changées).
- `game-version-deprecated.avsc` : version d'un jeu déclarée obsolète.
- `incident-aggregated.avsc` / `IncidentAggregatedEvent` : incidents agrégés (compte, sévérité moyenne).
- `new-rating.avsc` / `NewRatingEvent` : nouvelle évaluation par un joueur (note, commentaire, temps de jeu).
- `patch-published.avsc` / `PatchPublishedEvent` : publication d'un patch (changelog, versions avant/après).
- `platform-catalog-update.avsc` : mise à jour du catalogue d'une plateforme.
- `player-created.avsc` : création d'un joueur (profil de base).
- `price-update.avsc` / `PriceUpdateEvent` : mise à jour de prix (ancien/nouveau prix, raison).
- `sales-milestone.avsc` / `SalesMilestoneEvent` : atteinte d'un palier de ventes.
- `patch-published.avsc` : publication de patch (déjà listé).

(Nota : certains événements existent à la fois en schéma Avro et en data class Kotlin ; d'autres sont présents uniquement comme schéma Avro selon le besoin d'intégration.)

