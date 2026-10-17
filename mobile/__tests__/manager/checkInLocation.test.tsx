import { fireEvent, screen } from "@testing-library/react-native";
import * as Location from "expo-location";
import { Linking } from "react-native";
import { setLocationTimings } from "../../src/location/LocationGate";
import { openManagerScreen } from "../support/managerApp";

/** Spec 033 T014 (US1): the position a check-in needs, and what happens when there is none. */
const state = jest.requireMock("expo-location").__state as Record<string, unknown>;
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));

const DENIED = "Check-in needs your location. Allow location for HLS in your phone settings, then try again.";
const SERVICES_OFF = "Turn on location (GPS) on your phone, then try again.";
const NO_FIX = "Your phone could not find its position. Move to an open area and try again.";

describe("when there is no position", () => {
  it("sends nothing and explains how to allow location when the permission is refused", async () => {
    const { manager, server } = await openManagerScreen("Check-in");
    state.permission = "denied";
    state.canAskAgain = false;
    const openSettings = jest.spyOn(Linking, "openSettings").mockResolvedValue();

    await press("Check in");

    expect(await screen.findByText(DENIED)).toBeTruthy();
    expect(server.callsTo("POST /api/v1/manager-attendance/me/check-in")).toHaveLength(0);
    expect(manager.checkIns).toHaveLength(0);
    expect(screen.queryByText(/Checked in at/)).toBeNull();
    await fireEvent.press(await screen.findByText("Open settings"));
    expect(openSettings).toHaveBeenCalled();
  });

  it("says to turn location on when the phone's services are off, with no Open settings", async () => {
    const { manager } = await openManagerScreen("Check-in");
    state.servicesEnabled = false;

    await press("Check in");

    expect(await screen.findByText(SERVICES_OFF)).toBeTruthy();
    expect(screen.queryByText("Open settings")).toBeNull();
    expect(manager.checkIns).toHaveLength(0);
  });

  it("says the phone could not find its position when no fix arrives in time", async () => {
    const { manager } = await openManagerScreen("Check-in");
    setLocationTimings({ waitSeconds: 0.05, reuseSeconds: 10 });
    state.neverResolves = true;

    await press("Check in");

    expect(await screen.findByText(NO_FIX)).toBeTruthy();
    expect(manager.checkIns).toHaveLength(0);
  });

  it("keeps every entry, and a retry after fixing the cause sends the check-in", async () => {
    const { manager } = await openManagerScreen("Check-in");
    state.permission = "denied";
    state.canAskAgain = false;
    await press("Field day");
    await fireEvent.changeText(await screen.findByLabelText("Purpose"), "Visit to St Mary's");
    await press("Check in");
    await screen.findByText(DENIED);
    expect(screen.getByLabelText("Purpose").props.value).toBe("Visit to St Mary's");

    state.permission = "granted";
    await press("Check in");

    expect(await screen.findByText("Checked in at 09:42, Field")).toBeTruthy();
    expect(manager.checkIns).toHaveLength(1);
    expect(screen.queryByText(DENIED)).toBeNull();
  });

  it("asks for the permission at the tap when it has not been decided, and checks in once it is given", async () => {
    const { manager } = await openManagerScreen("Check-in");
    state.permission = "undetermined";
    state.onRequestPermission = "granted";

    await press("Check in");

    expect(await screen.findByText("Checked in at 09:42, Office")).toBeTruthy();
    expect(Location.requestForegroundPermissionsAsync).toHaveBeenCalled();
    expect(manager.checkIns).toHaveLength(1);
  });
});

describe("the position that is sent", () => {
  it("takes a fresh reading at each tap and sends the same position in the body and in the audit header", async () => {
    const { manager, server } = await openManagerScreen("Check-in", {
      manager: {
        checkInRefusal: (() => {
          let first = true;
          return () => {
            if (!first) return undefined;
            first = false;
            return { status: 409, body: { reason: "Try again in a moment." } };
          };
        })(),
      },
    });

    await press("Check in");
    await screen.findByText("Try again in a moment.");
    // five seconds later (inside the 10 second window other calls share) the Manager is somewhere else
    state.position = { latitude: 13.0827, longitude: 80.2707, accuracy: 7 };
    await press("Check in");
    await screen.findByText("Checked in at 09:42, Office");

    expect(manager.checkIns).toHaveLength(2);
    expect(manager.checkIns[0]).toMatchObject({ latitude: 12.971599, longitude: 77.594566 });
    expect(manager.checkIns[1]).toMatchObject({ latitude: 13.0827, longitude: 80.2707, accuracyMeters: 7 });
    const calls = server.callsTo("POST /api/v1/manager-attendance/me/check-in");
    expect(calls[1].headers["X-HLS-Location"]).toMatch(/^lat=13\.082700;lng=80\.270700;acc=7\.0;/);
  });

  it("sends the accuracy the phone reports and never refuses a rough fix", async () => {
    const { manager } = await openManagerScreen("Check-in");
    state.position = { latitude: 12.9, longitude: 77.5, accuracy: 900 };

    await press("Check in");

    expect(await screen.findByText("Checked in at 09:42, Office")).toBeTruthy();
    expect(manager.checkIns[0].accuracyMeters).toBe(900);
  });
});
