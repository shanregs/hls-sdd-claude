/** Indian digit grouping with two decimals: 12,34,567.50 (spec 022 FR-022). */
export function rupees(value: number): string {
  const negative = value < 0;
  const [whole, fraction] = Math.abs(value).toFixed(2).split(".");
  let grouped = whole;
  if (whole.length > 3) {
    const head = whole.slice(0, -3).replace(/\B(?=(\d{2})+(?!\d))/g, ",");
    grouped = `${head},${whole.slice(-3)}`;
  }
  return `${negative ? "-" : ""}₹${grouped}.${fraction}`;
}

/** DD/MM/YYYY from an ISO date. */
export function dmy(iso: string | null | undefined): string {
  if (!iso) return "";
  const [year, month, day] = iso.slice(0, 10).split("-");
  return `${day}/${month}/${year}`;
}

/** "2026-11" as "November 2026". */
export function monthLabel(month: string): string {
  const [year, m] = month.split("-");
  return new Date(Number(year), Number(m) - 1, 1).toLocaleString("en-IN", {
    month: "long",
    year: "numeric",
  });
}

/** The current month as YYYY-MM. */
export function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}
