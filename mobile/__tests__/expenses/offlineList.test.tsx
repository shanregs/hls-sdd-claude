import { act, fireEvent, screen, waitFor } from "@testing-library/react-native";
import { MORE_DETAILS_TEXT } from "../../src/expenses/expenseMessages";
import { readListCache } from "../../src/outbox/listCache";
import { goOffline, openExpenseScreen, restartApp } from "../support/expenseApp";
import { press } from "../support/expenseActions";
import { claimDetail, ME } from "../support/expenseFixtures";

const claims = () => [
  claimDetail({ id: "c-1", status: "SUBMITTED", amount: "200.00", category: "OFFICE_SUPPLIES", expenseDate: "2026-10-22", note: "Paper" }),
  claimDetail({ id: "c-2", status: "REJECTED", amount: "300.00", category: "MARKETING", expenseDate: "2026-10-12" }),
  claimDetail({ id: "c-3", status: "APPROVED", amount: "1000.00", category: "TRAVEL_FUEL", expenseDate: "2026-10-10" }),
];

async function pullToRefresh() {
  const scroll = screen.getByTestId("screen-scroll");
  await act(async () => {
    scroll.props.refreshControl.props.onRefresh();
  });
}

/** Spec 036 T037: reading the last list with no connection (US3, FR-018). */
describe("offline list", () => {
  it("saves the unfiltered first page for the signed-in user after a successful load", async () => {
    await openExpenseScreen({ expenses: { claims: claims() } });
    await screen.findByLabelText(/Office Supplies, ₹200/);
    await waitFor(async () => expect(await readListCache(ME.id)).not.toBeNull());
    const cache = await readListCache(ME.id);
    expect(cache?.rows.map((r) => r.id).sort()).toEqual(["c-1", "c-2", "c-3"]);
    expect(cache?.owed).toBe("1000.00");
    expect(await readListCache("u-2")).toBeNull();
  });

  it("shows the saved list marked out of date when the load fails, and filters it here", async () => {
    const { server } = await openExpenseScreen({ expenses: { claims: claims() } });
    await screen.findByLabelText(/Office Supplies, ₹200/);
    await waitFor(async () => expect(await readListCache(ME.id)).not.toBeNull());

    goOffline(server);
    await pullToRefresh();
    expect(await screen.findByText(/May be out of date, loaded /)).toBeTruthy();
    expect(screen.getByLabelText(/Office Supplies, ₹200/)).toBeTruthy();
    expect(screen.getByText("Owed to you: ₹1,000")).toBeTruthy();

    await press("Rejected");
    await waitFor(() => expect(screen.queryByLabelText(/Office Supplies, ₹200/)).toBeNull());
    expect(screen.getByLabelText(/Marketing, ₹300/)).toBeTruthy();
    expect(screen.getByText(/May be out of date/)).toBeTruthy();
  });

  it("shows the waiting claims above the saved list", async () => {
    const { server } = await openExpenseScreen({ expenses: { claims: claims() } });
    await screen.findByLabelText(/Office Supplies, ₹200/);
    await waitFor(async () => expect(await readListCache(ME.id)).not.toBeNull());
    goOffline(server);
    await fireEvent.press(await screen.findByLabelText("Add expense"));
    await fireEvent.changeText(await screen.findByLabelText("Amount"), "99");
    await fireEvent.press(screen.getByLabelText("Marketing"));
    await fireEvent.changeText(screen.getByLabelText("Reason there is no bill"), "Cash");
    await press("Submit");
    expect(await screen.findByText("Waiting to send (1)")).toBeTruthy();
    expect(screen.getByLabelText(/Office Supplies, ₹200/)).toBeTruthy();
    expect(screen.getByText(/May be out of date/)).toBeTruthy();
  });

  it("opens a claim offline with its row and says more details need a connection", async () => {
    const { server } = await openExpenseScreen({ expenses: { claims: claims() } });
    await screen.findByLabelText(/Office Supplies, ₹200/);
    await waitFor(async () => expect(await readListCache(ME.id)).not.toBeNull());
    goOffline(server);
    await pullToRefresh();
    await screen.findByText(/May be out of date/);
    await press(String(screen.getByLabelText(/Office Supplies, ₹200/).props.accessibilityLabel));
    expect(await screen.findByText(MORE_DETAILS_TEXT)).toBeTruthy();
    expect(screen.getByText("Office Supplies, ₹200")).toBeTruthy();
    expect(screen.getByText("Date: 22/10/2026")).toBeTruthy();
    expect(screen.getByText("Note: Paper")).toBeTruthy();
    expect(screen.queryByLabelText("Withdraw claim")).toBeNull();
  });

  it("shows an error, not an old list, when the list was never loaded", async () => {
    const { server } = await openExpenseScreen({ openScreen: false, expenses: { claims: claims() } });
    server.offline = false;
    jest.requireMock("expo-file-system/legacy").__state.files.clear();
    await restartApp(ME, false);
    server.on("GET /api/v1/my-expenses", { status: 500, body: { message: "Boom" } });
    await restartApp(ME, true);
    expect(await screen.findByText("Something went wrong")).toBeTruthy();
    expect(screen.queryByText(/May be out of date/)).toBeNull();
  });
});
