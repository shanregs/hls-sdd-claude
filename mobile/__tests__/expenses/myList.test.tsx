import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import { openExpenseScreen } from "../support/expenseApp";
import { press } from "../support/expenseActions";
import { claimDetail, expenseModel } from "../support/expenseFixtures";

const claims = () => [
  claimDetail({ id: "c-draft", status: "DRAFT", expenseDate: "2026-10-20", amount: "100.00", category: "MARKETING" }),
  claimDetail({ id: "c-sub", status: "SUBMITTED", expenseDate: "2026-10-22", amount: "200.00", category: "OFFICE_SUPPLIES", submittedAt: "2026-10-22T04:00:00Z", ageDays: 1 }),
  claimDetail({ id: "c-app", status: "APPROVED", expenseDate: "2026-10-10", amount: "1000.00", category: "TRAVEL_FUEL" }),
  claimDetail({ id: "c-app2", status: "APPROVED", expenseDate: "2026-10-05", amount: "850.00", category: "TRAVEL_FUEL" }),
  claimDetail({ id: "c-appco", status: "APPROVED", expenseDate: "2026-10-04", amount: "9999.00", category: "RENT_UTILITIES", paidBy: "COMPANY" }),
  claimDetail({ id: "c-rej", status: "REJECTED", expenseDate: "2026-09-12", amount: "300.00", category: "OTHER", reason: "x" }),
  claimDetail({ id: "c-paid", status: "PAID", expenseDate: "2026-09-02", amount: "400.00", category: "TRAINING_STAY" }),
  claimDetail({ id: "c-pbc", status: "PAID_BY_COMPANY", expenseDate: "2026-09-01", amount: "500.00", category: "MARKETING", paidBy: "COMPANY" }),
];

const cards = () => screen.queryAllByRole("button").filter((b) => /, (Draft|Submitted|Approved|Rejected|Paid|Paid by company), /.test(String(b.props.accessibilityLabel ?? "")));

/** Spec 036 T034: My Expenses, the claimant's own list (US3). */
describe("my expenses list", () => {
  it("shows the claimant's own claims newest first, with the server's owed total", async () => {
    await openExpenseScreen({ expenses: { claims: claims() } });
    await screen.findByLabelText(/Marketing, ₹100, 20\/10\/2026, Head office, Draft/);
    const dates = cards().map((b) => String(b.props.accessibilityLabel).split(", ")[2]);
    expect(dates).toEqual(["22/10/2026", "20/10/2026", "10/10/2026", "05/10/2026", "04/10/2026", "12/09/2026", "02/09/2026", "01/09/2026"]);
    // Approved and paid by the claimant: 1000 + 850. The one paid by the company is not owed.
    expect(screen.getByText("Owed to you: ₹1,850")).toBeTruthy();
  });

  it("filters by each status through the server", async () => {
    const { server } = await openExpenseScreen({ expenses: { claims: claims() } });
    await screen.findByLabelText(/Marketing, ₹100/);
    const cases: [string, string, number][] = [
      ["Draft", "DRAFT", 1],
      ["Submitted", "SUBMITTED", 1],
      ["Approved", "APPROVED", 3],
      ["Rejected", "REJECTED", 1],
      ["Paid", "PAID", 1],
      ["Paid by company", "PAID_BY_COMPANY", 1],
    ];
    for (const [label, status, count] of cases) {
      await press(label);
      await waitFor(() => expect(server.calls.filter((c) => c.method === "GET" && c.query.status === status)).not.toHaveLength(0));
      await waitFor(() => expect(cards()).toHaveLength(count));
    }
    await press("All");
    await waitFor(() => expect(cards()).toHaveLength(8));
  });

  it("never shows another person's claim", async () => {
    await openExpenseScreen({ expenses: { claims: [...claims(), claimDetail({ id: "theirs", claimantId: "u-9", amount: "7777.00", status: "SUBMITTED" })] } });
    await screen.findByLabelText(/Marketing, ₹100/);
    expect(cards()).toHaveLength(8);
    expect(screen.queryByLabelText(/7,777/)).toBeNull();
  });

  it("pages 50 at a time with Load more", async () => {
    const many = Array.from({ length: 60 }, (_, i) =>
      claimDetail({ id: `c-${i}`, status: "DRAFT", amount: `${i + 1}.00`, expenseDate: `2026-09-${String((i % 28) + 1).padStart(2, "0")}` }),
    );
    await openExpenseScreen({ expenses: { claims: many } });
    await waitFor(() => expect(cards().length).toBeGreaterThan(0));
    expect(cards()).toHaveLength(50);
    await press("Load more");
    await waitFor(() => expect(cards()).toHaveLength(60));
    expect(screen.queryByText("Load more")).toBeNull();
  });

  it("says there are no expenses yet and points to Add expense", async () => {
    await openExpenseScreen();
    expect(await screen.findByText("No expenses yet. Choose Add expense to raise one.")).toBeTruthy();
  });

  it("shows a load error with Try again", async () => {
    const { expenses } = await openExpenseScreen({ expenses: { claims: claims(), refusals: { list: { status: 500, body: { message: "Boom" } } } } });
    expect(await screen.findByText("Something went wrong")).toBeTruthy();
    delete expenses.refusals.list;
    await fireEvent.press(await screen.findByLabelText("Retry"));
    expect(await screen.findByLabelText(/Marketing, ₹100/)).toBeTruthy();
  });

  it("filters by month through the server and goes back to all months", async () => {
    const { server } = await openExpenseScreen({ expenses: { claims: claims() } });
    await screen.findByLabelText(/Marketing, ₹100/);
    await press("Filter by month, off");
    await press("Previous month");
    await waitFor(() => expect(server.calls.some((c) => c.query.month === "2026-09")).toBe(true));
    await waitFor(() => expect(cards()).toHaveLength(3));
    await press("Show all months");
    await waitFor(() => expect(cards()).toHaveLength(8));
  });

  it("shows the Add expense button only when the menu grants Create", async () => {
    await openExpenseScreen({ model: expenseModel({ grants: { create: false } }) });
    await screen.findByText("No expenses yet.");
    expect(screen.queryByLabelText("Add expense")).toBeNull();
  });
});
