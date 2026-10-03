import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";
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
    json: async () => body,
  } as Response;
}

describe("ProfilePage (User Story 5, FR-015)", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("marks the caller's own device and lists other sessions", async () => {
    authFetch.mockResolvedValueOnce(
      jsonResponse([
        {
          id: "s1",
          deviceDescription: "Chrome on Windows",
          signedInAt: "2026-01-01T00:00:00Z",
          lastActivityAt: "2026-01-01T00:05:00Z",
          current: true,
        },
        {
          id: "s2",
          deviceDescription: "Safari on iPhone",
          signedInAt: "2026-01-01T00:00:00Z",
          lastActivityAt: "2026-01-01T00:05:00Z",
          current: false,
        },
      ]),
    );

    render(<ProfilePage />);

    expect(await screen.findByText("Chrome on Windows")).toBeInTheDocument();
    expect(screen.getByText("Safari on iPhone")).toBeInTheDocument();
    expect(screen.getByText("This device")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /end session/i }),
    ).toBeInTheDocument();
  });

  it("ends another session and refreshes the list", async () => {
    authFetch
      .mockResolvedValueOnce(
        jsonResponse([
          {
            id: "s1",
            deviceDescription: "Chrome on Windows",
            signedInAt: "2026-01-01T00:00:00Z",
            lastActivityAt: "2026-01-01T00:05:00Z",
            current: true,
          },
          {
            id: "s2",
            deviceDescription: "Safari on iPhone",
            signedInAt: "2026-01-01T00:00:00Z",
            lastActivityAt: "2026-01-01T00:05:00Z",
            current: false,
          },
        ]),
      )
      .mockResolvedValueOnce(jsonResponse({}))
      .mockResolvedValueOnce(
        jsonResponse([
          {
            id: "s1",
            deviceDescription: "Chrome on Windows",
            signedInAt: "2026-01-01T00:00:00Z",
            lastActivityAt: "2026-01-01T00:05:00Z",
            current: true,
          },
        ]),
      );

    const user = userEvent.setup();
    render(<ProfilePage />);

    await screen.findByText("Safari on iPhone");
    await user.click(screen.getByRole("button", { name: /end session/i }));

    await waitFor(() =>
      expect(screen.queryByText("Safari on iPhone")).not.toBeInTheDocument(),
    );
    expect(authFetch).toHaveBeenCalledWith("/api/v1/me/sessions/s2", {
      method: "DELETE",
    });
  });
});
