import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../../app/RouteGuard";
import { buildMuiTheme } from "../../../theme/tokens";
import { ActivityCalendarPage } from "../ActivityCalendarPage";

const authFetch = vi.fn();
let roles: string[] = ["DIRECTOR"];
let navigation: { section: string; items: unknown[] }[] = [];

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

vi.mock("../../../access-model/useAccessModel", async () => {
  const actual = await vi.importActual<
    typeof import("../../../access-model/useAccessModel")
  >("../../../access-model/useAccessModel");
  return {
    ...actual,
    useAccessModel: () => ({
      loading: false,
      accessModel: { roles, dataScope: {}, navigation },
    }),
  };
});

function grant(role: string, label: string) {
  roles = [role];
  navigation = [
    {
      section: "OPERATIONS",
      items: [
        {
          label,
          route: "/operations/activity-calendar",
          actions: ["VIEW"],
        },
      ],
    },
  ];
}

const COUNTS = {
  planned: 0,
  done: 0,
  doneLate: 0,
  open: 0,
  missed: 0,
  cancelled: 0,
  rescheduled: 0,
};

function item(over: Record<string, unknown>) {
  return {
    key: "k",
    activityId: "a",
    kind: "SCHOOL_VISIT",
    subKind: null,
    date: "2026-10-05",
    status: "DONE",
    doneLate: false,
    completedOn: "2026-10-05",
    rescheduled: false,
    originalDate: null,
    daysMoved: null,
    daysLate: null,
    people: [
      { userId: "u1", name: "Meera Manager", zoneName: "North", manager: true },
    ],
    zoneId: "z1",
    zoneName: "North",
    place: "Green Valley School",
    link: null,
    ...over,
  };
}

function response(items: unknown[], over: Record<string, unknown> = {}) {
  return {
    month: "2026-10",
    today: "2026-10-12",
    partial: false,
    sources: [
      { group: "DRIVES", available: true, read: true },
      { group: "VISITS", available: true, read: true },
      { group: "TASKS", available: true, read: true },
      {
        group: "TRAINING",
        available: false,
        read: false,
        note: "Training is not included yet",
      },
    ],
    summary: {
      ...COUNTS,
      planned: 3,
      done: 1,
      missed: 2,
      rescheduled: 1,
      byKind: { SCHOOL_VISIT: { ...COUNTS, planned: 3 } },
      byManager: [],
    },
    items,
    ...over,
  };
}

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function renderPage() {
  return render(
    <ThemeProvider theme={buildMuiTheme("light")}>
      <MemoryRouter initialEntries={["/operations/activity-calendar"]}>
        <Routes>
          <Route
            path="/operations/activity-calendar"
            element={
              <RouteGuard>
                <ActivityCalendarPage />
              </RouteGuard>
            }
          />
          <Route path="/marketing/calendar" element={<div>Marketing screen</div>} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  authFetch.mockReset();
  grant("DIRECTOR", "Activity Calendar");
});

describe("Activity Calendar (spec 035 US1)", () => {
  it("shows the month with statuses, the summary and the reschedule mark for the Director", async () => {
    authFetch.mockResolvedValue(
      json(
        response([
          item({ key: "1", activityId: "a1" }),
          item({
            key: "2",
            activityId: "a2",
            date: "2026-10-20",
            status: "MISSED",
            rescheduled: true,
            originalDate: "2026-10-14",
            daysMoved: 6,
            place: null,
            zoneName: null,
          }),
          item({ key: "3", activityId: "a3", date: "2026-10-22", status: "CANCELLED" }),
        ]),
      ),
    );
    renderPage();

    expect(
      await screen.findByRole("heading", { name: "Activity Calendar", level: 1 }),
    ).toBeInTheDocument();
    expect(await screen.findByText("Planned 3")).toBeInTheDocument();
    expect(screen.getByText("Missed 2")).toBeInTheDocument();
    expect(screen.getByText("Rescheduled 1")).toBeInTheDocument();
    expect(screen.getAllByText(/Green Valley School/).length).toBeGreaterThan(0);
    expect(screen.getByText(/first planned 14\/10\/2026/)).toBeInTheDocument();
    expect(screen.getByText(/No place recorded · No Zone/)).toBeInTheDocument();
    expect(screen.getByText("Training is not included yet")).toBeInTheDocument();
    expect(authFetch).toHaveBeenCalledWith(
      expect.stringMatching(/^\/api\/v1\/activity-calendar\?month=\d{4}-\d{2}$/),
    );
  });

  it("shows the empty message with zero counts for an empty month", async () => {
    authFetch.mockResolvedValue(
      json(response([], { summary: { ...COUNTS, byKind: {}, byManager: [] } })),
    );
    renderPage();
    expect(await screen.findByText("Nothing planned this month.")).toBeInTheDocument();
    expect(screen.getByText("Planned 0")).toBeInTheDocument();
  });

  it("marks the counts partial when a source could not be read", async () => {
    authFetch.mockResolvedValue(
      json(
        response([], {
          partial: true,
          sources: [
            { group: "TASKS", available: true, read: false, note: "Tasks could not be loaded" },
          ],
        }),
      ),
    );
    renderPage();
    expect(await screen.findByText("Tasks could not be loaded")).toBeInTheDocument();
    expect(screen.getByText(/Counts are partial/)).toBeInTheDocument();
  });

  it("opens a read-only detail with no action control and no link without a grant", async () => {
    authFetch.mockResolvedValue(json(response([item({})])));
    renderPage();
    await userEvent.click(
      await screen.findByRole("button", { name: /School visit 05\/10\/2026 Done/ }),
    );

    const dialog = await screen.findByRole("dialog");
    expect(dialog).toHaveTextContent("Meera Manager");
    expect(dialog).toHaveTextContent("Green Valley School");
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /complete|move|cancel|reschedule/i })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Close" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("shows the owning-screen link only when the item carries one", async () => {
    authFetch.mockResolvedValue(
      json(
        response([
          item({ link: { screen: "MARKETING", path: "/marketing/calendar" } }),
        ]),
      ),
    );
    renderPage();
    await userEvent.click(
      await screen.findByRole("button", { name: /School visit 05\/10\/2026 Done/ }),
    );
    expect(await screen.findByRole("link", { name: /Open in marketing/ })).toHaveAttribute(
      "href",
      "/marketing/calendar",
    );
  });

  it("titles the page per role: Admin and Director 'Activity Calendar', Manager 'My Activity Calendar'", async () => {
    authFetch.mockResolvedValue(json(response([])));
    grant("ADMIN", "Activity Calendar");
    const admin = renderPage();
    expect(await screen.findByRole("heading", { name: "Activity Calendar", level: 1 })).toBeInTheDocument();
    admin.unmount();

    grant("MANAGER", "My Activity Calendar");
    renderPage();
    expect(await screen.findByRole("heading", { name: "My Activity Calendar", level: 1 })).toBeInTheDocument();
  });
});
