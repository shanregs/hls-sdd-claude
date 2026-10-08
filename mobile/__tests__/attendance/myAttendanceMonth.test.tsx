import { fireEvent, screen } from "@testing-library/react-native";
import { notification } from "../support/notificationsFixtures";
import { openNotificationsScreen } from "../support/notificationsApp";
import { openAttendanceScreen } from "../support/attendanceApp";

afterEach(() => jest.restoreAllMocks());

const ATTENDANCE = "GET /api/v1/attendance/me";
const titled = (title: string, link: string | null) => notification({ title, message: `${title} message`, link });
const open = async (title: string) => fireEvent.press(await screen.findByLabelText(new RegExp(`^(Unread. )?${title}.`)));

describe("My Attendance opened from a notification link (spec 021 research §2)", () => {
  it("opens the month in the link", async () => {
    const { server } = await openNotificationsScreen({ notifications: { items: [titled("Lock", "/my-attendance?month=2026-08")] } });

    await open("Lock");

    expect(await screen.findByText("August 2026")).toBeTruthy();
    expect(server.callsTo(ATTENDANCE).map((c) => c.query.month)).toEqual(["2026-08"]);
  });

  it("offers the previous year's months when the link is in the previous year (a December lock seen in January)", async () => {
    const { server } = await openNotificationsScreen({
      today: "2026-01-06",
      notifications: { items: [titled("December lock", "/my-attendance?month=2025-12")] },
    });

    await open("December lock");

    expect(await screen.findByText("December 2025")).toBeTruthy();
    expect(server.callsTo(ATTENDANCE).map((c) => c.query.month)).toEqual(["2025-12"]);
    expect(screen.getByLabelText("Previous month")).toBeTruthy();
  });

  it.each([
    ["a month outside the previous and current year", "/my-attendance?month=2024-05"],
    ["a malformed month", "/my-attendance?month=abc"],
    ["no month", "/my-attendance"],
  ])("opens the current month for %s", async (_name, link) => {
    const { server } = await openNotificationsScreen({ notifications: { items: [titled("Odd link", link)] } });

    await open("Odd link");

    expect(await screen.findByText("October 2026")).toBeTruthy();
    expect(server.callsTo(ATTENDANCE).map((c) => c.query.month)).toEqual(["2026-10"]);
  });

  it("re-opens on another month when a second link is followed", async () => {
    await openNotificationsScreen({
      notifications: { items: [titled("First", "/my-attendance?month=2026-08"), titled("Second", "/my-attendance?month=2026-09")] },
    });
    await open("First");
    await screen.findByText("August 2026");

    await fireEvent.press(await screen.findByLabelText(/^Notifications, \d+ unread$/));
    await open("Second");

    expect(await screen.findByText("September 2026")).toBeTruthy();
  });

  it("is unchanged when My Attendance is opened from the menu", async () => {
    const { server } = await openAttendanceScreen("My Attendance");

    expect(await screen.findByText("October 2026")).toBeTruthy();
    expect(server.callsTo(ATTENDANCE).map((c) => c.query.month)).toEqual(["2026-10"]);
  });
});
