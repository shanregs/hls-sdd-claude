import { screen } from "@testing-library/react-native";
import { openExpenseScreen } from "../support/expenseApp";
import { claimDetail } from "../support/expenseFixtures";
import { fillClaim, openAddForm, press, takeBill } from "../support/expenseActions";

const existing = () => claimDetail({ id: "c-old", status: "SUBMITTED", amount: "850.00", expenseDate: "2026-10-22", category: "MARKETING" });

async function fillSameClaim() {
  await openAddForm();
  await fillClaim({ amount: "850", category: "Marketing", date: { from: "2026-10-23", to: "2026-10-22" } });
  await takeBill();
  await press("Submit");
}

/** Spec 036 T021: the server's possible-duplicate warning (US1). */
describe("duplicate warning", () => {
  it("opens the dialog with the matching claims when the server warns", async () => {
    await openExpenseScreen({ expenses: { claims: [existing()] } });
    await fillSameClaim();
    expect(await screen.findByText("You may have raised this already: same date, amount and category.")).toBeTruthy();
    expect(await screen.findByLabelText(/Matching claim Marketing ₹850 Submitted/)).toBeTruthy();
  });

  it("resends with confirmDuplicate true when the claimant submits anyway", async () => {
    const { expenses, server } = await openExpenseScreen({ expenses: { claims: [existing()] } });
    await fillSameClaim();
    await press("Submit anyway");
    expect(await screen.findByText("Submitted.")).toBeTruthy();
    const submits = server.callsTo("POST /api/v1/my-expenses/{id}/submit").map((c) => c.body as { confirmDuplicate: boolean });
    expect(submits.map((s) => s.confirmDuplicate)).toEqual([false, true]);
    expect(expenses.claims.filter((c) => c.status === "SUBMITTED")).toHaveLength(2);
    expect(server.callsTo("POST /api/v1/my-expenses")).toHaveLength(1);
  });

  it("keeps the entries and leaves a Draft on the server when the claimant cancels", async () => {
    const { expenses } = await openExpenseScreen({ expenses: { claims: [existing()] } });
    await fillSameClaim();
    await screen.findByText("Possible duplicate");
    await press("Cancel (possible duplicate)");
    expect(screen.queryByText("Possible duplicate")).toBeNull();
    expect(screen.getByLabelText("Amount").props.value).toBe("850");
    const mine = expenses.claims.find((c) => c.id !== "c-old");
    expect(mine?.status).toBe("DRAFT");
  });
});
