import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../../app/RouteGuard";
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

function json(body: unknown) {
  return { ok: true, status: 200, json: async () => body } as Response;
}

const NOTE = "People holding this designation with no salary of their own";
const DEFAULT_NOTE =
  "A suggested default only: it is offered when a person's salary is entered, and nobody is paid it.";

const ROWS = [
  {
    designationId: "m1",
    name: "Zone Coordinator",
    kind: "MANAGER",
    retired: false,
    inEffect: null,
    missingSalary: { flag: true, people: 1, note: NOTE },
    defaultNote: DEFAULT_NOTE,
  },
  {
    designationId: "t1",
    name: "Senior Teacher",
    kind: "TEACHER",
    retired: false,
    inEffect: { amount: "30000.00", effectiveOn: "2026-10-01" },
    missingSalary: { flag: true, people: 4, note: NOTE },
    defaultNote: DEFAULT_NOTE,
  },
  {
    designationId: "t2",
    name: "Junior Teacher",
    kind: "TEACHER",
    retired: false,
    inEffect: { amount: "18000.00", effectiveOn: "2026-10-01" },
    missingSalary: { flag: false, people: 0, note: NOTE },
    defaultNote: DEFAULT_NOTE,
  },
];

function renderList() {
  return render(
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
    </MemoryRouter>,
  );
}

beforeEach(() => {
  authFetch.mockReset();
  authFetch.mockImplementation(async (url: string) =>
    url.includes("/people")
      ? json({ content: [], page: 0, size: 25, totalElements: 0 })
      : json(ROWS),
  );
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

describe("Missing-salary flag on the designation defaults (spec 013a US4)", () => {
  it("shows the flag with the number of people who hold each designation without a salary of their own", async () => {
    const user = userEvent.setup();
    renderList();
    await user.click(
      await screen.findByRole("tab", { name: "Designation defaults" }),
    );
    await screen.findByText("Zone Coordinator");
    expect(screen.getByText("1 person")).toBeInTheDocument();
    expect(screen.getByText("4 people")).toBeInTheDocument();
    expect(screen.getByText("None")).toBeInTheDocument(); // the designation that leaves nobody without a salary
  });

  it("counts the flagged designations in the filter label", async () => {
    const user = userEvent.setup();
    renderList();
    await user.click(
      await screen.findByRole("tab", { name: "Designation defaults" }),
    );
    await screen.findByText("Senior Teacher");
    expect(
      screen.getByLabelText(
        /only designations held by people without a salary \(2\)/i,
      ),
    ).toBeInTheDocument();
  });

  it("shows nothing for a role without access", async () => {
    navigation = [];
    renderList();
    expect(await screen.findByText("Not authorized")).toBeInTheDocument();
    expect(screen.queryByText("1 person")).not.toBeInTheDocument();
    expect(authFetch).not.toHaveBeenCalled();
  });
});
