import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { UserActivityPage } from "./UserActivityPage";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

describe("UserActivityPage (User Story 3, FR-003/FR-007)", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("renders a readable label for each action type", async () => {
    authFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        content: [
          {
            occurredAt: "2026-01-01T10:20:00Z",
            actorUserId: null,
            affectedUserId: "user-1",
            action: "ACCOUNT_DEACTIVATED",
            detail: null,
          },
        ],
        page: 0,
        size: 25,
        totalElements: 1,
      }),
    });

    render(<UserActivityPage />);

    expect(await screen.findAllByText("Account deactivated")).toHaveLength(1);
  });

  it("shows an error message when the request fails", async () => {
    authFetch.mockResolvedValueOnce({ ok: false, json: async () => ({}) });

    render(<UserActivityPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /could not load user activity/i,
    );
  });
});
