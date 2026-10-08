// Mirror of EVENT_BATCHES in functions/src/events.ts: unknown ids fail fast
// instead of after the visitor filled the whole form.
export const ALLOWED_BATCH_IDS: ReadonlySet<string> = new Set([
  "game-06-06-2026",
]);

export function isAllowedBatchId(batchId: string): boolean {
  return ALLOWED_BATCH_IDS.has(batchId);
}
