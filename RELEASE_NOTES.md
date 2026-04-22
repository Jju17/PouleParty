# Release 1.9.0

> ⚠️ **Do not paste this "Summary" paragraph into any store field.** Only the blocks explicitly labelled **App Store Connect**, **Google Play Console**, or **App Review Notes** below are store-safe. Apple rejected 1.8.1 (1) because an "Android" mention slipped into What's New, keep the two stores' copy strictly separate.

**Summary (internal, do not paste):** reliability + permission-parity pass. Onboarding now requires "Always" location on both platforms (matches what the game actually needs when the phone is pocketed), failed victory writes show a Retry prompt instead of silently shipping to the leaderboard, and a pile of under-the-hood fixes, jammer determinism, Stripe webhook dedup race, Mapbox retry, cross-platform parity tests (584 iOS / 74 Functions / full Android pass).

---

## 📱 App Store Connect, field "What's New in This Version"

ASC uses **plain text per locale** (switch the language tab in the top-right of the ASC page and paste the matching block). No XML-style tags, tags are Play Console only. **Do NOT mention "Android", "Google Play", or any other platform** (guideline 2.3.10 → straight rejection).

**English (U.S.)**

Onboarding now requires "Always Allow" for location, the Chicken needs it to keep broadcasting position to the Hunters while the phone is in a pocket, which the previous "While Using" setting silently broke. If you previously chose "While Using", the app will prompt you to upgrade on your next game.

Also: a Retry button now appears if the network drops while you're registering the winning code (no more silent "did it save?" moments), and the under-the-hood reliability is tightened across payments, power-up spawning, and background tracking.

**French**

L'onboarding exige désormais l'accès "Toujours autoriser" pour la localisation, la Poule en a besoin pour continuer à partager sa position avec les Chasseurs quand le téléphone est dans une poche, ce que le mode "Pendant l'utilisation" cassait silencieusement. Si tu avais choisi "Pendant l'utilisation", l'app te proposera de passer à "Toujours" à la prochaine partie.

Aussi : un bouton Réessayer apparaît maintenant si le réseau lâche au moment d'enregistrer le code gagnant (fini les doutes sur "ça a bien été sauvé ?"). Plus de la fiabilité en coulisses sur les paiements, le spawn des power-ups et le tracking en arrière-plan.

**Dutch**

Onboarding vereist nu "Altijd toestaan" voor locatie, de Kip heeft het nodig om de positie te blijven delen met Jagers wanneer de telefoon in je zak zit, wat "Tijdens gebruik" stilletjes brak. Als je eerder "Tijdens gebruik" koos, zal de app je vragen om te upgraden bij je volgende partij.

Ook: een Opnieuw-knop verschijnt nu als het netwerk uitvalt terwijl je de winnende code registreert (geen stille "is het opgeslagen?"-momenten meer). Verder veel achtergrond-betrouwbaarheid rond betalingen, power-up-spawning en tracking.

---

## 📱 App Store Connect, field "Promotional Text"

iOS-only field, max **170 characters per locale**, editable anytime without a new submission. Evergreen pitch, reusing the 1.8.1 copy since it still fits.

**English (U.S.)** · 154 chars

Real-world GPS hide-and-seek. One Chicken hides, the rest chase inside a shrinking zone. Power-ups, a 6-char code to share, play with your squad outdoors.

**French** · 157 chars

Cache-cache GPS dans la vraie vie. Une Poule se cache, les autres la chassent dans une zone qui rétrécit. Power-ups, code à 6 caractères, entre potes dehors.

**Dutch** · 158 chars

GPS-verstoppertje in het echte leven. Eén Kip verstopt zich, Jagers zoeken in een krimpende zone op de kaart. Power-ups, 6-cijferige code, buiten met je crew.

---

## 🤖 Google Play Console, field "Release notes"

Separate from App Store. Paste only into Play Console, never into App Store Connect (Apple forbids cross-platform references). Android mentions are fine here.

<en-US>
Onboarding now requires "Allow all the time" for location, the Chicken needs it to keep broadcasting with the phone in a pocket, which Fine-only silently broke. You'll be prompted to upgrade on your next game.

Also: Retry button if your winning-code submission fails mid-flight, more resilient power-up spawning on network hiccups, screens now tear down cleanly so background Firestore streams don't linger.
</en-US>

<fr-FR>
L'onboarding exige désormais la localisation "Toujours autoriser", la Poule en a besoin pour continuer à émettre avec le téléphone en poche, ce que l'accès Fine seul cassait silencieusement. Mise à jour proposée à la prochaine partie.

Aussi : bouton Réessayer si l'enregistrement du code gagnant foire en plein vol, spawn de power-ups plus solide face aux hoquets réseau, écrans proprement nettoyés à la fermeture.
</fr-FR>

<nl-NL>
Onboarding vereist nu "Altijd toestaan" voor locatie, de Kip heeft het nodig om te blijven uitzenden met de telefoon in de zak, wat Fine-alleen stilletjes brak. Upgrade-prompt bij je volgende partij.

Ook: Opnieuw-knop als je winnende-code-inzending midden in de lucht faalt, sterker power-up-spawning bij netwerkhikjes, schermen worden netjes opgeruimd.
</nl-NL>

---

## 📝 App Store Connect, field "App Review Information → Notes"

Mandatory for this build, it touches the paid Stripe flow (webhook hardening, Forfait + Caution unchanged functionally) and tightens the location permission requirement. Paste verbatim:

```
Thank you for reviewing 1.9.0.

(1) Location permission change. Onboarding now requires "Always Allow" to proceed past the location slide (1.8.1 accepted "While Using"). The game genuinely needs background location, the Chicken role keeps broadcasting its position to the Hunters while the phone is pocketed during an active game, and the stayInTheZone mode has a timer-driven location write that only fires when the app is backgrounded. The existing NSLocationAlwaysAndWhenInUseUsageDescription string (unchanged from 1.8.1 (2)) already explains this scenario with a concrete example ("the Chicken can run through the neighbourhood with the screen off while the Hunters watch the zone follow them on their map. Background location stops as soon as the game ends."). The tightened gate ensures every player reaches the game with a consistent permission state, otherwise the Chicken role silently stops broadcasting once the phone screen locks, which defeats the core mechanic.

(2) Apple Pay, unchanged from 1.8.1 (2). Stripe's PaymentSheet remains the integration point for card, Bancontact, and Apple Pay. The test instructions are verbatim:

To test without real money, apply the promo code APPLE_REVIEW_99 on the Forfait recap screen (99%-off coupon maintained specifically for review). The PaymentSheet opens with the Apple Pay button visible for a negligible amount, you do not need to complete the transaction; the button's presence confirms the integration.

Two paid flows reachable from the main screen:
 a. Create a paid game: tap "Create Party" → pick the "Forfait" plan → complete the wizard → tap "Pay" on the recap.
 b. Join a paid game: tap "Start" → enter a 6-character code for a game whose creator chose the "Caution" plan → validate team name → tap "Pay".

If no card is configured in the device's Apple Wallet the Stripe SDK hides the Apple Pay button, this is by design. Apple Pay sandbox cards (https://developer.apple.com/apple-pay/sandbox-testing/) work for opening the Apple Pay sheet.

Merchant ID: merchant.dev.rahier.pouleparty (registered in our Apple Developer account).
Entitlement: com.apple.developer.in-app-payments is enabled.
Code reference: ios/PouleParty/Features/Payment/PaymentFeature.swift, PaymentSheet.Configuration.applePay = .init(merchantId: "merchant.dev.rahier.pouleparty", merchantCountryCode: "BE").

(3) What changed in this build (user-visible):
- Location permission now requires "Always" at onboarding (see point 1).
- Retry prompt if a winning-code submission fails due to connection loss (previously the client silently navigated to the leaderboard without a server record).

Under-the-hood reliability (not visible in QA, mentioned for completeness since the webhook path was touched):
- Stripe webhook deduplication hardened (atomic Firestore transaction, 3-state claim machine, 5-minute stale-claim window). Behaviour is unchanged in the happy path; the fix only affects concurrent duplicate deliveries.
- Mapbox road-snap now retries transient 5xx/429 with exponential backoff and bails on persistent 4xx without burning retries.
- Jammer noise is now deterministic (seeded from the game's driftSeed bucketed per second) instead of Double.random / Math.random.

We can provide a screen recording on request.
```

---

## Version Info (internal)

| Platform | Version | Build |
|---|---|---|
| iOS | 1.9.0 | 3 |
| Android | 1.9.0 | 26 |

---

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

# Release 1.8.2 (Android hotfix)

> ⚠️ **Do not paste this "Summary" paragraph into any store field.** Only the blocks explicitly labelled **Google Play Console** below are store-safe. **Do not submit anything new to Apple** for 1.8.2 — iOS is not affected by this bug and stays on 1.8.1 (2).

**Summary (internal, do not paste):** emergency fix for the Mapbox crash that 1.8.1 (24) had on every map screen in release builds. Android-only.

---

## 🤖 Google Play Console — field "Release notes"

Android-only, paste into Play Console. No App Store equivalent (iOS keeps 1.8.1).

<en-US>
Fix: the map screens crashed on first open in release builds. Sorry for the wait — this rebuild is a clean hotfix, nothing else changed.
</en-US>

<fr-FR>
Correctif : les écrans avec la carte crashaient à l'ouverture. Désolé pour l'attente — ce rebuild est un simple hotfix, rien d'autre ne change.
</fr-FR>

<nl-NL>
Fix: de kaartschermen crashten bij het openen. Sorry voor het wachten — deze rebuild is enkel een hotfix, verder verandert er niets.
</nl-NL>

---

## Version Info (internal)

| Platform | Version | Build |
|---|---|---|
| iOS | unchanged (1.8.1) | unchanged (2) |
| Android | 1.8.2 | 25 |

---

# Release 1.8.1

> ⚠️ **Do not paste this "Summary" paragraph into any store field.** Only the blocks explicitly labelled **App Store Connect**, **Google Play Console**, or **App Review Notes** below are store-safe. Apple rejected a previous submission because this summary was pasted into the App Store "What's New" field — it mentioned "Android" which Apple forbids in App Store copy (guideline 2.3.10).

**Summary (internal, do not paste):** a proper success moment after you pay — confetti, code ready to share, live countdown.

---

## 📱 App Store Connect — field "What's New in This Version"

ASC uses **plain text per locale** (switch the language tab in the top-right of the ASC page and paste the matching block). No XML-style tags — tags are Play Console only.

**English (U.S.)**

After you create a paid game or register with a deposit, you finally get a proper success screen — confetti, the game code ready to share, a live countdown to start, and a status that updates in real time as soon as the payment is confirmed.

**French**

Quand tu crées une partie payante ou t'inscris avec une caution, tu obtiens enfin un vrai écran de confirmation — confettis, code de partie prêt à partager, countdown live jusqu'au start, et un statut qui se met à jour en direct dès que le paiement est confirmé.

**Dutch**

Na een betaalde aanmaak of registratie met waarborg krijg je eindelijk een echt bevestigingsscherm — confetti, spelcode klaar om te delen, live aftelling tot de start, en een status die live bijwerkt zodra je betaling bevestigd is.

---

## 📱 App Store Connect — field "Promotional Text"

iOS-only field, max **170 characters per locale**, editable anytime without a new submission. Evergreen pitch — swap for a campaign without resubmitting. Plain text, no tags.

**English (U.S.)** · 154 chars

Real-world GPS hide-and-seek. One Chicken hides, the rest chase inside a shrinking zone. Power-ups, a 6-char code to share, play with your squad outdoors.

**French** · 157 chars

Cache-cache GPS dans la vraie vie. Une Poule se cache, les autres la chassent dans une zone qui rétrécit. Power-ups, code à 6 caractères, entre potes dehors.

**Dutch** · 158 chars

GPS-verstoppertje in het echte leven. Eén Kip verstopt zich, Jagers zoeken in een krimpende zone op de kaart. Power-ups, 6-cijferige code, buiten met je crew.

---

## 🤖 Google Play Console — field "Release notes"

Separate from App Store. Paste only into Play Console, never into App Store Connect (Apple forbids "Android" references).

<en-US>
After a paid game creation or deposit registration, a full success screen with confetti, the game code ready to share, a live countdown, and a status that updates in real time as soon as the payment is confirmed.
</en-US>

<fr-FR>
Après la création d'une partie payante ou une inscription avec caution, un écran de succès complet avec confettis, code de partie prêt à partager, countdown live, et un statut qui se met à jour dès que le paiement est confirmé.
</fr-FR>

<nl-NL>
Na een betaalde aanmaak of registratie met waarborg: een volledig succesoverzicht met confetti, deelbare spelcode, live aftelling, en een status die live bijwerkt zodra je betaling bevestigd is.
</nl-NL>

---

## 📝 App Store Connect — field "App Review Information → Notes"

For the 1.8.1 **resubmit** (after the 2.3.10 / 2.1 / 5.1.1(ii) rejection on build 1). Paste verbatim:

```
Thank you for the review.

(1) Android references in What's New — removed. The "What's New" text for 1.8.1 is now strictly iOS-only with no mention of any other platform.

(2) Apple Pay visibility. Apple Pay is presented by Stripe's drop-in PaymentSheet during the app's two paid flows:
  a. Create a paid game — from the main screen tap "Create Party", then on the plan-selection sheet pick "Forfait" (the middle paid plan), complete the short wizard and tap "Pay" on the recap step. The Stripe PaymentSheet opens and, on a device with a configured Apple Pay wallet, an Apple Pay button is shown at the top of the sheet next to card and Bancontact.
  b. Join a paid game — from the main screen tap "Start", enter a 6-character code for a game whose creator chose the "Caution" (deposit) plan. After validating your team name, tap "Pay". The same PaymentSheet opens and the Apple Pay button is shown if the device wallet is configured.

To test without real money: apply the promo code APPLE_REVIEW_99 on the Forfait recap screen (99%-off coupon we created specifically for review). The PaymentSheet still opens with the Apple Pay button visible for a negligible amount — you do not need to complete the transaction, the button's presence confirms the integration.

If no card is set up in the device's Apple Wallet the Stripe SDK hides the Apple Pay button — this is by design. Apple Pay sandbox cards (https://developer.apple.com/apple-pay/sandbox-testing/) do work for opening the Apple Pay sheet.

Merchant ID: merchant.dev.rahier.pouleparty (registered in our Apple Developer account).
Entitlement: com.apple.developer.in-app-payments is enabled.
Code reference: ios/PouleParty/Features/Payment/PaymentFeature.swift — PaymentSheet.Configuration.applePay = .init(merchantId: "merchant.dev.rahier.pouleparty", merchantCountryCode: "BE").

We can provide a screen recording on request.

(3) Location purpose strings. NSLocationWhenInUseUsageDescription and NSLocationAlwaysAndWhenInUseUsageDescription have been rewritten to describe the specific use and provide a concrete example of how the location data is used (Hunters seeing the Chicken's live position during an active game, Chicken running with the phone in a pocket while the zone keeps following them). The "Always" justification explicitly states that tracking stops as soon as the game ends.

Test account for QA is available on request.
```

---

## Version Info (internal)

| Platform | Version | Build |
|---|---|---|
| iOS | 1.8.1 | 2 |
| Android | 1.8.1 | 24 |

---

# Release 1.8.0

> ⚠️ **Do not paste the "Summary" paragraph below into any store field.** Only the blocks explicitly labelled **App Store Connect** and **Google Play Console** are store-safe.

**Summary (internal, do not paste):** privacy, moderation and safety pass — report a player from the leaderboard, safety reminder before first game, full account deletion, and background location now matches the latest mobile platform requirements.

---

## 📱 App Store Connect — field "What's New in This Version"

**English (U.S.)**

Report a player directly from the leaderboard, new safety tips before your first game, and account deletion now fully removes your profile. Background location handling updated to match the latest iOS requirements.

**French**

Signale un joueur directement depuis le classement, nouveaux conseils de sécurité avant ta première partie, et la suppression de compte efface maintenant tout ton profil. Gestion de la localisation en arrière-plan mise à jour pour les dernières exigences d'iOS.

**Dutch**

Rapporteer een speler rechtstreeks vanuit het klassement, nieuwe veiligheidstips vóór je eerste spel, en het verwijderen van je account wist nu ook volledig je profiel. Achtergrondlocatie bijgewerkt volgens de nieuwste iOS-vereisten.

---

## 🤖 Google Play Console — field "Release notes"

<en-US>
Report a player from the leaderboard, new safety tips before your first game, and account deletion now fully wipes your profile. Background location handling upgraded for Android 14+.
</en-US>

<fr-FR>
Signale un joueur depuis le classement, nouveaux conseils de sécurité avant ta première partie, et la suppression de compte efface maintenant tout ton profil. Gestion de la localisation en arrière-plan mise à jour pour Android 14+.
</fr-FR>

<nl-NL>
Rapporteer een speler vanuit het klassement, nieuwe veiligheidstips vóór je eerste spel, en accountverwijdering wist nu je volledige profiel. Achtergrondlocatie bijgewerkt voor Android 14+.
</nl-NL>

---

## Version Info (internal)

| Platform | Version | Build |
|---|---|---|
| iOS | 1.8.0 | 4 |
| Android | 1.8.0 | 22 |

---

# Release 1.7.1

## What's New

Apple Pay (iOS) and Google Pay (Android) are now available at checkout, on top of card and Bancontact. One-tap payment for anyone with a wallet set up.

---

## App Store "What's New" (copy-paste)

<en>
Apple Pay is now available at checkout — pay in one tap with Face ID or Touch ID, on top of card and Bancontact.
</en>

<fr>
Apple Pay est désormais disponible au paiement — en un tap avec Face ID ou Touch ID, en plus de carte et Bancontact.
</fr>

<nl>
Apple Pay is nu beschikbaar bij het afrekenen — betaal met één tap via Face ID of Touch ID, naast kaart en Bancontact.
</nl>

---

## Google Play Release Notes (copy-paste format)

<en-US>
Google Pay is now available at checkout — pay in one tap, on top of card and Bancontact.
</en-US>

<fr-FR>
Google Pay est disponible au paiement — en un tap, en plus de carte et Bancontact.
</fr-FR>

<nl-NL>
Google Pay is nu beschikbaar bij het afrekenen — betaal met één tap, naast kaart en Bancontact.
</nl-NL>

---

## Version Info

| Platform | Version | Build |
|---|---|---|
| iOS | 1.6.3 | 11 |
| Android | 1.6.3 | 18 |

---

# Release 1.6.2

## What's New

Critical iOS fix: power-ups were invisible on the map since 1.6.0 because of a silent decode failure. Fixed both on the server (Cloud Function now writes an explicit `id` field) and on the client (iOS decoder now injects the document ID before decoding, so legacy docs still work).

---

## App Store "What's New" (copy-paste)

<en>
Fix: power-ups are visible on the map again — a silent decode error since 1.6.0 caused the iOS map to show no power-ups at all. Sorry!
</en>

<fr>
Correctif : les power-ups réapparaissent sur la carte — une erreur de décodage silencieuse depuis la 1.6.0 les rendait invisibles sur iOS. Désolé !
</fr>

<nl>
Fix: power-ups zijn weer zichtbaar op de kaart — een stille decodeerfout sinds 1.6.0 zorgde ervoor dat er op iOS helemaal geen power-ups verschenen. Sorry!
</nl>

---

## Google Play Release Notes (copy-paste format)

<en-US>
Fix: power-ups reappear on the map after a silent iOS decode bug introduced in 1.6.0.
</en-US>

<fr-FR>
Correctif : les power-ups réapparaissent sur la carte après un bug de décodage silencieux sur iOS introduit en 1.6.0.
</fr-FR>

<nl-NL>
Fix: power-ups zijn weer zichtbaar op de kaart na een stille iOS-decodeerfout in 1.6.0.
</nl-NL>

---

## Version Info

| Platform | Version | Build |
|---|---|---|
| iOS | 1.6.2 | 10 |
| Android | 1.6.2 | 17 |

---

# Release 1.6.1

## What's New

Translation pass: about 70 FR and NL strings fixed across iOS, Android, and the website — clearer terminology, consistent tutoiement, and idiomatic Dutch. Dutch (NL) added to the web landing page.

### 🇫🇷 Français
- "Code de trouvaille" → **Code de capture**, "Autant pour moi" → **Laisse tomber**
- Tutoiement harmonisé (notifs, onboarding, alertes)
- "Ouvrir les paramètres" partout (cohérent avec l'OS)

### 🇳🇱 Nederlands
- **Vangstcode** voor de gevonden code, **Meedoen aan spel** (au lieu du "Spel deelnemen" grammaticalement cassé)
- **FINAAL**, **Bevestigen**, **Klassement bekijken**, **Opnieuw proberen**
- Idiomen opgeschoond (**1 dag van tevoren**, **Adres zoeken**, …)
- Neue **NL-locale op de website** (landing page)

---

## App Store "What's New" (copy-paste)

<en>
Translation polish. Dozens of French and Dutch strings got a clean-up pass: clearer terminology, consistent casual tone, and more natural Dutch phrasing. No functional changes.
</en>

<fr>
Petit lifting des traductions : des dizaines de textes en français et néerlandais ont été repris — terminologie plus claire, tutoiement cohérent, formulations plus naturelles en néerlandais. Aucun changement de fonctionnement.
</fr>

<nl>
Vertalingen opgepoetst. Tientallen Franse en Nederlandse teksten zijn bijgewerkt: duidelijker terminologie, consistente casual toon en natuurlijker Nederlands. Geen functionele wijzigingen.
</nl>

---

## Google Play Release Notes (copy-paste format)

<en-US>
Translation polish: dozens of French and Dutch strings reviewed for consistency and tone. No functional changes.
</en-US>

<fr-FR>
Lifting des traductions : des dizaines de textes en français et néerlandais revus pour plus de cohérence et un ton uniforme. Aucun changement de fonctionnement.
</fr-FR>

<nl-NL>
Vertalingen opgepoetst: tientallen Franse en Nederlandse teksten herzien voor samenhang en toon. Geen functionele wijzigingen.
</nl-NL>

---

## Version Info

| Platform | Version | Build |
|---|---|---|
| iOS | 1.6.1 | 9 |
| Android | 1.6.1 | 16 |

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
