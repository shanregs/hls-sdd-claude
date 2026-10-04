import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { LockMonthDialog } from "./LockMonthDialog";
import { ReopenDialog } from "./ReopenDialog";
import { AttendanceGridPage } from "./AttendanceGridPage";

const authFetch = vi.fn();
let grantedActions = ["VIEW", "EDIT", "PROCESS"];

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch, user: { id: "me", displayName: "Asha" } }),
}));

vi.mock("../../access-model/useAccessModel", () => ({
  useAccessModel: () => ({
    loading: false,
    accessModel: {
      roles: ["ADMIN"],
      dataScope: {},
      navigation: [
        {
          section: "OPERATIONS",
          items: [
            {
              label: "Attendance",
              route: "/operations/attendance",
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

describe("LockMonthDialog (User Story 6)", () => {
  beforeEach(() => authFetch.mockReset());

  it("lists the unmarked Teachers and days when the lock is refused", async () => {
    authFetch.mockResolvedValue(
      json(
        {
          reason:
            "Some working days are still unmarked. Mark them, then lock again.",
          unmarked: [
            { teacherId: "t1", name: "Tara Teacher", dates: ["2026-09-03"] },
          ],
        },
        409,
      ),
    );
    render(
      <LockMonthDialog month="2026-09" onClose={vi.fn()} onLocked={vi.fn()} />,
    );

    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Lock month" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /still unmarked/,
    );
    expect(screen.getByText(/Tara Teacher/)).toBeInTheDocument();
    expect(screen.getByText(/03\/09\/2026/)).toBeInTheDocument();
  });

  it("reports success to the caller", async () => {
    authFetch.mockResolvedValue(json({ locked: 3 }));
    const onLocked = vi.fn();
    render(
      <LockMonthDialog month="2026-09" onClose={vi.fn()} onLocked={onLocked} />,
    );

    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Lock month" }));

    await vi.waitFor(() => expect(onLocked).toHaveBeenCalled());
  });
});

describe("ReopenDialog (User Story 6)", () => {
  beforeEach(() => authFetch.mockReset());

  it("requires a reason before reopening", async () => {
    render(
      <ReopenDialog
        teacherId="t1"
        teacherName="Tara Teacher"
        month="2026-09"
        onClose={vi.fn()}
        onReopened={vi.fn()}
      />,
    );

    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Reopen" }));

    expect(await screen.findByText("Enter a reason.")).toBeInTheDocument();
    expect(authFetch).not.toHaveBeenCalled();
  });

  it("sends the trimmed reason", async () => {
    authFetch.mockResolvedValue(json({ state: "OPEN" }));
    const onReopened = vi.fn();
    render(
      <ReopenDialog
        teacherId="t1"
        teacherName="Tara Teacher"
        month="2026-09"
        onClose={vi.fn()}
        onReopened={onReopened}
      />,
    );
    const user = userEvent.setup();

    await user.type(screen.getByLabelText(/Reason/), "  Wrong day  ");
    await user.click(screen.getByRole("button", { name: "Reopen" }));

    await vi.waitFor(() => expect(onReopened).toHaveBeenCalled());
    const [url, init] = authFetch.mock.calls[0];
    expect(url).toBe("/api/v1/attendance/teachers/t1/months/2026-09/reopen");
    expect(JSON.parse(init.body)).toEqual({ reason: "Wrong day" });
  });
});

describe("Lock controls on the grid page", () => {
  beforeEach(() => {
    authFetch.mockReset();
    authFetch.mockImplementation(async (url: string) =>
      url.startsWith("/api/v1/attendance/grid")
        ? json({
            month: "x",
            days: 30,
            page: 0,
            size: 50,
            totalElements: 0,
            content: [],
          })
        : json({ content: [], page: 0, size: 100, totalElements: 0 }),
    );
  });

  it("offers Lock month only with PROCESS", async () => {
    grantedActions = ["VIEW", "EDIT", "PROCESS"];
    const { unmount } = render(<AttendanceGridPage />);
    expect(
      await screen.findByRole("button", { name: "Lock month" }),
    ).toBeInTheDocument();
    unmount();

    grantedActions = ["VIEW", "EDIT"];
    render(<AttendanceGridPage />);
    await screen.findByText(/No teachers are placed/);
    expect(
      screen.queryByRole("button", { name: "Lock month" }),
    ).not.toBeInTheDocument();
  });
});
