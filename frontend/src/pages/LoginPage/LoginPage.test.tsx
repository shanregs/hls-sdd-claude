import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { LoginPage } from "./LoginPage";
import { AuthProvider, useAuth } from "../../auth/AuthContext";

function mockFetchOnce(status: number, body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn(() =>
      Promise.resolve({
        ok: status < 400,
        status,
        json: () => Promise.resolve(body),
      } as Response),
    ),
  );
}

function AccessTokenProbe() {
  const { accessToken } = useAuth();
  return <span data-testid="access-token">{accessToken ?? "none"}</span>;
}

function renderWithAuth() {
  return render(
    <AuthProvider>
      <LoginPage />
      <AccessTokenProbe />
    </AuthProvider>,
  );
}

describe("LoginPage", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("stores an access token after a successful password login (User Story 1, scenario 1)", async () => {
    mockFetchOnce(200, { accessToken: "token-abc", expiresIn: 900 });
    renderWithAuth();

    await userEvent.type(screen.getByTestId("phone-input"), "+919800000001");
    await userEvent.type(screen.getByTestId("password-input"), "correct-password");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    await waitFor(() => expect(screen.getByTestId("access-token")).toHaveTextContent("token-abc"));
  });

  it("shows a generic error on incorrect credentials, never revealing which field was wrong (FR-015, scenario 2)", async () => {
    mockFetchOnce(401, { message: "Incorrect phone number or password." });
    renderWithAuth();

    await userEvent.type(screen.getByTestId("phone-input"), "+919800000001");
    await userEvent.type(screen.getByTestId("password-input"), "wrong-password");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByTestId("login-error")).toHaveTextContent("Incorrect phone number or password.");
    expect(screen.getByTestId("access-token")).toHaveTextContent("none");
  });

  it("prompts for an MFA code instead of issuing tokens when MFA is enabled (scenario 3)", async () => {
    mockFetchOnce(200, { mfaChallengeId: "challenge-1", method: "SMS" });
    renderWithAuth();

    await userEvent.type(screen.getByTestId("phone-input"), "+919800000002");
    await userEvent.type(screen.getByTestId("password-input"), "correct-password");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByTestId("mfa-form")).toBeInTheDocument();
    expect(screen.getByTestId("access-token")).toHaveTextContent("none");
  });

  it("completes login after a correct MFA code", async () => {
    mockFetchOnce(200, { mfaChallengeId: "challenge-1", method: "SMS" });
    renderWithAuth();
    await userEvent.type(screen.getByTestId("phone-input"), "+919800000002");
    await userEvent.type(screen.getByTestId("password-input"), "correct-password");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));
    await screen.findByTestId("mfa-form");

    mockFetchOnce(200, { accessToken: "token-after-mfa", expiresIn: 900 });
    await userEvent.type(screen.getByTestId("mfa-code-input"), "123456");
    await userEvent.click(screen.getByRole("button", { name: /verify/i }));

    await waitFor(() => expect(screen.getByTestId("access-token")).toHaveTextContent("token-after-mfa"));
  });
});
