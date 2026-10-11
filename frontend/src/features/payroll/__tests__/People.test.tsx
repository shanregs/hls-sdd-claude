import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { axe } from "vitest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../../app/RouteGuard";
import { buildMuiTheme, type ThemeMode } from "../../../theme/tokens";
import { SalaryStructuresPage } from "../SalaryStructuresPage";

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

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const PEOPLE = [
  {
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
  {
    kind: "MANAGER",
    personId: "m-2",
    name: "Meena Manager",
    designation: "Zone Manager",
    inEffect: { amount: "50000.00", effectiveOn: "2026-11-01" },
    joiningDate: null,
    exitDate: null,
    active: true,
    salaryEditable: true,
    roles: [],
  },
  {
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
  {
    kind: "STAFF",
    personId: "u-1",
    name: "Asha Admin",
    designation: null,
    inEffect: null,
    joiningDate: null,
    exitDate: null,
    active: true,
    salaryEditable: true,
    roles: ["ADMIN", "DIRECTOR"],
  },
  {
    kind: "TEACHER",
    personId: "t-2",
    name: "Retired Teacher",
    designation: null,
    inEffect: null,
    joiningDate: null,
    exitDate: null,
    active: false,
    salaryEditable: false,
    roles: [],
  },
];

function page(content: unknown[], total = content.length) {
  return { content, page: 0, size: 25, totalElements: total };
}

function renderPeople(mode: ThemeMode = "light") {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <MemoryRouter initialEntries={["/operations/salary-structures"]}>
        <Routes>
          <Route
            path="/operations/salary-structures"
            element={
              <RouteGuard>
                <SalaryStructuresPage />
              </RouteGuard>
            }
          />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

function lastRequest(): URLSearchParams {
  const urls = authFetch.mock.calls.map(([url]) => url as string);
  const last = urls[urls.length - 1];
  return new URL(last, "http://localhost").searchParams;
}

beforeEach(() => {
  authFetch.mockReset();
  authFetch.mockImplementation(async () => json(page(PEOPLE)));
  navigation = [
    {
      section: "OPERATIONS",
      items: [
        {
          label: "Salary Structures",
          route: "/operations/salary-structures",
          actions: ["VIEW", "CREATE"],
        },
      ],
    },
  ];
});

describe("People tab (spec 013a User Story 5)", () => {
  it("lists everyone with their own salary in rupees and the date as DD/MM/YYYY", async () => {
    renderPeople();
    expect(await screen.findByText("Manoj Manager")).toBeInTheDocument();
    expect(screen.getByText(/₹65,000\.00/)).toBeInTheDocument();
    expect(screen.getByText(/₹50,000\.00/)).toBeInTheDocument();
    expect(screen.getAllByText(/from 01\/11\/2026/)).toHaveLength(2);
    // two Managers on one designation show different salaries
    expect(screen.getAllByText("Zone Manager")).toHaveLength(2);
  });

  it("says no salary yet for a person without one and shows a staff member's roles", async () => {
    renderPeople();
    await screen.findByText("Asha Admin");
    expect(screen.getAllByText("No salary yet").length).toBeGreaterThanOrEqual(
      2,
    );
    expect(screen.getByText("Admin, Director")).toBeInTheDocument();
  });

  it("marks someone who no longer works here", async () => {
    renderPeople();
    await screen.findByText("Retired Teacher");
    expect(screen.getByText("Not working here")).toBeInTheDocument();
  });

  it("links each person to their own salary page, Teachers included", async () => {
    renderPeople();
    const manoj = await screen.findByRole("link", { name: "Manoj Manager" });
    expect(manoj).toHaveAttribute(
      "href",
      "/operations/salary-structures/people/MANAGER/m-1",
    );
    expect(screen.getByRole("link", { name: "Tara Teacher" })).toHaveAttribute(
      "href",
      "/operations/salary-structures/people/TEACHER/t-1",
    );
    expect(screen.getByRole("link", { name: "Asha Admin" })).toHaveAttribute(
      "href",
      "/operations/salary-structures/people/STAFF/u-1",
    );
  });

  it("asks the server for people without a salary when the filter is switched on", async () => {
    const user = userEvent.setup();
    renderPeople();
    await screen.findByText("Manoj Manager");
    await user.click(screen.getByLabelText("Only people without a salary"));
    await waitFor(() =>
      expect(lastRequest().get("withoutSalary")).toBe("true"),
    );
    expect(lastRequest().get("page")).toBe("0");
  });

  it("asks for one kind of person and searches by name", async () => {
    const user = userEvent.setup();
    renderPeople();
    await screen.findByText("Manoj Manager");
    await user.type(screen.getByLabelText("Search by name"), "Tara");
    await waitFor(() => expect(lastRequest().get("q")).toBe("Tara"));

    await user.click(screen.getByLabelText("Who"));
    await user.click(await screen.findByRole("option", { name: "Managers" }));
    await waitFor(() => expect(lastRequest().get("kind")).toBe("MANAGER"));
  });

  it("pages through a long list", async () => {
    authFetch.mockImplementation(async () => json(page(PEOPLE, 60)));
    const user = userEvent.setup();
    renderPeople();
    await screen.findByText("Manoj Manager");
    await user.click(screen.getByRole("button", { name: /go to page 2/i }));
    await waitFor(() => expect(lastRequest().get("page")).toBe("1"));
  });

  it("shows an empty state, and a different one when everyone has a salary", async () => {
    authFetch.mockImplementation(async () => json(page([])));
    const user = userEvent.setup();
    renderPeople();
    expect(await screen.findByText("No people found.")).toBeInTheDocument();
    await user.click(screen.getByLabelText("Only people without a salary"));
    expect(
      await screen.findByText("Everyone has a salary."),
    ).toBeInTheDocument();
  });

  it("shows an error state when the list cannot be loaded", async () => {
    authFetch.mockImplementation(async () =>
      json({ reason: "Could not load the people." }, 500),
    );
    renderPeople();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load the people.",
    );
  });

  it("shows nothing and fetches nothing for a role without the item", async () => {
    navigation = [];
    renderPeople();
    expect(await screen.findByText("Not authorized")).toBeInTheDocument();
    expect(authFetch).not.toHaveBeenCalled();
  });

  it.each(["light", "dark"] as const)(
    "has no accessibility violations in %s mode",
    async (mode) => {
      const { container } = renderPeople(mode);
      await screen.findByText("Manoj Manager");
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});
