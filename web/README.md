# Web — Landing Page

React 19 + Vite 7 + Tailwind CSS 4 web app. Marketing site, "create a party"
contact pages, and an interactive Easter egg.

## Stack

| Package | Version | Purpose |
|---|---|---|
| React | ^19.2.4 | UI framework |
| React Router DOM | ^7.13.1 | Client-side routing |
| Firebase | ^12.11.0 | Auth, Firestore, Analytics |
| Tailwind CSS | ^4.2.1 | Utility-first styling |
| Vite | ^7.3.1 | Build tool & dev server |
| TypeScript | ~5.9.3 | Type checking |

## Pages

| Route | File | Description |
|---|---|---|
| `/` | `Home.tsx` | Landing page with hero, app store badges, interactive chicken |
| `/privacy` | `Privacy.tsx` | GDPR-compliant privacy policy |
| `/terms` | `Terms.tsx` | Terms of service |
| `/support` | `Support.tsx` | FAQ and support contact |
| `/delete-account` | `DeleteAccount.tsx` | Account-deletion endpoint required by Play Console |
| `/creer-une-partie` | `CreateParty.tsx` | "Want to create a party?" CTA target — FR locale, beta contact info |
| `/create-a-party` | `CreateParty.tsx` | Same, EN locale |
| `/een-feestje-organiseren` | `CreateParty.tsx` | Same, NL locale |
| `/inscription` | `Inscription.tsx` | PP-52 paid event registration wizard (intro → form → recap → Stripe Checkout), FR locale |
| `/registration` | `Inscription.tsx` | Same, EN locale |
| `/inschrijving` | `Inscription.tsx` | Same, NL locale |
| `/inscription/success` | `InscriptionSuccess.tsx` | Stripe success_url landing, FR — confetti + chicken bounce |
| `/registration/success` | `InscriptionSuccess.tsx` | Same, EN locale |
| `/inschrijving/success` | `InscriptionSuccess.tsx` | Same, NL locale |
| `/inscription/cancel` | `InscriptionCancel.tsx` | Stripe cancel_url landing, FR — keeps `?batchId=…` so the retry link works |
| `/registration/cancel` | `InscriptionCancel.tsx` | Same, EN locale |
| `/inschrijving/cancel` | `InscriptionCancel.tsx` | Same, NL locale |

The three `CreateParty` routes (PP-46) and the nine `Inscription*` routes
(PP-52) all share the same components per family; the URL slug pins the
i18n locale (FR / EN / NL) regardless of the visitor's stored preference,
and the header toggle navigates to the matching localized slug via
`Layout.tsx`'s `localizedPathFor` helper. The mobile apps build the
`CreateParty` URL from the device language and open it in the system
browser via the Home "Envie de créer une partie ?" button (see PP-46).
Inscription URLs are shared externally per-event with `?batchId=…`
(e.g. `?batchId=game-06-06-2026`) and POST to the
`createPendingRegistration` Cloud Function via the Firebase Hosting
rewrite `/api/createPendingRegistration` (configured in
root `firebase.json`).

## PP-52 — Universal Link / App Link well-known files

`public/.well-known/apple-app-site-association` (no extension, served as
`application/json` via `firebase.json` `headers`) registers
`pouleparty.be/join` paths for the iOS app (`TEAMID.dev.rahier.pouleparty`).
`public/.well-known/assetlinks.json` registers the Android app
(`dev.rahier.pouleparty2`) against the Play App Signing SHA-256. Both files
are deployed alongside the React bundle whenever you `firebase deploy
--only hosting` — see also the `firebase.json` `ignore` list which had to
drop the legacy `**/.*` glob so the `.well-known/` directory ships.

## Project structure

```
web/
├── public/                     # Static assets (fonts, sprites, icons)
├── src/
│   ├── components/
│   │   ├── Layout.tsx          # Header, footer, nav wrapper
│   │   └── ClickableChicken.tsx # Interactive Easter egg
│   ├── pages/                  # Route pages
│   ├── i18n/                   # Internationalization (en, fr, nl)
│   ├── main.tsx                # Entry point (React Router setup)
│   ├── firebase.ts             # Firebase config
│   ├── theme.tsx               # Dark/light theme context
│   └── index.css               # Global styles, Tailwind, animations
├── index.html                  # HTML entry with OG tags, PWA meta, dark mode script
├── vite.config.ts
├── tsconfig.json
└── package.json
```

## Firebase integration

- **Project:** `pouleparty-ba586` (staging), `pouleparty-prod` (prod)
- **Region:** `europe-west1`
- **Services:** Anonymous Auth, Firestore, Analytics

## Features

- **Dark mode** with localStorage persistence and flash prevention
- **i18n** with English / French / Dutch (auto-detected from browser, overridable per page via URL)
- **Interactive Easter egg** — click the chicken through progressive phases (fire, lasers, nuke, egg hatching)

## Build & run

```bash
npm install
npm run dev       # Dev server with HMR
npm run build     # TypeScript check + production bundle → dist/
npm run preview   # Serve built output locally
```

## Deploy

```bash
firebase deploy --only hosting --project pouleparty-ba586
firebase deploy --only hosting --project pouleparty-prod
```
