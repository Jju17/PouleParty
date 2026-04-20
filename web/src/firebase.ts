import { initializeApp } from "firebase/app";
import { getFunctions } from "firebase/functions";

const app = initializeApp({
  projectId: "pouleparty-prod",
  appId: "1:1047338092854:web:5350c58adb0ebd23db8b97",
  storageBucket: "pouleparty-prod.firebasestorage.app",
  apiKey: "AIzaSyDIB83YywX0aWV2kZlr7z1qqrCHqrygpeo",
  authDomain: "pouleparty-prod.firebaseapp.com",
  messagingSenderId: "1047338092854",
  measurementId: "G-GS7ZW3VJ9F",
});

export const functions = getFunctions(app, "europe-west1");
