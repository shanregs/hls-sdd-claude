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
import { UserManagementPage } from "../features/users/UserManagementPage";
import { CreateUserDialog } from "../features/users/CreateUserDialog";
import { EditRolesDialog } from "../features/users/EditRolesDialog";
import { ResetPasswordDialog } from "../features/users/ResetPasswordDialog";
import { ZonesPage } from "../features/zones/ZonesPage";
import { ZoneDialog } from "../features/zones/ZoneDialog";
import { PlaceDialog } from "../features/zones/PlaceDialog";
import { BulkImportPlacesDialog } from "../features/zones/BulkImportPlacesDialog";
import { SchoolsPage } from "../features/schools/SchoolsPage";
import { SchoolDialog } from "../features/schools/SchoolDialog";
import { ManagersPage } from "../features/managers/ManagersPage";
import { TeachersPage } from "../features/teachers/TeachersPage";
import { TeacherDialog } from "../features/teachers/TeacherDialog";
import { SalaryDialog } from "../features/teachers/SalaryDialog";
import { MyTeacherProfile } from "../features/teachers/MyTeacherProfile";
import { AttendanceSetupPage } from "../features/attendance/AttendanceSetupPage";
import { AttendanceHistoryPage } from "../features/attendance/AttendanceHistoryPage";
import { HolidayCalendarPage } from "../features/attendance/HolidayCalendarPage";
import { LockMonthDialog } from "../features/attendance/LockMonthDialog";
import { ReopenDialog } from "../features/attendance/ReopenDialog";
import { MyAttendancePage } from "../features/attendance/MyAttendancePage";
import { MarkDialog } from "../features/attendance/MarkDialog";
import { SettingsPage } from "../account/SettingsPage";
import { TeacherAttendancePage } from "../features/attendance/TeacherAttendancePage";
import { AttendanceGridPage } from "../features/attendance/AttendanceGridPage";
import { ManagerDashboard as MasterDataManagerDashboard } from "../dashboards/ManagerDashboard";

const ACCESS_MODEL = {
  roles: ["ADMIN"],
  navigation: [
    {
      section: "OPERATIONS",
      items: [
        {
          label: "Attendance Setup",
          route: "/master-data/attendance-setup",
          actions: ["VIEW", "EDIT"],
        },
        {
          label: "Attendance",
          route: "/operations/attendance",
          actions: ["VIEW", "CREATE", "EDIT", "DELETE"],
        },
        {
          label: "Teacher Attendance",
          route: "/operations/teacher-attendance",
          actions: ["VIEW", "CREATE", "EDIT"],
        },
      ],
    },
    {
      section: "Dashboard",
      items: [{ label: "Dashboard", route: "/dashboard", actions: ["VIEW"] }],
    },
    {
      section: "SYSTEM",
      items: [
        {
          label: "User Management",
          route: "/identity/users",
          actions: ["VIEW", "CREATE", "EDIT"],
        },
        {
          label: "Role & Permissions",
          route: "/identity/permissions",
          actions: ["VIEW", "EDIT"],
        },
      ],
    },
    {
      section: "MASTER DATA",
      items: [
        {
          label: "Zones",
          route: "/master-data/zones",
          actions: ["VIEW", "CREATE", "EDIT", "DELETE"],
        },
        {
          label: "Schools",
          route: "/master-data/schools",
          actions: ["VIEW", "CREATE", "EDIT"],
        },
        {
          label: "Managers",
          route: "/master-data/managers",
          actions: ["VIEW", "CREATE", "EDIT"],
        },
        {
          label: "Teachers",
          route: "/master-data/teachers",
          actions: ["VIEW", "CREATE", "EDIT"],
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
  dataScope: { DASHBOARD: "ORG_WIDE", TEACHER_SALARY: "ORG_WIDE" },
};

const TEACHER = {
  id: "t1",
  name: "Tara Teacher",
  phone: "9800000004",
  email: null,
  address: null,
  status: "ACTIVE",
  statusEffectiveOn: "2026-04-01",
  allowedNextStatuses: ["ON_LEAVE", "EXITED"],
  userId: null,
  version: 0,
  school: { id: "s1", name: "St Mary's" },
  manager: null,
  pendingPlacement: null,
  placements: null,
};

const ZONE = {
  id: "z1",
  name: "North Zone",
  version: 0,
  placeCount: 1,
  schoolCount: 1,
};

const authFetch = vi.fn(async (input: RequestInfo) => {
  const url = typeof input === "string" ? input : input.url;
  if (url.includes("/access-model")) {
    return { ok: true, json: async () => ACCESS_MODEL } as Response;
  }
  if (url.includes("/me/sessions")) {
    return { ok: true, json: async () => [] } as Response;
  }
  const emptyPage = { content: [], page: 0, size: 25, totalElements: 0 };
  if (url.startsWith("/api/v1/zones?")) {
    return {
      ok: true,
      json: async () => ({
        ...emptyPage,
        content: [
          {
            id: "z1",
            name: "North Zone",
            version: 0,
            placeCount: 1,
            schoolCount: 1,
            managerCount: 1,
          },
        ],
        totalElements: 1,
      }),
    } as Response;
  }
  if (url.startsWith("/api/v1/schools?")) {
    return {
      ok: true,
      json: async () => ({
        ...emptyPage,
        content: [
          {
            id: "s1",
            name: "St Mary's",
            place: { id: "p1", name: "Madurantakam", pinCode: "603306" },
            zone: { id: "z1", name: "North Zone" },
            address: "1 Main Road",
            contactPerson: null,
            contactPhone: null,
            billingContact: null,
            active: true,
            version: 0,
            manager: null,
            teacherCount: 1,
          },
        ],
        totalElements: 1,
      }),
    } as Response;
  }
  if (url.startsWith("/api/v1/managers?")) {
    return {
      ok: true,
      json: async () => ({
        ...emptyPage,
        content: [
          {
            id: "m1",
            userId: "u1",
            displayName: "Manoj Manager",
            phone: "9800000003",
            active: true,
            version: 0,
            zones: [{ id: "z1", name: "North Zone" }],
            schoolCount: 1,
            teacherCount: 1,
          },
        ],
        totalElements: 1,
      }),
    } as Response;
  }
  if (url.startsWith("/api/v1/teachers?")) {
    return {
      ok: true,
      json: async () => ({
        ...emptyPage,
        content: [TEACHER],
        totalElements: 1,
      }),
    } as Response;
  }
  if (url === "/api/v1/teachers/me") {
    return { ok: true, status: 200, json: async () => TEACHER } as Response;
  }
  if (url.startsWith("/api/v1/me/scope")) {
    return {
      ok: true,
      json: async () => ({
        orgWide: false,
        zoneCount: 1,
        schoolCount: 1,
        zones: [],
      }),
    } as Response;
  }
  if (url.endsWith("/salary")) {
    return {
      ok: true,
      json: async () => ({
        current: { id: "e1", amount: 22000, effectiveOn: "2026-10-01" },
        history: [{ id: "e1", amount: 22000, effectiveOn: "2026-10-01" }],
      }),
    } as Response;
  }
  if (url === "/api/v1/me/profile") {
    return {
      ok: true,
      json: async () => ({
        id: "u1",
        displayName: "Priya Manager",
        phone: "9800000003",
        username: null,
        email: null,
        roles: ["MANAGER"],
      }),
    } as Response;
  }
  if (
    url.startsWith("/api/v1/attendance/teacher-grid") ||
    url.startsWith("/api/v1/attendance/grid")
  ) {
    const cells = Array.from({ length: 31 }, (_, i) => ({
      date: `2026-10-${String(i + 1).padStart(2, "0")}`,
      code: i === 0 ? "P" : null,
      dayValue: i === 0 ? 1 : null,
      setByKind: i === 0 ? "SUPERVISOR" : null,
      state: i === 0 ? "MARKED" : i % 7 === 3 ? "WEEKLY_OFF" : "UNMARKED",
    }));
    return {
      ok: true,
      json: async () => ({
        month: "2026-10",
        days: 31,
        page: 0,
        size: 50,
        totalElements: 1,
        content: [
          {
            teacherId: "t1",
            name: "Tara Teacher",
            status: "ACTIVE",
            school: { id: "s1", name: "St Mary's" },
            manager: null,
            locked: false,
            rollup: {
              workingDays: 27,
              daysWorked: 1,
              daysLeave: 0,
              trainingAvailable: 0,
              trainingAttended: 0,
              unmarked: 26,
              weightedTotal: 1,
              locked: false,
              frozen: false,
            },
            cells,
          },
        ],
      }),
    } as Response;
  }
  if (url.startsWith("/api/v1/attendance/me")) {
    const days = Array.from({ length: 31 }, (_, i) => {
      const date = `2026-10-${String(i + 1).padStart(2, "0")}`;
      return {
        date,
        state: i === 0 ? "MARKED" : "UNMARKED",
        mark:
          i === 0
            ? {
                date,
                code: "P",
                codeName: "Present",
                category: "WORKED",
                dayValue: 1,
                schoolId: "s1",
                schoolName: "St Mary's",
                setByKind: "SUPERVISOR",
                setByUserId: "u2",
                setByName: "Manoj",
                setAt: "2026-10-01T05:00:00Z",
                note: null,
                version: 0,
              }
            : null,
        editableBy: "NONE",
      };
    });
    return {
      ok: true,
      json: async () => ({
        teacherId: "t1",
        name: "Tara",
        month: "2026-10",
        locked: false,
        state: "OPEN",
        rollup: {
          workingDays: 27,
          daysWorked: 1,
          daysLeave: 0,
          trainingAvailable: 0,
          trainingAttended: 0,
          unmarked: 26,
          weightedTotal: 1,
          locked: false,
          frozen: false,
        },
        days,
      }),
    } as Response;
  }
  if (url.startsWith("/api/v1/attendance/status-codes")) {
    return {
      ok: true,
      json: async () => [
        {
          id: "c1",
          shortCode: "P",
          name: "Present",
          category: "WORKED",
          weight: 1,
          active: true,
          system: true,
          inUse: true,
          version: 0,
        },
        {
          id: "c2",
          shortCode: "SICK",
          name: "Sick leave",
          category: "LEAVE",
          weight: 0,
          active: true,
          system: false,
          inUse: false,
          version: 0,
        },
      ],
    } as Response;
  }
  if (url === "/api/v1/attendance/calendar") {
    return {
      ok: true,
      json: async () => ({
        defaultWeeklyOff: ["SUN"],
        defaultVersion: 0,
        schoolOverrides: [
          {
            schoolId: "s1",
            schoolName: "St Mary's",
            weeklyOff: ["SAT", "SUN"],
          },
        ],
        nonWorkingDates: [
          { date: "2026-10-02", description: "Gandhi Jayanti" },
        ],
      }),
    } as Response;
  }
  if (url.includes("/places")) {
    return { ok: true, json: async () => emptyPage } as Response;
  }
  if (url.includes("/api/v1/identity/users")) {
    return {
      ok: true,
      json: async () => ({
        content: [
          {
            id: "u9",
            displayName: "Alice Manager",
            phone: "9800000001",
            username: null,
            email: null,
            roles: ["MANAGER"],
            active: true,
          },
        ],
        page: 0,
        size: 25,
        totalElements: 1,
      }),
    } as Response;
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
  /** Dialogs render in a portal outside the render container, so they are checked on the body. */
  axeTarget?: () => HTMLElement;
}

const SAMPLE_USER = {
  id: "u9",
  displayName: "Alice Manager",
  phone: "9800000001",
  username: null,
  email: null,
  roles: ["MANAGER" as const],
  active: true,
};

const pages: PageCase[] = [
  { name: "SignInPage", render: () => <SignInPage /> },
  { name: "OtpEntryPage", render: () => <OtpEntryPage /> },
  { name: "ForgotPasswordPage", render: () => <ForgotPasswordPage /> },
  {
    name: "ProfilePage",
    render: () => <ProfilePage />,
    settle: () => screen.findByText("Account details"),
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
  {
    name: "UserManagementPage",
    render: () => <UserManagementPage />,
    settle: () => screen.findAllByText("Alice Manager"),
  },
  {
    name: "CreateUserDialog",
    render: () => <CreateUserDialog onClose={vi.fn()} onCreated={vi.fn()} />,
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
  },
  {
    name: "EditRolesDialog",
    render: () => (
      <EditRolesDialog user={SAMPLE_USER} onClose={vi.fn()} onSaved={vi.fn()} />
    ),
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
  },
  {
    name: "ZonesPage",
    render: () => <ZonesPage />,
    settle: () => screen.findAllByText("North Zone"),
  },
  {
    name: "ZoneDialog",
    render: () => <ZoneDialog onClose={vi.fn()} onSaved={vi.fn()} />,
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
  },
  {
    name: "PlaceDialog",
    render: () => (
      <PlaceDialog zoneId="z1" onClose={vi.fn()} onSaved={vi.fn()} />
    ),
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
  },
  {
    name: "BulkImportPlacesDialog",
    render: () => (
      <BulkImportPlacesDialog zone={ZONE} onClose={vi.fn()} onDone={vi.fn()} />
    ),
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
  },
  {
    name: "SchoolsPage",
    render: () => <SchoolsPage />,
    settle: () => screen.findAllByText("St Mary's"),
  },
  {
    name: "SchoolDialog",
    render: () => <SchoolDialog onClose={vi.fn()} onSaved={vi.fn()} />,
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
  },
  {
    name: "ManagersPage",
    render: () => <ManagersPage />,
    settle: () => screen.findAllByText("Manoj Manager"),
  },
  {
    name: "TeachersPage",
    render: () => <TeachersPage />,
    settle: () => screen.findAllByText("Tara Teacher"),
  },
  {
    name: "TeacherDialog",
    render: () => <TeacherDialog onClose={vi.fn()} onSaved={vi.fn()} />,
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
  },
  {
    name: "SalaryDialog",
    render: () => <SalaryDialog teacher={TEACHER as never} onClose={vi.fn()} />,
    settle: () => screen.findByText(/current:/i),
    axeTarget: () => document.body,
  },
  {
    name: "MyTeacherProfile",
    render: () => <MyTeacherProfile />,
    settle: () => screen.findByText("Tara Teacher"),
  },
  {
    name: "AttendanceSetupPage",
    render: () => <AttendanceSetupPage />,
    settle: () => screen.findByText("Present"),
  },
  {
    name: "HolidayCalendarPage",
    render: () => <HolidayCalendarPage />,
    settle: () => screen.findByText("Gandhi Jayanti"),
  },
  {
    name: "MyAttendancePage",
    render: () => <MyAttendancePage />,
    settle: () => screen.findByRole("table", { name: "Attendance calendar" }),
  },
  {
    name: "AttendanceHistoryPage",
    render: () => <AttendanceHistoryPage />,
    settle: () => screen.findByRole("table", { name: "Attendance calendar" }),
  },
  {
    name: "TeacherAttendancePage",
    render: () => <TeacherAttendancePage />,
    settle: () => screen.findByRole("table", { name: "Attendance grid" }),
  },
  {
    name: "AttendanceGridPage",
    render: () => <AttendanceGridPage />,
    settle: () => screen.findByRole("table", { name: "Attendance grid" }),
  },
  {
    name: "SettingsPage",
    render: () => <SettingsPage />,
    settle: async () => {
      await screen.findByText("Change password", { selector: "h2" });
      await screen.findByText(/no active sessions/i);
    },
  },
  {
    name: "MarkDialog",
    render: () => (
      <MarkDialog
        date="2026-10-04"
        existing={null}
        onSave={vi.fn()}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />
    ),
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
  },
  {
    name: "LockMonthDialog",
    render: () => (
      <LockMonthDialog month="2026-09" onClose={vi.fn()} onLocked={vi.fn()} />
    ),
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
  },
  {
    name: "ReopenDialog",
    render: () => (
      <ReopenDialog
        teacherId="t1"
        teacherName="Tara Teacher"
        month="2026-09"
        onClose={vi.fn()}
        onReopened={vi.fn()}
      />
    ),
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
  },
  {
    name: "Manager dashboard with assigned counts",
    render: () => <MasterDataManagerDashboard />,
    settle: () => screen.findByText("My assigned zones"),
  },
  {
    name: "ResetPasswordDialog",
    render: () => (
      <ResetPasswordDialog
        user={SAMPLE_USER}
        isSelf
        onClose={vi.fn()}
        onDone={vi.fn()}
      />
    ),
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
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

        const results = await axe(
          page.axeTarget ? page.axeTarget() : container,
          {
            runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
          },
        );

        // vitest-axe@0.1.0's `toHaveNoViolations` matcher types don't match this project's
        // Vitest 2 typings, so violations are asserted directly for a readable failure message.
        const summary = results.violations.map(
          (violation) =>
            `${violation.id}: ${violation.help} (${violation.nodes.length} node(s))`,
        );
        expect(summary).toEqual([]);
      },
      // axe on the larger bordered tables is slow in jsdom; the default 5s is too tight under load.
      30_000,
    );
  },
);
