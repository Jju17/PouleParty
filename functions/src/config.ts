import { getFirestore } from "firebase-admin/firestore";

export const REGION = "europe-west1";

export function db(): FirebaseFirestore.Firestore {
  return getFirestore();
}
