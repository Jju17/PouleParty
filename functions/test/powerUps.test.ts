import { describe, expect, it } from "vitest";
import { isWithinCollectionRange, roleMayUsePowerUp } from "../src/powerUps";

describe("roleMayUsePowerUp", () => {
  it("keeps hunter power-ups for hunters and the rest for the chicken", () => {
    expect(roleMayUsePowerUp("hunter", "radarPing")).toBe(true);
    expect(roleMayUsePowerUp("hunter", "zonePreview")).toBe(true);
    expect(roleMayUsePowerUp("hunter", "zoneFreeze")).toBe(false);
    expect(roleMayUsePowerUp("chicken", "zoneFreeze")).toBe(true);
    expect(roleMayUsePowerUp("chicken", "invisibility")).toBe(true);
    expect(roleMayUsePowerUp("chicken", "radarPing")).toBe(false);
  });
  it("refuses referees, outsiders and unknown types", () => {
    expect(roleMayUsePowerUp("gameMaster", "radarPing")).toBe(false);
    expect(roleMayUsePowerUp(null, "radarPing")).toBe(false);
    expect(roleMayUsePowerUp("chicken", "godMode")).toBe(false);
  });
});

describe("isWithinCollectionRange", () => {
  const target = { lat: 50.85, lng: 4.35 };
  it("accepts the collection radius plus GPS tolerance", () => {
    expect(isWithinCollectionRange(target, { lat: 50.85, lng: 4.35 })).toBe(true);
    expect(isWithinCollectionRange(target, { lat: 50.85045, lng: 4.35 })).toBe(true);
  });
  it("refuses a player clearly out of range", () => {
    expect(isWithinCollectionRange(target, { lat: 50.851, lng: 4.35 })).toBe(false);
  });
});
