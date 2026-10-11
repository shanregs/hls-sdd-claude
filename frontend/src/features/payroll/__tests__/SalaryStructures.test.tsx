import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { axe } from "vitest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../../app/RouteGuard";
import { buildMuiTheme, type ThemeMode } from "../../../theme/tokens";
import { DesignationSalaryPage } from "../DesignationSalaryPage";
import { SalaryStructuresPage } from "../SalaryStructuresPage";
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

const DESIGNATION_ID = "d-1";

const LIST = [
  {
    designationId: DESIGNATION_ID,
    name: "Principal",
    kind: "MANAGER",
    retired: false,
    inEffect: { amount: "125000.00", effectiveOn: "2026-11-01" },
    missingSalary: { flag: false, people: 0, note: null },
  },
  {
    designationId: "d-2",
    name: "Primary Teacher",
    kind: "TEACHER",
    retired: false,
    inEffect: null,
    missingSalary: {
      flag: true,
      people: 3,
      note: "Teachers with no salary of their own",
    },
    teacherDefaultNote:
      "Only the default offered when a Teacher's salary is set.",
  },
];

const DETAIL = {
  designationId: DESIGNATION_ID,
  name: "Principal",
  kind: "MANAGER",
  retired: false,
  inEffect: { amount: "125000.00", effectiveOn: "2026-11-01" },
  missingSalary: { flag: false, people: 0, note: null },
  history: [
    {
      id: "r2",
      designationId: DESIGNATION_ID,
      amount: "125000.00",
      effectiveOn: "2026-11-01",
      note: "Annual revision",
      recordedBy: { id: "u1", name: "A. Admin" },
      recordedAt: "2026-10-08T06:12:00Z",
    },
    {
      id: "r1",
      designationId: DESIGNATION_ID,
      amount: "110000.00",
      effectiveOn: "2025-11-01",
      note: null,
      recordedBy: { id: "u1", name: "A. Admin" },
      recordedAt: "2025-10-10T06:12:00Z",
    },
  ],
};

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function mockApi(write: Response = json({ id: "r3" }, 201)) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method) return write;
    if (url.includes("/designations/")) return json(DETAIL);
    return json(LIST);
  });
}

function renderAt(path: string, mode: ThemeMode = "light") {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route
            path="/operations/salary-structures"
            element={
              <RouteGuard>
                <SalaryStructuresPage />
              </RouteGuard>
            }
          />
          <Route
            path="/operations/salary-structures/:designationId"
            element={
              <RouteGuard>
                <DesignationSalaryPage />
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
  grantFor(["VIEW", "CREATE"]);
  mockApi();
});

describe("Salary Structures list (spec 013a US1, US4)", () => {
  it("shows each designation with the salary in effect in rupees and the date as DD/MM/YYYY", async () => {
    renderAt("/operations/salary-structures");
    expect(await screen.findByText("Principal")).toBeInTheDocument();
    expect(screen.getByText(/₹1,25,000\.00/)).toBeInTheDocument();
    expect(screen.getByText(/from 01\/11\/2026/)).toBeInTheDocument();
  });

  it("says no salary yet, shows the flag with the number of people and the Teacher note", async () => {
    renderAt("/operations/salary-structures");
    await screen.findByText("Primary Teacher");
    expect(screen.getByText("No salary yet")).toBeInTheDocument();
    expect(screen.getByText("3 people")).toBeInTheDocument();
    expect(
      screen.getByText("Teachers with no salary of their own"),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /Only the default offered when a Teacher's salary is set/,
      ),
    ).toBeInTheDocument();
  });

  it("filters to the designations that leave people without a salary", async () => {
    const user = userEvent.setup();
    renderAt("/operations/salary-structures");
    await screen.findByText("Principal");
    await user.click(screen.getByLabelText(/only designations/i));
    expect(screen.queryByText("Principal")).not.toBeInTheDocument();
    expect(screen.getByText("Primary Teacher")).toBeInTheDocument();
  });

  it("shows an error state when the list cannot be loaded", async () => {
    authFetch.mockResolvedValue(json({ reason: "Could not load" }, 500));
    renderAt("/operations/salary-structures");
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load",
    );
  });

  it("shows an empty state when there are no designations", async () => {
    authFetch.mockResolvedValue(json([]));
    renderAt("/operations/salary-structures");
    expect(await screen.findByText("No designations yet.")).toBeInTheDocument();
  });

  it.each(["light", "dark"] as const)(
    "has no accessibility violations in %s mode",
    async (mode) => {
      const { container } = renderAt("/operations/salary-structures", mode);
      await screen.findByText("Principal");
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});

describe("Designation salary page (spec 013a US1)", () => {
  const PATH = `/operations/salary-structures/${DESIGNATION_ID}`;

  it("shows the history newest first with who, when and the note", async () => {
    renderAt(PATH);
    await screen.findByRole("heading", { name: "Principal" });
    const table = screen.getByRole("table", { name: /salary history/i });
    const rows = within(table).getAllByRole("row");
    expect(rows[1]).toHaveTextContent("₹1,25,000.00");
    expect(rows[1]).toHaveTextContent("01/11/2026");
    expect(rows[1]).toHaveTextContent("A. Admin");
    expect(rows[1]).toHaveTextContent("Annual revision");
    expect(rows[2]).toHaveTextContent("₹1,10,000.00");
  });

  it("names each problem inline and sends nothing while the form is invalid", async () => {
    const user = userEvent.setup();
    renderAt(PATH);
    await screen.findByRole("heading", { name: "Principal" });
    authFetch.mockClear();

    await user.type(screen.getByLabelText(/monthly salary/i), "100.123");
    await user.click(screen.getByRole("button", { name: "Record salary" }));
    expect(
      screen.getByText("The amount can have at most two decimal places."),
    ).toBeInTheDocument();

    const amount = screen.getByLabelText(/monthly salary/i);
    await user.clear(amount);
    await user.type(amount, "0");
    await user.click(screen.getByRole("button", { name: "Record salary" }));
    expect(
      screen.getByText("The amount must be more than zero."),
    ).toBeInTheDocument();

    await user.clear(amount);
    await user.type(amount, "10000000");
    await user.click(screen.getByRole("button", { name: "Record salary" }));
    expect(screen.getByText(/at most 9,999,999\.99/)).toBeInTheDocument();
    expect(authFetch).not.toHaveBeenCalled();
  });

  it("refuses a date before the first day of this month", async () => {
    const user = userEvent.setup();
    renderAt(PATH);
    await screen.findByRole("heading", { name: "Principal" });
    await user.type(screen.getByLabelText(/monthly salary/i), "30000");
    const date = screen.getByLabelText(/effective from/i);
    await user.clear(date);
    await user.type(date, "2020-01-01");
    await user.click(screen.getByRole("button", { name: "Record salary" }));
    expect(
      screen.getByText(/cannot be before the first day of this month/i),
    ).toBeInTheDocument();
  });

  it("records a valid salary and reloads the history", async () => {
    const user = userEvent.setup();
    renderAt(PATH);
    await screen.findByRole("heading", { name: "Principal" });
    await user.type(screen.getByLabelText(/monthly salary/i), "130000");
    await user.click(screen.getByRole("button", { name: "Record salary" }));

    await waitFor(() =>
      expect(authFetch).toHaveBeenCalledWith(
        `/api/v1/salary-structures/designations/${DESIGNATION_ID}/salaries`,
        expect.objectContaining({ method: "POST" }),
      ),
    );
    const post = authFetch.mock.calls.find(
      ([, init]) => init?.method === "POST",
    );
    expect(JSON.parse(post![1].body)).toEqual({
      amount: "130000",
      effectiveOn: firstOfThisMonthIso(),
      note: null,
    });
    expect(
      await screen.findByText("The salary was recorded."),
    ).toBeInTheDocument();
  });

  it("shows the server's reason when the record is refused", async () => {
    const user = userEvent.setup();
    mockApi(json({ reason: "That designation does not exist." }, 404));
    renderAt(PATH);
    await screen.findByRole("heading", { name: "Principal" });
    await user.type(screen.getByLabelText(/monthly salary/i), "30000");
    await user.click(screen.getByRole("button", { name: "Record salary" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "That designation does not exist.",
    );
  });

  it.each(["light", "dark"] as const)(
    "has no accessibility violations in %s mode",
    async (mode) => {
      const { container } = renderAt(PATH, mode);
      await screen.findByRole("heading", { name: "Principal" });
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});

describe("per-role visibility (SC-004)", () => {
  it("hides the record form from a viewer without CREATE", async () => {
    grantFor(["VIEW"]);
    renderAt(`/operations/salary-structures/${DESIGNATION_ID}`);
    await screen.findByRole("heading", { name: "Principal" });
    expect(screen.queryByText("Record a salary")).not.toBeInTheDocument();
    expect(screen.getByText("History")).toBeInTheDocument();
  });

  it.each(["Zone Manager", "Teacher", "System"])(
    "shows 'Not authorized' and fetches nothing for a %s deep link",
    async () => {
      navigation = []; // these roles are never given the item
      renderAt(`/operations/salary-structures/${DESIGNATION_ID}`);
      expect(await screen.findByText("Not authorized")).toBeInTheDocument();
      expect(authFetch).not.toHaveBeenCalled();
    },
  );
});
