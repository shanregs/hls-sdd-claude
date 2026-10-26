import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import { SAVED_OFFLINE_TEXT } from "../../src/expenses/expenseMessages";
import { formatLongDate } from "../../src/formats/dates";
import { goOnline, openExpenseScreen, restartApp } from "../support/expenseApp";
import { fillClaim, openAddForm, press, takeBill } from "../support/expenseActions";

async function raise(amount = "850") {
  await openAddForm();
  await fillClaim({ amount, category: "Marketing", note: "Banners" });
  await takeBill();
  await press("Submit");
}

/** Spec 036 T028: a kill or a lost reply at any step still ends in one claim and one copy of each bill (US2, SC-002). */
describe("sync recovery", () => {
  it("makes one claim when the reply to the create is lost", async () => {
    const { expenses, server } = await openExpenseScreen({ expenses: { loseReply: { create: 1 } } });
    await raise();
    await screen.findByText(SAVED_OFFLINE_TEXT);
    expect(expenses.claims).toHaveLength(1);

    goOnline(server);
    expect(await screen.findByLabelText(/Marketing, ₹850, .*Submitted, 1 bill/)).toBeTruthy();
    expect(expenses.claims).toHaveLength(1);
    expect(expenses.claims[0].billCount).toBe(1);
    // The retry used the same request id and the server answered it with the first claim.
    const ids = server.callsTo("POST /api/v1/my-expenses").map((c) => (c.body as { clientRequestId: string }).clientRequestId);
    expect(new Set(ids).size).toBe(1);
  });

  it("adds no second copy of a bill when the reply to the upload is lost", async () => {
    const { expenses, server } = await openExpenseScreen({ expenses: { loseReply: { bill: 1 } } });
    await raise();
    await screen.findByText(SAVED_OFFLINE_TEXT);
    expect(expenses.claims[0].bills).toHaveLength(1);

    goOnline(server);
    expect(await screen.findByLabelText(/Marketing, ₹850, .*Submitted, 1 bill/)).toBeTruthy();
    expect(expenses.uploads).toHaveLength(1);
    expect(expenses.claims).toHaveLength(1);
  });

  it("counts the claim as sent when the reply to the submit is lost, and submits only once", async () => {
    const { expenses, server } = await openExpenseScreen({ expenses: { loseReply: { submit: 1 } } });
    await raise();
    await screen.findByText(SAVED_OFFLINE_TEXT);
    expect(expenses.claims[0].status).toBe("SUBMITTED");

    goOnline(server);
    await screen.findByLabelText(/Marketing, ₹850, .*Submitted/);
    await waitFor(() => expect(screen.queryByText(/Waiting to send/)).toBeNull());
    expect(server.callsTo("POST /api/v1/my-expenses/{id}/submit")).toHaveLength(1);
  });

  it("resumes after the app is killed once the claim and its bill are on the server", async () => {
    const { expenses, server } = await openExpenseScreen();
    expenses.refusals.submit = { status: 503, body: { message: "busy" } };
    await raise();
    await screen.findByText(SAVED_OFFLINE_TEXT);
    expect(expenses.claims[0]).toMatchObject({ status: "DRAFT", billCount: 1 });

    await restartApp();
    expect(await screen.findByText("Waiting to send (1)")).toBeTruthy();
    delete expenses.refusals.submit;
    goOnline(server);
    expect(await screen.findByLabelText(/Marketing, ₹850, .*Submitted, 1 bill/)).toBeTruthy();
    expect(expenses.claims).toHaveLength(1);
    expect(expenses.uploads).toHaveLength(1);
    expect(server.callsTo("POST /api/v1/my-expenses")).toHaveLength(1);
  });

  it("resumes after a kill between the claim and its bill without a second claim", async () => {
    const { expenses, server } = await openExpenseScreen();
    expenses.refusals.bill = { status: 503, body: { message: "busy" } };
    await raise();
    await screen.findByText(SAVED_OFFLINE_TEXT);
    expect(expenses.claims).toHaveLength(1);

    await restartApp();
    await screen.findByText("Waiting to send (1)");
    delete expenses.refusals.bill;
    goOnline(server);
    expect(await screen.findByLabelText(/Marketing, ₹850, .*Submitted, 1 bill/)).toBeTruthy();
    expect(expenses.claims).toHaveLength(1);
    expect(server.callsTo("POST /api/v1/my-expenses")).toHaveLength(1);
  });

  it("keeps the claim id when the submit is refused, and a corrected date updates the same claim", async () => {
    const { expenses, server } = await openExpenseScreen();
    await openAddForm();
    await fillClaim({ amount: "850", category: "Marketing" });
    // 20 August is 64 days before the server's date, over the limit the server checks at submit.
    await fireEvent.press(await screen.findByLabelText(`Date, ${formatLongDate("2026-10-23")}`));
    await fireEvent.press(screen.getByLabelText("Previous month"));
    await fireEvent.press(screen.getByLabelText("Previous month"));
    await fireEvent.press(await screen.findByLabelText(formatLongDate("2026-08-20")));
    await takeBill();
    await press("Submit");
    expect(await screen.findByText(/older than 60 days/)).toBeTruthy();
    expect(expenses.claims).toHaveLength(1);

    await fireEvent.press(await screen.findByLabelText(`Date, ${formatLongDate("2026-08-20")}`));
    await fireEvent.press(screen.getByLabelText("Next month"));
    await fireEvent.press(screen.getByLabelText("Next month"));
    await fireEvent.press(await screen.findByLabelText(formatLongDate("2026-10-20")));
    await press("Submit");

    expect(await screen.findByText("Submitted.")).toBeTruthy();
    expect(expenses.claims).toHaveLength(1);
    expect(expenses.claims[0]).toMatchObject({ status: "SUBMITTED", expenseDate: "2026-10-20" });
    expect(server.callsTo("POST /api/v1/my-expenses")).toHaveLength(1);
    expect(server.callsTo("PUT /api/v1/my-expenses/{id}")).toHaveLength(1);
    expect(expenses.uploads).toHaveLength(1);
  });
});
