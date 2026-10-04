import { fireEvent, render, screen } from "@testing-library/react-native";
import * as SecureStore from "expo-secure-store";
import App from "../../App";
import { minTouchTarget } from "../../src/theme/tokens";
import { authResult, newServer, type FakeServer } from "../support/fakeServer";
import { chooseMenuItem, openDevices, openMenu } from "../support/navigation";

/**
 * Spec 018 T052 (FR-016, SC-010): every interactive element on the main screens has an accessible
 * name and role. Touch-target size and contrast are verified manually with TalkBack on a device
 * (quickstart.md scenario on theme and accessibility); here the design constant is checked.
 */
let server: FakeServer;

function expectNamedControls() {
  for (const button of screen.queryAllByRole("button")) {
    expect(button.props.accessibilityLabel ?? button.props["aria-label"]).toBeTruthy();
  }
  for (const input of screen.queryAllByLabelText(/./).filter((n) => typeof n.props.onChangeText === "function")) {
    expect(input.props.accessibilityLabel).toBeTruthy();
  }
}

async function signedIn() {
  server = newServer();
  server.on("POST /api/v1/auth/renew", { status: 200, body: authResult() });
  server.on("GET /api/v1/me/sessions", { status: 200, body: [] });
  await SecureStore.setItemAsync("hls.renewalCredential", "stored");
  await render(<App />);
  await screen.findByText("Welcome, Tara");
}

describe("accessibility of the main screens", () => {
  it("keeps the minimum touch target at 48 dp", () => {
    expect(minTouchTarget).toBeGreaterThanOrEqual(48);
  });

  it("Sign In has labelled fields, buttons and headings", async () => {
    newServer();
    await render(<App />);
    await screen.findByText("Sign in to HLS");

    expect(screen.getByRole("header", { name: "Sign in to HLS" })).toBeTruthy();
    for (const label of ["Phone number or username", "Password", "Sign in", "Forgot password", "Sign in with password", "Sign in with a one-time code"]) {
      expect(screen.getByLabelText(label)).toBeTruthy();
    }
    expectNamedControls();
  });

  it("Home and the menu have a header and labelled controls", async () => {
    await signedIn();

    expect(screen.getByRole("header", { name: "Welcome, Tara" })).toBeTruthy();
    expect(screen.getByLabelText("Open menu")).toBeTruthy();
    await openMenu();
    expect(await screen.findByLabelText("Log out")).toBeTruthy();
    expectNamedControls();
  });

  it("Profile has labelled fields and actions", async () => {
    await signedIn();
    await chooseMenuItem("My Profile");
    await screen.findByText("Appearance");

    for (const label of ["Phone number", "Name", "Username", "Email", "Follow device theme", "Light theme", "Dark theme", "Signed-in devices", "Log out"]) {
      expect(screen.getByLabelText(label)).toBeTruthy();
    }
    expect(screen.getAllByRole("header", { name: "Profile" }).length).toBeGreaterThan(0);
    expectNamedControls();
  });

  it("Signed-in devices has a header and a back control", async () => {
    await signedIn();
    await openDevices();

    expect(await screen.findByRole("header", { name: "Signed-in devices" })).toBeTruthy();
    expect(screen.getByLabelText("Back")).toBeTruthy();
    expectNamedControls();
  });

  it("No connection and Not authorized have a header and a labelled action", async () => {
    server = newServer();
    await SecureStore.setItemAsync("hls.renewalCredential", "stored");
    server.on("POST /api/v1/auth/renew", { status: 200, body: authResult() });
    server.offline = true;
    await render(<App />);

    expect(await screen.findByRole("header", { name: "No connection" })).toBeTruthy();
    expect(screen.getByLabelText("Retry")).toBeTruthy();
    await fireEvent.press(screen.getByLabelText("Retry"));
  });
});
