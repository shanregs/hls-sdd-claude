import { screen, waitFor } from "@testing-library/react-native";
import { listItems } from "../../src/outbox/outboxStore";
import { goOffline, openExpenseScreen } from "../support/expenseApp";
import { fillClaim, openAddForm, press, takeBill } from "../support/expenseActions";
import { bill, claimDetail, ME } from "../support/expenseFixtures";
import type { FakeReply } from "../support/fakeServer";

const refuse = (reason: string): FakeReply => ({ status: 400, body: { reason } });

async function raise() {
  await openAddForm();
  await fillClaim({ amount: "850", category: "Marketing" });
  await takeBill();
  await press("Submit");
}

const noSuccessShown = () => {
  expect(screen.queryByText("Submitted.")).toBeNull();
  expect(screen.queryByText("Saved as a draft.")).toBeNull();
  expect(screen.queryByText("Bills added.")).toBeNull();
};

/** Spec 036 T042 (US4, SC-007): nothing is shown as done before the server confirmed it. */
describe("never a false success", () => {
  it("shows no success when the create is refused, and no claim exists", async () => {
    const { expenses } = await openExpenseScreen({ expenses: { refusals: { create: refuse("amount must be above zero") } } });
    await raise();
    expect(await screen.findByText("amount must be above zero")).toBeTruthy();
    noSuccessShown();
    expect(expenses.claims).toHaveLength(0);
    expect(screen.getByLabelText("Amount")).toBeTruthy();
  });

  it("shows the claim as submitted never when a bill is refused: it stays a Draft with no bill", async () => {
    const { expenses } = await openExpenseScreen({ expenses: { refusals: { bill: refuse("A bill must be a jpg, png or pdf file.") } } });
    await raise();
    expect(await screen.findByText("A bill must be a jpg, png or pdf file.")).toBeTruthy();
    noSuccessShown();
    expect(expenses.claims[0]).toMatchObject({ status: "DRAFT", billCount: 0 });
    expect(expenses.claims[0].history.some((h) => h.event === "SUBMITTED")).toBe(false);
    // Leaving the form shows the claim as the server holds it: a Draft, not Submitted.
    await press("Cancel");
    expect(await screen.findByLabelText(/Marketing, ₹850, .*Draft, no bill/)).toBeTruthy();
    expect(screen.queryByLabelText(/, Submitted, /)).toBeNull();
  });

  it("shows no success when the submit is refused, and the claim stays a Draft", async () => {
    const { expenses } = await openExpenseScreen({ expenses: { refusals: { submit: refuse("The expense is older than 60 days.") } } });
    await raise();
    expect(await screen.findByText("The expense is older than 60 days.")).toBeTruthy();
    noSuccessShown();
    expect(expenses.claims[0].status).toBe("DRAFT");
  });

  it("leaves a Rejected claim as it was when the update is refused", async () => {
    const { expenses } = await openExpenseScreen({
      expenses: {
        claims: [claimDetail({ id: "c-1", status: "REJECTED", category: "MARKETING", amount: "300.00", bills: [bill()], allowedActions: ["EDIT", "SUBMIT", "ADD_BILL"] })],
        refusals: { update: refuse("amount can have at most two decimals") },
      },
    });
    await press(String((await screen.findByLabelText(/₹300/)).props.accessibilityLabel));
    await press("Correct and resubmit");
    await press("Submit");
    expect(await screen.findByText("amount can have at most two decimals")).toBeTruthy();
    noSuccessShown();
    expect(expenses.claims[0]).toMatchObject({ status: "REJECTED", amount: "300.00" });
  });

  it("keeps a claim in the list when its deletion is refused", async () => {
    const { expenses } = await openExpenseScreen({
      expenses: { claims: [claimDetail({ id: "c-1", status: "DRAFT", amount: "50.00" })], refusals: { delete: { status: 409, body: { reason: "Only a Draft claim can be deleted." } } } },
    });
    await press(String((await screen.findByLabelText(/₹50/)).props.accessibilityLabel));
    await press("Delete claim");
    await press("Delete");
    expect(await screen.findByText("Only a Draft claim can be deleted.")).toBeTruthy();
    expect(screen.queryByText("Deleted.")).toBeNull();
    expect(expenses.claims).toHaveLength(1);
  });

  it("leaves everything as it was when a withdrawal fails for want of a connection", async () => {
    const { expenses, server } = await openExpenseScreen({
      expenses: { claims: [claimDetail({ id: "c-1", status: "SUBMITTED", amount: "50.00", allowedActions: ["WITHDRAW"] })] },
    });
    await press(String((await screen.findByLabelText(/₹50/)).props.accessibilityLabel));
    await screen.findByLabelText("Withdraw claim");
    server.offline = true;
    await press("Withdraw claim");
    await press("Withdraw");
    await screen.findByText(/needs a connection/);
    expect(screen.queryByText(/Withdrawn/)).toBeNull();
    expect(expenses.claims[0].status).toBe("SUBMITTED");
  });

  it("keeps an offline claim waiting, not sent, with nothing on the server", async () => {
    const { expenses, server } = await openExpenseScreen();
    goOffline(server);
    await raise();
    expect(await screen.findByText("Waiting to send (1)")).toBeTruthy();
    expect(screen.queryByText("Submitted.")).toBeNull();
    expect(screen.queryByLabelText(/, Submitted, /)).toBeNull();
    expect(expenses.claims).toHaveLength(0);
    await waitFor(async () => expect((await listItems(ME.id)).items).toHaveLength(1));
    expect((await listItems(ME.id)).items[0].state).toBe("WAITING");
  });
});
