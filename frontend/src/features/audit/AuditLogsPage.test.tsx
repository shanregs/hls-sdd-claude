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

  it("shows the origin of login and activity rows, and leaves change rows blank", async () => {
    authFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        content: [
          {
            occurredAt: "2026-10-04T09:30:00Z",
            type: "LOGIN",
            actorUserId: "u1",
            summary: "Sign-in (password) signed in",
            source: "ANDROID",
            appVersion: "1.0.0",
            location: {
              status: "AVAILABLE",
              latitude: 12.5,
              longitude: 77.25,
              accuracyMeters: 9.2,
              capturedAt: "2026-10-04T09:29:59Z",
            },
            deviceRooted: false,
          },
          {
            occurredAt: "2026-10-04T09:00:00Z",
            type: "CHANGE",
            actorUserId: "u2",
            summary: "MANAGERS.VIEW set to true",
          },
        ],
        page: 0,
        size: 25,
        totalElements: 2,
      }),
    });

    render(<AuditLogsPage />);

    expect(
      await screen.findByText("12.500000, 77.250000 (±9 m)"),
    ).toBeInTheDocument();
    expect(screen.getByText("Android app")).toBeInTheDocument();
    expect(screen.getByText("MANAGERS.VIEW set to true")).toBeInTheDocument();
    expect(screen.getAllByText("Android app")).toHaveLength(1);
  });
});
