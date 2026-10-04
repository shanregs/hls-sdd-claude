import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SessionsCard } from "./SessionsCard";

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

const CURRENT = {
  id: "s1",
  deviceDescription: "Chrome on Windows",
  signedInAt: "2026-01-01T00:00:00Z",
  lastActivityAt: "2026-01-01T00:05:00Z",
  current: true,
};
const OTHER = {
  id: "s2",
  deviceDescription: "Safari on iPhone",
  signedInAt: "2026-01-01T00:00:00Z",
  lastActivityAt: "2026-01-01T00:05:00Z",
  current: false,
};

describe("SessionsCard (User Story 5, FR-015)", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("marks the caller's own device and lists other sessions", async () => {
    authFetch.mockResolvedValueOnce(jsonResponse([CURRENT, OTHER]));

    render(<SessionsCard />);

    expect(await screen.findByText("Chrome on Windows")).toBeInTheDocument();
    expect(screen.getByText("Safari on iPhone")).toBeInTheDocument();
    expect(screen.getByText("This device")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /end session/i }),
    ).toBeInTheDocument();
    expect(authFetch).toHaveBeenCalledWith("/api/v1/me/sessions");
  });

  it("ends another session and refreshes the list", async () => {
    authFetch
      .mockResolvedValueOnce(jsonResponse([CURRENT, OTHER]))
      .mockResolvedValueOnce(jsonResponse({}))
      .mockResolvedValueOnce(jsonResponse([CURRENT]));

    const user = userEvent.setup();
    render(<SessionsCard />);

    await screen.findByText("Safari on iPhone");
    await user.click(screen.getByRole("button", { name: /end session/i }));

    await waitFor(() =>
      expect(screen.queryByText("Safari on iPhone")).not.toBeInTheDocument(),
    );
    expect(authFetch).toHaveBeenCalledWith("/api/v1/me/sessions/s2", {
      method: "DELETE",
    });
  });

  it("says so when there are no sessions and shows an error when loading fails", async () => {
    authFetch.mockResolvedValueOnce(jsonResponse([]));
    const { unmount } = render(<SessionsCard />);
    expect(await screen.findByText("No active sessions.")).toBeInTheDocument();
    unmount();

    authFetch.mockResolvedValueOnce(jsonResponse({}, false));
    render(<SessionsCard />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load your sessions.",
    );
  });
});
