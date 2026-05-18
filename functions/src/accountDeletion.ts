import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getAppCheck } from "firebase-admin/app-check";
import { onRequest } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { defineSecret } from "firebase-functions/params";

// AND-H6 (store-audit 2026-05-18) — self-service account-deletion request
// endpoint backing `pouleparty.be/delete-account`. Google Play 2024+ requires
// the deletion URL to host a self-service form, not just a `mailto:` link.
//
// What this DOES not do : actually scrub the data. The existing Privacy /
// Terms / DeleteAccount page promise "manual scrub within 30 days" — that
// scrub is a Julien-side operation (Firebase Auth deletion, /users doc,
// past game references). What this handler DOES is :
//   1. Persist the request in `/accountDeletionRequests/{rid}` so it can't
//      be lost in an inbox.
//   2. Notify Julien via Resend so the SLA clock starts.
//   3. Return a synchronous ack to the form so the user gets a clear
//      "we received it" state instead of a vague mailto trampoline.

const REGION = "europe-west1";
const COLLECTION = "accountDeletionRequests";
const RESEND_API_KEY = defineSecret("RESEND_API_KEY");
const NOTIFY_EMAIL = "julien@rahier.dev";
const FROM_ADDRESS = "PouleParty <noreply@pouleparty.be>";

const MAX_EMAIL_LEN = 254;
const MAX_NAME_LEN = 60;
const MAX_REASON_LEN = 500;

interface DeletionRequestPayload {
  email: string;
  nickname?: string;
  reason?: string;
}

function validatePayload(body: unknown): DeletionRequestPayload {
  if (!body || typeof body !== "object") {
    throw new Error("Missing request body");
  }
  const b = body as Record<string, unknown>;

  // Honeypot — mirror the pattern from `createPendingRegistration`. Hidden
  // input the web form ships but real users never see; a non-empty value
  // means a bot auto-filled every visible field.
  const honeypot = typeof b.nicknameAlt === "string" ? b.nicknameAlt.trim() : "";
  if (honeypot.length > 0) {
    throw new Error("invalid request");
  }

  const emailRaw = typeof b.email === "string" ? b.email.trim().toLowerCase() : "";
  if (
    !emailRaw ||
    emailRaw.length > MAX_EMAIL_LEN ||
    // Same tight regex as registrations.ts — rejects CR/LF that could
    // header-inject into Resend.
    !/^[^\s@,]+@[^\s@,]+\.[^\s@,]+$/.test(emailRaw)
  ) {
    throw new Error("Valid email is required");
  }

  const nickname =
    (typeof b.nickname === "string" ? b.nickname.trim() : "").slice(0, MAX_NAME_LEN) ||
    undefined;
  const reason =
    (typeof b.reason === "string" ? b.reason.trim() : "").slice(0, MAX_REASON_LEN) ||
    undefined;

  return { email: emailRaw, nickname, reason };
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

async function notifyJulien(
  payload: DeletionRequestPayload,
  requestId: string,
  apiKey: string
): Promise<void> {
  const lines = [
    `Request ID: ${requestId}`,
    `Email:      ${payload.email}`,
    `Nickname:   ${payload.nickname ?? "(not provided)"}`,
    `Reason:     ${payload.reason ?? "(not provided)"}`,
    "",
    `Firestore doc: /accountDeletionRequests/${requestId}`,
    "Flip `processed: true` once the manual scrub is done.",
  ];
  const text = lines.join("\n");
  const html = `<pre style="font-family:ui-monospace,monospace;font-size:13px;line-height:1.6">${escapeHtml(text)}</pre>`;

  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from: FROM_ADDRESS,
      to: [NOTIFY_EMAIL],
      reply_to: payload.email,
      subject: `[PouleParty] Account deletion request (${requestId})`,
      text,
      html,
    }),
  });
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Resend returned ${response.status}: ${body}`);
  }
}

export const processAccountDeletion = onRequest(
  {
    region: REGION,
    secrets: [RESEND_API_KEY],
    cors: [
      "https://pouleparty.be",
      "https://pouleparty-ba586.web.app",
      "https://pouleparty-prod.web.app",
      "http://localhost:5173",
    ],
    // Conservative caps — the form fires once per submit, and a brute-force
    // here just produces noise (the data we collect is what the attacker
    // already typed into the form).
    maxInstances: 5,
    concurrency: 20,
  },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }

    // App Check (reCAPTCHA Enterprise on the web) — same gate as
    // `createPendingRegistration` so bots can't drown the inbox.
    const appCheckHeader = req.header("X-Firebase-AppCheck");
    if (!appCheckHeader) {
      logger.warn("processAccountDeletion: missing App Check token");
      res.status(401).json({ error: "Missing App Check token" });
      return;
    }
    try {
      await getAppCheck().verifyToken(appCheckHeader);
    } catch (err) {
      logger.warn("processAccountDeletion: App Check verify failed", err);
      res.status(401).json({ error: "Invalid App Check token" });
      return;
    }

    let payload: DeletionRequestPayload;
    try {
      payload = validatePayload(req.body);
    } catch (err) {
      res.status(400).json({ error: (err as Error).message });
      return;
    }

    try {
      const docRef = getFirestore().collection(COLLECTION).doc();
      const requestId = docRef.id;
      await docRef.set({
        requestId,
        email: payload.email,
        nickname: payload.nickname ?? null,
        reason: payload.reason ?? null,
        createdAt: FieldValue.serverTimestamp(),
        processed: false,
        processedAt: null,
      });
      logger.info(
        `Account deletion request ${requestId} recorded for ${payload.email}`
      );

      // Best-effort notification. A Resend outage shouldn't fail the
      // request — the Firestore doc is the canonical record and Julien
      // can poll `/accountDeletionRequests where processed==false`.
      try {
        await notifyJulien(payload, requestId, RESEND_API_KEY.value());
      } catch (err) {
        logger.error(`Resend notify failed for ${requestId}`, err);
      }

      res.status(200).json({ requestId });
    } catch (err) {
      logger.error("processAccountDeletion failed", err);
      res.status(500).json({ error: "Internal error" });
    }
  }
);
