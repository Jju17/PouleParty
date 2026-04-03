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

## 2. Notifications push

### Problème

Aucune notification ne semble arriver. Le backend (Cloud Functions) est en place et envoie déjà des notifications localisées via FCM pour 4 événements :
- `chicken_start` — La partie commence (envoyée à la Poule)
- `hunter_start` — La chasse commence (envoyée aux Hunters, après le head start)
- `zone_shrink` — La zone rétrécit (envoyée à tous)
- `hunter_found` — Un hunter a trouvé la Poule (envoyée à tous, via `onGameUpdated`)

### Diagnostic préliminaire (issu de l'audit)

1. **iOS : clés de localisation manquantes** — Les Cloud Functions envoient des `titleLocKey` / `bodyLocKey` (ex: `notif_chicken_start_title`). Côté Android, ces clés existent dans `strings.xml`. Côté **iOS, elles sont absentes** du `Localizable.xcstrings`. Résultat : les notifs arrivent peut-être mais avec un contenu vide.

2. **Enregistrement FCM token** — Le flow est en place sur les 2 plateformes :
   - iOS : `AppDelegate` → `MessagingDelegate` → `FCMTokenManager.saveToken()` + sauvegarde au `signIn` dans `AppFeature`
   - Android : `PouleFCMService.onNewToken()` → `saveTokenToFirestore()`
   - Les tokens sont stockés dans `/fcmTokens/{userId}` avec `token`, `platform`, `updatedAt`

3. **Android : channel `game_events` OK** — Le `NotificationChannel` est créé dans `PoulePartyApp.onCreate()`.

4. **Timing potentiel** — Le FCM token est sauvé quand il est reçu par le SDK, mais si l'auth anonyme n'est pas encore faite à ce moment (`currentUser == null`), le token est perdu silencieusement (les deux plateformes ont un `guard/?: return`).

### Actions à mener

- [ ] **iOS** : Ajouter les clés `notif_chicken_start_title`, `notif_chicken_start_body`, `notif_hunter_start_title`, `notif_hunter_start_body`, `notif_zone_shrink_title`, `notif_zone_shrink_body`, `notif_hunter_found_title`, `notif_hunter_found_body` dans `Localizable.xcstrings`
- [ ] **iOS + Android** : Vérifier dans la console Firebase que les Cloud Tasks sont bien enqueued quand une partie est créée (logs `onGameCreated`)
- [ ] **iOS + Android** : Ajouter un re-save du FCM token après l'auth anonyme (pour couvrir le cas où `onNewToken` est appelé avant `signIn`)
- [ ] **Tester** : Créer une partie, vérifier dans Firestore que le document `fcmTokens/{userId}` existe avec un token valide, puis vérifier les logs Cloud Functions

---

## 3. Réservation de partie payante

### Contexte actuel

- Le bouton "I am la Poule" crée une partie immédiatement (ChickenConfig → ChickenMap)
- Aucune limite de parties par jour ni de joueurs (champ `numberOfPlayers` existe mais default à 10, non enforced)
- Aucune notion de paiement dans le code actuel
- Un système d'événements/inscriptions existe déjà (`registerForEvent` Cloud Function + Google Sheets) mais c'est pour un événement ponctuel, pas pour des parties

### Changements sur le mode gratuit

- Limiter à **1 partie gratuite par jour** par utilisateur (vérifier côté Firestore rules ou Cloud Function)
- Limiter à **max 5 joueurs** par partie gratuite (enforcer dans `hunterIds` rules)

### Nouveau flow "Partie payante"

Deux formules au choix pour le créateur :

#### Formule A — Forfait

Le créateur paie un montant basé sur le nombre de joueurs sélectionné.

- UI : Slider ou picker pour choisir le nombre de joueurs → prix affiché dynamiquement
- Variante "Teambuilding" : Le manager/entreprise paie pour tout le monde → les hunters rejoignent gratuitement
- Les hunters utilisent le code de partie classique pour rejoindre

#### Formule B — Caution + commission

Le créateur paie une caution (~10 €) et définit un prix par joueur.

- Le créateur fixe le tarif par hunter
- PouleParty prend un pourcentage (à définir) sur chaque inscription payante
- Les hunters paient pour rejoindre la partie

### Questions d'implémentation

- **Paiement** : Intégrer Stripe (ou RevenueCat pour simplifier l'in-app). Stripe est plus flexible pour les paiements custom (montants variables, commissions). Un MCP Stripe est disponible dans les outils.
- **Modèle Firestore** : Ajouter au document `Game` :
  - `pricingModel`: `"free"` | `"flat"` | `"deposit"`
  - `pricePerPlayer`: number (centimes)
  - `depositAmount`: number (centimes)
  - `commissionPercent`: number
  - `maxPlayers`: number (enforced)
  - `isPaid`: boolean
- **Cloud Function** : Créer un endpoint `createPaidGame` qui valide le paiement Stripe avant de créer le document Game
- **Firestore rules** : Empêcher la création de parties payantes directement depuis le client (forcer le passage par la Cloud Function)
- **Limite quotidienne (gratuit)** : Query `games` par `creatorId` + `startTimestamp > today 00:00` + `pricingModel == "free"`. Si count >= 1, bloquer.
- **Remboursement** : Que se passe-t-il si la Poule annule ? Si pas assez de hunters rejoignent ? Prévoir une politique de remboursement.
- **UI** : Nouveau screen entre Selection et ChickenConfig, ou bien un mode dans ChickenConfig avec un toggle "Partie payante"
- **Deep link** : Permettre de partager un lien de partie payante pour que les hunters puissent payer et rejoindre directement

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
