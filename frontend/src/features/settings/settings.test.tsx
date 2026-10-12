import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { axe } from "vitest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../app/RouteGuard";
import { SystemDashboard } from "../../dashboards/SystemDashboard";
import { buildMuiTheme } from "../../theme/tokens";
import { SecuritySettingsPage } from "./SecuritySettingsPage";
import { problemWith, type SettingView } from "./settingsApi";

const authFetch = vi.fn();
let navigation: { section: string; items: unknown[] }[] = [];

vi.mock("../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

vi.mock("../../access-model/useAccessModel", async () => {
  const actual = await vi.importActual<
    typeof import("../../access-model/useAccessModel")
  >("../../access-model/useAccessModel");
  return {
    ...actual,
    useAccessModel: () => ({
      loading: false,
      accessModel: { roles: ["SYSTEM"], dataScope: {}, navigation },
    }),
  };
});

function grantFor(actions: string[]) {
  navigation = [
    {
      section: "SYSTEM CONFIGURATION",
      items: [{ label: "Security Settings", route: "/system/security", actions }],
    },
  ];
}

function setting(over: Partial<SettingView>): SettingView {
  return {
    key: "lockout.max-failed-attempts",
    label: "Failed sign-ins before lockout",
    unit: "attempts",
    type: "INTEGER",
    min: 3,
    max: 20,
    default: "5",
    value: "5",
    isDefault: true,
    updatedAt: null,
    updatedBy: null,
    ...over,
  };
}

const GROUP = {
  group: "security",
  settings: [
    setting({}),
    setting({
      key: "password.min-length",
      label: "Minimum password length",
      unit: "characters",
      min: 8,
      max: 64,
      default: "10",
      value: "12",
      isDefault: false,
      updatedAt: "2026-10-01T10:00:00Z",
      updatedBy: "Sam System",
    }),
  ],
};

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function renderPage() {
  return render(
    <ThemeProvider theme={buildMuiTheme("light")}>
      <MemoryRouter initialEntries={["/system/security"]}>
        <Routes>
          <Route
            path="/system/security"
            element={
              <RouteGuard>
                <SecuritySettingsPage />
              </RouteGuard>
            }
          />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  authFetch.mockReset();
  grantFor(["VIEW", "EDIT"]);
  authFetch.mockImplementation(async (_url: string, init?: RequestInit) =>
    init?.method === "PUT" ? json(GROUP) : json(GROUP),
  );
});

describe("problemWith", () => {
  it("accepts values in range and refuses the rest", () => {
    const s = setting({});
    expect(problemWith(s, "3")).toBeNull();
    expect(problemWith(s, "20")).toBeNull();
    expect(problemWith(s, "2")).toMatch(/from 3 to 20/);
    expect(problemWith(s, "21")).toMatch(/from 3 to 20/);
    expect(problemWith(s, "4.5")).toMatch(/whole number/);
    expect(problemWith(s, "")).toMatch(/whole number/);
    const v = setting({ type: "VERSION" });
    expect(problemWith(v, "1.2.0")).toBeNull();
    expect(problemWith(v, "2")).toMatch(/1\.2\.0/);
  });
});

describe("Security Settings (spec 011 US2)", () => {
  it("shows every value with its range, default and who changed it", async () => {
    renderPage();
    expect(
      await screen.findByRole("heading", { name: "Security Settings" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Minimum password length")).toHaveValue("12");
    expect(screen.getByText("3 to 20 attempts")).toBeInTheDocument();
    expect(screen.getByText(/Default is 10 · Changed by Sam System/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save changes" })).toBeDisabled();
  });

  it("refuses an out-of-range value on the screen without calling the server", async () => {
    const user = userEvent.setup();
    renderPage();
    const field = await screen.findByLabelText("Failed sign-ins before lockout");
    authFetch.mockClear();
    await user.clear(field);
    await user.type(field, "1");
    expect(screen.getByText("1 unsaved change")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Save changes" }));
    expect(screen.getByText(/from 3 to 20 attempts/, { selector: "p" })).toBeInTheDocument();
    expect(authFetch).not.toHaveBeenCalled();
  });

  it("saves only the changed values", async () => {
    const user = userEvent.setup();
    renderPage();
    const field = await screen.findByLabelText("Failed sign-ins before lockout");
    await user.clear(field);
    await user.type(field, "3");
    await user.click(screen.getByRole("button", { name: "Save changes" }));
    await waitFor(() =>
      expect(authFetch).toHaveBeenCalledWith(
        "/api/v1/settings/security",
        expect.objectContaining({
          method: "PUT",
          body: JSON.stringify({ values: { "lockout.max-failed-attempts": "3" } }),
        }),
      ),
    );
    expect(await screen.findByText("Settings saved.")).toBeInTheDocument();
  });

  it("shows the server's per-field problem when a save is refused", async () => {
    const user = userEvent.setup();
    authFetch.mockImplementation(async (_url: string, init?: RequestInit) =>
      init?.method === "PUT"
        ? json(
            {
              message: "Some settings are not valid.",
              fields: { "lockout.max-failed-attempts": "Server says no." },
            },
            422,
          )
        : json(GROUP),
    );
    renderPage();
    const field = await screen.findByLabelText("Failed sign-ins before lockout");
    await user.clear(field);
    await user.type(field, "4");
    await user.click(screen.getByRole("button", { name: "Save changes" }));
    expect(await screen.findByText("Server says no.")).toBeInTheDocument();
    expect(screen.getByText("Some settings are not valid.")).toBeInTheDocument();
  });

  it("is read-only without the Edit grant", async () => {
    grantFor(["VIEW"]);
    renderPage();
    const field = await screen.findByLabelText("Failed sign-ins before lockout");
    expect(field).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Save changes" })).toBeNull();
  });

  it("shows not-authorized without the View grant and never loads the data", async () => {
    navigation = [];
    renderPage();
    await waitFor(() => expect(authFetch).not.toHaveBeenCalled());
    expect(screen.queryByLabelText("Minimum password length")).toBeNull();
  });

  it("has no accessibility violations", async () => {
    const { container } = renderPage();
    await screen.findByLabelText("Minimum password length");
    const results = await axe(container, {
      runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
    });
    expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
  }, 30_000);
});

describe("System dashboard (spec 011 US1)", () => {
  const DASHBOARD = {
    health: {
      database: "UP",
      applicationVersion: "0.1.0",
      migrationVersion: "40",
      startedAt: "2026-10-11T08:00:00Z",
      uptimeSeconds: 3700,
      serverTime: "2026-10-11T09:01:40Z",
    },
    users: {
      total: 12,
      active: 10,
      inactive: 2,
      lockedNow: 1,
      byRole: { ADMIN: 1, TEACHER: 7 },
    },
    failedSignInsLast24h: 3,
    recentAudit: {
      status: "OK",
      events: [
        {
          kind: "CHANGE",
          at: "2026-10-11T09:00:00Z",
          actorName: "Sam System",
          summary: "SECURITY_SETTING lockout.max-failed-attempts value: 5 -> 3",
        },
      ],
    },
  };

  it("shows health, users and the recent audit events", async () => {
    authFetch.mockResolvedValue(json(DASHBOARD));
    render(
      <ThemeProvider theme={buildMuiTheme("light")}>
        <SystemDashboard />
      </ThemeProvider>,
    );
    expect(await screen.findByText("Database UP")).toBeInTheDocument();
    expect(screen.getByText(/migration 40/)).toBeInTheDocument();
    expect(screen.getByText("10 active")).toBeInTheDocument();
    expect(screen.getByText(/1 locked now/)).toBeInTheDocument();
    expect(screen.getByText(/3 failed sign-ins/)).toBeInTheDocument();
    expect(screen.getByText(/lockout.max-failed-attempts value: 5 -> 3/)).toBeInTheDocument();
  });

  it("says so when the audit lookup is unavailable and still shows the rest", async () => {
    authFetch.mockResolvedValue(
      json({ ...DASHBOARD, recentAudit: { status: "UNAVAILABLE", events: [] } }),
    );
    render(
      <ThemeProvider theme={buildMuiTheme("light")}>
        <SystemDashboard />
      </ThemeProvider>,
    );
    expect(await screen.findByText("Database UP")).toBeInTheDocument();
    expect(screen.getByText(/not available right now/)).toBeInTheDocument();
  });

  it("shows an error when the dashboard cannot load", async () => {
    authFetch.mockResolvedValue(json({}, 403));
    render(
      <ThemeProvider theme={buildMuiTheme("light")}>
        <SystemDashboard />
      </ThemeProvider>,
    );
    expect(await screen.findByText("Could not load the dashboard.")).toBeInTheDocument();
  });
});
