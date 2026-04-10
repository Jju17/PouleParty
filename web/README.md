# Web — Landing Page & Event Registration

React 19 + Vite 7 + Tailwind CSS 4 web app. Marketing site with event registration and an interactive Easter egg.

## Stack

| Package | Version | Purpose |
|---|---|---|
| React | ^19.2.4 | UI framework |
| React Router DOM | ^7.13.1 | Client-side routing |
| Firebase | ^12.11.0 | Auth, Firestore, Cloud Functions, Analytics |
| Tailwind CSS | ^4.2.1 | Utility-first styling |
| Vite | ^7.3.1 | Build tool & dev server |
| TypeScript | ~5.9.3 | Type checking |

## Pages

| Route | File | Description |
|---|---|---|
| `/` | `Home.tsx` | Landing page with hero, app store badges, interactive chicken |
| `/register` | `Register.tsx` | Event registration form (name, email, phone, payment preference) |
| `/privacy` | `Privacy.tsx` | GDPR-compliant privacy policy |
| `/terms` | `Terms.tsx` | Terms of service |
| `/support` | `Support.tsx` | FAQ and support contact |

## Project structure

```
web/
├── public/                     # Static assets (fonts, sprites, icons)
├── src/
│   ├── components/
│   │   ├── Layout.tsx          # Header, footer, nav wrapper
│   │   └── ClickableChicken.tsx # Interactive Easter egg
│   ├── pages/                  # Route pages (Home, Register, Privacy, Terms, Support)
│   ├── i18n/                   # Internationalization (en, fr)
│   ├── main.tsx                # Entry point (React Router setup)
│   ├── firebase.ts             # Firebase config & Cloud Functions client
│   ├── theme.tsx               # Dark/light theme context
│   └── index.css               # Global styles, Tailwind, animations
├── index.html                  # HTML entry with OG tags, PWA meta, dark mode script
├── vite.config.ts
├── tsconfig.json
└── package.json
```

## Firebase integration

- **Project:** `pouleparty-ba586`
- **Region:** `europe-west1`
- **Services:** Anonymous Auth, Firestore, Cloud Functions, Analytics
- **Callable functions:**
  - `registerForEvent()` — form submission with validation
  - `getRegistrationCount()` — returns `{ count, max }`

## Features

- **Dark mode** with localStorage persistence and flash prevention
- **i18n** with English and French (auto-detects browser language)
- **Event registration** with phone validation (BE/FR), duplicate/capacity checks
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
firebase deploy --only hosting
```
