import { fireEvent, render, screen } from "@testing-library/react-native";
import { preventScreenCaptureAsync } from "expo-screen-capture";
import App from "../../App";
import { authResult, newServer } from "../support/fakeServer";
import { logOutViaMenu } from "../support/navigation";

/**
 * Spec 018 T046 (FR-004): no token or renewal credential is ever written to the console, and the
 * app window is marked secure (blank recent-apps thumbnail and screenshots).
 */
describe("secrets and the secure window", () => {
  it("never logs the access token or the renewal credential through a full sign-in and logout", async () => {
    const spies = (["log", "info", "warn", "error", "debug"] as const).map((level) =>
      jest.spyOn(console, level).mockImplementation(() => undefined),
    );
    const server = newServer();
    server.on("POST /api/v1/auth/login", {
      status: 200,
      body: authResult({ accessToken: "SECRET-ACCESS-TOKEN-123", renewalCredential: "SECRET-RENEWAL-CREDENTIAL-456" }),
    });
    server.on("POST /api/v1/auth/logout", { status: 204 });

    await render(<App />);
    await screen.findByText("Sign in to HLS");
    await fireEvent.changeText(screen.getByLabelText("Phone number or username"), "9876543210");
    await fireEvent.changeText(screen.getByLabelText("Password"), "correct-horse-5");
    await fireEvent.press(screen.getByLabelText("Sign in"));
    await screen.findByText("Welcome, Tara");
    await logOutViaMenu();
    await screen.findByText("Sign in to HLS");

    const everything = spies.flatMap((spy) => spy.mock.calls.flat().map(String)).join("\n");
    expect(everything).not.toContain("SECRET-ACCESS-TOKEN-123");
    expect(everything).not.toContain("SECRET-RENEWAL-CREDENTIAL-456");
    expect(everything).not.toContain("correct-horse-5");
    spies.forEach((spy) => spy.mockRestore());
  });

  it("marks the app window secure on start", async () => {
    newServer();

    await render(<App />);
    await screen.findByText("Sign in to HLS");

    expect(preventScreenCaptureAsync).toHaveBeenCalled();
  });
});
