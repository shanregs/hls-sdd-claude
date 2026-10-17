import { fireEvent, screen } from "@testing-library/react-native";
import { chooseDate } from "../support/leaveActions";
import { managerDay, managerLeave, managerMonth, monthDay, regularization, today } from "../support/managerFixtures";
import { openManagerScreen, type OpenedManagerApp } from "../support/managerApp";

/**
 * Spec 033 T045 (SC-005, SC-006): for every action that changes something, whatever goes wrong (the server refuses, it
 * fails, the phone is offline, the session cannot be renewed), nothing is ever shown as done, and what the Manager
 * entered is still there.
 */
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));
const pressText = async (text: string) => fireEvent.press(await screen.findByText(text));
const BASE = "/api/v1/manager-attendance/me";

interface Case {
  name: string;
  open: () => Promise<OpenedManagerApp>;
  /** The request the action makes, as the fake server's route. */
  route: string;
  /** Performs the action as the Manager would, up to and including the last tap. */
  act: () => Promise<void>;
  /** True when the screen shows the action as done. */
  done: () => boolean;
  /** Checks the Manager's entries are still on screen. */
  kept?: () => void;
}

const MISSED = "Tuesday 6 October 2026, absent";

const cases: Case[] = [
  {
    name: "check in",
    open: () => openManagerScreen("Check-in"),
    route: `POST ${BASE}/check-in`,
    act: async () => {
      await press("Field day");
      await fireEvent.changeText(await screen.findByLabelText("Purpose"), "Visit to St Mary's");
      await press("Check in");
    },
    done: () => screen.queryByText(/Checked in at/) !== null,
    kept: () => expect(screen.getByLabelText("Purpose").props.value).toBe("Visit to St Mary's"),
  },
  {
    name: "check out",
    open: () => openManagerScreen("Check-in", { manager: { today: today({ day: managerDay() }) } }),
    route: `POST ${BASE}/check-out`,
    act: async () => {
      await press("Check out");
    },
    done: () => screen.queryByText(/Checked out at/) !== null,
  },
  {
    name: "complete a check-out",
    open: () => openManagerScreen("Check-in", { manager: { today: today({ missingCheckOutDates: ["2026-10-02"] }) } }),
    route: `POST ${BASE}/days/{date}/complete-check-out`,
    act: async () => {
      await press("Complete check-out for 02/10/2026");
      await fireEvent.changeText(await screen.findByLabelText("Check-out time"), "18:05");
      await fireEvent.changeText(screen.getByLabelText("Reason"), "Forgot to tap out");
      await pressText("Send");
    },
    // done means the screen is still there and the day no longer needs completing (a sign-out removes the screen)
    done: () => screen.queryByLabelText("Check in") !== null && screen.queryByText("No check-out on 02/10/2026") === null,
    kept: () => expect(screen.getByLabelText("Reason").props.value).toBe("Forgot to tap out"),
  },
  {
    name: "add a photo",
    open: () => openManagerScreen("Check-in", { manager: { today: today({ day: managerDay({ hasPhoto: false }) }) } }),
    route: `POST ${BASE}/days/{date}/photo`,
    act: async () => {
      await pressText("Add photo");
      await pressText("Take a photo");
      await pressText("Upload photo");
    },
    done: () => screen.queryByText(/camera\.jpg, JPG, 12 B/) !== null,
    kept: () => expect(screen.getByText("camera.jpg, JPG, 340 KB")).toBeTruthy(),
  },
  {
    name: "request leave",
    open: () => openManagerScreen("My Leave"),
    route: `POST ${BASE}/leave`,
    act: async () => {
      await press("Casual Leave");
      await chooseDate("First date", "not chosen", "Monday 12 October 2026");
      await chooseDate("Last date", "not chosen", "Wednesday 14 October 2026");
      await fireEvent.changeText(screen.getByLabelText("Reason"), "Family function");
      await press("Submit");
    },
    done: () => screen.queryByText("Request submitted") !== null,
    kept: () => expect(screen.getByLabelText("Reason").props.value).toBe("Family function"),
  },
  {
    name: "cancel leave",
    open: () => openManagerScreen("My Leave", { manager: { leaves: [managerLeave({ status: "PENDING" })] } }),
    route: `DELETE ${BASE}/leave/{id}`,
    act: async () => {
      await press("Cancel request for 12/10/2026 – 14/10/2026");
      const buttons = await screen.findAllByText("Cancel request");
      await fireEvent.press(buttons[buttons.length - 1]);
    },
    done: () => screen.queryByLabelText(/12\/10\/2026 – 14\/10\/2026, Cancelled/) !== null,
  },
  {
    name: "ask to add a check-in",
    open: () =>
      openManagerScreen("My Month", {
        manager: { months: { "2026-10": managerMonth({ days: [monthDay("2026-10-06", { status: "ABSENT" })] }) } },
      }),
    route: `POST ${BASE}/regularizations`,
    act: async () => {
      await press(MISSED);
      await pressText("Ask to add a check-in");
      await fireEvent.changeText(await screen.findByLabelText("Time you arrived"), "09:15");
      await fireEvent.changeText(screen.getByLabelText("Reason"), "Phone was off");
      await pressText("Send");
    },
    done: () => screen.queryByLabelText(/Check-in for 06\/10\/2026 at 09:15, Pending/) !== null,
    kept: () => expect(screen.getByLabelText("Reason").props.value).toBe("Phone was off"),
  },
  {
    name: "cancel a check-in request",
    open: () =>
      openManagerScreen("My Month", {
        manager: { months: { "2026-10": managerMonth({ days: [] }) }, regularizations: [regularization()] },
      }),
    route: `DELETE ${BASE}/regularizations/{id}`,
    act: async () => {
      await press("Cancel check-in request for 02/10/2026");
      const buttons = await screen.findAllByText("Cancel request");
      await fireEvent.press(buttons[buttons.length - 1]);
    },
    done: () => screen.queryByLabelText(/Check-in for 02\/10\/2026 at 09:15, Cancelled/) !== null,
  },
];

describe.each(cases)("$name", (c) => {
  it("shows the server's refusal and nothing as done", async () => {
    const { server } = await c.open();
    server.on(c.route, { status: 409, body: { reason: "Refused for the test." } });

    await c.act();

    expect(await screen.findByText("Refused for the test.")).toBeTruthy();
    expect(c.done()).toBe(false);
    c.kept?.();
  });

  it("shows a failure of the server and nothing as done", async () => {
    const { server } = await c.open();
    server.on(c.route, { status: 500, body: { reason: "The server could not do that." } });

    await c.act();

    expect(await screen.findByText("The server could not do that.")).toBeTruthy();
    expect(c.done()).toBe(false);
    c.kept?.();
  });

  it("says nothing was sent when the phone is offline and shows nothing as done", async () => {
    const { server } = await c.open();
    server.offline = true;

    await c.act();

    expect(await screen.findByText("No connection. Nothing was sent.")).toBeTruthy();
    expect(c.done()).toBe(false);
    c.kept?.();
  });

  it("shows nothing as done when the session cannot be renewed", async () => {
    const { server } = await c.open();
    server.on(c.route, { status: 401, body: { message: "Please sign in again." } });
    server.on("POST /api/v1/auth/renew", { status: 401, body: { message: "Please sign in again." } });

    await c.act();

    expect(c.done()).toBe(false);
  });
});
