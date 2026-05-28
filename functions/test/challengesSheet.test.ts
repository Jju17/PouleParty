import { describe, expect, test } from "vitest";
import {
  categoryOf,
  endOfGameValidation,
  extractGameId,
  groupByCategory,
  pickLocalized,
  renderHtml,
  requiresId,
  resolveLocale,
  type ChallengeDoc,
  type Locale,
} from "../src/challengesSheet";

function challenge(overrides: Partial<ChallengeDoc> = {}): ChallengeDoc {
  return {
    id: "street-test",
    points: 10,
    type: "oneShot",
    level: 1,
    number: 1,
    titleByLocale: { fr: "Titre", en: "Title", nl: "Titel" },
    bodyByLocale: { fr: "Corps", en: "Body", nl: "Lichaam" },
    ...overrides,
  };
}

describe("categoryOf", () => {
  test("maps street- prefix to street", () => {
    expect(categoryOf("street-brabanconne")).toBe("street");
  });
  test("maps bar- prefix to bar", () => {
    expect(categoryOf("bar-mime-poule")).toBe("bar");
  });
  test("maps special- prefix to special", () => {
    expect(categoryOf("special-troc-oeuf")).toBe("special");
  });
  test("maps chicken- prefix to special", () => {
    expect(categoryOf("chicken-found")).toBe("special");
  });
  test("unknown prefix falls back to other", () => {
    expect(categoryOf("event-some-id")).toBe("other");
    expect(categoryOf("no-prefix-here")).toBe("other");
    expect(categoryOf("")).toBe("other");
  });
});

describe("pickLocalized", () => {
  test("returns the requested locale when present", () => {
    expect(pickLocalized({ fr: "FR", en: "EN", nl: "NL" }, "fr")).toBe("FR");
    expect(pickLocalized({ fr: "FR", en: "EN", nl: "NL" }, "en")).toBe("EN");
    expect(pickLocalized({ fr: "FR", en: "EN", nl: "NL" }, "nl")).toBe("NL");
  });
  test("falls back to fr when locale missing", () => {
    expect(pickLocalized({ fr: "FR" }, "en")).toBe("FR");
    expect(pickLocalized({ fr: "FR" }, "nl")).toBe("FR");
  });
  test("returns empty string when both locale and fr missing", () => {
    expect(pickLocalized({ en: "EN" }, "fr")).toBe("");
    expect(pickLocalized({}, "fr")).toBe("");
  });
  test("treats empty string as missing", () => {
    expect(pickLocalized({ fr: "FR", en: "" }, "en")).toBe("FR");
    expect(pickLocalized({ fr: "" }, "fr")).toBe("");
  });
  test("is safe on null/undefined map", () => {
    expect(pickLocalized(null as unknown as Record<string, string>, "fr")).toBe("");
    expect(pickLocalized(undefined as unknown as Record<string, string>, "fr")).toBe("");
  });
});

describe("groupByCategory", () => {
  test("orders categories: street → bar → special → other", () => {
    const docs = [
      challenge({ id: "special-1" }),
      challenge({ id: "other-1" }),
      challenge({ id: "bar-1" }),
      challenge({ id: "street-1" }),
    ];
    const out = [...groupByCategory(docs).keys()];
    expect(out).toEqual(["street", "bar", "special", "other"]);
  });

  test("within a category, sorts by points desc then id asc", () => {
    const docs = [
      challenge({ id: "street-z", points: 10 }),
      challenge({ id: "street-a", points: 50 }),
      challenge({ id: "street-m", points: 50 }),
    ];
    const grouped = groupByCategory(docs);
    const street = grouped.get("street")!;
    expect(street.map((c) => c.id)).toEqual(["street-a", "street-m", "street-z"]);
  });

  test("skips empty categories", () => {
    const docs = [challenge({ id: "street-1" }), challenge({ id: "bar-1" })];
    const keys = [...groupByCategory(docs).keys()];
    expect(keys).toEqual(["street", "bar"]);
  });

  test("empty input returns empty map", () => {
    expect(groupByCategory([]).size).toBe(0);
  });

  test("chicken- prefix lands in special bucket", () => {
    const docs = [challenge({ id: "chicken-found", points: 1000 }), challenge({ id: "special-egg" })];
    const grouped = groupByCategory(docs);
    expect(grouped.get("special")!.map((c) => c.id)).toEqual([
      "chicken-found",
      "special-egg",
    ]);
  });
});

describe("requiresId / endOfGameValidation heuristics", () => {
  test("detects FR pièce d'identité", () => {
    expect(requiresId("... Pièce d'identité obligatoire sur la preuve.")).toBe(true);
    expect(requiresId("piece d'identite verifiable")).toBe(true);
  });
  test("detects EN ID phrases", () => {
    expect(requiresId("ID required on the proof")).toBe(true);
    expect(requiresId("ID card required, visible on the proof")).toBe(true);
  });
  test("detects NL identiteitsbewijs", () => {
    expect(requiresId("identiteitsbewijs verplicht op het bewijs")).toBe(true);
  });
  test("returns false on innocent text", () => {
    expect(requiresId("Take a selfie with a stranger")).toBe(false);
    expect(requiresId("")).toBe(false);
  });

  test("endOfGameValidation detects all three locales", () => {
    expect(endOfGameValidation("Validé par la Poule en fin de partie")).toBe(true);
    expect(endOfGameValidation("Validated by the Chicken at the end of the game")).toBe(true);
    expect(
      endOfGameValidation("Gevalideerd door de Kip aan het einde van het spel"),
    ).toBe(true);
    expect(endOfGameValidation("regular body text")).toBe(false);
  });
});

describe("resolveLocale", () => {
  test("query lang wins when valid", () => {
    expect(resolveLocale({ query: { lang: "en" } })).toBe("en");
    expect(resolveLocale({ query: { lang: "nl" } })).toBe("nl");
    expect(resolveLocale({ query: { lang: "FR" } })).toBe("fr");
  });
  test("path prefix is used when query missing (PP-99 /<locale>/challenges/<gameId>)", () => {
    expect(resolveLocale({ path: "/en/challenges/abc123" })).toBe("en");
    expect(resolveLocale({ path: "/nl/challenges/abc123" })).toBe("nl");
    expect(resolveLocale({ path: "/fr/challenges/abc123" })).toBe("fr");
  });
  test("defaults to fr (FR-first audience) when no signal", () => {
    expect(resolveLocale({})).toBe("fr");
    expect(resolveLocale({ path: "/" })).toBe("fr");
    expect(resolveLocale({ query: { lang: "de" } })).toBe("fr");
    expect(resolveLocale({ query: { lang: "" } })).toBe("fr");
  });
  test("query wins over path when both present", () => {
    expect(resolveLocale({ query: { lang: "nl" }, path: "/en/challenges/abc123" })).toBe("nl");
  });
  test("non-locale first segment doesn't match", () => {
    expect(resolveLocale({ path: "/foo/challenges/abc123" })).toBe("fr");
  });
});

describe("extractGameId (PP-99 /<locale>/challenges/<gameId>)", () => {
  test("extracts gameId across all three locales", () => {
    expect(extractGameId({ path: "/fr/challenges/abc123" })).toBe("abc123");
    expect(extractGameId({ path: "/en/challenges/abc123" })).toBe("abc123");
    expect(extractGameId({ path: "/nl/challenges/abc123" })).toBe("abc123");
  });
  test("returns null when locale prefix missing", () => {
    expect(extractGameId({ path: "/challenges/abc123" })).toBe(null);
    expect(extractGameId({ path: "/challenges-fr/abc123" })).toBe(null);
  });
  test("returns null when gameId missing", () => {
    expect(extractGameId({ path: "/fr/challenges/" })).toBe(null);
    expect(extractGameId({ path: "/fr/challenges" })).toBe(null);
  });
  test("ignores trailing query / fragment", () => {
    expect(extractGameId({ path: "/fr/challenges/abc123?print=1" })).toBe("abc123");
    expect(extractGameId({ path: "/fr/challenges/abc123#top" })).toBe("abc123");
  });
});

describe("renderHtml — locale wiring", () => {
  const docs: ChallengeDoc[] = [
    challenge({
      id: "street-brabanconne",
      points: 150,
      number: 1,
      titleByLocale: {
        fr: "Chanter la Brabançonne",
        en: "Sing the Brabançonne",
        nl: "Zing de Brabançonne",
      },
      bodyByLocale: {
        fr: "Au milieu de la Grand-Place.",
        en: "In the middle of the Grand-Place.",
        nl: "Midden op de Grote Markt.",
      },
    }),
    challenge({
      id: "bar-mime-poule",
      points: 120,
      number: 2,
      type: "repeatable",
      titleByLocale: {
        fr: "Tout le bar mime la poule",
        en: "The whole bar mimes the chicken",
        nl: "De hele bar doet de kip na",
      },
      bodyByLocale: { fr: "...", en: "...", nl: "..." },
    }),
  ];

  test.each<Locale>(["fr", "en", "nl"])("lang=%s sets the html lang attribute", (lang) => {
    const html = renderHtml(docs, lang);
    expect(html).toMatch(new RegExp(`<html lang="${lang}">`));
  });

  test("FR renders FR strings (titles, brand tag, rules header)", () => {
    const html = renderHtml(docs, "fr");
    expect(html).toContain("Chanter la Brabançonne");
    expect(html).toContain("Tout le bar mime la poule");
    expect(html).toContain("LES REGLES DU JEU");
    expect(html).toContain("EN RUE");
    expect(html).toContain("EN BAR");
    expect(html).toContain("BONNE CHASSE !");
  });

  test("EN renders EN strings", () => {
    const html = renderHtml(docs, "en");
    expect(html).toContain("Sing the Brabançonne");
    expect(html).toContain("GAME RULES");
    expect(html).toContain("ON THE STREET");
    expect(html).toContain("AT THE BAR");
    expect(html).toContain("HAPPY HUNTING !");
  });

  test("NL renders NL strings", () => {
    const html = renderHtml(docs, "nl");
    expect(html).toContain("Zing de Brabançonne");
    expect(html).toContain("SPELREGELS");
    expect(html).toContain("OP STRAAT");
    expect(html).toContain("IN DE BAR");
    expect(html).toContain("GOEDE JACHT !");
  });
});

describe("renderHtml — structural assertions", () => {
  test("renders the cover + challenges as two pages", () => {
    const html = renderHtml([challenge()], "fr");
    expect(html.match(/<div class="page">/g)?.length).toBe(2);
  });

  test("contains the chicken pixel logo as a data: URL", () => {
    const html = renderHtml([], "fr");
    expect(html).toContain('src="data:image/png;base64,');
  });

  test("renders the #number prefix on a numbered challenge", () => {
    const html = renderHtml(
      [challenge({ id: "street-x", number: 7, titleByLocale: { fr: "Titre 7" } })],
      "fr",
    );
    expect(html).toContain("#7");
    expect(html).toContain("Titre 7");
  });

  test("omits the #number prefix when number is the sentinel 0", () => {
    const html = renderHtml(
      [challenge({ id: "street-x", number: 0, titleByLocale: { fr: "Titre" } })],
      "fr",
    );
    expect(html).not.toMatch(/<span class="num">#0<\/span>/);
  });

  test("oneShot challenge gets the orange-bordered card class, repeatable gets pink", () => {
    const html = renderHtml(
      [
        challenge({ id: "street-1", type: "oneShot" }),
        challenge({ id: "bar-1", type: "repeatable" }),
      ],
      "fr",
    );
    expect(html).toMatch(/<li class="challenge one"/);
    expect(html).toMatch(/<li class="challenge rep"/);
  });

  test("ID-required challenge surfaces the badge", () => {
    const html = renderHtml(
      [
        challenge({
          id: "street-nicolas",
          titleByLocale: { fr: "Nicolas fait la poule" },
          bodyByLocale: { fr: "Trouver un Nicolas. Pièce d'identité obligatoire sur la preuve." },
        }),
      ],
      "fr",
    );
    expect(html).toContain('class="badge-id"');
  });

  test("end-of-game challenge surfaces the star marker", () => {
    const html = renderHtml(
      [
        challenge({
          id: "special-troc",
          titleByLocale: { fr: "Troc de l'œuf" },
          bodyByLocale: { fr: "Échange ton œuf. Validé par la Poule en fin de partie." },
        }),
      ],
      "fr",
    );
    expect(html).toContain('<span class="star">*</span>');
  });

  test("empty challenge list still renders both pages with empty state", () => {
    const html = renderHtml([], "fr");
    expect(html.match(/<div class="page">/g)?.length).toBe(2);
    expect(html).toContain("Aucun défi pour le moment.");
  });

  test("escapes HTML in localized strings (XSS guard)", () => {
    const html = renderHtml(
      [
        challenge({
          id: "street-xss",
          titleByLocale: { fr: "<script>alert(1)</script>" },
          bodyByLocale: { fr: "evil" },
        }),
      ],
      "fr",
    );
    expect(html).not.toContain("<script>alert(1)</script>");
    expect(html).toContain("&lt;script&gt;alert(1)&lt;/script&gt;");
  });
});
