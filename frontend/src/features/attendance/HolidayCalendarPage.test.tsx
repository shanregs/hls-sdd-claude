import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { HolidayCalendarPage } from "./HolidayCalendarPage";

const authFetch = vi.fn();
let grantedActions = ["VIEW", "EDIT"];

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch, user: { id: "me", displayName: "Me" } }),
}));

vi.mock("../../access-model/useAccessModel", () => ({
  useAccessModel: () => ({
    loading: false,
    accessModel: {
      roles: ["ADMIN"],
      dataScope: {},
      navigation: [
        {
          section: "MASTER DATA",
          items: [
            {
              label: "Holiday Calendar",
              route: "/master-data/holiday-calendar",
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

const CALENDAR = {
  defaultWeeklyOff: ["SUN"],
  defaultVersion: 0,
  schoolOverrides: [],
  nonWorkingDates: [{ date: "2026-10-02", description: "Gandhi Jayanti" }],
};

function mockApi() {
  authFetch.mockImplementation(async (url: string) =>
    url === "/api/v1/attendance/calendar"
      ? json(CALENDAR)
      : json({ content: [], page: 0, size: 100, totalElements: 0 }),
  );
}

describe("HolidayCalendarPage", () => {
  beforeEach(() => {
    authFetch.mockReset();
    grantedActions = ["VIEW", "EDIT"];
  });

  it("lists holidays with dates as DD/MM/YYYY", async () => {
    mockApi();
    render(<HolidayCalendarPage />);

    expect(
      await screen.findByRole("heading", { name: "Holiday Calendar" }),
    ).toBeInTheDocument();
    expect(await screen.findByText("02/10/2026")).toBeInTheDocument();
    expect(screen.getByText("Gandhi Jayanti")).toBeInTheDocument();
  });

  it("shows edit controls when EDIT is granted", async () => {
    mockApi();
    render(<HolidayCalendarPage />);

    expect(
      await screen.findByRole("button", { name: "Save default" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add date" })).toBeDisabled();
  });

  it("hides every edit control for a view-only grant", async () => {
    grantedActions = ["VIEW"];
    mockApi();
    render(<HolidayCalendarPage />);

    await screen.findByText("Gandhi Jayanti");
    expect(screen.queryByRole("button", { name: "Save default" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Add date" })).toBeNull();
  });
});
