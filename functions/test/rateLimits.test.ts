import { describe, expect, it } from "vitest";
import {
  FOUND_CODE_HARD_CAP,
  FOUND_CODE_LOCK_STEPS_MS,
  emptyAttempts,
  isLocked,
  recordWrongCode,
} from "../src/gameplay";
import {
  GAME_RATE_LIMIT_LOCK_MS,
  GAME_RATE_LIMIT_MAX_FAILURES,
  GAME_RATE_LIMIT_WINDOW_MS,
  isGameLocked,
  recordGameFailure,
} from "../src/gameMaster";
import { ensureTeamName, MAX_TEAM_NAME_LENGTH } from "../src/roles";

const NOW = 1_800_000_000_000;

describe("recordWrongCode", () => {
  it("locks after three wrong codes, longer at each lockout", () => {
    let state = emptyAttempts();
    const locks: number[] = [];
    for (let i = 0; i < 12; i++) {
      state = recordWrongCode(state, NOW, null);
      if (state.lockedUntilMs !== null) locks.push(state.lockedUntilMs - NOW);
    }
    expect(locks).toEqual(FOUND_CODE_LOCK_STEPS_MS);
  });

  it("locks until the end of the game past the hard cap", () => {
    let state = emptyAttempts();
    const end = NOW + 3_600_000;
    for (let i = 0; i < FOUND_CODE_HARD_CAP; i++) state = recordWrongCode(state, NOW, end);
    expect(state.lockedUntilMs).toBe(end);
    expect(isLocked(state, end - 1)).toBe(true);
    expect(isLocked(state, end)).toBe(false);
  });
});

describe("recordGameFailure", () => {
  it("locks the whole game after too many failures in the window", () => {
    let counter = null as ReturnType<typeof recordGameFailure> | null;
    for (let i = 0; i < GAME_RATE_LIMIT_MAX_FAILURES - 1; i++) counter = recordGameFailure(counter, NOW);
    expect(isGameLocked(counter, NOW)).toBe(false);
    counter = recordGameFailure(counter, NOW);
    expect(counter.lockedUntilMs).toBe(NOW + GAME_RATE_LIMIT_LOCK_MS);
    expect(isGameLocked(counter, NOW + 1)).toBe(true);
  });

  it("starts a new window once the old one has expired", () => {
    const old = recordGameFailure(null, NOW);
    const next = recordGameFailure(old, NOW + GAME_RATE_LIMIT_WINDOW_MS + 1);
    expect(next.failures).toBe(1);
  });
});

describe("ensureTeamName", () => {
  it("trims and bounds team names", () => {
    expect(ensureTeamName("  Les Poulets  ")).toBe("Les Poulets");
    expect(() => ensureTeamName("x".repeat(MAX_TEAM_NAME_LENGTH + 1))).toThrow(/characters/);
    expect(() => ensureTeamName("   ")).toThrow(/characters/);
    expect(() => ensureTeamName(42)).toThrow(/required/);
  });
});
