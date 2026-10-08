import { describe, expect, test } from "vitest";
import en from "./en";
import fr from "./fr";
import nl from "./nl";

function shape(value: unknown, path = ""): string[] {
  if (Array.isArray(value)) return [`${path}[]`];
  if (typeof value === "function") return [`${path}()`];
  if (value && typeof value === "object") {
    return Object.entries(value as Record<string, unknown>).flatMap(([key, child]) =>
      shape(child, path ? `${path}.${key}` : key)
    );
  }
  return [path];
}

function strings(value: unknown): string[] {
  if (typeof value === "string") return [value];
  if (Array.isArray(value)) return value.flatMap(strings);
  if (value && typeof value === "object") return Object.values(value).flatMap(strings);
  return [];
}

describe("translation parity", () => {
  test("fr and nl define exactly the keys of en", () => {
    const reference = shape(en).sort();
    expect(shape(fr).sort()).toEqual(reference);
    expect(shape(nl).sort()).toEqual(reference);
  });

  test("no translation contains an em dash", () => {
    for (const dict of [en, fr, nl]) {
      for (const value of strings(dict)) expect(value).not.toContain("\u2014");
    }
  });
});
