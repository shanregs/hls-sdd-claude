import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SessionsPage } from "./SessionsPage";

const authFetch = vi.fn();

vi.mock("../auth/useAuth", () => ({
  useAuth: () => ({ user: { id: "u1", roles: ["MANAGER"] }, authFetch }),
}));

describe("SessionsPage", () => {
  beforeEach(() => authFetch.mockReset());

  it("lists the caller's sessions under its own heading", async () => {
    authFetch.mockResolvedValue({
      ok: true,
      json: async () => [
        {
          id: "s1",
          deviceDescription: "Chrome on Windows",
          signedInAt: "2026-01-01T00:00:00Z",
          lastActivityAt: "2026-01-01T00:05:00Z",
          current: true,
        },
      ],
    } as Response);

    render(<SessionsPage />);

    expect(
      screen.getByRole("heading", { name: "My sessions", level: 1 }),
    ).toBeInTheDocument();
    expect(await screen.findByText("Chrome on Windows")).toBeInTheDocument();
    expect(screen.getByText("This device")).toBeInTheDocument();
    expect(authFetch).toHaveBeenCalledWith("/api/v1/me/sessions");
  });
});
