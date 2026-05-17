import { google } from "googleapis";
import { logger } from "firebase-functions/v2";

// PP-52 — Append a row to the D-Day Google Sheet whenever a
// registration is paid. The sheet is shared with Martin / l'orga for
// at-a-glance roster + reconciliation.
//
// Auth: Application Default Credentials. In Cloud Functions v2 ADC
// resolves to the runtime compute service account (cf
// `reference_web_firebase.md`):
//   - prod    : 1047338092854-compute@developer.gserviceaccount.com
//   - staging :  847523524308-compute@developer.gserviceaccount.com
// The Sheet MUST be shared as Editor with the relevant compute SA
// AND the Google Sheets API must be enabled on the project (Console
// → APIs & Services → Enable Sheets API). Both are one-time setup.

interface RegistrationSnapshot {
  registrationId: string;
  batchId: string;
  playerName: string;
  teamName: string;
  email: string;
  phone: string;
  teamSize: number;
  code: string;
  locale: string;
}

const SHEET_TAB = "Inscriptions"; // Name of the tab in the spreadsheet.
const ID_COLUMN_RANGE = `${SHEET_TAB}!B:B`; // Column B holds registrationId.
const HEADER_RANGE = `${SHEET_TAB}!A1:K1`;

const HEADER_ROW = [
  "timestamp",
  "registrationId",
  "batchId",
  "playerName",
  "teamName",
  "email",
  "phone",
  "teamSize",
  "code",
  "paid",
  "paidAt",
];

async function sheetsClient() {
  const auth = new google.auth.GoogleAuth({
    scopes: ["https://www.googleapis.com/auth/spreadsheets"],
  });
  return google.sheets({ version: "v4", auth });
}

/**
 * Write the column header into row 1 if it's still empty. Runs once
 * per sheet lifetime — every subsequent append finds the row already
 * populated and short-circuits. No frozen-row formatting (Sheets API
 * batchUpdate would be needed for that) — Julien freezes row 1
 * manually if needed.
 */
async function ensureHeader(
  sheets: ReturnType<typeof google.sheets>,
  sheetId: string
): Promise<void> {
  const existing = await sheets.spreadsheets.values.get({
    spreadsheetId: sheetId,
    range: HEADER_RANGE,
  });
  const firstCell = existing.data.values?.[0]?.[0];
  if (firstCell && String(firstCell).trim().length > 0) return;

  await sheets.spreadsheets.values.update({
    spreadsheetId: sheetId,
    range: HEADER_RANGE,
    valueInputOption: "USER_ENTERED",
    requestBody: { values: [HEADER_ROW] },
  });
  logger.info(`Sheet header written to spreadsheet ${sheetId}`);
}

/**
 * Append-or-skip. Reads the registrationId column first; if the new
 * registration is already there (Stripe webhook retry that somehow
 * raced past the Firestore idempotency guard, or a manual rerun),
 * skip without writing a duplicate row.
 */
export async function appendRegistrationRow(
  reg: RegistrationSnapshot,
  sheetId: string
): Promise<void> {
  const sheets = await sheetsClient();

  await ensureHeader(sheets, sheetId);

  const existing = await sheets.spreadsheets.values.get({
    spreadsheetId: sheetId,
    range: ID_COLUMN_RANGE,
    majorDimension: "COLUMNS",
  });
  const ids = (existing.data.values?.[0] ?? []) as string[];
  if (ids.includes(reg.registrationId)) {
    logger.info(`Sheet row for ${reg.registrationId} already exists, skipping`);
    return;
  }

  const nowIso = new Date().toISOString();
  await sheets.spreadsheets.values.append({
    spreadsheetId: sheetId,
    range: `${SHEET_TAB}!A:K`,
    valueInputOption: "USER_ENTERED",
    insertDataOption: "INSERT_ROWS",
    requestBody: {
      values: [
        [
          nowIso,
          reg.registrationId,
          reg.batchId,
          reg.playerName,
          reg.teamName,
          reg.email,
          reg.phone,
          String(reg.teamSize),
          reg.code,
          "TRUE",
          nowIso,
        ],
      ],
    },
  });
  logger.info(`Sheet row appended for ${reg.registrationId}`);
}
