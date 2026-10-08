import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ManagersPage } from "./ManagersPage";

const authFetch = vi.fn();
let managerActions = ["VIEW", "CREATE", "EDIT"];
let designationActions: string[] = ["VIEW", "CREATE", "EDIT"];

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch, user: { id: "me", displayName: "Me" } }),
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
              label: "Managers",
              route: "/master-data/managers",
              actions: managerActions,
            },
            ...(designationActions.length
              ? [
                  {
                    label: "Designations",
                    route: "/master-data/designations",
                    actions: designationActions,
                  },
                ]
              : []),
          ],
        },
      ],
    },
  }),
}));

const COMPLETE = {
  id: "m1",
  userId: "u1",
  displayName: "Manoj Manager",
  phone: "9800000003",
  active: true,
  version: 4,
  zones: [{ id: "z1", name: "North Zone" }],
  schoolCount: 2,
  employment: {
    employeeId: "HLS-M-001",
    joiningDate: "2026-01-05",
    exitDate: null,
    designation: { id: "d1", name: "Zone Manager", retired: false },
    history: [
      {
        designationId: "d1",
        name: "Zone Manager",
        effectiveOn: "2026-01-05",
        recordedAt: "2026-01-06T10:00:00Z",
      },
    ],
    missing: [],
  },
};
const GAPS = {
  id: "m2",
  userId: "u2",
  displayName: "Olga Other",
  phone: "9800000009",
  active: false,
  version: 0,
  zones: [],
  schoolCount: 0,
  employment: {
    employeeId: null,
    joiningDate: null,
    exitDate: null,
    designation: null,
    missing: ["DESIGNATION", "JOINING_DATE", "EXIT_DATE"],
  },
};

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function mockApi(rows: unknown[]) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method) return jsonResponse(COMPLETE);
    if (url.startsWith("/api/v1/designations/options")) {
      return jsonResponse([
        { id: "d1", name: "Zone Manager", kind: "MANAGER", retired: false },
        {
          id: "d2",
          name: "Senior Zone Manager",
          kind: "MANAGER",
          retired: false,
        },
      ]);
    }
    if (url.startsWith("/api/v1/managers/m")) return jsonResponse(COMPLETE);
    return jsonResponse({
      content: rows,
      page: 0,
      size: 25,
      totalElements: rows.length,
    });
  });
}

describe("Managers screen: employment details (spec 005a US2)", () => {
  beforeEach(() => {
    authFetch.mockReset();
    managerActions = ["VIEW", "CREATE", "EDIT"];
    designationActions = ["VIEW", "CREATE", "EDIT"];
    window.history.pushState({}, "", "/master-data/managers");
  });

  it("shows designation, employee id and joining date, and flags what is missing", async () => {
    mockApi([COMPLETE, GAPS]);
    render(<ManagersPage />);

    expect((await screen.findAllByText("Zone Manager")).length).toBeGreaterThan(
      0,
    );
    expect(screen.getByText("HLS-M-001")).toBeInTheDocument();
    expect(screen.getByText("05/01/2026")).toBeInTheDocument();
    expect(screen.getByText("designation missing")).toBeInTheDocument();
    expect(screen.getByText("joining date missing")).toBeInTheDocument();
    expect(screen.getByText("exit date missing")).toBeInTheDocument();
  });

  it("shows no flag when every detail is recorded", async () => {
    mockApi([COMPLETE]);
    render(<ManagersPage />);

    await screen.findByText("Manoj Manager");
    expect(
      screen.queryByText(/(designation|joining date|exit date) missing/i),
    ).toBeNull();
  });

  it("offers the employment editor only with the Designations EDIT grant", async () => {
    mockApi([COMPLETE]);
    const { unmount } = render(<ManagersPage />);
    await screen.findByText("Manoj Manager");
    expect(
      screen.getByRole("button", { name: "Employment" }),
    ).toBeInTheDocument();
    unmount();

    // a Zone Manager style fixture: sees the fields, has no Designations item and so no editor
    designationActions = [];
    mockApi([COMPLETE]);
    render(<ManagersPage />);
    await screen.findByText("Manoj Manager");
    expect(screen.getByText("HLS-M-001")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Employment" })).toBeNull();
  });

  it("sends the employment fields, then the new designation with its effective date", async () => {
    const user = userEvent.setup();
    mockApi([COMPLETE]);
    render(<ManagersPage />);
    await screen.findByText("Manoj Manager");

    await user.click(screen.getByRole("button", { name: "Employment" }));
    expect(await screen.findByText("Designation history")).toBeInTheDocument();
    const id = within(screen.getByRole("dialog")).getByLabelText(
      /^employee id/i,
    );
    await user.clear(id);
    await user.type(id, "HLS-M-777");
    await user.click(screen.getByLabelText("Designation"));
    await user.click(
      await screen.findByRole("option", { name: "Senior Zone Manager" }),
    );
    await user.click(screen.getByRole("button", { name: "Save" }));

    const writes = authFetch.mock.calls.filter(([, init]) => init?.method);
    expect(writes.map(([url]) => url)).toEqual([
      "/api/v1/managers/m1/employment",
      "/api/v1/managers/m1/designation",
    ]);
    expect(JSON.parse(writes[0][1].body)).toMatchObject({
      employeeId: "HLS-M-777",
      version: 4,
    });
    expect(JSON.parse(writes[1][1].body)).toMatchObject({
      designationId: "d2",
    });
  });

  it("filters to Managers with missing details", async () => {
    const user = userEvent.setup();
    mockApi([COMPLETE, GAPS]);
    render(<ManagersPage />);
    await screen.findByText("Manoj Manager");

    await user.click(screen.getByLabelText("Missing details"));

    const last = authFetch.mock.calls
      .map(([url]) => String(url))
      .filter((u) => u.startsWith("/api/v1/managers?"))
      .pop();
    expect(last).toContain("missing=true");
  });

  it("starts filtered when opened from the Designations screen", async () => {
    window.history.pushState({}, "", "/master-data/managers?missing=true");
    mockApi([GAPS]);
    render(<ManagersPage />);

    await screen.findByText("Olga Other");
    expect(String(authFetch.mock.calls[0][0])).toContain("missing=true");
    expect(screen.getByLabelText("Missing details")).toBeChecked();
  });
});
