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
const DEFAULT_NOTE =
  "A suggested default only: it is offered when a person's salary is entered, and nobody is paid it.";

const LIST = [
  {
    designationId: DESIGNATION_ID,
    name: "Principal",
    kind: "MANAGER",
    retired: false,
    inEffect: { amount: "125000.00", effectiveOn: "2026-11-01" },
    missingSalary: { flag: false, people: 0, note: null },
    defaultNote: DEFAULT_NOTE,
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
      note: "People holding this designation with no salary of their own",
    },
    defaultNote: DEFAULT_NOTE,
  },
];

const DETAIL = {
  designationId: DESIGNATION_ID,
  name: "Principal",
  kind: "MANAGER",
  retired: false,
  inEffect: { amount: "125000.00", effectiveOn: "2026-11-01" },
  missingSalary: { flag: false, people: 0, note: null },
  defaultNote: DEFAULT_NOTE,
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

const NO_PEOPLE = { content: [], page: 0, size: 25, totalElements: 0 };

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

/** The People tab opens first, so every test also needs an answer for it. */
function mockApi(
  write: Response = json({ id: "r3" }, 201),
  list: Response = json(LIST),
) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method) return write;
    if (url.includes("/people")) return json(NO_PEOPLE);
    if (url.includes("/designations/")) return json(DETAIL);
    return list;
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

async function openDefaultsTab() {
  const user = userEvent.setup();
  await user.click(
    await screen.findByRole("tab", { name: "Designation defaults" }),
  );
  return user;
}

beforeEach(() => {
  authFetch.mockReset();
  grantFor(["VIEW", "CREATE"]);
  mockApi();
});

describe("Salary Structures page tabs", () => {
  it("opens on the People tab and offers the designation defaults on the second", async () => {
    renderAt("/operations/salary-structures");
    expect(
      await screen.findByRole("tab", { name: "People", selected: true }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("tab", { name: "Designation defaults" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Pay policy" }),
    ).toBeInTheDocument();
  });

  it("links each tab to a labelled panel", async () => {
    renderAt("/operations/salary-structures");
    const tab = await screen.findByRole("tab", { name: "People" });
    const panel = screen.getByRole("tabpanel", { name: "People" });
    expect(tab).toHaveAttribute("aria-controls", panel.id);
  });
});

describe("Designation defaults tab (spec 013a US1, US4)", () => {
  it("shows each designation with its suggested default in rupees and the date as DD/MM/YYYY", async () => {
    renderAt("/operations/salary-structures");
    await openDefaultsTab();
    expect(await screen.findByText("Principal")).toBeInTheDocument();
    expect(screen.getByText(/₹1,25,000\.00/)).toBeInTheDocument();
    expect(screen.getByText(/from 01\/11\/2026/)).toBeInTheDocument();
    expect(screen.getByText(/only suggestions/i)).toBeInTheDocument();
  });

  it("says no default yet and shows the flag with the number of people who hold it without a salary", async () => {
    renderAt("/operations/salary-structures");
    await openDefaultsTab();
    await screen.findByText("Primary Teacher");
    expect(screen.getByText("No default yet")).toBeInTheDocument();
    expect(screen.getByText("3 people")).toBeInTheDocument();
    expect(
      screen.getByText(
        "People holding this designation with no salary of their own",
      ),
    ).toBeInTheDocument();
  });

  it("filters to the designations held by people without a salary", async () => {
    renderAt("/operations/salary-structures");
    const user = await openDefaultsTab();
    await screen.findByText("Principal");
    await user.click(screen.getByLabelText(/only designations/i));
    expect(screen.queryByText("Principal")).not.toBeInTheDocument();
    expect(screen.getByText("Primary Teacher")).toBeInTheDocument();
  });

  it("shows an error state when the list cannot be loaded", async () => {
    mockApi(json({}), json({ reason: "Could not load" }, 500));
    renderAt("/operations/salary-structures");
    await openDefaultsTab();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load",
    );
  });

  it("shows an empty state when there are no designations", async () => {
    mockApi(json({}), json([]));
    renderAt("/operations/salary-structures");
    await openDefaultsTab();
    expect(await screen.findByText("No designations yet.")).toBeInTheDocument();
  });

  it.each(["light", "dark"] as const)(
    "has no accessibility violations in %s mode",
    async (mode) => {
      const { container } = renderAt("/operations/salary-structures", mode);
      await openDefaultsTab();
      await screen.findByText("Principal");
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});

describe("Designation default page (spec 013a US1)", () => {
  const PATH = `/operations/salary-structures/${DESIGNATION_ID}`;

  it("says the amount is only a suggested default", async () => {
    renderAt(PATH);
    await screen.findByRole("heading", { name: "Principal" });
    expect(
      screen.getByRole("heading", {
        name: "Suggested default in effect today",
      }),
    ).toBeInTheDocument();
    expect(screen.getByText(/nobody is paid it/i)).toBeInTheDocument();
  });

  it("shows the history newest first with who, when and the note", async () => {
    renderAt(PATH);
    await screen.findByRole("heading", { name: "Principal" });
    const table = screen.getByRole("table", { name: /default history/i });
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

    await user.type(
      screen.getByLabelText(/suggested monthly salary/i),
      "100.123",
    );
    await user.click(screen.getByRole("button", { name: "Record default" }));
    expect(
      screen.getByText("The amount can have at most two decimal places."),
    ).toBeInTheDocument();

    const amount = screen.getByLabelText(/suggested monthly salary/i);
    await user.clear(amount);
    await user.type(amount, "0");
    await user.click(screen.getByRole("button", { name: "Record default" }));
    expect(
      screen.getByText("The amount must be more than zero."),
    ).toBeInTheDocument();

    await user.clear(amount);
    await user.type(amount, "10000000");
    await user.click(screen.getByRole("button", { name: "Record default" }));
    expect(screen.getByText(/at most 9,999,999\.99/)).toBeInTheDocument();
    expect(authFetch).not.toHaveBeenCalled();
  });

  it("records a valid default and reloads the history", async () => {
    const user = userEvent.setup();
    renderAt(PATH);
    await screen.findByRole("heading", { name: "Principal" });
    await user.type(
      screen.getByLabelText(/suggested monthly salary/i),
      "130000",
    );
    await user.click(screen.getByRole("button", { name: "Record default" }));

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
      await screen.findByText("The default was recorded."),
    ).toBeInTheDocument();
  });

  it("shows the server's reason when the record is refused", async () => {
    const user = userEvent.setup();
    mockApi(json({ reason: "That designation does not exist." }, 404));
    renderAt(PATH);
    await screen.findByRole("heading", { name: "Principal" });
    await user.type(
      screen.getByLabelText(/suggested monthly salary/i),
      "30000",
    );
    await user.click(screen.getByRole("button", { name: "Record default" }));
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
    expect(
      screen.queryByText("Record a suggested default"),
    ).not.toBeInTheDocument();
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
