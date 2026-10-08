import { describe, expect, it } from "vitest";
import {
  IN_PROGRESS_GRACE_MS,
  MAX_SHRINK_NOTIFICATIONS,
  READY_TO_LAUNCH_GRACE_MS,
  planCreationTasks,
  planRuntimeTasks,
  validateSchedulingInput,
} from "../src/lifecycleTasks";
import { recipientsFor } from "../src/notifications";

const START = new Date("2030-01-01T20:00:00Z");
const END = new Date("2030-01-01T21:00:00Z");

describe("planCreationTasks", () => {
  it("plans the full lifecycle of an auto-start game", () => {
    const tasks = planCreationTasks("g1", START, END, 5, 10, false);
    const ids = tasks.map((t) => t.id);
    expect(ids).toContain("status-start-g1");
    expect(ids).toContain("status-end-g1");
    expect(ids).toContain("status-end-fallback-g1");
    expect(ids).toContain("spawn-g1-0");
    expect(ids).toContain("notif-hunterstart-g1");
    expect(ids).toContain("ooz-g1-0");
    expect(ids).not.toContain("status-unlaunched-g1");
    expect(tasks.find((t) => t.id === "status-start-g1")?.payload.targetStatus).toBe("inProgress");
    expect(new Set(ids).size).toBe(ids.length);
  });

  it("schedules one shrink notification and one batch per interval before the end", () => {
    const tasks = planCreationTasks("g1", START, END, 5, 10, false);
    const shrinks = tasks.filter((t) => t.id.startsWith("notif-shrink-"));
    const spawns = tasks.filter((t) => t.id.startsWith("spawn-") && t.id !== "spawn-g1-0");
    expect(shrinks).toHaveLength(5);
    expect(spawns.map((t) => t.payload.batchIndex)).toEqual([1, 2, 3, 4, 5]);
    expect(shrinks[0].scheduleTime.toISOString()).toBe("2030-01-01T20:15:00.000Z");
  });

  it("defers runtime tasks of a manual-start game and keeps two fallback endings", () => {
    const tasks = planCreationTasks("g1", START, END, 5, 10, true);
    const ids = tasks.map((t) => t.id);
    expect(ids.sort()).toEqual(
      ["notif-chickenstart-g1", "status-end-fallback-g1", "status-start-g1", "status-unlaunched-g1"].sort()
    );
    expect(tasks.find((t) => t.id === "status-start-g1")?.payload.targetStatus).toBe("readyToLaunch");
    const unlaunched = tasks.find((t) => t.id === "status-unlaunched-g1")!;
    expect(unlaunched.payload).toMatchObject({ targetStatus: "done", expectedCurrentStatus: "readyToLaunch" });
    expect(unlaunched.scheduleTime.getTime()).toBe(END.getTime() + READY_TO_LAUNCH_GRACE_MS);
    const fallback = tasks.find((t) => t.id === "status-end-fallback-g1")!;
    expect(fallback.scheduleTime.getTime()).toBe(END.getTime() + IN_PROGRESS_GRACE_MS);
  });

  it("caps shrink notifications on very long games", () => {
    const longEnd = new Date(START.getTime() + 1000 * 60 * 60 * 1000);
    const tasks = planRuntimeTasks("g1", START, longEnd, 0, 1, "");
    expect(tasks.filter((t) => t.id.startsWith("notif-shrink-"))).toHaveLength(MAX_SHRINK_NOTIFICATIONS);
  });

  it("suffixes launch-time ids so they never collide with creation-time ids", () => {
    const ids = planRuntimeTasks("g1", START, END, 0, 10, "-launch").map((t) => t.id);
    expect(ids).toContain("spawn-g1-launch-0");
    expect(ids).toContain("status-end-g1-launch");
  });
});

describe("validateSchedulingInput", () => {
  const now = START.getTime() - 60_000;
  it("accepts a well-formed future game", () => {
    expect(validateSchedulingInput(START, END, 5, 10, now)).toBeNull();
  });
  it("rejects bad timings", () => {
    expect(validateSchedulingInput(undefined, END, 5, 10, now)).toBe("missingTiming");
    expect(validateSchedulingInput(START, END, 5, 0.5, now)).toBe("invalidInterval");
    expect(validateSchedulingInput(END, START, 5, 10, now)).toBe("startNotBeforeEnd");
    expect(validateSchedulingInput(START, END, -1, 10, now)).toBe("negativeHeadStart");
    expect(validateSchedulingInput(START, END, 5, 10, END.getTime() + 120_000)).toBe("inThePast");
  });
});

describe("recipientsFor", () => {
  const game = { roles: { c: "chicken", h1: "hunter", h2: "hunter", g: "gameMaster" } };
  it("notifies the chicken and referees at start, hunters at hunter start", () => {
    expect(recipientsFor("chicken_start", game).sort()).toEqual(["c", "g"]);
    expect(recipientsFor("hunter_start", game).sort()).toEqual(["h1", "h2"]);
  });
  it("notifies everyone on a shrink", () => {
    expect(recipientsFor("zone_shrink", game).sort()).toEqual(["c", "g", "h1", "h2"]);
  });
});
