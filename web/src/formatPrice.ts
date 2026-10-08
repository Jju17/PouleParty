const INTL_LOCALE: Record<string, string> = { fr: "fr-BE", nl: "nl-BE", en: "en-GB" };

/** Formats a euro amount for display in the visitor's locale. */
export function formatPrice(amountEuros: number, locale: string): string {
  return new Intl.NumberFormat(INTL_LOCALE[locale] ?? "fr-BE", {
    style: "currency",
    currency: "EUR",
    maximumFractionDigits: Number.isInteger(amountEuros) ? 0 : 2,
  }).format(amountEuros);
}
