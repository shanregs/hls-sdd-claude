import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import * as SecureStore from "expo-secure-store";
import App from "../../App";
import { authResult, newServer, type FakeServer } from "../support/fakeServer";

let server: FakeServer;

async function storeCredential(value = "stored-credential") {
  await SecureStore.setItemAsync("hls.renewalCredential", value);
}

describe("reopening the app", () => {
  it("renews silently from the stored credential and goes straight to the home", async () => {
    server = newServer();
    await storeCredential("stored-credential");
    server.on("POST /api/v1/auth/renew", { status: 200, body: authResult({ renewalCredential: "rotated-credential" }) });

    await render(<App />);

    await screen.findByText("Welcome, Tara");
    expect(screen.queryByText("Sign in to HLS")).toBeNull();
    expect(server.callsTo("POST /api/v1/auth/renew")[0].body).toEqual({ renewalCredential: "stored-credential" });
    // The credential is rotated in secure storage.
    expect(await SecureStore.getItemAsync("hls.renewalCredential")).toBe("rotated-credential");
  });

  it("shows Sign In when nothing is stored, without calling renew", async () => {
    server = newServer();

    await render(<App />);

    await screen.findByText("Sign in to HLS");
    expect(server.callsTo("POST /api/v1/auth/renew")).toHaveLength(0);
  });

  it("clears the stored credential and shows Sign In with an explanation when renewal is refused", async () => {
    server = newServer();
    await storeCredential();
    server.on("POST /api/v1/auth/renew", { status: 401, body: { message: "Please sign in again." } });

    await render(<App />);

    await screen.findByText("Sign in to HLS");
    await screen.findByText("Your session ended. Please sign in again.");
    expect(await SecureStore.getItemAsync("hls.renewalCredential")).toBeNull();
  });

  it("returns to Sign In and wipes the device when a reused credential is detected (401)", async () => {
    server = newServer();
    await storeCredential("old-credential");
    server.on("POST /api/v1/auth/renew", { status: 401, body: { message: "Please sign in again." } });

    await render(<App />);

    await screen.findByText("Sign in to HLS");
    expect(await SecureStore.getItemAsync("hls.renewalCredential")).toBeNull();
    expect(screen.queryByText(/Welcome/)).toBeNull();
  });
});

describe("version gate", () => {
  it("shows the update screen and blocks sign-in when the app is older than the server's minimum", async () => {
    server = newServer("9.0.0");

    await render(<App />);

    await screen.findByText("Please update the HLS app");
    expect(screen.getByText(/Version 9\.0\.0 or later is required/)).toBeTruthy();
    expect(screen.queryByText("Sign in to HLS")).toBeNull();
    expect(server.callsTo("POST /api/v1/auth/login")).toHaveLength(0);
  });

  it("shows the update screen when the server answers 426 on renewal", async () => {
    server = newServer();
    await storeCredential();
    server.on("POST /api/v1/auth/renew", {
      status: 426,
      body: { code: "APP_UPDATE_REQUIRED", minimumVersion: "1.5.0" },
    });

    await render(<App />);

    await screen.findByText("Please update the HLS app");
  });
});

describe("no connection", () => {
  it("shows a no-connection screen for a signed-in user, then recovers on Retry", async () => {
    server = newServer();
    await storeCredential();
    server.on("POST /api/v1/auth/renew", { status: 200, body: authResult() });
    server.offline = true;

    await render(<App />);

    await screen.findByText("No connection");
    expect(screen.queryByText(/Welcome/)).toBeNull();
    // The credential is kept: being offline is not a sign-out.
    expect(await SecureStore.getItemAsync("hls.renewalCredential")).toBe("stored-credential");

    server.offline = false;
    await fireEvent.press(screen.getByLabelText("Retry"));

    await screen.findByText("Welcome, Tara");
    await waitFor(() => expect(screen.queryByText("No connection")).toBeNull());
  });
});
