import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SessionsPage } from "./SessionsPage";

const authFetch = vi.fn();
const logout = vi.fn();
let grantedActions = ["VIEW", "DELETE"];

vi.mock("../access-model/useAccessModel", () => ({
  useAccessModel: () => ({
    loading: false,
    accessModel: {
      roles: ["MANAGER"],
      dataScope: {},
      navigation: [
        {
          section: "ACCOUNT",
          items: [
            {
              label: "Sessions",
              route: "/account/sessions",
              actions: grantedActions,
            },
          ],
        },
      ],
    },
  }),
}));

vi.mock("../auth/useAuth", () => ({
  useAuth: () => ({
    user: { id: "u1", roles: ["MANAGER"] },
    authFetch,
    logout,
  }),
}));

const CHROME =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36";

const SESSIONS = [
  {
    id: "s1",
    deviceDescription: CHROME,
    signedInAt: new Date(2026, 9, 3, 8, 15, 0).toISOString(),
    lastActivityAt: new Date(2026, 9, 3, 9, 0, 0).toISOString(),
    current: false,
    clientType: "WEB",
    appVersion: null,
  },
  {
    id: "s2",
    deviceDescription: "okhttp/4.12",
    signedInAt: new Date(2026, 9, 4, 14, 31, 23).toISOString(),
    lastActivityAt: new Date(2026, 9, 4, 14, 40, 0).toISOString(),
    current: true,
    clientType: "ANDROID",
    appVersion: "1.2.0",
  },
];

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function mockApi(
  extra: (url: string, init?: RequestInit) => Response | null = () => null,
) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    const custom = extra(url, init);
    if (custom) return custom;
    if (url === "/api/v1/me/sessions" && !init?.method) return json(SESSIONS);
    return json({});
  });
}

function calls(method: string) {
  return authFetch.mock.calls
    .filter(([, init]) => init?.method === method)
    .map(([url]) => String(url));
}

describe("SessionsPage", () => {
  beforeEach(() => {
    authFetch.mockReset();
    logout.mockReset();
    logout.mockResolvedValue(undefined);
    grantedActions = ["VIEW", "DELETE"];
  });

  it("shows the sessions but no delete icons when the role may not delete", async () => {
    grantedActions = ["VIEW"];
    mockApi();
    render(<SessionsPage />);

    const table = await screen.findByRole("table", { name: "Sessions" });
    expect(within(table).getAllByRole("row")).toHaveLength(3);
    expect(
      within(table).queryByRole("columnheader", { name: "Delete" }),
    ).toBeNull();
    expect(screen.queryByRole("button", { name: /Delete/ })).toBeNull();
  });

  it("shows the sessions as a grid: number, signed in since, origin and a delete icon", async () => {
    mockApi();
    render(<SessionsPage />);

    const table = await screen.findByRole("table", { name: "Sessions" });
    expect(
      screen.getByRole("heading", { name: "My sessions", level: 1 }),
    ).toBeInTheDocument();
    for (const name of ["Session", "Signed in since", "Origin", "Delete"]) {
      expect(
        within(table).getByRole("columnheader", { name }),
      ).toBeInTheDocument();
    }
    const rows = within(table).getAllByRole("row").slice(1);
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByRole("rowheader")).toHaveTextContent("1");
    expect(rows[0]).toHaveTextContent("03/10/2026 08:15:00");
    expect(rows[0]).toHaveTextContent("Chrome on Windows");
    expect(rows[1]).toHaveTextContent("2");
    expect(rows[1]).toHaveTextContent("04/10/2026 14:31:23");
    expect(rows[1]).toHaveTextContent("Android app 1.2.0");
    expect(rows[1]).toHaveTextContent("This device");
    expect(
      within(rows[0]).getByRole("button", { name: "Delete session 1" }),
    ).toBeInTheDocument();
  });

  it("deletes another session after confirming, and stays signed in", async () => {
    mockApi((url, init) =>
      init?.method === "DELETE" ? json(null, 204) : null,
    );
    render(<SessionsPage />);
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "Delete session 1" }),
    );
    const dialog = await screen.findByRole("dialog");
    expect(dialog).toHaveTextContent("Delete session 1?");
    expect(dialog).toHaveTextContent("That device will be signed out.");
    await user.click(within(dialog).getByRole("button", { name: "Delete" }));

    await vi.waitFor(() =>
      expect(calls("DELETE")).toEqual(["/api/v1/me/sessions/s1"]),
    );
    expect(logout).not.toHaveBeenCalled();
  });

  it("warns that deleting the current session signs you out, then signs out", async () => {
    mockApi((url, init) =>
      init?.method === "DELETE" ? json(null, 204) : null,
    );
    render(<SessionsPage />);
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "Delete session 2" }),
    );
    const dialog = await screen.findByRole("dialog");
    expect(dialog).toHaveTextContent("you will be signed out");
    await user.click(within(dialog).getByRole("button", { name: "Delete" }));

    await vi.waitFor(() => expect(logout).toHaveBeenCalled());
    expect(calls("DELETE")).toEqual(["/api/v1/me/sessions/s2"]);
  });

  it("deletes every session from the icon at the top and signs you out", async () => {
    mockApi((url, init) =>
      init?.method === "DELETE" ? json({ ended: 2 }) : null,
    );
    render(<SessionsPage />);
    const user = userEvent.setup();

    await screen.findByRole("table", { name: "Sessions" });
    await user.click(
      screen.getByRole("button", { name: "Delete all my sessions" }),
    );
    const dialog = await screen.findByRole("dialog");
    expect(dialog).toHaveTextContent("Delete all sessions?");
    await user.click(within(dialog).getByRole("button", { name: "Delete" }));

    await vi.waitFor(() => expect(logout).toHaveBeenCalled());
    expect(calls("DELETE")).toEqual(["/api/v1/me/sessions"]);
  });

  it("does nothing when the confirmation is cancelled", async () => {
    mockApi();
    render(<SessionsPage />);
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "Delete session 1" }),
    );
    await user.click(
      within(await screen.findByRole("dialog")).getByRole("button", {
        name: "Cancel",
      }),
    );

    expect(calls("DELETE")).toEqual([]);
    expect(logout).not.toHaveBeenCalled();
  });

  it("says so when a delete fails and keeps you signed in", async () => {
    mockApi((url, init) =>
      init?.method === "DELETE" ? json({ reason: "nope" }, 500) : null,
    );
    render(<SessionsPage />);
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "Delete session 1" }),
    );
    await user.click(
      within(await screen.findByRole("dialog")).getByRole("button", {
        name: "Delete",
      }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not end the session",
    );
    expect(logout).not.toHaveBeenCalled();
  });

  it("shows an empty state and disables delete-all when there are no sessions", async () => {
    authFetch.mockResolvedValue(json([]));
    render(<SessionsPage />);

    expect(await screen.findByText("No active sessions.")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Delete all my sessions" }),
    ).toBeDisabled();
  });

  it("shows an error when the sessions cannot be loaded", async () => {
    authFetch.mockResolvedValue(json({}, 500));
    render(<SessionsPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load your sessions.",
    );
  });
});
