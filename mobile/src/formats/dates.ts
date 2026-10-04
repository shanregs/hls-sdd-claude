/** Dates and times as DD/MM/YYYY and 24-hour HH:mm, the HLS convention (Constitution, Additional Constraints). */
const pad = (n: number) => String(n).padStart(2, "0");

export function formatDate(value: string | Date): string {
  const d = typeof value === "string" ? new Date(value) : value;
  if (Number.isNaN(d.getTime())) return "";
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`;
}

export function formatDateTime(value: string | Date): string {
  const d = typeof value === "string" ? new Date(value) : value;
  if (Number.isNaN(d.getTime())) return "";
  return `${formatDate(d)} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
