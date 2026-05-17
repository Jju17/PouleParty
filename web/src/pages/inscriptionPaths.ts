// PP-52 — Shared mapping between the 3 localized inscription slugs and
// our i18n locale codes. Mirrored server-side in
// `functions/src/registrations.ts` (`successPathForLocale`) so Stripe's
// redirect lands on the slug matching the visitor's chosen language.

export type InscriptionLocale = "fr" | "en" | "nl";

interface BasePath {
  base: string;
  locale: InscriptionLocale;
}

const PATH_LOCALES: BasePath[] = [
  { base: "/inscription", locale: "fr" },
  { base: "/registration", locale: "en" },
  { base: "/inschrijving", locale: "nl" },
];

const FALLBACK = PATH_LOCALES[0];

/** Derive the language we should pin the page to from the current URL. */
export function localeFromPathname(pathname: string): InscriptionLocale {
  const match = PATH_LOCALES.find((p) => pathname.startsWith(p.base));
  return (match ?? FALLBACK).locale;
}

/** Derive the inscription base slug for a given locale (for back/retry links). */
export function basePathForLocale(locale: string): string {
  const match = PATH_LOCALES.find((p) => p.locale === locale);
  return (match ?? FALLBACK).base;
}
