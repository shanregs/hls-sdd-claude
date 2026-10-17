import { fireEvent, screen } from "@testing-library/react-native";
import { openAttendanceScreen } from "../support/attendanceApp";
import { managerDay, managerFieldModel, plannedActivity, today } from "../support/managerFixtures";
import { openManagerScreen } from "../support/managerApp";
import { DIRECTOR_MODEL } from "../support/fixtures";

/** Spec 033 T027 (US3): the Manager's day on Home. */
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));
const pressText = async (text: string) => fireEvent.press(await screen.findByText(text));

describe("when the card appears", () => {
  it("is on Home for a user whose menu offers the check-in screen, with the headline from the server", async () => {
    await openManagerScreen(null);

    expect(await screen.findByLabelText("Today")).toBeTruthy();
    expect(await screen.findByText("Not checked in")).toBeTruthy();
    expect(screen.getByText(/Check in before 10:30/)).toBeTruthy();
  });

  it("is not on a Teacher's Home, and today is never requested for one", async () => {
    const { server } = await openAttendanceScreen(null);

    expect(screen.queryByLabelText("Today")).toBeNull();
    expect(server.calls.filter((c) => c.path.endsWith("/manager-attendance/me/today"))).toHaveLength(0);
  });

  it("is not on a Director's Home", async () => {
    const { server } = await openAttendanceScreen(null, { model: DIRECTOR_MODEL });

    expect(screen.queryByLabelText("Today")).toBeNull();
    expect(server.calls.filter((c) => c.path.endsWith("/manager-attendance/me/today"))).toHaveLength(0);
  });
});

describe("the three states", () => {
  it("opens Check-in from the Check in button", async () => {
    await openManagerScreen(null);

    await press("Check in");

    expect(await screen.findByLabelText("Office day")).toBeTruthy();
  });

  it("shows the check-in time and day type once checked in, with Check out", async () => {
    await openManagerScreen(null, {
      manager: { today: today({ day: managerDay({ dayType: "FIELD", late: true, flags: ["LATE"] }) }) },
    });

    expect(await screen.findByText("Checked in at 09:42, Field")).toBeTruthy();
    expect(screen.getByText("Late")).toBeTruthy();
    expect(screen.getByLabelText("Check out")).toBeTruthy();
    expect(screen.queryByLabelText("Check in")).toBeNull();
  });

  it("changes to checked out after the server confirms the check-out, without leaving Home", async () => {
    const { manager } = await openManagerScreen(null, { manager: { today: today({ day: managerDay() }) } });
    manager.now = "2026-10-05T12:35:00Z";

    await press("Check out");

    expect(await screen.findByText("Checked out at 18:05")).toBeTruthy();
    expect(manager.checkOuts).toHaveLength(1);
    expect(screen.queryByLabelText("Check out")).toBeNull();
  });

  it("says Checked out when the server already has a check-out", async () => {
    await openManagerScreen(null, {
      manager: { today: today({ day: managerDay({ checkOutAt: "2026-10-05T12:35:00Z" }) }) },
    });

    expect(await screen.findByText("Checked out at 18:05")).toBeTruthy();
    expect(screen.queryByLabelText("Check out")).toBeNull();
  });

  it.each([
    ["a weekly off or a holiday", today({ workingDay: false }), "Today is a weekly off or a holiday"],
    ["approved full-day leave", today({ leave: { fraction: 1, unpaid: false } }), "You are on approved leave today"],
  ])("says so on %s and offers no Check in", async (_name, view, text) => {
    await openManagerScreen(null, { manager: { today: view } });

    expect(await screen.findByText(text)).toBeTruthy();
    expect(screen.queryByLabelText("Check in")).toBeNull();
  });

  it("still offers Check in after the cut-off, and shows the Late mark only as the server returns it", async () => {
    await openManagerScreen(null, { manager: { today: today({ missedCutoff: "08:00:00" }) } });

    expect(await screen.findByLabelText("Check in")).toBeTruthy();
    expect(screen.getByText(/Check in before 08:00/)).toBeTruthy();
    expect(screen.queryByText("Late")).toBeNull();
  });

  it("hides the buttons without the Create action", async () => {
    await openManagerScreen(null, { model: managerFieldModel({ create: false }) });

    expect(await screen.findByText("Not checked in")).toBeTruthy();
    expect(screen.queryByLabelText("Check in")).toBeNull();
  });
});

describe("today's plan", () => {
  const plan = [
    plannedActivity({ id: "a1", kind: "TASK", place: null, status: "OPEN" }),
    plannedActivity({ id: "a2", kind: "COLLEGE_DRIVE", place: "Anna Nagar College", status: "PLANNED" }),
  ];

  it("lists the activities in the server's order with kind, place and status", async () => {
    await openManagerScreen(null, { manager: { today: today({ plannedActivities: plan }) } });

    expect(await screen.findByLabelText("Task, open. Show details")).toBeTruthy();
    const rows = screen.getAllByLabelText(/Show details/).map((n) => n.props.accessibilityLabel);
    expect(rows).toEqual(["Task, open. Show details", "College drive, Anna Nagar College, planned. Show details"]);
  });

  it("says there are none", async () => {
    await openManagerScreen(null);

    expect(await screen.findByText("No planned activities for today.")).toBeTruthy();
  });

  it("opens an activity's details: kind, place, date and status, and nothing invented", async () => {
    await openManagerScreen(null, { manager: { today: today({ plannedActivities: plan }) } });

    await press("College drive, Anna Nagar College, planned. Show details");

    expect(await screen.findByText("Place: Anna Nagar College")).toBeTruthy();
    expect(screen.getByText("Date: 05/10/2026")).toBeTruthy();
    expect(screen.getByText("Status: planned")).toBeTruthy();
    await press("Close");
    expect(screen.queryByText("Place: Anna Nagar College")).toBeNull();
  });
});

describe("days still missing a check-out", () => {
  it("lists each with Complete check-out and clears it after the server completes it", async () => {
    const { manager } = await openManagerScreen(null, {
      manager: { today: today({ missingCheckOutDates: ["2026-10-02", "2026-10-03"] }) },
    });

    expect(await screen.findByText("No check-out on 02/10/2026")).toBeTruthy();
    expect(screen.getByText("No check-out on 03/10/2026")).toBeTruthy();
    await press("Complete check-out for 03/10/2026");
    await fireEvent.changeText(await screen.findByLabelText("Check-out time"), "17:30");
    await fireEvent.changeText(screen.getByLabelText("Reason"), "Left in a hurry");
    await pressText("Send");

    await screen.findByText("No check-out on 02/10/2026");
    expect(manager.completions).toEqual([{ date: "2026-10-03", time: "17:30", reason: "Left in a hurry" }]);
  });
});

describe("when today cannot be loaded", () => {
  it("shows the server's reason with Try again and no stale day", async () => {
    await openManagerScreen(null, {
      prepare: (server) =>
        server.on("GET /api/v1/manager-attendance/me/today", { status: 500, body: { reason: "Could not load today." } }),
    });

    expect(await screen.findByText("Could not load today.")).toBeTruthy();
    expect(screen.getByText("Try again")).toBeTruthy();
    expect(screen.queryByText("Not checked in")).toBeNull();
  });

  it("says no connection when the server cannot be reached, and loads on Try again once it can", async () => {
    const { server } = await openManagerScreen(null, {
      prepare: (s) =>
        s.on("GET /api/v1/manager-attendance/me/today", () => {
          throw new TypeError("Network request failed");
        }),
    });
    expect(await screen.findByText("No connection. Today could not be loaded.")).toBeTruthy();
    server.on("GET /api/v1/manager-attendance/me/today", { status: 200, body: today({ day: managerDay() }) });

    await pressText("Try again");

    expect(await screen.findByText("Checked in at 09:42, Office")).toBeTruthy();
  });
});
