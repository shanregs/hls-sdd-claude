import { act, fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import * as SecureStore from "expo-secure-store";
import { AppState, type AppStateStatus } from "react-native";
import App from "../../App";
import { ACCESS_MODEL_REFRESH_AFTER_BACKGROUND_MS } from "../../src/config/constants";
import { MANAGER_MODEL, TEACHER_MODEL } from "../support/fixtures";
import { authResult, newServer, type FakeServer } from "../support/fakeServer";
import { openMenu } from "../support/navigation";

let server: FakeServer;
let handler: ((state: AppStateStatus) => void) | undefined;
let now = 1_000_000;

beforeEach(async () => {
  handler = undefined;
  now = 1_000_000;
  jest.spyOn(Date, "now").mockImplementation(() => now);
  jest.spyOn(AppState, "addEventListener").mockImplementation(((
    _type: string,
    listener: (s: AppStateStatus) => void,
  ) => {
    handler = listener;
    return { remove: () => undefined };
  }) as never);
  server = newServer();
  server.on("POST /api/v1/auth/renew", { status: 200, body: authResult() });
  await SecureStore.setItemAsync("hls.renewalCredential", "stored");
});

afterEach(() => jest.restoreAllMocks());

async function startApp() {
  await render(<App />);
  await screen.findByText("Welcome, Tara");
}

async function background(forMs: number) {
  await act(async () => handler?.("background"));
  now += forMs;
  await act(async () => handler?.("active"));
}

describe("access model refresh (spec FR-012, SC-005)", () => {
  it("is fetched when the app starts with a restored session", async () => {
    await startApp();

    expect(server.callsTo("GET /api/v1/me/access-model")).toHaveLength(1);
  });

  it("is fetched right after a fresh sign-in", async () => {
    await SecureStore.deleteItemAsync("hls.renewalCredential");
    server.on("POST /api/v1/auth/login", { status: 200, body: authResult() });
    await render(<App />);
    await screen.findByText("Sign in to HLS");
    await fireEvent.changeText(screen.getByLabelText("Phone number or username"), "9876543210");
    await fireEvent.changeText(screen.getByLabelText("Password"), "x");
    await fireEvent.press(screen.getByLabelText("Sign in"));
    await screen.findByText("Welcome, Tara");

    expect(server.callsTo("GET /api/v1/me/access-model")).toHaveLength(1);
  });

  it("is fetched again after 5 minutes in the background, and the menu follows without a restart", async () => {
    await startApp();
    server.on("GET /api/v1/me/access-model", { status: 200, body: MANAGER_MODEL });

    await background(ACCESS_MODEL_REFRESH_AFTER_BACKGROUND_MS);

    await waitFor(() => expect(server.callsTo("GET /api/v1/me/access-model")).toHaveLength(2));
    await waitFor(() => expect(screen.getByLabelText("Roles: MANAGER")).toBeTruthy());
    await openMenu();
    expect(await screen.findByLabelText("Profile")).toBeTruthy();
    expect(screen.queryByLabelText("My Profile")).toBeNull();
  });

  it("is not fetched again after a short time in the background", async () => {
    await startApp();

    await background(ACCESS_MODEL_REFRESH_AFTER_BACKGROUND_MS - 1000);

    expect(server.callsTo("GET /api/v1/me/access-model")).toHaveLength(1);
  });

  it("keeps showing the last good menu when a refresh fails", async () => {
    await startApp();
    server.on("GET /api/v1/me/access-model", { status: 500, body: { message: "boom" } });

    await background(ACCESS_MODEL_REFRESH_AFTER_BACKGROUND_MS);

    await waitFor(() => expect(server.callsTo("GET /api/v1/me/access-model")).toHaveLength(2));
    expect(screen.getByText("Welcome, Tara")).toBeTruthy();
    expect(screen.getByLabelText(`Roles: ${TEACHER_MODEL.roles.join(", ")}`)).toBeTruthy();
  });
});
