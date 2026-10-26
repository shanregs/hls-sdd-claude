import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import { signOutWarning } from "../../src/expenses/expenseMessages";
import { goOffline, goOnline, openExpenseScreen, signOutAndSignInAs, type OpenedExpenseApp } from "../support/expenseApp";
import { fillClaim, openAddForm, press, takeBill } from "../support/expenseActions";
import { ME } from "../support/expenseFixtures";
import { chooseMenuItem } from "../support/navigation";

const OTHER = { id: "u-2", displayName: "Manoj", roles: ["MANAGER"] };
const fsState = () => jest.requireMock("expo-file-system/legacy").__state;

async function captureOffline(opened: OpenedExpenseApp, note = "Petrol for Green Valley") {
  goOffline(opened.server);
  await openAddForm();
  await fillClaim({ amount: "850", category: "Marketing", note });
  await takeBill();
  await press("Submit");
  await screen.findByText("Waiting to send (1)");
}

/** Spec 036 T030: several people on one phone (US2, SC-006). */
describe("shared phone", () => {
  it("warns with the count before signing out from the menu, and Stay keeps the session", async () => {
    const opened = await openExpenseScreen();
    await captureOffline(opened);
    await chooseMenuItem("Log out");
    expect(await screen.findByText(signOutWarning(1))).toBeTruthy();
    await press("Stay");
    expect(screen.queryByText(signOutWarning(1))).toBeNull();
    expect(screen.getByText("Waiting to send (1)")).toBeTruthy();
  });

  it("warns before signing out from Profile too", async () => {
    const opened = await openExpenseScreen();
    await captureOffline(opened);
    await chooseMenuItem("Profile");
    await fireEvent.press(await screen.findByLabelText("Log out"));
    expect(await screen.findByText(signOutWarning(1))).toBeTruthy();
    await press("Sign out");
    expect(await screen.findByText("Sign in to HLS")).toBeTruthy();
  });

  it("signs out at once when nothing is waiting", async () => {
    await openExpenseScreen();
    await chooseMenuItem("Log out");
    expect(await screen.findByText("Sign in to HLS")).toBeTruthy();
  });

  it("sends nothing after sign-out, shows another user none of them, and gives them back to the same user", async () => {
    const opened = await openExpenseScreen();
    await captureOffline(opened);
    opened.server.offline = false;
    jest.requireMock("@react-native-community/netinfo").__state.isConnected = true;
    const before = opened.server.callsTo("POST /api/v1/my-expenses").length;

    await signOutAndSignInAs(opened, OTHER, undefined, { confirmSignOut: true });
    await chooseMenuItem("My Expenses");
    await screen.findByText(/Owed to you/);
    expect(screen.queryByText(/Waiting to send/)).toBeNull();
    expect(opened.server.callsTo("POST /api/v1/my-expenses")).toHaveLength(before);
    expect(opened.expenses.claims).toHaveLength(0);

    // The other user's list is theirs alone.
    expect(screen.queryByLabelText(/Marketing, ₹850/)).toBeNull();

    // The first user signs in again: the claim is back and is sent.
    await signOutAndSignInAs(opened, ME);
    await chooseMenuItem("My Expenses");
    expect(await screen.findByLabelText(/Marketing, ₹850, .*Submitted, 1 bill/)).toBeTruthy();
    await waitFor(() => expect(screen.queryByText(/Waiting to send/)).toBeNull());
    expect(opened.expenses.claims).toHaveLength(1);
    expect(opened.expenses.claims[0].claimantId).toBe(ME.id);
  });

  it("keeps the waiting claims when the session expires, and sends them after signing in again", async () => {
    const opened = await openExpenseScreen();
    await captureOffline(opened);
    opened.server.offline = false;
    // The server no longer accepts the session and will not renew it.
    opened.expenses.refusals.create = { status: 401, body: { message: "Please sign in again." } };
    opened.server.on("POST /api/v1/auth/renew", { status: 401, body: { message: "Expired." } });
    await press("Send now");
    expect(await screen.findByText("Sign in to HLS")).toBeTruthy();
    expect(opened.expenses.claims).toHaveLength(0);

    delete opened.expenses.refusals.create;
    await signOutSignIn(opened);
    await chooseMenuItem("My Expenses");
    expect(await screen.findByLabelText(/Marketing, ₹850, .*Submitted, 1 bill/)).toBeTruthy();
    expect(opened.expenses.claims).toHaveLength(1);
  });

  it("never leaves the claim or its photo in plain form on the phone", async () => {
    const opened = await openExpenseScreen();
    await captureOffline(opened, "Petrol for Green Valley");
    for (const [uri, content] of fsState().files as Map<string, string>) {
      if (uri.includes("/cache/") && !uri.includes("outbox-")) continue; // the camera's own picture, not ours
      const plain = Buffer.from(content, "base64").toString("latin1");
      expect(plain).not.toContain("Green Valley");
      expect(plain).not.toContain("850");
      expect(plain).not.toContain("Marketing");
      expect(plain).not.toContain("JFIF");
    }
    // No decrypted copy is left in the cache after the photo was kept.
    expect([...(fsState().files as Map<string, string>).keys()].filter((k) => k.includes("outbox-"))).toEqual([]);
    goOnline(opened.server);
    await waitFor(() => expect(screen.queryByText(/Waiting to send/)).toBeNull());
    expect([...(fsState().files as Map<string, string>).keys()].filter((k) => k.includes("/outbox/") && !k.endsWith("list-cache.enc"))).toEqual([]);
  });
});

/** After the session ended the sign-in screen is showing: sign in as the same person. */
async function signOutSignIn(opened: OpenedExpenseApp) {
  opened.server.on("POST /api/v1/auth/renew", { status: 200, body: { accessToken: "access-u-1", expiresInSeconds: 900, renewalCredential: "renewal-2", user: ME } });
  opened.server.on("POST /api/v1/auth/login", { status: 200, body: { accessToken: "access-u-1", expiresInSeconds: 900, renewalCredential: "renewal-2", user: ME } });
  await fireEvent.changeText(screen.getByLabelText("Phone number or username"), "tara");
  await fireEvent.changeText(screen.getByLabelText("Password"), "password-1");
  await fireEvent.press(screen.getByLabelText("Sign in"));
  await screen.findByText(`Welcome, ${ME.displayName}`);
}
