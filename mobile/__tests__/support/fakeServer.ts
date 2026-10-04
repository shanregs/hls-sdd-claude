import { PROFILE, TEACHER_MODEL } from "./fixtures";

/**
 * A tiny in-memory stand-in for the HLS API: tests register handlers by "METHOD /path" and the
 * real HTTP client and providers run against it (spec 018 tests).
 */
export interface FakeRequest {
  method: string;
  path: string;
  headers: Record<string, string>;
  body: unknown;
}

export interface FakeReply {
  status: number;
  body?: unknown;
  headers?: Record<string, string>;
}

export type Handler = (request: FakeRequest) => FakeReply | Promise<FakeReply>;

export class FakeServer {
  readonly calls: FakeRequest[] = [];
  private handlers = new Map<string, Handler>();
  offline = false;

  on(route: string, handler: Handler | FakeReply): this {
    this.handlers.set(route, typeof handler === "function" ? handler : () => handler);
    return this;
  }

  install(): void {
    globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
      if (this.offline) throw new TypeError("Network request failed");
      const url = new URL(String(input));
      const method = (init?.method ?? "GET").toUpperCase();
      const request: FakeRequest = {
        method,
        path: url.pathname,
        headers: { ...((init?.headers as Record<string, string>) ?? {}) },
        body: init?.body ? JSON.parse(String(init.body)) : undefined,
      };
      this.calls.push(request);
      const handler = this.handlers.get(`${method} ${url.pathname}`);
      const reply: FakeReply = handler ? await handler(request) : { status: 404, body: { message: "not found" } };
      if (reply.status === 204) return new Response(null, { status: 204, headers: reply.headers });
      return new Response(reply.body === undefined ? "" : JSON.stringify(reply.body), {
        status: reply.status,
        headers: reply.headers,
      });
    }) as typeof fetch;
  }

  callsTo(route: string): FakeRequest[] {
    const [method, path] = route.split(" ");
    return this.calls.filter((c) => c.method === method && c.path === path);
  }
}

export const TARA = { id: "u-1", displayName: "Tara", roles: ["TEACHER"] };

export function authResult(overrides: Partial<{ renewalCredential: string; accessToken: string }> = {}) {
  return {
    accessToken: overrides.accessToken ?? "access-1",
    expiresInSeconds: 900,
    renewalCredential: overrides.renewalCredential ?? "renewal-1",
    user: TARA,
  };
}

/** The usual starting point: app-config says any version is fine; the user is a Teacher. */
export function newServer(minimumVersion = "0.0.0"): FakeServer {
  const server = new FakeServer();
  server.on("GET /api/v1/mobile/app-config", {
    status: 200,
    body: { minimumVersion, locationWaitSeconds: 4, locationReuseSeconds: 10 },
  });
  server.on("GET /api/v1/me/access-model", { status: 200, body: TEACHER_MODEL });
  server.on("GET /api/v1/me/profile", { status: 200, body: PROFILE });
  server.install();
  return server;
}
