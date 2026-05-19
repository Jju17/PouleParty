import { describe, expect, test } from "vitest";
import { escapeForSheet } from "../src/sheets";

// F1 + F2 (review 2026-05-19) — the Sheets boundary helper.
//
// Google Sheets values written with `valueInputOption: USER_ENTERED`
// evaluate cells whose value starts with `=`, `+`, `-`, or `@` as
// formulas (the same Excel quirk that turns `=1+1` into `2`, or worse,
// `=HYPERLINK(…)` into a phishable link). The mitigation is to prefix
// the value with `'` so Sheets treats it as a literal — the leading
// quote is invisible in the UI.
//
// These tests pin the helper to the four formula prefixes and confirm
// it is a no-op for everything else. Firestore receives the raw value
// (no leading quote — the canonical store stays clean); this helper
// runs only at the Sheet write boundary.

describe("escapeForSheet", () => {
  test("prefixes the four formula-trigger characters", () => {
    expect(escapeForSheet("=1+1")).toBe("'=1+1");
    expect(escapeForSheet("+32477123456")).toBe("'+32477123456");
    expect(escapeForSheet("-5551234")).toBe("'-5551234");
    expect(escapeForSheet("@admin")).toBe("'@admin");
  });

  test("passes through values that don't start with a trigger char", () => {
    expect(escapeForSheet("0477123456")).toBe("0477123456");
    expect(escapeForSheet("Alice")).toBe("Alice");
    expect(escapeForSheet("Les Poules")).toBe("Les Poules");
    expect(escapeForSheet("alice@example.com")).toBe("alice@example.com");
    expect(escapeForSheet("123 Main St.")).toBe("123 Main St.");
  });

  test("passes through the empty string (Sheets writes \"\" as blank)", () => {
    expect(escapeForSheet("")).toBe("");
  });

  test("only prefixes the FIRST char — internal trigger chars are fine", () => {
    // `5+5` doesn't start with a formula char, so the cell renders as
    //   literal "5+5" (Sheets doesn't auto-evaluate without a leading =).
    expect(escapeForSheet("5+5")).toBe("5+5");
    expect(escapeForSheet("team @home")).toBe("team @home");
    expect(escapeForSheet("rate=5%")).toBe("rate=5%");
  });

  test("guards against a HYPERLINK formula payload in a free-text field", () => {
    // If a future field (teamName, playerName) ever carries a malicious
    // value like `=HYPERLINK("http://phish.example","Click")`, the
    // prefix keeps it literal in the orga's Sheet.
    const payload = '=HYPERLINK("http://phish.example","Click")';
    expect(escapeForSheet(payload)).toBe(`'${payload}`);
  });

  test("guards against a CONCATENATE-style data exfil attempt", () => {
    // Same idea — anything starting with `=` is a formula. Prefix wins.
    const payload = '=CONCATENATE("a","b","c")';
    expect(escapeForSheet(payload)).toBe(`'${payload}`);
  });
});
