import { describe, expect, test } from "vitest";
import { escapeForSheet } from "../src/sheets";

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

  test("passes through the empty string", () => {
    expect(escapeForSheet("")).toBe("");
  });

  test("only prefixes the FIRST char — internal trigger chars are fine", () => {
    expect(escapeForSheet("5+5")).toBe("5+5");
    expect(escapeForSheet("team @home")).toBe("team @home");
    expect(escapeForSheet("rate=5%")).toBe("rate=5%");
  });

  test("guards against a HYPERLINK formula payload in a free-text field", () => {
    const payload = '=HYPERLINK("http://phish.example","Click")';
    expect(escapeForSheet(payload)).toBe(`'${payload}`);
  });

  test("guards against a CONCATENATE-style data exfil attempt", () => {
    const payload = '=CONCATENATE("a","b","c")';
    expect(escapeForSheet(payload)).toBe(`'${payload}`);
  });
});
