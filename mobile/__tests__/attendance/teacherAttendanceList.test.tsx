import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import { gridRow, rollup } from "../support/attendanceFixtures";
import { openAttendanceScreen } from "../support/attendanceApp";
import { MANAGER_MODEL } from "../support/fixtures";

afterEach(() => jest.restoreAllMocks());

const asha = gridRow({
  teacherId: "t-asha",
  name: "Asha Rao",
  rollup: rollup({ daysWorked: 12, daysLeave: 1, unmarked: 5 }),
});
const bala = gridRow({
  teacherId: "t-bala",
  name: "Bala Nair",
  school: { id: "school-2", name: "Demo School Two" },
  rollup: rollup({ daysWorked: 9, daysLeave: 0, unmarked: 8 }),
  locked: true,
});

const GRID = "GET /api/v1/attendance/teacher-grid";

const open = (grid = [asha, bala]) =>
  openAttendanceScreen("Teacher Attendance", { model: MANAGER_MODEL, attendance: { grid } });

describe("Teacher Attendance list (spec 019 US4)", () => {
  it("shows only the Teachers the server returned, with School and the server's rollup figures", async () => {
    // Another Manager's Teacher exists on the server but is not in this Manager's answer.
    const { server } = await open();

    expect(await screen.findByLabelText(/^Asha Rao, Demo School One\. Worked 12, leave 1, unmarked 5$/)).toBeTruthy();
    expect(screen.getByLabelText(/^Bala Nair, Demo School Two\. Worked 9, leave 0, unmarked 8, locked$/)).toBeTruthy();
    expect(screen.queryByText("Chitra Menon")).toBeNull();
    expect(screen.getByText("Worked 12 · Leave 1 · Unmarked 5")).toBeTruthy();

    const calls = server.callsTo(GRID);
    expect(calls).toHaveLength(1);
    expect(calls[0].query).toMatchObject({ month: "2026-10", page: "0", size: "25" });
    expect(calls[0].query.query).toBeUndefined();
  });

  it("always shows the search box and sends the term after the delay, narrowing the list", async () => {
    const { server } = await open();
    await screen.findByText("Asha Rao");
    expect(screen.getByLabelText("Search Teachers")).toBeTruthy();

    await fireEvent.changeText(screen.getByLabelText("Search Teachers"), "bal");
    expect(server.callsTo(GRID)).toHaveLength(1); // not sent immediately

    await waitFor(() => expect(server.callsTo(GRID)).toHaveLength(2));
    expect(server.callsTo(GRID)[1].query.query).toBe("bal");
    await waitFor(() => expect(screen.queryByText("Asha Rao")).toBeNull());
    expect(screen.getByText("Bala Nair")).toBeTruthy();
  });

  it("sends one request for fast typing, not one per key", async () => {
    const { server } = await open();
    await screen.findByText("Asha Rao");

    for (const text of ["a", "as", "ash"]) {
      await fireEvent.changeText(screen.getByLabelText("Search Teachers"), text);
    }
    await waitFor(() => expect(server.callsTo(GRID)).toHaveLength(2));
    await new Promise((resolve) => setTimeout(resolve, 450));
    expect(server.callsTo(GRID)).toHaveLength(2);
    expect(server.callsTo(GRID)[1].query.query).toBe("ash");
  });

  it("loads the next page of 25 on Load more", async () => {
    const many = Array.from({ length: 30 }, (_, i) =>
      gridRow({ teacherId: `t-${i}`, name: `Teacher ${String(i).padStart(2, "0")}` }),
    );
    const { server } = await open(many);

    await screen.findByText("Teacher 00");
    expect(screen.getByText("Teacher 24")).toBeTruthy();
    expect(screen.queryByText("Teacher 25")).toBeNull();

    await fireEvent.press(screen.getByLabelText("Load more"));
    expect(await screen.findByText("Teacher 29")).toBeTruthy();
    expect(server.callsTo(GRID)[1].query).toMatchObject({ page: "1", size: "25" });
    expect(screen.queryByLabelText("Load more")).toBeNull();
  });

  it("reloads for another month", async () => {
    const { server } = await open();
    await screen.findByText("Asha Rao");

    await fireEvent.press(screen.getByLabelText("Previous month"));
    await waitFor(() => expect(server.callsTo(GRID)).toHaveLength(2));
    expect(server.callsTo(GRID)[1].query.month).toBe("2026-09");
    expect(await screen.findByText("September 2026")).toBeTruthy();
  });

  it("says No Teachers found for an empty list", async () => {
    await open([]);

    expect(await screen.findByText("No Teachers found")).toBeTruthy();
  });

  it("shows an error with Retry for a failed load and recovers", async () => {
    let failing = true;
    await openAttendanceScreen("Teacher Attendance", {
      model: MANAGER_MODEL,
      prepare: (server) =>
        server.on(GRID, () =>
          failing
            ? { status: 500, body: {} }
            : {
                status: 200,
                body: { month: "2026-10", days: 31, content: [asha], page: 0, size: 25, totalElements: 1 },
              },
        ),
    });

    expect(await screen.findByText("Something went wrong")).toBeTruthy();
    failing = false;
    await fireEvent.press(screen.getByLabelText("Retry"));
    expect(await screen.findByText("Asha Rao")).toBeTruthy();
  });
});
