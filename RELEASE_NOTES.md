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
