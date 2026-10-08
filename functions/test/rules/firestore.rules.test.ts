import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import { assertFails, assertSucceeds, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import {
  addDoc,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  limit,
  query,
  setDoc,
  Timestamp,
  updateDoc,
  where,
} from "firebase/firestore";
import { createRulesEnv, UID } from "./env";

const GAME = "abcdefGHIJKLMNOPQRST";
const GAME_CODE = "ABCDEF";

let env: RulesTestEnvironment;

function fsAs(uid: string | null) {
  return uid ? env.authenticatedContext(uid).firestore() : env.unauthenticatedContext().firestore();
}

function newGamePayload(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  const now = Date.now();
  const payload: Record<string, unknown> = {
    name: "Friday",
    creatorId: UID.chicken,
    status: "waiting",
    gameMode: "stayInTheZone",
    maxPlayers: 5,
    chickenCanSeeHunters: false,
    foundCode: "",
    winners: [],
    roles: { [UID.chicken]: "chicken" },
    hasGameMasterPassword: false,
    isAdminCreation: false,
    manualStartEnabled: false,
    isDebugGame: false,
    gameCode: GAME_CODE,
    timing: {
      start: Timestamp.fromMillis(now + 3_600_000),
      end: Timestamp.fromMillis(now + 7_200_000),
      headStartMinutes: 2,
    },
    zone: {
      radius: 1500,
      shrinkIntervalMinutes: 5,
      shrinkMetersPerUpdate: 100,
      driftSeed: 12345,
    },
    powerUps: { enabled: true, enabledTypes: ["zoneFreeze", "zonePreview"], activeEffects: {} },
    ...overrides,
  };
  for (const key of Object.keys(payload)) {
    if (payload[key] === undefined) delete payload[key];
  }
  return payload;
}

async function seedGame(overrides: Record<string, unknown> = {}): Promise<void> {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(
      doc(ctx.firestore(), "games", GAME),
      newGamePayload({
        roles: {
          [UID.chicken]: "chicken",
          [UID.hunter]: "hunter",
          [UID.otherHunter]: "hunter",
          [UID.gm]: "gameMaster",
        },
        ...overrides,
      })
    );
  });
}

beforeAll(async () => {
  env = await createRulesEnv();
});

afterAll(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
});

describe("game create", () => {
  it("accepts a well-formed game seeded with its creator as chicken", async () => {
    await assertSucceeds(setDoc(doc(fsAs(UID.chicken), "games", GAME), newGamePayload()));
  });

  it("accepts the released Android shape with null optionals and derived fields", async () => {
    const payload = newGamePayload({
      gameCode: undefined,
      registrationBatchId: null,
      adminCreation: false,
      debugGame: false,
      startDate: Timestamp.now(),
      timing: { ...(newGamePayload().timing as object), actualStart: null },
    });
    await assertSucceeds(setDoc(doc(fsAs(UID.chicken), "games", GAME), payload));
  });

  it("rejects a gameCode that does not match the document id", async () => {
    await assertFails(
      setDoc(doc(fsAs(UID.chicken), "games", GAME), newGamePayload({ gameCode: "ZZZZZZ" }))
    );
  });

  it("rejects pre-seeded members, winners, a password flag or a found code", async () => {
    const fs = fsAs(UID.chicken);
    const ref = doc(fs, "games", GAME);
    await assertFails(setDoc(ref, newGamePayload({ roles: { [UID.chicken]: "chicken", [UID.hunter]: "hunter" } })));
    await assertFails(setDoc(ref, newGamePayload({ winners: [{ hunterId: UID.hunter }] })));
    await assertFails(setDoc(ref, newGamePayload({ hasGameMasterPassword: true })));
    await assertFails(setDoc(ref, newGamePayload({ foundCode: "1234" })));
  });

  it("caps players at 5 unless the game is an admin creation", async () => {
    const ref = doc(fsAs(UID.chicken), "games", GAME);
    await assertFails(setDoc(ref, newGamePayload({ maxPlayers: 6 })));
    await assertSucceeds(setDoc(ref, newGamePayload({ maxPlayers: 500, isAdminCreation: true })));
  });

  it("rejects malformed shapes", async () => {
    const ref = doc(fsAs(UID.chicken), "games", GAME);
    await assertFails(setDoc(ref, newGamePayload({ gameMode: "hideAndSeek" })));
    await assertFails(setDoc(ref, newGamePayload({ name: "x".repeat(61) })));
    await assertFails(setDoc(ref, newGamePayload({ powerUps: { enabled: true, enabledTypes: ["godMode"] } })));
    await assertFails(
      setDoc(ref, newGamePayload({ timing: { ...(newGamePayload().timing as object), headStartMinutes: -1 } }))
    );
  });

  it("rejects a creator other than the caller", async () => {
    await assertFails(setDoc(doc(fsAs(UID.hunter), "games", GAME), newGamePayload()));
  });
});

describe("game read", () => {
  it("lets any signed-in user get a game by id but not list every game", async () => {
    await seedGame();
    const fs = fsAs(UID.outsider);
    await assertSucceeds(getDoc(doc(fs, "games", GAME)));
    await assertFails(getDocs(collection(fs, "games")));
    await assertFails(getDoc(doc(fsAs(null), "games", GAME)));
  });

  it("lets a creator list their own games", async () => {
    await seedGame();
    const fs = fsAs(UID.chicken);
    await assertSucceeds(getDocs(query(collection(fs, "games"), where("creatorId", "==", UID.chicken))));
  });

  it("keeps the released single-result join query working", async () => {
    await seedGame();
    const fs = fsAs(UID.outsider);
    await assertSucceeds(
      getDocs(query(collection(fs, "games"), where("gameCode", "==", GAME_CODE), limit(1)))
    );
    await assertFails(getDocs(query(collection(fs, "games"), where("gameCode", "==", GAME_CODE), limit(5))));
  });

  it("serves the code index by get only", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "gameCodes", GAME_CODE), { gameId: GAME });
    });
    const fs = fsAs(UID.outsider);
    await assertSucceeds(getDoc(doc(fs, "gameCodes", GAME_CODE)));
    await assertFails(getDocs(collection(fs, "gameCodes")));
    await assertFails(setDoc(doc(fs, "gameCodes", "ZZZZZZ"), { gameId: GAME }));
  });
});

describe("game update and delete", () => {
  it("lets the creator or the designated chicken end the game", async () => {
    await seedGame({ status: "inProgress", creatorId: UID.creatorHunter });
    await assertSucceeds(updateDoc(doc(fsAs(UID.chicken), "games", GAME), { status: "done" }));
    await seedGame({ status: "inProgress", creatorId: UID.creatorHunter });
    await assertSucceeds(updateDoc(doc(fsAs(UID.creatorHunter), "games", GAME), { status: "done" }));
  });

  it("refuses status writes from hunters and any other target status", async () => {
    await seedGame({ status: "waiting" });
    await assertFails(updateDoc(doc(fsAs(UID.hunter), "games", GAME), { status: "done" }));
    await assertFails(updateDoc(doc(fsAs(UID.chicken), "games", GAME), { status: "inProgress" }));
  });

  it("freezes every setting after creation except the derived game code", async () => {
    await seedGame({ gameCode: undefined });
    const ref = doc(fsAs(UID.chicken), "games", GAME);
    await assertSucceeds(updateDoc(ref, { gameCode: GAME_CODE }));
    await assertFails(updateDoc(ref, { gameCode: "ZZZZZZ" }));
    await assertFails(updateDoc(ref, { maxPlayers: 500 }));
    await assertFails(updateDoc(ref, { isDebugGame: true }));
    await assertFails(updateDoc(ref, { "timing.headStartMinutes": 0 }));
    await assertFails(updateDoc(ref, { [`roles.${UID.outsider}`]: "hunter" }));
    await assertFails(updateDoc(ref, { winners: [{ hunterId: UID.hunter }] }));
  });

  it("lets only the creator delete a waiting game", async () => {
    await seedGame();
    await assertFails(deleteDoc(doc(fsAs(UID.hunter), "games", GAME)));
    await assertSucceeds(deleteDoc(doc(fsAs(UID.chicken), "games", GAME)));
  });
});

describe("game subcollections", () => {
  it("hides private and lifecycle docs from everyone", async () => {
    await seedGame();
    for (const uid of [UID.chicken, UID.hunter, UID.gm]) {
      await assertFails(getDoc(doc(fsAs(uid), "games", GAME, "private", "security")));
      await assertFails(getDoc(doc(fsAs(uid), "games", GAME, "lifecycle", "taskManifest")));
    }
  });

  it("restricts the zone schedule and challenge data to participants", async () => {
    await seedGame();
    await assertSucceeds(getDoc(doc(fsAs(UID.hunter), "games", GAME, "zone", "schedule")));
    await assertFails(getDoc(doc(fsAs(UID.outsider), "games", GAME, "zone", "schedule")));
    await assertSucceeds(getDoc(doc(fsAs(UID.gm), "games", GAME, "aggregates", "leaderboard")));
    await assertFails(getDoc(doc(fsAs(UID.outsider), "games", GAME, "players", UID.hunter)));
    await assertFails(setDoc(doc(fsAs(UID.hunter), "games", GAME, "players", UID.hunter), { teamName: "x" }));
  });
});

describe("challenge submissions", () => {
  const SUB = "subAAAAAAAAAAAAAAAAA";
  const mediaUrl = (gameId: string, sub: string) =>
    `https://firebasestorage.googleapis.com/v0/b/demo.appspot.com/o/gameSubmissions%2F${gameId}%2F${sub}.jpg?alt=media&token=t`;
  const submission = (overrides: Record<string, unknown> = {}) => ({
    challengeId: "c1",
    hunterId: UID.hunter,
    type: "oneShot",
    submittedAt: Timestamp.now(),
    mediaUrl: mediaUrl(GAME, SUB),
    mediaType: "image",
    status: "pending",
    ...overrides,
  });

  it("lets a hunter submit their own proof while the game is live", async () => {
    await seedGame({ status: "inProgress" });
    const ref = doc(fsAs(UID.hunter), "games", GAME, "challengeSubmissions", SUB);
    await assertSucceeds(setDoc(ref, submission()));
  });

  it("refuses foreign media, extra keys and submissions outside a live game", async () => {
    await seedGame({ status: "inProgress" });
    const ref = doc(fsAs(UID.hunter), "games", GAME, "challengeSubmissions", SUB);
    await assertFails(setDoc(ref, submission({ mediaUrl: "https://evil.example/x.jpg" })));
    await assertFails(setDoc(ref, submission({ mediaUrl: mediaUrl("otherGame", SUB) })));
    await assertFails(setDoc(ref, submission({ status: "validated" })));
    await assertFails(setDoc(ref, { ...submission(), validatedBy: UID.hunter }));
    await seedGame({ status: "waiting" });
    await assertFails(setDoc(ref, submission()));
  });

  it("shows a submission to its author and the validators only", async () => {
    await seedGame({ status: "inProgress" });
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "games", GAME, "challengeSubmissions", SUB), submission());
    });
    const path = ["games", GAME, "challengeSubmissions", SUB] as const;
    await assertSucceeds(getDoc(doc(fsAs(UID.hunter), ...path)));
    await assertSucceeds(getDoc(doc(fsAs(UID.chicken), ...path)));
    await assertSucceeds(getDoc(doc(fsAs(UID.gm), ...path)));
    await assertFails(getDoc(doc(fsAs(UID.otherHunter), ...path)));
  });
});

describe("power-up collection by released clients", () => {
  async function seedPowerUp(type: string, status = "inProgress") {
    await seedGame({ status });
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "games", GAME, "powerUps", "pu-1"), { id: "pu-1", type });
    });
  }
  const collect = (uid: string) =>
    updateDoc(doc(fsAs(uid), "games", GAME, "powerUps", "pu-1"), {
      collectedBy: uid,
      collectedAt: Timestamp.now(),
    });

  it("lets a hunter collect a hunter power-up and the chicken a chicken power-up", async () => {
    await seedPowerUp("radarPing");
    await assertSucceeds(collect(UID.hunter));
    await seedPowerUp("zoneFreeze");
    await assertSucceeds(collect(UID.chicken));
  });

  it("refuses outsiders, the wrong role and games that are not live", async () => {
    await seedPowerUp("radarPing");
    await assertFails(collect(UID.outsider));
    await assertFails(collect(UID.chicken));
    await seedPowerUp("zoneFreeze");
    await assertFails(collect(UID.hunter));
    await seedPowerUp("radarPing", "waiting");
    await assertFails(collect(UID.hunter));
  });
});

describe("reports", () => {
  it("accepts a bounded report from a participant", async () => {
    await seedGame();
    await assertSucceeds(
      addDoc(collection(fsAs(UID.hunter), "reports"), {
        reporterId: UID.hunter,
        reportedUserId: UID.otherHunter,
        reportedNickname: "Nick",
        gameId: GAME,
        createdAt: Timestamp.now(),
      })
    );
  });

  it("refuses outsiders, self-reports and oversized nicknames", async () => {
    await seedGame();
    const base = {
      reporterId: UID.hunter,
      reportedUserId: UID.otherHunter,
      reportedNickname: "Nick",
      gameId: GAME,
      createdAt: Timestamp.now(),
    };
    await assertFails(addDoc(collection(fsAs(UID.outsider), "reports"), { ...base, reporterId: UID.outsider }));
    await assertFails(addDoc(collection(fsAs(UID.hunter), "reports"), { ...base, reportedUserId: UID.hunter }));
    await assertFails(addDoc(collection(fsAs(UID.hunter), "reports"), { ...base, reportedNickname: "x".repeat(61) }));
  });
});
