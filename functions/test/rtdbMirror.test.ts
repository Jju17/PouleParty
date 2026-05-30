import { describe, it, expect } from "vitest";
import { extractGameMeta } from "../src/rtdbMirror";

describe("extractGameMeta", () => {
  it("converts hunterIds / gameMasterIds arrays to {uid:true} maps", () => {
    const meta = extractGameMeta({
      creatorId: "c1",
      chickenId: "ch1",
      gameMode: "followTheChicken",
      status: "inProgress",
      hunterIds: ["h1", "h2"],
      gameMasterIds: ["g1"],
    });
    expect(meta).toEqual({
      creatorId: "c1",
      chickenId: "ch1",
      gameMode: "followTheChicken",
      status: "inProgress",
      hunterIds: { h1: true, h2: true },
      gameMasterIds: { g1: true },
    });
  });

  it("defaults missing/invalid fields safely", () => {
    expect(extractGameMeta(undefined)).toEqual({
      creatorId: "",
      chickenId: "",
      gameMode: "",
      status: "",
      hunterIds: {},
      gameMasterIds: {},
    });
  });

  it("ignores non-string and empty ids in the arrays", () => {
    const meta = extractGameMeta({
      hunterIds: ["h1", 42, "", null, "h2"],
    });
    expect(meta.hunterIds).toEqual({ h1: true, h2: true });
    expect(meta.gameMasterIds).toEqual({});
  });
});
