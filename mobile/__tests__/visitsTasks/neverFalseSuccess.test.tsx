import { fireEvent, screen } from "@testing-library/react-native";
import { taskSummaryText } from "../../src/tasks/TaskCard";
import { chooseDate } from "../support/leaveActions";
import { actionsOf } from "../support/tasksServer";
import { openVisitTaskScreen, type OpenedVisitTaskApp } from "../support/visitTaskApp";
import { prospect, taskDetail, visit } from "../support/visitTaskFixtures";

/**
 * Spec 034 T042 (SC-004, SC-005, SC-008): for every step that changes something, whatever goes wrong (the server refuses, it fails,
 * the phone is offline, the session cannot be renewed), nothing is ever shown as done and what the Manager entered is still there.
 */
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));
const pressText = async (text: string) => fireEvent.press(await screen.findByText(text));
const escape = (text: string) => text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const MK = "/api/v1/marketing";
const TK = "/api/v1/tasks";
const CARD = "Visit with Green Valley Public School, 06/10/2026, planned";

const withActions = (overrides: Parameters<typeof taskDetail>[0]) => {
  const detail = taskDetail({ ...overrides, actions: {} as never });
  return { ...detail, actions: actionsOf(detail) };
};

async function openTask(detail: ReturnType<typeof withActions>) {
  const app = await openVisitTaskScreen("My Tasks", { tasks: { details: [detail] } });
  await press("All");
  await press(taskSummaryText(detail.task));
  await screen.findByText("History");
  return app;
}

interface Case {
  name: string;
  open: () => Promise<OpenedVisitTaskApp>;
  route: string;
  /** Performs the steps; calls `arm` right before the last tap, which is when the failure should start. */
  act: (arm: () => void) => Promise<void>;
  /** True when the screen shows the step as done. */
  done: () => boolean;
  kept?: () => void;
}

const cases: Case[] = [
  {
    name: "complete a visit",
    open: async () => {
      const app = await openVisitTaskScreen("Marketing Calendar", { marketing: { visits: [visit({ version: 2 })] } });
      await press(CARD);
      return app;
    },
    route: `POST ${MK}/activities/{id}/complete`,
    act: async (arm) => {
      await pressText("Complete visit");
      await fireEvent.changeText(await screen.findByLabelText("Outcome"), "Met the Principal");
      arm();
      await press("Send");
    },
    done: () => screen.queryByText("Completed") !== null,
    kept: () => expect(screen.getByLabelText("Outcome").props.value).toBe("Met the Principal"),
  },
  {
    name: "add a photo to a visit",
    open: async () => {
      const app = await openVisitTaskScreen("Marketing Calendar", { marketing: { visits: [visit()] } });
      await press(CARD);
      return app;
    },
    route: `POST ${MK}/activities/{id}/attachments`,
    act: async (arm) => {
      await pressText("Add photo");
      await pressText("Take a photo");
      arm();
      await press("Upload photos");
    },
    done: () => screen.queryByText("camera.jpg, JPG, 12 B") !== null,
  },
  {
    name: "plan a visit",
    open: () => openVisitTaskScreen("Marketing Calendar", { marketing: { visits: [], prospects: [prospect()] } }),
    route: `POST ${MK}/activities`,
    act: async (arm) => {
      await press("Plan a visit");
      await press("Choose Green Valley Public School");
      await chooseDate("Date" as never, "not chosen", "Wednesday 7 October 2026");
      arm();
      await press("Send");
    },
    done: () => screen.queryByText("Visit, 07/10/2026") !== null,
    kept: () => expect(screen.getByText("Chosen: Green Valley Public School")).toBeTruthy(),
  },
  {
    name: "reschedule a visit",
    open: async () => {
      const app = await openVisitTaskScreen("Marketing Calendar", { marketing: { visits: [visit({ version: 1 })] } });
      await press(CARD);
      return app;
    },
    route: `POST ${MK}/activities/{id}/reschedule`,
    act: async (arm) => {
      await pressText("Reschedule");
      await chooseDate("New date" as never, "not chosen", "Wednesday 7 October 2026");
      arm();
      await press("Send");
    },
    done: () => screen.queryByText("Moved from 06/10/2026 to 07/10/2026") !== null,
    kept: () => expect(screen.getByLabelText("New date, Wednesday 7 October 2026")).toBeTruthy(),
  },
  {
    name: "cancel a visit",
    open: async () => {
      const app = await openVisitTaskScreen("Marketing Calendar", { marketing: { visits: [visit({ version: 1 })] } });
      await press(CARD);
      return app;
    },
    route: `POST ${MK}/activities/{id}/cancel`,
    act: async (arm) => {
      await pressText("Cancel visit");
      await fireEvent.changeText(await screen.findByLabelText("Reason"), "Principal is away");
      const buttons = screen.getAllByText("Cancel visit");
      arm();
      await fireEvent.press(buttons[buttons.length - 1]);
    },
    done: () => screen.queryByText("Cancelled because: Principal is away") !== null,
    kept: () => expect(screen.getByLabelText("Reason").props.value).toBe("Principal is away"),
  },
  {
    name: "acknowledge a task",
    open: () => openTask(withActions({ task: { id: "t-1", title: "Call", dueOn: "2026-10-05", status: "ASSIGNED" } })),
    route: `POST ${TK}/{id}/acknowledge`,
    act: async (arm) => {
      arm();
      await pressText("Acknowledge");
    },
    done: () => screen.queryByText("Acknowledged") !== null,
  },
  {
    name: "start a task",
    open: () => openTask(withActions({ task: { id: "t-1", title: "Call", dueOn: "2026-10-05", status: "ACKNOWLEDGED" } })),
    route: `POST ${TK}/{id}/start`,
    act: async (arm) => {
      arm();
      await pressText("Start");
    },
    done: () => screen.queryByText("In progress") !== null,
  },
  {
    name: "give an overdue reason",
    open: () =>
      openTask(withActions({ task: { id: "t-1", title: "Call", dueOn: "2026-10-01", status: "IN_PROGRESS", overdue: true, daysOverdue: 4 } })),
    route: `POST ${TK}/{id}/overdue-reason`,
    act: async (arm) => {
      await pressText("Give a reason");
      await fireEvent.changeText(screen.getByLabelText("Reason"), "The Principal was away");
      arm();
      await press("Send");
    },
    done: () => screen.queryByText("Reason for being overdue: The Principal was away") !== null,
    kept: () => expect(screen.getByLabelText("Reason").props.value).toBe("The Principal was away"),
  },
  {
    name: "complete a task",
    open: () => openTask(withActions({ task: { id: "t-1", title: "Call", dueOn: "2026-10-05", status: "IN_PROGRESS" } })),
    route: `POST ${TK}/{id}/complete`,
    act: async (arm) => {
      await pressText("Complete");
      await fireEvent.changeText(screen.getByLabelText("Note"), "Met the Principal");
      arm();
      await press("Send");
    },
    done: () => screen.queryByText("Completion note: Met the Principal") !== null,
    kept: () => expect(screen.getByLabelText("Note").props.value).toBe("Met the Principal"),
  },
  {
    name: "add evidence to a task",
    open: () => openTask(withActions({ task: { id: "t-1", title: "Call", dueOn: "2026-10-05", status: "IN_PROGRESS" } })),
    route: `POST ${TK}/{id}/evidence`,
    act: async (arm) => {
      await pressText("Take a photo");
      arm();
      await press("Upload photos");
    },
    done: () => screen.queryByText("Evidence (1 of 5)") !== null,
  },
];

describe.each(cases)("$name", (c) => {
  it("shows the server's refusal and nothing as done", async () => {
    const { server } = await c.open();

    await c.act(() => server.on(c.route, { status: 409, body: { reason: "Refused for the test." } }));

    expect(await screen.findByText(new RegExp(escape("Refused for the test.")))).toBeTruthy();
    expect(c.done()).toBe(false);
    c.kept?.();
  });

  it("shows a failure of the server and nothing as done", async () => {
    const { server } = await c.open();

    await c.act(() => server.on(c.route, { status: 500, body: { reason: "The server could not do that." } }));

    expect(await screen.findByText(new RegExp(escape("The server could not do that.")))).toBeTruthy();
    expect(c.done()).toBe(false);
    c.kept?.();
  });

  it("says nothing was sent when the phone is offline and shows nothing as done", async () => {
    const { server } = await c.open();

    await c.act(() => {
      server.offline = true;
    });

    expect(await screen.findByText(new RegExp(escape("No connection. Nothing was sent.")))).toBeTruthy();
    expect(c.done()).toBe(false);
    c.kept?.();
  });

  it("shows nothing as done when the session cannot be renewed", async () => {
    const { server } = await c.open();

    await c.act(() => {
      server.on(c.route, { status: 401, body: { message: "Please sign in again." } });
      server.on("POST /api/v1/auth/renew", { status: 401, body: { message: "Please sign in again." } });
    });

    expect(c.done()).toBe(false);
  });
});

describe("a photo that fails after its action succeeded", () => {
  it("never turns a completed visit into a failure, and never undoes it", async () => {
    const { marketing } = await openVisitTaskScreen("Marketing Calendar", {
      marketing: { visits: [visit({ version: 2 })], refusals: { attach: { status: 500, body: { reason: "Photos are unavailable." } } } },
    });
    await press(CARD);
    await pressText("Complete visit");
    await fireEvent.changeText(await screen.findByLabelText("Outcome"), "Met the Principal");
    await pressText("Take a photo");

    await press("Send");

    expect(await screen.findByText("Completed")).toBeTruthy();
    expect(await screen.findByText("camera.jpg: Photos are unavailable.")).toBeTruthy();
    expect(marketing.visits[0].status).toBe("COMPLETED");
  });
});
