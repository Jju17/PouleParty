import { describe, expect, test } from "vitest";
import {
  CODE_ALPHABET,
  basePathForLocale,
  generateCode,
  originFor,
  validatePayload,
} from "../src/registrations";

function basePayload(overrides: Record<string, unknown> = {}): unknown {
  return {
    batchId: "game-06-06-2026",
    playerName: "Alice",
    teamName: "Les Poules",
    email: "alice@example.com",
    phone: "0477123456",
    teamSize: 3,
    locale: "fr",
    consentAcknowledgedAt: "2026-05-19T10:00:00.000Z",
    ...overrides,
  };
}

describe("validatePayload — happy path", () => {
  test("accepts a well-formed payload and normalizes the email", () => {
    const out = validatePayload(basePayload({ email: " Alice@Example.COM " }));
    expect(out.batchId).toBe("game-06-06-2026");
    expect(out.playerName).toBe("Alice");
    expect(out.teamName).toBe("Les Poules");
    expect(out.email).toBe("alice@example.com"); // trimmed + lowercased
    expect(out.phone).toBe("0477123456");
    expect(out.teamSize).toBe(3);
    expect(out.locale).toBe("fr");
    expect(out.consentAcknowledgedAt).toBe("2026-05-19T10:00:00.000Z");
  });

  test("accepts teamSize as a numeric string ('4')", () => {
    const out = validatePayload(basePayload({ teamSize: "4" }));
    expect(out.teamSize).toBe(4);
  });

  test("trims player and team names", () => {
    const out = validatePayload(
      basePayload({ playerName: "  Alice  ", teamName: "  Team  " })
    );
    expect(out.playerName).toBe("Alice");
    expect(out.teamName).toBe("Team");
  });
});

describe("validatePayload — body shape rejections", () => {
  test("throws on missing body", () => {
    expect(() => validatePayload(undefined)).toThrow(/Missing request body/);
  });

  test("throws on non-object body", () => {
    expect(() => validatePayload("not an object")).toThrow(/Missing request body/);
    expect(() => validatePayload(42)).toThrow(/Missing request body/);
    expect(() => validatePayload(null)).toThrow(/Missing request body/);
  });
});

describe("validatePayload — honeypot", () => {
  test("rejects when nicknameAlt is non-empty (naive bot fill)", () => {
    expect(() =>
      validatePayload(basePayload({ nicknameAlt: "Acme Corp" }))
    ).toThrow(/invalid request/);
  });

  test("rejects when nicknameAlt is whitespace-padded non-empty", () => {
    expect(() =>
      validatePayload(basePayload({ nicknameAlt: "  spam  " }))
    ).toThrow(/invalid request/);
  });

  test("accepts when nicknameAlt is empty string or pure whitespace", () => {
    expect(() => validatePayload(basePayload({ nicknameAlt: "" }))).not.toThrow();
    expect(() => validatePayload(basePayload({ nicknameAlt: "   " }))).not.toThrow();
  });

  test("accepts when nicknameAlt is missing", () => {
    const p = basePayload();
    delete (p as Record<string, unknown>).nicknameAlt;
    expect(() => validatePayload(p)).not.toThrow();
  });
});

describe("validatePayload — batchId allowlist", () => {
  test("rejects empty batchId", () => {
    expect(() => validatePayload(basePayload({ batchId: "" }))).toThrow(/batchId is required/);
    expect(() => validatePayload(basePayload({ batchId: "   " }))).toThrow(/batchId is required/);
  });

  test("rejects unknown batchId", () => {
    expect(() => validatePayload(basePayload({ batchId: "game-99-99-9999" }))).toThrow(
      /not recognized/
    );
  });

  test("rejects non-string batchId", () => {
    expect(() => validatePayload(basePayload({ batchId: 12345 }))).toThrow(/batchId is required/);
    expect(() => validatePayload(basePayload({ batchId: null }))).toThrow(/batchId is required/);
  });
});

describe("validatePayload — playerName / teamName length", () => {
  test("rejects empty playerName", () => {
    expect(() => validatePayload(basePayload({ playerName: "" }))).toThrow(/playerName/);
    expect(() => validatePayload(basePayload({ playerName: "    " }))).toThrow(/playerName/);
  });

  test("rejects empty teamName", () => {
    expect(() => validatePayload(basePayload({ teamName: "" }))).toThrow(/teamName/);
    expect(() => validatePayload(basePayload({ teamName: "    " }))).toThrow(/teamName/);
  });

  test("silently truncates oversize playerName/teamName to 60 chars", () => {
    const huge = "x".repeat(10_000);
    const out = validatePayload(basePayload({ playerName: huge, teamName: huge }));
    expect(out.playerName.length).toBe(60);
    expect(out.teamName.length).toBe(60);
  });
});

describe("validatePayload — email", () => {
  test("rejects empty email", () => {
    expect(() => validatePayload(basePayload({ email: "" }))).toThrow(/email/);
  });

  test("rejects email missing @", () => {
    expect(() => validatePayload(basePayload({ email: "notanemail.com" }))).toThrow(/email/);
  });

  test("rejects email missing TLD", () => {
    expect(() => validatePayload(basePayload({ email: "user@example" }))).toThrow(/email/);
  });

  test("rejects email longer than 254 chars (RFC 5321)", () => {
    const long = `${"x".repeat(251)}@a.b`;
    expect(() => validatePayload(basePayload({ email: long }))).toThrow(/email/);
  });

  test("accepts email at exactly the 254-char boundary", () => {
    const exact = `${"x".repeat(250)}@a.b`;
    expect(exact.length).toBe(254);
    expect(() => validatePayload(basePayload({ email: exact }))).not.toThrow();
  });

  test("rejects email containing CR/LF (header injection)", () => {
    expect(() => validatePayload(basePayload({ email: "foo@bar.com\r\nBcc: x@y.com" }))).toThrow(/email/);
    expect(() => validatePayload(basePayload({ email: "foo@bar.com\nBcc: x@y.com" }))).toThrow(/email/);
  });

  test("rejects email containing comma (RFC 5322 multi-recipient)", () => {
    expect(() => validatePayload(basePayload({ email: "a@b.com,c@d.com" }))).toThrow(/email/);
  });

  test("rejects email containing a tab character", () => {
    expect(() => validatePayload(basePayload({ email: "a@b\t.com" }))).toThrow(/email/);
  });

  test("accepts a plus-aliased email", () => {
    const out = validatePayload(basePayload({ email: "alice+test@example.com" }));
    expect(out.email).toBe("alice+test@example.com");
  });

  test("lowercases and trims input", () => {
    const out = validatePayload(basePayload({ email: "  Alice.Smith@EXAMPLE.com " }));
    expect(out.email).toBe("alice.smith@example.com");
  });
});

describe("validatePayload — phone", () => {
  test("stores a plain local-format phone unmodified", () => {
    expect(validatePayload(basePayload({ phone: "0477123456" })).phone).toBe("0477123456");
  });

  test("stores an international-format phone unmodified (no leading quote)", () => {
    expect(validatePayload(basePayload({ phone: "+32477123456" })).phone).toBe("+32477123456");
  });

  test("stores a phone starting with - unmodified (no leading quote)", () => {
    expect(validatePayload(basePayload({ phone: "-5551234" })).phone).toBe("-5551234");
  });

  test("rejects phones containing chars outside the allowed class", () => {
    expect(() => validatePayload(basePayload({ phone: "=1+1234" }))).toThrow(/phone/);
    expect(() => validatePayload(basePayload({ phone: "@admin5" }))).toThrow(/phone/);
    expect(() => validatePayload(basePayload({ phone: "abcdef" }))).toThrow(/phone/);
    expect(() => validatePayload(basePayload({ phone: "0477!@#" }))).toThrow(/phone/);
  });

  test("rejects too-short phone (< 6 chars)", () => {
    expect(() => validatePayload(basePayload({ phone: "12345" }))).toThrow(/phone/);
  });

  test("truncates phone longer than MAX_PHONE_LEN (20) before regex check", () => {
    const out = validatePayload(basePayload({ phone: "1".repeat(30) }));
    expect(out.phone.length).toBe(20);
  });
});

describe("validatePayload — teamSize", () => {
  test("accepts 3 / 4 / 5", () => {
    for (const size of [3, 4, 5]) {
      expect(validatePayload(basePayload({ teamSize: size })).teamSize).toBe(size);
    }
  });

  test("rejects 0 / 1 / 2 / 6 / 100", () => {
    for (const size of [0, 1, 2, 6, 100]) {
      expect(() => validatePayload(basePayload({ teamSize: size }))).toThrow(/teamSize/);
    }
  });

  test("rejects non-numeric teamSize", () => {
    expect(() => validatePayload(basePayload({ teamSize: "three" }))).toThrow(/teamSize/);
    expect(() => validatePayload(basePayload({ teamSize: null }))).toThrow(/teamSize/);
    expect(() => validatePayload(basePayload({ teamSize: NaN }))).toThrow(/teamSize/);
  });
});

describe("validatePayload — locale fallback", () => {
  test("accepts a 2-char locale string", () => {
    for (const loc of ["fr", "en", "nl"]) {
      expect(validatePayload(basePayload({ locale: loc })).locale).toBe(loc);
    }
  });

  test("falls back to 'fr' when locale is missing", () => {
    const p = basePayload();
    delete (p as Record<string, unknown>).locale;
    expect(validatePayload(p).locale).toBe("fr");
  });

  test("falls back to 'fr' when locale has wrong length", () => {
    expect(validatePayload(basePayload({ locale: "english" })).locale).toBe("fr");
    expect(validatePayload(basePayload({ locale: "f" })).locale).toBe("fr");
  });

  test("falls back to 'fr' on non-string locale", () => {
    expect(validatePayload(basePayload({ locale: 42 })).locale).toBe("fr");
  });
});

describe("validatePayload — consent", () => {
  test("rejects missing consentAcknowledgedAt", () => {
    const p = basePayload();
    delete (p as Record<string, unknown>).consentAcknowledgedAt;
    expect(() => validatePayload(p)).toThrow(/consentAcknowledgedAt/);
  });

  test("rejects empty consent timestamp", () => {
    expect(() => validatePayload(basePayload({ consentAcknowledgedAt: "" }))).toThrow(
      /consentAcknowledgedAt/
    );
  });

  test("rejects unparseable consent timestamp", () => {
    expect(() => validatePayload(basePayload({ consentAcknowledgedAt: "not-a-date" }))).toThrow(
      /consentAcknowledgedAt/
    );
  });

  test("accepts a valid ISO 8601 timestamp", () => {
    const out = validatePayload(basePayload({ consentAcknowledgedAt: "2026-05-19T10:00:00.000Z" }));
    expect(out.consentAcknowledgedAt).toBe("2026-05-19T10:00:00.000Z");
  });
});

describe("generateCode", () => {
  test("returns a 6-character string", () => {
    for (let i = 0; i < 200; i += 1) {
      const code = generateCode();
      expect(code).toHaveLength(6);
    }
  });

  test("every character is drawn from the readable alphabet (no 0 / O / 1 / I)", () => {
    expect(CODE_ALPHABET).not.toContain("0");
    expect(CODE_ALPHABET).not.toContain("O");
    expect(CODE_ALPHABET).not.toContain("1");
    expect(CODE_ALPHABET).not.toContain("I");

    const allowed = new Set(CODE_ALPHABET);
    for (let i = 0; i < 500; i += 1) {
      const code = generateCode();
      for (const ch of code) {
        expect(allowed.has(ch)).toBe(true);
      }
    }
  });

  test("produces high entropy (no collisions across 5000 draws)", () => {
    const seen = new Set<string>();
    for (let i = 0; i < 5000; i += 1) {
      seen.add(generateCode());
    }
    expect(seen.size).toBe(5000);
  });
});

function reqWithOrigin(origin: string | string[] | undefined) {
  return { headers: { origin } as Record<string, string | string[] | undefined> };
}

describe("originFor", () => {
  test("echoes back an allowed origin verbatim", () => {
    expect(originFor(reqWithOrigin("https://pouleparty.be"))).toBe("https://pouleparty.be");
    expect(originFor(reqWithOrigin("https://pouleparty-ba586.web.app"))).toBe(
      "https://pouleparty-ba586.web.app"
    );
    expect(originFor(reqWithOrigin("https://pouleparty-prod.web.app"))).toBe(
      "https://pouleparty-prod.web.app"
    );
    expect(originFor(reqWithOrigin("http://localhost:5173"))).toBe("http://localhost:5173");
  });

  test("falls back to prod for an unknown origin", () => {
    expect(originFor(reqWithOrigin("https://evil.example"))).toBe("https://pouleparty.be");
  });

  test("falls back to prod when Origin is missing", () => {
    expect(originFor(reqWithOrigin(undefined))).toBe("https://pouleparty.be");
  });

  test("falls back when Origin header is an array (multi-value)", () => {
    // Arrays fail the typeof === "string" check and fall back to prod.
    expect(originFor(reqWithOrigin(["https://pouleparty.be"]))).toBe("https://pouleparty.be");
  });

  test("rejects a near-match (subdomain or trailing slash drift)", () => {
    expect(originFor(reqWithOrigin("https://pouleparty.be/"))).toBe("https://pouleparty.be");
    expect(originFor(reqWithOrigin("https://evil.pouleparty.be"))).toBe("https://pouleparty.be");
    expect(originFor(reqWithOrigin("http://pouleparty.be"))).toBe("https://pouleparty.be");
  });
});

describe("basePathForLocale (PP-99 — locale-prefixed slugs)", () => {
  test("maps each known locale to its localized slug under /<locale>/", () => {
    expect(basePathForLocale("fr")).toBe("/fr/inscription");
    expect(basePathForLocale("en")).toBe("/en/registration");
    expect(basePathForLocale("nl")).toBe("/nl/inschrijving");
  });

  test("falls back to /fr/inscription for unknown locales", () => {
    expect(basePathForLocale("de")).toBe("/fr/inscription");
    expect(basePathForLocale("")).toBe("/fr/inscription");
    expect(basePathForLocale("FR")).toBe("/fr/inscription"); // case-sensitive
  });
});
