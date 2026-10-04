import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { UserManagementPage } from "./UserManagementPage";
import type { UserSummary } from "./userManagementApi";

const authFetch = vi.fn();
let grantedActions = ["VIEW", "CREATE", "EDIT"];

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
          section: "SYSTEM",
          items: [
            {
              label: "User Management",
              route: "/identity/users",
              actions: grantedActions,
            },
          ],
        },
      ],
    },
  }),
}));

const ALICE: UserSummary = {
  id: "u1",
  displayName: "Alice Manager",
  phone: "9800000001",
  username: null,
  email: null,
  roles: ["MANAGER"],
  active: true,
};
const BOB: UserSummary = {
  id: "u2",
  displayName: "Bob Teacher",
  phone: "9800000002",
  username: null,
  email: null,
  roles: ["TEACHER"],
  active: false,
};
const ME: UserSummary = {
  id: "me",
  displayName: "Me Admin",
  phone: "9800000003",
  username: null,
  email: null,
  roles: ["ADMIN"],
  active: true,
};

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function page(content: UserSummary[]) {
  return jsonResponse({
    content,
    page: 0,
    size: 25,
    totalElements: content.length,
  });
}

function urlOf(call: unknown[]): string {
  return String(call[0]);
}

/** List GETs return `content`; any write (has a method) returns `writeResponse`. */
function mockApi(content: UserSummary[], writeResponse: Response) {
  authFetch.mockImplementation(async (_url: string, init?: RequestInit) =>
    init?.method ? writeResponse : page(content),
  );
}

function wasCalledWith(url: string): boolean {
  return authFetch.mock.calls.some((c) => urlOf(c) === url);
}

describe("UserManagementPage", () => {
  beforeEach(() => {
    authFetch.mockReset();
    grantedActions = ["VIEW", "CREATE", "EDIT"];
  });

  it("lists users with roles and active/inactive status (User Story 2)", async () => {
    authFetch.mockResolvedValue(page([ALICE, BOB]));

    render(<UserManagementPage />);

    expect(
      (await screen.findAllByText("Alice Manager")).length,
    ).toBeGreaterThan(0);
    expect(screen.getAllByText("MANAGER").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Active").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Inactive").length).toBeGreaterThan(0);
  });

  it("searches by text and shows an empty state, not an error, when nothing matches", async () => {
    authFetch.mockResolvedValueOnce(page([ALICE]));
    authFetch.mockResolvedValue(page([]));
    const user = userEvent.setup();

    render(<UserManagementPage />);
    await screen.findAllByText("Alice Manager");
    await user.type(screen.getByLabelText(/search by name or phone/i), "zzz");

    expect(
      await screen.findByText(/no users match your search/i),
    ).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    const lastUrl = urlOf(authFetch.mock.calls.at(-1)!);
    expect(lastUrl).toContain("query=zzz");
  });

  it("shows an error alert when the list fails to load", async () => {
    authFetch.mockResolvedValue(jsonResponse({}, 500));

    render(<UserManagementPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /could not load users/i,
    );
  });

  it("creates a user and reloads the list (User Story 1)", async () => {
    let created = false;
    authFetch.mockImplementation(async (_url: string, init?: RequestInit) => {
      if (init?.method === "POST") {
        created = true;
        return jsonResponse(ALICE, 201);
      }
      return page(created ? [ALICE] : []);
    });
    const user = userEvent.setup();

    render(<UserManagementPage />);
    await user.click(
      await screen.findByRole("button", { name: /create user/i }),
    );
    await user.type(screen.getByLabelText(/display name/i), "Alice Manager");
    await user.type(screen.getByLabelText(/phone number/i), "9800000001");
    await user.click(screen.getByRole("checkbox", { name: "MANAGER" }));
    await user.click(screen.getByRole("button", { name: /^create$/i }));

    expect(
      (await screen.findAllByText("Alice Manager")).length,
    ).toBeGreaterThan(0);
    const postCall = authFetch.mock.calls.find(
      (c) => (c[1] as RequestInit | undefined)?.method === "POST",
    )!;
    expect(urlOf(postCall)).toBe("/api/v1/identity/users");
    expect(
      JSON.parse((postCall[1] as RequestInit).body as string),
    ).toMatchObject({
      displayName: "Alice Manager",
      phone: "9800000001",
      roles: ["MANAGER"],
    });
  });

  it("shows a duplicate-phone error from the API inline and keeps the dialog open", async () => {
    mockApi(
      [],
      jsonResponse(
        { reason: "A user with phone 9800000001 already exists." },
        409,
      ),
    );
    const user = userEvent.setup();

    render(<UserManagementPage />);
    await user.click(
      await screen.findByRole("button", { name: /create user/i }),
    );
    await user.type(screen.getByLabelText(/display name/i), "Dup");
    await user.type(screen.getByLabelText(/phone number/i), "9800000001");
    await user.click(screen.getByRole("checkbox", { name: "TEACHER" }));
    await user.click(screen.getByRole("button", { name: /^create$/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "A user with phone 9800000001 already exists.",
    );
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("requires at least one role before submitting the create form", async () => {
    authFetch.mockResolvedValue(page([]));
    const user = userEvent.setup();

    render(<UserManagementPage />);
    await user.click(
      await screen.findByRole("button", { name: /create user/i }),
    );
    await user.type(screen.getByLabelText(/display name/i), "No Role");
    await user.type(screen.getByLabelText(/phone number/i), "9800000009");
    await user.click(screen.getByRole("button", { name: /^create$/i }));

    expect(
      await screen.findByText(/choose at least one role/i),
    ).toBeInTheDocument();
    expect(
      authFetch.mock.calls.some(
        (c) => (c[1] as RequestInit | undefined)?.method === "POST",
      ),
    ).toBe(false);
  });

  it("deactivates an active user and reactivates an inactive one (User Story 4)", async () => {
    mockApi([ALICE, BOB], jsonResponse(null, 204));
    const user = userEvent.setup();

    render(<UserManagementPage />);
    await screen.findAllByText("Alice Manager");

    await user.click(screen.getAllByRole("button", { name: "Deactivate" })[0]);
    expect(wasCalledWith("/api/v1/identity/users/u1/deactivate")).toBe(true);

    const reactivate = (
      await screen.findAllByRole("button", { name: "Reactivate" })
    )[0];
    await user.click(reactivate);
    expect(wasCalledWith("/api/v1/identity/users/u2/reactivate")).toBe(true);
  });

  it("shows the last-admin rejection message inline when deactivation returns 409 (User Story 6)", async () => {
    mockApi(
      [ALICE],
      jsonResponse(
        {
          reason:
            "This would leave no active user able to administer the system as Admin.",
        },
        409,
      ),
    );
    const user = userEvent.setup();

    render(<UserManagementPage />);
    await screen.findAllByText("Alice Manager");
    await user.click(screen.getAllByRole("button", { name: "Deactivate" })[0]);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "no active user able to administer the system as Admin",
    );
  });

  it("asks for confirmation before deactivating your own account", async () => {
    mockApi([ME], jsonResponse(null, 204));
    const user = userEvent.setup();

    render(<UserManagementPage />);
    await screen.findAllByText("Me Admin");
    await user.click(screen.getAllByRole("button", { name: "Deactivate" })[0]);

    const dialog = await screen.findByRole("dialog");
    expect(
      within(dialog).getByText(/your current session will end/i),
    ).toBeInTheDocument();
    expect(wasCalledWith("/api/v1/identity/users/me/deactivate")).toBe(false);
    await user.click(
      within(dialog).getByRole("button", { name: "Deactivate" }),
    );
    expect(wasCalledWith("/api/v1/identity/users/me/deactivate")).toBe(true);
  });

  it("hides every write action when only VIEW is granted (FR-012)", async () => {
    grantedActions = ["VIEW"];
    authFetch.mockResolvedValue(page([ALICE]));

    render(<UserManagementPage />);
    await screen.findAllByText("Alice Manager");

    expect(
      screen.queryByRole("button", { name: /create user/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /^Edit roles of / }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Deactivate" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /reset password/i }),
    ).not.toBeInTheDocument();
  });

  it("shows Create but not row actions when CREATE is granted without EDIT (FR-012)", async () => {
    grantedActions = ["VIEW", "CREATE"];
    authFetch.mockResolvedValue(page([ALICE]));

    render(<UserManagementPage />);
    await screen.findAllByText("Alice Manager");

    expect(
      screen.getByRole("button", { name: /create user/i }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Deactivate" }),
    ).not.toBeInTheDocument();
  });
});
