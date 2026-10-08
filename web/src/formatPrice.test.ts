import { describe, expect, test } from "vitest";
import { formatPrice } from "./formatPrice";

const NBSP = /[  ]/g;

describe("formatPrice", () => {
  test("follows each locale's currency layout", () => {
    expect(formatPrice(36, "fr").replace(NBSP, " ")).toBe("36 €");
    expect(formatPrice(36, "nl").replace(NBSP, " ")).toBe("€ 36");
    expect(formatPrice(36, "en").replace(NBSP, " ")).toBe("€36");
  });
  test("keeps cents when the amount has them", () => {
    expect(formatPrice(12.5, "en")).toBe("€12.50");
  });
  test("falls back to Belgian French for unknown locales", () => {
    expect(formatPrice(12, "de").replace(NBSP, " ")).toBe("12 €");
  });
});
