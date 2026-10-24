import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import { formatLongDate } from "../../src/formats/dates";
import { saveItem } from "../../src/outbox/outboxStore";
import type { OutboxItem } from "../../src/outbox/types";
import { openExpenseScreen } from "../support/expenseApp";
import { press } from "../support/expenseActions";
import { claimDetail } from "../support/expenseFixtures";
import { makeItem } from "../support/outboxFixtures";

const fsState = () => jest.requireMock("expo-file-system/legacy").__state;
const outboxFiles = () => [...(fsState().files as Map<string, string>).keys()].filter((k) => k.includes("/outbox/") && !k.endsWith("list-cache.enc"));

/** Saves an item for the signed-in user before the app opens, as if it had been captured earlier. */
async function seed(overrides: Parameters<typeof makeItem>[0] = {}): Promise<OutboxItem> {
  fsState().files.set("file:///cache/photo.jpg", "SEVMTE9fQklMTF9CWVRFUw==");
  const item = makeItem({ ownerUserId: "u-1", ...overrides });
  await saveItem(item, item.bills.length > 0 ? { [item.bills[0].localId]: "file:///cache/photo.jpg" } : {});
  return item;
}

/** Spec 036 T029: what a refusal leaves on the phone, and what the claimant can do with it (US2, SC-005). */
describe("refused and held claims", () => {
  it("keeps a refused claim with the server's reason, lets it be corrected and sent again", async () => {
    await seed({ itemId: "old", fields: { expenseDate: "2026-08-20", category: "MARKETING", note: "Banners" } });
    const { expenses } = await openExpenseScreen();

    expect(await screen.findByText(/older than 60 days/)).toBeTruthy();
    expect(screen.getByText("Not sent")).toBeTruthy();
    expect(expenses.claims).toHaveLength(1);

    await press("Waiting claim: Marketing, ₹850, 20/08/2026, Not sent");
    expect(await screen.findByText(/Not sent: The expense is older than 60 days/)).toBeTruthy();
    await fireEvent.press(await screen.findByLabelText(`Date, ${formatLongDate("2026-08-20")}`));
    await fireEvent.press(screen.getByLabelText("Next month"));
    await fireEvent.press(screen.getByLabelText("Next month"));
    await fireEvent.press(await screen.findByLabelText(formatLongDate("2026-10-20")));
    await press("Submit");

    expect(await screen.findByText("Submitted.")).toBeTruthy();
    expect(expenses.claims).toHaveLength(1);
    expect(expenses.claims[0]).toMatchObject({ status: "SUBMITTED", expenseDate: "2026-10-20" });
    expect(outboxFiles()).toEqual([]);
  });

  it("sends a refused claim again with Send again", async () => {
    await seed({ itemId: "old", fields: { expenseDate: "2026-08-20" } });
    const { expenses, server } = await openExpenseScreen();
    await screen.findByText(/older than 60 days/);
    // Nothing is sent again by itself.
    const submits = server.callsTo("POST /api/v1/my-expenses/{id}/submit").length;
    expenses.today = "2026-08-30";
    await press("Send again Travel and Fuel ₹850");
    await waitFor(() => expect(expenses.claims[0].status).toBe("SUBMITTED"));
    expect(server.callsTo("POST /api/v1/my-expenses/{id}/submit").length).toBe(submits + 1);
  });

  it("deletes a refused claim and its photo from the phone", async () => {
    await seed({ itemId: "old", fields: { expenseDate: "2026-08-20" } });
    await openExpenseScreen();
    await screen.findByText(/older than 60 days/);
    expect(outboxFiles().length).toBeGreaterThan(0);
    await press("Delete Travel and Fuel ₹850");
    await press("Delete");
    await waitFor(() => expect(screen.queryByText(/Waiting to send/)).toBeNull());
    expect(outboxFiles()).toEqual([]);
  });

  it("shows a stale-version refusal with the server's current values, and the claimant re-applies the change by hand", async () => {
    await seed({
      itemId: "edit",
      intent: "SAVE_DRAFT",
      bills: [],
      serverClaimId: "c-7",
      serverVersion: 3,
      stage: "UPDATE",
      baseSnapshot: { expenseDate: "2026-10-22", amount: "850.00", categoryLabel: "Marketing", note: "old" },
      fields: { category: "MARKETING", note: "phone edit" },
    });
    const { expenses } = await openExpenseScreen({
      expenses: { claims: [claimDetail({ id: "c-7", category: "MARKETING", status: "DRAFT", version: 5, amount: "900.00", note: "web edit" })] },
    });

    expect(await screen.findByText(/changed by someone else/)).toBeTruthy();
    expect(await screen.findByText(/Current claim on the server: ₹900, 22\/10\/2026, web edit/)).toBeTruthy();
    expect(expenses.claims[0].note).toBe("web edit");

    await press("Waiting claim: Marketing, ₹850, 22/10/2026, Not sent");
    await fireEvent.changeText(await screen.findByLabelText("Note"), "web edit and phone edit");
    await fireEvent.changeText(screen.getByLabelText("Amount"), "900");
    await fireEvent.changeText(screen.getByLabelText("Reason there is no bill"), "Cash");
    await press("Save as draft");
    expect(await screen.findByText("Saved as a draft.")).toBeTruthy();
    expect(expenses.claims[0]).toMatchObject({ note: "web edit and phone edit", amount: "900.00" });
  });

  it("holds a possible duplicate for confirmation and sends it on Confirm", async () => {
    await seed({ itemId: "dup", fields: { category: "MARKETING" } });
    const { expenses } = await openExpenseScreen({
      expenses: { claims: [claimDetail({ id: "c-old", category: "MARKETING", status: "SUBMITTED", amount: "850.00", expenseDate: "2026-10-22" })] },
    });
    expect(await screen.findByText("Possible duplicate")).toBeTruthy();
    await press("Confirm Marketing ₹850");
    await waitFor(() => expect(expenses.claims.filter((c) => c.status === "SUBMITTED")).toHaveLength(2));
    await waitFor(() => expect(screen.queryByText(/Waiting to send/)).toBeNull());
  });

  it("leaves the claim a server Draft when the claimant keeps it as a Draft", async () => {
    await seed({ itemId: "dup", fields: { category: "MARKETING" } });
    const { expenses } = await openExpenseScreen({
      expenses: { claims: [claimDetail({ id: "c-old", category: "MARKETING", status: "SUBMITTED", amount: "850.00", expenseDate: "2026-10-22" })] },
    });
    await screen.findByText("Possible duplicate");
    await press("Keep Marketing ₹850 as Draft");
    await waitFor(() => expect(screen.queryByText(/Waiting to send/)).toBeNull());
    const mine = expenses.claims.find((c) => c.id !== "c-old");
    expect(mine).toMatchObject({ status: "DRAFT", billCount: 1 });
    expect(outboxFiles()).toEqual([]);
  });
});
