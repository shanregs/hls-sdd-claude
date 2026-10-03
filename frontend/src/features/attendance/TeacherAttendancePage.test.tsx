import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TeacherAttendancePage } from "./TeacherAttendancePage";
import { currentMonth, dateKey, daysInMonth, todayKey } from "./monthUtils";

const authFetch = vi.fn();
let grantedActions = ["VIEW", "CREATE", "EDIT"];

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch, user: { id: "me", displayName: "Manoj" } }),
}));

vi.mock("../../access-model/useAccessModel", () => ({
  useAccessModel: () => ({
    loading: false,
    accessModel: {
      roles: ["MANAGER"],
      dataScope: {},
      navigation: [
        {
          section: "OPERATIONS",
          items: [
            {
              label: "Teacher Attendance",
              route: "/operations/teacher-attendance",
              actions: grantedActions,
            },
          ],
        },
      ],
    },
  }),
}));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const CODES = [
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
];

const ROLLUP = {
  workingDays: 26,
  daysWorked: 1,
  daysLeave: 0,
  trainingAvailable: 0,
  trainingAttended: 0,
  unmarked: 3,
  weightedTotal: 1,
  locked: false,
  frozen: false,
};

function gridResponse(locked = false, rows = 1) {
  const month = currentMonth();
  const today = todayKey();
  const cells = Array.from({ length: daysInMonth(month) }, (_, i) => {
    const date = dateKey(month, i + 1);
    return {
      date,
      code: null,
      dayValue: null,
      setByKind: null,
      state: date > today ? "FUTURE" : "UNMARKED",
    };
  });
  return {
    month,
    days: cells.length,
    page: 0,
    size: 50,
    totalElements: rows,
    content: Array.from({ length: rows }, (_, i) => ({
      teacherId: `t${i + 1}`,
      name: i === 0 ? "Tara Teacher" : `Teacher ${i + 1}`,
      status: "ACTIVE",
      school: { id: "s1", name: "Demo School One" },
      manager: null,
      locked,
      rollup: ROLLUP,
      cells,
    })),
  };
}

function monthView() {
  const month = currentMonth();
  const today = todayKey();
  return {
    teacherId: "t1",
    name: "Tara Teacher",
    month,
    locked: false,
    state: "OPEN",
    rollup: ROLLUP,
    days: Array.from({ length: daysInMonth(month) }, (_, i) => {
      const date = dateKey(month, i + 1);
      return {
        date,
        state: date > today ? "FUTURE" : "UNMARKED",
        mark: null,
        editableBy: date > today ? "NONE" : "SUPERVISOR",
      };
    }),
  };
}

function mockApi(grid: unknown, writeResponse: Response = json({})) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method) return writeResponse;
    if (url.startsWith("/api/v1/attendance/teacher-grid")) return json(grid);
    if (url.startsWith("/api/v1/attendance/status-codes")) return json(CODES);
    if (url.startsWith("/api/v1/attendance/teachers/"))
      return json(monthView());
    return json({});
  });
}

describe("TeacherAttendancePage (User Story 2)", () => {
  beforeEach(() => {
    authFetch.mockReset();
    grantedActions = ["VIEW", "CREATE", "EDIT"];
  });

  it("shows one column per day of the month and the rows returned", async () => {
    mockApi(gridResponse());
    render(<TeacherAttendancePage />);

    const table = await screen.findByRole("table", { name: "Attendance grid" });
    const headers = within(table).getAllByRole("columnheader");
    // Teacher + one per day + Worked, Unmarked, Total.
    expect(headers).toHaveLength(daysInMonth(currentMonth()) + 4);
    expect(within(table).getByText("Tara Teacher")).toBeInTheDocument();
  });

  it("marks a day through the dialog and reloads the grid", async () => {
    mockApi(gridResponse(), json({ date: "x" }));
    render(<TeacherAttendancePage />);
    const user = userEvent.setup();

    const cells = await screen.findAllByRole("button", {
      name: /Tara Teacher, .*: Unmarked/,
    });
    await user.click(cells[0]);
    await user.click(await screen.findByRole("button", { name: "Save" }));

    const put = authFetch.mock.calls.find(([, init]) => init?.method === "PUT");
    expect(put).toBeDefined();
    expect(String(put![0])).toMatch(
      /\/api\/v1\/attendance\/teachers\/t1\/marks\//,
    );
  });

  it("shows a locked row as locked with no editable cells", async () => {
    mockApi(gridResponse(true));
    render(<TeacherAttendancePage />);

    await screen.findByRole("table", { name: "Attendance grid" });
    expect(screen.getByText(/Locked/)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Tara Teacher, .*Unmarked/ }),
    ).toBeNull();
  });

  it("shows an empty state instead of an error when nobody is placed", async () => {
    mockApi({ ...gridResponse(false, 0), content: [], totalElements: 0 });
    render(<TeacherAttendancePage />);

    expect(
      await screen.findByText(/No teachers are placed in/),
    ).toBeInTheDocument();
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("shows the server error when the grid cannot load", async () => {
    authFetch.mockImplementation(async () =>
      json({ reason: "Not authorized" }, 403),
    );
    render(<TeacherAttendancePage />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Not authorized",
    );
  });

  it("offers no editing for a view-only grant", async () => {
    grantedActions = ["VIEW"];
    mockApi(gridResponse());
    render(<TeacherAttendancePage />);

    await screen.findByRole("table", { name: "Attendance grid" });
    expect(
      screen.queryByRole("button", { name: /Tara Teacher, .*Unmarked/ }),
    ).toBeNull();
  });
});
