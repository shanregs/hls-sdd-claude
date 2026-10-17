import { fireEvent, screen } from "@testing-library/react-native";
import { taskSummaryText } from "../../src/tasks/TaskCard";
import { actionsOf } from "../support/tasksServer";
import { openVisitTaskScreen } from "../support/visitTaskApp";
import { prospect, taskDetail, visit } from "../support/visitTaskFixtures";

/** Spec 034 T043: the visit and task screens can be used with a screen reader and a thumb. */
afterEach(() => jest.restoreAllMocks());

type TreeNode = { props: Record<string, unknown>; children?: unknown[]; parent: TreeNode | null };

function textOf(node: TreeNode): string {
  const parts: string[] = [];
  const walk = (n: unknown) => {
    if (typeof n === "string") parts.push(n);
    else if (n && typeof n === "object" && "children" in n) (n as TreeNode).children?.forEach(walk);
  };
  walk(node);
  return parts.join("").trim();
}

/** Every button and radio has a name a screen reader can say: its label, or the words written on it. */
function expectNamedControls() {
  for (const control of [...screen.queryAllByRole("button"), ...screen.queryAllByRole("radio")]) {
    const node = control as unknown as TreeNode;
    const label = node.props.accessibilityLabel;
    const named = (typeof label === "string" && label.trim() !== "") || textOf(node) !== "";
    expect(named).toBe(true);
  }
}

const flat = (style: unknown) => Object.assign({}, ...[style].flat(Infinity).filter(Boolean));
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));
const pressText = async (text: string) => fireEvent.press(await screen.findByText(text));

describe("Marketing Calendar", () => {
  it("names the filter chips and every row, tells Missed in words, and names the detail's buttons", async () => {
    await openVisitTaskScreen("Marketing Calendar", {
      marketing: { visits: [visit({ id: "a", date: "2026-10-01", effectiveStatus: "MISSED" }), visit({ id: "b", date: "2026-10-06" })] },
    });

    const missed = await screen.findByLabelText("Visit with Green Valley Public School, 01/10/2026, missed");
    expect(missed.props.accessibilityRole).toBe("button");
    for (const label of ["All", "Planned", "Completed", "Cancelled", "Missed", "Plan a visit"]) {
      expect(screen.getByLabelText(label)).toBeTruthy();
    }
    expectNamedControls();

    await fireEvent.press(missed);
    await screen.findByText("Complete visit");
    expectNamedControls();
    expect(flat(screen.getByText("Complete visit").parent?.props.style).minHeight ?? 48).toBeGreaterThanOrEqual(48);
  });

  it("names the complete, plan and reschedule forms and announces a refusal", async () => {
    await openVisitTaskScreen("Marketing Calendar", {
      marketing: {
        visits: [visit({ version: 2 })],
        prospects: [prospect()],
        refusals: { complete: { status: 409, body: { reason: "The visit was changed meanwhile." } } },
      },
    });
    await press("Visit with Green Valley Public School, 06/10/2026, planned");
    await pressText("Complete visit");
    await fireEvent.changeText(await screen.findByLabelText("Outcome"), "Met the Principal");
    for (const label of ["Outcome", "Notes", "Follow-up date, not chosen", "Send", "Cancel"]) {
      expect(screen.getByLabelText(label)).toBeTruthy();
    }
    expectNamedControls();

    await press("Send");
    const refusal = await screen.findByText("The visit was changed meanwhile.");
    expect(refusal.props.accessibilityRole).toBe("alert");
  });
});

describe("My Tasks", () => {
  const detail = () => {
    const d = taskDetail({ task: { id: "t-1", title: "Call the Principal", dueOn: "2026-10-01", status: "IN_PROGRESS", overdue: true, daysOverdue: 4 }, actions: {} as never });
    return { ...d, actions: actionsOf(d) };
  };

  it("names the view chips and the rows, and tells overdue in words", async () => {
    await openVisitTaskScreen("My Tasks", { tasks: { details: [detail()] } });

    await screen.findByLabelText("Overdue (1)");
    await press("Overdue (1)");
    const row = await screen.findByLabelText("Call the Principal, due 01/10/2026, in progress, 4 days overdue");
    expect(row.props.accessibilityRole).toBe("button");
    expect(screen.getByText("4 days overdue")).toBeTruthy();
    expectNamedControls();
  });

  it("names the steps, the reason form and the evidence controls, and announces a refusal", async () => {
    const d = detail();
    const { tasks } = await openVisitTaskScreen("My Tasks", { tasks: { details: [d] } });
    await press("All");
    await press(taskSummaryText(d.task));
    await screen.findByText("History");
    expectNamedControls();

    await pressText("Give a reason");
    await fireEvent.changeText(screen.getByLabelText("Reason"), "The Principal was away");
    for (const label of ["Reason", "Follow-up date, not chosen", "Send", "Cancel"]) expect(screen.getByLabelText(label)).toBeTruthy();
    expectNamedControls();
    tasks.refusals.overdue = { status: 409, body: { reason: "The task was changed on the web." } };

    await press("Send");
    const refusal = await screen.findByText("The task was changed on the web.");
    expect(refusal.props.accessibilityRole).toBe("alert");
  });
});

describe("Home cards", () => {
  it("names the visits and tasks cards as buttons with what they say", async () => {
    await openVisitTaskScreen(null, {
      marketing: { visits: [visit({ date: "2026-10-05" })] },
      tasks: { details: [taskDetail({ task: { id: "t-1", dueOn: "2026-10-05" } })] },
    });

    const visits = await screen.findByLabelText("1 visit today");
    const tasks = await screen.findByLabelText("0 overdue, 1 due today");
    expect(visits.props.accessibilityRole).toBe("button");
    expect(tasks.props.accessibilityRole).toBe("button");
  });
});
