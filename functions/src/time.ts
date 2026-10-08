const BRUSSELS_FORMAT = new Intl.DateTimeFormat("fr-BE", {
  timeZone: "Europe/Brussels",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hourCycle: "h23",
});

/** `YYYY-MM-DD HH:mm:ss` in the business time zone, for human-facing exports. */
export function formatBrussels(date: Date): string {
  const parts = Object.fromEntries(
    BRUSSELS_FORMAT.formatToParts(date).map((part) => [part.type, part.value])
  );
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`;
}
