import { initializeApp } from "firebase/app";
import { getFunctions } from "firebase/functions";

const app = initializeApp({
  projectId: "pouleparty-ba586",
  appId: "1:847523524308:web:f3c668f3473f4b5a041541",
  storageBucket: "pouleparty-ba586.firebasestorage.app",
  apiKey: "AIzaSyDiRR0sjbN7QW3SfbJLeTC6JnJ0ywB2BuI",
  authDomain: "pouleparty-ba586.firebaseapp.com",
  messagingSenderId: "847523524308",
  measurementId: "G-XX9H54JSFW",
});

export const functions = getFunctions(app, "europe-west1");
