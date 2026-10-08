import { describe, expect, it } from "vitest";
import { derivedGameCode, normalizedDriftSeed, randomFoundCode } from "../src/gameTriggers";

describe("derivedGameCode", () => {
  it("uppercases the first six characters of the document id", () => {
    expect(derivedGameCode("abcDEF123456")).toBe("ABCDEF");
  });
});

describe("normalizedDriftSeed", () => {
  it("leaves 32-bit seeds alone", () => {
    expect(normalizedDriftSeed(12345)).toBeNull();
    expect(normalizedDriftSeed(2147483647)).toBeNull();
    expect(normalizedDriftSeed(-2147483648)).toBeNull();
  });
  it("truncates 64-bit seeds the way the server PRNG already does", () => {
    expect(normalizedDriftSeed(2 ** 40 + 7)).toBe((2 ** 40 + 7) | 0);
    expect(normalizedDriftSeed(2 ** 32)).toBe(1);
  });
  it("ignores missing or invalid seeds", () => {
    expect(normalizedDriftSeed(undefined)).toBeNull();
    expect(normalizedDriftSeed(Number.NaN)).toBeNull();
  });
});

describe("randomFoundCode", () => {
  it("always returns four digits", () => {
    for (let i = 0; i < 200; i++) expect(randomFoundCode()).toMatch(/^\d{4}$/);
  });
});
