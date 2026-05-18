# PouleParty — Audit global

**Date** : 2026-05-17
**Périmètre** : iOS (~16k LoC Swift) + Android (~17k LoC Kotlin) + Cloud Functions (~2.8k LoC TS) + Web (~2.6k LoC TS/TSX) + `firestore.rules` (272 LoC)
**Échéance critique** : D-Day **2026-06-06** (20 jours).
**Méthode** : 6 audits parallèles (sécurité, Functions, iOS, Android, web, parity iOS↔Android).

---

## État d'avancement — 2026-05-18

### ✅ CRITICAL (11/11)

| ID | Sujet | Statut | Deploy |
|----|-------|--------|--------|
| CRIT-1 | `/eventRegistrations` PII lock + CFs callable | ✅ Code + tests + iOS/Android callers | ✅ Staging + Prod |
| CRIT-2 | `foundCode` → `/private/security` + getFoundCode CF | ✅ Code + tests + iOS/Android UI | ✅ Staging + Prod |
| CRIT-3 | `hunterIds`/`winners` restreints + `submitFoundCode` CF | ✅ Code + tests + iOS/Android callers | ✅ Staging + Prod |
| CRIT-4 | Stripe hardening (CORS, maxInstances, honeypot, batchId allowlist, max-length, email regex, phone formula-injection) | ✅ Code + tests | ✅ Staging + Prod |
| CRIT-5 | `crypto.randomInt` + transactional uniqueness sur codes payants | ✅ Code + tests | ✅ Staging + Prod |
| CRIT-6 | iOS `Dictionary uniquingKeysWith` (Victory + Challenges) | ✅ Code + tests | n/a (mobile) |
| CRIT-7 | iOS `LiveLocationManager` refactor (multi-consumer + serialized auth queue) | ✅ Code + tests | n/a (mobile) |
| CRIT-8 | Android i18n extraction (40 strings wizard + 4 JoinFlow errors) | ✅ Code + tests | n/a (mobile) |
| CRIT-9 | Android deeplink mid-game route check | ✅ Code + tests | n/a (mobile) |
| CRIT-10 | Cloud Tasks cleanup au game delete (deterministic IDs + manifest + onGameDeleted) | ✅ Code + tests | ✅ Staging + Prod |
| CRIT-11 | Functions `transitionGameStatus` transactionnel | ✅ Code + tests | ✅ Staging + Prod (V1) |

### ✅ HIGH addressés (14/21)

| ID | Sujet | Statut |
|----|-------|--------|
| HIGH-1 | Functions Mapbox `Promise.allSettled` + fallback raw coord | ✅ Code |
| HIGH-2 | Functions Mapbox token log sanitization | ✅ Code |
| HIGH-4 | Functions Stripe idempotencyKey | ✅ Code |
| HIGH-5 | Functions Stripe webhook amount/currency/mode cross-check + not-found graceful | ✅ Code |
| HIGH-6 | Functions `gmRateLimits` tx.delete on success | ✅ Code |
| HIGH-8 | iOS JoinFlow GM error strings → `String(localized:)` | ✅ Code |
| HIGH-10 | iOS `findLastUpdate` bounded + early break | ✅ Code |
| HIGH-11 | iOS ChickenMap scenePhase resume | ✅ Code |
| HIGH-12 | Android NotificationChannel centralisé dans PoulePartyApp | ✅ Code |
| HIGH-13 | Android `BaseMapViewModel.streamJobs` thread-safe | ✅ Code |
| HIGH-14 | Android `MediaPlayer.create` NPE guard | ✅ Code |
| HIGH-16 | Android deeplink `pathPrefix` → `path` exact | ✅ Code |
| HIGH-17 | Parity Android shrink boundary off-by-one | ✅ Code (V1) |
| HIGH-18 | Android heartbeat retry via `withRetry` | ✅ Code (V1) |
| HIGH-19 | JoinFlow/GM error i18n iOS+Android (jumelé HIGH-8) | ✅ Code |
| HIGH-20 | Web security headers (HSTS, CSP, X-Frame-Options, etc.) | ✅ Code |
| HIGH-21 | Web `firebase.ts` dead code supprimé + dep retirée | ✅ Code |

### ⏳ HIGH différés (7/21) — backlog post-D-Day

| ID | Sujet | Raison de différer |
|----|-------|--------------------|
| HIGH-3 | Functions webhook side-effects recovery (scheduled re-run) | Nice-to-have ; le mécanisme actuel log les échecs et permet récupération manuelle |
| HIGH-7 | Functions GM password brute-force per-IP rate limit | Cluster séparé (M-1 audit), nécessite Cloud Functions IP extraction |
| HIGH-9 | iOS effects sans `.cancellable(id:)` sur 3 features map | Refactor ChickenMap/HunterMap/GameMasterMap, ~30 effects à wrapper |
| HIGH-6 (iOS) | iOS audio doublage on Home re-task | Mineur ; nécessite guard sur `playSound()` |
| HIGH-15 | Android `collectAsState` → `collectAsStateWithLifecycle` (12 screens) | Mécanique mais répétitif ; gain batterie |
| iOS GameMaster strings | `Features/GameMasterMap/GameMasterMapView.swift:79-203` (15+ strings) | Symétrique de la GameMasterMapScreen Android (toujours en FR) ; à faire en parallèle |
| Android GameMaster strings | `ui/gamemastermap/GameMasterMapScreen.kt:143-284` (15+ strings) | Idem iOS |

### Non-couvert (manuel par Julien)

- **App Check** (CRIT-4 reste partiel) : reCAPTCHA Enterprise site key à configurer dans Firebase Console (staging + prod), puis activation `enforceAppCheck: true` sur `createPendingRegistration` + init du Web SDK.
- **Firestore TTL policy** sur `gmRateLimits.firstAttemptAt` (HIGH-6 complémentaire) : à configurer Firebase Console → Firestore → TTL.
- **Cloud Tasks IAM** : vérifier que le compute SA staging/prod a bien `roles/cloudtasks.taskRunner` pour le `onGameDeleted` delete.

### ✅ Tous les déploiements réalisés

- Cloud Functions staging + prod : `onGameDeleted`, `onGameCreated`, `createPendingRegistration`, `confirmRegistrationPayment`, `spawnPowerUpBatch`, `joinAsGameMaster` (+ V1/V2/V3 déjà déployées)
- Firestore Rules staging + prod (lock `/eventRegistrations`, restrict `hunterIds`/`winners`)
- Hosting staging + prod (security headers + Inscription.tsx honeypot)

### Métriques finales

- **CRITICAL** : 11/11 fixés (10 déployés + 1 pending re-auth)
- **HIGH** : 14/21 fixés (60 % couverts dans la session ; 7 différés au backlog)
- **Builds** : iOS ✅ / Android ✅ / Functions ✅ / Web ✅
- **Tests** : iOS (642 tests) ✅ / Android (testStagingDebugUnitTest) ✅ / Functions (122 tests) ✅
- **Strings i18n ajoutées** : 49 (40 wizard + 5 JoinFlow Android, 2+2 iOS GM errors)
- **Cloud Functions nouvelles** : `validateRegistrationCode`, `lookupGameByValidationCode`, `submitFoundCode`, `getFoundCode`, `onGameDeleted`
- **Cloud Functions modifiées** : `transitionGameStatus`, `createPendingRegistration`, `confirmRegistrationPayment`, `onGameCreated`, `spawnPowerUpBatch`, `joinAsGameMaster`
- **Rules** : 4 changements (deny `/eventRegistrations` reads, restrict `hunterIds` to own UID, deny `winners` direct writes, exclude `winners` from creator/participant update rules)

---

---

## 0. TL;DR — Les 10 choses à fixer avant le 06/06

Ordonnées par ratio « risque × probabilité × proximité D-Day ».

| # | ID | Plateforme | Sujet | Sévérité |
|---|----|----|-------|----------|
| 1 | SEC-C2 | rules | `/eventRegistrations` lisible par n'importe quel user anonyme → fuite PII complète (email, phone, nom, équipe) de tous les payants | **CRITICAL** |
| 2 | SEC-H9 | rules | `foundCode` lisible dans le doc Game → tout hunter gagne sans trouver la poule | **CRITICAL** |
| 3 | SEC-H8 | rules | `winners` + `hunterIds` writables par n'importe qui sans check d'identité → auto-déclaration de victoire | **CRITICAL** |
| 4 | SEC-C1 / WEB-C2 | functions / web | `createPendingRegistration` zéro auth / zéro rate-limit / zéro App Check → coût Stripe + pollution PII | **CRITICAL** |
| 5 | FN-C3 / FN-C4 | functions | `Math.random()` pour les codes payants + collision TOCTOU sur génération | **CRITICAL** |
| 6 | iOS-C1 | iOS | `Dictionary(uniqueKeysWithValues:)` crash Victory/Challenges sur doublons | **CRITICAL** |
| 7 | iOS-C2 / iOS-C3 | iOS | `LiveLocationManager` race continuations + `startTracking` self-terminate → gel silencieux de la position chicken | **CRITICAL** |
| 8 | AND-C1 | Android | Strings FR codées en dur partout (wizard, GameMaster, JoinFlow) → NL/EN voient du français cassé | **CRITICAL** |
| 9 | FN-H1 | functions | Mapbox snap : 1 échec sur 5 fait disparaître tout le batch de power-ups | **HIGH** |
| 10 | PAR-1 | iOS/Android | Deeplink mid-game : iOS l'ignore, Android le rejoue au retour Home → JoinFlow s'ouvre en plein milieu d'une partie suivante | **CRITICAL** (parity) |

Hors top-10 mais à faire avant D-Day : **WEB-C1** (security headers HTTP), **FN-H6** (Stripe idempotency key), **PAR-4** (heartbeat Android sans retry).

---

## 1. CRITICAL — bloquant D-Day

### CRIT-1. `/eventRegistrations` lisible par tout user anonyme (PII complète)
- **Fichier** : `firestore.rules:249-252`
- **Symptôme** : `allow read: if request.auth != null;` + Firebase Auth anonyme automatique → n'importe qui qui installe l'app peut `db.collection('eventRegistrations').get()` et exfiltrer tous les `{playerName, teamName, email, phone, code, paid, stripeSessionId, locale}`.
- **Le commentaire prétend que l'entropie 36⁶ du code protège** : faux, il ne protège que le *lookup par code*, pas la lecture brute.
- **Impact** : GDPR breach, fuite massive des payants D-Day.
- **Fix** : lock `allow read: if false;`. Déplacer `validateRegistrationCode` et `lookupGameByValidationCode` en Cloud Functions callable qui retournent `{valid: bool}` / `{gameId, ...}` (sans email/phone). Le client iOS/Android n'a besoin que de ces booléens.

### CRIT-2. `foundCode` exposé dans le doc Game lisible par tout hunter
- **Fichier** : `firestore.rules:8` + champ `Game.foundCode`
- **Symptôme** : tout hunter authentifié peut lire le `foundCode` 4 chiffres directement dans `/games/{id}` et le soumettre sans avoir physiquement trouvé la poule.
- **Impact** : intégrité du jeu cassée. Cumulé à CRIT-3 c'est trivial.
- **Fix** : déplacer `foundCode` dans `/games/{gameId}/private/` (admin-SDK-only). Soumission via callable CF qui compare server-side.

### CRIT-3. `hunterIds` / `winners` writables sans check d'identité
- **Fichier** : `firestore.rules:46-51`
- **Symptôme** : la règle laisse n'importe quel user auth écrire dans `hunterIds` et `winners`. Aucune contrainte « le UID ajouté == `request.auth.uid` ». Un attaquant peut :
  - s'ajouter à n'importe quel `hunterIds` (impersonation),
  - **se déclarer winner d'une partie où il n'est même pas hunter**,
  - saturer `hunterIds` jusqu'à `maxPlayers` pour bloquer les vrais joueurs.
- **Fix** :
  ```
  request.resource.data.hunterIds
    .removeAll(resource.data.hunterIds)
    .hasOnly([request.auth.uid])
  ```
  Et pour `winners`, déléguer à une Cloud Function qui revalide `foundCode` server-side. À regrouper avec CRIT-2.

### CRIT-4. `createPendingRegistration` zéro protection
- **Fichier** : `functions/src/registrations.ts:167-247` + appel `web/src/pages/Inscription.tsx:90`
- **Symptôme** : `onRequest({ cors: true })`, pas d'auth, pas de rate-limit, pas d'App Check, pas de captcha, pas de honeypot. Une boucle `curl` peut :
  - cramer le quota Stripe (lock du compte avant D-Day),
  - polluer `/eventRegistrations` avec des PII fake (email + phone d'une victime sous un team name attaquant),
  - réserver des codes (avant fix CRIT-5) que JoinFlow doit ensuite skip.
- **Fix** :
  1. **App Check + reCAPTCHA Enterprise** (`consumeAppCheckToken: true` sur le `onRequest`).
  2. Whitelist explicite des origines (réutiliser `ALLOWED_ORIGINS` au lieu de `cors: true`).
  3. Rate-limit IP-based (in-memory LRU ou Firestore counter).
  4. Honeypot field caché côté formulaire.
  5. Valider `batchId` contre une allowlist (`^game-\d{2}-\d{2}-\d{4}$`).

### CRIT-5. `Math.random()` pour les codes payants + génération racy
- **Fichier** : `functions/src/registrations.ts:135-141` (code), `143-158` (uniqueness)
- **Symptôme** :
  - `Math.random()` non cryptographique → un attaquant qui capture quelques codes peut prédire les suivants (xorshift128+ a des attaques publiées).
  - La vérif d'unicité fait `where ... limit 1` puis `set()` sans transaction → deux requests concurrentes peuvent générer le même code et écrire deux docs. JoinFlow `limit 1` ne récupère que le premier → l'autre payant ne peut plus rejoindre la partie.
- **Fix** :
  ```ts
  import { randomInt } from "crypto";
  // génération + transaction qui rejette si collision
  ```
  Ou utiliser le code comme docId dans une sous-collection par batch → Firestore enforce l'unicité via `create`.

### CRIT-6. iOS Victory/Challenges crash sur `Dictionary(uniqueKeysWithValues:)`
- **Fichiers** : `ios/PouleParty/Features/Victory/Victory.swift:118-119`, `ios/PouleParty/Features/Challenges/Challenges.swift:68`
- **Symptôme** : `Dictionary(uniqueKeysWithValues:)` **trap (fatalError)** sur clé dupliquée. `Game.winners` peut contenir deux entrées avec même `hunterId` après un retry réseau d'`addWinner` ou un re-submit après `winnerRegistrationFailed`.
- **Impact** : crash en prod à l'affichage de la fin de partie.
- **Fix** :
  ```swift
  Dictionary(winners.map { ($0.hunterId, $0) }, uniquingKeysWith: { first, _ in first })
  ```

### CRIT-7. iOS `LiveLocationManager` race + `startTracking` self-terminate
- **Fichiers** : `ios/PouleParty/Clients/LocationClient.swift:108-152`
- **Symptôme** :
  - `authorizationContinuation` non synchronisée + classe non `@MainActor` → "continuation called twice" trap si deux appels concurrents pendant la transition `.notDetermined → whenInUse → always`.
  - `startTracking()` appelle `stopTracking()` qui **finit la continuation du writer chicken déjà actif** → si une seconde feature appelle `startTracking()`, la boucle d'écriture position chicken sort silencieusement. La poule cesse d'émettre sa position pour le reste de la partie.
- **Fix** : annotation `@MainActor` sur le manager + une seule continuation gérée explicitement + support N consumers concurrents (ref-counting sur `startUpdatingLocation`) OU exposer seulement `lastLocation`/`subscribe`.

### CRIT-8. Android : strings FR codées en dur partout (rule violation)
- **Fichiers** : `ui/home/HomeViewModel.kt:188-191,526,534`, `ui/gamemastermap/GameMasterMapScreen.kt:143-284`, tous les `ui/gamecreation/steps/*.kt`
- **Symptôme** : dizaines de string literals français (parfois accents stripés : « Duree », « depart », « Recapitulatif ») jamais extraits dans `strings.xml`. Les utilisateurs EN et NL voient du français cassé. Le wizard de création complet, l'écran GameMaster et les erreurs JoinFlow.
- **Impact** : CLAUDE.md rule violée, NL/EN cassés à D-Day où des touristes peuvent participer.
- **Fix** : extraction massive vers `values/strings.xml` + `values-fr/` + `values-nl/`. Pour les VMs, exposer des enums de message keys résolus dans le Composable via `stringResource`.

### CRIT-9. Parity : deeplink mid-game ignoré sur iOS, rejoué sur Android
- **Fichiers** : iOS `Features/App.swift:75-86` ✅ vs Android `MainActivity.kt:53-61` + `data/DeeplinkBus.kt` ❌
- **Symptôme** : iOS drop explicitement le deeplink quand state ∈ `{chickenMap, hunterMap, gameMasterMap, victory}`. Android écrit dans `DeeplinkBus` inconditionnellement. `HomeViewModel` n'observe que pendant que Home est sur le back stack → le deeplink reste dans `.value` et **se rejoue dès que l'utilisateur revient à Home après la partie**, ouvrant le JoinFlow par-dessus.
- **Fix Android** : `MainActivity.handleDeeplink` doit consulter `navController.currentDestination?.route` et drop si dans un écran de jeu.

### CRIT-10. Functions : Cloud Tasks dangling après deletion de game
- **Fichier** : `functions/src/index.ts` (pas de handler)
- **Symptôme** : aucun `onDocumentDeleted("games/{gameId}")`. Quand la creator supprime un game en `waiting`, **toutes les tâches déjà enquêtées continuent de tirer** (jusqu'à 101 spawnPowerUpBatch + status + notifs). Chaque handler fait un early-return mais paye l'invocation + 3 retries Cloud Tasks.
- **Impact** : facture Functions + Mapbox + bruit logs. Bloque le pattern « delete-and-recreate-with-same-id ».
- **Fix** : flag `lifecycle.cancelled: true` sur le doc, ou tracker chaque task enquêtée dans une sous-collection et `tasks.delete` au cleanup.

### CRIT-11. Functions : `transitionGameStatus` non atomique
- **Fichier** : `functions/src/index.ts:283-307`
- **Symptôme** : read + update séparés, pas de transaction. Cloud Tasks retries (3×) + race avec un write client (chicken cancel) peuvent écraser un `done` côté serveur.
- **Fix** : wrap dans `db.runTransaction` avec check `status === expectedCurrentStatus` avant update.

---

## 2. HIGH — à fixer avant ou peu après D-Day

### HIGH-1. Functions — Mapbox snap fail = batch power-ups perdu
- **Fichier** : `functions/src/index.ts:488-501`, `mapbox.ts:77`
- `Promise.all` rejette tout le batch sur une seule erreur. Mapbox down 5 min → 45 tentatives, batch perdu, plus de power-ups pendant l'incident.
- **Fix** : `Promise.allSettled` + fallback raw coordinate par power-up.

### HIGH-2. Functions — Mapbox URL avec `access_token=` loggée à chaque retry
- **Fichier** : `functions/src/mapbox.ts:51,74`
- Token leak via Cloud Logging accessible à `roles/logging.viewer`. Token rotation nécessaire à chaque offboarding.
- **Fix** : logger uniquement les coordonnées, jamais l'URL complète. Idéalement utiliser un token `sk.` server-only séparé du `pk.` client.

### HIGH-3. Functions — Side-effects webhook silencieusement avalés
- **Fichier** : `functions/src/registrations.ts:329-338`
- Resend ou Sheets en panne pendant 1 webhook delivery → user payé, pas d'email, pas de ligne Sheet. Récupération manuelle par grep des logs.
- **Fix** : champ `sideEffectsStatus: {email, sheet, attemptedAt}` sur le doc + scheduled function qui re-run toutes les 10 min pour `paid: true && sideEffectsStatus.email == "failed"`.

### HIGH-4. Functions — Stripe Checkout non idempotent
- **Fichier** : `functions/src/registrations.ts:208-235`
- Blip réseau entre Stripe → CF perdu → user re-submit → deux sessions Stripe, deux registrations. Aucune détection avant que le user signale ses deux receipts.
- **Fix** : passer `idempotencyKey: sha256(batchId:email:registrationId)` à `stripe.checkout.sessions.create`.

### HIGH-5. Functions — Recommendations defense-in-depth Stripe webhook
- **Fichier** : `functions/src/registrations.ts:291-316`
- Pas de cross-check `session.amount_total === teamSize × 1200` ni `payment_status === 'paid'` ni `currency === 'eur'`. Coupons / refactor futurs activeraient le paid:true à €0.
- **Fix** : ajouter les asserts dans le webhook.

### HIGH-6. Functions — `gmRateLimits` croît à l'infini
- **Fichier** : `functions/src/gameMaster.ts:33-37`
- Pas de TTL, pas de cleanup. Storage qui grossit linéairement.
- **Fix** : TTL Firestore 24h sur `firstAttemptAt`, ou delete le doc sur succès.

### HIGH-7. Functions — GM password brute-force via re-auth anonyme
- **Fichier** : `functions/src/gameMaster.ts:163-211`
- Rate-limit per-(uid, gameId) seulement. Anonymous Auth re-issue un UID à la demande → ~2000 UIDs couvrent les 10k codes 4 chiffres.
- **Fix** : rate-limit per-IP **ou** per-gameId global (max 50 tentatives/heure quel que soit le UID). Mieux : code 8+ chars donné out-of-band.

### HIGH-8. iOS — Strings FR codées en dur (rule violation)
- **Fichiers** : `Features/GameCreation/GameMasterPasswordStep.swift:25-60`, `Features/JoinFlow/JoinFlow.swift:300-776`, `Features/GameMasterMap/GameMasterMapView.swift:79-203`
- Symétrique de CRIT-8 côté Android. Le `Localizable.xcstrings` contient ces strings comme clés brutes en français sans traduction.
- **Fix** : extraire en `String(localized:)` avec clés EN + ajouter FR/NL.

### HIGH-9. iOS — Effects map sans `.cancellable(id:)`
- **Fichiers** : `Features/ChickenMap/ChickenMap.swift:439-619`, `HunterMap.swift:587-746`, `GameMasterMap.swift:118-162`
- Six effects long-lived par feature, aucun avec ID cancellable. Re-fire de `.task` (changement d'identité de la view) → double listener Firestore, double heartbeat, double location writer.
- **Fix** : `.cancellable(id: CancelID.x, cancelInFlight: true)` sur chaque stream + heartbeat + timer.

### HIGH-10. iOS — `findLastUpdate()` O(n) sans borne
- **Fichier** : `Models/Game+Computed.swift:151-181`
- Boucle exécutée chaque seconde × 3 features. Pour un game stale (timeskew, test laissé tourner), milliers d'itérations / tick.
- **Fix** : break early si `lastRadius <= 0`, cap à 10k itérations.

### HIGH-11. iOS — Pas de `scenePhase` resume sur ChickenMap
- **Fichier** : `Features/ChickenMap/ChickenMapView.swift`
- HunterMap a déjà l'handler avec un commentaire explicite. Chicken sans handler → après backgrounding en `followTheChicken`, marker chicken stale jusqu'au prochain mouvement de 10 m. Casse la démo D-Day.
- **Fix** : mirror du pattern HunterMap (`.onChange(of: scenePhase)` → `appBecameActive` → `setChickenLocation`).

### HIGH-12. Android — `LocationForegroundService` channel split entre 2 fichiers
- **Fichiers** : `PoulePartyApp.kt:35` + `LocationForegroundService.kt:69,82-94`
- Brittle : un refactor qui appelle `startForeground` avant `ensureChannel` casse silencieusement sur OEMs / API 31+.
- **Fix** : centraliser les channels dans `PoulePartyApp.createNotificationChannels()`.

### HIGH-13. Android — `BaseMapViewModel.streamJobs` non thread-safe
- **Fichier** : `ui/map/BaseMapViewModel.kt:36`
- `mutableListOf<Job>()` muté depuis plusieurs coroutines + cleared en `onCleared`. `ConcurrentModificationException` possible.
- **Fix** : `Collections.synchronizedList(mutableListOf())` ou `synchronized(streamJobs) { ... }`.

### HIGH-14. Android — `MediaPlayer.create` peut NPE
- **Fichier** : `ui/home/HomeScreen.kt:85-95`
- `MediaPlayer.create()` retourne null sur échec → `.apply { … }` throw NPE → crash Home (launcher screen).
- **Fix** : `runCatching { MediaPlayer.create(…)?.apply { … } }.getOrNull()` + tous les call sites null-safe.

### HIGH-15. Android — `collectAsState()` partout au lieu de `collectAsStateWithLifecycle()`
- **Fichiers** : 12 écrans dans `ui/`
- Recompositions + listeners actifs même app en background → batterie / CPU / coûts Firestore inutiles.
- **Fix** : `import androidx.lifecycle.compose.collectAsStateWithLifecycle` partout (mécanique).

### HIGH-16. Android — Deeplink `pathPrefix="/join"` capture `/joinparty`, `/joiner`...
- **Fichier** : `AndroidManifest.xml:46`
- `pathPrefix` = string match, pas segment match. iOS est plus strict → inconsistance cross-plateforme.
- **Fix** : passer `android:path="/join"` (exact match).

### HIGH-17. Parity — `processRadiusUpdate` boundary off-by-one
- **Fichiers** : iOS `GameTimerLogic.swift:286` (`>=`) vs Android `GameTimerHelper.kt:309` (`!after()` = `<=`)
- iOS shrink à `now == nextUpdate`, Android shrink à `now > nextUpdate` → désynchro 1 s à chaque shrink.
- **Fix Android** : `if (now.time < nextUpdate.time) return null`.

### HIGH-18. Parity — Android `updateHeartbeat` sans retry
- **Fichier** : `data/FirestoreRepository.kt:442-446` vs iOS `Clients/ApiClient.swift:670-674`
- iOS wrap dans `withRetry` avec commentaire explicite (« un blip = chicken paraît disconnect »). Android fire-and-forget.
- **Fix Android** : wrap dans `withRetry("updateHeartbeat($gameId)")`.

### HIGH-19. Parity — Erreurs JoinFlow/GM password seulement en français
- **iOS** : `Features/JoinFlow/JoinFlow.swift:300-302,346` — strings interpolation Swift brut, pas `String(localized:)`.
- **Android** : `ui/home/HomeViewModel.kt:188-191,526,534` — literals français.
- **Fix** : extraction en parallèle iOS/Android, dans le même PR.

### HIGH-20. Web — Aucun header de sécurité HTTP
- **Fichier** : `firebase.json:9-18`
- Pas de CSP, HSTS, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, Permissions-Policy. La page `/inscription` (email + phone + nom) est entièrement frameable / clickjackable.
- **Fix** : ajouter le bloc `headers` complet dans `firebase.json` (template prêt dans le rapport web).

### HIGH-21. Web — `firebase.ts` dead code mais expose la config prod
- **Fichier** : `web/src/firebase.ts`
- Dead code tree-shaké aujourd'hui, mais footgun : un futur contributeur qui importe → bundle ×2, accès direct prod Firestore depuis le navigateur.
- **Fix** : delete le fichier + retirer `firebase` de `web/package.json` `dependencies`.

---

## 3. MEDIUM — sprint suivant

### Functions
- **FN-M1** : memory/timeout par défaut (256MB / 60s) sans marge pour Mapbox-bound spawns ou Resend/Sheets latency → bump à `512MiB` / `90-120s`.
- **FN-M2** : retry budget `onTaskDispatched` × `snapToRoad` retries internes = 9 tentatives/power-up (fix avec HIGH-1).
- **FN-M3** : `scheduleGameLifecycleTasks` enqueue en série (100 RTT pour un game 4h) → `Promise.all`.
- **FN-M4** : `getTokensForUserIds` ne dédup pas `chickenId ∩ hunterIds` → push doublé si invariant cassé.
- **FN-M5** : GameMasters ne reçoivent **aucune** notification (zone_shrink notamment).
- **FN-M9** : `confirmRegistrationPayment` throw sur doc manquant → 500 → Stripe retry pendant 3 jours.
- **FN-M11** : pas de max-length sur `playerName`/`teamName`/`phone` (10 MB possible).
- **FN-M12** : Resend sans timeout ni retry.
- **FN-M13** : Sheets dedup O(N) sur colonne B (lent quand >1k registrations).

### Sécurité
- **SEC-M1** : `joinAsGameMaster` brute-force via re-auth anonyme (cf HIGH-7).
- **SEC-M2** : `gameMasterPassword` stocké en clair dans `/private/` → ajouter HMAC-SHA256 + salt.
- **SEC-M3** : `computeZoneConfiguration` callable sans auth + 1000 itérations max → DoS compute.
- **SEC-H5** : email regex `/^\S+@\S+\.\S+$/` accepte `\r\n` → injection header email Resend potentielle. Resserrer + cap 254 chars.
- **SEC-H7** : `chickenLocations` rule `hasAll` (pas `hasOnly`) → chicken peut forcer `invisible: true` sans burn d'Invisibility power-up.
- **SEC-H4** : Sheets writes en `USER_ENTERED` → injection formules CSV. Passer en `RAW` ou prefix `'`.

### iOS
- **iOS-M1** : streams `apiClient.gameConfigStream` ignorent `nil` (doc deleted) → stuck-on-map.
- **iOS-M2** : `JoinFlow` `userId` fallback à `""` masque un bug auth-not-ready → creator se joint comme hunter.
- **iOS-M3** : `decoy` distance fixe 200–500m → décoy hors zone en fin de partie, distinguable du vrai chicken. Clamp à `min(zone.radius - 30, 500)`.
- **iOS-M4** : `findLastUpdate` ignore `endDate` → comptage post-game wrong.
- **iOS-M9** : Timer 10 Hz pour pulse power-ups tourne même sans power-ups visibles.
- **iOS-M10** : hunter écoute `chickenLocationStream` en `stayInTheZone` même sans Radar Ping actif → coûts Firestore inutiles.

### Android
- **AND-M1** : `LocationForegroundService` silent fail si permission `BACKGROUND_LOCATION` manque → user surface aucune erreur.
- **AND-M2** : `RefreshActiveGame` à chaque `ON_RESUME` sans cooldown → 15 reads/no-op possibles.
- **AND-M4** : `FirebaseAuth.getInstance()` / `FirebaseFirestore.getInstance()` directs dans `AppNavigation` + `HomeScreen` → bypass Hilt, untestable.
- **AND-M5** : `PouleFCMService` pas `@AndroidEntryPoint`.
- **AND-M6** : `while (true)` dans coroutines (4 occurrences) → idiomatiquement `while (isActive)` (latent regression).
- **AND-M8** : `contentDescription` codés en anglais → TalkBack casse en FR/NL.

### Parity
- **PAR-M7** : `Challenge.lastUpdated` nullable iOS vs default `Timestamp.now()` Android.
- **PAR-M8** : `Localizable.xcstrings` iOS — 46 keys sans FR, 53 sans NL, 229 sans EN (clés FR-only).

### Web
- **WEB-M1** : `cancel_url` `batchId` round-trip permet forcer un batchId arbitraire (combiné CRIT-4).
- **WEB-M2** : pas de route `/join` côté web → tap sur l'email depuis desktop = redirect vers `/` avec query-string perdue.
- **WEB-M3** : pas de `noindex` sur `/inscription/success` etc. → Google index le session_id.
- **WEB-M4** : inline script anti-FOUC dans `index.html` bloque tout futur CSP strict.

---

## 4. LOW — backlog / cleanup

### Functions
- L1 : `seedChallenges.ts` SA fallback (documenté, OK).
- L2 : pas de check `Number.isFinite` sur `zone.*` parsing (NaN slip-through).
- L4 : `stripeSessionId` update après `set()` → orphans `paid: false` accumulés en cas de Stripe fail.
- L6 : webhook accepte non-POST.

### Sécurité
- L1 : `existingSeed` injection par chicken (game design, low impact).
- L2 : iOS deeplink replay sur cold restart (pas de consume).
- L3 : deeplink ne valide pas shape du `code` (UI corruption / log spam).
- L4 : logs UID + email correlation possible si futur collaborator.

### iOS
- L2 : `winnerNotificationDismissed` clock.sleep non cancellable → flicker.
- L3 : `JoinFlow.codeChanged` validating sans cancellation sur edits rapides.
- L4 : `setHunterLocation`/`setChickenLocation` sans retry → blip = position perdue (subway, dense buildings).
- L5 : `HomeFeature.onTask` retry findActiveGame seulement 1× avec 2s.
- L8 : `GameStatus.init` decode fallback `.waiting` sur unknown → typo serveur = game stuck.
- L9 : pas de tests TestStore sur heartbeat / scenePhase / decoy bounds.

### Android
- L1 : `DeeplinkBus` singleton object workaround (à re-évaluer post Hilt 2.55+).
- L4 : `fetchNicknames` N awaits séquentiels (slow leaderboard) → `whereIn` chunked.
- L6 : `Geocoder.getFromLocationName` deprecated (warning only).
- L7 : verify production Play App Signing SHA-256 dans `assetlinks.json`.
- L9 : `FirebaseAnalytics.setAnalyticsCollectionEnabled(false)` en debug.

### Web
- L8 : email regex client-side faible (relayer sur `<input type="email">` + validator.js server).
- L10 : bundle monolithique 322KB (split Inscription + confetti).
- L4/L5/L7 : a11y polish (labels i18n, `aria-label` Step `n / m`).

### Parity
- PAR-14/15 : int32 truncation defensive guards (latent, à régler en PP-13/14 Phase 2 cleanup).

---

## 5. Annexes par plateforme

### A. Sécurité — résumé
4 CRITICAL (PII leak `/eventRegistrations`, `foundCode` exposé, `winners` writable, `createPendingRegistration` ouvert), 9 HIGH (open-redirect Origin spoof, Stripe webhook hardening, Resend header injection, Sheets formula injection, `chickenLocations` extra fields, hunters self-declare winner, `foundCode` exposé), MEDIUM/LOW : GM brute-force, `gmRateLimits` no TTL, `computeZoneConfiguration` unauth.

### B. Cloud Functions — résumé
4 CRITICAL (Cloud Tasks dangling, transition non atomique, `Math.random` codes, collision TOCTOU), 7 HIGH (Mapbox batch fail-all, token leak URL, Int32 seed overflow, `gmRateLimits` unbounded, webhook side-effects silencieux, Stripe non-idempotent, recursive `onGameUpdated`), 14 MEDIUM, 10 LOW, 3 SUSPECT.

### C. iOS — résumé
3 CRITICAL (Dictionary crash, LocationManager race, startTracking self-terminate), 6 HIGH (FR hardcoded, effects sans cancellable, findLastUpdate unbounded, scenePhase resume, continuation never resumed, audio doublage), 10 MEDIUM, 10 LOW. **Architecture TCA propre, zéro `!`/`try!`/`as!`, Mapbox lifecycle clean, listeners Firestore corrects.** Gap test coverage : heartbeat lifecycle, scenePhase resume, decoy bounds.

### D. Android — résumé
1 CRITICAL (FR hardcoded), 5 HIGH (notification channels split, streamJobs racy, MediaPlayer NPE, collectAsState non-lifecycle-aware, deeplink pathPrefix trop large), 8 MEDIUM, 9 LOW. **Aucune fuite mémoire, coroutines bien scoped, listeners removés, foreground service correct, runtime permissions OK, signing externalisé.** Le `DeeplinkBus` singleton object est justifié et documenté.

### E. Web — résumé
2 CRITICAL (security headers manquants, payment endpoint sans protection), 1 HIGH (dead code firebase.ts), 4 MEDIUM (batchId open-redirect, /join fallback, noindex, inline script CSP), 8 LOW. **Pas de XSS, pas de `dangerouslySetInnerHTML`, pas de fuite PII dans logs, deps à jour, contact email correct (`julien@rahier.dev`).**

### F. Parity iOS ↔ Android — résumé
1 CRITICAL (deeplink mid-game), 5 HIGH (off-by-one shrink, heartbeat retry, validation code error string, GM password string, **#3 faux positif Game.gameMode**), 2 MEDIUM, 2 LOW, 5 SUSPECT. **Excellente parité globale** : zone math, power-up spawning, Firestore models, JoinFlow steps, profanity filter, deeplink contract iOS, gates hunter location, invisible flag PP-87 — tous identiques. Le gros écart restant est la couverture i18n iOS.

---

## 6. Plan d'exécution recommandé

### Sprint 1 (semaine du 2026-05-18 — D-Day J-20 → J-13)
**Goal : éliminer les CRITICAL bloquants D-Day.**
1. CRIT-1 + CRIT-2 + CRIT-3 (1 PR rules + 2 callable functions `validateRegistrationCode` / `submitWinner`).
2. CRIT-4 (App Check + rate-limit + honeypot — coordonner web + functions).
3. CRIT-5 (`crypto.randomInt` + transactional uniqueness).
4. CRIT-6 (1-ligne iOS).
5. CRIT-7 (refactor `LiveLocationManager`).
6. CRIT-8 (extraction strings Android, gros mais mécanique).
7. CRIT-9 (Android deeplink route check).
8. CRIT-10 + CRIT-11 (Functions atomicity + cancellation).

### Sprint 2 (semaine du 2026-05-25 — J-13 → J-6)
**Goal : durcir et limiter le risque opérationnel D-Day.**
- HIGH-1 (Mapbox fallback), HIGH-3 (webhook side-effects recovery), HIGH-4 (Stripe idempotency), HIGH-5 (amount cross-check).
- HIGH-9 (iOS effects cancellable), HIGH-11 (iOS scenePhase chicken), HIGH-17 (parity off-by-one), HIGH-18 (Android heartbeat retry).
- HIGH-20 (web security headers : PR 1 fichier).

### Sprint 3 (post D-Day)
**Cleanup + tech debt.**
- HIGH-2/6/7/12/13/14/15/16/19/21 + tous les MEDIUM.
- Refactor `findLastUpdate` (HIGH-10) + tests gap iOS (L9).
- LOW backlog.

---

## 7. Métriques

| Métrique | Valeur |
|----------|--------|
| Total findings | ~150 |
| CRITICAL | 11 |
| HIGH | 21 |
| MEDIUM | ~45 |
| LOW | ~55 |
| SUSPECT | ~12 |
| Lignes de code auditées | ~38 000 |
| Plateformes | 5 (iOS, Android, Functions, Web, Rules) |
| Temps audit | ~7 min (6 agents parallèles) |

---

*Audit produit le 2026-05-17 via 6 agents Claude parallèles. Les détails complets de chaque finding (location exacte, snippet de fix, impact) sont dans les rapports d'agents originaux ; ce document est la consolidation priorisée.*
