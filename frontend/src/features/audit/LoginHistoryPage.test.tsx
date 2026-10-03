import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { LoginHistoryPage } from "./LoginHistoryPage";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

describe("LoginHistoryPage (User Story 1, FR-001/FR-005)", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("lists login history entries returned by the backend", async () => {
    authFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        content: [
          {
            occurredAt: "2026-01-01T10:00:00Z",
            userId: "u1",
            phoneMasked: "98XXXXX001",
            method: "PASSWORD",
            eventType: "SIGN_IN_SUCCESS",
            outcome: "SUCCESS",
          },
        ],
        page: 0,
        size: 25,
        totalElements: 1,
      }),
    });

    render(<LoginHistoryPage />);

    expect(await screen.findAllByText("98XXXXX001")).toHaveLength(1);
  });

  it("shows an empty state (not an error) when no entries match", async () => {
    authFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ content: [], page: 0, size: 25, totalElements: 0 }),
    });

    render(<LoginHistoryPage />);

    expect(await screen.findByText(/no rows/i)).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows an error message when the request fails", async () => {
    authFetch.mockResolvedValueOnce({ ok: false, json: async () => ({}) });

    render(<LoginHistoryPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /could not load login history/i,
    );
  });
});
