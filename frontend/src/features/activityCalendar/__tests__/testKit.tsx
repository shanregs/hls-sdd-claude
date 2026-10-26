import { render } from "@testing-library/react";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { RouteGuard } from "../../../app/RouteGuard";
import { buildMuiTheme } from "../../../theme/tokens";
import { ActivityCalendarPage } from "../ActivityCalendarPage";

/** Shared fixtures for the Activity Calendar page tests (the vi.mock calls stay in each test file). */
export const access = {
  roles: ["DIRECTOR"] as string[],
  navigation: [] as { section: string; items: unknown[] }[],
};

export function grant(role: string, label: string) {
  access.roles = [role];
  access.navigation = [
    {
      section: "OPERATIONS",
      items: [
        { label, route: "/operations/activity-calendar", actions: ["VIEW"] },
      ],
    },
  ];
}

export const COUNTS = {
  planned: 0,
  done: 0,
  doneLate: 0,
  open: 0,
  missed: 0,
  cancelled: 0,
  rescheduled: 0,
};

export function item(over: Record<string, unknown>) {
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

export const SOURCES_OK = [
  { group: "DRIVES", available: true, read: true },
  { group: "VISITS", available: true, read: true },
  { group: "TASKS", available: true, read: true },
  { group: "TRAINING", available: true, read: true },
];

export function response(items: unknown[], over: Record<string, unknown> = {}) {
  return {
    month: "2026-10",
    today: "2026-10-12",
    partial: false,
    sources: SOURCES_OK,
    summary: { ...COUNTS, planned: items.length, byKind: {}, byManager: [] },
    items,
    ...over,
  };
}

export const FILTER_CHOICES = {
  managers: [
    { id: "u1", name: "Meera Manager" },
    { id: "u2", name: "Nikhil Manager" },
  ],
  zones: [
    { id: "z1", name: "North" },
    { id: "z2", name: "South" },
  ],
  kinds: [
    { kind: "CAMPUS_DRIVE", available: true },
    { kind: "SCHOOL_VISIT", available: true },
    { kind: "CALL", available: true },
    { kind: "MEETING", available: true },
    { kind: "TASK", available: true },
    { kind: "TRAINING", available: true },
  ],
  statuses: ["PLANNED", "DONE", "MISSED", "CANCELLED"],
};

export function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

/** Answers each endpoint of the calendar API; the calendar body can be a function of the requested URL. */
export function route(
  authFetch: { mockImplementation: (fn: (url: string) => Promise<Response>) => unknown },
  handlers: {
    calendar?: (url: string) => unknown;
    attention?: (url: string) => unknown;
    filters?: unknown;
  },
) {
  authFetch.mockImplementation(async (url: string) => {
    if (url.includes("/needs-attention")) {
      return json(handlers.attention ? handlers.attention(url) : { groups: [] });
    }
    if (url.includes("/filters")) {
      return json(handlers.filters ?? FILTER_CHOICES);
    }
    return json(handlers.calendar ? handlers.calendar(url) : response([]));
  });
}

export function renderPage() {
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
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}
