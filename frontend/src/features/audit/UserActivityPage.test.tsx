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

  it("shows where an action came from: source, app version and location, or the reason", async () => {
    authFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        content: [
          {
            occurredAt: "2026-10-04T09:40:00Z",
            actorUserId: "user-1",
            affectedUserId: "user-1",
            action: "PROFILE_UPDATED",
            detail: "display name",
            source: "ANDROID",
            appVersion: "1.1.0",
            location: {
              status: "AVAILABLE",
              latitude: 12.34,
              longitude: 56.78,
              accuracyMeters: 7.4,
              capturedAt: "2026-10-04T09:39:59Z",
            },
          },
          {
            occurredAt: "2026-10-04T09:41:00Z",
            actorUserId: "user-2",
            affectedUserId: "user-2",
            action: "SESSION_ENDED",
            detail: null,
            source: "ANDROID",
            appVersion: "1.1.0",
            location: {
              status: "SERVICES_OFF",
              latitude: null,
              longitude: null,
              accuracyMeters: null,
              capturedAt: null,
            },
          },
        ],
        page: 0,
        size: 25,
        totalElements: 2,
      }),
    });

    render(<UserActivityPage />);

    expect(
      await screen.findByText("12.340000, 56.780000 (±7 m)"),
    ).toBeInTheDocument();
    expect(screen.getAllByText("Android app")).toHaveLength(2);
    expect(
      screen.getByText("Location unavailable — location services off"),
    ).toBeInTheDocument();
  });

  it("has a Source filter", async () => {
    authFetch.mockResolvedValue({
      ok: true,
      json: async () => ({ content: [], page: 0, size: 25, totalElements: 0 }),
    });

    render(<UserActivityPage />);

    expect(
      await screen.findByRole("combobox", { name: "Source" }),
    ).toBeInTheDocument();
  });
});
