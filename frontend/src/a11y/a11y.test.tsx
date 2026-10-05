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
import { ApiAccessPage } from "../features/audit/ApiAccessPage";
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
import { RolePermissionsGrid } from "../features/permissions/RolePermissionsGrid";
import { SessionsPage } from "../account/SessionsPage";
import { AllSessionsPage } from "../features/sessions/AllSessionsPage";
import { NotificationsPage } from "../features/notifications/NotificationsPage";
import { ContractsListPage } from "../features/schoolbilling/ContractsListPage";
import { SchoolContractPage } from "../features/schoolbilling/SchoolContractPage";
import { MouFormDialog } from "../features/schoolbilling/MouFormDialog";
import { MapTeachersDialog } from "../features/schoolbilling/MapTeachersDialog";
import { DrivesPage } from "../features/recruitment/DrivesPage";
import { DriveDetailPage } from "../features/recruitment/DriveDetailPage";
import { CandidatesPage } from "../features/recruitment/CandidatesPage";
import { OffersPage } from "../features/recruitment/OffersPage";
import { InductionPage } from "../features/recruitment/InductionPage";
import { RecruitmentDashboard } from "../features/recruitment/RecruitmentDashboard";
import { DriveDialog } from "../features/recruitment/DriveDialog";
import { CollegeDialog } from "../features/recruitment/CollegeDialog";
import {
  AddCandidateDialog,
  AssessmentDialog,
  OutcomeDialog,
} from "../features/recruitment/CandidateDialogs";
import { OfferDialog } from "../features/recruitment/OfferDialog";
import { OfferAcceptDialog } from "../features/recruitment/OfferAcceptDialog";
import { ReasonDialog } from "../features/recruitment/ReasonDialog";
import {
  BatchDialog,
  SignOffDialog,
} from "../features/recruitment/InductionDialogs";
import { SettingsPage } from "../account/SettingsPage";
import { TeacherAttendancePage } from "../features/attendance/TeacherAttendancePage";
import { AttendanceGridPage } from "../features/attendance/AttendanceGridPage";
import { ApplyLeavePage } from "../features/leave/ApplyLeavePage";
import { MyLeaveHistoryPage } from "../features/leave/MyLeaveHistoryPage";
import { LeaveManagementPage } from "../features/leave/LeaveManagementPage";
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
      section: "RECRUITMENT",
      items: [
        {
          label: "Campus Drives",
          route: "/recruitment/drives",
          actions: ["VIEW", "CREATE", "EDIT"],
        },
        {
          label: "Candidates",
          route: "/recruitment/candidates",
          actions: ["VIEW", "CREATE", "EDIT"],
        },
        {
          label: "Offers",
          route: "/recruitment/offers",
          actions: ["VIEW", "CREATE", "EDIT"],
        },
        {
          label: "Induction",
          route: "/recruitment/induction",
          actions: ["VIEW", "CREATE", "EDIT"],
        },
        {
          label: "Recruitment Dashboard",
          route: "/recruitment/dashboard",
          actions: ["VIEW"],
        },
      ],
    },
    {
      section: "Dashboard",
      items: [{ label: "Dashboard", route: "/dashboard", actions: ["VIEW"] }],
    },
    {
      section: "ACCOUNT",
      items: [
        {
          label: "Sessions",
          route: "/account/sessions",
          actions: ["VIEW", "DELETE"],
        },
        {
          label: "Notifications",
          route: "/account/notifications",
          actions: ["VIEW", "DELETE"],
        },
        {
          label: "All Sessions",
          route: "/identity/sessions",
          actions: ["VIEW", "DELETE"],
        },
      ],
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

const CONTRACT_SAMPLE = {
  id: "c1",
  schoolId: "s1",
  state: "ACTIVE",
  status: "ACTIVE",
  salaryMode: "PER_TEACHER",
  teacherCount: 2,
  rate: null,
  signedOn: "2026-09-20",
  startsOn: "2026-10-01",
  endsOn: null,
  version: 0,
  positions: [
    {
      id: "p1",
      number: 1,
      title: "Maths PGT",
      salary: "20000.00",
      teacherId: "t1",
      teacherName: "Tara Teacher",
    },
    {
      id: "p2",
      number: 2,
      title: null,
      salary: "18000.00",
      teacherId: null,
      teacherName: null,
    },
  ],
  signatories: [
    {
      id: "g1",
      party: "SCHOOL",
      name: "R. Kumar",
      designation: "Principal",
      userId: null,
    },
    {
      id: "g2",
      party: "HLS",
      name: "Manoj Manager",
      designation: "Zone Manager",
      userId: "u1",
    },
  ],
};

const RECRUIT_DRIVE = {
  id: "d1",
  college: { id: "c1", name: "Demo College", city: "Chennai" },
  season: "2026-27",
  venue: "Main hall",
  status: "HELD",
  cancelReason: null,
  dates: ["2026-10-02"],
  interviewers: [{ userId: "u1", name: "Priya Manager" }],
  scheduledBy: "u1",
  candidates: 2,
  outcomes: { SELECTED: 1, PENDING: 1 },
  heldNoCandidates: false,
  version: 0,
};

const RECRUIT_COLLEGE = {
  id: "c1",
  name: "Demo College",
  city: "Chennai",
  placementOfficer: { name: "Ms Placement", phone: "9444400001" },
  principal: { name: "Dr Principal" },
  drives: 1,
  version: 0,
};

const RECRUIT_CANDIDATES = [
  {
    id: "k1",
    driveId: "d1",
    name: "Asha Student",
    phone: "9555500001",
    email: null,
    degree: "B.A.",
    year: "Final",
    notes: null,
    outcome: "SELECTED",
    outcomeAt: null,
    teacherId: null,
    assessment: null,
    version: 0,
  },
  {
    id: "k2",
    driveId: "d1",
    name: "Ravi Student",
    phone: "9555500002",
    email: null,
    degree: null,
    year: null,
    notes: null,
    outcome: null,
    outcomeAt: null,
    teacherId: null,
    assessment: null,
    version: 0,
  },
];

const RECRUIT_OFFER = {
  id: "o1",
  candidateId: "k1",
  candidateName: "Asha Student",
  college: "Demo College",
  role: "Trainee / English Trainer",
  monthlySalary: "16000.00",
  allowances: null,
  terms: null,
  expectedJoining: null,
  offerDate: "2026-10-01",
  responseDeadline: "2026-10-20",
  status: "ISSUED",
  supersedesId: null,
  declineReason: null,
  issuedByName: "Divya Director",
  issuedAt: null,
  decidedAt: null,
  teacherId: null,
  version: 0,
};

const RECRUIT_COUNTS = {
  drivesScheduled: 1,
  drivesHeld: 1,
  interviewed: 2,
  assessed: 1,
  selected: 1,
  offered: 1,
  accepted: 0,
  inducted: 0,
  readyToDeploy: 0,
  placed: 0,
  active: 0,
  joiningRatio: "0.00",
};

function recruitmentResponse(url: string): unknown {
  if (url.includes("/api/v1/recruitment/candidates")) return RECRUIT_CANDIDATES;
  if (url.includes("/api/v1/recruitment/offers")) return [RECRUIT_OFFER];
  if (url.includes("/api/v1/recruitment/colleges")) return [RECRUIT_COLLEGE];
  if (url.includes("/api/v1/recruitment/interviewers")) {
    return [{ userId: "u1", name: "Priya Manager" }];
  }
  if (url.includes("/api/v1/recruitment/dashboard")) {
    return {
      colleges: [
        {
          collegeId: "c1",
          name: "Demo College",
          city: "Chennai",
          counts: RECRUIT_COUNTS,
        },
      ],
      totals: RECRUIT_COUNTS,
      readyToDeployTotal: 0,
    };
  }
  if (/\/api\/v1\/recruitment\/drives\/[^/?]*$/.test(url)) return RECRUIT_DRIVE;
  if (url.includes("/api/v1/recruitment/drives")) return [RECRUIT_DRIVE];
  if (url.includes("/roster")) {
    return [
      {
        enrolmentId: "e1",
        teacherId: "t1",
        name: "Ready Rani",
        result: null,
        remarks: null,
        followUp: null,
        days: [
          { date: "2026-10-02", status: "PRESENT", dayValue: 1, reason: null },
        ],
      },
    ];
  }
  if (url.includes("/api/v1/induction/batches")) {
    return [
      {
        id: "b1",
        name: "October Induction",
        startsOn: "2026-10-01",
        endsOn: "2026-10-31",
        trainer: "Trainer Tina",
        venueType: "PHYSICAL",
        venue: "Head office",
        seatLimit: 10,
        enrolled: 1,
        status: "RUNNING",
        version: 0,
      },
    ];
  }
  if (url.includes("/api/v1/induction/ready-to-deploy")) {
    return [{ teacherId: "t2", name: "Ready Rani", status: "ACTIVE" }];
  }
  if (url.includes("/api/v1/induction/to-be-enrolled")) return [];
  return undefined;
}

const authFetch = vi.fn(async (input: RequestInfo) => {
  const url = typeof input === "string" ? input : input.url;
  if (url.includes("/access-model")) {
    return { ok: true, json: async () => ACCESS_MODEL } as Response;
  }
  const recruitment = recruitmentResponse(url);
  if (recruitment !== undefined) {
    return { ok: true, json: async () => recruitment } as Response;
  }
  const aSession = {
    id: "s1",
    deviceDescription:
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/154.0.0.0 Safari/537.36",
    signedInAt: "2026-10-04T08:30:00Z",
    lastActivityAt: "2026-10-04T09:00:00Z",
    current: true,
    clientType: "WEB",
    appVersion: null,
  };
  if (url === "/api/v1/me/leave/types") {
    return {
      ok: true,
      json: async () => [
        { id: "t1", code: "CASUAL", name: "Casual" },
        { id: "t2", code: "SICK", name: "Sick" },
      ],
    } as Response;
  }
  if (url.startsWith("/api/v1/leave?")) {
    return {
      ok: true,
      json: async () => ({
        content: [
          {
            id: "r1",
            teacherId: "t1",
            teacherName: "Tara Teacher",
            schoolId: "s1",
            schoolName: "Demo School One",
            leaveType: "Casual",
            firstDate: "2026-01-14",
            lastDate: "2026-01-16",
            halfDayStart: false,
            halfDayEnd: true,
            workingDays: 2.5,
            reason: "Pongal travel",
            status: "PENDING",
            decidedByName: null,
            decidedAt: null,
            decisionNote: null,
            cancelledBy: null,
            createdAt: "2026-01-02T10:00:00Z",
            version: 0,
            allowedActions: ["APPROVE", "REJECT"],
          },
        ],
        page: 0,
        size: 25,
        totalElements: 1,
        pendingCount: 1,
      }),
    } as Response;
  }
  if (url.startsWith("/api/v1/me/leave?")) {
    const leave = (id: string, status: string, extra = {}) => ({
      id,
      teacherId: "t1",
      teacherName: "Tara Teacher",
      schoolId: "s1",
      schoolName: "Demo School One",
      leaveType: "Casual",
      firstDate: "2026-01-14",
      lastDate: "2026-01-16",
      halfDayStart: false,
      halfDayEnd: false,
      workingDays: 3,
      reason: "Pongal travel",
      status,
      decidedByName: null,
      decidedAt: null,
      decisionNote: null,
      cancelledBy: null,
      createdAt: "2026-01-02T10:00:00Z",
      version: 0,
      allowedActions: [],
      ...extra,
    });
    return {
      ok: true,
      json: async () => ({
        content: [
          leave("r1", "PENDING", { allowedActions: ["CANCEL"] }),
          leave("r2", "REJECTED", {
            decidedByName: "Manoj Manager",
            decidedAt: "2026-01-03T10:00:00Z",
            decisionNote: "Exams that week",
          }),
        ],
        page: 0,
        size: 25,
        totalElements: 2,
      }),
    } as Response;
  }
  if (url.includes("/me/notifications/unread-count")) {
    return { ok: true, json: async () => ({ unread: 1 }) } as Response;
  }
  if (url.includes("/me/notifications")) {
    return {
      ok: true,
      json: async () => ({
        content: [
          {
            id: "n1",
            type: "LEAVE_DECIDED",
            title: "Your leave was approved",
            message: "Your leave 12/10/2026 to 13/10/2026 was approved.",
            link: "/leave/history",
            channel: "IN_APP",
            read: false,
            createdAt: "2026-10-05T08:30:00Z",
            updatedAt: "2026-10-05T08:30:00Z",
          },
        ],
        page: 0,
        size: 25,
        totalElements: 1,
        unread: 1,
      }),
    } as Response;
  }
  if (url.includes("/me/sessions")) {
    return { ok: true, json: async () => [aSession] } as Response;
  }
  if (url.startsWith("/api/v1/admin/sessions")) {
    return {
      ok: true,
      json: async () => ({
        content: [
          {
            ...aSession,
            userId: "u1",
            userName: "Priya Manager",
            userPhone: "9800000003",
          },
        ],
        page: 0,
        size: 25,
        totalElements: 1,
      }),
    } as Response;
  }
  if (url === "/api/v1/identity/permission-matrix") {
    const roles = ["ADMIN", "DIRECTOR", "MANAGER", "TEACHER", "SYSTEM"];
    return {
      ok: true,
      json: async () => ({
        entries: [
          {
            role: "ADMIN",
            module: "USER_MANAGEMENT",
            action: "VIEW",
            granted: true,
          },
          {
            role: "ADMIN",
            module: "USER_MANAGEMENT",
            action: "EDIT",
            granted: true,
          },
          {
            role: "MANAGER",
            module: "DASHBOARD",
            action: "VIEW",
            granted: true,
          },
        ],
        modules: [
          {
            module: "DASHBOARD",
            eligible: Object.fromEntries(roles.map((r) => [r, ["VIEW"]])),
          },
          {
            module: "USER_MANAGEMENT",
            eligible: Object.fromEntries(
              roles.map((r) => [r, ["VIEW", "CREATE", "EDIT"]]),
            ),
          },
          {
            module: "IDENTITY_PERMISSIONS",
            eligible: Object.fromEntries(
              roles.map((r) => [
                r,
                ["ADMIN", "DIRECTOR", "SYSTEM"].includes(r)
                  ? ["VIEW", "EDIT"]
                  : [],
              ]),
            ),
          },
        ],
      }),
    } as Response;
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
  if (url.includes("/api/v1/school-contracts/signatory-candidates")) {
    return {
      ok: true,
      json: async () => [
        { userId: "u1", name: "Manoj Manager", designation: "Zone Manager" },
        { userId: "u2", name: "Divya Director", designation: "Director" },
      ],
    } as Response;
  }
  if (url.includes("/api/v1/school-contracts/schools/")) {
    return {
      ok: true,
      json: async () => ({
        schoolId: "s1",
        schoolName: "Demo School One",
        zoneManagerName: "Manoj Manager",
        contracts: [CONTRACT_SAMPLE],
        unmappedTeachers: [{ teacherId: "t9", teacherName: "Meena Selvi" }],
      }),
    } as Response;
  }
  if (url.includes("/api/v1/school-contracts")) {
    return {
      ok: true,
      json: async () => ({
        content: [
          {
            schoolId: "s1",
            schoolName: "Demo School One",
            zoneManagerName: "Manoj Manager",
            status: "ACTIVE",
            contractId: "c1",
            startsOn: "2026-10-01",
            endsOn: null,
            teacherCount: 4,
            filled: 3,
            vacant: 1,
            unmapped: 1,
            salaryMode: "SAME_FOR_ALL",
            signedOn: "2026-09-20",
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
  {
    name: "ContractsListPage",
    render: () => <ContractsListPage />,
    settle: () => screen.findByText("Demo School One"),
  },
  {
    name: "SchoolContractPage",
    render: () => <SchoolContractPage />,
    settle: () => screen.findByRole("region", { name: "Contract in effect" }),
  },
  {
    name: "MouFormDialog",
    render: () => (
      <MouFormDialog
        schoolId="s1"
        schoolName="Demo School One"
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />
    ),
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
  },
  {
    name: "MapTeachersDialog",
    render: () => (
      <MapTeachersDialog
        contract={CONTRACT_SAMPLE as never}
        teachers={[
          { teacherId: "t9", teacherName: "Meena Selvi", from: "not mapped" },
        ]}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />
    ),
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
  },
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
    name: "ApiAccessPage",
    render: () => <ApiAccessPage />,
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
    name: "LeaveManagementPage",
    render: () => <LeaveManagementPage />,
    settle: () => screen.findByRole("table", { name: "Leave requests" }),
  },
  {
    name: "MyLeaveHistoryPage",
    render: () => <MyLeaveHistoryPage />,
    settle: () => screen.findByRole("table", { name: "My leave requests" }),
  },
  {
    name: "ApplyLeavePage",
    render: () => <ApplyLeavePage />,
    settle: () => screen.findByLabelText(/Leave type/),
  },
  {
    name: "SettingsPage",
    render: () => <SettingsPage />,
    settle: () => screen.findByRole("button", { name: "Edit" }),
  },
  {
    name: "RolePermissionsGrid",
    render: () => <RolePermissionsGrid />,
    settle: () =>
      screen.findByRole("table", { name: "Role and permission matrix" }),
  },
  {
    name: "SessionsPage",
    render: () => <SessionsPage />,
    settle: () => screen.findByRole("table", { name: "Sessions" }),
  },
  {
    name: "NotificationsPage",
    render: () => <NotificationsPage />,
    settle: () => screen.findByText("Your leave was approved"),
  },
  {
    name: "AllSessionsPage",
    render: () => <AllSessionsPage />,
    settle: () => screen.findByText("Priya Manager"),
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
    name: "DrivesPage",
    render: () => <DrivesPage />,
    settle: () => screen.findAllByText("Demo College, Chennai"),
  },
  {
    name: "DriveDetailPage",
    render: () => <DriveDetailPage />,
    settle: () => screen.findByRole("table", { name: "Candidates" }),
  },
  {
    name: "CandidatesPage",
    render: () => <CandidatesPage />,
    settle: () => screen.findByRole("table", { name: "Candidates" }),
  },
  {
    name: "OffersPage",
    render: () => <OffersPage />,
    settle: () => screen.findByRole("table", { name: "Offers" }),
  },
  {
    name: "InductionPage",
    render: () => <InductionPage />,
    settle: () => screen.findByRole("table", { name: "Roster" }),
  },
  {
    name: "RecruitmentDashboard",
    render: () => <RecruitmentDashboard />,
    settle: () =>
      screen.findByRole("table", { name: "Recruitment funnel by college" }),
  },
  {
    name: "DriveDialog",
    render: () => <DriveDialog onClose={vi.fn()} onSaved={vi.fn()} />,
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
  },
  {
    name: "CollegeDialog",
    render: () => <CollegeDialog onClose={vi.fn()} onSaved={vi.fn()} />,
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
  },
  {
    name: "AddCandidateDialog",
    render: () => (
      <AddCandidateDialog driveId="d1" onClose={vi.fn()} onSaved={vi.fn()} />
    ),
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
  },
  {
    name: "OutcomeDialog",
    render: () => (
      <OutcomeDialog
        candidate={RECRUIT_CANDIDATES[0] as never}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />
    ),
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
  },
  {
    name: "AssessmentDialog",
    render: () => (
      <AssessmentDialog
        candidate={RECRUIT_CANDIDATES[0] as never}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />
    ),
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
  },
  {
    name: "OfferDialog",
    render: () => (
      <OfferDialog mode="create" onClose={vi.fn()} onSaved={vi.fn()} />
    ),
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
  },
  {
    name: "OfferAcceptDialog",
    render: () => (
      <OfferAcceptDialog
        offer={RECRUIT_OFFER as never}
        onClose={vi.fn()}
        onAccepted={vi.fn()}
      />
    ),
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
  },
  {
    name: "ReasonDialog",
    render: () => (
      <ReasonDialog
        title="Decline the offer?"
        label="Reason"
        confirmLabel="Decline"
        onConfirm={async () => null}
        onCancel={vi.fn()}
      />
    ),
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
  },
  {
    name: "BatchDialog",
    render: () => <BatchDialog onClose={vi.fn()} onSaved={vi.fn()} />,
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
  },
  {
    name: "SignOffDialog",
    render: () => (
      <SignOffDialog
        row={{
          enrolmentId: "e1",
          teacherId: "t1",
          name: "Ready Rani",
          result: null,
          remarks: null,
          followUp: null,
          days: [],
        }}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />
    ),
    settle: () => screen.findByRole("dialog"),
    axeTarget: () => document.body,
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
