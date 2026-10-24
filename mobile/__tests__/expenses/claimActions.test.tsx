import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import { NEEDS_CONNECTION_TEXT } from "../../src/expenses/expenseMessages";
import { openExpenseScreen } from "../support/expenseApp";
import { press, takeBill } from "../support/expenseActions";
import { bill, claimDetail } from "../support/expenseFixtures";

async function openClaim(label: RegExp) {
  await press(String((await screen.findByLabelText(label)).props.accessibilityLabel));
}

/** Spec 036 T036: acting on a claim as the server allows (US3). */
describe("claim actions", () => {
  it("withdraws a Submitted claim after confirming, and it becomes a Draft", async () => {
    const { expenses, server } = await openExpenseScreen({
      expenses: { claims: [claimDetail({ id: "c-1", status: "SUBMITTED", amount: "200.00", version: 4, allowedActions: ["WITHDRAW"], bills: [bill()] })] },
    });
    await openClaim(/₹200/);
    await press("Withdraw claim");
    expect(expenses.claims[0].status).toBe("SUBMITTED");
    await press("Withdraw");
    expect(await screen.findByText("Draft")).toBeTruthy();
    expect(expenses.claims[0].status).toBe("DRAFT");
    expect(server.callsTo("POST /api/v1/my-expenses/{id}/withdraw")[0].body).toEqual({ version: 4 });
    expect(await screen.findByLabelText("Delete claim")).toBeTruthy();
    expect(screen.queryByLabelText("Withdraw claim")).toBeNull();
  });

  it("deletes a Draft after confirming and removes it from the list", async () => {
    const { expenses } = await openExpenseScreen({ expenses: { claims: [claimDetail({ id: "c-1", status: "DRAFT", amount: "200.00" })] } });
    await openClaim(/₹200/);
    await press("Delete claim");
    await press("Delete");
    expect(await screen.findByText("Deleted.")).toBeTruthy();
    expect(expenses.claims).toHaveLength(0);
    await waitFor(() => expect(screen.queryByLabelText(/₹200/)).toBeNull());
  });

  it("changes nothing when the server refuses a withdrawal, and shows its reason", async () => {
    const { expenses } = await openExpenseScreen({
      expenses: {
        claims: [claimDetail({ id: "c-1", status: "SUBMITTED", amount: "200.00", allowedActions: ["WITHDRAW"] })],
        refusals: { withdraw: { status: 409, body: { reason: "This claim has just been approved." } } },
      },
    });
    await openClaim(/₹200/);
    await press("Withdraw claim");
    await press("Withdraw");
    expect(await screen.findByText("This claim has just been approved.")).toBeTruthy();
    expect(expenses.claims[0].status).toBe("SUBMITTED");
    expect(screen.queryByText("Draft")).toBeNull();
  });

  it("explains that withdrawing needs a connection and does nothing offline", async () => {
    const { expenses, server } = await openExpenseScreen({
      expenses: { claims: [claimDetail({ id: "c-1", status: "SUBMITTED", amount: "200.00", allowedActions: ["WITHDRAW"] })] },
    });
    await openClaim(/₹200/);
    await screen.findByLabelText("Withdraw claim");
    server.offline = true;
    await press("Withdraw claim");
    await press("Withdraw");
    expect(await screen.findByText(NEEDS_CONNECTION_TEXT)).toBeTruthy();
    expect(expenses.claims[0].status).toBe("SUBMITTED");
  });

  it("explains that deleting needs a connection and does nothing offline", async () => {
    const { expenses, server } = await openExpenseScreen({ expenses: { claims: [claimDetail({ id: "c-1", status: "DRAFT", amount: "200.00" })] } });
    await openClaim(/₹200/);
    await screen.findByLabelText("Delete claim");
    server.offline = true;
    await press("Delete claim");
    await press("Delete");
    expect(await screen.findByText(NEEDS_CONNECTION_TEXT)).toBeTruthy();
    expect(expenses.claims).toHaveLength(1);
  });

  it("corrects and resubmits a Rejected claim as the same claim", async () => {
    const { expenses, server } = await openExpenseScreen({
      expenses: {
        claims: [
          claimDetail({
            id: "c-1",
            status: "REJECTED",
            category: "MARKETING",
            amount: "300.00",
            note: "Banners",
            version: 7,
            bills: [bill()],
            decision: { by: "u-5", byName: "Divya", at: "2026-10-22T05:00:00Z", reason: "Amount does not match the bill" },
            allowedActions: ["EDIT", "SUBMIT", "ADD_BILL"],
          }),
        ],
      },
    });
    await openClaim(/₹300/);
    await press("Correct and resubmit");
    expect(await screen.findByText("Rejected: Amount does not match the bill")).toBeTruthy();
    expect(screen.getByLabelText("Amount").props.value).toBe("300.00");
    expect(screen.getByLabelText("Note").props.value).toBe("Banners");
    await fireEvent.changeText(screen.getByLabelText("Amount"), "280");
    await press("Submit");

    expect(await screen.findByText("Submitted.")).toBeTruthy();
    expect(expenses.claims).toHaveLength(1);
    expect(expenses.claims[0]).toMatchObject({ id: "c-1", status: "SUBMITTED", amount: "280.00" });
    expect(server.callsTo("POST /api/v1/my-expenses")).toHaveLength(0);
    expect(server.callsTo("PUT /api/v1/my-expenses/{id}")[0].body).toMatchObject({ version: 7, amount: "280.00" });
    expect(server.callsTo("POST /api/v1/my-expenses/{id}/submit")).toHaveLength(1);
  });

  it("edits a Draft and saves it as a Draft", async () => {
    const { expenses } = await openExpenseScreen({
      expenses: { claims: [claimDetail({ id: "c-1", status: "DRAFT", category: "OFFICE_SUPPLIES", amount: "50.00", note: "Pens" })] },
    });
    await openClaim(/₹50/);
    await press("Edit");
    await fireEvent.changeText(await screen.findByLabelText("Note"), "Pens and paper");
    await press("Save as draft");
    expect(await screen.findByText("Saved as a draft.")).toBeTruthy();
    expect(expenses.claims[0]).toMatchObject({ status: "DRAFT", note: "Pens and paper" });
  });

  it("adds a bill to a claim", async () => {
    const { expenses } = await openExpenseScreen({ expenses: { claims: [claimDetail({ id: "c-1", status: "DRAFT", amount: "50.00" })] } });
    await openClaim(/₹50/);
    await press("Add bill");
    await takeBill();
    await press("Upload bills");
    expect(await screen.findByText("Bills added.")).toBeTruthy();
    expect(expenses.claims[0].bills).toHaveLength(1);
    expect(await screen.findByLabelText(/Bill 1: bill-/)).toBeTruthy();
  });

  it("shows nothing as added when the server refuses the bill", async () => {
    const { expenses } = await openExpenseScreen({
      expenses: { claims: [claimDetail({ id: "c-1", status: "DRAFT", amount: "50.00" })], refusals: { bill: { status: 400, body: { reason: "A claim can have at most 5 bills." } } } },
    });
    await openClaim(/₹50/);
    await press("Add bill");
    await takeBill();
    await press("Upload bills");
    expect(await screen.findByText("A claim can have at most 5 bills.")).toBeTruthy();
    expect(screen.queryByText("Bills added.")).toBeNull();
    expect(expenses.claims[0].bills).toHaveLength(0);
  });

  it("submits a Draft that has a bill, and shows the server's reason when it has none", async () => {
    const { expenses } = await openExpenseScreen({
      expenses: {
        claims: [
          claimDetail({ id: "c-ok", status: "DRAFT", category: "MARKETING", amount: "10.00", bills: [bill()] }),
          claimDetail({ id: "c-bad", status: "DRAFT", category: "OFFICE_SUPPLIES", amount: "20.00" }),
        ],
      },
    });
    await openClaim(/Office Supplies, ₹20/);
    await press("Submit claim");
    expect(await screen.findByText(/Add a bill or give the reason/)).toBeTruthy();
    expect(expenses.claims.find((c) => c.id === "c-bad")?.status).toBe("DRAFT");
    await press("Back to the expenses");
    await openClaim(/Marketing, ₹10/);
    await press("Submit claim");
    await waitFor(() => expect(expenses.claims.find((c) => c.id === "c-ok")?.status).toBe("SUBMITTED"));
    // Nothing is left waiting on the phone after either send.
    expect(screen.queryByText(/Waiting to send/)).toBeNull();
  });
});
