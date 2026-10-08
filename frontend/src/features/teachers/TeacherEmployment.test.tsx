import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TeachersPage } from "./TeachersPage";
import { MyTeacherProfile } from "./MyTeacherProfile";

const authFetch = vi.fn();
let teacherActions = ["VIEW", "CREATE", "EDIT"];
let designationActions: string[] = ["VIEW", "CREATE", "EDIT"];

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
              label: "Teachers",
              route: "/master-data/teachers",
              actions: teacherActions,
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

function teacher(over: Record<string, unknown> = {}) {
  return {
    id: "t1",
    name: "Tara Teacher",
    phone: "9800000004",
    email: null,
    address: null,
    status: "ACTIVE",
    statusEffectiveOn: "2026-04-01",
    allowedNextStatuses: [],
    userId: null,
    version: 3,
    school: { id: "s1", name: "St Mary's" },
    manager: null,
    pendingPlacement: null,
    placements: null,
    employment: {
      employeeId: "HLS-T-001",
      designation: { id: "d1", name: "Primary Teacher", retired: false },
      missing: [],
    },
    ...over,
  };
}

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function mockApi(rows: unknown[]) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method) return jsonResponse(teacher());
    if (url.startsWith("/api/v1/designations/options")) {
      return jsonResponse([
        { id: "d1", name: "Primary Teacher", kind: "TEACHER", retired: false },
        { id: "d2", name: "Senior Teacher", kind: "TEACHER", retired: false },
      ]);
    }
    return jsonResponse({
      content: rows,
      page: 0,
      size: 25,
      totalElements: rows.length,
    });
  });
}

describe("Teachers screen: designation and employee id (spec 005a US3)", () => {
  beforeEach(() => {
    authFetch.mockReset();
    teacherActions = ["VIEW", "CREATE", "EDIT"];
    designationActions = ["VIEW", "CREATE", "EDIT"];
    window.history.pushState({}, "", "/master-data/teachers");
  });

  it("shows the designation and employee id, and flags a Teacher with none", async () => {
    mockApi([
      teacher(),
      teacher({
        id: "t2",
        name: "Unplaced Teacher",
        employment: {
          employeeId: null,
          designation: null,
          missing: ["DESIGNATION"],
        },
      }),
    ]);
    render(<TeachersPage />);

    expect(await screen.findByText("Primary Teacher")).toBeInTheDocument();
    expect(screen.getByText("HLS-T-001")).toBeInTheDocument();
    expect(screen.getByText("designation missing")).toBeInTheDocument();
  });

  it("lets a Zone Manager (Teachers EDIT, no Designations grant) read but not edit them", async () => {
    designationActions = [];
    teacherActions = ["VIEW", "EDIT"];
    mockApi([teacher()]);
    render(<TeachersPage />);

    expect(await screen.findByText("Primary Teacher")).toBeInTheDocument();
    expect(screen.getByText("HLS-T-001")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Employment" })).toBeNull();
  });

  it("saves the designation and employee id with the Teacher's version", async () => {
    const user = userEvent.setup();
    mockApi([teacher()]);
    render(<TeachersPage />);
    await screen.findByText("Primary Teacher");

    await user.click(screen.getByRole("button", { name: "Employment" }));
    const id = within(await screen.findByRole("dialog")).getByLabelText(
      /^employee id/i,
    );
    await user.clear(id);
    await user.type(id, "HLS-T-900");
    await user.click(screen.getByLabelText("Designation"));
    await user.click(
      await screen.findByRole("option", { name: "Senior Teacher" }),
    );
    await user.click(screen.getByRole("button", { name: "Save" }));

    const write = authFetch.mock.calls.find(([, init]) => init?.method);
    expect(write?.[0]).toBe("/api/v1/teachers/t1/employment");
    expect(JSON.parse(write?.[1].body)).toEqual({
      designationId: "d2",
      employeeId: "HLS-T-900",
      version: 3,
    });
  });

  it("filters to Teachers with no designation, also from the Designations link", async () => {
    window.history.pushState(
      {},
      "",
      "/master-data/teachers?missingDesignation=true",
    );
    mockApi([
      teacher({
        employment: {
          employeeId: null,
          designation: null,
          missing: ["DESIGNATION"],
        },
      }),
    ]);
    render(<TeachersPage />);

    await screen.findByText("Tara Teacher");
    expect(String(authFetch.mock.calls[0][0])).toContain(
      "missingDesignation=true",
    );
    expect(screen.getByLabelText("Missing designation")).toBeChecked();
  });

  it("My Profile shows neither field", async () => {
    authFetch.mockResolvedValue(jsonResponse(teacher({ employment: null })));
    render(<MyTeacherProfile />);

    expect(await screen.findByText("Tara Teacher")).toBeInTheDocument();
    expect(screen.queryByText(/employee id/i)).toBeNull();
    expect(screen.queryByText(/designation/i)).toBeNull();
  });
});
