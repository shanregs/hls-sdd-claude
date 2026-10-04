import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SettingsPage } from "./SettingsPage";

const authFetch = vi.fn();

vi.mock("../auth/useAuth", () => ({
  useAuth: () => ({ authFetch, user: { id: "me", displayName: "Tara" } }),
}));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const PROFILE = {
  id: "u1",
  displayName: "Tara Teacher",
  phone: "9800000004",
  username: null,
  email: null,
  roles: ["TEACHER"],
};

function mockApi(write: Response = json(PROFILE)) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method) return write;
    return json(PROFILE);
  });
}

function lastWrite() {
  const call = authFetch.mock.calls.filter(([, init]) => init?.method).at(-1);
  return call ? { url: String(call[0]), body: JSON.parse(call[1].body) } : null;
}

describe("SettingsPage", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("shows the profile with the phone number read-only", async () => {
    mockApi();
    render(<SettingsPage />);

    expect(await screen.findByLabelText(/^Name/)).toHaveValue("Tara Teacher");
    expect(screen.getByLabelText("Phone number")).toBeDisabled();
    expect(screen.getByText(/Roles: TEACHER/)).toBeInTheDocument();
  });

  it("holds the password form but not the sessions, which have their own page", async () => {
    mockApi();
    render(<SettingsPage />);

    expect(
      await screen.findByRole("heading", { name: "Change password" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("My sessions")).toBeNull();
    expect(authFetch.mock.calls.map((c) => String(c[0]))).not.toContain(
      "/api/v1/me/sessions",
    );
  });

  it("saves an edited name, username and email", async () => {
    mockApi(json({ ...PROFILE, displayName: "Tara T", username: "tara.t" }));
    render(<SettingsPage />);
    const user = userEvent.setup();

    const name = await screen.findByLabelText(/^Name/);
    await user.clear(name);
    await user.type(name, "Tara T");
    await user.type(screen.getByLabelText("Username"), "tara.t");
    await user.click(screen.getByRole("button", { name: "Save profile" }));

    expect(
      await screen.findByText("Your profile was saved."),
    ).toBeInTheDocument();
    expect(lastWrite()).toEqual({
      url: "/api/v1/me/profile",
      body: { displayName: "Tara T", username: "tara.t", email: "" },
    });
  });

  it("validates the profile fields before sending", async () => {
    mockApi();
    render(<SettingsPage />);
    const user = userEvent.setup();

    const name = await screen.findByLabelText(/^Name/);
    await user.clear(name);
    await user.type(screen.getByLabelText("Email"), "nope");
    await user.click(screen.getByRole("button", { name: "Save profile" }));

    expect(
      await screen.findByText("Your name is required."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Enter a valid email address."),
    ).toBeInTheDocument();
    expect(lastWrite()).toBeNull();
  });

  it("shows the server refusal when a username is taken", async () => {
    mockApi(json({ reason: "That username is already taken." }, 409));
    render(<SettingsPage />);
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("Username"), "taken");
    await user.click(screen.getByRole("button", { name: "Save profile" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("already taken");
  });

  it("changes the password when the form is valid", async () => {
    mockApi(json(null, 204));
    render(<SettingsPage />);
    const user = userEvent.setup();

    await user.type(
      await screen.findByLabelText(/Current password/),
      "old-password-1",
    );
    await user.type(screen.getByLabelText(/^New password/), "a-new-password-9");
    await user.type(
      screen.getByLabelText(/Confirm new password/),
      "a-new-password-9",
    );
    await user.click(screen.getByRole("button", { name: "Change password" }));

    expect(
      await screen.findByText(/Your password was changed/),
    ).toBeInTheDocument();
    expect(lastWrite()).toEqual({
      url: "/api/v1/me/password",
      body: {
        currentPassword: "old-password-1",
        newPassword: "a-new-password-9",
      },
    });
  });

  it("refuses a short or mismatched new password without calling the server", async () => {
    mockApi();
    render(<SettingsPage />);
    const user = userEvent.setup();

    await user.type(
      await screen.findByLabelText(/Current password/),
      "old-password-1",
    );
    await user.type(screen.getByLabelText(/^New password/), "short");
    await user.type(screen.getByLabelText(/Confirm new password/), "different");
    await user.click(screen.getByRole("button", { name: "Change password" }));

    expect(
      await screen.findByText(/at least 10 characters/),
    ).toBeInTheDocument();
    expect(screen.getByText("The passwords do not match.")).toBeInTheDocument();
    expect(lastWrite()).toBeNull();
  });

  it("shows the server message for a wrong current password", async () => {
    mockApi(json({ reason: "Your current password is incorrect." }, 400));
    render(<SettingsPage />);
    const user = userEvent.setup();

    await user.type(
      await screen.findByLabelText(/Current password/),
      "wrong-password",
    );
    await user.type(screen.getByLabelText(/^New password/), "a-new-password-9");
    await user.type(
      screen.getByLabelText(/Confirm new password/),
      "a-new-password-9",
    );
    await user.click(screen.getByRole("button", { name: "Change password" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "current password is incorrect",
    );
  });
});
