import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProfilePage } from "./ProfilePage";

const authFetch = vi.fn();

vi.mock("../auth/useAuth", () => ({
  useAuth: () => ({
    user: { id: "u1", displayName: "Priya Manager", roles: ["MANAGER"] },
    authFetch,
  }),
}));

function jsonResponse(body: unknown, ok = true) {
  return {
    ok,
    status: ok ? 200 : 500,
    json: async () => body,
  } as Response;
}

const PROFILE = {
  id: "u1",
  displayName: "Priya Manager",
  phone: "9800000003",
  username: "priya.m",
  email: null,
  roles: ["MANAGER"],
};

function renderPage() {
  return render(
    <MemoryRouter>
      <ProfilePage />
    </MemoryRouter>,
  );
}

describe("ProfilePage: the caller's own account details", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("shows the account details from the profile API, read-only", async () => {
    authFetch.mockResolvedValue(jsonResponse(PROFILE));

    renderPage();

    expect(
      await screen.findByRole("heading", { name: "My profile" }),
    ).toBeInTheDocument();
    expect(await screen.findByText("Priya Manager")).toBeInTheDocument();
    expect(screen.getByText("9800000003")).toBeInTheDocument();
    expect(screen.getByText("priya.m")).toBeInTheDocument();
    expect(screen.getByText("MANAGER")).toBeInTheDocument();
    // An email that was never set reads "Not set", not blank.
    expect(screen.getByText("Not set")).toBeInTheDocument();
    expect(screen.queryByRole("textbox")).toBeNull();
    expect(authFetch).toHaveBeenCalledWith("/api/v1/me/profile");
  });

  it("does not load or show sessions here; they are on Settings", async () => {
    authFetch.mockResolvedValue(jsonResponse(PROFILE));

    renderPage();
    await screen.findByText("Priya Manager");

    expect(authFetch.mock.calls.map((c) => String(c[0]))).not.toContain(
      "/api/v1/me/sessions",
    );
    expect(screen.queryByText("My sessions")).toBeNull();
    expect(screen.getByRole("link", { name: "Settings" })).toHaveAttribute(
      "href",
      "/account/settings",
    );
    expect(screen.getByRole("link", { name: "Sessions" })).toHaveAttribute(
      "href",
      "/account/sessions",
    );
  });

  it("shows an error when the profile cannot be loaded", async () => {
    authFetch.mockResolvedValue(jsonResponse({}, false));

    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load your profile.",
    );
  });
});
