import { fireEvent, screen } from "@testing-library/react-native";
import { formatLongDate } from "../../src/formats/dates";

/** Opens the add-expense form from the list. */
export async function openAddForm() {
  await fireEvent.press(await screen.findByLabelText("Add expense"));
  await screen.findByLabelText("Amount");
}

export interface ClaimInput {
  amount?: string;
  category?: string;
  /** "YYYY-MM-DD" to choose in the date chooser; the form starts on the server's date. */
  date?: { from: string; to: string };
  note?: string;
  reason?: string;
}

/** Fills the claim form the way a claimant would. */
export async function fillClaim({ amount, category, date, note, reason }: ClaimInput) {
  if (amount !== undefined) await fireEvent.changeText(screen.getByLabelText("Amount"), amount);
  if (category) await fireEvent.press(screen.getByLabelText(category));
  if (date) {
    await fireEvent.press(await screen.findByLabelText(`Date, ${formatLongDate(date.from)}`));
    await fireEvent.press(await screen.findByLabelText(formatLongDate(date.to)));
  }
  if (note !== undefined) await fireEvent.changeText(screen.getByLabelText("Note"), note);
  if (reason !== undefined) await fireEvent.changeText(screen.getByLabelText("Reason"), reason);
}

/** Takes a bill photo with the (fake) camera and waits until it is prepared. */
export async function takeBill(n = 1) {
  await fireEvent.press(screen.getByLabelText("Take photo"));
  await screen.findByLabelText(`Remove bill ${n}`);
}

export async function press(label: string) {
  await fireEvent.press(await screen.findByLabelText(label));
}
