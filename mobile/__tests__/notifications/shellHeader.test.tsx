import { act, fireEvent, screen } from "@testing-library/react-native";
import { AppState, type AppStateStatus } from "react-native";
import { ACCESS_MODEL_REFRESH_AFTER_BACKGROUND_MS } from "../../src/config/constants";
import { TEACHER_MODEL } from "../support/fixtures";
import { notification } from "../support/notificationsFixtures";
import { openNotificationsScreen } from "../support/notificationsApp";
import { chooseMenuItem } from "../support/navigation";

afterEach(() => jest.restoreAllMocks());

const items = () => [notification(), notification(), notification()];

describe("the bell in the shell header (spec 021 research §3)", () => {
  it.each(["My Attendance", "Apply Leave", "My Leave History"])("is shown on %s", async (label) => {
    await openNotificationsScreen({ open: "home", notifications: { items: items() } });
    await screen.findByLabelText("Notifications, 3 unread");

    await chooseMenuItem(label);

    expect(await screen.findByLabelText("Notifications, 3 unread")).toBeTruthy();
  });

  it("is shown on the Not authorized screen, which has the same header", async () => {
    const handlers: ((state: AppStateStatus) => void)[] = [];
    jest.spyOn(AppState, "addEventListener").mockImplementation(((_type: string, listener: (s: AppStateStatus) => void) => {
      handlers.push(listener);
      return { remove: () => undefined };
    }) as never);
    const { server } = await openNotificationsScreen({ open: "home", notifications: { items: items() } });
    await screen.findByLabelText("Notifications, 3 unread");
    await chooseMenuItem("My Attendance");
    // Permissions changed on the web: My Attendance is gone, Notifications is not.
    server.on("GET /api/v1/me/access-model", {
      status: 200,
      body: {
        ...TEACHER_MODEL,
        navigation: TEACHER_MODEL.navigation.filter((s) => s.section === "Dashboard" || s.section === "ACCOUNT"),
      },
    });
    const realNow = Date.now();
    await act(async () => handlers.forEach((h) => h("background")));
    jest.spyOn(Date, "now").mockReturnValue(realNow + ACCESS_MODEL_REFRESH_AFTER_BACKGROUND_MS);
    await act(async () => handlers.forEach((h) => h("active")));

    await screen.findByText("You do not have access to that page.");
    expect(await screen.findByLabelText("Notifications, 3 unread")).toBeTruthy();
  });

  it("keeps its count across the full-screen Devices sub-screen, which has no bell of its own", async () => {
    const { server } = await openNotificationsScreen({ open: "home", notifications: { items: items() } });
    server.on("GET /api/v1/me/sessions", { status: 200, body: [] });
    await screen.findByLabelText("Notifications, 3 unread");
    await chooseMenuItem("My Profile");
    await screen.findByText("Appearance");
    await fireEvent.press(screen.getByLabelText("Signed-in devices"));

    expect(await screen.findByText("Signed-in devices")).toBeTruthy();
    expect(screen.queryByLabelText(/^Notifications,/)).toBeNull();

    await fireEvent.press(screen.getByLabelText("Back"));
    expect(await screen.findByLabelText("Notifications, 3 unread")).toBeTruthy();
    // The provider was not reset by the sub-screen: the count was not asked for again from nothing.
    expect(screen.queryByLabelText("Notifications, count unavailable")).toBeNull();
  });
});
