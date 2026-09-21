import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { TeacherOtpLoginPage } from "./TeacherOtpLoginPage";
import { AuthProvider, useAuth } from "../../auth/AuthContext";

function mockFetchOnce(status: number, body: unknown = {}) {
  vi.stubGlobal(
    "fetch",
    vi.fn(() => Promise.resolve({ ok: status < 400, status, json: () => Promise.resolve(body) } as Response)),
  );
}

function AccessTokenProbe() {
  const { accessToken } = useAuth();
  return <span data-testid="access-token">{accessToken ?? "none"}</span>;
}

function renderWithAuth() {
  return render(
    <AuthProvider>
      <TeacherOtpLoginPage />
      <AccessTokenProbe />
    </AuthProvider>,
  );
}

describe("TeacherOtpLoginPage", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("moves to the verify step after requesting an OTP (scenario 1)", async () => {
    mockFetchOnce(202);
    renderWithAuth();

    await userEvent.type(screen.getByTestId("otp-phone-input"), "+919800000099");
    await userEvent.click(screen.getByRole("button", { name: /send login code/i }));

    expect(await screen.findByTestId("otp-verify-form")).toBeInTheDocument();
  });

  it("issues tokens after a correct OTP (scenario 2)", async () => {
    mockFetchOnce(202);
    renderWithAuth();
    await userEvent.type(screen.getByTestId("otp-phone-input"), "+919800000099");
    await userEvent.click(screen.getByRole("button", { name: /send login code/i }));
    await screen.findByTestId("otp-verify-form");

    mockFetchOnce(200, { accessToken: "teacher-token", expiresIn: 900 });
    await userEvent.type(screen.getByTestId("otp-code-input"), "482913");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    await waitFor(() => expect(screen.getByTestId("access-token")).toHaveTextContent("teacher-token"));
  });

  it("shows a retry-able error on an expired/incorrect OTP (scenario 3)", async () => {
    mockFetchOnce(202);
    renderWithAuth();
    await userEvent.type(screen.getByTestId("otp-phone-input"), "+919800000099");
    await userEvent.click(screen.getByRole("button", { name: /send login code/i }));
    await screen.findByTestId("otp-verify-form");

    mockFetchOnce(401, { message: "invalid" });
    await userEvent.type(screen.getByTestId("otp-code-input"), "000000");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByTestId("otp-error")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /request a new code/i })).toBeInTheDocument();
  });
});
