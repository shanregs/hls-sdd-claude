import type { ClaimStatus } from "../api/expensesApi";

/** The words of the My Expenses screens (spec 036 contracts/screens.md "Words"). A refusal is the server's `reason`. */
export const STATUS_WORDS: Record<ClaimStatus, string> = {
  DRAFT: "Draft",
  SUBMITTED: "Submitted",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  PAID: "Paid",
  PAID_BY_COMPANY: "Paid by company",
};

export const STATUS_FILTERS: { value: ClaimStatus | "ALL"; label: string }[] = [
  { value: "ALL", label: "All" },
  { value: "DRAFT", label: "Draft" },
  { value: "SUBMITTED", label: "Submitted" },
  { value: "APPROVED", label: "Approved" },
  { value: "REJECTED", label: "Rejected" },
  { value: "PAID", label: "Paid" },
  { value: "PAID_BY_COMPANY", label: "Paid by company" },
];

export const OUTBOX_WORDS = {
  WAITING: "Waiting to send",
  SENDING: "Sending...",
  NEEDS_CONFIRMATION: "Possible duplicate",
  REFUSED: "Not sent",
} as const;

export const SAVED_OFFLINE_TEXT = "Saved. It will be sent when you are back online.";
export const NOT_SAVED_TEXT = "Not saved. Free some space and try again.";
export const PHOTO_UNREADABLE_TEXT = "This photo could not be read. Try another or retake it.";
export const BILL_TOO_LARGE_TEXT = "This bill is larger than 10 MB. Retake it or choose a smaller file.";
export const NEEDS_CONNECTION_TEXT = "This needs a connection. Nothing was changed.";
export const MORE_DETAILS_TEXT = "More details need a connection.";
export const BATCH_WHEN_CONNECTED_TEXT = "Choose the batch when connected";
export const MAX_BILLS = 5;

export const signOutWarning = (count: number): string =>
  `${count} ${count === 1 ? "claim is" : "claims are"} not sent yet. They stay on this phone and are sent when you sign in again.`;

/** An amount from the server ("1850.00") as rupees with Indian digit grouping. */
export function formatRupees(amount: string | number): string {
  const value = Number(amount);
  if (!Number.isFinite(value)) return "";
  const [whole, fraction] = Math.abs(value).toFixed(2).split(".");
  const last3 = whole.slice(-3);
  const rest = whole.slice(0, -3);
  const grouped = rest ? `${rest.replace(/\B(?=(\d{2})+(?!\d))/g, ",")},${last3}` : last3;
  return `${value < 0 ? "-" : ""}₹${grouped}${fraction === "00" ? "" : `.${fraction}`}`;
}
