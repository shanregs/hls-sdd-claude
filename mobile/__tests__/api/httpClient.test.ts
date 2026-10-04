import * as SecureStore from "expo-secure-store";
import AsyncStorage from "@react-native-async-storage/async-storage";
import {
  api,
  registerClientHeadersProvider,
  setAuthHooks,
} from "../../src/api/httpClient";
import {
  ApiError,
  ForbiddenError,
  LockedError,
  NoConnectionError,
  RateLimitedError,
  UnauthorizedError,
  UpdateRequiredError,
  WebOnlyRoleError,
} from "../../src/api/errors";
import { getAccessToken, setAccessToken } from "../../src/security/memoryToken";

type FetchMock = jest.Mock<Promise<Response>, [string, RequestInit]>;

function reply(status: number, body?: unknown, headers: Record<string, string> = {}): Response {
  if (status === 204 || status === 205 || status === 304) {
    return new Response(null, { status, headers });
  }
  return new Response(body === undefined ? "" : JSON.stringify(body), { status, headers });
}

let fetchMock: FetchMock;

beforeEach(() => {
  fetchMock = jest.fn();
  globalThis.fetch = fetchMock as unknown as typeof fetch;
  setAccessToken(null);
  setAuthHooks(null);
  jest.clearAllMocks();
});

const headersOf = (call: number) => (fetchMock.mock.calls[call][1].headers ?? {}) as Record<string, string>;

describe("headers", () => {
  it("adds the client headers to every call", async () => {
    fetchMock.mockImplementation(async () => reply(200, { ok: true }));

    await api.get("/api/v1/anything", { auth: false });
    await api.post("/api/v1/other", { a: 1 }, { auth: false });

    for (const call of [0, 1]) {
      expect(headersOf(call)["X-HLS-Client"]).toBe("android/1.0.0");
      expect(headersOf(call)["User-Agent"]).toMatch(/^HLS-Android\/1\.0\.0 \(Android /);
    }
    expect(headersOf(1)["Content-Type"]).toBe("application/json");
  });

  it("adds no location header when no provider supplies one", async () => {
    fetchMock.mockImplementation(async () => reply(200, {}));

    await api.get("/api/v1/anything", { auth: false });

    expect(Object.keys(headersOf(0)).filter((k) => k.toLowerCase().startsWith("x-hls-location"))).toEqual([]);
  });

  it("merges provider headers and ignores a provider that throws", async () => {
    fetchMock.mockImplementation(async () => reply(200, {}));
    const off1 = registerClientHeadersProvider(async () => ({ "X-HLS-Test": "yes" }));
    const off2 = registerClientHeadersProvider(async () => {
      throw new Error("boom");
    });

    await api.get("/api/v1/anything", { auth: false });
    off1();
    off2();

    expect(headersOf(0)["X-HLS-Test"]).toBe("yes");
  });

  it("sends the bearer token only when asked", async () => {
    fetchMock.mockImplementation(async () => reply(200, {}));
    setAccessToken("tok");

    await api.get("/api/v1/secure");
    await api.get("/api/v1/public", { auth: false });

    expect(headersOf(0).Authorization).toBe("Bearer tok");
    expect(headersOf(1).Authorization).toBeUndefined();
  });
});

describe("storage", () => {
  it("never persists the access token", async () => {
    fetchMock.mockImplementation(async () => reply(200, {}));
    setAccessToken("secret-access-token");

    await api.get("/api/v1/secure");

    expect(SecureStore.setItemAsync).not.toHaveBeenCalled();
    expect(AsyncStorage.setItem).not.toHaveBeenCalled();
    expect(getAccessToken()).toBe("secret-access-token");
  });
});

describe("error mapping", () => {
  const cases: Array<[number, unknown, Record<string, string>, new (...a: never[]) => Error]> = [
    [401, { message: "Please sign in again." }, {}, UnauthorizedError],
    [403, { code: "WEB_ONLY_ROLE", message: "Your account uses the HLS web application." }, {}, WebOnlyRoleError],
    [403, { message: "no" }, {}, ForbiddenError],
    [423, { message: "Account locked until 14:32.", unlockAt: "2026-10-04T09:02:00Z" }, {}, LockedError],
    [426, { code: "APP_UPDATE_REQUIRED", minimumVersion: "1.2.0" }, {}, UpdateRequiredError],
    [429, { message: "Too many requests." }, { "Retry-After": "30" }, RateLimitedError],
    [500, { message: "boom" }, {}, ApiError],
  ];

  it.each(cases)("maps %i to the right typed error", async (status, body, headers, ErrorType) => {
    fetchMock.mockImplementation(async () => reply(status, body, headers));

    await expect(api.get("/api/v1/x", { auth: false })).rejects.toBeInstanceOf(ErrorType);
  });

  it("carries the details of locked, rate-limited and update-required errors", async () => {
    fetchMock.mockResolvedValueOnce(reply(423, { message: "Locked", unlockAt: "2026-10-04T09:02:00Z" }));
    await expect(api.get("/api/v1/x", { auth: false })).rejects.toMatchObject({ unlockAt: "2026-10-04T09:02:00Z" });

    fetchMock.mockResolvedValueOnce(reply(429, { message: "Slow" }, { "Retry-After": "30" }));
    await expect(api.get("/api/v1/x", { auth: false })).rejects.toMatchObject({ retryAfterSeconds: 30 });

    fetchMock.mockResolvedValueOnce(reply(426, { minimumVersion: "1.2.0" }));
    await expect(api.get("/api/v1/x", { auth: false })).rejects.toMatchObject({ minimumVersion: "1.2.0" });
  });

  it("maps a network failure to NoConnectionError", async () => {
    fetchMock.mockRejectedValue(new TypeError("Network request failed"));

    await expect(api.get("/api/v1/x", { auth: false })).rejects.toBeInstanceOf(NoConnectionError);
  });

  it("returns the parsed body on success and undefined for an empty one", async () => {
    fetchMock.mockResolvedValueOnce(reply(200, { a: 1 }));
    await expect(api.get("/api/v1/x", { auth: false })).resolves.toEqual({ a: 1 });

    fetchMock.mockResolvedValueOnce(reply(204));
    await expect(api.get("/api/v1/x", { auth: false })).resolves.toBeUndefined();
  });
});

describe("renewal on 401", () => {
  it("renews once and retries the call", async () => {
    setAccessToken("old");
    const renewAccess = jest.fn(async () => {
      setAccessToken("new");
      return true;
    });
    setAuthHooks({ renewAccess });
    fetchMock.mockResolvedValueOnce(reply(401, { message: "expired" })).mockResolvedValueOnce(reply(200, { ok: 1 }));

    await expect(api.get("/api/v1/secure")).resolves.toEqual({ ok: 1 });

    expect(renewAccess).toHaveBeenCalledTimes(1);
    expect(headersOf(0).Authorization).toBe("Bearer old");
    expect(headersOf(1).Authorization).toBe("Bearer new");
  });

  it("surfaces UnauthorizedError when renewal fails, without retrying", async () => {
    setAccessToken("old");
    setAuthHooks({ renewAccess: jest.fn(async () => false) });
    fetchMock.mockImplementation(async () => reply(401, { message: "expired" }));

    await expect(api.get("/api/v1/secure")).rejects.toBeInstanceOf(UnauthorizedError);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("does not try to renew a call that was sent without credentials", async () => {
    const renewAccess = jest.fn(async () => true);
    setAuthHooks({ renewAccess });
    fetchMock.mockImplementation(async () => reply(401, { message: "Wrong" }));

    await expect(api.post("/api/v1/auth/login", {}, { auth: false })).rejects.toBeInstanceOf(UnauthorizedError);
    expect(renewAccess).not.toHaveBeenCalled();
  });
});
