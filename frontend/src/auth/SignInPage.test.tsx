import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { SignInPage } from "./SignInPage";

const loginWithPassword = vi.fn();

vi.mock("./useAuth", () => ({
  useAuth: () => ({ loginWithPassword }),
}));

function renderSignInPage() {
  return render(
    <MemoryRouter>
      <SignInPage />
    </MemoryRouter>,
  );
}

describe("SignInPage (User Story 1)", () => {
  it("never renders a role-selection control", () => {
    renderSignInPage();

    expect(screen.queryByRole("radiogroup")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("combobox", { name: /role/i }),
    ).not.toBeInTheDocument();
  });

  it("submits a phone number and password on the Password tab", async () => {
    loginWithPassword.mockResolvedValueOnce(undefined);
    const user = userEvent.setup();
    renderSignInPage();

    await user.type(
      screen.getByLabelText(/phone number or username/i),
      "9876543210",
    );
    await user.type(
      screen.getByLabelText(/^password/i, { selector: "input" }),
      "correct-horse-battery",
    );
    await user.click(screen.getByRole("button", { name: /sign in/i }));

    expect(loginWithPassword).toHaveBeenCalledWith(
      "9876543210",
      "correct-horse-battery",
    );
  });

  it("submits a username and password on the Password tab", async () => {
    loginWithPassword.mockResolvedValueOnce(undefined);
    const user = userEvent.setup();
    renderSignInPage();

    await user.type(
      screen.getByLabelText(/phone number or username/i),
      "priya.manager",
    );
    await user.type(
      screen.getByLabelText(/^password/i, { selector: "input" }),
      "correct-horse-battery",
    );
    await user.click(screen.getByRole("button", { name: /sign in/i }));

    expect(loginWithPassword).toHaveBeenCalledWith(
      "priya.manager",
      "correct-horse-battery",
    );
  });

  it("shows the generic failure message on invalid credentials", async () => {
    loginWithPassword.mockRejectedValueOnce(
      new Error("Your phone number/username or password is incorrect."),
    );
    const user = userEvent.setup();
    renderSignInPage();

    await user.type(
      screen.getByLabelText(/phone number or username/i),
      "9876543210",
    );
    await user.type(
      screen.getByLabelText(/^password/i, { selector: "input" }),
      "wrong-password",
    );
    await user.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Your phone number/username or password is incorrect.",
    );
  });

  it("shows a One-time code tab with a phone-number form, and a Forgot password link", async () => {
    const user = userEvent.setup();
    renderSignInPage();

    expect(
      screen.getByRole("tab", { name: /one-time code/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /forgot password/i }),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: /one-time code/i }));
    expect(
      screen.getByRole("button", { name: /send code/i }),
    ).toBeInTheDocument();
  });
});
