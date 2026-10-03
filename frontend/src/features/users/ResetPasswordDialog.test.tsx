import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ResetPasswordDialog } from "./ResetPasswordDialog";
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

describe("ResetPasswordDialog (User Story 5)", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("submits the new password and calls onDone", async () => {
    authFetch.mockResolvedValueOnce(jsonResponse(null, 204));
    const onDone = vi.fn();
    const user = userEvent.setup();

    render(
      <ResetPasswordDialog
        user={USER}
        isSelf={false}
        onClose={vi.fn()}
        onDone={onDone}
      />,
    );
    await user.type(
      screen.getByLabelText(/new password/i),
      "a-brand-new-password",
    );
    await user.click(screen.getByRole("button", { name: /reset password/i }));

    expect(authFetch).toHaveBeenCalledWith(
      "/api/v1/identity/users/u1/reset-password",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ newPassword: "a-brand-new-password" }),
      }),
    );
    expect(onDone).toHaveBeenCalled();
  });

  it("shows the server's password-policy message inline, same as the self-service reset", async () => {
    authFetch.mockResolvedValueOnce(
      jsonResponse(
        {
          reason:
            "Password must be at least 10 characters and not your phone number.",
        },
        400,
      ),
    );
    const user = userEvent.setup();

    render(
      <ResetPasswordDialog
        user={USER}
        isSelf={false}
        onClose={vi.fn()}
        onDone={vi.fn()}
      />,
    );
    await user.type(screen.getByLabelText(/new password/i), "9800000001");
    await user.click(screen.getByRole("button", { name: /reset password/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Password must be at least 10 characters and not your phone number.",
    );
  });

  it("validates the minimum length client-side before calling the API", async () => {
    const user = userEvent.setup();

    render(
      <ResetPasswordDialog
        user={USER}
        isSelf={false}
        onClose={vi.fn()}
        onDone={vi.fn()}
      />,
    );
    await user.type(screen.getByLabelText(/new password/i), "short");
    await user.click(screen.getByRole("button", { name: /reset password/i }));

    expect(
      await screen.findByText(/at least 10 characters/i),
    ).toBeInTheDocument();
    expect(authFetch).not.toHaveBeenCalled();
  });

  it("warns that the session ends when resetting your own password", () => {
    render(
      <ResetPasswordDialog
        user={USER}
        isSelf
        onClose={vi.fn()}
        onDone={vi.fn()}
      />,
    );

    expect(
      screen.getByText(/your current session will end/i),
    ).toBeInTheDocument();
  });
});
