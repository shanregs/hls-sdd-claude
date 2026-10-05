import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MyLeaveHistoryPage } from "./MyLeaveHistoryPage";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
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

const PENDING = leave("r1", "PENDING", { allowedActions: ["CANCEL"] });
const REJECTED = leave("r2", "REJECTED", {
  decidedByName: "Manoj Manager",
  decidedAt: "2026-01-03T10:00:00Z",
  decisionNote: "Exams that week",
});

function page(content: unknown[]) {
  return json({ content, page: 0, size: 25, totalElements: content.length });
}

describe("MyLeaveHistoryPage", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("lists the requests with status, decider and the rejection reason", async () => {
    authFetch.mockResolvedValue(page([PENDING, REJECTED]));
    render(<MyLeaveHistoryPage />);

    const table = await screen.findByRole("table", {
      name: "My leave requests",
    });
    expect(within(table).getByText("Pending")).toBeInTheDocument();
    expect(within(table).getByText("Rejected")).toBeInTheDocument();
    expect(within(table).getByText("Exams that week")).toBeInTheDocument();
    expect(within(table).getByText(/Manoj Manager/)).toBeInTheDocument();
    expect(within(table).getAllByText("14/01/2026 to 16/01/2026")).toHaveLength(
      2,
    );
  });

  it("offers cancel only where the server allows it", async () => {
    authFetch.mockResolvedValue(page([PENDING, REJECTED]));
    render(<MyLeaveHistoryPage />);

    await screen.findByRole("table", { name: "My leave requests" });
    expect(
      screen.getAllByRole("button", { name: /^Cancel leave/ }),
    ).toHaveLength(1);
  });

  it("cancels after a confirmation and reloads", async () => {
    let cancelled = false;
    authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
      if (init?.method === "POST") {
        cancelled = true;
        return json(leave("r1", "CANCELLED", { cancelledBy: "TEACHER" }));
      }
      return page(
        cancelled
          ? [leave("r1", "CANCELLED", { cancelledBy: "TEACHER" })]
          : [PENDING],
      );
    });
    render(<MyLeaveHistoryPage />);
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: /^Cancel leave/ }),
    );
    await user.click(screen.getByRole("button", { name: "Cancel request" }));

    expect(await screen.findByText("Cancelled")).toBeInTheDocument();
    expect(authFetch).toHaveBeenCalledWith(
      "/api/v1/me/leave/r1/cancel",
      expect.objectContaining({ method: "POST" }),
    );
    expect(screen.queryByRole("button", { name: /^Cancel leave/ })).toBeNull();
  });

  it("says so when there is nothing yet, and shows a load error", async () => {
    authFetch.mockResolvedValueOnce(page([]));
    const { unmount } = render(<MyLeaveHistoryPage />);
    expect(
      await screen.findByText("You have no leave requests yet."),
    ).toBeInTheDocument();
    unmount();

    authFetch.mockResolvedValueOnce(json({ reason: "boom" }, 500));
    render(<MyLeaveHistoryPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent("boom");
  });
});
