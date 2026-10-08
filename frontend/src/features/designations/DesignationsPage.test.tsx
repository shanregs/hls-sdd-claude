import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { DesignationsPage } from "./DesignationsPage";

const authFetch = vi.fn();
let grantedActions = ["VIEW", "CREATE", "EDIT"];

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

vi.mock("../../access-model/useAccessModel", () => ({
  useAccessModel: () => ({
    loading: false,
    accessModel: {
      roles: ["ADMIN"],
      dataScope: {},
      navigation: [
        {
          section: "MASTER DATA",
          items: [
            {
              label: "Designations",
              route: "/master-data/designations",
              actions: grantedActions,
            },
          ],
        },
      ],
    },
  }),
}));

const ROWS = [
  {
    id: "d1",
    name: "Primary Teacher",
    kind: "TEACHER",
    retired: false,
    holders: 4,
    version: 0,
  },
  {
    id: "d2",
    name: "Zone Manager",
    kind: "MANAGER",
    retired: true,
    holders: 1,
    version: 2,
  },
];

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function mockApi(
  summary = {
    teachersMissingDesignation: 3,
    managersMissingDesignation: 1,
    managersMissingJoiningDate: 2,
    managersMissingExitDate: 0,
  },
  write: Response = jsonResponse(ROWS[0]),
) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method) return write;
    if (url === "/api/v1/designations/summary") return jsonResponse(summary);
    return jsonResponse(ROWS);
  });
}

function renderPage() {
  return render(
    <MemoryRouter>
      <DesignationsPage />
    </MemoryRouter>,
  );
}

describe("DesignationsPage (spec 005a US1 and US4)", () => {
  beforeEach(() => {
    authFetch.mockReset();
    grantedActions = ["VIEW", "CREATE", "EDIT"];
  });

  it("lists designations with kind, status and the number of people", async () => {
    mockApi();
    renderPage();

    expect(
      (await screen.findAllByText("Primary Teacher")).length,
    ).toBeGreaterThan(0);
    expect(screen.getAllByText("Zone Manager").length).toBeGreaterThan(0);
    expect(screen.getByText("Retired")).toBeInTheDocument();
    expect(screen.getByText("Active")).toBeInTheDocument();
    expect(screen.getByText("Teachers")).toBeInTheDocument();
    expect(screen.getByText("Managers")).toBeInTheDocument();
  });

  it("offers add, rename, retire and reactivate with the grants", async () => {
    mockApi();
    renderPage();
    await screen.findAllByText("Primary Teacher");

    expect(
      screen.getByRole("button", { name: "Add designation" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retire" })).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Reactivate" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Edit Primary Teacher" }),
    ).toBeInTheDocument();
  });

  it("hides every action for a read-only grant", async () => {
    grantedActions = ["VIEW"];
    mockApi();
    renderPage();
    await screen.findAllByText("Primary Teacher");

    expect(
      screen.queryByRole("button", { name: "Add designation" }),
    ).toBeNull();
    expect(screen.queryByRole("button", { name: "Retire" })).toBeNull();
    expect(screen.queryByRole("button", { name: /^Edit / })).toBeNull();
  });

  it("names the problem when the name is empty and shows the server's refusal", async () => {
    const user = userEvent.setup();
    mockApi(
      undefined,
      jsonResponse(
        { reason: 'There is already a designation named "X".' },
        409,
      ),
    );
    renderPage();
    await screen.findAllByText("Primary Teacher");

    await user.click(screen.getByRole("button", { name: "Add designation" }));
    await user.click(screen.getByRole("button", { name: "Save" }));
    expect(await screen.findByText("Enter a name.")).toBeInTheDocument();

    await user.type(screen.getByLabelText(/name/i), "X");
    await user.click(screen.getByRole("button", { name: "Save" }));
    expect(
      await screen.findByText(/already a designation/),
    ).toBeInTheDocument();
  });

  it("shows the missing-details counts with links to the filtered lists", async () => {
    mockApi();
    renderPage();

    const teachers = await screen.findByRole("link", { name: "3" });
    expect(teachers).toHaveAttribute(
      "href",
      "/master-data/teachers?missingDesignation=true",
    );
    expect(screen.getByRole("link", { name: "1" })).toHaveAttribute(
      "href",
      "/master-data/managers?missing=true",
    );
  });

  it("shows no flag when every count is zero", async () => {
    mockApi({
      teachersMissingDesignation: 0,
      managersMissingDesignation: 0,
      managersMissingJoiningDate: 0,
      managersMissingExitDate: 0,
    });
    renderPage();

    expect(
      await screen.findByText(/every manager and teacher has the details/i),
    ).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "0" })).toBeNull();
  });

  it("shows an error when the list cannot be loaded", async () => {
    authFetch.mockResolvedValue(jsonResponse({ reason: "Boom" }, 500));
    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent("Boom");
  });
});
