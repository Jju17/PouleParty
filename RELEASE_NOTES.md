# Beta testers announcement (WhatsApp)

🐔🎉 **Poule Party est dispo sur l'App Store !**

Quelques mois de taf pour en arriver là. En gros ce qu'on peut faire :

🗺️ **Jeu de chasse en extérieur** — 1 Poule (qui se cache et fuit) vs les Chasseurs (qui doivent la trouver physiquement) dans une zone GPS qui rétrécit progressivement sur une vraie carte
🎮 **Deux modes de jeu** — soit la zone suit la Poule, soit elle est fixée à l'avance et dérive toute seule
⚡ **Power-ups** — 6 pouvoirs à ramasser sur la map (invisibilité, radar, brouillage de signal, faux signal, gel de zone, preview de la prochaine zone)
📳 Feedback haptique sur les moments clés, notifications push localisées, écran qui reste allumé pendant les parties
🔥 Parties gratuites ou payantes (forfait ou caution + commission)

👉 https://apps.apple.com/us/app/poule-party/id6738432103

Téléchargez, organisez une partie et faites péter vos retours 🏃💨🐔

---

# Release 1.6.0

## What's New

### ⚡ Power-ups rebuilt server-side
- Power-ups are now spawned by a Cloud Function, not by the Chicken's phone
- No more missed spawns when the Chicken's device is backgrounded, offline, or crashes
- Road-snapping still happens — just on the server now (via Mapbox Directions)
- Activation is atomic: power-up state and the game's active effects update together in a single transaction

### 🎯 Pulsing collection disc
- Each power-up on the map now shows a pulsing disc in its neon color
- Way easier to spot from a distance, and more fun to walk into

### 🔐 Smarter onboarding after account reset
- If your anonymous Firebase identity is invalidated, the app sends you back through onboarding to set a nickname tied to your new identity

### ♻️ Under the hood
- Firebase SDKs upgraded (admin 13, functions 7) — 3 security vulnerabilities fixed
- Android: new shared neon glow helper matching iOS behavior pixel-for-pixel
- Android: power-up map rendering factored into a shared composable
- 3 flaky Android tests stabilized

### 🐛 Fixes
- Power-ups no longer disappear from the map after a regression in the map annotation rendering
- Smaller iOS icon files (kept zero alpha)

---

## App Store "What's New" (copy-paste)

<en>
Power-ups got a major upgrade: they're now spawned reliably by the server, so no more missed spawns if the Chicken's phone sleeps or loses signal. Each power-up now pulses on the map with its own neon color — way easier to spot. Smarter auto-recovery if your account gets reset. Plus a bunch of under-the-hood polish.
</en>

<fr>
Gros coup de jeune sur les power-ups : ils sont désormais générés par le serveur, donc plus aucun raté si le téléphone de la Poule s'endort ou perd la connexion. Chaque power-up pulse maintenant sur la carte avec sa couleur néon — bien plus facile à repérer. Reprise automatique plus intelligente si ton compte est réinitialisé. Et plein de petits polish sous le capot.
</fr>

<nl>
Power-ups kregen een grote upgrade: ze worden nu betrouwbaar door de server gespawnd, dus geen gemiste spawns meer als de telefoon van de Kip slaapt of verbinding verliest. Elke power-up pulseert nu op de kaart in zijn eigen neonkleur — veel makkelijker te zien. Slimmer automatisch herstel als je account wordt gereset. Plus allerlei afwerking onder de motorkap.
</nl>

---

## Google Play Release Notes (copy-paste format)

<en-US>
Power-ups are now spawned server-side — no more missed spawns if the Chicken's phone sleeps. Each power-up pulses on the map in its neon color, way easier to spot. Smarter auto-recovery if your account is reset. Plus SDK upgrades, security fixes, and UI polish.
</en-US>

<fr-FR>
Les power-ups sont désormais générés côté serveur — plus aucun raté si le téléphone de la Poule s'endort. Chaque power-up pulse sur la carte avec sa couleur néon, bien plus visible. Reprise automatique plus intelligente après réinitialisation de compte. Mises à jour SDK, corrections de sécurité et polish UI.
</fr-FR>

<nl-NL>
Power-ups worden nu serverzijdig gespawnd — geen gemiste spawns meer als de telefoon van de Kip slaapt. Elke power-up pulseert op de kaart in zijn neonkleur, veel zichtbaarder. Slimmer automatisch herstel na account-reset. Plus SDK-upgrades, beveiligingsfixes en UI-afwerking.
</nl-NL>

---

## Version Info

| Platform | Version | Build |
|---|---|---|
| iOS | 1.6.0 | 8 |
| Android | 1.6.0 | 15 |

---

# Release 1.5.0

## What's New

### 🎯 New: Challenges feature
- Hunters can now complete fun side-challenges during a game and earn points
- Live team leaderboard with gold/silver/bronze podium
- Validate a challenge → send your proof on WhatsApp → row turns neon green

### 🗺️ Location permission UX
- "Open Settings" button when location access is denied (onboarding + Home)

### 🔔 Push notifications
- Localized titles and bodies now properly resolved on iOS and Android

### ♻️ Under the hood
- Large codebase cleanup and reorganization on iOS and Android
- Launch Screen logo fixed (was invisible after the previous logo rename)
- Cross-platform data model parity (Winner timestamp type aligned)

---

## App Store "What's New" (copy-paste)

<en>
Play with challenges! Your friends hunting the Chicken can now complete fun side-quests during a game and race up a team leaderboard. Also: clearer "Open Settings" button when you deny location, localized push notifications, and a big internal cleanup. Bug fix: the launch screen logo is back.
</en>

<fr>
Les défis débarquent ! Les chasseurs peuvent maintenant relever des défis fun pendant la partie et grimper au classement des équipes. Aussi : bouton « Ouvrir les Réglages » plus clair quand on refuse la localisation, notifications push localisées, et gros nettoyage interne. Correction : le logo du launch screen est de retour.
</fr>

<nl>
Uitdagingen zijn er! Jagers kunnen nu tijdens een spel leuke zijopdrachten voltooien en klimmen in de team-ranglijst. Ook: duidelijkere "Open Instellingen"-knop bij geweigerde locatietoegang, gelokaliseerde pushnotificaties, en een grote interne opschoonbeurt. Bugfix: het logo op het launch-scherm is terug.
</nl>

---

## Google Play Release Notes (copy-paste format)

<en-US>
Challenges! Hunters can now complete fun side-quests during a game and climb the team leaderboard. Plus: "Open Settings" button on location-denied prompts, localized push notifications, logo fix on the launch screen.
</en-US>

<fr-FR>
Des défis ! Les chasseurs peuvent relever des défis fun pendant la partie et grimper au classement des équipes. Aussi : bouton « Ouvrir les Réglages » quand on refuse la localisation, notifications push localisées, logo du launch screen corrigé.
</fr-FR>

<nl-NL>
Uitdagingen! Jagers kunnen tijdens een spel leuke zijopdrachten voltooien en klimmen in de team-ranglijst. Plus: "Open Instellingen"-knop bij geweigerde locatietoegang, gelokaliseerde pushnotificaties, logo op het launch-scherm hersteld.
</nl-NL>

---

## Version Info

| Platform | Version | Build |
|---|---|---|
| iOS | 1.5.0 | 7 |
| Android | 1.5.0 | 14 |

---

# Release 1.4.1

## What's New

### 🐛 Hotfix
- Fixed user profile migration not running on first launch after update — nickname and FCM token are now correctly written to Firestore

---

## Google Play Release Notes (copy-paste format)

<en-US>
Bug fix: user profile migration now runs correctly after update. Your nickname is properly saved to the cloud.
</en-US>

<fr-FR>
Correction : la migration du profil utilisateur fonctionne maintenant correctement après la mise à jour. Votre pseudo est bien sauvegardé dans le cloud.
</fr-FR>

<nl-NL>
Bugfix: gebruikersprofielmigratie werkt nu correct na de update. Je bijnaam wordt correct opgeslagen in de cloud.
</nl-NL>

---

## Version Info

| Platform | Version | Build |
|---|---|---|
| iOS | 1.4.1 | 6 |
| Android | 1.4.1 | 13 |

---
---

# Release 1.4.0

## What's New

### 📳 Haptic feedback
- Feel the game! Vibrations on countdown ticks, power-up collection/activation, out-of-zone warnings, code validation, winner found, and game over
- Both iOS and Android

### 🔒 Screen stays on
- The display no longer turns off during gameplay on the chicken or hunter map

### 🗺️ Power-up markers redesigned (Android)
- Power-ups on the map now show as colored circles with white icons, matching the iOS design

### 👤 User profiles on Firestore
- Nickname is now saved to Firestore when set during onboarding or changed in settings
- FCM tokens and nicknames are stored together in `/users/{userId}`

### 🔄 Silent migration
- Existing users are automatically migrated to the new `/users` collection on first launch — no re-onboarding needed

### 🐛 Fixes
- Fixed dark edges on the leaderboard sheet on iOS

---

## Store Descriptions

### App Store (iOS)

**What's New (EN):**
Feel the game with haptic feedback! Vibrations on countdown, power-ups, zone warnings, and more. Screen now stays on during gameplay. Your nickname is saved to the cloud. Fixed leaderboard display.

**Quoi de neuf (FR):**
Ressentez le jeu avec le retour haptique ! Vibrations au compte à rebours, power-ups, alertes de zone, et plus. L'écran reste allumé en jeu. Votre pseudo est sauvegardé dans le cloud. Correction de l'affichage du classement.

**Wat is er nieuw (NL):**
Voel het spel met haptische feedback! Trillingen bij aftellen, power-ups, zonewaarschuwingen en meer. Scherm blijft aan tijdens het spel. Je bijnaam wordt opgeslagen in de cloud. Leaderboard weergave hersteld.

### Google Play (Android)

**What's New (EN):**
Haptic feedback brings the game to life — feel countdown ticks, power-up pickups, zone warnings, and more. Screen stays on during gameplay. Power-up markers redesigned with colored icons. Nickname now saved to the cloud.

**Quoi de neuf (FR):**
Le retour haptique donne vie au jeu — ressentez le compte à rebours, les power-ups, les alertes de zone, et plus. L'écran reste allumé en jeu. Marqueurs de power-ups redessinés. Pseudo sauvegardé dans le cloud.

**Wat is er nieuw (NL):**
Haptische feedback brengt het spel tot leven — voel het aftellen, power-ups, zonewaarschuwingen en meer. Scherm blijft aan tijdens het spel. Power-up markers opnieuw ontworpen. Bijnaam wordt nu opgeslagen in de cloud.

---

## Google Play Release Notes (copy-paste format)

<en-US>
Feel the game with haptic feedback! Vibrations on countdown, power-ups, zone warnings, and more. Screen stays on during gameplay. Power-up markers redesigned with colored icons. Nickname now saved to the cloud.
</en-US>

<fr-FR>
Ressentez le jeu avec le retour haptique ! Vibrations au compte à rebours, power-ups, alertes de zone, et plus. L'écran reste allumé en jeu. Marqueurs de power-ups redessinés. Pseudo sauvegardé dans le cloud.
</fr-FR>

<nl-NL>
Voel het spel met haptische feedback! Trillingen bij aftellen, power-ups, zonewaarschuwingen en meer. Scherm blijft aan tijdens het spel. Power-up markers opnieuw ontworpen. Bijnaam wordt nu opgeslagen in de cloud.
</nl-NL>

---

## Version Info

| Platform | Version | Build |
|---|---|---|
| iOS | 1.4.0 | 5 |
| Android | 1.4.0 | 12 |
