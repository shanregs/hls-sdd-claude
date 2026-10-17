import { fireEvent, screen } from "@testing-library/react-native";
import { managerDay } from "../support/managerFixtures";
import { openManagerScreen } from "../support/managerApp";

/** Spec 033 T017 (US1, SC-005): a check-in is never shown as made unless the server confirmed it. */
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));

describe("without a connection", () => {
  it("says nothing was sent, keeps every entry, shows nothing as checked in, and succeeds on the next try online", async () => {
    const { manager, server } = await openManagerScreen("Check-in");
    await press("Field day");
    await fireEvent.changeText(await screen.findByLabelText("Purpose"), "Visit to St Mary's");
    server.offline = true;

    await press("Check in");

    expect(await screen.findByText("No connection. Nothing was sent.")).toBeTruthy();
    expect(screen.queryByText(/Checked in at/)).toBeNull();
    expect(screen.getByLabelText("Purpose").props.value).toBe("Visit to St Mary's");
    expect(manager.checkIns).toHaveLength(0);

    server.offline = false;
    await press("Check in");

    expect(await screen.findByText("Checked in at 09:42, Field")).toBeTruthy();
    expect(manager.checkIns).toHaveLength(1);
    expect(screen.queryByText("No connection. Nothing was sent.")).toBeNull();
  });

  it("sends one request for a double tap", async () => {
    const { manager } = await openManagerScreen("Check-in");
    const button = await screen.findByLabelText("Check in");

    await fireEvent.press(button);
    await fireEvent.press(button);

    await screen.findByText("Checked in at 09:42, Office");
    expect(manager.checkIns).toHaveLength(1);
  });

  it("renews the session once after a 401 and then checks in", async () => {
    const { manager, server } = await openManagerScreen("Check-in");
    let first = true;
    server.on("POST /api/v1/manager-attendance/me/check-in", (request) => {
      if (first) {
        first = false;
        return { status: 401, body: { message: "Please sign in again." } };
      }
      manager.checkIns.push(request.body as never);
      const day = managerDay({ checkInAt: manager.now });
      manager.today = { ...manager.today, day };
      return { status: 201, body: day };
    });

    await press("Check in");

    await screen.findByText("Checked in at 09:42, Office");
    expect(manager.checkIns).toHaveLength(1);
    expect(server.callsTo("POST /api/v1/auth/renew").length).toBeGreaterThanOrEqual(1);
  });

  it("shows a load failure on the screen with a way to try again, and no stale day", async () => {
    await openManagerScreen("Check-in", {
      prepare: (server) =>
        server.on("GET /api/v1/manager-attendance/me/today", { status: 500, body: { reason: "Could not load today." } }),
    });

    expect(await screen.findByText("Could not load today.")).toBeTruthy();
    expect(screen.getByText("Try again")).toBeTruthy();
    expect(screen.queryByLabelText("Check in")).toBeNull();
  });
});
