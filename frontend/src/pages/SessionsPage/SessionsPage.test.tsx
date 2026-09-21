import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionsPage } from "./SessionsPage";
import { AuthProvider, useAuth } from "../../auth/AuthContext";

const sessionsBody = [
  {
    id: "s1",
    channel: "WEB",
    deviceLabel: "Chrome on Windows",
    issuedAt: "2026-09-21T00:00:00Z",
    lastActiveAt: "2026-09-21T00:00:00Z",
  },
  {
    id: "s2",
    channel: "MOBILE",
    deviceLabel: null,
    issuedAt: "2026-09-21T00:00:00Z",
    lastActiveAt: "2026-09-21T00:00:00Z",
  },
];

function mockFetchSequence(responses: Array<{ status: number; body: unknown }>) {
  let call = 0;
  vi.stubGlobal(
    "fetch",
    vi.fn(() => {
      const { status, body } = responses[Math.min(call, responses.length - 1)];
      call += 1;
      return Promise.resolve({ ok: status < 400, status, json: () => Promise.resolve(body) } as Response);
    }),
  );
}

/** Logs a fake access token into AuthContext on mount, so SessionsPage's own list/revoke logic can be exercised without going through a real login flow. */
function LogInWithFakeToken() {
  const { setTokens } = useAuth();
  useEffect(() => {
    setTokens({ accessToken: "fake-token", expiresIn: 900 });
  }, [setTokens]);
  return null;
}

function renderAuthenticated() {
  return render(
    <AuthProvider>
      <LogInWithFakeToken />
      <SessionsPage />
    </AuthProvider>,
  );
}

describe("SessionsPage", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("lists each active session with identifying detail (User Story 5, scenario 1)", async () => {
    mockFetchSequence([{ status: 200, body: sessionsBody }]);
    renderAuthenticated();

    const rows = await screen.findAllByTestId("session-row");
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent("Chrome on Windows");
    expect(rows[1]).toHaveTextContent("MOBILE");
  });

  it("removes a session from the list after revoking it (scenario 2)", async () => {
    mockFetchSequence([
      { status: 200, body: sessionsBody },
      { status: 204, body: {} }, // DELETE
      { status: 200, body: [sessionsBody[1]] }, // reload after revoke
    ]);
    renderAuthenticated();

    await screen.findAllByTestId("session-row");
    const revokeButtons = screen.getAllByRole("button", { name: /revoke/i });
    await userEvent.click(revokeButtons[0]);

    await waitFor(async () => {
      const rows = await screen.findAllByTestId("session-row");
      expect(rows).toHaveLength(1);
    });
  });
});
