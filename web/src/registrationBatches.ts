// Allowlist of registration batchIds the web form accepts.
//
// **Parity:** must stay in sync with `ALLOWED_BATCH_IDS` in
// `functions/src/registrations.ts`. Adding a new event = add the
// batchId in both files in the same commit. The server rejects
// anything outside its allowlist with HTTP 400 "batchId is not
// recognized"; the client mirror exists so unknown ids land on the
// FatalError view immediately instead of letting the visitor fill the
// whole form before being rejected at submit time.
export const ALLOWED_BATCH_IDS: ReadonlySet<string> = new Set([
  "game-06-06-2026",
]);

export function isAllowedBatchId(batchId: string): boolean {
  return ALLOWED_BATCH_IDS.has(batchId);
}
