import type { TileValue } from "./dashboardApi";

/** Indian digit grouping (12,34,567): the last three digits, then pairs. */
export function formatIndianNumber(value: number): string {
  const negative = value < 0;
  const [whole, fraction] = Math.abs(value).toString().split(".");
  let grouped = whole;
  if (whole.length > 3) {
    const head = whole.slice(0, -3).replace(/\B(?=(\d{2})+(?!\d))/g, ",");
    grouped = `${head},${whole.slice(-3)}`;
  }
  return `${negative ? "-" : ""}${grouped}${fraction ? `.${fraction}` : ""}`;
}

/** Rupees with Indian grouping and no paise: ₹1,25,000. */
export function formatRupees(value: number): string {
  return `₹${formatIndianNumber(Math.round(value))}`;
}

/** DD/MM/YYYY from an ISO date or timestamp (the date part as written, no time zone shift). */
export function formatDate(iso: string): string {
  const [year, month, day] = iso.slice(0, 10).split("-");
  return `${day}/${month}/${year}`;
}

/** One decimal at most: 62.5%, 40%. */
export function formatPercent(value: number): string {
  return `${Math.round(value * 10) / 10}%`;
}

/** A figure as the screen shows it, by its unit. */
export function formatValue(value: TileValue): string {
  switch (value.unit) {
    case "RUPEES":
      return formatRupees(Number(value.value));
    case "PERCENT":
      return formatPercent(Number(value.value));
    case "COUNT":
      return formatIndianNumber(Number(value.value));
    default:
      return String(value.value);
  }
}

/** The time of day a reading was taken, in the viewer's locale: "6:30 pm". */
export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString("en-IN", {
    hour: "numeric",
    minute: "2-digit",
  });
}
