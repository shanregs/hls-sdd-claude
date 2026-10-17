import { fireEvent, screen } from "@testing-library/react-native";
import { pullToRefresh } from "../support/leaveActions";
import { openVisitTaskScreen } from "../support/visitTaskApp";
import { visit, visitTaskModel } from "../support/visitTaskFixtures";

/** Spec 034 T014 (US1): the month's visits, grouped and filtered, as the server describes them. */
const MENU = "Marketing Calendar";
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));

const OCT = [
  visit({ id: "today", date: "2026-10-05", prospect: { id: "p-1", name: "Today School" } }),
  visit({ id: "next", date: "2026-10-20", type: "CALL", prospect: { id: "p-2", name: "Later School" } }),
  visit({ id: "soon", date: "2026-10-07", prospect: { id: "p-3", name: "Soon School" } }),
  visit({ id: "old", date: "2026-10-01", effectiveStatus: "MISSED", prospect: { id: "p-4", name: "Missed School" } }),
  visit({ id: "done", date: "2026-10-02", status: "COMPLETED", effectiveStatus: "COMPLETED", outcome: "Met", prospect: { id: "p-5", name: "Done School" } }),
  visit({ id: "off", date: "2026-10-03", status: "CANCELLED", effectiveStatus: "CANCELLED", cancelReason: "Away", prospect: { id: "p-6", name: "Off School" } }),
];

describe("the month's visits", () => {
  it("opens on the current month, asks the server for that range for the Manager, and groups today, later and earlier", async () => {
    const { server } = await openVisitTaskScreen(MENU, { marketing: { visits: OCT } });

    expect(await screen.findByText("Today School")).toBeTruthy();
    // Home also asks for today alone, for its card; the month is the request that spans the whole month
    const call = server.callsTo("GET /api/v1/marketing/activities").find((c) => c.query.to === "2026-10-31");
    expect(call?.query).toEqual({ from: "2026-10-01", to: "2026-10-31", mine: "true" });

    const headers = screen.getAllByRole("header").map((n) => n.props.children);
    expect(headers).toEqual(expect.arrayContaining(["Today", "Later", "Earlier"]));
    // order on screen: today, then later in date order, then earlier newest first
    const names = screen.getAllByLabelText(/^(Visit|Call) with /).map((n) => n.props.accessibilityLabel.split(",")[0]);
    expect(names).toEqual([
      "Visit with Today School",
      "Visit with Soon School",
      "Call with Later School",
      "Visit with Off School",
      "Visit with Done School",
      "Visit with Missed School",
    ]);
  });

  it("shows each visit's date, kind and the server's status in words, and Missed only when the server says so", async () => {
    await openVisitTaskScreen(MENU, { marketing: { visits: OCT } });

    expect(await screen.findByLabelText("Visit with Today School, 05/10/2026, planned")).toBeTruthy();
    expect(screen.getByLabelText("Call with Later School, 20/10/2026, planned")).toBeTruthy();
    expect(screen.getByLabelText("Visit with Missed School, 01/10/2026, missed")).toBeTruthy();
    expect(screen.getByLabelText("Visit with Done School, 02/10/2026, completed")).toBeTruthy();
    expect(screen.getByLabelText("Visit with Off School, 03/10/2026, cancelled")).toBeTruthy();
  });

  it("does not work out Missed itself: a past date the server still calls planned shows Planned", async () => {
    const stale = visit({ id: "stale", date: "2026-10-01", effectiveStatus: "PLANNED", prospect: { id: "p-9", name: "Server Says Planned" } });
    await openVisitTaskScreen(MENU, { marketing: { visits: [stale] } });

    expect(await screen.findByLabelText("Visit with Server Says Planned, 01/10/2026, planned")).toBeTruthy();
    expect(screen.queryByLabelText(/Server Says Planned.*missed/)).toBeNull();
  });

  it("marks a visit whose date was moved", async () => {
    await openVisitTaskScreen(MENU, {
      marketing: { visits: [visit({ rescheduled: true, dateHistory: [{ from: "2026-10-08", to: "2026-10-06" }] })] },
    });

    expect(await screen.findByLabelText("Visit with Green Valley Public School, 06/10/2026, planned, moved")).toBeTruthy();
  });
});

describe("the status filter", () => {
  it.each([
    ["Planned", ["Today School", "Soon School", "Later School"], ["Missed School", "Done School", "Off School"]],
    ["Completed", ["Done School"], ["Today School", "Missed School", "Off School"]],
    ["Cancelled", ["Off School"], ["Today School", "Done School"]],
    ["Missed", ["Missed School"], ["Today School", "Done School", "Off School"]],
  ])("%s shows only those visits", async (label, shown, hidden) => {
    await openVisitTaskScreen(MENU, { marketing: { visits: OCT } });
    await screen.findByText("Today School");

    await press(label);

    for (const name of shown) expect(screen.getByText(name)).toBeTruthy();
    for (const name of hidden) expect(screen.queryByText(name)).toBeNull();
    await press("All");
    expect(screen.getByText("Off School")).toBeTruthy();
  });

  it("keeps the chosen filter while a visit is opened and closed", async () => {
    await openVisitTaskScreen(MENU, { marketing: { visits: OCT } });
    await screen.findByText("Today School");
    await press("Completed");

    await press("Visit with Done School, 02/10/2026, completed");
    await screen.findByText("Outcome");
    await press("Back to the visits");

    expect(await screen.findByText("Done School")).toBeTruthy();
    expect(screen.queryByText("Today School")).toBeNull();
  });
});

describe("moving between months", () => {
  it("loads the next month and the previous one from the server", async () => {
    const { server, marketing } = await openVisitTaskScreen(MENU, {
      marketing: { visits: [...OCT, visit({ id: "nov", date: "2026-11-10", prospect: { id: "p-7", name: "November School" } })] },
    });
    await screen.findByText("Today School");
    expect(marketing.visits.length).toBe(7);

    await press("Next month");

    expect(await screen.findByText("November School")).toBeTruthy();
    expect(screen.queryByText("Today School")).toBeNull();
    const months = server.callsTo("GET /api/v1/marketing/activities").map((c) => c.query.from).filter((from) => from.endsWith("-01"));
    expect(months).toEqual(["2026-10-01", "2026-11-01"]);
    await press("Previous month");
    expect(await screen.findByText("Today School")).toBeTruthy();
  });

  it("says when a month has no visits, and points to planning one only with the Create grant", async () => {
    await openVisitTaskScreen(MENU, { marketing: { visits: [] } });
    expect(await screen.findByText("No visits this month. Plan a visit to add one.")).toBeTruthy();
  });

  it("says no visits without the plan hint when Create is not granted", async () => {
    await openVisitTaskScreen(MENU, { model: visitTaskModel({ visits: { create: false } }), marketing: { visits: [] } });
    expect(await screen.findByText("No visits this month.")).toBeTruthy();
  });
});

describe("when the list cannot be loaded", () => {
  it("shows the server's reason with Retry and no stale rows", async () => {
    const { marketing } = await openVisitTaskScreen(MENU, { marketing: { visits: OCT } });
    await screen.findByText("Today School");
    marketing.refusals.list = { status: 500, body: { reason: "Could not load the visits." } };

    await press("Next month");

    expect(await screen.findByText("Could not load the visits.")).toBeTruthy();
    expect(screen.getByLabelText("Retry")).toBeTruthy();
    expect(screen.queryByText("Today School")).toBeNull();
  });

  it("says there is no connection", async () => {
    await openVisitTaskScreen(MENU, {
      prepare: (server) =>
        server.on("GET /api/v1/marketing/activities", () => {
          throw new TypeError("Network request failed");
        }),
    });

    expect(await screen.findByText("No connection")).toBeTruthy();
  });

  it("reloads from the server on a pull down, picking up a visit planned on the web", async () => {
    const { marketing } = await openVisitTaskScreen(MENU, { marketing: { visits: [OCT[0]] } });
    await screen.findByText("Today School");
    marketing.visits = [...marketing.visits, OCT[2]];

    await pullToRefresh();

    expect(await screen.findByText("Soon School")).toBeTruthy();
  });
});
