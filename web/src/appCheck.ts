// CRIT-4 (audit 2026-05-17): Firebase App Check for the public web form.
//
// The Inscription form POSTs to `createPendingRegistration` (Stripe Checkout
// session creation). Without App Check, that endpoint is wide open — a script
// can spam Stripe API quota, pollute /eventRegistrations with garbage docs,
// and burn the Firestore + Stripe budget right before D-Day.
//
// reCAPTCHA Enterprise (score-based, invisible to real users) attests the
// browser session. The Firebase SDK fetches a token at startup and refreshes
// it automatically; we attach it as `X-Firebase-AppCheck` on the fetch().
//
// Environment selection is runtime via hostname so a single Vite build serves
// both staging and prod hosting:
//   pouleparty.be / pouleparty-prod.web.app    → prod project
//   pouleparty-ba586.web.app / localhost / 127 → staging project
//
// Firebase web config values are public-by-design (apiKey is a project
// identifier, not a secret); see https://firebase.google.com/support/guides/security-checklist.

import { initializeApp, type FirebaseApp } from "firebase/app";
import {
  initializeAppCheck,
  ReCaptchaEnterpriseProvider,
  getToken,
  type AppCheck,
} from "firebase/app-check";

const PROD_CONFIG = {
  apiKey: "AIzaSyDIB83YywX0aWV2kZlr7z1qqrCHqrygpeo",
  authDomain: "pouleparty-prod.firebaseapp.com",
  projectId: "pouleparty-prod",
  storageBucket: "pouleparty-prod.firebasestorage.app",
  messagingSenderId: "1047338092854",
  appId: "1:1047338092854:web:5350c58adb0ebd23db8b97",
  measurementId: "G-GS7ZW3VJ9F",
} as const;

const STAGING_CONFIG = {
  apiKey: "AIzaSyDiRR0sjbN7QW3SfbJLeTC6JnJ0ywB2BuI",
  authDomain: "pouleparty-ba586.firebaseapp.com",
  projectId: "pouleparty-ba586",
  storageBucket: "pouleparty-ba586.firebasestorage.app",
  messagingSenderId: "847523524308",
  appId: "1:847523524308:web:f3c668f3473f4b5a041541",
  measurementId: "G-XX9H54JSFW",
} as const;

const PROD_RECAPTCHA_SITE_KEY = "6LfnI_AsAAAAAP0lP06DPVtH3jNn6sVmy--yuf1L";
const STAGING_RECAPTCHA_SITE_KEY = "6Lc3HfAsAAAAANajWgIG8leQU9A3r1bAHSedm9Yy";

function isProdHost(): boolean {
  if (typeof window === "undefined") return false;
  const host = window.location.hostname;
  return host === "pouleparty.be" || host === "pouleparty-prod.web.app";
}

let firebaseApp: FirebaseApp | null = null;
let appCheckInstance: AppCheck | null = null;

/** Idempotent: call once at app entry (main.tsx). */
export function initAppCheck(): void {
  if (firebaseApp) return;
  const prod = isProdHost();
  const config = prod ? PROD_CONFIG : STAGING_CONFIG;
  const siteKey = prod ? PROD_RECAPTCHA_SITE_KEY : STAGING_RECAPTCHA_SITE_KEY;
  try {
    firebaseApp = initializeApp(config);
    appCheckInstance = initializeAppCheck(firebaseApp, {
      provider: new ReCaptchaEnterpriseProvider(siteKey),
      isTokenAutoRefreshEnabled: true,
    });
  } catch (err) {
    // Init failure (CSP block, network down at boot, etc.) is recoverable —
    // `getAppCheckToken` will return null and the request goes through
    // anyway while `enforceAppCheck` is off server-side. Log so we notice.
    console.warn("App Check init failed:", err);
  }
}

/**
 * Returns the current App Check token, or null if init didn't complete or
 * token fetch failed. Safe to call before init — returns null. The caller
 * attaches the token as `X-Firebase-AppCheck` header when non-null.
 */
export async function getAppCheckToken(): Promise<string | null> {
  if (!appCheckInstance) return null;
  try {
    const result = await getToken(appCheckInstance, /* forceRefresh */ false);
    return result.token;
  } catch (err) {
    console.warn("App Check token fetch failed:", err);
    return null;
  }
}
