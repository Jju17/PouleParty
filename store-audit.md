# PouleParty — Store Compliance Audit (App Store + Play Store)

**Date** : 2026-05-18
**Cible** : Soumission 1.13.0 pour D-Day **2026-06-06**
**Méthode** : 3 agents Claude parallèles avec WebSearch des guidelines officielles 2026
**Périmètre** : iOS App Store Review Guidelines + Google Play Developer Program Policies + GDPR / consumer law / Stripe legal posture / deeplink integrity

---

## 0. TL;DR — Bloquants à fixer AVANT de soumettre

Ces 10 items casseraient soit la review (rejet certain), soit la flow utilisateur à D-Day. À traiter dans cet ordre.

| # | ID | Plateforme | Sujet |
|---|----|----|-------|
| 1 | XPLAT-B1 | Privacy / GDPR | Privacy Policy ne mentionne pas Stripe / Resend / Google Sheets → breach GDPR Art. 13 + Data Safety form mismatch |
| 2 | XPLAT-B2 | Legal | Terms of Service ne contient aucune clause paiement/remboursement/loi applicable/CRD Art. 16(l) pour les 12 € du D-Day |
| 3 | iOS-B1 / XPLAT-B4 | iOS | Deux `PrivacyInfo.xcprivacy` divergents — l'ancien (resources/) sous-déclare les data types collectés |
| 4 | AND-B1 / XPLAT-H7 | Android | `assetlinks.json` ne liste qu'une SHA-256 → si c'est l'upload key, le deeplink email D-Day est cassé en prod |
| 5 | AND-B2 | Android | Manque `dataExtractionRules` Android 12+ → fuites via Auto-Backup + D2D |
| 6 | iOS-B3 | iOS | 4 `INFOPLIST_KEY_NSLocation*` dupliqués dans pbxproj (mergés à build-time) → contradictions avec `Info.plist` manuel + `NSLocationUsageDescription` macOS dans target iOS |
| 7 | iOS-B2 | iOS | Token Mapbox `pk.*` non-restreint en bundle ID → drain quota possible |
| 8 | iOS-H4 | iOS | App Attest entitlement manquant → App Check va silently fail dès qu'on active l'enforce mobile |
| 9 | iOS-H3 | iOS | Widget extension `IPHONEOS_DEPLOYMENT_TARGET = 26.2` alors que host = 17.0 → Live Activities cassée sur ~30 % de la base installée |
| 10 | XPLAT-H5 | Web | Pas de checkbox consent Terms/Privacy avant paiement Stripe → CRD Art. 8(2) violation |

---

## 1. BLOCKING (14 findings)

### iOS — 4 bloquants

**iOS-B1. Deux `PrivacyInfo.xcprivacy` divergents**
- Fichiers : `ios/PouleParty/PrivacyInfo.xcprivacy` (riche, 104 lignes) et `ios/PouleParty/Resources/PrivacyInfo.xcprivacy` (stale, 72 lignes)
- L'ancien marque UserID / DeviceID `Linked=false` (faux — l'UID Firebase Auth est lié aux records de gameplay) et omet nickname / Crashlytics / ProductInteraction
- Xcode bundle un seul à build-time, non-déterministe selon machines
- **Fix** : supprimer `Resources/PrivacyInfo.xcprivacy` ; vérifier le restant est dans le "Copy Bundle Resources" du target PouleParty ; **ajouter aussi un PrivacyInfo.xcprivacy au target PoulePartyWidgetsExtension** (Live Activities ship comme exécutable séparé, Apple valide les deux)
- Vérif post-fix : `unzip -l PouleParty.ipa | grep xcprivacy` doit retourner 1 fichier par binaire

**iOS-B2. Token Mapbox `pk.*` non-restreint exposé dans `Info.plist`**
- `Info.plist:20-21` : `MBXAccessToken = pk.eyJ1Ijoiamp1MTciLCJhIjoi…`
- Le format `pk.*` est OK pour distribuer client-side, mais le token actuel n'a aucune restriction de bundle ID configurée côté Mapbox dashboard
- **Fix** :
  1. Rotate le token sur https://account.mapbox.com/access-tokens/
  2. Restreindre le nouveau au bundle ID `dev.rahier.pouleparty` (Token settings → "Allowed bundle IDs")
  3. Faire la même chose côté Android (`res/values/strings.xml#mapbox_access_token`)
  4. Coller le nouveau dans `Info.plist` et `strings.xml`

**iOS-B3. 4 `INFOPLIST_KEY_NSLocation*` dans pbxproj merges avec `Info.plist` manuel**
- `project.pbxproj:777-778` (Debug) + `:834-835` (Release)
- Le projet a `GENERATE_INFOPLIST_FILE = YES` ET `INFOPLIST_FILE = PouleParty/Info.plist` → mode merge
- 4 keys redondants `INFOPLIST_KEY_NSLocation*` (dont `NSLocationUsageDescription` qui est macOS-only et `NSLocationAlwaysUsageDescription` iOS-11-deprecated)
- Deux sources de vérité avec wording légèrement différent (`"shrinking game zone"` Info.plist vs `"shrinking play zone"` pbxproj)
- **Fix** : Build Settings → search "Location" → supprimer les 4 entrées avec icône "set in this target", garder uniquement le `Info.plist` manuel

**iOS-B4. App Review Notes manquantes pour `NSLocationAlways*` (D-Day)**
- Reviewers tournent l'app ~3 min en sandbox ; sans guide pour atteindre le prompt Always, guideline 2.5.4 + 5.1.1(i) tirent
- **Fix** : dans `RELEASE_NOTES.md` section `## 📝 App Store Connect — field "App Review Information → Notes"` pour 1.13.0, ajouter le path pour atteindre le prompt Always (Create a game → 1 min head start, 5 min duration → place start zone → Follow the Chicken → Start). Format documenté dans CLAUDE.md `"App Review Notes"` rule.

### Android — 2 bloquants

**AND-B1. `assetlinks.json` SHA-256 probablement upload key au lieu de App Signing key**
- `web/public/.well-known/assetlinks.json:8` : une seule SHA-256 (`32:95:…29:DF`)
- Avec Play App Signing (obligatoire AAB), la binaire installée via Play Store est signée par la **clé Google-managed**, pas la upload key. Si la SHA-256 publiée est l'upload key, autoVerify échoue et **le deeplink email D-Day s'ouvre dans Chrome au lieu de l'app pour tous les payants**
- **Fix** :
  1. Play Console > Setup > App integrity > App signing key certificate → copier la SHA-256
  2. Updater `assetlinks.json` pour inclure **les 2 fingerprints** (App signing key + upload key) — tableau JSON avec les 2 valeurs
  3. Redeploy web hosting prod
  4. Vérif : `adb shell pm get-app-links dev.rahier.pouleparty2` doit retourner `verified` (pas `legacy_failure`)

**AND-B2. Manque `dataExtractionRules` Android 12+**
- `AndroidManifest.xml:14` : `android:allowBackup="true"` sans `android:dataExtractionRules`
- Avec target SDK 35, Auto-Backup default + D2D embarquent `SharedPreferences` (nickname, FCM/auth state) sans filtre
- **Fix** : créer `app/src/main/res/xml/data_extraction_rules.xml` avec `<cloud-backup>` et `<device-transfer>` blocks excluant les SharedPreferences sensibles ; référencer via `android:dataExtractionRules="@xml/data_extraction_rules"` dans `<application>`

### Cross-platform — 4 bloquants

**XPLAT-B1. Privacy Policy ne mentionne PAS Stripe / Resend / Google Sheets**
- `web/src/i18n/en.ts:16-93` (+ fr/nl) : la Policy actuelle dit `"No email, name, or personal account is required"` ce qui contredit le formulaire d'inscription qui collecte exactement ces fields
- GDPR Art. 13(1)(c)/(e) : recipients nommés obligatoires AVANT collecte
- **Fix** :
  - Ajouter section "Paid event registrations" dans `dataCollected` listant : nom, équipe, email, phone, taille équipe, IP, locale, Stripe session ID, code 6-char
  - Ajouter Stripe (`stripe.com/privacy`), Resend (`resend.com/legal/privacy-policy`), Google (Sheets API — `policies.google.com/privacy`) à `thirdParties`
  - État de la rétention : `/eventRegistrations` gardé 12 mois post-event puis purgé

**XPLAT-B2. Terms of Service vide pour le paiement événement**
- `web/src/i18n/en.ts:115-152` : Terms parle seulement du jeu gratuit, rien sur les 12 €
- Manque : partie contractante, prix total, **CRD Art. 16(l) opt-out cooling-off 14 jours** (event daté), politique de remboursement, force majeure, loi applicable (Belgique/Bruxelles), **lien ODR EU** (obligatoire B2C trader EU)
- **Fix** : ajouter section "Paid event tickets" dans Terms couvrant tout ça. Invoquer explicitement Art. 16(l) CRD pour bloquer les remboursements de dernière minute. Footer : link ODR (`ec.europa.eu/consumers/odr`).

**XPLAT-B3. AASA `Content-Type` à vérifier après build Vite**
- `firebase.json:9-17` route correctement, mais si Vite filtre `.well-known/*` (filenames `.`-prefixed parfois ignorés), le SPA catch-all sert `index.html` avec `text/html` au lieu de `application/json` → Universal Link cassé silencieusement
- iOS cache l'échec une fois install — un user qui a installé avant le fix ne récupère pas le lien sans réinstall
- **Fix** : après next deploy, `curl -I https://pouleparty.be/.well-known/apple-app-site-association` doit retourner 200 + `Content-Type: application/json`. Idem `assetlinks.json`. Si Vite drop `.well-known`, override `publicDir` ou postbuild copy.

**XPLAT-B4. = iOS-B1** (deux PrivacyInfo.xcprivacy divergents — déjà couvert)

---

## 2. HIGH (23 findings)

### iOS — 7 HIGH

| ID | Sujet | Fix court |
|----|-------|-----------|
| iOS-H1 | `ENABLE_APP_SANDBOX=YES` (macOS-only, injecte un entitlement noise) | Supprimer × 2 configs |
| iOS-H2 | `ENABLE_USER_SELECTED_FILES=readonly` (macOS sandbox) | Supprimer × 2 configs |
| iOS-H3 | Widget min iOS = 26.2 vs host = 17.0 → Live Activities cassée sur 25-30 % de la base | Bisser widget à `IPHONEOS_DEPLOYMENT_TARGET = 17.0` |
| iOS-H4 | Manque `com.apple.developer.devicecheck.appattest-environment` → App Attest fail dès enforce mobile | Ajouter dans `PouleParty.entitlements` + build setting `APPATTEST_ENVIRONMENT = development/production` × 2 configs + capability "App Attest" dans Apple Dev Portal |
| iOS-H5 | Required Reason APIs Firebase/Mapbox couverts par leurs propres manifests — pas de changement aujourd'hui mais surveiller à chaque ajout de code direct file-API |
| iOS-H6 | Bouton "Delete My Data" → renommer "Delete Account" pour guideline 5.1.1(v) | Edit `Settings.swift:170` + `SettingsSections.swift:139` + xcstrings en/fr/nl + web mirror |
| iOS-H7 | `applinks:pouleparty.be` entitlement — vérifier que l'AASA n'a pas de redirect chain | `curl -sI` post-deploy |

### Android — 8 HIGH

| ID | Sujet | Fix court |
|----|-------|-----------|
| AND-H1 | Background location declaration form (Play Console) : vidéo + justification écrite obligatoires avant D-Day | Enregistrer 30-60s vidéo unlisted YouTube + feuille justification |
| AND-H2 | Prominent disclosure background location trop generic en onboarding | Réécrire `location_partial` / `location_denied` (en/fr/nl) : mentionner explicitement "GPS uploaded to Firebase Firestore, shared real-time with other players, tracking stops at game end" |
| AND-H3 | Intent-filter App Links manque `www.pouleparty.be` (certains email clients préfixent www) | Ajouter `<data android:host="www.pouleparty.be" .../>` + assetlinks.json sur www. |
| AND-H4 | Token Mapbox non-restreint (cf iOS-B2) | Restreindre côté Mapbox dashboard pour `dev.rahier.pouleparty2` + Play App Signing SHA-1 |
| AND-H5 | = XPLAT-B1 (Privacy Policy missing Stripe/Resend/Sheets) |
| AND-H6 | Page web account-deletion = mailto seul → Play 2024+ veut self-service form | Replace par form (name + email + reason + reCAPTCHA → Cloud Function `processAccountDeletion`) |
| AND-H7 | UGC reporting OK mais blocking manquant (Play UGC policy) | Soit ajouter "Block this player" client-side filter, soit justifier ephemerality dans Content Rating questionnaire |
| AND-H8 | Network Security Config sans pinning + sans debug-overrides | Ajouter `<debug-overrides>` block pour build local emulator (10.0.2.2) et optionnellement `<pin-set>` pour les 4 hosts prod |

### Cross-platform — 8 HIGH

| ID | Sujet | Fix court |
|----|-------|-----------|
| XPLAT-H1 | Route `/delete-account` peut-être non-déclarée dans React Router → URL Play Console 404 | Vérifier `<Route path="/delete-account">` dans main.tsx et `curl -I` doit retourner 200 |
| XPLAT-H2 | Privacy Policy promet erasure complet mais code in-app garde nickname dans winners/registrations | Soit ajouter Cloud Function `processAccountDeletion(uid)` qui scrub toutes les références, soit rewrite la Policy pour clarifier que les past games gardent le team name indéfiniment |
| XPLAT-H3 | Privacy Policy ne décrit pas comment exercer Art. 20 (portability) | Soit ajouter callable CF `requestMyData` (JSON export), soit préciser dans `rightPortabilityText` le format livré sous 30j |
| XPLAT-H4 | DKIM/SPF/DMARC `pouleparty.be` non-vérifié → Gmail/Outlook 2026 risquent quarantine des emails Resend | Resend dashboard vérifier domain ; ajouter DMARC TXT `p=quarantine` avec `rua` ; tester avec `mail-tester.com` |
| XPLAT-H5 | Pas de checkbox consent Terms/Privacy avant paiement Stripe → CRD Art. 8(2) | Ajouter checkbox required step recap : `[ ] I accept the Terms and Privacy Policy. I understand this is a dated event (CRD Art. 16(l))`. Stocker timestamp consent dans `/eventRegistrations` |
| XPLAT-H6 | Children's Privacy âge 16 incohérent avec Belgique GDPR-K (13) + pas d'age-gate pour event payant | Trancher : 18+ pour event payant + 13+ pour app gratuite, mirror dans Apple age rating + Play content rating questionnaire |
| XPLAT-H7 | = AND-B1 (assetlinks.json single fingerprint) |
| XPLAT-H8 | Google Sheet plaintext PII partagé entre staging + prod compute SA | Séparer staging/prod Sheet IDs ; restreindre sharing minimum ; purge rows post-D-Day ; mentionner rétention dans Privacy |

---

## 3. MEDIUM (20 findings)

Polish + best-practice gaps — à attaquer après les BLOCKING/HIGH.

### iOS — 5 MEDIUM
- iOS-M1 `Localizable.xcstrings` — 5 entries `state: "new"`, vérifier qu'elles sont toutes `shouldTranslate: false` ou traduites (Apple Guideline 4.5.5)
- iOS-M2 `UISupportedInterfaceOrientations_iPad` mais `TARGETED_DEVICE_FAMILY = 1` → ligne morte
- iOS-M3 Triplicats `[sdk=iphoneos*]` / `[sdk=iphonesimulator*]` dans pbxproj — bruit
- iOS-M4 `NSUserActivityTypes` avec `NSUserActivityTypeBrowsingWeb` techniquement no-op (type système) — laisser tel quel ou nettoyer
- iOS-M5 Mapbox token + Privacy manifest — couvert par SDK manifests

### Android — 8 MEDIUM
- AND-M1 `fastlane/metadata/` quasi-vide (changelog versionCode 11 vs current 37) — soit supprimer le dossier soit le remplir
- AND-M2 Locales fastlane `fr-FR`/`nl-NL` mais marché D-Day = Belgique (`fr-BE`/`nl-BE`) → ajouter les listings Belgium en Play Console
- AND-M3 `applicationId = pouleparty2` vs `namespace = pouleparty` documenté (footgun reviewers/devs)
- AND-M4 **Critique en pratique** : product flavors `staging`/`production` vides → AAB byte-identical → staging pollue Firestore prod en tests Play pre-launch. Ajouter `applicationIdSuffix = ".staging"` + `google-services.json` séparé par flavor
- AND-M5 `kapt` deprecated, migrer KSP (Hilt 2.53+, gain de build × 2-3)
- AND-M6 Compose BOM / Firebase BOM / activity-compose / lifecycle / nav stale (5+ mois) — bumper après stabilisation
- AND-M7 Vérifier 16 KB alignment des `.so` Mapbox/Firebase/Crashlytics dans l'AAB final (deadline 31 mai 2026 pour uploads API 35+)
- AND-M8 `LocationForegroundService.NOTIFICATION_ID = 42` collision potentielle avec `PouleFCMService` counter → bumper à valeur high (>1M)

### Cross-platform — 7 MEDIUM
- XPLAT-M1 AASA `appIDs` ne couvre pas une future second target (widget Live Activity) — à noter pour follow-up
- XPLAT-M2 Stripe Apple Pay button visibility — note interne pour App Review information
- XPLAT-M3 iOS `applinks:` entitlement ne liste pas les staging hosts → can't fully QA staging → ajouter `applinks:pouleparty-ba586.web.app`
- XPLAT-M4 Privacy "International Data Transfers" omet Stripe (US) + Resend (US) → ajouter dans en/fr/nl
- XPLAT-M5 Privacy / Terms `lastUpdated = May 12, 2026` mais on est le 18 → bumper après fixes B1/B2
- XPLAT-M6 Callables `validateRegistrationCode` / `lookupGameByValidationCode` sans rate-limit per UID/IP → token bucket dans `/rateLimits/{uid_or_ip}` doc avec TTL
- XPLAT-M7 Mapbox token (lié à iOS-B2 / AND-H4) — vérifier URL/bundle restrictions Mapbox dashboard

---

## 4. LOW (15 findings) — polish, no blocker

iOS L1-L4, Android L1-L5, Cross-platform L1-L6. Détails dans les rapports d'agents originaux.

Highlights :
- AND-L1 Verify icon 512×512 hi-res Play upload avec alpha-on (inverse de l'icon iOS qui veut alpha-off)
- AND-L2 FCM default notification icon = `ic_launcher` (full color) au lieu d'un silhouette monochrome → status bar white square cosmetic bug
- AND-L3 `Log.d` du code validation en prod (low risk mais Play security scan peut flagger)
- AND-L4 ProGuard `-keep class com.google.firebase.** { *; }` trop large
- iOS-L1 Universal Link handler ignore non-pouleparty.be hosts — comportement correct
- XPLAT-L1 Resend HTML template `escapeHtml` correct
- XPLAT-L3 Pas de cookie banner — ajouter mention "strictly-necessary only" dans Privacy

---

## 5. Confirmations positives (rien à faire, juste à savoir)

- ✅ Email contact `julien@rahier.dev` cohérent partout (jamais `rahier.julien@gmail.com`)
- ✅ Date D-Day `2026-06-06` cohérente dans CLAUDE.md + Inscription + Resend email
- ✅ Firestore rules `/eventRegistrations` correctement locked `if false` (CRIT-1 audit précédent)
- ✅ Honeypot + App Check enforce + Stripe idempotency key + webhook amount cross-check (CRIT-4 + HIGH-4/5 audit précédent)
- ✅ Stripe pour event physique : Apple Guideline 3.1.3(b) exempt IAP (physical goods rule, confirmed 2025 update)
- ✅ App Privacy Manifest (root) — déclarations complètes
- ✅ Export compliance `ITSAppUsesNonExemptEncryption = false` (skip App Encryption Documentation prompt)
- ✅ `SUPPORTS_MACCATALYST = NO` correctement set
- ✅ Web `firebase.json` Hosting headers HSTS+CSP+X-Frame-Options DENY (HIGH-20 audit précédent)
- ✅ Mode `staging` Firebase séparé `pouleparty-ba586` / prod `pouleparty-prod`

---

## 6. Plan d'exécution recommandé

### Sprint 1 (J-19 → J-13, semaine 2026-05-18)

**Objectif : éliminer les 14 BLOCKING + 5 HIGH critiques.**

**Backend / Web (script + git)** :
1. **XPLAT-B1** réécrire Privacy `dataCollected` + `thirdParties` (en/fr/nl) — Stripe/Resend/Sheets/retention
2. **XPLAT-B2** ajouter section "Paid event tickets" Terms (en/fr/nl) + CRD Art. 16(l) + ODR link
3. **XPLAT-H5** checkbox consent step recap Inscription + timestamp `/eventRegistrations`
4. **XPLAT-H1** vérifier route `/delete-account` dans main.tsx + `curl -I`
5. **XPLAT-H2** soit Cloud Function `processAccountDeletion` qui scrub, soit reword Privacy
6. **XPLAT-H4** Resend domain verification + DMARC TXT
7. **AND-B1 / XPLAT-H7** : add 2 SHA-256 dans `assetlinks.json` + redeploy
8. **XPLAT-B3** verify AASA Content-Type post-build (`curl -I`)
9. **XPLAT-M5** bumper `lastUpdated` Privacy + Terms
10. **XPLAT-H8** : split staging/prod Google Sheet IDs

**iOS (Xcode + code)** :
11. **iOS-B1** supprimer `Resources/PrivacyInfo.xcprivacy` + ajouter manifest widget extension
12. **iOS-B2** rotate Mapbox token + restrict bundle ID
13. **iOS-B3** supprimer 4 `INFOPLIST_KEY_NSLocation*` × 2 configs dans pbxproj
14. **iOS-B4** draft App Review Notes 1.13.0 dans `RELEASE_NOTES.md`
15. **iOS-H1+H2** delete 6 lignes macOS sandbox dans pbxproj
16. **iOS-H3** widget `IPHONEOS_DEPLOYMENT_TARGET = 17.0`
17. **iOS-H4** ajouter App Attest entitlement + build settings `APPATTEST_ENVIRONMENT` + capability Apple Dev Portal
18. **iOS-H6** rename "Delete My Data" → "Delete Account" (Settings.swift + SettingsSections.swift + xcstrings + DeleteAccount.tsx)

**Android (gradle + xml + Play Console)** :
19. **AND-B1** mêmes 2 SHA-256 que XPLAT-H7 (déjà fait en backend step 7)
20. **AND-B2** créer `data_extraction_rules.xml` + référence Manifest
21. **AND-H1** vidéo justification background location pour Play Console form
22. **AND-H2** reword `location_partial` / `location_denied` strings (en/fr/nl) — explicit Firebase + real-time sharing
23. **AND-H3** add `www.pouleparty.be` intent-filter
24. **AND-H4** = iOS-B2 (Mapbox restriction — fait une fois côté dashboard)
25. **AND-H6** form `delete-account` (lié à XPLAT-H1)
26. **AND-H7** "Block player" feature OU justifier ephemerality
27. **AND-H8** add `<debug-overrides>` network_security_config

### Sprint 2 (J-13 → J-6)

**Objectif : MEDIUM critiques + flavors fix.**

- AND-M4 product flavors staging/prod (CRUCIAL : staging build pollue Firestore prod sinon)
- AND-M7 vérifier 16 KB alignment AAB
- Tous les MEDIUM iOS + Android
- XPLAT-M6 rate-limit callables

### Sprint 3 (post-D-Day) : LOW + polish

Tous les LOW + cleanup.

---

## 7. Métriques

| | iOS | Android | Cross-platform | Total |
|--|--|--|--|--|
| BLOCKING | 4 | 2 | 4 | **14** (-3 doublons = 11 uniques) |
| HIGH | 7 | 8 | 8 | **23** (-2 doublons) |
| MEDIUM | 5 | 8 | 7 | **20** |
| LOW | 4 | 5 | 6 | **15** |
| **Total** | **20** | **23** | **25** | **~65** |

Sources principales :
- Apple App Review Guidelines 2026 (`developer.apple.com/app-store/review/guidelines/`)
- Apple Privacy Manifest docs (`developer.apple.com/documentation/bundleresources/privacy-manifest-files`)
- Google Play Developer Program Policies 2026 (`support.google.com/googleplay/android-developer/`)
- Google Play Data Safety (`support.google.com/googleplay/android-developer/answer/10787469`)
- Google Play Account deletion 2024+ (`support.google.com/googleplay/android-developer/answer/13327111`)
- GDPR Art. 13/17/20/32 + CRD Art. 16(l) + Belgian Data Protection Act
- Stripe SSA + Resend DMARC docs
- iOS Universal Links / Android App Links verification docs

---

*Audit produit le 2026-05-18 via 3 agents Claude parallèles avec WebSearch des guidelines officielles. Les détails granulaires (file:line + citations URLs) sont dans les rapports d'agents originaux ; ce document est la consolidation priorisée. Companion de `audit.md`.*
