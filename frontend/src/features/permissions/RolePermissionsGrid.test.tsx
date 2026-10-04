import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RolePermissionsGrid } from "./RolePermissionsGrid";

const authFetch = vi.fn();
let grantedActions = ["VIEW", "EDIT"];

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
          section: "SYSTEM",
          items: [
            {
              label: "Role & Permissions",
              route: "/identity/permissions",
              actions: grantedActions,
            },
          ],
        },
      ],
    },
  }),
}));

const ALL_ROLES = ["ADMIN", "DIRECTOR", "MANAGER", "TEACHER", "SYSTEM"];

function eligible(by: Record<string, string[]>) {
  return Object.fromEntries(ALL_ROLES.map((r) => [r, by[r] ?? []]));
}

const MATRIX = {
  entries: [
    { role: "ADMIN", module: "DASHBOARD", action: "VIEW", granted: true },
    { role: "DIRECTOR", module: "DASHBOARD", action: "VIEW", granted: true },
    { role: "MANAGER", module: "DASHBOARD", action: "VIEW", granted: true },
    { role: "ADMIN", module: "USER_MANAGEMENT", action: "VIEW", granted: true },
    { role: "ADMIN", module: "USER_MANAGEMENT", action: "EDIT", granted: true },
    {
      role: "DIRECTOR",
      module: "USER_MANAGEMENT",
      action: "VIEW",
      granted: true,
    },
    // A grant that was switched off stays a row, and reads as not granted.
    {
      role: "MANAGER",
      module: "USER_MANAGEMENT",
      action: "VIEW",
      granted: false,
    },
  ],
  modules: [
    {
      module: "DASHBOARD",
      eligible: eligible({
        ADMIN: ["VIEW"],
        DIRECTOR: ["VIEW"],
        MANAGER: ["VIEW"],
        TEACHER: ["VIEW"],
        SYSTEM: ["VIEW"],
      }),
    },
    {
      module: "USER_MANAGEMENT",
      eligible: eligible({
        ADMIN: ["VIEW", "CREATE", "EDIT"],
        DIRECTOR: ["VIEW", "CREATE", "EDIT"],
        MANAGER: ["VIEW", "CREATE", "EDIT"],
        TEACHER: ["VIEW", "CREATE", "EDIT"],
        SYSTEM: ["VIEW", "CREATE", "EDIT"],
      }),
    },
    {
      module: "IDENTITY_PERMISSIONS",
      eligible: eligible({
        ADMIN: ["VIEW", "EDIT"],
        DIRECTOR: ["VIEW", "EDIT"],
        SYSTEM: ["VIEW", "EDIT"],
      }),
    },
  ],
};

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function cell(module: string, role: string) {
  const row = screen.getByRole("rowheader", { name: module }).closest("tr")!;
  const column = ALL_ROLES.indexOf(role) + 1;
  return within(row).getAllByRole("cell")[column - 1];
}

describe("RolePermissionsGrid (User Story 3, FR-002/FR-005)", () => {
  beforeEach(() => {
    authFetch.mockReset();
    grantedActions = ["VIEW", "EDIT"];
  });

  it("shows one row per module and one column per role", async () => {
    authFetch.mockResolvedValue(json(MATRIX));

    render(<RolePermissionsGrid />);

    const table = await screen.findByRole("table", {
      name: "Role and permission matrix",
    });
    for (const role of ALL_ROLES) {
      expect(
        within(table).getByRole("columnheader", { name: role }),
      ).toBeInTheDocument();
    }
    expect(screen.getByRole("rowheader", { name: "DASHBOARD" })).toBeVisible();
    expect(
      screen.getByRole("rowheader", { name: "USER_MANAGEMENT" }),
    ).toBeVisible();
    expect(screen.getAllByRole("row")).toHaveLength(1 + MATRIX.modules.length);
  });

  it("shows a coloured icon for each granted action and a grey one when not granted", async () => {
    authFetch.mockResolvedValue(json(MATRIX));
    render(<RolePermissionsGrid />);
    await screen.findByRole("table");

    // ADMIN on USER_MANAGEMENT: View and Edit granted, Create eligible but not granted.
    const admin = cell("USER_MANAGEMENT", "ADMIN");
    expect(
      within(admin).getByRole("button", {
        name: "ADMIN View USER_MANAGEMENT: granted",
      }),
    ).toHaveAttribute("aria-pressed", "true");
    expect(
      within(admin).getByRole("button", {
        name: "ADMIN Edit USER_MANAGEMENT: granted",
      }),
    ).toHaveAttribute("aria-pressed", "true");
    expect(
      within(admin).getByRole("button", {
        name: "ADMIN Create USER_MANAGEMENT: not granted",
      }),
    ).toHaveAttribute("aria-pressed", "false");
    // A switched-off grant reads as not granted too; an action with no row at all as well.
    const manager = cell("USER_MANAGEMENT", "MANAGER");
    expect(
      within(manager).getByRole("button", {
        name: "MANAGER View USER_MANAGEMENT: not granted",
      }),
    ).toBeInTheDocument();
    expect(
      within(manager).getByRole("button", {
        name: "MANAGER Edit USER_MANAGEMENT: not granted",
      }),
    ).toBeInTheDocument();
  });

  it("shows a dash where nothing applies to the role", async () => {
    authFetch.mockResolvedValue(json(MATRIX));
    render(<RolePermissionsGrid />);
    await screen.findByRole("table");

    const teacher = cell("IDENTITY_PERMISSIONS", "TEACHER");
    expect(within(teacher).getByText("—")).toBeInTheDocument();
    expect(within(teacher).queryByRole("button")).toBeNull();
    expect(
      within(cell("IDENTITY_PERMISSIONS", "ADMIN")).getAllByRole("button"),
    ).toHaveLength(2);
  });

  it("opens the confirmation for the clicked icon and saves the change", async () => {
    authFetch.mockImplementation(async (url: string, init?: RequestInit) =>
      init?.method === "PUT" ? json({}) : json(MATRIX),
    );
    render(<RolePermissionsGrid />);
    const user = userEvent.setup();
    await screen.findByRole("table");

    await user.click(
      screen.getByRole("button", {
        name: "ADMIN Create USER_MANAGEMENT: not granted",
      }),
    );
    const dialog = await screen.findByRole("dialog");
    expect(dialog).toHaveTextContent("ADMIN");
    expect(dialog).toHaveTextContent("USER_MANAGEMENT");
    expect(dialog).toHaveTextContent("CREATE");
    await user.click(within(dialog).getByRole("switch"));
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    await vi.waitFor(() => {
      const put = authFetch.mock.calls.find(
        ([, init]) => init?.method === "PUT",
      );
      expect(put?.[0]).toBe(
        "/api/v1/identity/permission-matrix/ADMIN/USER_MANAGEMENT/CREATE",
      );
      expect(JSON.parse(put?.[1].body)).toEqual({ granted: true });
    });
  });

  it("shows the server's refusal inside the confirmation", async () => {
    authFetch.mockImplementation(async (url: string, init?: RequestInit) =>
      init?.method === "PUT"
        ? json(
            {
              reason: "That permission does not apply to this role and module.",
            },
            409,
          )
        : json(MATRIX),
    );
    render(<RolePermissionsGrid />);
    const user = userEvent.setup();
    await screen.findByRole("table");

    await user.click(
      screen.getByRole("button", {
        name: "ADMIN Create USER_MANAGEMENT: not granted",
      }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("switch"));
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "does not apply",
    );
  });

  it("is read-only when the caller cannot edit the matrix", async () => {
    grantedActions = ["VIEW"];
    authFetch.mockResolvedValue(json(MATRIX));
    render(<RolePermissionsGrid />);
    await screen.findByRole("table");

    expect(screen.queryAllByRole("button")).toHaveLength(0);
    expect(
      screen.getByRole("img", { name: "ADMIN Edit USER_MANAGEMENT: granted" }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/click an icon/i)).toBeNull();
  });

  it("shows an error message when the matrix fails to load", async () => {
    authFetch.mockResolvedValueOnce({ ok: false, json: async () => ({}) });

    render(<RolePermissionsGrid />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /could not load the permission matrix/i,
    );
  });
});
