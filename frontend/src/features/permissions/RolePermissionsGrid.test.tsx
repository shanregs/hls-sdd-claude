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

const ALL_ROLES = ["SYSTEM", "ADMIN", "DIRECTOR", "MANAGER", "TEACHER"];

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

/** The data cells of a module's row, in column order: one per action under each role. */
function rowCells(module: string) {
  const row = screen.getByRole("rowheader", { name: module }).closest("tr")!;
  return within(row).getAllByRole("cell");
}

// The fixture's modules use View, Create and Edit, so each role has three sub-columns.
const ACTION_COLUMNS = ["VIEW", "CREATE", "EDIT"];

/** The cell for one role and action of a module. */
function cellFor(module: string, role: string, action: string) {
  const column =
    ALL_ROLES.indexOf(role) * ACTION_COLUMNS.length +
    ACTION_COLUMNS.indexOf(action);
  return rowCells(module)[column];
}

describe("RolePermissionsGrid (User Story 3, FR-002/FR-004a)", () => {
  beforeEach(() => {
    authFetch.mockReset();
    grantedActions = ["VIEW", "EDIT"];
  });

  it("puts System first, then Admin, Director, Manager and Teacher", async () => {
    authFetch.mockResolvedValue(json(MATRIX));

    render(<RolePermissionsGrid />);

    const table = await screen.findByRole("table", {
      name: "Role and permission matrix",
    });
    const groups = within(table)
      .getAllByRole("columnheader")
      .filter((h) => h.getAttribute("scope") === "colgroup")
      .map((h) => h.textContent);
    expect(groups).toEqual([
      "SYSTEM",
      "ADMIN",
      "DIRECTOR",
      "MANAGER",
      "TEACHER",
    ]);
    expect(ALL_ROLES).toEqual(groups);
  });

  it("sizes the Module column to the longest module name plus 5 characters", async () => {
    authFetch.mockResolvedValue(json(MATRIX));

    render(<RolePermissionsGrid />);

    const table = await screen.findByRole("table", {
      name: "Role and permission matrix",
    });
    // The longest fixture module is IDENTITY_PERMISSIONS (20 characters), so 25 characters wide.
    const longest = Math.max(...MATRIX.modules.map((m) => m.module.length));
    expect(longest).toBe(20);
    expect(
      within(table).getByRole("columnheader", { name: "Module" }),
    ).toHaveStyle({ width: "25ch" });
    expect(screen.getByRole("rowheader", { name: "DASHBOARD" })).toHaveStyle({
      width: "25ch",
    });
  });

  it("gives every action its own small cell under each role", async () => {
    authFetch.mockResolvedValue(json(MATRIX));

    render(<RolePermissionsGrid />);

    const table = await screen.findByRole("table", {
      name: "Role and permission matrix",
    });
    // One header per action under each role: 5 roles x 3 actions, each named for its role.
    const subHeaders = within(table)
      .getAllByRole("columnheader")
      .filter(
        (h) =>
          h.getAttribute("scope") === "col" && h.hasAttribute("aria-label"),
      );
    expect(subHeaders).toHaveLength(ALL_ROLES.length * ACTION_COLUMNS.length);
    expect(
      within(table).getByRole("columnheader", { name: "SYSTEM View" }),
    ).toBeInTheDocument();
    expect(
      within(table).getByRole("columnheader", { name: "TEACHER Edit" }),
    ).toBeInTheDocument();
    // A module row has a cell for each of them, and one row per module.
    expect(rowCells("USER_MANAGEMENT")).toHaveLength(
      ALL_ROLES.length * ACTION_COLUMNS.length,
    );
    expect(screen.getAllByRole("row")).toHaveLength(2 + MATRIX.modules.length);
  });

  it("shows a coloured icon for each granted action and a grey one when not granted", async () => {
    authFetch.mockResolvedValue(json(MATRIX));
    render(<RolePermissionsGrid />);
    await screen.findByRole("table");

    // ADMIN on USER_MANAGEMENT: View and Edit granted, Create eligible but not granted, each in its own cell.
    expect(
      within(cellFor("USER_MANAGEMENT", "ADMIN", "VIEW")).getByRole("button", {
        name: "ADMIN View USER_MANAGEMENT: granted",
      }),
    ).toHaveAttribute("aria-pressed", "true");
    expect(
      within(cellFor("USER_MANAGEMENT", "ADMIN", "EDIT")).getByRole("button", {
        name: "ADMIN Edit USER_MANAGEMENT: granted",
      }),
    ).toHaveAttribute("aria-pressed", "true");
    expect(
      within(cellFor("USER_MANAGEMENT", "ADMIN", "CREATE")).getByRole(
        "button",
        { name: "ADMIN Create USER_MANAGEMENT: not granted" },
      ),
    ).toHaveAttribute("aria-pressed", "false");
    // A switched-off grant reads as not granted too; an action with no row at all as well.
    expect(
      within(cellFor("USER_MANAGEMENT", "MANAGER", "VIEW")).getByRole(
        "button",
        {
          name: "MANAGER View USER_MANAGEMENT: not granted",
        },
      ),
    ).toBeInTheDocument();
    expect(
      within(cellFor("USER_MANAGEMENT", "MANAGER", "EDIT")).getByRole(
        "button",
        {
          name: "MANAGER Edit USER_MANAGEMENT: not granted",
        },
      ),
    ).toBeInTheDocument();
  });

  it("colours each granted action differently and keeps not-granted ones grey", async () => {
    authFetch.mockResolvedValue(json(MATRIX));
    render(<RolePermissionsGrid />);
    await screen.findByRole("table");

    const iconColour = (name: string) =>
      getComputedStyle(
        screen.getByRole("button", { name }).querySelector("svg")!,
      ).color;
    const view = iconColour("ADMIN View USER_MANAGEMENT: granted");
    const edit = iconColour("ADMIN Edit USER_MANAGEMENT: granted");
    const create = iconColour("ADMIN Create USER_MANAGEMENT: not granted");

    // View is blue and Edit is orange: two granted actions, two colours.
    expect(view).toBe("rgb(30, 90, 168)");
    expect(edit).toBe("rgb(230, 81, 0)");
    expect(view).not.toBe(edit);
    // A not-granted action is grey whichever action it is, never its own colour.
    expect(create).not.toBe("rgb(46, 125, 50)");
    expect(iconColour("MANAGER View USER_MANAGEMENT: not granted")).toBe(
      create,
    );
  });

  it("leaves a cell empty where an action does not apply", async () => {
    authFetch.mockResolvedValue(json(MATRIX));
    render(<RolePermissionsGrid />);
    await screen.findByRole("table");

    // Teacher cannot hold Role & Permissions at all; nobody can Create there.
    for (const action of ACTION_COLUMNS) {
      expect(
        within(cellFor("IDENTITY_PERMISSIONS", "TEACHER", action)).queryByRole(
          "button",
        ),
      ).toBeNull();
    }
    expect(
      within(cellFor("IDENTITY_PERMISSIONS", "ADMIN", "CREATE")).queryByRole(
        "button",
      ),
    ).toBeNull();
    const adminCells = ACTION_COLUMNS.map((a) =>
      within(cellFor("IDENTITY_PERMISSIONS", "ADMIN", a)).queryByRole("button"),
    );
    expect(adminCells.filter(Boolean)).toHaveLength(2);
    // DASHBOARD offers View only, so its Create and Edit cells are empty for everyone.
    expect(
      within(cellFor("DASHBOARD", "SYSTEM", "EDIT")).queryByRole("button"),
    ).toBeNull();
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
