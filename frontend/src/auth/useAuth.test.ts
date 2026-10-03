import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createElement, useState } from "react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { AuthProvider, useAuth } from "./useAuth";

function fakeResponse(options: {
  ok: boolean;
  status?: number;
  body?: unknown;
}): Response {
  return {
    ok: options.ok,
    status: options.status ?? (options.ok ? 200 : 401),
    headers: new Headers(),
    json: async () => options.body,
    text: async () => (typeof options.body === "string" ? options.body : ""),
  } as unknown as Response;
}

function Probe() {
  const { authFetch, initializing } = useAuth();
  const [result, setResult] = useState<string | null>(null);

  return createElement(
    "div",
    null,
    createElement(
      "span",
      { "data-testid": "initializing" },
      String(initializing),
    ),
    createElement(
      "button",
      {
        onClick: async () => {
          const response = await authFetch("/api/v1/protected");
          setResult(await response.text());
        },
      },
      "Call",
    ),
    createElement("span", { "data-testid": "result" }, result ?? ""),
  );
}

describe("useAuth silent renewal (User Story 3, FR-009)", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("renews silently on a 401 and retries the original request transparently", async () => {
    const fetchMock = vi
      .fn()
      // 1) AuthProvider's mount-time renewal attempt: no valid cookie yet.
      .mockResolvedValueOnce(fakeResponse({ ok: false, status: 401 }))
      // 2) The protected call, with an expired/absent access token.
      .mockResolvedValueOnce(fakeResponse({ ok: false, status: 401 }))
      // 3) authFetch's silent renewal call succeeds.
      .mockResolvedValueOnce(
        fakeResponse({
          ok: true,
          body: {
            accessToken: "new-access-token",
            expiresInSeconds: 900,
            user: {
              id: "u1",
              displayName: "Priya Manager",
              roles: ["MANAGER"],
            },
          },
        }),
      )
      // 4) The retried original request, now authorized, succeeds.
      .mockResolvedValueOnce(
        fakeResponse({ ok: true, body: "protected data" }),
      );
    vi.stubGlobal("fetch", fetchMock);

    render(createElement(AuthProvider, null, createElement(Probe)));

    await waitFor(() =>
      expect(screen.getByTestId("initializing")).toHaveTextContent("false"),
    );

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: /call/i }));

    await waitFor(() =>
      expect(screen.getByTestId("result")).toHaveTextContent("protected data"),
    );

    expect(fetchMock).toHaveBeenCalledTimes(4);
    // The retried call (4th) must carry the freshly renewed access token.
    const retryCall = fetchMock.mock.calls[3];
    expect(retryCall[0]).toBe("/api/v1/protected");
    expect(
      (retryCall[1]?.headers as Record<string, string>).Authorization,
    ).toBe("Bearer new-access-token");
  });
});
