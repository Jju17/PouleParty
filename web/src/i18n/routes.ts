// PP-99 — Single source of truth for i18n URL routing on the web app.
//
// Slug map per route × locale + four pure helpers (routePath /
// localeFromPath / routeKeyFromPath / equivalentPath) that the React
// Router config, the I18nProvider, the header toggle and the SEO
// hreflang injector all consume.
//
// Slugs are LOCALIZED — `/fr/inscription` ≠ `/en/registration` ≠
// `/nl/inschrijving`. Best for SEO + UX in a multilingual market
// (Belgium FR + NL). The locale prefix is always present (`/fr/...`,
// `/en/...`, `/nl/...`); the root `/` is 301-redirected to the
// detected locale by `<LocaleRedirect />`.
//
// When adding a route: extend `RouteKey` + add an entry in `ROUTES`
// + register the React component in `main.tsx`'s `PAGE_COMPONENTS`.
// No path string-literal anywhere else.

export type Locale = "fr" | "en" | "nl";

export const LOCALES: readonly Locale[] = ["fr", "en", "nl"];

export type RouteKey =
  | "home"
  | "inscription"
  | "inscriptionSuccess"
  | "inscriptionCancel"
  | "createParty"
  | "privacy"
  | "terms"
  | "support"
  | "deleteAccount";

/** Slug suffix after `/<locale>/`. Empty string = locale-only path
 *  (the home route, served at `/fr`, `/en`, `/nl`). */
export const ROUTES: Record<RouteKey, Record<Locale, string>> = {
  home: { fr: "", en: "", nl: "" },
  inscription: {
    fr: "inscription",
    en: "registration",
    nl: "inschrijving",
  },
  inscriptionSuccess: {
    fr: "inscription/success",
    en: "registration/success",
    nl: "inschrijving/success",
  },
  inscriptionCancel: {
    fr: "inscription/cancel",
    en: "registration/cancel",
    nl: "inschrijving/cancel",
  },
  createParty: {
    fr: "creer-une-partie",
    en: "create-a-party",
    nl: "een-feestje-organiseren",
  },
  privacy: {
    fr: "confidentialite",
    en: "privacy",
    nl: "privacy",
  },
  terms: {
    fr: "conditions",
    en: "terms",
    nl: "voorwaarden",
  },
  support: {
    fr: "support",
    en: "support",
    nl: "support",
  },
  deleteAccount: {
    fr: "supprimer-compte",
    en: "delete-account",
    nl: "account-verwijderen",
  },
};

export function isLocale(value: string): value is Locale {
  return value === "fr" || value === "en" || value === "nl";
}

/** Full pathname for a (key, locale) — e.g. `routePath("inscription", "fr")` → `/fr/inscription`. */
export function routePath(key: RouteKey, locale: Locale): string {
  const slug = ROUTES[key][locale];
  return slug === "" ? `/${locale}` : `/${locale}/${slug}`;
}

/** Returns the locale prefix at segment 1 if it's one of fr|en|nl. */
export function localeFromPath(pathname: string): Locale | null {
  const segment = pathname.split("/")[1] ?? "";
  return isLocale(segment) ? segment : null;
}

/** Identify which route the pathname matches. Strips the locale prefix
 *  and looks the tail up in the slug map. */
export function routeKeyFromPath(pathname: string): RouteKey | null {
  const locale = localeFromPath(pathname);
  if (!locale) return null;
  const tail = pathname
    .slice(`/${locale}`.length)
    .replace(/^\/+/, "")
    .replace(/\/+$/, "");
  for (const key of Object.keys(ROUTES) as RouteKey[]) {
    if (ROUTES[key][locale] === tail) return key;
  }
  return null;
}

/** Toggle target: jump from `/fr/inscription` to `/nl/inschrijving`.
 *  If the current path isn't a known route, fall back to the locale's
 *  home so the toggle never lands on a 404. */
export function equivalentPath(pathname: string, newLocale: Locale): string {
  const key = routeKeyFromPath(pathname);
  if (key === null) return `/${newLocale}`;
  return routePath(key, newLocale);
}

/** Detect the visitor's preferred locale from localStorage > navigator
 *  > `en` fallback. Used by `<LocaleRedirect />` on `/` and on any
 *  unknown path that hits the wildcard route. */
export function detectLocale(): Locale {
  if (typeof localStorage !== "undefined") {
    try {
      const stored = localStorage.getItem("locale");
      if (stored && isLocale(stored)) return stored;
    } catch {
      // Safari private mode / hardened storage throws on read.
    }
  }
  if (typeof navigator !== "undefined") {
    const browser = navigator.language.slice(0, 2);
    if (isLocale(browser)) return browser;
  }
  return "en";
}
