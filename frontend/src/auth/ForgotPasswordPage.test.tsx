import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ForgotPasswordPage } from "./ForgotPasswordPage";
import { getResetChannels, requestOtp } from "./authApi";

vi.mock("./authApi", async () => {
  const actual = await vi.importActual<typeof import("./authApi")>("./authApi");
  return {
    ...actual,
    getResetChannels: vi.fn(),
    requestOtp: vi.fn(),
  };
});

function renderForgotPasswordPage() {
  return render(
    <MemoryRouter>
      <ForgotPasswordPage />
    </MemoryRouter>,
  );
}

async function goToChannelStep(identifier: string) {
  const user = userEvent.setup();
  renderForgotPasswordPage();
  await user.type(
    screen.getByLabelText(/phone number or username/i),
    identifier,
  );
  await user.click(screen.getByRole("button", { name: /continue/i }));
  return user;
}

describe("ForgotPasswordPage channel choice (User Story 5, FR-016)", () => {
  beforeEach(() => {
    vi.mocked(getResetChannels).mockReset();
    vi.mocked(requestOtp).mockReset();
  });

  it("offers only SMS when the account has no registered email", async () => {
    vi.mocked(getResetChannels).mockResolvedValueOnce(["SMS"]);

    await goToChannelStep("9876543210");

    expect(
      await screen.findByRole("radio", { name: /text message/i }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("radio", { name: /^email$/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("radio", { name: /both sms and email/i }),
    ).not.toBeInTheDocument();
  });

  it("offers SMS, Email, and Both when the account has a registered email", async () => {
    vi.mocked(getResetChannels).mockResolvedValueOnce(["SMS", "EMAIL"]);

    await goToChannelStep("priya.manager");

    expect(
      await screen.findByRole("radio", { name: /text message/i }),
    ).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: /^email$/i })).toBeInTheDocument();
    expect(
      screen.getByRole("radio", { name: /both sms and email/i }),
    ).toBeInTheDocument();
  });
});
