import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AllSessionsPage } from "./AllSessionsPage";

const authFetch = vi.fn();
const logout = vi.fn();
let grantedActions = ["VIEW", "DELETE"];

vi.mock("../../access-model/useAccessModel", () => ({
  useAccessModel: () => ({
    loading: false,
    accessModel: {
      roles: ["SYSTEM"],
      dataScope: {},
      navigation: [
        {
          section: "SYSTEM CONFIGURATION",
          items: [
            {
              label: "All Sessions",
              route: "/identity/sessions",
              actions: grantedActions,
            },
          ],
        },
      ],
    },
  }),
}));

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({
    user: { id: "sys", roles: ["SYSTEM"] },
    authFetch,
    logout,
  }),
}));

const USERS = [
  {
    id: "u-tara",
    displayName: "Tara Teacher",
    phone: "9800000004",
    username: null,
    email: null,
    roles: ["TEACHER"],
    active: true,
  },
  {
    id: "u-sys",
    displayName: "Sunil System",
    phone: "9800000005",
    username: "sunil.system",
    email: null,
    roles: ["SYSTEM"],
    active: true,
  },
];

function session(
  id: string,
  userId: string,
  name: string,
  phone: string,
  current = false,
) {
  return {
    id,
    userId,
    userName: name,
    userPhone: phone,
    deviceDescription:
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/154.0.0.0 Safari/537.36",
    signedInAt: new Date(2026, 9, 4, 10, 0, 0).toISOString(),
    lastActivityAt: new Date(2026, 9, 4, 10, 5, 0).toISOString(),
    current,
    clientType: "WEB",
    appVersion: null,
  };
}

const ALL = [
  session("s1", "u-tara", "Tara Teacher", "9800000004"),
  session("s2", "u-sys", "Sunil System", "9800000005", true),
];

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function page(content: unknown[], total = content.length, number = 0) {
  return { content, page: number, size: 25, totalElements: total };
}

function mockApi(
  custom: (url: string, init?: RequestInit) => Response | null = () => null,
) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    const handled = custom(url, init);
    if (handled) return handled;
    if (url.startsWith("/api/v1/identity/users")) {
      return json({ content: USERS, page: 0, size: 20, totalElements: 2 });
    }
    if (url.startsWith("/api/v1/admin/sessions") && !init?.method) {
      const filtered = url.includes("userId=u-tara") ? [ALL[0]] : ALL;
      return json(page(filtered));
    }
    return json({ ended: 1, includesCurrent: false });
  });
}

function sessionCalls(method?: string) {
  return authFetch.mock.calls
    .filter(([url]) => String(url).startsWith("/api/v1/admin/sessions"))
    .filter(([, init]) => (method ? init?.method === method : !init?.method))
    .map(([url]) => String(url));
}

describe("AllSessionsPage (System)", () => {
  beforeEach(() => {
    authFetch.mockReset();
    logout.mockReset();
    logout.mockResolvedValue(undefined);
    grantedActions = ["VIEW", "DELETE"];
  });

  it("lists sessions without any delete icons when the role may only view", async () => {
    grantedActions = ["VIEW"];
    mockApi();
    render(<AllSessionsPage />);

    await screen.findByRole("table", { name: "Sessions" });
    expect(screen.queryByRole("button", { name: /Delete/ })).toBeNull();
    expect(screen.queryByRole("columnheader", { name: "Delete" })).toBeNull();
  });

  it("lists every user's sessions with the owner, time and origin", async () => {
    mockApi();
    render(<AllSessionsPage />);

    const table = await screen.findByRole("table", { name: "Sessions" });
    expect(
      within(table).getByRole("columnheader", { name: "User" }),
    ).toBeInTheDocument();
    const rows = within(table).getAllByRole("row").slice(1);
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent("Tara Teacher");
    expect(rows[0]).toHaveTextContent("9800000004");
    expect(rows[0]).toHaveTextContent("04/10/2026 10:00:00");
    expect(rows[0]).toHaveTextContent("Chrome on Windows");
    expect(rows[1]).toHaveTextContent("This device");
    expect(
      screen.getByRole("heading", { name: "All sessions", level: 1 }),
    ).toBeInTheDocument();
  });

  it("filters to one user and enables deleting all of that user's sessions", async () => {
    mockApi();
    render(<AllSessionsPage />);
    const user = userEvent.setup();
    await screen.findByRole("table", { name: "Sessions" });
    expect(
      screen.getByRole("button", {
        name: "Delete all sessions of the chosen user",
      }),
    ).toBeDisabled();

    await user.click(screen.getByRole("combobox", { name: "User" }));
    await user.click(
      await screen.findByRole("option", { name: /Tara Teacher/ }),
    );

    await vi.waitFor(() =>
      expect(sessionCalls().some((u) => u.includes("userId=u-tara"))).toBe(
        true,
      ),
    );
    expect(
      await screen.findByRole("button", {
        name: "Delete all sessions of Tara Teacher",
      }),
    ).toBeEnabled();
  });

  it("deletes one session after confirming and reloads", async () => {
    mockApi();
    render(<AllSessionsPage />);
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "Delete session 1" }),
    );
    const dialog = await screen.findByRole("dialog");
    expect(dialog).toHaveTextContent("Tara Teacher will be signed out");
    await user.click(within(dialog).getByRole("button", { name: "Delete" }));

    await vi.waitFor(() =>
      expect(sessionCalls("DELETE")).toEqual(["/api/v1/admin/sessions/s1"]),
    );
    expect(logout).not.toHaveBeenCalled();
  });

  it("signs you out when the session you delete is your own", async () => {
    mockApi((url, init) =>
      init?.method === "DELETE" && url.endsWith("/s2")
        ? json({ ended: 1, includesCurrent: true })
        : null,
    );
    render(<AllSessionsPage />);
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "Delete session 2" }),
    );
    const dialog = await screen.findByRole("dialog");
    expect(dialog).toHaveTextContent("you will be signed out");
    await user.click(within(dialog).getByRole("button", { name: "Delete" }));

    await vi.waitFor(() => expect(logout).toHaveBeenCalled());
  });

  it("deletes all sessions of the chosen user", async () => {
    mockApi();
    render(<AllSessionsPage />);
    const user = userEvent.setup();
    await screen.findByRole("table", { name: "Sessions" });
    await user.click(screen.getByRole("combobox", { name: "User" }));
    await user.click(
      await screen.findByRole("option", { name: /Tara Teacher/ }),
    );

    await user.click(
      await screen.findByRole("button", {
        name: "Delete all sessions of Tara Teacher",
      }),
    );
    const dialog = await screen.findByRole("dialog");
    expect(dialog).toHaveTextContent("Delete all sessions of Tara Teacher?");
    await user.click(within(dialog).getByRole("button", { name: "Delete" }));

    await vi.waitFor(() =>
      expect(sessionCalls("DELETE")).toEqual([
        "/api/v1/admin/sessions?userId=u-tara",
      ]),
    );
    expect(logout).not.toHaveBeenCalled();
  });

  it("deletes the sessions of every user and signs you out, since yours is among them", async () => {
    mockApi((url, init) =>
      init?.method === "DELETE"
        ? json({ ended: 2, includesCurrent: true })
        : null,
    );
    render(<AllSessionsPage />);
    const user = userEvent.setup();
    await screen.findByRole("table", { name: "Sessions" });

    await user.click(
      screen.getByRole("button", { name: "Delete all sessions of every user" }),
    );
    const dialog = await screen.findByRole("dialog");
    expect(dialog).toHaveTextContent("Delete the sessions of every user?");
    await user.click(within(dialog).getByRole("button", { name: "Delete" }));

    await vi.waitFor(() => expect(logout).toHaveBeenCalled());
    expect(sessionCalls("DELETE")).toEqual(["/api/v1/admin/sessions"]);
  });

  it("pages through the list and keeps the session numbers counting", async () => {
    mockApi((url, init) => {
      if (url.startsWith("/api/v1/admin/sessions") && !init?.method) {
        const second = url.includes("page=1");
        const rows = second
          ? [session("s26", "u-tara", "Tara Teacher", "9800000004")]
          : Array.from({ length: 25 }, (_, i) =>
              session(`s${i}`, "u-tara", "Tara Teacher", "9800000004"),
            );
        return json(page(rows, 26, second ? 1 : 0));
      }
      return null;
    });
    render(<AllSessionsPage />);
    const user = userEvent.setup();

    expect(await screen.findByText("Showing 1-25 of 26")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Next page" }));

    expect(await screen.findByText("Showing 26-26 of 26")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Delete session 26" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Next page" })).toBeDisabled();
  });

  it("shows an empty state and an error", async () => {
    authFetch.mockResolvedValue(json(page([])));
    const { unmount } = render(<AllSessionsPage />);
    expect(await screen.findByText("No active sessions.")).toBeInTheDocument();
    unmount();

    authFetch.mockResolvedValue(json({}, 403));
    render(<AllSessionsPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load sessions.",
    );
  });
});
