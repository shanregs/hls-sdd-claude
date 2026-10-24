import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { access, grant, item, renderPage, response, route } from "./testKit";

const authFetch = vi.fn();

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

vi.mock("../../../access-model/useAccessModel", async () => {
  const actual = await vi.importActual<
    typeof import("../../../access-model/useAccessModel")
  >("../../../access-model/useAccessModel");
  return {
    ...actual,
    useAccessModel: () => ({
      loading: false,
      accessModel: {
        roles: access.roles,
        dataScope: {},
        navigation: access.navigation,
      },
    }),
  };
});

beforeEach(() => {
  authFetch.mockReset();
  window.sessionStorage.clear();
  grant("DIRECTOR", "Activity Calendar");
});

const missed = item({
  key: "m1",
  activityId: "m1",
  kind: "TASK",
  date: "2026-10-04",
  status: "MISSED",
  daysLate: 8,
});
const moved = item({
  key: "r1",
  activityId: "r1",
  date: "2026-10-20",
  status: "PLANNED",
  rescheduled: true,
  originalDate: "2026-10-14",
  daysMoved: 6,
});

describe("Needs attention (spec 035 US3)", () => {
  it("groups missed and rescheduled items by Manager with days late or moved", async () => {
    route(authFetch, {
      calendar: () => response([]),
      attention: () => ({
        from: "2026-10-01",
        to: "2026-10-31",
        partial: false,
        groups: [
          {
            userId: "u1",
            name: "Meera Manager",
            missed: 1,
            rescheduled: 1,
            entries: [
              { item: missed, reasons: ["MISSED"], daysLate: 8, daysMoved: null },
              { item: moved, reasons: ["RESCHEDULED"], daysLate: null, daysMoved: 6 },
            ],
          },
        ],
      }),
    });
    renderPage();
    await userEvent.click(await screen.findByRole("tab", { name: "Needs attention" }));

    expect(await screen.findByRole("heading", { name: "Meera Manager" })).toBeInTheDocument();
    expect(screen.getByText("Missed 1 · Rescheduled 1")).toBeInTheDocument();
    expect(screen.getByText(/8 days late/)).toBeInTheDocument();
    expect(screen.getByText(/first planned 14\/10\/2026, now 20\/10\/2026.*moved 6 days/)).toBeInTheDocument();

    await userEvent.click(screen.getByText(/8 days late/));
    expect(within(await screen.findByRole("dialog")).getByText(/Meera Manager/)).toBeInTheDocument();
  });

  it("shows an empty message when nothing needs attention", async () => {
    route(authFetch, {});
    renderPage();
    await userEvent.click(await screen.findByRole("tab", { name: "Needs attention" }));
    expect(
      await screen.findByText("Nothing needs attention in this period."),
    ).toBeInTheDocument();
  });
});
