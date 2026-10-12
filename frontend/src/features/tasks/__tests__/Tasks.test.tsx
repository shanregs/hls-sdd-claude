import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { axe } from "vitest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../../app/RouteGuard";
import { buildMuiTheme, type ThemeMode } from "../../../theme/tokens";
import { TaskDetailPage } from "../TaskDetailPage";
import { TasksPage } from "../TasksPage";

const authFetch = vi.fn();
let roles: string[] = ["MANAGER"];
let navigation: { section: string; items: unknown[] }[] = [];

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

vi.mock("../../../access-model/useAccessModel", async () => {
  const actual = await vi.importActual<
    typeof import("../../../access-model/useAccessModel")
  >("../../../access-model/useAccessModel");
  return {
    ...actual,
    useAccessModel: () => ({
      loading: false,
      accessModel: { roles, dataScope: {}, navigation },
    }),
  };
});

function grant(role: string, label: string) {
  roles = [role];
  navigation = [
    {
      section: "OPERATIONS",
      items: [
        {
          label,
          route: "/operations/tasks",
          actions: ["VIEW", "CREATE", "EDIT"],
        },
      ],
    },
  ];
}

const ROW = {
  id: "t-1",
  type: "SCHOOL_VISIT",
  typeLabel: "School visit",
  title: "Visit Green Valley School",
  status: "ASSIGNED",
  dueOn: "2026-10-12",
  assigneeUserId: "u-1",
  assigneeName: "Meera Manager",
  assigneeActive: true,
  linkedType: null,
  linkedId: null,
  linkedLabel: null,
  place: null,
  overdue: false,
  daysOverdue: 0,
  escalated: false,
};

const OVERDUE = {
  ...ROW,
  id: "t-2",
  title: "Collect Principal feedback",
  dueOn: "2026-10-08",
  overdue: true,
  daysOverdue: 3,
  escalated: true,
};

const SUMMARY = {
  today: 1,
  upcoming: 0,
  overdue: 1,
  awaiting: 2,
  escalated: 1,
  done: 4,
};

function detail(actions: Record<string, boolean>, row = ROW) {
  return {
    task: row,
    description: "Meet the Principal",
    createdByName: "D. Director",
    escalateAfterDays: 3,
    source: "MANUAL",
    overdueReason: null,
    completionNote: null,
    completedAt: null,
    verifiedAt: null,
    verifiedByName: null,
    escalatedAt: null,
    cancelReason: null,
    parentTaskId: null,
    followUpTaskIds: [],
    history: [
      {
        event: "CREATED",
        actorName: "D. Director",
        at: "2026-10-10T05:00:00Z",
        before: null,
        after: "ASSIGNED",
        note: null,
      },
    ],
    evidence: [],
    actions: {
      acknowledge: false,
      start: false,
      complete: false,
      overdueReason: false,
      addEvidence: false,
      verify: false,
      sendBack: false,
      edit: false,
      cancel: false,
      ...actions,
    },
    version: 0,
  };
}

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function mockList(rows: unknown[] = [ROW, OVERDUE]) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method === "POST") return json(detail({}), 201);
    if (url.includes("/summary")) return json(SUMMARY);
    if (url.includes("/types"))
      return json([{ value: "SCHOOL_VISIT", label: "School visit" }]);
    if (url.includes("/assignees"))
      return json([{ userId: "u-1", name: "Meera Manager" }]);
    return json({
      content: rows,
      page: 0,
      size: 50,
      totalElements: rows.length,
    });
  });
}

function renderAt(path: string, mode: ThemeMode = "light") {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route
            path="/operations/tasks"
            element={
              <RouteGuard>
                <TasksPage />
              </RouteGuard>
            }
          />
          <Route
            path="/operations/tasks/:id"
            element={
              <RouteGuard>
                <TaskDetailPage />
              </RouteGuard>
            }
          />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  authFetch.mockReset();
  grant("MANAGER", "My Tasks");
  mockList();
});

describe("Task lists (spec 025 US1, US2)", () => {
  it("shows a Manager My Tasks with counts on the tabs and the due date as DD/MM/YYYY", async () => {
    renderAt("/operations/tasks");
    expect(
      await screen.findByText("Visit Green Valley School"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "My Tasks" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Today (1)" })).toBeInTheDocument();
    expect(
      screen.getByRole("tab", { name: "Awaiting verification (2)" }),
    ).toBeInTheDocument();
    expect(screen.getByText("12/10/2026")).toBeInTheDocument();
    // a Manager has no Manager column and no Manager filter
    expect(
      screen.queryByRole("columnheader", { name: "Manager" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Manager")).not.toBeInTheDocument();
  });

  it("marks overdue and escalated tasks", async () => {
    renderAt("/operations/tasks");
    await screen.findByText("Collect Principal feedback");
    expect(screen.getByText("3 days overdue")).toBeInTheDocument();
    expect(screen.getByText("Escalated")).toBeInTheDocument();
  });

  it("gives management the Manager Tasks view with a Manager column, filters and an escalation notice", async () => {
    grant("DIRECTOR", "Manager Tasks");
    renderAt("/operations/tasks");
    expect(
      await screen.findByRole("heading", { name: "Manager Tasks" }),
    ).toBeInTheDocument();
    await screen.findByText("Visit Green Valley School");
    expect(
      screen.getByRole("columnheader", { name: "Manager" }),
    ).toBeInTheDocument();
    expect(screen.getAllByText("Meera Manager").length).toBeGreaterThan(0);
    expect(
      screen.getByText(/1 open task has been escalated to the Director/),
    ).toBeInTheDocument();
  });

  it("asks the server for the list that the chosen tab names", async () => {
    const user = userEvent.setup();
    renderAt("/operations/tasks");
    await screen.findByText("Visit Green Valley School");
    authFetch.mockClear();
    await user.click(screen.getByRole("tab", { name: "Overdue (1)" }));
    await waitFor(() =>
      expect(
        authFetch.mock.calls.some(([u]) => String(u).includes("view=overdue")),
      ).toBe(true),
    );
  });

  it("shows an empty state and an error state", async () => {
    mockList([]);
    const { unmount } = renderAt("/operations/tasks");
    expect(await screen.findByText("No tasks here.")).toBeInTheDocument();
    unmount();
    authFetch.mockReset();
    authFetch.mockResolvedValue(json({ reason: "Could not load" }, 500));
    renderAt("/operations/tasks");
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load",
    );
  });

  it("names each problem inline in the new task form and sends nothing while invalid", async () => {
    const user = userEvent.setup();
    renderAt("/operations/tasks");
    await screen.findByText("Visit Green Valley School");
    await user.click(screen.getByRole("button", { name: "New task" }));
    authFetch.mockClear();
    await user.click(screen.getByRole("button", { name: "Create task" }));
    expect(
      screen.getByText("The title must be at least 3 characters."),
    ).toBeInTheDocument();
    expect(authFetch).not.toHaveBeenCalled();
  });

  it.each(["light", "dark"] as const)(
    "has no accessibility violations in %s mode",
    async (mode) => {
      const { container } = renderAt("/operations/tasks", mode);
      await screen.findByText("Visit Green Valley School");
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});

describe("Task detail (spec 025 US1, US3)", () => {
  it("offers only the actions the server allows", async () => {
    authFetch.mockResolvedValue(json(detail({ acknowledge: true })));
    renderAt("/operations/tasks/t-1");
    expect(
      await screen.findByRole("heading", { name: "Visit Green Valley School" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Acknowledge" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Verify" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Complete" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("list", { name: "Task history" }),
    ).toHaveTextContent("created by D. Director");
  });

  it("requires a completion note of 10 characters before it sends anything", async () => {
    const user = userEvent.setup();
    authFetch.mockResolvedValue(json(detail({ complete: true })));
    renderAt("/operations/tasks/t-1");
    await user.click(await screen.findByRole("button", { name: "Complete" }));
    authFetch.mockClear();
    await user.type(screen.getByLabelText(/completion note/i), "done");
    await user.click(screen.getByRole("button", { name: "Complete task" }));
    expect(
      screen.getByText("Write at least 10 characters."),
    ).toBeInTheDocument();
    expect(authFetch).not.toHaveBeenCalled();

    authFetch.mockResolvedValue(json(detail({ complete: false })));
    await user.type(
      screen.getByLabelText(/completion note/i),
      " and dusted well",
    );
    await user.click(screen.getByRole("button", { name: "Complete task" }));
    await waitFor(() => expect(authFetch).toHaveBeenCalled());
    const [url, init] = authFetch.mock.calls[0];
    expect(url).toBe("/api/v1/tasks/t-1/complete");
    expect(JSON.parse(init.body).note).toBe("done and dusted well");
  });

  it("asks for an overdue reason with an optional follow-up date", async () => {
    const user = userEvent.setup();
    authFetch.mockResolvedValue(json(detail({ overdueReason: true }, OVERDUE)));
    renderAt("/operations/tasks/t-2");
    await user.click(
      await screen.findByRole("button", { name: "Give overdue reason" }),
    );
    const dialog = screen.getByRole("dialog");
    expect(
      within(dialog).getByLabelText(/follow-up date/i),
    ).toBeInTheDocument();
    await user.type(
      within(dialog).getByLabelText(/^reason/i),
      "Principal was away",
    );
    authFetch.mockClear();
    await user.click(
      within(dialog).getByRole("button", { name: "Save reason" }),
    );
    await waitFor(() => expect(authFetch).toHaveBeenCalled());
    expect(authFetch.mock.calls[0][0]).toBe("/api/v1/tasks/t-2/overdue-reason");
  });

  it("shows the server's refusal", async () => {
    const user = userEvent.setup();
    authFetch.mockResolvedValue(json(detail({ acknowledge: true })));
    renderAt("/operations/tasks/t-1");
    await user.click(
      await screen.findByRole("button", { name: "Acknowledge" }),
    );
    authFetch.mockResolvedValue(
      json({ reason: "The task cannot be acknowledged." }, 409),
    );
    await user.click(screen.getByRole("button", { name: "Acknowledge" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "The task cannot be acknowledged.",
    );
  });

  it("lets a verifier verify or send back", async () => {
    grant("DIRECTOR", "Manager Tasks");
    authFetch.mockResolvedValue(
      json(detail({ verify: true, sendBack: true, cancel: false })),
    );
    renderAt("/operations/tasks/t-1");
    expect(
      await screen.findByRole("button", { name: "Verify" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Send back" }),
    ).toBeInTheDocument();
  });

  it.each(["light", "dark"] as const)(
    "has no accessibility violations in %s mode",
    async (mode) => {
      authFetch.mockResolvedValue(json(detail({ acknowledge: true })));
      const { container } = renderAt("/operations/tasks/t-1", mode);
      await screen.findByRole("heading", { name: "Visit Green Valley School" });
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});

describe("Access", () => {
  it("shows the not-authorized page when the menu has no Tasks item", async () => {
    roles = ["TEACHER"];
    navigation = [];
    renderAt("/operations/tasks");
    await waitFor(() =>
      expect(
        screen.queryByText("Visit Green Valley School"),
      ).not.toBeInTheDocument(),
    );
    expect(authFetch).not.toHaveBeenCalled();
  });
});
