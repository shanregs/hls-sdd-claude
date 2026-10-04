import { fetchAppConfig } from "../../src/api/appConfig";
import { fetchAccessModel } from "../../src/api/accessModelApi";
import {
  completePasswordReset,
  fetchResetChannels,
  loginWithPassword,
  logoutOnServer,
  renewSession,
  requestResetCode,
  requestSignInCode,
  verifyResetCode,
  verifySignInCode,
} from "../../src/api/authApi";
import { endSession, listSessions } from "../../src/api/sessionsApi";
import { authResult, newServer, type FakeServer } from "../support/fakeServer";

/**
 * Spec 018 T085: the app's API client speaks exactly the contract in
 * specs/018-android-app-foundation/contracts/mobile-api.md (and spec 001's auth-api.md).
 */
let server: FakeServer;

beforeEach(() => {
  server = newServer();
  for (const route of [
    "POST /api/v1/auth/login",
    "POST /api/v1/auth/otp/verify",
    "POST /api/v1/auth/renew",
  ]) {
    server.on(route, { status: 200, body: authResult() });
  }
  for (const route of [
    "POST /api/v1/auth/otp/request",
    "POST /api/v1/auth/logout",
    "POST /api/v1/auth/password-reset/complete",
    "DELETE /api/v1/me/sessions/s%201",
  ]) {
    server.on(route, { status: 200, body: {} });
  }
  server.on("GET /api/v1/auth/password-reset/channels", { status: 200, body: ["SMS"] });
  server.on("GET /api/v1/me/sessions", { status: 200, body: [] });
});

describe("every call", () => {
  it("identifies the Android client and its version", async () => {
    await loginWithPassword("9876543210", "pw");

    const headers = server.calls[server.calls.length - 1].headers;
    expect(headers["X-HLS-Client"]).toMatch(/^android\/\d+\.\d+\.\d+$/);
    expect(headers["User-Agent"]).toMatch(/^HLS-Android\//);
  });
});

describe("authentication endpoints", () => {
  it("sign-in by password", async () => {
    await loginWithPassword("priya.manager", "secret");
    const call = server.callsTo("POST /api/v1/auth/login")[0];
    expect(call.body).toEqual({ identifier: "priya.manager", password: "secret" });
    expect(call.headers.Authorization).toBeUndefined();
  });

  it("sign-in by one-time code", async () => {
    await requestSignInCode("9876543210");
    await verifySignInCode("9876543210", "123456");
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

  it("renewal sends the credential in the body, never a cookie", async () => {
    await renewSession("cred-1");
    const call = server.callsTo("POST /api/v1/auth/renew")[0];
    expect(call.body).toEqual({ renewalCredential: "cred-1" });
    expect(call.headers.Cookie).toBeUndefined();
  });

  it("logout carries the bearer token it was given", async () => {
    await logoutOnServer("tok-1");
    expect(server.callsTo("POST /api/v1/auth/logout")[0].headers.Authorization).toBe("Bearer tok-1");
  });

  it("password reset uses the channels, request, verify and complete endpoints", async () => {
    await fetchResetChannels("priya manager");
    await requestResetCode("priya.manager", "EMAIL");
    await verifyResetCode("priya.manager", "654321", "EMAIL");
    await completePasswordReset("reset-1", "a-long-new-password");

    expect(server.callsTo("GET /api/v1/auth/password-reset/channels")).toHaveLength(1);
    expect(server.calls.find((c) => c.path.endsWith("/channels"))).toBeDefined();
    expect(server.callsTo("POST /api/v1/auth/otp/request")[0].body).toEqual({
      destination: "priya.manager",
      channel: "EMAIL",
      purpose: "PASSWORD_RESET",
    });
    expect(server.callsTo("POST /api/v1/auth/otp/verify")[0].body).toEqual({
      destination: "priya.manager",
      code: "654321",
      purpose: "PASSWORD_RESET",
      channel: "EMAIL",
    });
    expect(server.callsTo("POST /api/v1/auth/password-reset/complete")[0].body).toEqual({
      resetToken: "reset-1",
      newPassword: "a-long-new-password",
    });
  });
});

describe("other endpoints", () => {
  it("app-config is public and falls back to the defaults when unreachable", async () => {
    const config = await fetchAppConfig();
    expect(config).toEqual({ minimumVersion: "0.0.0", locationWaitSeconds: 4, locationReuseSeconds: 10 });
    expect(server.callsTo("GET /api/v1/mobile/app-config")[0].headers.Authorization).toBeUndefined();

    server.offline = true;
    expect(await fetchAppConfig()).toEqual({ minimumVersion: "0.0.0", locationWaitSeconds: 4, locationReuseSeconds: 10 });
  });

  it("access model and sessions are plain authenticated GETs", async () => {
    await fetchAccessModel();
    await listSessions();
    expect(server.callsTo("GET /api/v1/me/access-model")).toHaveLength(1);
    expect(server.callsTo("GET /api/v1/me/sessions")).toHaveLength(1);
  });

  it("ending a session deletes it by id", async () => {
    await endSession("s 1");
    expect(server.callsTo("DELETE /api/v1/me/sessions/s%201")).toHaveLength(1);
  });
});
