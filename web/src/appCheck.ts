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
    console.warn("[appCheck] init failed", err);
  }
}

/**
 * Returns an App Check token, retrying once with a forced refresh. The
 * registration endpoint rejects requests without one, so null means the
 * submit will fail with a verification error the form explains.
 */
export async function getAppCheckToken(): Promise<string | null> {
  if (!appCheckInstance) return null;
  return (await fetchToken(appCheckInstance, false)) ?? fetchToken(appCheckInstance, true);
}

async function fetchToken(instance: AppCheck, forceRefresh: boolean): Promise<string | null> {
  try {
    return (await getToken(instance, forceRefresh)).token;
  } catch (err) {
    console.warn("[appCheck] token fetch failed", { forceRefresh, err });
    return null;
  }
}
