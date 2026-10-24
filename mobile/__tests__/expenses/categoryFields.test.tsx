import { screen } from "@testing-library/react-native";
import { openExpenseScreen } from "../support/expenseApp";
import { fillClaim, openAddForm, press, takeBill } from "../support/expenseActions";
import { linkOption } from "../support/expenseFixtures";

const disabled = () => screen.getByLabelText("Submit").props.accessibilityState?.disabled === true;
const batch = (overrides = {}) => linkOption({ kind: "INDUCTION_BATCH", kindLabel: "Induction batch", ref: "batch-1", label: "Batch Oct 2026", date: null, ...overrides });

/** Spec 036 T019: what each category asks for (US1). */
describe("category fields", () => {
  it("asks for a reason for Other and keeps Submit disabled until it is given", async () => {
    await openExpenseScreen();
    await openAddForm();
    await fillClaim({ amount: "400", category: "Other" });
    await takeBill();
    expect(screen.getByLabelText("Reason")).toBeTruthy();
    expect(disabled()).toBe(true);
    await fillClaim({ reason: "Courier to a school" });
    expect(disabled()).toBe(false);
  });

  it("does not show a reason field for the other categories", async () => {
    await openExpenseScreen();
    await openAddForm();
    await fillClaim({ amount: "400", category: "Marketing" });
    expect(screen.queryByLabelText("Reason")).toBeNull();
  });

  it("requires a batch for Training Stay and offers the server's batches", async () => {
    const { expenses } = await openExpenseScreen({ expenses: { batches: [batch()] } });
    await openAddForm();
    await fillClaim({ amount: "2500", category: "Training Stay" });
    await takeBill();
    expect(await screen.findByLabelText("Batch Oct 2026")).toBeTruthy();
    expect(disabled()).toBe(true);
    await press("Batch Oct 2026");
    expect(disabled()).toBe(false);
    await press("Submit");
    await screen.findByText("Submitted.");
    expect(expenses.claims[0].link?.ref).toBe("batch-1");
  });

  it("offers the planned work around the chosen date for Travel and Fuel, and none is needed", async () => {
    const { server } = await openExpenseScreen({ expenses: { linkOptions: [linkOption()] } });
    await openAddForm();
    await fillClaim({ amount: "850", category: "Travel and Fuel", date: { from: "2026-10-23", to: "2026-10-22" } });
    expect(await screen.findByLabelText("School visit: Green Valley Public School")).toBeTruthy();
    expect(server.callsTo("GET /api/v1/my-expenses/link-options").pop()?.query).toEqual({ category: "TRAVEL_FUEL", date: "2026-10-22" });
    await fillClaim({ reason: undefined });
    await fillClaim({ note: "Petrol" });
    await takeBill();
    expect(disabled()).toBe(false);
  });

  it("reloads the choices when the date changes and clears a choice that is no longer offered", async () => {
    const { server } = await openExpenseScreen({
      prepare: (s) =>
        s.on("GET /api/v1/my-expenses/link-options", (request) => ({
          status: 200,
          body: request.query.date === "2026-10-23" ? [linkOption({ ref: "today-visit", label: "Visit today" })] : [linkOption({ ref: "old-visit", label: "Visit yesterday" })],
        })),
    });
    await openAddForm();
    await fillClaim({ amount: "850", category: "Travel and Fuel" });
    await press("Visit today");
    await fillClaim({ date: { from: "2026-10-23", to: "2026-10-22" } });
    expect(await screen.findByLabelText("Visit yesterday")).toBeTruthy();
    expect(screen.queryByLabelText("Visit today")).toBeNull();
    await press("Save as draft");
    await screen.findByText("Saved as a draft.");
    const body = server.callsTo("POST /api/v1/my-expenses")[0].body as { link: unknown };
    expect(body.link).toBeNull();
  });

  it("sends no link for a category that takes none", async () => {
    const { server } = await openExpenseScreen({ expenses: { linkOptions: [linkOption()] } });
    await openAddForm();
    await fillClaim({ amount: "850", category: "Travel and Fuel" });
    await press("School visit: Green Valley Public School");
    await fillClaim({ category: "Marketing" });
    expect(screen.queryByText("Linked work (optional)")).toBeNull();
    await press("Save as draft");
    await screen.findByText("Saved as a draft.");
    expect((server.callsTo("POST /api/v1/my-expenses")[0].body as { link: unknown }).link).toBeNull();
  });

  it("shows a server refusal such as 'reason required' with every entry kept", async () => {
    await openExpenseScreen({
      prepare: (s) => s.on("POST /api/v1/my-expenses/{id}/submit", { status: 400, body: { reason: "reason required" } }),
    });
    await openAddForm();
    await fillClaim({ amount: "400", category: "Other", reason: "Courier" });
    await takeBill();
    await press("Submit");
    expect(await screen.findByText("reason required")).toBeTruthy();
    expect(screen.getByLabelText("Reason").props.value).toBe("Courier");
    expect(screen.getByLabelText("Amount").props.value).toBe("400");
  });
});
