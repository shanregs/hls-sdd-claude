import { render, screen } from "@testing-library/react";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter } from "react-router-dom";
import type { ReactElement } from "react";
import { describe, expect, it, vi } from "vitest";
import { axe } from "vitest-axe";
import { buildMuiTheme, type ThemeMode } from "../theme/tokens";
import { SignInPage } from "../auth/SignInPage";
import { OtpEntryPage } from "../auth/OtpEntryPage";
import { ForgotPasswordPage } from "../auth/ForgotPasswordPage";
import { ProfilePage } from "../account/ProfilePage";
import { AppShell } from "../app/AppShell";
import { AdminDashboard } from "../dashboards/AdminDashboard";
import { DirectorDashboard } from "../dashboards/DirectorDashboard";
import { ManagerDashboard } from "../dashboards/ManagerDashboard";
import { TeacherDashboard } from "../dashboards/TeacherDashboard";
import { SystemDashboard } from "../dashboards/SystemDashboard";
import { LoginHistoryPage } from "../features/audit/LoginHistoryPage";
import { ChangeHistoryPage } from "../features/audit/ChangeHistoryPage";
import { UserActivityPage } from "../features/audit/UserActivityPage";
import { AuditLogsPage } from "../features/audit/AuditLogsPage";

const ACCESS_MODEL = {
  roles: ["ADMIN"],
  navigation: [
    {
      section: "Dashboard",
      items: [{ label: "Dashboard", route: "/dashboard", actions: ["VIEW"] }],
    },
    {
      section: "SYSTEM",
      items: [
        {
          label: "Role & Permissions",
          route: "/identity/permissions",
          actions: ["VIEW", "EDIT"],
        },
      ],
    },
    {
      section: "ACCOUNT",
      items: [
        {
          label: "Profile",
          route: "/account/profile",
          actions: ["VIEW", "EDIT"],
        },
      ],
    },
  ],
  dataScope: { DASHBOARD: "ORG_WIDE" },
};

const authFetch = vi.fn(async (input: RequestInfo) => {
  const url = typeof input === "string" ? input : input.url;
  if (url.includes("/access-model")) {
    return { ok: true, json: async () => ACCESS_MODEL } as Response;
  }
  if (url.includes("/me/sessions")) {
    return { ok: true, json: async () => [] } as Response;
  }
  if (url.includes("/api/v1/audit/")) {
    return {
      ok: true,
      json: async () => ({ content: [], page: 0, size: 25, totalElements: 0 }),
    } as Response;
  }
  return { ok: true, json: async () => ({}) } as Response;
});

vi.mock("../auth/useAuth", () => ({
  useAuth: () => ({
    user: { id: "u1", displayName: "Priya Manager", roles: ["MANAGER"] },
    accessToken: "test-access-token",
    initializing: false,
    loginWithPassword: vi.fn(),
    loginWithOtp: vi.fn(),
    logout: vi.fn(),
    authFetch,
  }),
}));

vi.mock("../theme/ThemeModeProvider", () => ({
  useThemeMode: () => ({ mode: "light", toggleMode: vi.fn() }),
}));

function renderWithChrome(ui: ReactElement, mode: ThemeMode) {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <MemoryRouter>{ui}</MemoryRouter>
    </ThemeProvider>,
  );
}

interface PageCase {
  name: string;
  render: () => ReactElement;
  /** Awaited after render, for pages with an async effect that must settle first (RTL's
   * `findBy*` queries wrap their own polling in `act`, unlike a manual `act()` call). */
  settle?: () => Promise<unknown>;
}

const pages: PageCase[] = [
  { name: "SignInPage", render: () => <SignInPage /> },
  { name: "OtpEntryPage", render: () => <OtpEntryPage /> },
  { name: "ForgotPasswordPage", render: () => <ForgotPasswordPage /> },
  {
    name: "ProfilePage",
    render: () => <ProfilePage />,
    settle: () => screen.findByText(/no active sessions/i),
  },
  {
    name: "AppShell",
    render: () => <AppShell />,
    settle: () => screen.findByText(/role & permissions/i),
  },
  { name: "AdminDashboard", render: () => <AdminDashboard /> },
  { name: "DirectorDashboard", render: () => <DirectorDashboard /> },
  { name: "ManagerDashboard", render: () => <ManagerDashboard /> },
  { name: "TeacherDashboard", render: () => <TeacherDashboard /> },
  { name: "SystemDashboard", render: () => <SystemDashboard /> },
  {
    name: "LoginHistoryPage",
    render: () => <LoginHistoryPage />,
    settle: () => screen.findByText(/no rows/i),
  },
  {
    name: "ChangeHistoryPage",
    render: () => <ChangeHistoryPage />,
    settle: () => screen.findByText(/no rows/i),
  },
  {
    name: "UserActivityPage",
    render: () => <UserActivityPage />,
    settle: () => screen.findByText(/no rows/i),
  },
  {
    name: "AuditLogsPage",
    render: () => <AuditLogsPage />,
    settle: () => screen.findByText(/no rows/i),
  },
];

describe.each(["light", "dark"] as const)(
  "Accessibility in %s mode (FR-024/SC-007)",
  (mode) => {
    it.each(pages.map((page) => [page.name, page] as const))(
      "%s has no critical WCAG 2.2 AA violations",
      async (_name, page) => {
        const { container } = renderWithChrome(page.render(), mode);
        if (page.settle) {
          await page.settle();
        }

        const results = await axe(container, {
          runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
        });

        // vitest-axe@0.1.0's `toHaveNoViolations` matcher types don't match this project's
        // Vitest 2 typings, so violations are asserted directly for a readable failure message.
        const summary = results.violations.map(
          (violation) =>
            `${violation.id}: ${violation.help} (${violation.nodes.length} node(s))`,
        );
        expect(summary).toEqual([]);
      },
    );
  },
);
