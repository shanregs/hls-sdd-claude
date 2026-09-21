import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PasswordResetPage } from "./PasswordResetPage";

function mockFetchOnce(status: number, body: unknown = {}) {
  vi.stubGlobal(
    "fetch",
    vi.fn(() => Promise.resolve({ ok: status < 400, status, json: () => Promise.resolve(body) } as Response)),
  );
}

describe("PasswordResetPage", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("moves to the confirm step after requesting a reset (FR-016)", async () => {
    mockFetchOnce(202);
    render(<PasswordResetPage />);

    await userEvent.type(screen.getByTestId("identifier-input"), "+919800000001");
    await userEvent.click(screen.getByRole("button", { name: /send reset code/i }));

    expect(await screen.findByTestId("reset-confirm-form")).toBeInTheDocument();
  });

  it("shows the done state after successfully confirming a new password", async () => {
    mockFetchOnce(202);
    render(<PasswordResetPage />);
    await userEvent.type(screen.getByTestId("identifier-input"), "+919800000001");
    await userEvent.click(screen.getByRole("button", { name: /send reset code/i }));
    await screen.findByTestId("reset-confirm-form");

    mockFetchOnce(200);
    await userEvent.type(screen.getByTestId("reset-token-input"), "some-token");
    await userEvent.type(screen.getByTestId("new-password-input"), "new-secure-password");
    await userEvent.click(screen.getByRole("button", { name: /set new password/i }));

    expect(await screen.findByTestId("reset-done")).toBeInTheDocument();
  });

  it("shows an error when the reset token is invalid or expired", async () => {
    mockFetchOnce(202);
    render(<PasswordResetPage />);
    await userEvent.type(screen.getByTestId("identifier-input"), "+919800000001");
    await userEvent.click(screen.getByRole("button", { name: /send reset code/i }));
    await screen.findByTestId("reset-confirm-form");

    mockFetchOnce(401, { message: "invalid" });
    await userEvent.type(screen.getByTestId("reset-token-input"), "bad-token");
    await userEvent.type(screen.getByTestId("new-password-input"), "new-secure-password");
    await userEvent.click(screen.getByRole("button", { name: /set new password/i }));

    expect(await screen.findByTestId("reset-error")).toBeInTheDocument();
  });
});
