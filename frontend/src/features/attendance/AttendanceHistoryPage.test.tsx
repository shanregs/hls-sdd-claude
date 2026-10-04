import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AttendanceHistoryPage } from "./AttendanceHistoryPage";
import { currentMonth, dateKey, daysInMonth, shiftMonth } from "./monthUtils";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch, user: { id: "me", displayName: "Tara" } }),
}));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function monthView(month: string, locked: boolean) {
  const days = Array.from({ length: daysInMonth(month) }, (_, i) => ({
    date: dateKey(month, i + 1),
    state: i === 0 ? "MARKED" : "UNMARKED",
    mark:
      i === 0
        ? {
            date: dateKey(month, 1),
            code: "P",
            codeName: "Present",
            category: "WORKED",
            dayValue: 1,
            schoolId: "s1",
            schoolName: "Demo School One",
            setByKind: "SELF",
            setByUserId: "u1",
            setByName: "Tara",
            setAt: "2026-09-01T05:00:00Z",
            note: null,
            version: 0,
          }
        : null,
    editableBy: "NONE",
  }));
  return {
    teacherId: "t1",
    name: "Tara",
    month,
    locked,
    state: locked ? "LOCKED" : "OPEN",
    rollup: {
      workingDays: 26,
      daysWorked: 1,
      daysLeave: 0,
      trainingAvailable: 0,
      trainingAttended: 0,
      unmarked: 0,
      weightedTotal: 1,
      locked,
      frozen: locked,
    },
    days,
  };
}

function mockApi(locked: boolean) {
  authFetch.mockImplementation(async (url: string) => {
    const month = /month=(\d{4}-\d{2})/.exec(url)?.[1] ?? currentMonth();
    return json(monthView(month, locked));
  });
}

describe("AttendanceHistoryPage (User Story 7)", () => {
  beforeEach(() => authFetch.mockReset());

  it("opens on the previous month with the rollup", async () => {
    mockApi(false);
    render(<AttendanceHistoryPage />);

    expect(
      await screen.findByRole("table", { name: "Attendance calendar" }),
    ).toBeInTheDocument();
    const calls = authFetch.mock.calls.map((c) => String(c[0]));
    expect(calls[0]).toContain(`month=${shiftMonth(currentMonth(), -1)}`);
    expect(screen.queryByText(/^Locked:/)).toBeNull();
  });

  it("shows a locked banner and offers no edit affordance", async () => {
    mockApi(true);
    render(<AttendanceHistoryPage />);

    expect(await screen.findByText(/^Locked:/)).toBeInTheDocument();
    expect(screen.getByText(/\(locked\)/)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /mark|edit/i }),
    ).not.toBeInTheDocument();
  });

  it("goes back a month and cannot go into the current month", async () => {
    mockApi(false);
    render(<AttendanceHistoryPage />);
    const user = userEvent.setup();

    await screen.findByRole("table", { name: "Attendance calendar" });
    expect(screen.getByRole("button", { name: "Next month" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "Previous month" }));

    await vi.waitFor(() => {
      const urls = authFetch.mock.calls.map((c) => String(c[0]));
      expect(
        urls.some((u) => u.includes(`month=${shiftMonth(currentMonth(), -2)}`)),
      ).toBe(true);
    });
    expect(screen.getByRole("button", { name: "Next month" })).toBeEnabled();
  });

  it("shows the server message when the profile is not set up", async () => {
    authFetch.mockResolvedValue(
      json({ reason: "Your profile has not been set up yet." }, 404),
    );
    render(<AttendanceHistoryPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Your profile has not been set up yet.",
    );
  });
});
