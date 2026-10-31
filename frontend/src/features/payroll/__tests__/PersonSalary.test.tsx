import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { axe } from "vitest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../../app/RouteGuard";
import { buildMuiTheme, type ThemeMode } from "../../../theme/tokens";
import { PersonSalaryPage } from "../PersonSalaryPage";
import { firstOfThisMonthIso } from "../validation";

const authFetch = vi.fn();
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
      accessModel: { roles: ["ADMIN"], dataScope: {}, navigation },
    }),
  };
});

function grantFor(actions: string[]) {
  navigation = [
    {
      section: "OPERATIONS",
      items: [
        {
          label: "Salary Structures",
          route: "/operations/salary-structures",
          actions,
        },
      ],
    },
  ];
}

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const HISTORY = [
  {
    id: "r2",
    amount: "65000.00",
    effectiveOn: "2026-11-01",
    note: "Agreed at recruitment",
    recordedBy: { id: "u1", name: "A. Admin" },
    recordedAt: "2026-10-08T06:12:00Z",
  },
  {
    id: "r1",
    amount: "60000.00",
    effectiveOn: "2025-11-01",
    note: null,
    recordedBy: { id: "u1", name: "A. Admin" },
    recordedAt: "2025-10-10T06:12:00Z",
  },
];

const MANAGER = {
  person: {
    kind: "MANAGER",
    personId: "m-1",
    name: "Manoj Manager",
    designation: "Zone Manager",
    inEffect: { amount: "65000.00", effectiveOn: "2026-11-01" },
    joiningDate: "2025-06-01",
    exitDate: null,
    active: true,
    salaryEditable: true,
    roles: [],
  },
  suggestedDefault: { amount: "60000.00", effectiveOn: "2026-04-01" },
  history: HISTORY,
};

const MANAGER_WITHOUT = {
  ...MANAGER,
  person: { ...MANAGER.person, inEffect: null },
  history: [],
};

const TEACHER = {
  person: {
    kind: "TEACHER",
    personId: "t-1",
    name: "Tara Teacher",
    designation: "Primary Teacher",
    inEffect: { amount: "28000.00", effectiveOn: "2026-04-01" },
    joiningDate: null,
    exitDate: null,
    active: true,
    salaryEditable: false,
    roles: [],
  },
  suggestedDefault: null,
  history: [],
};

const STAFF = {
  person: {
    kind: "STAFF",
    personId: "u-1",
    name: "Asha Admin",
    designation: null,
    inEffect: null,
    joiningDate: "2026-01-05",
    exitDate: null,
    active: true,
    salaryEditable: true,
    roles: ["ADMIN"],
  },
  suggestedDefault: null,
  history: [],
};

function mockApi(detail: unknown, write: Response = json({ id: "r3" }, 201)) {
  authFetch.mockImplementation(async (_url: string, init?: RequestInit) =>
    init?.method ? write : json(detail),
  );
}

function renderAt(path: string, mode: ThemeMode = "light") {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route
            path="/operations/salary-structures/people/:kind/:personId"
            element={
              <RouteGuard>
                <PersonSalaryPage />
              </RouteGuard>
            }
          />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const MANAGER_PATH = "/operations/salary-structures/people/MANAGER/m-1";

beforeEach(() => {
  authFetch.mockReset();
  grantFor(["VIEW", "CREATE"]);
  mockApi(MANAGER);
});

describe("A Manager's own salary (spec 013a User Story 5)", () => {
  it("shows the salary in effect and the history newest first with who, when and the note", async () => {
    renderAt(MANAGER_PATH);
    await screen.findByRole("heading", { name: "Manoj Manager" });
    expect(
      screen.getByText(/₹65,000\.00 a month, from 01\/11\/2026/),
    ).toBeInTheDocument();
    const table = screen.getByRole("table", { name: /salary history/i });
    const rows = within(table).getAllByRole("row");
    expect(rows[1]).toHaveTextContent("₹65,000.00");
    expect(rows[1]).toHaveTextContent("A. Admin");
    expect(rows[1]).toHaveTextContent("Agreed at recruitment");
    expect(rows[2]).toHaveTextContent("₹60,000.00");
  });

  it("says plainly that a person without a salary cannot be paid", async () => {
    mockApi(MANAGER_WITHOUT);
    renderAt(MANAGER_PATH);
    await screen.findByRole("heading", { name: "Manoj Manager" });
    expect(
      screen.getByText(/No salary yet\. This person cannot be paid/),
    ).toBeInTheDocument();
    expect(
      screen.getByText("No salary has been recorded."),
    ).toBeInTheDocument();
  });

  it("offers the designation's default as a suggestion that is not saved until used", async () => {
    const user = userEvent.setup();
    renderAt(MANAGER_PATH);
    await screen.findByRole("heading", { name: "Manoj Manager" });
    expect(
      screen.getByText(/suggested default is ₹60,000\.00/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/not saved unless you record it/),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/monthly salary/i)).toHaveValue("");

    await user.click(screen.getByRole("button", { name: "Use suggestion" }));
    expect(screen.getByLabelText(/monthly salary/i)).toHaveValue("60000");
    // nothing was sent by using the suggestion
    expect(
      authFetch.mock.calls.filter(([, init]) => init?.method),
    ).toHaveLength(0);
  });

  it("names each problem inline and sends nothing while the form is invalid", async () => {
    const user = userEvent.setup();
    renderAt(MANAGER_PATH);
    await screen.findByRole("heading", { name: "Manoj Manager" });
    authFetch.mockClear();

    await user.click(screen.getByRole("button", { name: "Record salary" }));
    expect(screen.getByText("Enter the amount.")).toBeInTheDocument();

    const amount = screen.getByLabelText(/monthly salary/i);
    // the keyboard focus lands on the first invalid field
    await waitFor(() => expect(amount).toHaveFocus());
    await user.type(amount, "100.123");
    await user.click(screen.getByRole("button", { name: "Record salary" }));
    expect(
      screen.getByText("The amount can have at most two decimal places."),
    ).toBeInTheDocument();

    const date = screen.getByLabelText(/effective from/i);
    await user.clear(amount);
    await user.type(amount, "50000");
    await user.clear(date);
    await user.type(date, "2020-01-01");
    await user.click(screen.getByRole("button", { name: "Record salary" }));
    expect(
      screen.getByText(/cannot be before the first day of this month/i),
    ).toBeInTheDocument();
    expect(authFetch).not.toHaveBeenCalled();
  });

  it("records the person's own salary and reloads their history", async () => {
    const user = userEvent.setup();
    renderAt(MANAGER_PATH);
    await screen.findByRole("heading", { name: "Manoj Manager" });
    await user.type(screen.getByLabelText(/monthly salary/i), "70000");
    await user.click(screen.getByRole("button", { name: "Record salary" }));

    await waitFor(() =>
      expect(authFetch).toHaveBeenCalledWith(
        "/api/v1/salary-structures/people/MANAGER/m-1/salaries",
        expect.objectContaining({ method: "POST" }),
      ),
    );
    const post = authFetch.mock.calls.find(
      ([, init]) => init?.method === "POST",
    );
    expect(JSON.parse(post![1].body)).toEqual({
      amount: "70000",
      effectiveOn: firstOfThisMonthIso(),
      note: null,
    });
    expect(
      await screen.findByText("The salary was recorded."),
    ).toBeInTheDocument();
  });

  it("shows the server's reason when the record is refused", async () => {
    const user = userEvent.setup();
    mockApi(MANAGER, json({ reason: "Person not found." }, 404));
    renderAt(MANAGER_PATH);
    await screen.findByRole("heading", { name: "Manoj Manager" });
    await user.type(screen.getByLabelText(/monthly salary/i), "70000");
    await user.click(screen.getByRole("button", { name: "Record salary" }));
    expect(await screen.findByText("Person not found.")).toBeInTheDocument();
  });

  it("hides the form from a viewer without CREATE", async () => {
    grantFor(["VIEW"]);
    renderAt(MANAGER_PATH);
    await screen.findByRole("heading", { name: "Manoj Manager" });
    expect(
      screen.queryByRole("button", { name: "Record salary" }),
    ).not.toBeInTheDocument();
    expect(screen.getByText("History")).toBeInTheDocument();
  });

  it.each(["light", "dark"] as const)(
    "has no accessibility violations in %s mode",
    async (mode) => {
      const { container } = renderAt(MANAGER_PATH, mode);
      await screen.findByRole("heading", { name: "Manoj Manager" });
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});

describe("A Teacher is read-only here", () => {
  it("shows the salary in effect and points to the Teachers screen, with no form", async () => {
    mockApi(TEACHER);
    renderAt("/operations/salary-structures/people/TEACHER/t-1");
    await screen.findByRole("heading", { name: "Tara Teacher" });
    expect(
      screen.getByText(/₹28,000\.00 a month, from 01\/04\/2026/),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Teachers screen" }),
    ).toHaveAttribute("href", "/master-data/teachers");
    expect(
      screen.queryByRole("button", { name: "Record salary" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText("History")).not.toBeInTheDocument();
  });
});

describe("A staff member (Admin, Director or other staff)", () => {
  const STAFF_PATH = "/operations/salary-structures/people/STAFF/u-1";

  it("shows their roles, the joining date and a form for their first salary", async () => {
    mockApi(STAFF);
    renderAt(STAFF_PATH);
    await screen.findByRole("heading", { name: "Asha Admin" });
    expect(screen.getByLabelText("Joining date")).toHaveValue("2026-01-05");
    expect(
      screen.getByRole("button", { name: "Record salary" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/No salary yet/)).toBeInTheDocument();
  });

  it("refuses an exit before the joining date without sending anything", async () => {
    const user = userEvent.setup();
    mockApi(STAFF);
    renderAt(STAFF_PATH);
    await screen.findByRole("heading", { name: "Asha Admin" });
    authFetch.mockClear();
    const exit = screen.getByLabelText("Exit date");
    await user.type(exit, "2025-12-31");
    await user.click(screen.getByRole("button", { name: "Save dates" }));
    expect(
      screen.getByText("The exit date cannot be before the joining date."),
    ).toBeInTheDocument();
    expect(authFetch).not.toHaveBeenCalled();
  });

  it("saves the dates", async () => {
    const user = userEvent.setup();
    mockApi(STAFF, json({ joiningDate: "2026-01-05", exitDate: "2026-12-31" }));
    renderAt(STAFF_PATH);
    await screen.findByRole("heading", { name: "Asha Admin" });
    await user.type(screen.getByLabelText("Exit date"), "2026-12-31");
    await user.click(screen.getByRole("button", { name: "Save dates" }));
    await waitFor(() =>
      expect(authFetch).toHaveBeenCalledWith(
        "/api/v1/salary-structures/people/STAFF/u-1/employment",
        expect.objectContaining({ method: "PUT" }),
      ),
    );
    const put = authFetch.mock.calls.find(([, init]) => init?.method === "PUT");
    expect(JSON.parse(put![1].body)).toEqual({
      joiningDate: "2026-01-05",
      exitDate: "2026-12-31",
    });
    expect(
      await screen.findByText("The dates were saved."),
    ).toBeInTheDocument();
  });
});

describe("access and errors", () => {
  it.each(["Zone Manager", "Teacher", "System"])(
    "shows 'Not authorized' and fetches nothing for a %s deep link",
    async () => {
      navigation = [];
      renderAt(MANAGER_PATH);
      expect(await screen.findByText("Not authorized")).toBeInTheDocument();
      expect(authFetch).not.toHaveBeenCalled();
    },
  );

  it("shows an error when the person cannot be loaded", async () => {
    authFetch.mockImplementation(async () =>
      json({ reason: "Person not found." }, 404),
    );
    renderAt(MANAGER_PATH);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Person not found.",
    );
  });
});
