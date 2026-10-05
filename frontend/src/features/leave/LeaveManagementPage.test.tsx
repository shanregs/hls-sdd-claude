import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { LeaveManagementPage } from "./LeaveManagementPage";

const authFetch = vi.fn();
let granted = new Set(["VIEW", "APPROVE"]);

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));
vi.mock("../common/useGrantedActions", () => ({
  useGrantedActions: () => granted,
}));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function leave(id: string, status: string, extra: object = {}) {
  return {
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
  };
}

const PENDING = leave("r1", "PENDING", {
  allowedActions: ["APPROVE", "REJECT"],
});
const APPROVED = leave("r2", "APPROVED", {
  teacherName: "Meena Selvi",
  allowedActions: ["REVOKE"],
});

function list(content: unknown[], pendingCount = 1) {
  return json({
    content,
    page: 0,
    size: 25,
    totalElements: content.length,
    pendingCount,
  });
}

describe("LeaveManagementPage", () => {
  beforeEach(() => {
    authFetch.mockReset();
    granted = new Set(["VIEW", "APPROVE"]);
  });

  it("lists pending requests first with the pending count", async () => {
    authFetch.mockResolvedValue(list([PENDING], 3));
    render(<LeaveManagementPage />);

    const table = await screen.findByRole("table", { name: "Leave requests" });
    expect(within(table).getByText("Tara Teacher")).toBeInTheDocument();
    expect(within(table).getByText("Pongal travel")).toBeInTheDocument();
    expect(screen.getByText("3 pending")).toBeInTheDocument();
    expect(String(authFetch.mock.calls[0][0])).toContain("status=PENDING");
  });

  it("approves after the dialog and reloads", async () => {
    let approved = false;
    authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
      if (init?.method === "POST") {
        approved = true;
        return json(leave("r1", "APPROVED"));
      }
      return approved ? list([], 0) : list([PENDING]);
    });
    render(<LeaveManagementPage />);
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", {
        name: "Approve leave of Tara Teacher",
      }),
    );
    await user.type(screen.getByLabelText(/Note/), "Enjoy");
    await user.click(screen.getByRole("button", { name: "Approve" }));

    expect(
      await screen.findByText("No leave requests to show."),
    ).toBeInTheDocument();
    const post = authFetch.mock.calls.find(
      ([, init]) => init?.method === "POST",
    );
    expect(String(post![0])).toBe("/api/v1/leave/r1/approve");
    expect(JSON.parse(post![1].body)).toEqual({ note: "Enjoy", version: 0 });
  });

  it("requires a reason to reject and shows the server refusal", async () => {
    authFetch.mockImplementation(async (url: string, init?: RequestInit) =>
      init?.method === "POST"
        ? json({ reason: "This request was cancelled by the Teacher." }, 409)
        : list([PENDING]),
    );
    render(<LeaveManagementPage />);
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", {
        name: "Reject leave of Tara Teacher",
      }),
    );
    await user.click(screen.getByRole("button", { name: "Reject" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "A reason is required.",
    );

    await user.type(
      screen.getByLabelText(/Reason for rejecting/),
      "Exams that week",
    );
    await user.click(screen.getByRole("button", { name: "Reject" }));
    expect(
      await screen.findByText(/cancelled by the Teacher/),
    ).toBeInTheDocument();
  });

  it("offers revoke for an approved request", async () => {
    authFetch.mockResolvedValue(list([APPROVED], 0));
    render(<LeaveManagementPage />);

    expect(
      await screen.findByRole("button", {
        name: "Revoke leave of Meena Selvi",
      }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Approve leave/ })).toBeNull();
  });

  it("hides the decision icons without the Approve permission", async () => {
    granted = new Set(["VIEW"]);
    authFetch.mockResolvedValue(list([PENDING]));
    render(<LeaveManagementPage />);

    await screen.findByRole("table", { name: "Leave requests" });
    expect(screen.queryByRole("button", { name: /leave of/ })).toBeNull();
  });
});
