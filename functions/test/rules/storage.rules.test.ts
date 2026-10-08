import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import { assertFails, assertSucceeds, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, setDoc } from "firebase/firestore";
import { getBytes, ref, uploadBytes } from "firebase/storage";
import { createRulesEnv, UID } from "./env";

const GAME = "abcdefGHIJKLMNOPQRST";
const FILE = "subAAAAAAAAAAAAAAAAA";
const BYTES = new Uint8Array([1, 2, 3]);

let env: RulesTestEnvironment;

async function seedGame(status: string): Promise<void> {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "games", GAME), {
      status,
      roles: { [UID.chicken]: "chicken", [UID.hunter]: "hunter", [UID.gm]: "gameMaster" },
    });
    await uploadBytes(ref(ctx.storage(), `gameSubmissions/${GAME}/existingAAAAAAAAAAA.jpg`), BYTES, {
      contentType: "image/jpeg",
    });
  });
}

function storageAs(uid: string) {
  return env.authenticatedContext(uid).storage();
}

beforeAll(async () => {
  env = await createRulesEnv();
});

afterAll(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.clearStorage();
});

describe("challenge proof uploads", () => {
  it("accepts a hunter photo named after its submission during a live game", async () => {
    await seedGame("inProgress");
    await assertSucceeds(
      uploadBytes(ref(storageAs(UID.hunter), `gameSubmissions/${GAME}/${FILE}.jpg`), BYTES, {
        contentType: "image/jpeg",
      })
    );
  });

  it("refuses other roles, odd names, mismatched types and games that are not live", async () => {
    await seedGame("inProgress");
    const upload = (uid: string, name: string, contentType: string) =>
      uploadBytes(ref(storageAs(uid), `gameSubmissions/${GAME}/${name}`), BYTES, { contentType });
    await assertFails(upload(UID.chicken, `${FILE}.jpg`, "image/jpeg"));
    await assertFails(upload(UID.hunter, "../../escape.jpg", "image/jpeg"));
    await assertFails(upload(UID.hunter, `${FILE}.jpg`, "video/mp4"));
    await assertFails(upload(UID.hunter, `${FILE}.png`, "image/png"));
    await seedGame("done");
    await assertFails(upload(UID.hunter, `${FILE}.jpg`, "image/jpeg"));
  });

  it("lets participants read proofs and hides them from outsiders", async () => {
    await seedGame("inProgress");
    const path = `gameSubmissions/${GAME}/existingAAAAAAAAAAA.jpg`;
    await assertSucceeds(getBytes(ref(storageAs(UID.chicken), path)));
    await assertSucceeds(getBytes(ref(storageAs(UID.gm), path)));
    await assertSucceeds(getBytes(ref(storageAs(UID.hunter), path)));
    await assertFails(getBytes(ref(storageAs(UID.outsider), path)));
  });
});
