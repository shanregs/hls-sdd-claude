import { fireEvent, screen } from "@testing-library/react-native";
import { chooseDate } from "../support/leaveActions";
import { openVisitTaskScreen } from "../support/visitTaskApp";
import { prospect, visitTaskModel } from "../support/visitTaskFixtures";

/** Spec 034 T034 (US4): planning a visit against a prospect. */
const MENU = "Marketing Calendar";
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));

const PROSPECTS = [
  prospect({ id: "p-1", name: "Green Valley Public School", zoneName: "Demo Zone", contactPerson: "Mrs Rao" }),
  prospect({ id: "p-2", name: "Demo School One", won: true, schoolId: "s-1", zoneName: "Demo Zone", contactPerson: "R. Kumar" }),
  prospect({ id: "p-3", name: "Lakeside Academy", zoneName: "North Zone", contactPerson: "Mr Bose" }),
];

async function openForm(setup: Parameters<typeof openVisitTaskScreen>[1] = {}) {
  const app = await openVisitTaskScreen(MENU, { marketing: { visits: [], prospects: PROSPECTS }, ...setup });
  await press("Plan a visit");
  await screen.findByLabelText("Search a School or prospect");
  return app;
}

describe("the Plan a visit button", () => {
  it("is offered only with the Create grant", async () => {
    await openVisitTaskScreen(MENU, { model: visitTaskModel({ visits: { create: false } }), marketing: { visits: [] } });

    await screen.findByText("No visits this month.");
    expect(screen.queryByLabelText("Plan a visit")).toBeNull();
  });
});

describe("the form", () => {
  it("lists the prospects with Zone and contact person, marking a won prospect as a School, and searches as the Manager types", async () => {
    const { marketing } = await openForm();

    expect(await screen.findByText("Green Valley Public School, Demo Zone, Mrs Rao")).toBeTruthy();
    expect(screen.getByText("Demo School One (School), Demo Zone, R. Kumar")).toBeTruthy();
    await fireEvent.changeText(screen.getByLabelText("Search a School or prospect"), "lake");

    expect(await screen.findByText("Lakeside Academy, North Zone, Mr Bose")).toBeTruthy();
    expect(screen.queryByText("Green Valley Public School, Demo Zone, Mrs Rao")).toBeNull();
    expect(marketing.prospectQueries).toEqual(expect.arrayContaining(["", "lake"]));
  });

  it("says a search matches nothing", async () => {
    await openForm();

    await fireEvent.changeText(screen.getByLabelText("Search a School or prospect"), "zzz");

    expect(await screen.findByText("No School or prospect matches.")).toBeTruthy();
  });

  it("keeps Send disabled without a prospect and a date, and says which is still needed", async () => {
    await openForm();
    await screen.findByLabelText("Choose Green Valley Public School");

    expect(screen.getByLabelText("Send").props.accessibilityState?.disabled).toBe(true);
    expect(screen.getByText("Still needed: a School or prospect and a date.")).toBeTruthy();
    await press("Choose Green Valley Public School");
    expect(screen.getByText("Still needed: a date.")).toBeTruthy();
    await chooseDate("Date" as never, "not chosen", "Wednesday 7 October 2026");
    expect(screen.queryByText(/Still needed/)).toBeNull();
    expect(screen.getByLabelText("Send").props.accessibilityState?.disabled).toBe(false);
  });

  it("limits the notes to 1000 characters", async () => {
    await openForm();

    expect(screen.getByLabelText("Notes").props.maxLength).toBe(1000);
  });
});

describe("sending", () => {
  it("posts the prospect, kind, date, colleagues and trimmed notes, then opens the new visit", async () => {
    const { marketing } = await openForm();
    await press("Choose Green Valley Public School");
    await press("Call");
    await chooseDate("Date" as never, "not chosen", "Wednesday 7 October 2026");
    await press("Divya Director, not attending");
    await fireEvent.changeText(screen.getByLabelText("Notes"), "  Ask about the timetable  ");

    await press("Send");

    expect(await screen.findByText("Call, 07/10/2026")).toBeTruthy();
    expect(marketing.planned).toEqual([
      { prospectId: "p-1", type: "CALL", date: "2026-10-07", attendeeUserIds: ["u-2"], notes: "Ask about the timetable" },
    ]);
    expect(screen.getByText("Attending: Divya Director")).toBeTruthy();
    expect(screen.getByText("Notes: Ask about the timetable")).toBeTruthy();
  });

  it("leaves out the colleagues and notes when none are given, so the server records the Manager", async () => {
    const { marketing } = await openForm();
    await press("Choose Demo School One");
    await chooseDate("Date" as never, "not chosen", "Wednesday 7 October 2026");

    await press("Send");

    expect(await screen.findByText("Visit, 07/10/2026")).toBeTruthy();
    expect(marketing.planned[0]).toEqual({ prospectId: "p-2", type: "VISIT", date: "2026-10-07" });
    expect(screen.getByText("Attending: Manoj Manager")).toBeTruthy();
  });

  it("shows the new visit in the month list after going back", async () => {
    await openForm();
    await press("Choose Green Valley Public School");
    await chooseDate("Date" as never, "not chosen", "Wednesday 7 October 2026");
    await press("Send");
    await screen.findByText("Visit, 07/10/2026");

    await press("Back to the visits");

    expect(await screen.findByLabelText("Visit with Green Valley Public School, 07/10/2026, planned")).toBeTruthy();
  });

  it.each([
    ["a date in the past", 400, "The date cannot be in the past."],
    ["a prospect outside the Manager's Zones", 404, "Prospect not found."],
    ["a rule", 400, "A visit is for a prospect or for a School, not both and not neither."],
  ])("shows the server's reason for %s, plans nothing and keeps every entry", async (_name, status, reason) => {
    const { marketing } = await openForm({
      marketing: { visits: [], prospects: PROSPECTS, refusals: { plan: { status, body: { reason } } } },
    });
    await press("Choose Green Valley Public School");
    await chooseDate("Date" as never, "not chosen", "Wednesday 7 October 2026");
    await fireEvent.changeText(screen.getByLabelText("Notes"), "Ask about the timetable");

    await press("Send");

    expect(await screen.findByText(reason)).toBeTruthy();
    expect(marketing.visits).toHaveLength(0);
    expect(screen.getByText("Chosen: Green Valley Public School")).toBeTruthy();
    expect(screen.getByLabelText("Date, Wednesday 7 October 2026")).toBeTruthy();
    expect(screen.getByLabelText("Notes").props.value).toBe("Ask about the timetable");
  });

  it("says nothing was sent when offline and keeps the entries", async () => {
    const { server, marketing } = await openForm();
    await press("Choose Green Valley Public School");
    await chooseDate("Date" as never, "not chosen", "Wednesday 7 October 2026");
    server.offline = true;

    await press("Send");

    expect(await screen.findByText("No connection. Nothing was sent.")).toBeTruthy();
    expect(marketing.visits).toHaveLength(0);
    expect(screen.getByText("Chosen: Green Valley Public School")).toBeTruthy();
  });

  it("sends one request for a double tap", async () => {
    const { marketing } = await openForm();
    await press("Choose Green Valley Public School");
    await chooseDate("Date" as never, "not chosen", "Wednesday 7 October 2026");
    const send = screen.getByLabelText("Send");

    await fireEvent.press(send);
    await fireEvent.press(send);

    await screen.findByText("Visit, 07/10/2026");
    expect(marketing.planned).toHaveLength(1);
  });
});
