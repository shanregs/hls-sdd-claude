import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { AuditLogsPage } from "./AuditLogsPage";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

describe("AuditLogsPage (User Story 4, FR-008/FR-009)", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("renders combined entries from all three sources together", async () => {
    authFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        content: [
          {
            occurredAt: "2026-01-01T10:00:00Z",
            type: "LOGIN",
            actorUserId: "u1",
            summary: "Sign-in (password) success",
          },
          {
            occurredAt: "2026-01-01T09:00:00Z",
            type: "CHANGE",
            actorUserId: "u2",
            summary: "MANAGER.ATTENDANCE.EDIT set to true",
          },
          {
            occurredAt: "2026-01-01T08:00:00Z",
            type: "ACTIVITY",
            actorUserId: "u3",
            summary: "account deactivated",
          },
        ],
        page: 0,
        size: 25,
        totalElements: 3,
      }),
    });

    render(<AuditLogsPage />);

    expect(
      await screen.findByText("Sign-in (password) success"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("MANAGER.ATTENDANCE.EDIT set to true"),
    ).toBeInTheDocument();
    expect(screen.getByText("account deactivated")).toBeInTheDocument();
  });

  it("shows an empty state (not an error) when no entries match", async () => {
    authFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ content: [], page: 0, size: 25, totalElements: 0 }),
    });

    render(<AuditLogsPage />);

    expect(await screen.findByText(/no rows/i)).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows an error message when the request fails", async () => {
    authFetch.mockResolvedValueOnce({ ok: false, json: async () => ({}) });

    render(<AuditLogsPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /could not load audit logs/i,
    );
  });

  it("re-queries with repeated type params when the type filter narrows results", async () => {
    authFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ content: [], page: 0, size: 25, totalElements: 0 }),
    });

    render(<AuditLogsPage />);

    expect(await screen.findByText(/no rows/i)).toBeInTheDocument();
    expect(authFetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/audit/logs?"),
    );
  });
});
