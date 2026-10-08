import { readFileSync } from "fs";
import { resolve } from "path";
import {
  initializeTestEnvironment,
  RulesTestEnvironment,
} from "@firebase/rules-unit-testing";

const ROOT = resolve(__dirname, "../../..");

export const PROJECT_ID = "demo-pouleparty";

export async function createRulesEnv(): Promise<RulesTestEnvironment> {
  return initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: readFileSync(resolve(ROOT, "firestore.rules"), "utf8"),
      host: "127.0.0.1",
      port: 8180,
    },
    database: {
      rules: readFileSync(resolve(ROOT, "database.rules.json"), "utf8"),
      host: "127.0.0.1",
      port: 9100,
    },
    storage: {
      rules: readFileSync(resolve(ROOT, "storage.rules"), "utf8"),
      host: "127.0.0.1",
      port: 9299,
    },
  });
}

export const UID = {
  chicken: "chicken-uid",
  creatorHunter: "creator-uid",
  hunter: "hunter-uid",
  otherHunter: "hunter-2-uid",
  gm: "gm-uid",
  outsider: "outsider-uid",
} as const;
