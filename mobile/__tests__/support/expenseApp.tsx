import { fireEvent, render, screen } from "@testing-library/react-native";
import * as SecureStore from "expo-secure-store";
import App from "../../App";
import type { AccessModel } from "../../src/api/accessModelApi";
import type { SignedInUser } from "../../src/api/authApi";
import { resetServerClock } from "../../src/attendance/serverClock";
import { expenseModel, ME } from "./expenseFixtures";
import { installExpenses, type ExpensesFake } from "./expensesServer";
import { newServer, type FakeServer } from "./fakeServer";
import { PROFILE } from "./fixtures";
import { chooseMenuItem, logOutViaMenu } from "./navigation";

export interface ExpenseSetup {
  /** The access model; a user with OPERATIONS → My Expenses unless given. */
  model?: AccessModel;
  /** Who is signed in (id kept so the outbox owner is known). */
  user?: SignedInUser;
  /** The server's date ("YYYY-MM-DD"); the server clock is 09:30 India time that day. */
  today?: string;
  expenses?: Partial<ExpensesFake>;
  /** Runs after the expense routes are installed, to replace or add a route. */
  prepare?: (server: FakeServer, expenses: ExpensesFake) => void;
  /** Open My Expenses from the drawer (default true); false stays on Home. */
  openScreen?: boolean;
}

export interface OpenedExpenseApp {
  server: FakeServer;
  expenses: ExpensesFake;
  serverTime: Date;
}

function authFor(user: SignedInUser, credential = "renewal-1") {
  return { accessToken: `access-${user.id}`, expiresInSeconds: 900, renewalCredential: credential, user };
}

function serve(server: FakeServer, user: SignedInUser, model: AccessModel, serverTime: Date) {
  const headers = { Date: serverTime.toUTCString() };
  server.on("GET /api/v1/me/access-model", { status: 200, body: model, headers });
  server.on("GET /api/v1/me/profile", { status: 200, body: { ...PROFILE, id: user.id, displayName: user.displayName } });
  server.on("POST /api/v1/auth/renew", { status: 200, body: authFor(user) });
  server.on("POST /api/v1/auth/login", { status: 200, body: authFor(user) });
  server.on("POST /api/v1/auth/logout", { status: 204 });
}

/**
 * Signs in with a stored session as a chosen user, installs the My Expenses routes with the setup's overrides, and opens
 * My Expenses from the drawer. The server's date is 2026-10-23 unless set.
 */
export async function openExpenseScreen(setup: ExpenseSetup = {}): Promise<OpenedExpenseApp> {
  resetServerClock();
  const today = setup.today ?? "2026-10-23";
  const serverTime = new Date(`${today}T04:00:00Z`);
  jest.spyOn(Date, "now").mockReturnValue(serverTime.getTime());

  const user = setup.user ?? ME;
  const server = newServer();
  serve(server, user, setup.model ?? expenseModel(), serverTime);
  await SecureStore.setItemAsync("hls.renewalCredential", "stored");
  const expenses = installExpenses(server, { today, userId: user.id, ...setup.expenses });
  setup.prepare?.(server, expenses);

  await render(<App />);
  await screen.findByText(`Welcome, ${user.displayName}`);
  if (setup.openScreen !== false) await chooseMenuItem("My Expenses");
  return { server, expenses, serverTime };
}

/** Logs out through the drawer, then signs in as another user (a shared phone). */
export async function signOutAndSignInAs(
  opened: OpenedExpenseApp,
  user: SignedInUser,
  model: AccessModel = expenseModel(),
  options: { confirmSignOut?: boolean } = {},
): Promise<void> {
  await logOutViaMenu();
  if (options.confirmSignOut) await fireEvent.press(await screen.findByLabelText("Sign out"));
  await screen.findByText("Sign in to HLS");
  serve(opened.server, user, model, opened.serverTime);
  opened.expenses.userId = user.id;
  await fireEvent.changeText(screen.getByLabelText("Phone number or username"), "user");
  await fireEvent.changeText(screen.getByLabelText("Password"), "password-1");
  await fireEvent.press(screen.getByLabelText("Sign in"));
  await screen.findByText(`Welcome, ${user.displayName}`);
}

/** The phone loses its connection: requests fail and NetInfo says so. */
export function goOffline(server: FakeServer) {
  server.offline = true;
  jest.requireMock("@react-native-community/netinfo").__emit({ isConnected: false, isInternetReachable: false });
}

/** The connection returns: requests work and NetInfo reports it, which starts a run of the outbox. */
export function goOnline(server: FakeServer) {
  server.offline = false;
  jest.requireMock("@react-native-community/netinfo").__emit({ isConnected: true, isInternetReachable: true });
}

/** Closes the app and opens it again on the stored session (a kill and a restart), then opens My Expenses. */
export async function restartApp(user: SignedInUser = ME, openScreen = true) {
  await screen.unmount();
  await render(<App />);
  await screen.findByText(`Welcome, ${user.displayName}`);
  if (openScreen) await chooseMenuItem("My Expenses");
}
