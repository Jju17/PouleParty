import { describe, expect, test } from "vitest";
import {
  deeplinkFor,
  escapeHtml,
  pickStrings,
  renderHtml,
  renderText,
  type RegistrationSnapshot,
} from "../src/email/registrationConfirmation";

// PP-52 — coverage for the Resend email template. We don't deliver any
// emails here; we render the HTML + plain-text body in memory and pin the
// invariants that matter at the inbox: locale routing, HTML escaping
// (avoid XSS on the user's own name → mailbox view), deeplink encoding,
// and absence of unresolved template placeholders.

function snapshot(overrides: Partial<RegistrationSnapshot> = {}): RegistrationSnapshot {
  return {
    registrationId: "abc123registrationId",
    batchId: "game-06-06-2026",
    playerName: "Alice",
    teamName: "Les Poules",
    email: "alice@example.com",
    teamSize: 3,
    code: "ABCD23",
    locale: "fr",
    ...overrides,
  };
}

// ─── pickStrings — locale routing ─────────────────────────────────────

describe("pickStrings", () => {
  test("returns the FR pack by default", () => {
    const { lang, strings } = pickStrings("fr");
    expect(lang).toBe("fr");
    expect(strings.subject).toContain("PouleParty");
    expect(strings.subject).toMatch(/confirm/i);
  });

  test("returns EN for 'en'", () => {
    const { lang, strings } = pickStrings("en");
    expect(lang).toBe("en");
    expect(strings.dDay).toMatch(/June/);
  });

  test("returns NL for 'nl'", () => {
    const { lang, strings } = pickStrings("nl");
    expect(lang).toBe("nl");
    expect(strings.dDay).toMatch(/juni/);
  });

  test("falls back to FR for unknown locales", () => {
    // D-Day audience is FR-first; an unexpected `locale` value (corrupt
    // doc, future store-of-record drift) should never blow up — fall back
    // to the friendliest pack.
    expect(pickStrings("de").lang).toBe("fr");
    expect(pickStrings("").lang).toBe("fr");
    expect(pickStrings("EN").lang).toBe("fr"); // case-sensitive on purpose
    expect(pickStrings("garbage").lang).toBe("fr");
  });
});

// ─── escapeHtml — XSS guard ───────────────────────────────────────────

describe("escapeHtml", () => {
  test("escapes the five HTML-significant characters", () => {
    expect(escapeHtml("&<>\"'")).toBe("&amp;&lt;&gt;&quot;&#39;");
  });

  test("leaves benign ASCII alone", () => {
    expect(escapeHtml("hello world 123")).toBe("hello world 123");
  });

  test("escapes a <script> injection attempt", () => {
    // The mail client renders our HTML; a malicious teamName must not
    // become a live <script> tag in the inbox.
    expect(escapeHtml("<script>alert(1)</script>")).toBe(
      "&lt;script&gt;alert(1)&lt;/script&gt;"
    );
  });

  test("escapes a single attribute-breakout payload", () => {
    expect(escapeHtml("\" onerror=alert(1) \"")).toBe(
      "&quot; onerror=alert(1) &quot;"
    );
  });
});

// ─── deeplinkFor — URL encoding ───────────────────────────────────────

describe("deeplinkFor", () => {
  test("builds the canonical pouleparty.be/join?code=… URL", () => {
    expect(deeplinkFor("ABCD23")).toBe("https://pouleparty.be/join?code=ABCD23");
  });

  test("URL-encodes characters that would break the query string", () => {
    // generateCode() never emits these, but if a code is ever overridden
    // from the console (debug / manual reissue) the URL must still parse.
    expect(deeplinkFor("AB CD")).toBe("https://pouleparty.be/join?code=AB%20CD");
    expect(deeplinkFor("A&B=C")).toBe("https://pouleparty.be/join?code=A%26B%3DC");
  });
});

// ─── renderHtml ───────────────────────────────────────────────────────

describe("renderHtml", () => {
  test("includes the validation code, team name, player name", () => {
    const { strings } = pickStrings("fr");
    const html = renderHtml(snapshot(), strings, "fr");
    expect(html).toContain("ABCD23");
    expect(html).toContain("Les Poules");
    expect(html).toContain("Alice");
  });

  test("sets the html lang attribute to the picked locale", () => {
    const { strings } = pickStrings("en");
    const html = renderHtml(snapshot({ locale: "en" }), strings, "en");
    expect(html).toMatch(/<html lang="en">/);
  });

  test("renders the CTA pointing at the join deeplink", () => {
    const { strings } = pickStrings("fr");
    const html = renderHtml(snapshot({ code: "XYZ789" }), strings, "fr");
    expect(html).toContain('href="https://pouleparty.be/join?code=XYZ789"');
  });

  test("HTML-escapes a name containing < > characters (XSS guard)", () => {
    // The greeting wraps `escapeHtml(name)` in <strong>; if someone slips
    // `<script>` into playerName via a curl bypass, we must not render it.
    const { strings } = pickStrings("fr");
    const html = renderHtml(snapshot({ playerName: "<script>alert(1)</script>" }), strings, "fr");
    expect(html).not.toContain("<script>alert(1)</script>");
    expect(html).toContain("&lt;script&gt;alert(1)&lt;/script&gt;");
  });

  test("HTML-escapes a teamName with double quotes", () => {
    const { strings } = pickStrings("fr");
    const html = renderHtml(snapshot({ teamName: 'Team " evil' }), strings, "fr");
    expect(html).not.toContain('Team " evil');
    expect(html).toContain("Team &quot; evil");
  });

  test("contains no unresolved ${} or {placeholder} templates", () => {
    for (const locale of ["fr", "en", "nl"] as const) {
      const { strings } = pickStrings(locale);
      const html = renderHtml(snapshot({ locale }), strings, locale);
      // template literal substitution leaves no ${…}; presence of one
      // means a string interpolation slipped through as a literal.
      expect(html).not.toMatch(/\$\{[^}]+\}/);
      // i18n placeholder convention is `{name}`; we use no such tokens
      // in the email template, so any survivor is a bug.
      expect(html).not.toMatch(/\{[a-zA-Z]+\}/);
    }
  });

  test("supports dark mode via prefers-color-scheme", () => {
    const { strings } = pickStrings("fr");
    const html = renderHtml(snapshot(), strings, "fr");
    expect(html).toContain("@media (prefers-color-scheme: dark)");
    expect(html).toContain('name="color-scheme" content="light dark"');
  });
});

// ─── renderText — plain-text fallback ─────────────────────────────────

describe("renderText", () => {
  test("contains the validation code and the deeplink", () => {
    const { strings } = pickStrings("fr");
    const text = renderText(snapshot({ code: "QWERTY" }), strings);
    expect(text).toContain("QWERTY");
    expect(text).toContain("https://pouleparty.be/join?code=QWERTY");
  });

  test("strips HTML tags from interpolated strings", () => {
    const { strings } = pickStrings("fr");
    const text = renderText(snapshot(), strings);
    // The HTML strings include <strong>…</strong>; the text fallback must
    // not surface tags to the user's plain-text reader.
    expect(text).not.toMatch(/<[a-z][^>]*>/);
  });

  test("includes the support email", () => {
    const { strings } = pickStrings("en");
    const text = renderText(snapshot({ locale: "en" }), strings);
    expect(text).toContain("julien@rahier.dev");
  });

  test("includes the registration reference id", () => {
    const { strings } = pickStrings("fr");
    const text = renderText(snapshot({ registrationId: "regXYZ" }), strings);
    expect(text).toContain("regXYZ");
  });
});

// ─── localization parity — every pack has all fields filled ──────────

describe("STRINGS parity", () => {
  test("each locale renders a non-empty subject and dDay", () => {
    for (const locale of ["fr", "en", "nl"] as const) {
      const { strings } = pickStrings(locale);
      expect(strings.subject.length).toBeGreaterThan(0);
      expect(strings.dDay.length).toBeGreaterThan(0);
      expect(strings.cta.length).toBeGreaterThan(0);
      expect(strings.codeLabel.length).toBeGreaterThan(0);
    }
  });

  test("greeting / body / fallback are functions that produce non-empty HTML", () => {
    for (const locale of ["fr", "en", "nl"] as const) {
      const { strings } = pickStrings(locale);
      expect(strings.greeting("X")).toMatch(/X/);
      expect(strings.body("MyTeam", 4, "TestDay")).toMatch(/MyTeam/);
      expect(strings.body("MyTeam", 4, "TestDay")).toMatch(/TestDay/);
      expect(strings.fallback("CODE99")).toMatch(/CODE99/);
    }
  });
});
