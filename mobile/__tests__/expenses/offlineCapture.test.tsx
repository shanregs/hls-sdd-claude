import { screen } from "@testing-library/react-native";
import { SAVED_OFFLINE_TEXT } from "../../src/expenses/expenseMessages";
import { goOffline, goOnline, openExpenseScreen, restartApp } from "../support/expenseApp";
import { fillClaim, openAddForm, press, takeBill } from "../support/expenseActions";
import { linkOption } from "../support/expenseFixtures";

async function captureOffline(amount: string, category = "Marketing") {
  await openAddForm();
  await fillClaim({ amount, category });
  await takeBill();
  await press("Submit");
  await screen.findByText(SAVED_OFFLINE_TEXT);
}

/** Spec 036 T027: capture with no signal and send later (US2). */
describe("offline capture", () => {
  it("saves a claim as Waiting to send, keeps it across a restart, and sends it when the connection returns", async () => {
    const { server, expenses } = await openExpenseScreen();
    goOffline(server);
    await captureOffline("850");

    expect(await screen.findByText("Waiting to send (1)")).toBeTruthy();
    expect(screen.getByLabelText(/Waiting claim: Marketing, ₹850, .*Waiting to send/)).toBeTruthy();
    expect(screen.getByText("Waiting for a connection")).toBeTruthy();
    expect(expenses.claims).toHaveLength(0);

    // The app cannot sign in again with no connection at all (the access token lives in memory only), so the restart
    // happens with the connection back but the server refusing creates for now.
    server.offline = false;
    expenses.refusals.create = { status: 503, body: { message: "busy" } };
    await restartApp();
    expect(await screen.findByText("Waiting to send (1)")).toBeTruthy();
    expect(expenses.claims).toHaveLength(0);

    delete expenses.refusals.create;
    goOnline(server);
    expect(await screen.findByLabelText(/Marketing, ₹850, .*Submitted, 1 bill/)).toBeTruthy();
    expect(screen.queryByText(/Waiting to send/)).toBeNull();
    expect(expenses.claims).toHaveLength(1);
    expect(expenses.claims[0]).toMatchObject({ status: "SUBMITTED", billCount: 1 });
  });

  it("sends several waiting claims in the order they were captured, clearing each only after the server confirmed it", async () => {
    const { server, expenses } = await openExpenseScreen();
    goOffline(server);
    await captureOffline("100");
    await captureOffline("200");
    await captureOffline("300");
    expect(await screen.findByText("Waiting to send (3)")).toBeTruthy();

    goOnline(server);
    expect(await screen.findByLabelText(/Marketing, ₹300, .*Submitted/)).toBeTruthy();
    expect(expenses.claims.map((c) => c.amount)).toEqual(["100.00", "200.00", "300.00"]);
    expect(screen.queryByText(/Waiting to send/)).toBeNull();
  });

  it("keeps a claim waiting when the connection comes back but the server fails", async () => {
    const { server, expenses } = await openExpenseScreen();
    goOffline(server);
    await captureOffline("850");
    expenses.refusals.create = { status: 503, body: { message: "busy" } };
    goOnline(server);
    await screen.findByLabelText(/Waiting claim: Marketing, ₹850/);
    expect(screen.getByText("Waiting to send (1)")).toBeTruthy();
    expect(expenses.claims).toHaveLength(0);
  });

  it("saves a Training Stay with no cached batch but does not send it until the batch is chosen", async () => {
    const { server, expenses } = await openExpenseScreen({ expenses: { batches: [linkOption({ kind: "INDUCTION_BATCH", ref: "batch-1", label: "Batch Oct 2026" })] } });
    goOffline(server);
    await captureOffline("2500", "Training Stay");
    expect(await screen.findByText("Choose the batch when connected")).toBeTruthy();

    goOnline(server);
    await screen.findByText("Waiting to send (1)");
    await new Promise((resolve) => setTimeout(resolve, 500));
    expect(expenses.claims).toHaveLength(0);
    expect(screen.getByText("Choose the batch when connected")).toBeTruthy();
  });
});
