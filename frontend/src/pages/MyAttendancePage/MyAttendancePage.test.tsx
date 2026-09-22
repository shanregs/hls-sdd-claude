import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MyAttendancePage } from "./MyAttendancePage";
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
      <MyAttendancePage />
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

const teacherMeBody = {
  id: "teacher-1",
  name: "Priya Sharma",
  phone: "+919811111111",
  hlsOfferedSalary: 17000,
  status: "ACTIVE",
  createdAt: "2026-09-01T00:00:00Z",
};

const rollupBody = {
  teacherId: "teacher-1",
  period: "2026-09",
  trainingDaysTotal: 0,
  trainingDaysAttended: 0,
  daysWorked: 1,
  daysLeave: 0,
  overallWorkingDays: 22,
  unmarkedDays: 21,
  weightedAttendanceTotal: 1,
  lockStatus: "UNLOCKED",
};

describe("MyAttendancePage", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("marks Present attendance and shows a confirmation (User Story 1, AC1)", async () => {
    mockFetchSequence([
      { status: 200, body: statusCodesBody },
      { status: 200, body: teacherMeBody },
      { status: 200, body: rollupBody },
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
          markedBy: "teacher-1",
          markedByRole: "TEACHER",
          markedAt: "2026-09-22T09:00:00Z",
        },
      },
      { status: 200, body: rollupBody },
    ]);
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("mark-school-id-input"),
      "school-1",
    );
    await userEvent.click(screen.getByRole("button", { name: /^save$/i }));

    expect(
      await screen.findByTestId("mark-attendance-message"),
    ).toHaveTextContent("PRESENT");
  });

  it("marks a half-day (AC2)", async () => {
    mockFetchSequence([
      { status: 200, body: statusCodesBody },
      { status: 200, body: teacherMeBody },
      { status: 200, body: rollupBody },
      {
        status: 200,
        body: {
          id: "mark-1",
          teacherId: "teacher-1",
          markDate: "2026-09-22",
          schoolId: "school-1",
          statusCode: "PRESENT",
          fractionalValue: 0.5,
          evidence: {},
          markedBy: "teacher-1",
          markedByRole: "TEACHER",
          markedAt: "2026-09-22T09:00:00Z",
        },
      },
      { status: 200, body: rollupBody },
    ]);
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("mark-school-id-input"),
      "school-1",
    );
    await userEvent.clear(screen.getByTestId("mark-fractional-value-input"));
    await userEvent.type(
      screen.getByTestId("mark-fractional-value-input"),
      "0.5",
    );
    await userEvent.click(screen.getByRole("button", { name: /^save$/i }));

    expect(
      await screen.findByTestId("mark-attendance-message"),
    ).toBeInTheDocument();
  });

  it("marks with optional evidence (AC3)", async () => {
    mockFetchSequence([
      { status: 200, body: statusCodesBody },
      { status: 200, body: teacherMeBody },
      { status: 200, body: rollupBody },
      {
        status: 200,
        body: {
          id: "mark-1",
          teacherId: "teacher-1",
          markDate: "2026-09-22",
          schoolId: "school-1",
          statusCode: "PRESENT",
          fractionalValue: 1,
          evidence: { checkinCode: "ABC123" },
          markedBy: "teacher-1",
          markedByRole: "TEACHER",
          markedAt: "2026-09-22T09:00:00Z",
        },
      },
      { status: 200, body: rollupBody },
    ]);
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("mark-school-id-input"),
      "school-1",
    );
    await userEvent.type(
      screen.getByTestId("mark-checkin-code-input"),
      "ABC123",
    );
    await userEvent.click(screen.getByRole("button", { name: /^save$/i }));

    expect(
      await screen.findByTestId("mark-attendance-message"),
    ).toBeInTheDocument();
  });

  it("shows an error when marking fails, e.g. a locked month (FR-012)", async () => {
    mockFetchSequence([
      { status: 200, body: statusCodesBody },
      { status: 200, body: teacherMeBody },
      { status: 200, body: rollupBody },
      { status: 409, body: { message: "Attendance for 2026-09 is locked" } },
    ]);
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("mark-school-id-input"),
      "school-1",
    );
    await userEvent.click(screen.getByRole("button", { name: /^save$/i }));

    expect(
      await screen.findByTestId("mark-attendance-error"),
    ).toHaveTextContent("locked");
  });

  it("shows this month's rollup figures (User Story 3)", async () => {
    mockFetchSequence([
      { status: 200, body: statusCodesBody },
      { status: 200, body: teacherMeBody },
      { status: 200, body: rollupBody },
    ]);
    renderAuthenticated();

    expect(
      await screen.findByTestId("my-rollup-days-worked"),
    ).toHaveTextContent("1");
    expect(screen.getByTestId("my-rollup-lock-status")).toHaveTextContent(
      "UNLOCKED",
    );
  });
});
