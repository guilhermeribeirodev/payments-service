/** Formats an amount in minor units (e.g. 1500 + "GBP") for display ("£15.00"). */
export function formatMoney(amountMinor: number, currency: string, locale = "en-GB"): string {
  const digits = new Intl.NumberFormat(locale, { style: "currency", currency }).resolvedOptions().maximumFractionDigits ?? 2;
  return new Intl.NumberFormat(locale, { style: "currency", currency }).format(amountMinor / 10 ** digits);
}

/** Parses a user-entered decimal string ("15.50") into minor units (1550); returns undefined if invalid. */
export function toMinorUnits(input: string, fractionDigits = 2): number | undefined {
  const match = /^\d+(\.\d+)?$/.exec(input.trim());
  if (!match) return undefined;
  const [whole, frac = ""] = input.trim().split(".");
  if (frac.length > fractionDigits) return undefined;
  const minor = Number(whole + frac.padEnd(fractionDigits, "0"));
  return minor > 0 ? minor : undefined;
}
