import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { axe } from "vitest-axe";
import { ThemeProvider } from "@mui/material/styles";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { buildMuiTheme, type ThemeMode } from "../../theme/tokens";
import { ApiAccessPage } from "./ApiAccessPage";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

const rows = [
  {
    id: "a1",
    occurredAt: "2026-10-04T09:30:12Z",
    userId: "user-1",
    sessionId: "s-1",
    httpMethod: "GET",
    routeTemplate: "/api/v1/me/access-model",
    statusCode: 200,
    source: "ANDROID",
    appVersion: "1.0.0",
    location: {
      status: "AVAILABLE",
      latitude: 12.971599,
      longitude: 77.594566,
      accuracyMeters: 18.5,
      capturedAt: "2026-10-04T09:30:10Z",
    },
  },
  {
    id: "a2",
    occurredAt: "2026-10-04T09:31:00Z",
    userId: null,
    sessionId: null,
    httpMethod: "POST",
    routeTemplate: "/api/v1/auth/login",
    statusCode: 401,
    source: "ANDROID",
    appVersion: "1.0.0",
    location: {
      status: "NO_FIX",
      latitude: null,
      longitude: null,
      accuracyMeters: null,
      capturedAt: null,
    },
  },
];

function renderPage(mode: ThemeMode = "light") {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <ApiAccessPage />
    </ThemeProvider>,
  );
}

describe("ApiAccessPage (spec 018, FR-023a, FR-028)", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("lists each request with method, route, status, source and location", async () => {
    authFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        content: rows,
        page: 0,
        size: 25,
        totalElements: 2,
      }),
    });

    renderPage();

    expect(
      await screen.findByText("/api/v1/me/access-model"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("12.971599, 77.594566 (±19 m)"),
    ).toBeInTheDocument();
    expect(screen.getByText("/api/v1/auth/login")).toBeInTheDocument();
    expect(screen.getByText("401")).toBeInTheDocument();
    expect(
      screen.getByText("Location unavailable — no position found in time"),
    ).toBeInTheDocument();
    expect(screen.getAllByText("Android app")).toHaveLength(2);
  });

  it("shows an empty state, not an error, when nothing matches", async () => {
    authFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ content: [], page: 0, size: 25, totalElements: 0 }),
    });

    renderPage();

    expect(await screen.findByText(/no rows/i)).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows an error message when the request fails", async () => {
    authFetch.mockResolvedValueOnce({ ok: false, json: async () => ({}) });

    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /could not load api access/i,
    );
  });

  it("sends the method and location-status filters to the server", async () => {
    authFetch.mockResolvedValue({
      ok: true,
      json: async () => ({ content: [], page: 0, size: 25, totalElements: 0 }),
    });
    const user = userEvent.setup();

    renderPage();
    await screen.findByText(/no rows/i);
    await user.click(screen.getByRole("combobox", { name: "Method" }));
    await user.click(await screen.findByRole("option", { name: "POST" }));
    await user.click(screen.getByRole("combobox", { name: "Location" }));
    await user.click(
      await screen.findByRole("option", { name: "No position found" }),
    );

    await waitFor(() =>
      expect(
        authFetch.mock.calls.some(
          ([url]) =>
            String(url).includes("httpMethod=POST") &&
            String(url).includes("locationStatus=NO_FIX"),
        ),
      ).toBe(true),
    );
  });

  it.each(["light", "dark"] as const)(
    "has no accessibility violations in %s mode",
    async (mode) => {
      authFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          content: rows,
          page: 0,
          size: 25,
          totalElements: 2,
        }),
      });

      const { container } = renderPage(mode);
      await screen.findByText("/api/v1/me/access-model");

      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      // vitest-axe's matcher typings do not match this project's Vitest, so assert directly.
      const summary = results.violations.map(
        (v) => `${v.id}: ${v.help} (${v.nodes.length} node(s))`,
      );
      expect(summary).toEqual([]);
    },
    30_000,
  );
});
