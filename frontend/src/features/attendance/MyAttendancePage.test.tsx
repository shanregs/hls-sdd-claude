import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MyAttendancePage } from "./MyAttendancePage";
import { currentMonth, daysInMonth, dateKey, todayKey } from "./monthUtils";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch, user: { id: "me", displayName: "Tara" } }),
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
  {
    id: "c2",
    shortCode: "L",
    name: "Leave",
    category: "LEAVE",
    weight: 0,
    active: true,
    system: true,
    inUse: false,
    version: 0,
  },
];

const ROLLUP = {
  workingDays: 26,
  daysWorked: 3,
  daysLeave: 0,
  trainingAvailable: 0,
  trainingAttended: 0,
  unmarked: 5,
  weightedTotal: 3,
  locked: false,
  frozen: false,
};

function mark(date: string, kind: "SELF" | "SUPERVISOR") {
  return {
    date,
    code: "P",
    codeName: "Present",
    category: "WORKED",
    dayValue: 1,
    schoolId: "s1",
    schoolName: "Demo School One",
    setByKind: kind,
    setByUserId: "u1",
    setByName: kind === "SUPERVISOR" ? "Manoj" : "Tara",
    setAt: "2026-10-04T05:00:00Z",
    note: null,
    version: 0,
  };
}

/** The current month with today editable, the day before set by a supervisor, and the rest quiet. */
function monthView(locked = false) {
  const month = currentMonth();
  const today = todayKey();
  const days = Array.from({ length: daysInMonth(month) }, (_, i) => {
    const date = dateKey(month, i + 1);
    return {
      date,
      state: date > today ? "FUTURE" : "UNMARKED",
      mark: null as ReturnType<typeof mark> | null,
      editableBy: !locked && date === today ? "SELF" : "NONE",
    };
  });
  const yesterday = days.find((d) => d.date < today);
  if (yesterday) {
    yesterday.state = "MARKED";
    yesterday.mark = mark(yesterday.date, "SUPERVISOR");
  }
  return {
    teacherId: "t1",
    name: "Tara",
    month,
    locked,
    state: locked ? "LOCKED" : "OPEN",
    rollup: locked ? { ...ROLLUP, locked: true, frozen: true } : ROLLUP,
    days,
  };
}

function mockApi(
  view: unknown,
  saveResponse: Response = json(mark(todayKey(), "SELF")),
) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method === "PUT") return saveResponse;
    if (url.startsWith("/api/v1/attendance/status-codes")) return json(CODES);
    if (url.startsWith("/api/v1/attendance/me")) {
      return view === null
        ? json({ reason: "Your profile has not been set up yet." }, 404)
        : json(view);
    }
    return json({});
  });
}

describe("MyAttendancePage (User Story 1)", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("shows the month calendar and the rollup", async () => {
    mockApi(monthView());
    render(<MyAttendancePage />);

    expect(
      await screen.findByRole("table", { name: "Attendance calendar" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Working days")).toBeInTheDocument();
    expect(screen.getByText("Weighted total")).toBeInTheDocument();
  });

  it("marks today as a whole day present", async () => {
    mockApi(monthView());
    render(<MyAttendancePage />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Mark today" }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    const put = authFetch.mock.calls.find(([, init]) => init?.method === "PUT");
    expect(put).toBeDefined();
    expect(String(put![0])).toContain(
      `/api/v1/attendance/me/marks/${todayKey()}`,
    );
    expect(JSON.parse(put![1].body)).toMatchObject({
      statusCode: "P",
      dayValue: 1,
    });
  });

  it("shows a supervisor-set day with who set it and no way to edit it", async () => {
    mockApi(monthView());
    render(<MyAttendancePage />);

    await screen.findByRole("table", { name: "Attendance calendar" });
    expect(screen.getByText("by Manoj")).toBeInTheDocument();
    const group = screen.getByRole("group", { name: /set by Manoj/ });
    expect(group).toBeInTheDocument();
  });

  it("shows the server refusal inside the dialog", async () => {
    mockApi(
      monthView(),
      json(
        { reason: "This day was set by your Manager. Ask them to correct it." },
        409,
      ),
    );
    render(<MyAttendancePage />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Mark today" }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "set by your Manager",
    );
  });

  it("shows a friendly message when the user has no linked teacher record", async () => {
    mockApi(null);
    render(<MyAttendancePage />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Your profile has not been set up yet.",
    );
    expect(screen.queryByRole("table")).toBeNull();
  });

  it("explains a locked month and offers no marking", async () => {
    mockApi(monthView(true));
    render(<MyAttendancePage />);

    expect(await screen.findByText(/This month is locked/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Mark today" })).toBeNull();
  });
});
