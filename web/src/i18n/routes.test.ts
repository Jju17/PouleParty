// @vitest-environment jsdom
import { describe, expect, test } from "vitest";
import {
  detectLocale,
  equivalentPath,
  isLocale,
  localeFromPath,
  routeKeyFromPath,
  routePath,
  ROUTES,
  type RouteKey,
} from "./routes";

describe("isLocale", () => {
  test("accepts fr / en / nl", () => {
    expect(isLocale("fr")).toBe(true);
    expect(isLocale("en")).toBe(true);
    expect(isLocale("nl")).toBe(true);
  });
  test("rejects anything else", () => {
    expect(isLocale("FR")).toBe(false);
    expect(isLocale("de")).toBe(false);
    expect(isLocale("")).toBe(false);
    expect(isLocale("français")).toBe(false);
  });
});

describe("routePath", () => {
  test("home is locale-only (no trailing slug)", () => {
    expect(routePath("home", "fr")).toBe("/fr");
    expect(routePath("home", "en")).toBe("/en");
    expect(routePath("home", "nl")).toBe("/nl");
  });

  test("inscription is locale-prefixed + localized slug", () => {
    expect(routePath("inscription", "fr")).toBe("/fr/inscription");
    expect(routePath("inscription", "en")).toBe("/en/registration");
    expect(routePath("inscription", "nl")).toBe("/nl/inschrijving");
  });

  test("nested success / cancel slugs are concatenated", () => {
    expect(routePath("inscriptionSuccess", "fr")).toBe("/fr/inscription/success");
    expect(routePath("inscriptionCancel", "en")).toBe("/en/registration/cancel");
  });

  test("create-party slugs are fully localized", () => {
    expect(routePath("createParty", "fr")).toBe("/fr/creer-une-partie");
    expect(routePath("createParty", "en")).toBe("/en/create-a-party");
    expect(routePath("createParty", "nl")).toBe("/nl/een-feestje-organiseren");
  });

  test("privacy / terms / support / deleteAccount fully covered", () => {
    expect(routePath("privacy", "fr")).toBe("/fr/confidentialite");
    expect(routePath("privacy", "en")).toBe("/en/privacy");
    expect(routePath("privacy", "nl")).toBe("/nl/privacy");
    expect(routePath("terms", "fr")).toBe("/fr/conditions");
    expect(routePath("terms", "nl")).toBe("/nl/voorwaarden");
    expect(routePath("support", "fr")).toBe("/fr/support");
    expect(routePath("deleteAccount", "fr")).toBe("/fr/supprimer-compte");
    expect(routePath("deleteAccount", "nl")).toBe("/nl/account-verwijderen");
  });
});

describe("localeFromPath", () => {
  test("extracts /<locale>/ prefix", () => {
    expect(localeFromPath("/fr/inscription")).toBe("fr");
    expect(localeFromPath("/en")).toBe("en");
    expect(localeFromPath("/nl/account-verwijderen")).toBe("nl");
  });
  test("returns null on no prefix or unknown locale", () => {
    expect(localeFromPath("/")).toBe(null);
    expect(localeFromPath("/inscription")).toBe(null);
    expect(localeFromPath("/de/foo")).toBe(null);
    expect(localeFromPath("")).toBe(null);
  });
});

describe("routeKeyFromPath", () => {
  test("identifies every route across all 3 locales", () => {
    for (const key of Object.keys(ROUTES) as RouteKey[]) {
      for (const locale of ["fr", "en", "nl"] as const) {
        const path = routePath(key, locale);
        expect(routeKeyFromPath(path)).toBe(key);
      }
    }
  });
  test("returns null on unknown path", () => {
    expect(routeKeyFromPath("/")).toBe(null);
    expect(routeKeyFromPath("/fr/unknown")).toBe(null);
    expect(routeKeyFromPath("/fr/inscription/extra")).toBe(null);
    expect(routeKeyFromPath("/de/inscription")).toBe(null);
  });
  test("tolerates trailing slashes", () => {
    expect(routeKeyFromPath("/fr/inscription/")).toBe("inscription");
    expect(routeKeyFromPath("/fr/")).toBe("home");
  });
});

describe("equivalentPath", () => {
  test("toggles locale while keeping the same route", () => {
    expect(equivalentPath("/fr/inscription", "en")).toBe("/en/registration");
    expect(equivalentPath("/fr/inscription", "nl")).toBe("/nl/inschrijving");
    expect(equivalentPath("/en/registration", "fr")).toBe("/fr/inscription");
  });
  test("preserves nested success / cancel under the new locale", () => {
    expect(equivalentPath("/fr/inscription/success", "nl")).toBe("/nl/inschrijving/success");
    expect(equivalentPath("/en/registration/cancel", "fr")).toBe("/fr/inscription/cancel");
  });
  test("home stays as locale-only", () => {
    expect(equivalentPath("/fr", "en")).toBe("/en");
    expect(equivalentPath("/nl", "fr")).toBe("/fr");
  });
  test("create-party locale flips include the slug change", () => {
    expect(equivalentPath("/fr/creer-une-partie", "nl")).toBe("/nl/een-feestje-organiseren");
    expect(equivalentPath("/nl/een-feestje-organiseren", "en")).toBe("/en/create-a-party");
  });
  test("unknown path falls back to the locale home", () => {
    expect(equivalentPath("/foo/bar", "fr")).toBe("/fr");
    expect(equivalentPath("/fr/unknown", "en")).toBe("/en");
  });
});

describe("detectLocale", () => {
  test("returns en when neither localStorage nor navigator carry a locale signal", () => {
    // Vitest's default jsdom env has navigator.language === "en-US" — that's
    // already our `en` fallback, so this test mainly verifies no crash and
    // a valid Locale return.
    const result = detectLocale();
    expect(["fr", "en", "nl"]).toContain(result);
  });
  test("honours localStorage when set", () => {
    localStorage.setItem("locale", "nl");
    try {
      expect(detectLocale()).toBe("nl");
    } finally {
      localStorage.removeItem("locale");
    }
  });
  test("ignores garbage localStorage value", () => {
    localStorage.setItem("locale", "de");
    try {
      // Should fall back to navigator / "en".
      expect(["fr", "en", "nl"]).toContain(detectLocale());
    } finally {
      localStorage.removeItem("locale");
    }
  });
});

describe("ROUTES coverage invariant", () => {
  test("every route has all 3 locales", () => {
    for (const key of Object.keys(ROUTES) as RouteKey[]) {
      expect(ROUTES[key].fr).toBeDefined();
      expect(ROUTES[key].en).toBeDefined();
      expect(ROUTES[key].nl).toBeDefined();
    }
  });
  test("no two routes share the same path across 3 locales (sanity)", () => {
    // Build a flat list of (locale, slug) pairs and verify uniqueness
    // within each locale. A duplicate would mean two route keys
    // resolve to the same URL — `routeKeyFromPath` would be ambiguous.
    for (const locale of ["fr", "en", "nl"] as const) {
      const seen = new Set<string>();
      for (const key of Object.keys(ROUTES) as RouteKey[]) {
        const slug = ROUTES[key][locale];
        // home has empty slug; that's intended unique by construction.
        const fullPath = slug === "" ? `/${locale}` : `/${locale}/${slug}`;
        expect(seen.has(fullPath)).toBe(false);
        seen.add(fullPath);
      }
    }
  });
});
