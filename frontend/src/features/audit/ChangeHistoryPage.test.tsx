import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ChangeHistoryPage } from "./ChangeHistoryPage";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

describe("ChangeHistoryPage (User Story 2, FR-002/FR-006)", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("shows the prior and new value as distinct columns", async () => {
    authFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        content: [
          {
            occurredAt: "2026-01-01T10:15:00Z",
            actorUserId: "admin-1",
            entityType: "PERMISSION_MATRIX",
            entityId: "MANAGER.ATTENDANCE.EDIT",
            field: "granted",
            beforeValue: "false",
            afterValue: "true",
          },
        ],
        page: 0,
        size: 25,
        totalElements: 1,
      }),
    });

    render(<ChangeHistoryPage />);

    expect(await screen.findAllByText("MANAGER.ATTENDANCE.EDIT")).toHaveLength(
      1,
    );
    expect(screen.getAllByText("false").length).toBeGreaterThan(0);
    expect(screen.getAllByText("true").length).toBeGreaterThan(0);
  });

  it("shows an error message when the request fails", async () => {
    authFetch.mockResolvedValueOnce({ ok: false, json: async () => ({}) });

    render(<ChangeHistoryPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /could not load change history/i,
    );
  });
});
