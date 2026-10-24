import { act, fireEvent, screen } from "@testing-library/react-native";
import type { ClaimBody } from "../../src/api/expensesApi";
import { openExpenseScreen } from "../support/expenseApp";
import { fillClaim, openAddForm, press, takeBill } from "../support/expenseActions";
import { linkOption } from "../support/expenseFixtures";
import type { FakeReply } from "../support/fakeServer";

const fsState = () => jest.requireMock("expo-file-system/legacy").__state;
const outboxFiles = () => [...(fsState().files as Map<string, string>).keys()].filter((k) => k.includes("/outbox/") && !k.endsWith("list-cache.enc"));

/** Spec 036 T018: raising a claim with a bill photo, with a signal (US1). */
describe("raise a claim", () => {
  it("submits a Travel and Fuel claim with a bill, and lists it Submitted", async () => {
    const { expenses, server } = await openExpenseScreen();
    await openAddForm();
    await fillClaim({ amount: "850", category: "Travel and Fuel", date: { from: "2026-10-23", to: "2026-10-22" }, note: "Petrol, school visit" });
    await takeBill();
    await press("Submit");

    expect(await screen.findByText("Submitted.")).toBeTruthy();
    expect(await screen.findByLabelText(/Travel and Fuel, ₹850, 22\/10\/2026, Head office, Submitted, 1 bill/)).toBeTruthy();
    expect(expenses.claims).toHaveLength(1);
    expect(expenses.claims[0]).toMatchObject({ status: "SUBMITTED", billCount: 1, amount: "850.00", expenseDate: "2026-10-22" });
    const sent = server.callsTo("POST /api/v1/my-expenses")[0].body as ClaimBody;
    expect(sent).toMatchObject({
      amount: "850.00",
      category: "TRAVEL_FUEL",
      expenseDate: "2026-10-22",
      note: "Petrol, school visit",
      paidBy: "CLAIMANT",
      zoneId: null,
      link: null,
      confirmDuplicate: false,
      submit: false,
    });
    expect(sent.clientRequestId).toMatch(/^[0-9a-f-]{36}$/);
  });

  it("keeps the form open until the last step is confirmed by the server", async () => {
    let release: (reply: FakeReply) => void = () => undefined;
    const { expenses } = await openExpenseScreen({
      prepare: (server) => {
        const gate = new Promise<FakeReply>((resolve) => {
          release = resolve;
        });
        server.on("POST /api/v1/my-expenses/{id}/submit", () => gate);
      },
    });
    await openAddForm();
    await fillClaim({ amount: "850", category: "Marketing" });
    await takeBill();
    await press("Submit");

    // The claim and the bill are on the server, the submit has not been answered: nothing is shown as sent.
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 50));
    });
    expect(screen.queryByText("Submitted.")).toBeNull();
    expect(screen.getByLabelText("Amount")).toBeTruthy();
    expect(expenses.claims[0].status).toBe("DRAFT");

    release({ status: 200, body: { ...expenses.claims[0], status: "SUBMITTED", statusLabel: "Submitted", allowedActions: ["WITHDRAW"] } });
    expect(await screen.findByText("Submitted.")).toBeTruthy();
  });

  it("saves as a draft and leaves a server Draft", async () => {
    const { expenses, server } = await openExpenseScreen();
    await openAddForm();
    await fillClaim({ amount: "120.50", category: "Office Supplies" });
    await press("Save as draft");

    expect(await screen.findByText("Saved as a draft.")).toBeTruthy();
    expect(expenses.claims[0]).toMatchObject({ status: "DRAFT", amount: "120.50" });
    expect(server.callsTo("POST /api/v1/my-expenses/{id}/submit")).toHaveLength(0);
    expect(await screen.findByLabelText(/Office Supplies, ₹120.50, .*Draft, no bill/)).toBeTruthy();
  });

  it("links planned work chosen on a Travel and Fuel claim", async () => {
    const { expenses } = await openExpenseScreen({ expenses: { linkOptions: [linkOption()] } });
    await openAddForm();
    await fillClaim({ amount: "850", category: "Travel and Fuel", date: { from: "2026-10-23", to: "2026-10-22" } });
    await press("School visit: Green Valley Public School");
    await press("Save as draft");
    await screen.findByText("Saved as a draft.");
    expect(expenses.claims[0].link?.ref).toBe("link-visit-1");
  });

  it("sends once when Submit is tapped twice", async () => {
    const { server } = await openExpenseScreen();
    await openAddForm();
    await fillClaim({ amount: "850", category: "Marketing" });
    await takeBill();
    const submit = screen.getByLabelText("Submit");
    await fireEvent.press(submit);
    await fireEvent.press(submit);
    await screen.findByText("Submitted.");
    expect(server.callsTo("POST /api/v1/my-expenses")).toHaveLength(1);
    expect(server.callsTo("POST /api/v1/my-expenses/{id}/submit")).toHaveLength(1);
  });

  it("leaves no item and no file on the phone after success", async () => {
    await openExpenseScreen();
    await openAddForm();
    await fillClaim({ amount: "850", category: "Marketing" });
    await takeBill();
    await press("Submit");
    await screen.findByText("Submitted.");
    expect(outboxFiles()).toEqual([]);
  });

  it("keeps Submit disabled until the amount, a category and a bill or reason are there", async () => {
    await openExpenseScreen();
    await openAddForm();
    const disabled = () => screen.getByLabelText("Submit").props.accessibilityState?.disabled === true;
    expect(disabled()).toBe(true);
    await fillClaim({ amount: "850" });
    expect(disabled()).toBe(true);
    await fillClaim({ category: "Marketing" });
    expect(disabled()).toBe(true);
    await fireEvent.changeText(screen.getByLabelText("Reason there is no bill"), "Paid in cash at a stall");
    expect(disabled()).toBe(false);
  });

  it("shows a server refusal in place and keeps every entry", async () => {
    const { server } = await openExpenseScreen({
      prepare: (s) => s.on("POST /api/v1/my-expenses/{id}/submit", { status: 400, body: { reason: "The expense is older than 60 days." } }),
    });
    await openAddForm();
    await fillClaim({ amount: "850", category: "Marketing", note: "Banners" });
    await takeBill();
    await press("Submit");
    expect(await screen.findByText("The expense is older than 60 days.")).toBeTruthy();
    expect(screen.getByLabelText("Amount").props.value).toBe("850");
    expect(screen.getByLabelText("Note").props.value).toBe("Banners");
    expect(server.callsTo("POST /api/v1/my-expenses")).toHaveLength(1);

    // Sent again after the server accepts it: the same claim, not a second one.
    server.on("POST /api/v1/my-expenses/{id}/submit", (request) => ({ status: 200, body: { id: request.params.id } }));
    await press("Submit");
    expect(await screen.findByText("Submitted.")).toBeTruthy();
    expect(server.callsTo("POST /api/v1/my-expenses")).toHaveLength(1);
    expect(server.callsTo("PUT /api/v1/my-expenses/{id}")).toHaveLength(1);
  });
});
