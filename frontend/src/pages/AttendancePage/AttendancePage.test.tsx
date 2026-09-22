import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AttendancePage } from "./AttendancePage";
import { AuthProvider, useAuth } from "../../auth/AuthContext";

function mockFetchSequence(
  responses: Array<{ status: number; body: unknown }>,
) {
  let call = 0;
  vi.stubGlobal(
    "fetch",
    vi.fn(() => {
      const { status, body } = responses[Math.min(call, responses.length - 1)];
      call += 1;
      return Promise.resolve({
        ok: status < 400,
        status,
        json: () => Promise.resolve(body),
      } as Response);
    }),
  );
}

function LogInWithFakeToken() {
  const { setTokens } = useAuth();
  useEffect(() => {
    setTokens({ accessToken: "fake-token", expiresIn: 900 });
  }, [setTokens]);
  return null;
}

function renderAuthenticated() {
  return render(
    <AuthProvider>
      <LogInWithFakeToken />
      <AttendancePage />
    </AuthProvider>,
  );
}

const statusCodesBody = [
  {
    code: "PRESENT",
    label: "Present",
    category: "WORKED",
    weight: 1,
    active: true,
  },
  { code: "LEAVE", label: "Leave", category: "LEAVE", weight: 0, active: true },
];

describe("AttendancePage", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("marks attendance on behalf of an accountable Teacher (User Story 2, AC1)", async () => {
    mockFetchSequence([
      { status: 200, body: statusCodesBody },
      {
        status: 200,
        body: {
          id: "mark-1",
          teacherId: "teacher-1",
          markDate: "2026-09-22",
          schoolId: "school-1",
          statusCode: "PRESENT",
          fractionalValue: 1,
          evidence: {},
          markedBy: "manager-1",
          markedByRole: "MANAGER",
          markedAt: "2026-09-22T09:00:00Z",
        },
      },
    ]);
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("mark-teacher-id-input"),
      "teacher-1",
    );
    await userEvent.type(
      screen.getByTestId("mark-on-behalf-school-id-input"),
      "school-1",
    );
    await userEvent.click(screen.getByRole("button", { name: /^save$/i }));

    expect(
      await screen.findByTestId("mark-on-behalf-message"),
    ).toHaveTextContent("MANAGER");
  });

  it("shows a denied error for a non-accountable Teacher (User Story 2, AC2)", async () => {
    mockFetchSequence([
      { status: 200, body: statusCodesBody },
      { status: 403, body: {} },
    ]);
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("mark-teacher-id-input"),
      "teacher-2",
    );
    await userEvent.type(
      screen.getByTestId("mark-on-behalf-school-id-input"),
      "school-1",
    );
    await userEvent.click(screen.getByRole("button", { name: /^save$/i }));

    expect(await screen.findByTestId("mark-on-behalf-error")).toHaveTextContent(
      "not allowed",
    );
  });

  it("views a Teacher's rollup and marks (User Story 3)", async () => {
    mockFetchSequence([
      { status: 200, body: statusCodesBody },
      {
        status: 200,
        body: {
          teacherId: "teacher-1",
          period: "2026-09",
          trainingDaysTotal: 0,
          trainingDaysAttended: 0,
          daysWorked: 5,
          daysLeave: 1,
          overallWorkingDays: 22,
          unmarkedDays: 16,
          weightedAttendanceTotal: 5,
          lockStatus: "UNLOCKED",
        },
      },
      {
        status: 200,
        body: [
          {
            id: "m1",
            teacherId: "teacher-1",
            markDate: "2026-09-01",
            schoolId: "s1",
            statusCode: "PRESENT",
            fractionalValue: 1,
            markedBy: "u1",
            markedByRole: "TEACHER",
            markedAt: "2026-09-01T09:00:00Z",
          },
        ],
      },
    ]);
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("view-teacher-id-input"),
      "teacher-1",
    );
    await userEvent.click(screen.getByRole("button", { name: /look up/i }));

    expect(
      await screen.findByTestId("teacher-rollup-days-worked"),
    ).toHaveTextContent("5");
    expect(screen.getByTestId("teacher-marks-list")).toHaveTextContent(
      "PRESENT",
    );
  });

  it("locks a teacher-month (User Story 4, AC1)", async () => {
    mockFetchSequence([
      { status: 200, body: statusCodesBody },
      {
        status: 200,
        body: {
          teacherId: "teacher-1",
          period: "2026-09",
          status: "LOCKED",
          lockedAt: "2026-09-22T10:00:00Z",
          lockedBy: "director-1",
          reopenHistory: [],
        },
      },
    ]);
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("lock-teacher-id-input"),
      "teacher-1",
    );
    await userEvent.click(screen.getByTestId("lock-month-button"));

    expect(await screen.findByTestId("lock-status-value")).toHaveTextContent(
      "LOCKED",
    );
  });

  it("reopens a locked teacher-month with a reason (User Story 4, AC3)", async () => {
    mockFetchSequence([
      { status: 200, body: statusCodesBody },
      {
        status: 200,
        body: {
          teacherId: "teacher-1",
          period: "2026-09",
          status: "REOPENED",
          reopenHistory: [
            {
              reason: "Correction needed",
              reopenedAt: "2026-09-22T10:00:00Z",
              reopenedBy: "director-1",
            },
          ],
        },
      },
    ]);
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("lock-teacher-id-input"),
      "teacher-1",
    );
    await userEvent.type(
      screen.getByTestId("reopen-reason-input"),
      "Correction needed",
    );
    await userEvent.click(screen.getByRole("button", { name: /^reopen$/i }));

    expect(await screen.findByTestId("reopen-history")).toHaveTextContent(
      "Correction needed",
    );
  });

  it("shows a denied error when a non-Director attempts to lock or reopen (FR-011/FR-014)", async () => {
    mockFetchSequence([
      { status: 200, body: statusCodesBody },
      { status: 403, body: {} },
    ]);
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("lock-teacher-id-input"),
      "teacher-1",
    );
    await userEvent.click(screen.getByTestId("lock-month-button"));

    expect(await screen.findByTestId("lock-reopen-error")).toHaveTextContent(
      "not allowed",
    );
  });

  it("adds a new attendance status code (FR-005)", async () => {
    mockFetchSequence([
      { status: 200, body: statusCodesBody },
      {
        status: 200,
        body: {
          code: "SICK_LEAVE",
          label: "Sick Leave",
          category: "LEAVE",
          weight: 0,
          active: true,
        },
      },
    ]);
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("new-status-code-input"),
      "SICK_LEAVE",
    );
    await userEvent.type(
      screen.getByTestId("new-status-label-input"),
      "Sick Leave",
    );
    await userEvent.click(
      screen.getByRole("button", { name: /add status code/i }),
    );

    expect(await screen.findByTestId("status-code-message")).toHaveTextContent(
      "SICK_LEAVE",
    );
  });

  it("adds and deactivates a non-working calendar date (FR-022)", async () => {
    mockFetchSequence([
      { status: 200, body: statusCodesBody },
      {
        status: 200,
        body: {
          id: "date-1",
          date: "2026-12-25",
          label: "Christmas",
          active: true,
        },
      },
      {
        status: 200,
        body: [
          {
            id: "date-1",
            date: "2026-12-25",
            label: "Christmas",
            active: true,
          },
        ],
      },
      {
        status: 200,
        body: {
          id: "date-1",
          date: "2026-12-25",
          label: "Christmas",
          active: false,
        },
      },
      {
        status: 200,
        body: [
          {
            id: "date-1",
            date: "2026-12-25",
            label: "Christmas",
            active: false,
          },
        ],
      },
    ]);
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("new-holiday-date-input"),
      "2026-12-25",
    );
    await userEvent.type(
      screen.getByTestId("new-holiday-label-input"),
      "Christmas",
    );
    await userEvent.click(screen.getByRole("button", { name: /^add$/i }));

    expect(await screen.findByTestId("calendar-message")).toHaveTextContent(
      "Christmas",
    );
    expect(
      await screen.findByTestId("deactivate-non-working-date-date-1"),
    ).toBeInTheDocument();

    await userEvent.click(
      screen.getByTestId("deactivate-non-working-date-date-1"),
    );

    expect(
      await screen.findByTestId("non-working-dates-list"),
    ).toHaveTextContent("(inactive)");
  });

  it("loads and displays the attendance grid (User Story 5)", async () => {
    mockFetchSequence([
      { status: 200, body: statusCodesBody },
      {
        status: 200,
        body: {
          period: "2026-09",
          days: ["2026-09-01", "2026-09-02"],
          rows: [
            {
              teacherId: "teacher-1",
              teacherName: "A Teacher",
              cells: {
                "2026-09-01": {
                  statusCode: "PRESENT",
                  category: "WORKED",
                  fractionalValue: 1,
                  schoolId: "school-1",
                  editable: true,
                },
                "2026-09-02": {
                  statusCode: null,
                  category: null,
                  fractionalValue: null,
                  schoolId: null,
                  editable: true,
                },
              },
            },
          ],
        },
      },
    ]);
    renderAuthenticated();

    await userEvent.click(screen.getByRole("button", { name: /load grid/i }));

    expect(await screen.findByTestId("attendance-grid")).toHaveTextContent(
      "A Teacher",
    );
    expect(screen.getByTestId("attendance-grid")).toHaveTextContent("PRESENT");
  });
});
