import { act, fireEvent, screen } from "@testing-library/react-native";
import { AppState, type AppStateStatus } from "react-native";
import type { AccessModel } from "../../src/api/accessModelApi";
import { ACCESS_MODEL_REFRESH_AFTER_BACKGROUND_MS } from "../../src/config/constants";
import type { FakeServer } from "../support/fakeServer";
import { MANAGER_MODEL, TEACHER_MODEL } from "../support/fixtures";
import { openLeaveScreen } from "../support/leaveApp";
import { leaveRequest, previewDays } from "../support/leaveFixtures";

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

async function refreshMenuTo(server: FakeServer, model: AccessModel) {
  server.on("GET /api/v1/me/access-model", { status: 200, body: model });
  const realNow = Date.now();
  jest.spyOn(Date, "now").mockReturnValue(realNow);
  await act(async () => handler?.("background"));
  jest.spyOn(Date, "now").mockReturnValue(realNow + ACCESS_MODEL_REFRESH_AFTER_BACKGROUND_MS);
  await act(async () => handler?.("active"));
}

const withoutSections = (model: AccessModel, names: string[]): AccessModel => ({
  ...model,
  navigation: model.navigation.filter((s) => !names.includes(s.section)),
});

describe("a menu refresh that removes a leave screen (spec 020 FR-014)", () => {
  it("replaces Apply Leave with Not authorized and shows no form", async () => {
    const { server } = await openLeaveScreen("Apply Leave");
    await screen.findByLabelText("Check");

    await refreshMenuTo(server, withoutSections(TEACHER_MODEL, ["LEAVE"]));

    await screen.findByText("You do not have access to that page.");
    expect(screen.queryByLabelText("Check")).toBeNull();
    expect(screen.queryByLabelText("Submit")).toBeNull();
    await fireEvent.press(screen.getByLabelText("Go to home"));
    expect(await screen.findByText("Welcome, Tara")).toBeTruthy();
  });

  it("replaces an open request detail with Not authorized and no data", async () => {
    const { server } = await openLeaveScreen("Leave Management", {
      model: MANAGER_MODEL,
      leave: {
        requests: [leaveRequest({ as: "supervisor", id: "r1", teacherName: "Asha Rao" })],
        detailOf: () => ({ days: previewDays(["2026-10-12"]), problems: [] }),
      },
    });
    await fireEvent.press(await screen.findByLabelText(/^Asha Rao, Demo School One/));
    await screen.findByText("Days it would mark");

    await refreshMenuTo(server, withoutSections(MANAGER_MODEL, ["OPERATIONS"]));

    await screen.findByText("You do not have access to that page.");
    expect(screen.queryByText("Days it would mark")).toBeNull();
    expect(screen.queryByText("Asha Rao")).toBeNull();
    expect(screen.queryByLabelText("Approve")).toBeNull();
  });
});
