import { act, fireEvent, screen } from "@testing-library/react-native";
import { AppState, type AppStateStatus } from "react-native";
import { ACCESS_MODEL_REFRESH_AFTER_BACKGROUND_MS } from "../../src/config/constants";
import { TEACHER_MODEL } from "../support/fixtures";
import { openAttendanceScreen } from "../support/attendanceApp";

let handler: ((state: AppStateStatus) => void) | undefined;
// Every listener registered for AppState changes (the access model and the notification bell both register one).
let handlers: ((state: AppStateStatus) => void)[] = [];

beforeEach(() => {
  handler = undefined;
  handlers = [];
  jest.spyOn(AppState, "addEventListener").mockImplementation(((
    _type: string,
    listener: (s: AppStateStatus) => void,
  ) => {
    handlers.push(listener);
    handler = (state) => handlers.forEach((h) => h(state));
    return { remove: () => undefined };
  }) as never);
});

afterEach(() => jest.restoreAllMocks());

describe("a menu refresh that removes an attendance screen (spec 019 FR-013)", () => {
  it("replaces My Attendance with Not authorized and shows no attendance data", async () => {
    const { server } = await openAttendanceScreen("My Attendance");
    await screen.findByLabelText("Monday 5 October 2026, not marked");

    server.on("GET /api/v1/me/access-model", {
      status: 200,
      body: {
        ...TEACHER_MODEL,
        navigation: TEACHER_MODEL.navigation.filter((s) => s.section === "Dashboard"),
      },
    });
    const realNow = Date.now();
    jest.spyOn(Date, "now").mockReturnValue(realNow);
    await act(async () => handler?.("background"));
    jest.spyOn(Date, "now").mockReturnValue(realNow + ACCESS_MODEL_REFRESH_AFTER_BACKGROUND_MS);
    await act(async () => handler?.("active"));

    await screen.findByText("You do not have access to that page.");
    expect(screen.queryByLabelText("Monday 5 October 2026, not marked")).toBeNull();
    expect(screen.queryByLabelText(/^Working days/)).toBeNull();

    await fireEvent.press(screen.getByLabelText("Go to home"));
    expect(await screen.findByText("Welcome, Tara")).toBeTruthy();
  });
});
