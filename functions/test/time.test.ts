import { describe, expect, it } from "vitest";
import { formatBrussels } from "../src/time";
import { EVENT_BATCHES } from "../src/events";

describe("formatBrussels", () => {
  it("shifts UTC to Brussels summer and winter time", () => {
    expect(formatBrussels(new Date("2026-06-06T18:30:00Z"))).toBe("2026-06-06 20:30:00");
    expect(formatBrussels(new Date("2026-01-10T23:15:05Z"))).toBe("2026-01-11 00:15:05");
  });
});

describe("EVENT_BATCHES", () => {
  it("gives every batch a price and a date in each locale", () => {
    for (const batch of Object.values(EVENT_BATCHES)) {
      expect(batch.unitPriceCents).toBeGreaterThan(0);
      expect(Object.keys(batch.dateText).sort()).toEqual(["en", "fr", "nl"]);
    }
  });
});
