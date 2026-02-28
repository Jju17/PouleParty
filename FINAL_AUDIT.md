# FINAL AUDIT - PouleParty

> Basé sur les audits v1 et v2. Tout ce qui a déjà été implémenté est exclu.
> Dernière mise à jour : 28 février 2026

---

## HIGH - Sécurité & Fiabilité

### H1. Firestore rules : validation des types et tailles des champs

**Fichier** : `firestore.rules`

Les règles vérifient la présence des champs mais pas leurs types ni leurs tailles. Un client malveillant pourrait écrire des données corrompues.

**À ajouter** :
- Valider que `location` est un `latlng` (GeoPoint)
- Valider que `timestamp` est un `timestamp`
- Valider que `hunterId` est un `string` de taille raisonnable (`size() < 128`)
- Limiter la taille des tableaux `winners` et `hunterIds` (ex: `request.resource.data.winners.size() <= resource.data.numberOfPlayers`)

### H2. Firestore rules : rate limiting sur les écritures de positions

**Fichier** : `firestore.rules`

Le throttling des positions (5s) est uniquement côté client (`AppConstants`). Un client modifié peut spammer Firestore.

**À ajouter** : règle serveur type :
```
allow update: if request.time > resource.data.timestamp + duration.value(3, 's');
```

### H3. iOS ApiClient : `try?` sur le décodage Firestore dans les listeners

**Fichier** : `ios/PouleParty/ApiClient.swift`

Les streams temps réel utilisent `try?` pour décoder les documents Firestore. Si le schéma change ou qu'un document est corrompu, les données sont silencieusement ignorées (nil, tableaux vides) sans aucun log.

| Ligne | Opération |
|-------|-----------|
| ~88 | `try? doc.data(as: Game.self)` dans `findGameByCode` |
| ~119 | `try? document.data(as: ChickenLocation.self)` dans `chickenLocationStream` |
| ~146 | `try? snapshot.data(as: Game.self)` dans `gameConfigStream` |
| ~172 | `try? doc.data(as: HunterLocation.self)` dans `hunterLocationsStream` |

**Action** : remplacer par `do/catch` avec `Logger.error` pour tracer les erreurs de décodage.

### H4. Android : utiliser `stringResource()` dans tous les Composables

**Fichiers concernés** : tous les `*Screen.kt` + `GameInfoDialog.kt` + `GameRulesScreen.kt` + `EndGameCodeScreen.kt`

Les fichiers `strings.xml` (EN) et `values-fr/strings.xml` (FR) existent déjà avec toutes les traductions, mais les écrans utilisent des strings hardcodées au lieu de `stringResource(R.string.xxx)`.

| Fichier | Exemples |
|---------|----------|
| `SelectionScreen.kt` | "START", "Rules", "I am la poule", dialogues password/join |
| `ChickenMapScreen.kt` | "You are the 🐔", "Radius : %dm", "FOUND", "Cancel game" |
| `HunterMapScreen.kt` | "You are the Hunter", "Enter Found Code", "Wrong code" |
| `OnboardingScreen.kt` | Tous les slides (titres + descriptions) |
| `ChickenConfigScreen.kt` | "Game Settings", "Start at", "End at", "Game Mode" |
| `GameRulesScreen.kt` | "How to play", descriptions des modes |
| `GameInfoDialog.kt` | "Game Info", "Mode", "Start", "End" |
| `EndGameCodeScreen.kt` | "Show this code to the hunter" |

---

## MEDIUM - Robustesse & Qualité

### M1. iOS : force unwraps à sécuriser

| Fichier | Ligne | Code | Fix |
|---------|-------|------|-----|
| `ApiClient.swift` | ~49 | `throw lastError!` | `throw lastError ?? URLError(.unknown)` |
| `Victory.swift` | ~243 | `Self.colors.randomElement()!` | `Self.colors.randomElement() ?? .white` |

### M2. iOS : distinguer erreur réseau vs "game not found"

**Fichier** : `ios/PouleParty/Selection.swift` (ligne ~198)

`try? await apiClient.findGameByCode(code)` retourne `nil` que le jeu n'existe pas OU qu'il y ait une erreur réseau. L'utilisateur voit toujours "Game not found".

**Action** : utiliser `do/catch` pour afficher "Erreur réseau" vs "Partie introuvable".

### M3. Android : extraire les magic numbers du cooldown vers AppConstants

**Fichier** : `android/.../ui/huntermap/HunterMapViewModel.kt`

| Valeur | Ligne | Constante proposée |
|--------|-------|--------------------|
| `3` (max tentatives) | ~301 | `CODE_MAX_WRONG_ATTEMPTS` |
| `10_000` (cooldown ms) | ~302 | `CODE_WRONG_ATTEMPT_COOLDOWN_MS` |

Les mêmes valeurs iOS (`HunterMap.swift` lignes ~143-144) sont aussi hardcodées (3 et 10 secondes). Idéalement les centraliser dans les `Constants` des deux plateformes.

### M4. iOS : coordonnées Brussels dupliquées dans Game.swift

**Fichier** : `ios/PouleParty/Game.swift` (ligne ~25)

Le default `initialCoordinates: GeoPoint = .init(latitude: 50.8466, longitude: 4.3528)` est hardcodé alors que `Constants.swift` expose `AppConstants.defaultLatitude/defaultLongitude`.

**Action** : utiliser `GeoPoint(latitude: AppConstants.defaultLatitude, longitude: AppConstants.defaultLongitude)` comme valeur par défaut. Idem côté Android dans `Game.kt`.

### M5. Firestore rules : limiter la taille des tableaux

Les tableaux `winners` et `hunterIds` n'ont pas de limite de taille dans les règles. Un client pourrait les gonfler indéfiniment.

**Action** : ajouter une validation type :
```
request.resource.data.hunterIds.size() <= resource.data.numberOfPlayers
```

### M6. iOS : nettoyer les entrées stale dans Localizable.xcstrings

**Fichier** : `ios/Localizable.xcstrings`

Plusieurs clés ont `"extractionState": "stale"` — elles ne sont plus utilisées dans le code mais restent dans le fichier de localisation.

**Action** : supprimer les entrées stale pour garder le fichier propre.

### M7. Tests manquants — couverture critique

| Composant | Plateforme | Priorité |
|-----------|-----------|----------|
| `VictoryViewModel` | Android | Haute — seul ViewModel sans tests |
| Onboarding flow (permissions, navigation) | iOS | Moyenne — flow critique non testé |
| `FirestoreRepository` / `ApiClient` (retry, decoding) | Les deux | Moyenne — couche données non testée |
| `LocationRepository` / `LocationClient` | Les deux | Basse — dépendances système |

---

## LOW - Polish & Accessibilité

### L1. Accessibilité : contentDescription plus descriptifs (Android)

| Fichier | Élément | Actuel | Mieux |
|---------|---------|--------|-------|
| `SelectionScreen.kt` | Logo | `"Logo"` | `"Poule Party logo"` |
| `ChickenConfigScreen.kt` | Copy icon | `"Copy"` | `"Copy game code to clipboard"` |
| `GameInfoDialog.kt` | Copy icon | `"Copy"` | `"Copy game code to clipboard"` |

De plus, utiliser `stringResource()` pour les contentDescriptions afin qu'elles soient traduites.

### L2. Accessibilité iOS : labels manquants

| Fichier | Élément |
|---------|---------|
| `Victory.swift` | Lignes du leaderboard (HStack medal + nom + temps) — pas de `.accessibilityElement(children: .combine)` |
| `ChickenMap.swift` / `HunterMap.swift` | Boutons de contrôle de la carte |

### L3. iOS : valider le format du game code côté reducer

**Fichier** : `ios/PouleParty/Selection.swift`

Le game code est trimé mais pas validé (devrait être 6 caractères alphanumériques). La validation de longueur du nickname est dans la View (`.prefix(20)`) plutôt que dans le Reducer.

**Action** : déplacer la validation dans le Reducer pour cohérence.

### L4. iOS : centraliser les formatteurs de durée

Les formatteurs de temps (minutes:secondes) sont éparpillés dans :
- `Victory.swift` : `"+\(minutes)m \(String(format: "%02d", seconds))s"`
- `CountdownView.swift` : `String(format: "%02d")`

**Action** : créer un utilitaire `TimeFormatter` partagé.

### L5. Android : `streamJobs` redondant avec `viewModelScope`

**Fichiers** : `ChickenMapViewModel.kt`, `HunterMapViewModel.kt`

Les jobs de streaming sont trackés manuellement dans `streamJobs` puis annulés avec `cancelStreams()`. Or `viewModelScope` annule déjà automatiquement tous ses jobs quand le ViewModel est détruit.

Le tracking manuel reste utile pour l'annulation anticipée (game over mid-game), donc c'est correct. Mais documenter l'intention avec un commentaire pour éviter qu'un futur dev le supprime.

---

## Récapitulatif

| Priorité | # | Effort estimé |
|----------|---|---------------|
| HIGH | 4 | ~2-3h |
| MEDIUM | 7 | ~3-4h |
| LOW | 5 | ~1-2h |
| **Total** | **16** | **~6-9h** |

### Ordre recommandé

1. **H4** — `stringResource()` Android (le plus gros volume, impact localisation)
2. **H3** — `try?` listeners iOS (fiabilité temps réel)
3. **H1 + H2** — Firestore rules (sécurité serveur)
4. **M1 + M2** — Force unwraps + erreur réseau iOS
5. **M3 + M4** — Magic numbers + coordonnées dupliquées
6. **M7** — Tests manquants
7. **M5 + M6** — Firestore array limits + stale strings
8. **L1-L5** — Accessibilité et polish
