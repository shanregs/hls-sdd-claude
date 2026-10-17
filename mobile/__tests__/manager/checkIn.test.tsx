import { fireEvent, screen } from "@testing-library/react-native";
import { managerDay, plannedActivity, today } from "../support/managerFixtures";
import { openManagerScreen } from "../support/managerApp";

/** Spec 033 T013 and T015 (US1): the check-in form, what it sends, and what the server may refuse. */
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));

describe("the check-in form", () => {
  it("offers Office and Field only, never Leave, with Office chosen", async () => {
    await openManagerScreen("Check-in");

    expect(await screen.findByLabelText("Office day")).toBeTruthy();
    expect(screen.getByLabelText("Field day")).toBeTruthy();
    expect(screen.queryByText("Leave")).toBeNull();
    expect(screen.getByText(/Check in before 10:30/)).toBeTruthy();
    expect(screen.getByText(/after 09:30 is marked late/)).toBeTruthy();
  });

  it("checks in as Office with the position just read, and shows the server's time", async () => {
    const { manager } = await openManagerScreen("Check-in");

    await press("Check in");

    expect(await screen.findByText("Checked in at 09:42, Office")).toBeTruthy();
    expect(manager.checkIns).toEqual([
      { dayType: "OFFICE", activities: [], latitude: 12.971599, longitude: 77.594566, accuracyMeters: 18.5 },
    ]);
    // the form is gone: a second check-in is not offered
    expect(screen.queryByLabelText("Check in")).toBeNull();
    expect(screen.queryByLabelText("Field day")).toBeNull();
  });

  it("lists today's planned activities as the server returned them and sends the selected ones for a Field day", async () => {
    const { manager } = await openManagerScreen("Check-in", {
      manager: {
        today: today({
          plannedActivities: [
            plannedActivity({ id: "act-1", kind: "COLLEGE_DRIVE", place: "Anna Nagar College", status: "PLANNED" }),
            plannedActivity({ id: "act-2", kind: "MARKETING_VISIT", place: "St Mary's School", status: "PLANNED" }),
          ],
        }),
      },
    });

    await press("Field day");
    expect(await screen.findByLabelText("College drive, Anna Nagar College, planned, not selected")).toBeTruthy();
    expect(screen.getByLabelText("Marketing visit, St Mary's School, planned, not selected")).toBeTruthy();
    await press("Marketing visit, St Mary's School, planned, not selected");
    expect(screen.getByLabelText("Marketing visit, St Mary's School, planned, selected")).toBeTruthy();
    await press("Check in");

    expect(await screen.findByText("Checked in at 09:42, Field")).toBeTruthy();
    expect(manager.checkIns[0]).toMatchObject({
      dayType: "FIELD",
      activities: [{ kind: "MARKETING_VISIT", id: "act-2" }],
    });
  });

  it("sends a purpose of a Field day with no planned activity, trimmed", async () => {
    const { manager } = await openManagerScreen("Check-in");

    await press("Field day");
    await fireEvent.changeText(await screen.findByLabelText("Purpose"), "  Visit to St Mary's  ");
    await press("Check in");

    await screen.findByText("Checked in at 09:42, Field");
    expect(manager.checkIns[0]).toMatchObject({ dayType: "FIELD", purpose: "Visit to St Mary's", activities: [] });
    expect(screen.getByText("Purpose: Visit to St Mary's")).toBeTruthy();
  });

  it("sends no activities and no purpose for an Office day even after a Field draft", async () => {
    const { manager } = await openManagerScreen("Check-in", {
      manager: { today: today({ plannedActivities: [plannedActivity()] }) },
    });

    await press("Field day");
    await press("College drive, Anna Nagar College, planned, not selected");
    await fireEvent.changeText(await screen.findByLabelText("Purpose"), "Some purpose");
    await press("Office day");
    await press("Check in");

    await screen.findByText("Checked in at 09:42, Office");
    expect(manager.checkIns[0].activities).toEqual([]);
    expect(manager.checkIns[0].purpose).toBeUndefined();
  });

  it("limits the purpose to 500 characters", async () => {
    await openManagerScreen("Check-in");

    await press("Field day");

    expect((await screen.findByLabelText("Purpose")).props.maxLength).toBe(500);
    expect(screen.getByText("500 characters left")).toBeTruthy();
  });

  it("shows no form on a weekly off or holiday, or on approved full-day leave", async () => {
    await openManagerScreen("Check-in", { manager: { today: today({ workingDay: false }) } });
    expect(await screen.findByText(/weekly off or a holiday/)).toBeTruthy();
    expect(screen.queryByLabelText("Check in")).toBeNull();
  });

  it("shows no form on approved full-day leave but still offers one on half-day leave", async () => {
    const full = await openManagerScreen("Check-in", {
      manager: { today: today({ leave: { fraction: 1, unpaid: false } }) },
    });
    expect(await screen.findByText(/approved leave today/)).toBeTruthy();
    expect(screen.queryByLabelText("Check in")).toBeNull();
    expect(full.manager.checkIns).toHaveLength(0);
  });

  it("offers Check in on approved half-day leave", async () => {
    await openManagerScreen("Check-in", { manager: { today: today({ leave: { fraction: 0.5, unpaid: false } }) } });

    expect(await screen.findByLabelText("Check in")).toBeTruthy();
  });

  it("shows the day as the server holds it when the Manager has already checked in", async () => {
    await openManagerScreen("Check-in", {
      manager: { today: today({ day: managerDay({ dayType: "FIELD", purpose: "Visit", late: true, flags: ["LATE"] }) }) },
    });

    expect(await screen.findByText("Checked in at 09:42, Field")).toBeTruthy();
    expect(screen.getByText("Late")).toBeTruthy();
    expect(screen.queryByLabelText("Check in")).toBeNull();
  });
});

describe("what the server may refuse (spec 032)", () => {
  const refusals: [string, number, string][] = [
    ["a second check-in", 409, "You have already checked in today."],
    ["a locked month", 409, "Attendance for October 2026 is locked."],
    ["approved full-day leave", 400, "You are on approved leave today."],
    ["no joining date", 400, "No joining date is recorded for you."],
    ["a day outside employment", 400, "Your employment does not cover this date."],
    ["an activity that is not dated today", 404, "That activity is not planned for today."],
  ];

  it.each(refusals)("shows the server's reason for %s, records nothing and keeps the entries", async (_name, status, reason) => {
    const { manager } = await openManagerScreen("Check-in", {
      manager: {
        today: today({ plannedActivities: [plannedActivity()] }),
        checkInRefusal: () => ({ status, body: { reason } }),
      },
    });

    await press("Field day");
    await press("College drive, Anna Nagar College, planned, not selected");
    await fireEvent.changeText(await screen.findByLabelText("Purpose"), "Visit");
    await press("Check in");

    expect(await screen.findByText(reason)).toBeTruthy();
    expect(screen.queryByText(/Checked in at/)).toBeNull();
    expect(manager.today.day).toBeNull();
    // the entries are still there
    expect(screen.getByLabelText("College drive, Anna Nagar College, planned, selected")).toBeTruthy();
    expect(screen.getByLabelText("Purpose").props.value).toBe("Visit");
  });

  it("does not send a Field day with neither an activity nor a purpose, and says which is needed", async () => {
    const { manager } = await openManagerScreen("Check-in");

    await press("Field day");
    await press("Check in");

    expect(await screen.findByText(/needs a planned activity or a purpose/)).toBeTruthy();
    expect(manager.checkIns).toHaveLength(0);
  });
});
