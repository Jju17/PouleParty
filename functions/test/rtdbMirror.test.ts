import { describe, it, expect } from "vitest";
import { extractGameMeta } from "../src/rtdbMirror";

describe("extractGameMeta", () => {
  it("copies the roles map verbatim", () => {
    const meta = extractGameMeta({
      creatorId: "c1",
      gameMode: "followTheChicken",
      status: "inProgress",
      roles: { ch1: "chicken", h1: "hunter", h2: "hunter", g1: "gameMaster" },
    });
    expect(meta).toEqual({
      creatorId: "c1",
      gameMode: "followTheChicken",
      status: "inProgress",
      chickenCanSeeHunters: true,
      shareHunterLocations: true,
      roles: { ch1: "chicken", h1: "hunter", h2: "hunter", g1: "gameMaster" },
    });
  });

  it("defaults missing/invalid fields safely", () => {
    expect(extractGameMeta(undefined)).toEqual({
      creatorId: "",
      gameMode: "",
      status: "",
      chickenCanSeeHunters: true,
      shareHunterLocations: true,
      roles: {},
    });
  });

  it("drops invalid role values and non-string/empty keys", () => {
    const meta = extractGameMeta({
      roles: {
        h1: "hunter",
        bad: "spectator",
        "": "hunter",
        h2: 42,
        h3: "gameMaster",
      },
    });
    expect(meta.roles).toEqual({ h1: "hunter", h3: "gameMaster" });
  });

  it("ignores a roles value that is an array", () => {
    const meta = extractGameMeta({ roles: ["h1", "h2"] });
    expect(meta.roles).toEqual({});
  });

  it("shareHunterLocations: chickenCanSeeHunters OR a GameMaster is present", () => {
    // stayInTheZone, chicken can't see hunters, no GM -> no sharing at all
    expect(
      extractGameMeta({ chickenCanSeeHunters: false, roles: { h1: "hunter" } })
        .shareHunterLocations
    ).toBe(false);
    // a GameMaster always sees hunters, even when the chicken can't
    expect(
      extractGameMeta({
        chickenCanSeeHunters: false,
        roles: { h1: "hunter", g1: "gameMaster" },
      }).shareHunterLocations
    ).toBe(true);
    // chicken can see hunters -> sharing regardless of any GM
    expect(
      extractGameMeta({ chickenCanSeeHunters: true, roles: { h1: "hunter" } })
        .shareHunterLocations
    ).toBe(true);
  });

  it("chickenCanSeeHunters defaults to true when the field is absent", () => {
    expect(extractGameMeta({ roles: {} }).chickenCanSeeHunters).toBe(true);
    expect(
      extractGameMeta({ chickenCanSeeHunters: false, roles: {} })
        .chickenCanSeeHunters
    ).toBe(false);
  });
});
