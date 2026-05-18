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
const HEADER_RANGE = `${SHEET_TAB}!A1:M1`;
const REFUND_HEADER_RANGE = `${SHEET_TAB}!L1:M1`;

const HEADER_ROW = [
  "timestamp",       // A
  "registrationId",  // B
  "batchId",         // C
  "playerName",      // D
  "teamName",        // E
  "email",           // F
  "phone",           // G
  "teamSize",        // H
  "code",            // I
  "paid",            // J — flips to FALSE on refund (matches Firestore)
  "paidAt",          // K — never overwritten, original payment timestamp
  "refunded",        // L — TRUE after a full refund
  "refundedAt",      // M — refund timestamp
];

const REFUND_HEADER_CELLS = ["refunded", "refundedAt"];

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
 *
 * Also backfills the refund columns (L1/M1) on sheets that were
 * bootstrapped with the pre-refund 11-column header — the original
 * `ensureHeader` only ran when A1 was empty, so existing prod sheets
 * never got the "refunded" / "refundedAt" headers without this nudge.
 */
async function ensureHeader(
  sheets: ReturnType<typeof google.sheets>,
  sheetId: string
): Promise<void> {
  const existing = await sheets.spreadsheets.values.get({
    spreadsheetId: sheetId,
    range: HEADER_RANGE,
  });
  const headerRow = existing.data.values?.[0] ?? [];
  const firstCell = headerRow[0];
  const aIsEmpty = !firstCell || String(firstCell).trim().length === 0;

  if (aIsEmpty) {
    await sheets.spreadsheets.values.update({
      spreadsheetId: sheetId,
      range: HEADER_RANGE,
      valueInputOption: "USER_ENTERED",
      requestBody: { values: [HEADER_ROW] },
    });
    logger.info(`Sheet header written to spreadsheet ${sheetId}`);
    return;
  }

  // Header already exists — backfill the refund columns if missing
  // (columns L=11 and M=12 are zero-indexed in the response array).
  const lCell = headerRow[11];
  const mCell = headerRow[12];
  const needsRefundHeader =
    (!lCell || String(lCell).trim().length === 0) &&
    (!mCell || String(mCell).trim().length === 0);
  if (needsRefundHeader) {
    await sheets.spreadsheets.values.update({
      spreadsheetId: sheetId,
      range: REFUND_HEADER_RANGE,
      valueInputOption: "USER_ENTERED",
      requestBody: { values: [REFUND_HEADER_CELLS] },
    });
    logger.info(`Sheet refund header backfilled in spreadsheet ${sheetId}`);
  }
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

/**
 * Flips an existing row to refunded state on a `charge.refunded`
 * webhook. Finds the row by `registrationId` in column B, then in a
 * single `values.batchUpdate` flips J (paid → FALSE) and writes L
 * (refunded → TRUE) + M (refundedAt → now). K (paidAt) is intentionally
 * left untouched so the original payment timestamp survives as audit.
 *
 * No-op if the row is missing (registration paid before Sheet sync
 * went live, or row manually deleted) — logged for follow-up.
 */
export async function markRegistrationRefunded(
  registrationId: string,
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
  const idx = ids.indexOf(registrationId);
  if (idx < 0) {
    logger.warn(`Sheet: no row matches ${registrationId}, can't mark refunded`);
    return;
  }
  // ids[0] is the header value "registrationId" so a real data row is
  // at idx >= 1. Sheets is 1-indexed → spreadsheet row = idx + 1.
  const rowNumber = idx + 1;

  const nowIso = new Date().toISOString();
  await sheets.spreadsheets.values.batchUpdate({
    spreadsheetId: sheetId,
    requestBody: {
      valueInputOption: "USER_ENTERED",
      data: [
        { range: `${SHEET_TAB}!J${rowNumber}`, values: [["FALSE"]] },
        {
          range: `${SHEET_TAB}!L${rowNumber}:M${rowNumber}`,
          values: [["TRUE", nowIso]],
        },
      ],
    },
  });
  logger.info(`Sheet row ${rowNumber} marked refunded for ${registrationId}`);
}
