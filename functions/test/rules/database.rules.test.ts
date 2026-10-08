import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import { assertFails, assertSucceeds, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { get, ref, set } from "firebase/database";
import { createRulesEnv, UID } from "./env";

const GAME = "game-1";

let env: RulesTestEnvironment;

interface MetaOverrides {
  gameMode?: string;
  status?: string;
  chickenCanSeeHunters?: boolean;
  shareHunterLocations?: boolean;
  radarPingUntil?: number;
}

async function seed(meta: MetaOverrides = {}, invisible = false): Promise<void> {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.database();
    await set(ref(db, `games/${GAME}`), {
      meta: {
        creatorId: UID.creatorHunter,
        gameMode: meta.gameMode ?? "followTheChicken",
        status: meta.status ?? "inProgress",
        chickenCanSeeHunters: meta.chickenCanSeeHunters ?? false,
        shareHunterLocations: meta.shareHunterLocations ?? true,
        radarPingUntil: meta.radarPingUntil ?? 0,
        roles: {
          [UID.chicken]: "chicken",
          [UID.creatorHunter]: "hunter",
          [UID.hunter]: "hunter",
          [UID.gm]: "gameMaster",
        },
      },
      chickenLocations: { latest: { lat: 50.85, lng: 4.35, ts: 1, invisible } },
      hunterLocations: { [UID.hunter]: { lat: 50.86, lng: 4.36, ts: 1 } },
      presence: { chicken: { online: true, ts: 1 } },
    });
  });
}

function dbAs(uid: string | null) {
  return uid ? env.authenticatedContext(uid).database() : env.unauthenticatedContext().database();
}

beforeAll(async () => {
  env = await createRulesEnv();
});

afterAll(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearDatabase();
});

describe("hunterLocations parent read", () => {
  it("lets a GameMaster read every hunter at once", async () => {
    await seed();
    await assertSucceeds(get(ref(dbAs(UID.gm), `games/${GAME}/hunterLocations`)));
  });

  it("lets the chicken read every hunter only when chickenCanSeeHunters is on", async () => {
    await seed({ chickenCanSeeHunters: true });
    await assertSucceeds(get(ref(dbAs(UID.chicken), `games/${GAME}/hunterLocations`)));
    await seed({ chickenCanSeeHunters: false });
    await assertFails(get(ref(dbAs(UID.chicken), `games/${GAME}/hunterLocations`)));
  });

  it("denies hunters, outsiders and anonymous callers", async () => {
    await seed({ chickenCanSeeHunters: true });
    await assertFails(get(ref(dbAs(UID.hunter), `games/${GAME}/hunterLocations`)));
    await assertFails(get(ref(dbAs(UID.outsider), `games/${GAME}/hunterLocations`)));
    await assertFails(get(ref(dbAs(null), `games/${GAME}/hunterLocations`)));
  });
});

describe("hunterLocations write", () => {
  it("lets a hunter write only their own node while the game is live", async () => {
    await seed();
    const pos = { lat: 50.8, lng: 4.3, ts: 2 };
    await assertSucceeds(set(ref(dbAs(UID.hunter), `games/${GAME}/hunterLocations/${UID.hunter}`), pos));
    await assertFails(set(ref(dbAs(UID.hunter), `games/${GAME}/hunterLocations/${UID.otherHunter}`), pos));
    await assertFails(set(ref(dbAs(UID.gm), `games/${GAME}/hunterLocations/${UID.gm}`), pos));
  });

  it("refuses writes when sharing is off or the game is waiting", async () => {
    const pos = { lat: 50.8, lng: 4.3, ts: 2 };
    await seed({ shareHunterLocations: false });
    await assertFails(set(ref(dbAs(UID.hunter), `games/${GAME}/hunterLocations/${UID.hunter}`), pos));
    await seed({ status: "waiting" });
    await assertFails(set(ref(dbAs(UID.hunter), `games/${GAME}/hunterLocations/${UID.hunter}`), pos));
  });

  it("refuses out-of-range or extra fields", async () => {
    await seed();
    const node = `games/${GAME}/hunterLocations/${UID.hunter}`;
    await assertFails(set(ref(dbAs(UID.hunter), node), { lat: 91, lng: 4.3, ts: 2 }));
    await assertFails(set(ref(dbAs(UID.hunter), node), { lat: 50, lng: 4.3, ts: 2, extra: true }));
  });
});

describe("chickenLocations read", () => {
  const path = `games/${GAME}/chickenLocations/latest`;

  it("lets the designated chicken and a GameMaster read", async () => {
    await seed({ gameMode: "stayInTheZone" }, true);
    await assertSucceeds(get(ref(dbAs(UID.chicken), path)));
    await assertSucceeds(get(ref(dbAs(UID.gm), path)));
  });

  it("treats a demoted creator as a plain hunter", async () => {
    await seed({ gameMode: "stayInTheZone" });
    await assertFails(get(ref(dbAs(UID.creatorHunter), path)));
  });

  it("lets hunters read in followTheChicken unless the chicken is invisible", async () => {
    await seed({ gameMode: "followTheChicken" });
    await assertSucceeds(get(ref(dbAs(UID.hunter), path)));
    await seed({ gameMode: "followTheChicken" }, true);
    await assertFails(get(ref(dbAs(UID.hunter), path)));
  });

  it("lets hunters read in stayInTheZone only during a radar ping", async () => {
    await seed({ gameMode: "stayInTheZone", radarPingUntil: 0 });
    await assertFails(get(ref(dbAs(UID.hunter), path)));
    await seed({ gameMode: "stayInTheZone", radarPingUntil: Date.now() + 60_000 });
    await assertSucceeds(get(ref(dbAs(UID.hunter), path)));
  });

  it("denies outsiders", async () => {
    await seed();
    await assertFails(get(ref(dbAs(UID.outsider), path)));
  });
});

describe("chickenLocations write and presence", () => {
  it("lets only the chicken write its position and presence", async () => {
    await seed();
    const pos = { lat: 50.8, lng: 4.3, ts: 2 };
    await assertSucceeds(set(ref(dbAs(UID.chicken), `games/${GAME}/chickenLocations/latest`), pos));
    await assertFails(set(ref(dbAs(UID.creatorHunter), `games/${GAME}/chickenLocations/latest`), pos));
    await assertSucceeds(
      set(ref(dbAs(UID.chicken), `games/${GAME}/presence/chicken`), { online: true, ts: 3 })
    );
    await assertFails(
      set(ref(dbAs(UID.gm), `games/${GAME}/presence/chicken`), { online: false, ts: 3 })
    );
  });

  it("lets members read presence and hides it from outsiders", async () => {
    await seed();
    await assertSucceeds(get(ref(dbAs(UID.chicken), `games/${GAME}/presence/chicken`)));
    await assertSucceeds(get(ref(dbAs(UID.hunter), `games/${GAME}/presence/chicken`)));
    await assertFails(get(ref(dbAs(UID.outsider), `games/${GAME}/presence/chicken`)));
  });

  it("never exposes the meta mirror", async () => {
    await seed();
    await assertFails(get(ref(dbAs(UID.gm), `games/${GAME}/meta`)));
  });
});
