import { screen } from "@testing-library/react";
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

function lastCalendarUrl(): string {
  const calls = authFetch.mock.calls
    .map((c) => String(c[0]))
    .filter((u) => u.includes("/api/v1/activity-calendar") && !u.includes("/filters") && !u.includes("/needs-attention"));
  return calls[calls.length - 1];
}

describe("Activity Calendar filters (spec 035 US4)", () => {
  it("combines the filters in the request and clears them all", async () => {
    route(authFetch, {});
    renderPage();
    await userEvent.selectOptions(await screen.findByLabelText("Zone"), "South");
    await userEvent.selectOptions(screen.getByLabelText("Kind"), "Task");
    await userEvent.selectOptions(screen.getByLabelText("Status"), "Missed");
    await userEvent.selectOptions(screen.getByLabelText("Manager"), "Nikhil Manager");

    await vi.waitFor(() => {
      expect(lastCalendarUrl()).toMatch(/managerId=u2/);
    });
    expect(lastCalendarUrl()).toMatch(/zoneId=z2/);
    expect(lastCalendarUrl()).toMatch(/kind=TASK/);
    expect(lastCalendarUrl()).toMatch(/status=MISSED/);

    await userEvent.click(screen.getByRole("button", { name: "Clear filters" }));
    await vi.waitFor(() => {
      expect(lastCalendarUrl()).toMatch(/^\/api\/v1\/activity-calendar\?month=\d{4}-\d{2}$/);
    });
  });

  it("shows '6 more' for a day with 9 items and opens the full list of that day", async () => {
    const nine = Array.from({ length: 9 }, (_, i) =>
      item({ key: `k${i}`, activityId: `a${i}`, date: "2026-10-05" }),
    );
    route(authFetch, { calendar: () => response(nine, { month: "2026-10" }) });
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: "6 more" }));
    expect(await screen.findByRole("dialog")).toHaveTextContent("Activities on 05/10/2026");
    expect(screen.getAllByRole("button", { name: /^Open School visit/ })).toHaveLength(9);
  });

  it("switches to the list view with the same items", async () => {
    route(authFetch, {
      calendar: () => response([item({ key: "1", activityId: "a1" }), item({ key: "2", activityId: "a2", date: "2026-10-09" })]),
    });
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: "List view" }));
    expect(await screen.findByRole("table", { name: "Activity list" })).toBeInTheDocument();
    expect(screen.getAllByRole("row")).toHaveLength(3);
  });

  it("keeps the filters after a re-mount", async () => {
    route(authFetch, {});
    const first = renderPage();
    await userEvent.selectOptions(await screen.findByLabelText("Zone"), "North");
    await vi.waitFor(() => expect(lastCalendarUrl()).toMatch(/zoneId=z1/));
    first.unmount();

    renderPage();
    await vi.waitFor(() => expect(lastCalendarUrl()).toMatch(/zoneId=z1/));
    expect((await screen.findByLabelText("Zone")) as HTMLSelectElement).toHaveValue("z1");
  });
});
