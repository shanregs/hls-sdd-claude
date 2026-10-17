import { act, fireEvent, screen } from "@testing-library/react-native";
import { managerDay, managerFieldModel, today } from "../support/managerFixtures";
import { openManagerScreen } from "../support/managerApp";

/** Spec 033 T023 and T024 (US2): check-out, and completing the check-out of an earlier day. */
const state = jest.requireMock("expo-location").__state as Record<string, unknown>;
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));
const pressText = async (text: string) => fireEvent.press(await screen.findByText(text));

describe("checking out", () => {
  it("is offered only after a check-in and before a check-out", async () => {
    await openManagerScreen("Check-in");
    expect(await screen.findByLabelText("Check in")).toBeTruthy();
    expect(screen.queryByLabelText("Check out")).toBeNull();
  });

  it("sends a fresh position and shows the time the server recorded", async () => {
    const { manager } = await openManagerScreen("Check-in", { manager: { today: today({ day: managerDay() }) } });
    state.position = { latitude: 13.0827, longitude: 80.2707, accuracy: 11 };
    manager.now = "2026-10-05T12:35:00Z";

    await press("Check out");

    expect(await screen.findByText("Checked out at 18:05")).toBeTruthy();
    expect(manager.checkOuts).toEqual([{ latitude: 13.0827, longitude: 80.2707, accuracyMeters: 11 }]);
    expect(screen.queryByLabelText("Check out")).toBeNull();
  });

  it("sends nothing and explains when there is no position", async () => {
    const { manager } = await openManagerScreen("Check-in", { manager: { today: today({ day: managerDay() }) } });
    state.servicesEnabled = false;

    await press("Check out");

    expect(await screen.findByText("Turn on location (GPS) on your phone, then try again.")).toBeTruthy();
    expect(manager.checkOuts).toHaveLength(0);
    expect(screen.queryByText(/Checked out at/)).toBeNull();
  });

  it.each([
    ["no check-in", 400, "You have not checked in today."],
    ["already checked out", 409, "You have already checked out today."],
    ["a locked month", 409, "Attendance for October 2026 is locked."],
  ])("shows the server's reason for %s and changes nothing", async (_name, status, reason) => {
    const { manager } = await openManagerScreen("Check-in", {
      manager: { today: today({ day: managerDay() }), checkOutRefusal: () => ({ status, body: { reason } }) },
    });

    await press("Check out");

    expect(await screen.findByText(reason)).toBeTruthy();
    expect(screen.queryByText(/Checked out at/)).toBeNull();
    expect(manager.today.day?.checkOutAt).toBeNull();
    expect(screen.getByLabelText("Check out")).toBeTruthy();
  });

  it("says nothing was sent when offline and keeps Check out available", async () => {
    const { server } = await openManagerScreen("Check-in", { manager: { today: today({ day: managerDay() }) } });
    server.offline = true;

    await press("Check out");

    expect(await screen.findByText("No connection. Nothing was sent.")).toBeTruthy();
    expect(screen.queryByText(/Checked out at/)).toBeNull();
    expect(screen.getByLabelText("Check out")).toBeTruthy();
  });

  it("sends one request for a double tap", async () => {
    const { manager } = await openManagerScreen("Check-in", { manager: { today: today({ day: managerDay() }) } });
    const button = await screen.findByLabelText("Check out");

    await fireEvent.press(button);
    await fireEvent.press(button);

    await screen.findByText(/Checked out at/);
    expect(manager.checkOuts).toHaveLength(1);
  });

  it("is not offered without the Create action", async () => {
    await openManagerScreen("Check-in", {
      model: managerFieldModel({ create: false }),
      manager: { today: today({ day: managerDay() }) },
    });

    await screen.findByText("Checked in at 09:42, Office");
    expect(screen.queryByLabelText("Check out")).toBeNull();
  });
});

describe("completing a missing check-out", () => {
  const missing = today({ missingCheckOutDates: ["2026-10-02"] });

  it("lists the day and clears it once the server has completed it", async () => {
    const { manager } = await openManagerScreen("Check-in", { manager: { today: missing } });
    expect(await screen.findByText("No check-out on 02/10/2026")).toBeTruthy();

    await press("Complete check-out for 02/10/2026");
    expect(screen.getByText("Complete check-out for 02/10/2026")).toBeTruthy();
    await fireEvent.changeText(await screen.findByLabelText("Check-out time"), "18:05");
    await fireEvent.changeText(screen.getByLabelText("Reason"), "Forgot to tap out");
    await pressText("Send");

    // the day is reloaded after the server confirmed the completion
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 50));
    });
    expect(screen.queryByText("No check-out on 02/10/2026")).toBeNull();
    expect(manager.completions).toEqual([{ date: "2026-10-02", time: "18:05", reason: "Forgot to tap out" }]);
  });

  it("keeps Send disabled until both a time and a reason are given, and limits the reason to 500 characters", async () => {
    const { manager } = await openManagerScreen("Check-in", { manager: { today: missing } });
    await press("Complete check-out for 02/10/2026");

    await fireEvent.changeText(await screen.findByLabelText("Check-out time"), "18:05");
    await fireEvent.press(screen.getByText("Send"));
    await fireEvent.changeText(screen.getByLabelText("Reason"), "   ");
    await fireEvent.press(screen.getByText("Send"));

    expect(manager.completions).toHaveLength(0);
    expect(screen.getByLabelText("Reason").props.maxLength).toBe(500);
  });

  it.each([
    ["a time not on that day", 400, "The time must be on 02/10/2026."],
    ["a future time", 400, "The time cannot be in the future."],
    ["a locked month", 409, "Attendance for October 2026 is locked."],
  ])("shows the server's reason for %s and keeps the entries", async (_name, status, reason) => {
    await openManagerScreen("Check-in", {
      manager: { today: missing, completeRefusal: () => ({ status, body: { reason } }) },
    });
    await press("Complete check-out for 02/10/2026");
    await fireEvent.changeText(await screen.findByLabelText("Check-out time"), "23:59");
    await fireEvent.changeText(screen.getByLabelText("Reason"), "Forgot");
    await pressText("Send");

    expect(await screen.findByText(reason)).toBeTruthy();
    expect(screen.getByLabelText("Check-out time").props.value).toBe("23:59");
    expect(screen.getByLabelText("Reason").props.value).toBe("Forgot");
    expect(screen.getByText("No check-out on 02/10/2026")).toBeTruthy();
  });

  it("keeps the entries and shows nothing as completed when offline", async () => {
    const { server } = await openManagerScreen("Check-in", { manager: { today: missing } });
    await press("Complete check-out for 02/10/2026");
    await fireEvent.changeText(await screen.findByLabelText("Check-out time"), "18:05");
    await fireEvent.changeText(screen.getByLabelText("Reason"), "Forgot");
    server.offline = true;
    await pressText("Send");

    expect(await screen.findByText("No connection. Nothing was sent.")).toBeTruthy();
    expect(screen.getByLabelText("Reason").props.value).toBe("Forgot");
    expect(screen.getByText("No check-out on 02/10/2026")).toBeTruthy();
  });

  it("does not offer Complete check-out without the Create action", async () => {
    await openManagerScreen("Check-in", { model: managerFieldModel({ create: false }), manager: { today: missing } });

    expect(await screen.findByText("No check-out on 02/10/2026")).toBeTruthy();
    expect(screen.queryByText("Complete check-out")).toBeNull();
  });
});
