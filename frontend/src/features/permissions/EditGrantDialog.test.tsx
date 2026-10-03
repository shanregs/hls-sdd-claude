import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { EditGrantDialog } from "./EditGrantDialog";
import type { MatrixRow } from "./types";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

const ROW: MatrixRow = {
  id: "ADMIN|IDENTITY_PERMISSIONS|EDIT",
  role: "ADMIN",
  module: "IDENTITY_PERMISSIONS",
  action: "EDIT",
  granted: true,
};

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

describe("EditGrantDialog (User Story 3, FR-003/FR-004)", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("saves a toggled grant and calls onSaved on success", async () => {
    authFetch.mockResolvedValueOnce(jsonResponse({ ...ROW, granted: false }));
    const onSaved = vi.fn();
    const user = userEvent.setup();

    render(<EditGrantDialog row={ROW} onClose={vi.fn()} onSaved={onSaved} />);

    await user.click(screen.getByRole("switch", { name: /granted/i }));
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    expect(authFetch).toHaveBeenCalledWith(
      "/api/v1/identity/permission-matrix/ADMIN/IDENTITY_PERMISSIONS/EDIT",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify({ granted: false }),
      }),
    );
    expect(onSaved).toHaveBeenCalled();
  });

  it("shows the backend's 409 rejection reason inline instead of closing", async () => {
    authFetch.mockResolvedValueOnce(
      jsonResponse(
        {
          reason:
            "This would leave no one able to manage the permission matrix.",
        },
        409,
      ),
    );
    const onSaved = vi.fn();
    const user = userEvent.setup();

    render(<EditGrantDialog row={ROW} onClose={vi.fn()} onSaved={onSaved} />);

    await user.click(screen.getByRole("switch", { name: /granted/i }));
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    expect(
      await screen.findByText(
        /would leave no one able to manage the permission matrix/i,
      ),
    ).toBeInTheDocument();
    expect(onSaved).not.toHaveBeenCalled();
  });
});
