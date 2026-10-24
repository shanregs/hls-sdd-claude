import { screen } from "@testing-library/react-native";
import { openExpenseScreen } from "../support/expenseApp";
import { press } from "../support/expenseActions";
import { bill, claimDetail } from "../support/expenseFixtures";

const JPEG = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0, 16, 0x4a, 0x46, 0x49, 0x46, 0, 1]);

async function openClaim(label: RegExp) {
  await press(String((await screen.findByLabelText(label)).props.accessibilityLabel));
}

const buttons = () =>
  ["Edit", "Correct and resubmit", "Submit claim", "Add bill", "Withdraw claim", "Delete claim"].filter((label) => screen.queryByLabelText(label));

/** Spec 036 T035: one claim, with its bills, history and decision (US3). */
describe("claim detail", () => {
  it("shows the facts, the bills with a thumbnail, the removed bill struck through, the link and the history", async () => {
    const { expenses } = await openExpenseScreen({
      expenses: {
        claims: [
          claimDetail({
            id: "c-1",
            status: "SUBMITTED",
            category: "TRAVEL_FUEL",
            amount: "850.00",
            note: "Petrol, school visit",
            link: { kind: "SCHOOL_VISIT", kindLabel: "School visit", ref: "r", date: "2026-10-22", label: "School visit: Green Valley" },
            linkLabel: "School visit: Green Valley",
            bills: [bill({ id: "b-1", name: "bill-1.jpg" }), bill({ id: "b-2", name: "old.pdf", contentType: "application/pdf", removed: true, removedByName: "Divya", removalReason: "Unreadable" })],
            history: [
              { at: "2026-10-23T04:00:00Z", actorId: "u-1", actorName: "Tara", event: "CREATED", reason: null, detail: null },
              { at: "2026-10-23T04:05:00Z", actorId: "u-1", actorName: "Tara", event: "SUBMITTED", reason: null, detail: null },
            ],
            ageDays: 2,
            submittedAt: "2026-10-21T04:00:00Z",
            allowedActions: ["WITHDRAW"],
          }),
        ],
      },
    });
    expenses.files["b-1"] = { bytes: JPEG, contentType: "image/jpeg" };
    await openClaim(/Travel and Fuel, ₹850/);

    expect(await screen.findByText("Travel and Fuel, ₹850")).toBeTruthy();
    expect(screen.getByText("Submitted, 2 days")).toBeTruthy();
    expect(screen.getByText("Note: Petrol, school visit")).toBeTruthy();
    expect(screen.getByText("Linked work: School visit: Green Valley")).toBeTruthy();
    expect(await screen.findByLabelText("Bill photo bill-1.jpg")).toBeTruthy();
    expect(screen.getByLabelText(/Bill 2 \(removed\): old.pdf, PDF/)).toBeTruthy();
    expect(screen.getByText("Removed by Divya: Unreadable")).toBeTruthy();
    expect(screen.getByText("1 of 5")).toBeTruthy();
    expect(screen.getByText(/Tara: Created/)).toBeTruthy();
    expect(screen.getByText(/Tara: Submitted/)).toBeTruthy();
  });

  it("shows who rejected a claim, when and why, with Correct and resubmit", async () => {
    await openExpenseScreen({
      expenses: {
        claims: [
          claimDetail({
            id: "c-1",
            status: "REJECTED",
            amount: "300.00",
            decision: { by: "u-5", byName: "Divya", at: "2026-10-22T05:00:00Z", reason: "Bill is not readable" },
            allowedActions: ["EDIT", "SUBMIT", "ADD_BILL"],
          }),
        ],
      },
    });
    await openClaim(/₹300/);
    expect(await screen.findByText("Rejected by Divya on 22/10/2026 10:30")).toBeTruthy();
    expect(screen.getByText("Reason: Bill is not readable")).toBeTruthy();
    expect(buttons()).toEqual(["Correct and resubmit", "Submit claim", "Add bill"]);
  });

  it("shows the payment details of a Paid claim and no buttons", async () => {
    await openExpenseScreen({
      expenses: {
        claims: [
          claimDetail({
            id: "c-1",
            status: "PAID",
            payment: { how: "Payroll", paidOn: "2026-10-30", mode: "Bank transfer", reference: "UTR12345" },
            allowedActions: [],
          }),
        ],
      },
    });
    await openClaim(/Paid,/);
    expect(await screen.findByText("Paid on: 30/10/2026")).toBeTruthy();
    expect(screen.getByText("Mode: Bank transfer")).toBeTruthy();
    expect(screen.getByText("Reference: UTR12345")).toBeTruthy();
    expect(buttons()).toEqual([]);
  });

  it("offers a button only for each action the server lists", async () => {
    await openExpenseScreen({
      expenses: {
        claims: [
          claimDetail({ id: "c-d", status: "DRAFT", category: "MARKETING", amount: "10.00", allowedActions: ["EDIT", "SUBMIT", "DELETE", "ADD_BILL"] }),
          claimDetail({ id: "c-s", status: "SUBMITTED", category: "OFFICE_SUPPLIES", amount: "20.00", allowedActions: ["WITHDRAW"] }),
        ],
      },
    });
    await openClaim(/Marketing, ₹10/);
    await screen.findByText("Marketing, ₹10");
    expect(buttons()).toEqual(["Edit", "Submit claim", "Add bill", "Delete claim"]);
    await press("Back to the expenses");
    await openClaim(/Office Supplies, ₹20/);
    await screen.findByText("Office Supplies, ₹20");
    expect(buttons()).toEqual(["Withdraw claim"]);
  });

  it("never shows a review action, even if the server's list held one", async () => {
    await openExpenseScreen({
      expenses: {
        claims: [claimDetail({ id: "c-s", status: "SUBMITTED", amount: "20.00", allowedActions: ["WITHDRAW", "APPROVE", "REJECT", "REVERSE", "RECORD_PAID", "REMOVE_BILL"] })],
      },
    });
    await openClaim(/₹20/);
    await screen.findByLabelText("Withdraw claim");
    for (const label of ["Approve", "Reject", "Reverse", "Record payment", "Remove bill"]) {
      expect(screen.queryByText(new RegExp(label, "i"))).toBeNull();
    }
    expect(buttons()).toEqual(["Withdraw claim"]);
  });

  it("says a claim that is gone was not found", async () => {
    const { expenses } = await openExpenseScreen({ expenses: { claims: [claimDetail({ id: "c-1", amount: "20.00" })] } });
    await screen.findByLabelText(/₹20/);
    expenses.claims = [];
    await press(String(screen.getByLabelText(/₹20/).props.accessibilityLabel));
    expect(await screen.findByText("Claim not found")).toBeTruthy();
  });
});
