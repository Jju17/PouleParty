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
      roles: { ch1: "chicken", h1: "hunter", h2: "hunter", g1: "gameMaster" },
    });
  });

  it("defaults missing/invalid fields safely", () => {
    expect(extractGameMeta(undefined)).toEqual({
      creatorId: "",
      gameMode: "",
      status: "",
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
});
