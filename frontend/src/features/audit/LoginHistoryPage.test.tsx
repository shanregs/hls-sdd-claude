import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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

  describe("origin of the sign-in (spec 018)", () => {
    const androidRow = {
      occurredAt: "2026-10-04T09:30:00Z",
      userId: "u1",
      phoneMasked: "98XXXXX002",
      method: "PASSWORD",
      eventType: "SIGN_IN_SUCCESS",
      outcome: "Signed in",
      source: "ANDROID",
      appVersion: "1.0.0",
      location: {
        status: "AVAILABLE",
        latitude: 12.971599,
        longitude: 77.594566,
        accuracyMeters: 18.5,
        capturedAt: "2026-10-04T09:29:58Z",
      },
      deviceRooted: true,
    };

    it("shows source, app version, location with accuracy, and the rooted flag", async () => {
      authFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          content: [androidRow],
          page: 0,
          size: 25,
          totalElements: 1,
        }),
      });

      render(<LoginHistoryPage />);

      expect(await screen.findByText("Android app")).toBeInTheDocument();
      expect(screen.getByText("1.0.0")).toBeInTheDocument();
      expect(
        screen.getByText("12.971599, 77.594566 (±19 m)"),
      ).toBeInTheDocument();
      expect(screen.getByText("Rooted device suspected")).toBeInTheDocument();
    });

    it("shows the reason when there is no location, and nothing for a web sign-in", async () => {
      authFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          content: [
            {
              ...androidRow,
              phoneMasked: "98XXXXX003",
              deviceRooted: false,
              location: {
                status: "PERMISSION_DENIED",
                latitude: null,
                longitude: null,
                accuracyMeters: null,
                capturedAt: null,
              },
            },
            {
              ...androidRow,
              phoneMasked: "98XXXXX004",
              source: "WEB",
              appVersion: null,
              deviceRooted: false,
              location: {
                status: "NOT_APPLICABLE",
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

      render(<LoginHistoryPage />);

      expect(
        await screen.findByText("Location unavailable — permission denied"),
      ).toBeInTheDocument();
      expect(screen.getByText("Web")).toBeInTheDocument();
      expect(
        screen.queryByText("Rooted device suspected"),
      ).not.toBeInTheDocument();
    });

    it("filters by source and sends it to the server", async () => {
      authFetch.mockResolvedValue({
        ok: true,
        json: async () => ({
          content: [],
          page: 0,
          size: 25,
          totalElements: 0,
        }),
      });
      const user = userEvent.setup();

      render(<LoginHistoryPage />);
      await screen.findByText(/no rows/i);
      await user.click(screen.getByRole("combobox", { name: "Source" }));
      await user.click(
        await screen.findByRole("option", { name: "Android app" }),
      );

      await waitFor(() =>
        expect(
          authFetch.mock.calls.some(([url]) =>
            String(url).includes("source=ANDROID"),
          ),
        ).toBe(true),
      );
    });
  });
});
