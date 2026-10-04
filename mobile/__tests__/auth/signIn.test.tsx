import { act, fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import * as SecureStore from "expo-secure-store";
import App from "../../App";
import { authResult, newServer, type FakeServer } from "../support/fakeServer";

let server: FakeServer;

beforeEach(() => {
  server = newServer();
});

async function openApp() {
  await render(<App />);
  await screen.findByText("Sign in to HLS");
}

async function typeInto(label: string, value: string) {
  await fireEvent.changeText(screen.getByLabelText(label), value);
}

describe("password sign-in", () => {
  it("signs in with phone or username and a password, then shows the home", async () => {
    server.on("POST /api/v1/auth/login", { status: 200, body: authResult({ renewalCredential: "renewal-abc" }) });
    await openApp();

    await typeInto("Phone number or username", "9876543210");
    await typeInto("Password", "correct-horse-5");
    await fireEvent.press(screen.getByLabelText("Sign in"));

    await screen.findByText("Welcome, Tara");
    expect(server.callsTo("POST /api/v1/auth/login")[0].body).toEqual({
      identifier: "9876543210",
      password: "correct-horse-5",
    });
    // The renewal credential is in secure storage; the access token is never persisted.
    expect(await SecureStore.getItemAsync("hls.renewalCredential")).toBe("renewal-abc");
    expect(SecureStore.setItemAsync).toHaveBeenCalledTimes(1);
  });

  it("never asks the user to choose a role", async () => {
    await openApp();
    expect(screen.queryByText(/role/i)).toBeNull();
  });

  it("shows the server's generic message for a wrong password without saying which part was wrong", async () => {
    server.on("POST /api/v1/auth/login", {
      status: 401,
      body: { message: "Your phone number/username or password is incorrect." },
    });
    await openApp();

    await typeInto("Phone number or username", "9876543210");
    await typeInto("Password", "wrong");
    await fireEvent.press(screen.getByLabelText("Sign in"));

    await screen.findByText("Your phone number/username or password is incorrect.");
    expect(screen.queryByText(/Welcome/)).toBeNull();
    expect(await SecureStore.getItemAsync("hls.renewalCredential")).toBeNull();
  });

  it("shows the lockout message with the time the account unlocks", async () => {
    server.on("POST /api/v1/auth/login", {
      status: 423,
      body: { message: "Account locked until 14:32.", unlockAt: "2026-10-04T09:02:00Z" },
    });
    await openApp();

    await typeInto("Phone number or username", "9876543210");
    await typeInto("Password", "wrong");
    await fireEvent.press(screen.getByLabelText("Sign in"));

    await screen.findByText("Account locked until 14:32.");
  });

  it("tells a web-only account to use the web application and keeps no session", async () => {
    server.on("POST /api/v1/auth/login", {
      status: 403,
      body: { code: "WEB_ONLY_ROLE", message: "Your account uses the HLS web application." },
    });
    await openApp();

    await typeInto("Phone number or username", "admin.user");
    await typeInto("Password", "correct-horse-5");
    await fireEvent.press(screen.getByLabelText("Sign in"));

    await screen.findByText("Your account uses the HLS web application.");
    expect(screen.queryByText(/Welcome/)).toBeNull();
    expect(await SecureStore.getItemAsync("hls.renewalCredential")).toBeNull();
  });

  it("shows a no-connection message when the server cannot be reached", async () => {
    await openApp();
    server.offline = true;

    await typeInto("Phone number or username", "9876543210");
    await typeInto("Password", "correct-horse-5");
    await fireEvent.press(screen.getByLabelText("Sign in"));

    await screen.findByText("No connection. Check your internet and try again.");
  });

  it("keeps the Sign in button disabled until both fields are filled", async () => {
    await openApp();
    expect(screen.getByLabelText("Sign in").props.accessibilityState?.disabled).toBe(true);
    await typeInto("Phone number or username", "9876543210");
    await typeInto("Password", "x");
    await waitFor(() => expect(screen.getByLabelText("Sign in").props.accessibilityState?.disabled).toBeFalsy());
  });
});

describe("one-time code sign-in", () => {
  async function openCodeTab() {
    await openApp();
    await fireEvent.press(screen.getByLabelText("Sign in with a one-time code"));
  }

  it("requests a code, then signs in with it", async () => {
    server
      .on("POST /api/v1/auth/otp/request", { status: 200, body: { message: "If this is registered, a code has been sent." } })
      .on("POST /api/v1/auth/otp/verify", { status: 200, body: authResult() });
    await openCodeTab();

    await typeInto("Phone number", "9876543210");
    await fireEvent.press(screen.getByLabelText("Send code"));
    await screen.findByText("If this number is registered, a code has been sent.");

    await typeInto("One-time code", "123456");
    await fireEvent.press(screen.getByLabelText("Verify code and sign in"));

    await screen.findByText("Welcome, Tara");
    expect(server.callsTo("POST /api/v1/auth/otp/request")[0].body).toEqual({
      destination: "9876543210",
      channel: "SMS",
      purpose: "SIGN_IN",
    });
    expect(server.callsTo("POST /api/v1/auth/otp/verify")[0].body).toEqual({
      destination: "9876543210",
      code: "123456",
      purpose: "SIGN_IN",
    });
  });

  it("shows the server's message and a countdown when too many codes were requested", async () => {
    server.on("POST /api/v1/auth/otp/request", {
      status: 429,
      body: { message: "Please wait 30 seconds before requesting another code." },
      headers: { "Retry-After": "30" },
    });
    await openCodeTab();

    await typeInto("Phone number", "9876543210");
    await fireEvent.press(screen.getByLabelText("Send code"));

    await screen.findByText("Please wait 30 seconds before requesting another code.");
    expect(screen.getByText(/Resend code in \d+s/)).toBeTruthy();
  });

  it("shows an invalid-code message and stays signed out", async () => {
    server
      .on("POST /api/v1/auth/otp/request", { status: 200, body: {} })
      .on("POST /api/v1/auth/otp/verify", { status: 401, body: { message: "That code is not valid." } });
    await openCodeTab();

    await typeInto("Phone number", "9876543210");
    await fireEvent.press(screen.getByLabelText("Send code"));
    await screen.findByText("If this number is registered, a code has been sent.");
    await typeInto("One-time code", "000000");
    await fireEvent.press(screen.getByLabelText("Verify code and sign in"));

    await screen.findByText("That code is not valid.");
    expect(screen.queryByText(/Welcome/)).toBeNull();
  });
});

describe("forgot password", () => {
  it("resets a password with a code and returns to sign-in with a notice", async () => {
    server
      .on("GET /api/v1/auth/password-reset/channels", { status: 200, body: ["SMS"] })
      .on("POST /api/v1/auth/otp/request", { status: 200, body: {} })
      .on("POST /api/v1/auth/otp/verify", { status: 200, body: { resetToken: "reset-token-1" } })
      .on("POST /api/v1/auth/password-reset/complete", { status: 200 });
    await openApp();

    await fireEvent.press(screen.getByLabelText("Forgot password"));
    await screen.findByText("Reset your password");

    await typeInto("Phone number or username", "priya.manager");
    await act(async () => {
      await fireEvent.press(screen.getByLabelText("Send code"));
    });
    await screen.findByText("If this account exists, a code has been sent.");

    await typeInto("One-time code", "654321");
    await fireEvent.press(screen.getByLabelText("Verify code"));
    await screen.findByText("At least 10 characters.");

    await typeInto("New password", "new-password-123");
    await typeInto("Confirm new password", "new-password-123");
    await fireEvent.press(screen.getByLabelText("Change password"));

    await screen.findByText("Password changed. Sign in with your new password.");
    expect(server.callsTo("POST /api/v1/auth/password-reset/complete")[0].body).toEqual({
      resetToken: "reset-token-1",
      newPassword: "new-password-123",
    });
  });

  it("will not submit a password shorter than 10 characters or one that does not match", async () => {
    server
      .on("GET /api/v1/auth/password-reset/channels", { status: 200, body: ["SMS"] })
      .on("POST /api/v1/auth/otp/request", { status: 200, body: {} })
      .on("POST /api/v1/auth/otp/verify", { status: 200, body: { resetToken: "t" } });
    await openApp();
    await fireEvent.press(screen.getByLabelText("Forgot password"));
    await typeInto("Phone number or username", "9876543210");
    await act(async () => {
      await fireEvent.press(screen.getByLabelText("Send code"));
    });
    await screen.findByText("If this account exists, a code has been sent.");
    await typeInto("One-time code", "111111");
    await fireEvent.press(screen.getByLabelText("Verify code"));
    await screen.findByText("At least 10 characters.");

    await typeInto("New password", "short");
    await typeInto("Confirm new password", "short");
    expect(screen.getByLabelText("Change password").props.accessibilityState?.disabled).toBe(true);

    await typeInto("New password", "long-enough-pass");
    await typeInto("Confirm new password", "different-pass-x");
    await screen.findByText("The passwords do not match.");
    expect(screen.getByLabelText("Change password").props.accessibilityState?.disabled).toBe(true);
  });
});
