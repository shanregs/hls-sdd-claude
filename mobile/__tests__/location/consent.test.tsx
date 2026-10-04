import { fireEvent, render, screen } from "@testing-library/react-native";
import * as Location from "expo-location";
import * as SecureStore from "expo-secure-store";
import App from "../../App";
import { LOCATION_EXPLANATION } from "../../src/location/LocationConsent";
import { authResult, newServer, type FakeServer } from "../support/fakeServer";
import { chooseMenuItem } from "../support/navigation";

/**
 * Spec 018 T069, T082 (FR-018, FR-019, FR-021): the plain-language explanation comes before the
 * system permission prompt, can be reopened from Profile, and a "no" never stops sign-in. Every call
 * carries the location or the reason.
 */
const state = jest.requireMock("expo-location").__state as Record<string, unknown>;
let server: FakeServer;

beforeEach(() => jest.clearAllMocks());

async function openSignIn() {
  server = newServer();
  server.on("POST /api/v1/auth/login", { status: 200, body: authResult() });
  await render(<App />);
  await screen.findByText("Sign in to HLS");
}

async function submitCredentials() {
  await fireEvent.changeText(screen.getByLabelText("Phone number or username"), "9876543210");
  await fireEvent.changeText(screen.getByLabelText("Password"), "correct-horse-5");
  await fireEvent.press(screen.getByLabelText("Sign in"));
}

/** Presses Sign in without waiting for it to finish: with the dialog open the call is still pending. */
async function startSubmit() {
  await fireEvent.changeText(screen.getByLabelText("Phone number or username"), "9876543210");
  await fireEvent.changeText(screen.getByLabelText("Password"), "correct-horse-5");
  void fireEvent.press(screen.getByLabelText("Sign in"));
}

describe("the explanation before the first permission prompt", () => {
  it("is shown first, and the system prompt only follows after Continue", async () => {
    state.permission = "undetermined";
    await openSignIn();

    await startSubmit();
    await screen.findByText("Location and privacy");
    expect(screen.getByText(LOCATION_EXPLANATION)).toBeTruthy();
    expect(Location.requestForegroundPermissionsAsync).not.toHaveBeenCalled();
    expect(server.callsTo("POST /api/v1/auth/login")).toHaveLength(0);

    void fireEvent.press(screen.getByLabelText("Continue"));

    await screen.findByText(/Welcome, Tara/);
    expect(Location.requestForegroundPermissionsAsync).toHaveBeenCalledTimes(1);
    expect(server.callsTo("POST /api/v1/auth/login")[0].headers["X-HLS-Location"]).toMatch(/^lat=12\.971599;/);
  });

  it("lets sign-in go ahead without a location when the user chooses Not now, and does not ask again", async () => {
    state.permission = "undetermined";
    await openSignIn();

    await startSubmit();
    await screen.findByText("Location and privacy");
    void fireEvent.press(screen.getByLabelText("Not now"));

    await screen.findByText(/Welcome, Tara/);
    expect(Location.requestForegroundPermissionsAsync).not.toHaveBeenCalled();
    const login = server.callsTo("POST /api/v1/auth/login")[0];
    expect(login.headers["X-HLS-Location"]).toBeUndefined();
    expect(login.headers["X-HLS-Location-Status"]).toBe("PERMISSION_DENIED");
  });

  it("is not shown when the permission was already decided", async () => {
    state.permission = "denied";
    await openSignIn();

    await submitCredentials();

    await screen.findByText(/Welcome, Tara/);
    expect(screen.queryByText(LOCATION_EXPLANATION)).toBeNull();
    expect(server.callsTo("POST /api/v1/auth/login")[0].headers["X-HLS-Location-Status"]).toBe("PERMISSION_DENIED");
  });

  it("still signs in when the user denies the system prompt", async () => {
    state.permission = "undetermined";
    state.onRequestPermission = "denied";
    await openSignIn();

    await startSubmit();
    await screen.findByText("Location and privacy");
    void fireEvent.press(screen.getByLabelText("Continue"));

    await screen.findByText(/Welcome, Tara/);
    expect(server.callsTo("POST /api/v1/auth/login")[0].headers["X-HLS-Location-Status"]).toBe("PERMISSION_DENIED");
  });
});

describe("every call carries the location or the reason", () => {
  it("adds it to sign-in, the menu, and logout, but not to the public app-config call", async () => {
    await openSignIn();
    server.on("POST /api/v1/auth/logout", { status: 204 });

    await submitCredentials();
    await screen.findByText(/Welcome, Tara/);
    await chooseMenuItem("Log out");
    await screen.findByText("Sign in to HLS");

    for (const route of ["POST /api/v1/auth/login", "GET /api/v1/me/access-model", "POST /api/v1/auth/logout"]) {
      const headers = server.callsTo(route)[0].headers;
      expect(headers["X-HLS-Location"] ?? headers["X-HLS-Location-Status"]).toBeDefined();
    }
    const config = server.callsTo("GET /api/v1/mobile/app-config")[0].headers;
    expect(config["X-HLS-Location"]).toBeUndefined();
    expect(config["X-HLS-Location-Status"]).toBeUndefined();
  });

  it("goes ahead without delay beyond the server's wait when no position is found", async () => {
    server = newServer();
    server.on("GET /api/v1/mobile/app-config", {
      status: 200,
      body: { minimumVersion: "0.0.0", locationWaitSeconds: 0.05, locationReuseSeconds: 0.05 },
    });
    server.on("POST /api/v1/auth/login", { status: 200, body: authResult() });
    state.neverResolves = true;
    await render(<App />);
    await screen.findByText("Sign in to HLS");

    await submitCredentials();

    await screen.findByText(/Welcome, Tara/);
    expect(server.callsTo("POST /api/v1/auth/login")[0].headers["X-HLS-Location-Status"]).toBe("NO_FIX");
  });
});

describe("Profile → Location and privacy", () => {
  async function openPrivacy() {
    server = newServer();
    server.on("POST /api/v1/auth/renew", { status: 200, body: authResult() });
    await SecureStore.setItemAsync("hls.renewalCredential", "stored");
    await render(<App />);
    await screen.findByText(/Welcome, Tara/);
    await chooseMenuItem("My Profile");
    await screen.findByText("Appearance");
    await fireEvent.press(screen.getByLabelText("Location and privacy"));
    await screen.findByText(LOCATION_EXPLANATION);
  }

  it("shows the explanation again and that location is allowed", async () => {
    await openPrivacy();
    expect(screen.getByText("Allowed while you use the app.")).toBeTruthy();
  });

  it("offers to allow location when it was never allowed", async () => {
    state.permission = "undetermined";
    await openPrivacy();

    await screen.findByText("Not allowed yet.");
    await fireEvent.press(screen.getByLabelText("Allow location"));

    await screen.findByText("Allowed while you use the app.");
  });

  it("points to the phone settings when the permission was blocked", async () => {
    state.permission = "denied";
    state.canAskAgain = false;
    await openPrivacy();

    await screen.findByText(/You can change this in your phone/);
    expect(screen.getByLabelText("Open phone settings")).toBeTruthy();
  });

  it("says so when location is switched off on the phone", async () => {
    state.servicesEnabled = false;
    await openPrivacy();

    await screen.findByText("Location is switched off on your phone.");
  });
});
