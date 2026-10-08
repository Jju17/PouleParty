# Web

React 19, Vite 8, Tailwind 4, TypeScript 7. Landing page, paid registration form, account deletion form, legal pages, in three languages under `/<locale>/<localized-slug>`.

## Commands

```bash
cd web
npm run verify         # typecheck, oxlint, vitest (Testing Library + axe), build
npm run test:coverage
```

## Rules

- Copy lives in `src/i18n/{fr,en,nl}.ts`; `i18n/parity.test.ts` keeps the three in sync. No currency symbol in copy: `formatPrice`.
- Server errors arrive as codes and are translated (`registrationErrors.ts`); never show a raw server message.
- No inline script (CSP): the theme bootstrap lives in `public/boot.js`.
- App Check tokens come from `appCheck.ts`, which retries once with a forced refresh.
