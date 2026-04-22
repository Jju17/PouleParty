# TODO PouleParty

## 1. Nouveaux power-ups

### Existants (6)

| Power-up | Pour | Durée | Effet |
|----------|------|-------|-------|
| Zone Preview | Hunter | instant | Montre la prochaine zone avant qu'elle ne rétrécisse |
| Radar Ping | Hunter | 30s | Révèle la position de la Poule |
| Invisibility | Poule | 30s | Cache la Poule de tous les hunters |
| Zone Freeze | Poule | 120s | Empêche la zone de rétrécir |
| Decoy | Poule | 20s | Place un faux signal de Poule sur la map des hunters |
| Jammer | Poule | 30s | Brouille le signal de position de la Poule |

### Constat

- 4 power-ups Poule vs 2 power-ups Hunter — le ratio est déséquilibré
- Aucun power-up n'affecte la **vitesse** ou le **déplacement**
- Aucun power-up n'introduit d'**interaction entre hunters**
- Tous les power-ups sont **individuels** — aucun effet de groupe

### Idées brainstorm

**Pour les Hunters :**
- **Speed Boost** : Augmente la fréquence de mise à jour de la position de la Poule (polling plus rapide) pendant X secondes
- **Compass** : Affiche une flèche directionnelle vers la Poule (sans position exacte) pendant 30s
- **Trap** : Place un piège invisible sur la carte. Si la Poule passe à proximité (~50m), elle est révélée pendant 10s
- **Drone** : Envoie un "scan" circulaire depuis la position du hunter, qui révèle si la Poule est dans un rayon de 200m (oui/non, sans direction)
- **Hunter Coalition** : Partage la position de tous les hunters entre eux pendant 60s (utile pour se coordonner)

**Pour la Poule :**
- **Teleport Zone** : Déplace le centre de la zone de X mètres dans une direction choisie (1 seul usage, effet permanent)
- **Smokescreen** : Tous les hunters dans un rayon de 100m autour de la Poule perdent le signal pendant 15s
- **Shield** : La Poule ne peut pas être "trouvée" pendant 20s même si un hunter entre le code correct (le code est temporairement rejeté)

### Questions d'implémentation

- Les power-ups apparaissent déjà sur la carte et se collectent par proximité. Le système de spawn (`PowerUpSpawnHelper`) génère des batches à chaque zone shrink. Ce mécanisme est réutilisable.
- Chaque power-up actif est tracké côté Firestore via des champs `active{Type}Until` sur le document `Game`. Ajouter un nouveau type = ajouter un champ Timestamp + la logique côté client.
- Les règles Firestore (`firestore.rules`) autorisent déjà l'update des champs `active*` par n'importe quel joueur authentifié — pas de changement de règles nécessaire pour de nouveaux power-ups.
- Les types activés par partie sont configurables via `enabledPowerUpTypes` sur le modèle `Game`.
- Le champ `isHunterPowerUp` sur `PowerUpType` permet de filtrer quels power-ups apparaissent côté hunter vs côté poule. Un nouveau power-up hunter nécessite juste `isHunterPowerUp = true`.
- **Complexité** : Les power-ups "spatiaux" (Trap, Teleport Zone) nécessitent de stocker une position dans le document power-up + une logique de détection de proximité côté client.

---

## 2. Notifications push — ✅ DONE

Le flow est complet :
- **iOS** : les 8 clés `notif_*_title` / `notif_*_body` sont dans `Localizable.xcstrings`. `MessagingDelegate` → `FCMTokenManager.saveToken()` + re-save après `signInAnonymously()` dans `AppFeature.appStarted`
- **Android** : `PouleFCMService.onMessageReceived` résout les clés via `resolveString` avec fallback. `onNewToken` → `saveTokenToFirestore()`. Channel `game_events` créé dans `PoulePartyApp.onCreate()`. Re-save du token après auth anonyme déjà géré dans `AppNavigation.kt:78-118` (`FirebaseMessaging.token.await()` après `signInAnonymously().await()`, couvre la race `onNewToken` avant `currentUser`)
- **Backend** : `onGameCreated` (`functions/src/index.ts`) enqueue les Cloud Tasks pour `chicken_start`, `hunter_start`, `zone_shrink`. `onGameUpdated` gère `hunter_found`

---

## 3. Réservation de partie payante — ✅ MOSTLY DONE (manque Stripe)

### Fait

- Modèle `Game.pricing: { model, pricePerPlayer, deposit, commission }` sur les 2 plateformes
- Limite **1 partie gratuite par jour** : `apiClient.countFreeGamesToday(userId)` côté iOS (`Home.swift`), `PlanSelectionViewModel.canCreateFreeGame()` côté Android
- Limite **max 5 joueurs** en partie gratuite : soft-enforced via `PricingConfig.FreeGameConfig.maxPlayers = 5`
- **Feature `PlanSelection`** complète sur les 2 plateformes (screen + VM + wiring dans le flow de création)
- Formules `free` / `flat` / `deposit` disponibles dans l'UI, `GameRegistration.required` et `closesMinutesBefore` gérés
- Flow hunter : `JoinFlow` avec paiement/caution, `registration` subcollection

### Reste à faire

- [ ] **Intégration Stripe** (sera faite plus tard) :
  - [ ] Cloud Function `createPaidGame` qui valide le paiement Stripe avant de créer le document Game
  - [ ] Firestore rules : forcer le passage par la Cloud Function pour les parties payantes (bloquer création client direct si `pricing.model != "free"`)
  - [ ] Hard-enforcer `hunterIds.size() <= 5` dans les rules quand `pricing.model == "free"`
  - [ ] Politique de remboursement (annulation Poule, pas assez de hunters)
  - [ ] Deep link pour rejoindre une partie payante directement
  - MCP Stripe disponible dans les outils si besoin

---

## 4. Challenges in-game & leaderboard *(plus tard)*

### Concept

Système de défis à accomplir pendant les parties avec un leaderboard par équipe.
Inspiré du design ci-dessous (2 sections sur un même écran) :

### UI cible

```
┌─────────────────────────────────────────┐
│  📸  Relève des défis                   │
│  Fun, décalés et parfois très           │
│  compromettants.                        │
│                                         │
│  ┌───────────────────────────────────┐  │
│  │ OBJECTIF PRINCIPAL                │  │
│  │                                   │  │
│  │  [📍] Trouve la Poule    500 pts  │  │
│  │       ● En cours · zone 300m     │  │
│  │                                   │  │
│  │ DÉFIS BONUS                       │  │
│  │                                   │  │
│  │  [💍] Demande en mariage  100 pts │  │
│  │       Pas encore complété         │  │
│  │                                   │  │
│  │  [🗑] Photo dans une      50 pts  │  │
│  │       poubelle                 ✅ │  │
│  │       ✓ Complété                  │  │
│  │                                   │  │
│  │  [🎤] Chante la          100 pts  │  │
│  │       Brabançonne              ✅ │  │
│  │       ✓ Complété                  │  │
│  └───────────────────────────────────┘  │
│                                         │
│                                         │
│  🏆  Domine le score                    │
│  Points, gloire et légende éternelle.   │
│                                         │
│  ┌───────────────────────────────────┐  │
│  │  ÉQUIPE                    SCORE  │  │
│  │                                   │  │
│  │  1 (LF) Les Faucons      780 pts │  │
│  │         4 défis · poule   ██████  │  │
│  │         trouvée                   │  │
│  │                                   │  │
│  │  2 (PB) Poulets Bravas   400 pts │  │
│  │         6 défis · en      ████░░  │  │
│  │         chasse                    │  │
│  │                                   │  │
│  │  3 (TO) Team Omelette    340 pts │  │
│  │       ● Toi · 3 défis    ███░░░  │  │
│  │                                   │  │
│  │  4 (CG) Cluck Gang       210 pts │  │
│  │         2 défis · en      ██░░░░  │  │
│  │         route                     │  │
│  │                                   │  │
│  │  5 (KF) KFC Reloaded      80 pts │  │
│  │         1 défi · perdus   █░░░░░  │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

### Mécaniques

- **Objectif principal** : toujours "Trouve la Poule" (gros points, lié au gameplay core)
- **Défis bonus** : défis fun/décalés définis par le créateur de la partie (photo, chanson, gage...). Complétés manuellement (photo/validation par le groupe ?)
- **Leaderboard par équipe** : chaque groupe de hunters forme une équipe avec un nom. Classement par score = objectif principal + défis bonus complétés
- **Score en temps réel** : barres de progression proportionnelles au score max

### Idées de défis bonus

**Gages physiques :**
- Demande en mariage (à un inconnu)
- Photo dans une poubelle
- Chante la Brabançonne / Marseillaise
- Fais 10 pompes devant un café
- Selfie avec un chien

**Liés au gameplay :**
- Trouver la Poule en moins de 10 minutes
- Trouver la Poule en premier (3 parties d'affilée)
- Parcourir plus de 5 km dans une partie
- Rester dans la zone pendant toute la partie
- Utiliser un power-up au bon moment (dans les 30s avant un zone shrink)

### Questions d'implémentation

- **Équipes** : Nouveau concept. Chaque hunter rejoint une équipe (nom + initiales). Modèle Firestore : sous-collection `teams/{teamId}` dans le game doc, avec `name`, `initials`, `memberIds[]`, `score`
- **Défis custom** : Le créateur de partie définit les défis bonus dans ChickenConfig. Stockés dans `game.challenges[]` avec `title`, `points`, `type` (manual/auto)
- **Validation** : Les défis "gages" nécessitent une validation manuelle (photo uploadée ? vote du groupe ?). Les défis gameplay sont détectés automatiquement côté client.
- **Scoring** : Calculé en temps réel côté client à partir des `challenges` complétés + objectif principal. Pas besoin de collection séparée si les scores restent dans le game doc.
- **Anti-triche** : Les défis auto (distance, temps) doivent être validés côté serveur en croisant `hunterLocations` / `winners` timestamps.
- **UI** : Accessible depuis la HunterMap (bouton ou swipe up). Affiche les 2 sections : défis perso + leaderboard équipe.

---

## 5. Better handling no location allowed — ✅ DONE

- **iOS** : `OnboardingSlides.swift` gère `.denied` / `.restricted` avec un bouton "Open Settings" qui ouvre `UIApplication.openSettingsURLString`
- **Android** : `OnboardingScreen` affiche l'alerte avec un bouton "Ouvrir les Réglages" qui lance `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)` avec l'URI du package, et un bouton "OK" pour fermer. Re-check via `fineLocationLauncher` + `OnboardingIntent.RefreshPermissions` au retour.

---

## 6. Role Spectator — `SpectatorMapFeature`

### Concept

Ajouter un troisième rôle "spectateur" qui permet de regarder une partie en cours sans y participer. Valide au passage le choix d'architecture actuelle (composition over generic unification) : un nouveau rôle doit pouvoir se brancher sur les composants partagés sans rework.

### Structure cible

Calquée sur `ChickenMap/` et `HunterMap/` :

```
Features/SpectatorMap.swift            (reducer + Destination)
Features/SpectatorMap/
  SpectatorMapView.swift
  SpectatorMapContent.swift
```

### Composants réutilisables (déjà en place)

- `MapFeatureState` — protocole commun que le State du spectateur doit adopter
- `MapPowerUpsFeature` — Scope'é si le spectateur voit les power-ups actifs
- `MapBottomBar`, `MapCommonOverlays`, `MapCommonSheets`, `MapHapticsModifier`, `zoneOverlayContent`, `PowerUpMapMarker` — UI partagée

### Intégration `AppFeature`

- Ajouter un case `.spectatorMap(SpectatorMapFeature.State)` à l'enum `AppFeature.State`
- Même shape de delegate actions que les deux autres rôles (`.delegate(.returnedToMenu)`, etc.)

### Questions ouvertes à trancher avant implémentation

- Le spectateur voit-il la position de la Poule ? Des hunters ? Les deux ?
- Nouveau rôle dans les Firestore rules, ou lecture seule suffit ?
- Compte-t-il dans le cap de joueurs / affecte-t-il le pricing ?
- Comment rejoint-on une partie en tant que spectateur ? Code partagé, lien, invitation ?
- Le spectateur peut-il voir les power-ups collectés / activés par les joueurs ?

---

## 7. Stripe ↔ Firestore hand-reconciliation

### Problème

Si un event webhook Stripe `payment_intent.succeeded` n'atteint jamais `stripeWebhook` (livraison ratée, handler qui crash avant le delete du dedup marker), la partie reste bloquée en `pending_payment` ou la registration hunter reste sans `paid: true`, alors que Stripe a bien encaissé. Aujourd'hui, recovery = inspection manuelle Dashboard Stripe + Firestore.

### Plan

Ajouter une Cloud Function **scheduled** (cron quotidien) qui :

- Query Stripe `paymentIntents.list({ created: { gte: now-36h } })`, filtre `status == 'succeeded'`.
- Pour chaque PaymentIntent `kind == 'creator_flat'` : lit `games/{pi.metadata.gameId}`.
  - Si status == `pending_payment` → flip vers `waiting`, set `payment.paymentIntentId`, `payment.paidAt`, `payment.reconciledAt`, log `Warning`.
  - Si status absent ou déjà `waiting` → no-op.
- Pour chaque PaymentIntent `kind == 'hunter_deposit'` : idem sur `games/{gameId}/registrations/{uid}` (flip `paid: true`).

### Notes

- Fenêtre de 36h pour absorber les retries Stripe qui peuvent traîner jusqu'à 72h (on revérifie chaque jour donc un event loupé est récupéré max J+1).
- Cron schedule Firebase : `functions.pubsub.schedule("0 3 * * *").timeZone("Europe/Brussels")`.
- Métrique à observer : nombre de réconciliations par run > 0 signale un souci webhook persistant.

---

## 8. Modularisation iOS (priorité : low)

### Objectif possible

Extraire au moins `GameDomain` en Swift Package pour isoler la logique pure (Models, `GameTimerLogic`, `PowerUpSpawnLogic`, `ProfanityFilter`) — tests CLI ultra-rapides, rebuild incrémental plus léger, pas de fight avec Firebase (le package reste pure Swift).

### Split recommandé (si on se lance)

```
Packages/
├── GameDomain/       # Models/, GameTimerLogic, PowerUpSpawnLogic, ProfanityFilter
│                     # Pure Swift — zéro dépendance UI/Firebase/Mapbox
└── DesignSystem/     # Color+Utils, Font+Utils, BangerText, neonGlow, composants partagés
                      # SwiftUI uniquement
ios/PouleParty/       # features + clients + app — reste en place
```

**À ne PAS extraire** : `Clients/` (Firebase veut vivre au niveau app — plist + Crashlytics build script), les features TCA (couplage fort par design).

### Pourquoi c'est en low

- Solo dev → pas besoin de frontières pour isoler des équipes
- Build clean actuel < 1 min → pas un vrai problème
- Architecture déjà propre via protocoles + `Scope` + helpers partagés
- 469 tests passent en ~20s — acceptable

### Signaux pour reprioriser

Attaquer quand **un** de ces déclencheurs apparaît :

- Build clean > 2-3 min de manière régulière
- Collaboration à plusieurs sur iOS
- Besoin de partager `GameDomain` avec un autre projet (watchOS companion, serveur Swift, etc.)
- Dépassement de ~20 features

### Tests préalables avant de se lancer

- Xcode → Product → Perform Action → Build With Timing Summary — vérifier que `SwiftCompile` domine
- Si `SwiftCompile` ne domine pas (cas actuel probable), le gain sera marginal

### Pièges documentés

- **Firebase + SPM** : Crashlytics build script fragile, plist qui veut vivre au niveau app
- **Mapbox access token** : actuellement lu depuis Info.plist → injection via init nécessaire dans un package
- **Localizable.xcstrings** : fichier unique, perd l'intégration Xcode si split
- **Assets (Bangers, Early GameBoy, chicken.imageset)** : `.bundle(for: Bundle.module)` nécessaire partout
- **Widget target** : ne peut pas linker FirebaseMessaging — watch the transitive deps

---

## 9. Migration Gradle DSL deprecated (priorité : low)

### Warnings persistants dans `android/app/build.gradle.kts`

Chaque build release émet 3 warnings Kotlin DSL :

```
w: file:.../build.gradle.kts:19:1: 'fun Project.android(configure: Action<BaseAppModuleExtension>): Unit' is deprecated. Replaced by com.android.build.api.dsl.ApplicationExtension.
w: file:.../build.gradle.kts:76:5: 'fun BaseAppModuleExtension.kotlinOptions(configure: Action<DeprecatedKotlinJvmOptions>): Unit' is deprecated. Please migrate to the compilerOptions DSL.
w: file:.../build.gradle.kts:77:9: 'var jvmTarget: String' is deprecated. Please migrate to the compilerOptions DSL.
```

### Cause

- `android { … }` → l'ancien bloc `Project.android` de `BaseAppModuleExtension`, deprecated, remplacé par `com.android.build.api.dsl.ApplicationExtension`.
- `kotlinOptions { jvmTarget = "17" }` → deprecated au profit de `compilerOptions { jvmTarget = JvmTarget.JVM_17 }`.

### Plan

1. Remplacer l'import + extension `android { … }` par le nouveau DSL `ApplicationExtension` (ou laisser en l'état si le nouveau DSL reste source-compatible — à vérifier sur la version d'AGP qu'on utilise).
2. Remplacer `kotlinOptions { jvmTarget = "17" }` par `compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }`. Import à ajouter en haut du fichier.
3. Valider avec un `./gradlew :app:bundleProductionRelease` (voire `./gradlew help --scan` en amont) que les 3 warnings disparaissent **et** que la compilation Kotlin produit toujours du bytecode JVM 17.

### Pourquoi c'est en low

- Non bloquant : le projet build / package / ship correctement avec les 3 warnings en place depuis longtemps.
- Risque de casse non-nul (Gradle DSL migrations cassent régulièrement sur les versions de transition) → ne pas tenter en plein cycle release.
- Viole techniquement la Zero-Warnings policy de `CLAUDE.md`, mais les warnings sont pré-existants à toutes les releases récentes — à traiter hors hotfix.

### Signaux pour reprioriser

- Mise à jour AGP / Kotlin majeure qui rend les vieux DSL erreurs dures (plus juste deprecation).
- Accumulation d'autres deprecations Gradle qui rendent difficile de distinguer les warnings légitimes de ceux-là.

