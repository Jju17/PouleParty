export type EventLocale = "fr" | "en" | "nl";

export interface EventBatch {
  unitPriceCents: number;
  dateText: Record<EventLocale, string>;
  productName: string;
  productDescription: (teamName: string, teamSize: number) => string;
}

/** Every paid event the registration pipeline accepts, keyed by batchId. */
export const EVENT_BATCHES: Record<string, EventBatch> = {
  "game-06-06-2026": {
    unitPriceCents: 1200,
    dateText: {
      fr: "samedi 6 juin 2026",
      en: "Saturday 6 June 2026",
      nl: "zaterdag 6 juni 2026",
    },
    productName: "PouleParty, inscription événement physique 06/06/2026 Ixelles",
    productDescription: (teamName, teamSize) =>
      `Inscription événement en présentiel, samedi 6 juin 2026, 20h30, Ixelles (Bruxelles). Équipe « ${teamName} » (${teamSize} joueur·euse·s).`,
  },
};

export function eventBatch(batchId: string): EventBatch | undefined {
  return EVENT_BATCHES[batchId];
}
