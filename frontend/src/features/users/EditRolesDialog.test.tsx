import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EditRolesDialog } from "./EditRolesDialog";
import type { UserSummary } from "./userManagementApi";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

const USER: UserSummary = {
  id: "u1",
  displayName: "Alice Manager",
  phone: "9800000001",
  username: null,
  email: null,
  roles: ["MANAGER"],
  active: true,
};

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

describe("EditRolesDialog (User Story 3)", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("saves the full new role set and calls onSaved", async () => {
    authFetch.mockResolvedValueOnce(
      jsonResponse({ ...USER, roles: ["MANAGER", "DIRECTOR"] }),
    );
    const onSaved = vi.fn();
    const user = userEvent.setup();

    render(<EditRolesDialog user={USER} onClose={vi.fn()} onSaved={onSaved} />);
    await user.click(screen.getByRole("checkbox", { name: "DIRECTOR" }));
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    expect(authFetch).toHaveBeenCalledWith(
      "/api/v1/identity/users/u1/roles",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify({ roles: ["MANAGER", "DIRECTOR"] }),
      }),
    );
    expect(onSaved).toHaveBeenCalled();
  });

  it("shows the last-admin 409 reason inline and keeps the selection (User Story 6)", async () => {
    authFetch.mockResolvedValueOnce(
      jsonResponse(
        {
          reason:
            "This would leave no active user able to administer the system as Admin.",
        },
        409,
      ),
    );
    const onSaved = vi.fn();
    const user = userEvent.setup();
    const adminUser: UserSummary = { ...USER, roles: ["ADMIN", "MANAGER"] };

    render(
      <EditRolesDialog user={adminUser} onClose={vi.fn()} onSaved={onSaved} />,
    );
    await user.click(screen.getByRole("checkbox", { name: "ADMIN" }));
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "no active user able to administer the system as Admin",
    );
    expect(onSaved).not.toHaveBeenCalled();
    expect(screen.getByRole("checkbox", { name: "ADMIN" })).not.toBeChecked();
    expect(screen.getByRole("checkbox", { name: "MANAGER" })).toBeChecked();
  });

  it("refuses to submit an empty role set", async () => {
    const user = userEvent.setup();

    render(<EditRolesDialog user={USER} onClose={vi.fn()} onSaved={vi.fn()} />);
    await user.click(screen.getByRole("checkbox", { name: "MANAGER" }));
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    expect(await screen.findByText(/at least one role/i)).toBeInTheDocument();
    expect(authFetch).not.toHaveBeenCalled();
  });
});
