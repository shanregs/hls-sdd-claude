import { render, screen, waitFor } from "@testing-library/react-native";
import AsyncStorage from "@react-native-async-storage/async-storage";
import * as SecureStore from "expo-secure-store";
import App from "../../App";
import { getAccessToken } from "../../src/security/memoryToken";
import { authResult, newServer, type FakeServer } from "../support/fakeServer";
import { logOutViaMenu, openDevices } from "../support/navigation";

let server: FakeServer;

async function signedInApp() {
  server = newServer();
  await SecureStore.setItemAsync("hls.renewalCredential", "stored-credential");
  server.on("POST /api/v1/auth/renew", { status: 200, body: authResult() });
  server.on("POST /api/v1/auth/logout", { status: 204 });
  await render(<App />);
  await screen.findByText("Welcome, Tara");
}

describe("logout", () => {
  it("ends the session on the server, wipes the device, and shows Sign In", async () => {
    await signedInApp();
    expect(getAccessToken()).toBe("access-1");

    await logOutViaMenu();

    await screen.findByText("Sign in to HLS");
    expect(await SecureStore.getItemAsync("hls.renewalCredential")).toBeNull();
    expect(getAccessToken()).toBeNull();
    // The server call carried the token even though the device had already been wiped.
    const call = server.callsTo("POST /api/v1/auth/logout")[0];
    expect(call.headers.Authorization).toBe("Bearer access-1");
    // No user data left behind in ordinary storage either.
    expect(AsyncStorage.getAllKeys).toBeDefined();
    const keys = await AsyncStorage.getAllKeys();
    expect(keys.filter((k) => !k.startsWith("hls.themePreference"))).toEqual([]);
  });

  it("shows no signed-in screen, name or data after logging out", async () => {
    await signedInApp();

    await logOutViaMenu();

    await screen.findByText("Sign in to HLS");
    expect(screen.queryByText(/Welcome/)).toBeNull();
    expect(screen.queryByText("Tara")).toBeNull();
  });

  it("still clears everything on the device when the server cannot be reached", async () => {
    await signedInApp();
    server.offline = true;

    await logOutViaMenu();

    await screen.findByText("Sign in to HLS");
    expect(await SecureStore.getItemAsync("hls.renewalCredential")).toBeNull();
    expect(getAccessToken()).toBeNull();
  });

  it("does not prefill the previous user's identity on the next sign-in", async () => {
    await signedInApp();
    await logOutViaMenu();
    await screen.findByText("Sign in to HLS");

    expect(screen.getByLabelText("Phone number or username").props.value ?? "").toBe("");
  });
});

describe("a session ended elsewhere", () => {
  it("returns to Sign In with an explanation and wipes the device when the next request fails and cannot be renewed", async () => {
    await signedInApp();
    // The access token is rejected (ended elsewhere, deactivated, or revoked) and so is renewal.
    server.on("GET /api/v1/me/sessions", { status: 401, body: { message: "expired" } });
    server.on("POST /api/v1/auth/renew", { status: 401, body: { message: "Please sign in again." } });

    await openDevices();

    await screen.findByText("Sign in to HLS");
    await screen.findByText("Your session ended. Please sign in again.");
    expect(await SecureStore.getItemAsync("hls.renewalCredential")).toBeNull();
    expect(getAccessToken()).toBeNull();
  });

  it("keeps the user signed in and renews once when only the access token expired", async () => {
    await signedInApp();
    let first = true;
    server.on("GET /api/v1/me/sessions", () => {
      if (first) {
        first = false;
        return { status: 401, body: { message: "expired" } };
      }
      return { status: 200, body: [] };
    });
    server.on("POST /api/v1/auth/renew", {
      status: 200,
      body: authResult({ accessToken: "access-2", renewalCredential: "renewal-2" }),
    });

    await openDevices();

    await screen.findByText("These are the places you are signed in to HLS.");
    await waitFor(() => expect(getAccessToken()).toBe("access-2"));
    expect(await SecureStore.getItemAsync("hls.renewalCredential")).toBe("renewal-2");
    expect(screen.queryByText("Sign in to HLS")).toBeNull();
  });
});
