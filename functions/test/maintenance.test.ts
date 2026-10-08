import { describe, expect, it } from "vitest";
import { FINISHED_GAME_RETENTION_DAYS, purgeCutoff } from "../src/maintenance";

describe("purgeCutoff", () => {
  it("keeps finished games for the retention period", () => {
    const now = Date.UTC(2030, 0, 31);
    expect(purgeCutoff(now).toMillis()).toBe(now - FINISHED_GAME_RETENTION_DAYS * 86_400_000);
  });
});
