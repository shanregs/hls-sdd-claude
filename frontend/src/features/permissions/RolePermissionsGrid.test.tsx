import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { RolePermissionsGrid } from "./RolePermissionsGrid";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

describe("RolePermissionsGrid (User Story 3, FR-002/FR-005)", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("lists every matrix entry returned by the backend", async () => {
    authFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        entries: [
          {
            role: "MANAGER",
            module: "DASHBOARD",
            action: "VIEW",
            granted: true,
          },
          {
            role: "MANAGER",
            module: "IDENTITY_PERMISSIONS",
            action: "VIEW",
            granted: false,
          },
        ],
      }),
    });

    render(<RolePermissionsGrid />);

    // MUI X DataGrid can render a row's cells more than once while measuring in jsdom (no real
    // layout), so assert presence via findAllByText rather than requiring a single match.
    expect((await screen.findAllByText("MANAGER")).length).toBeGreaterThan(0);
    expect(
      screen.getAllByRole("button", { name: /^granted$/i }).length,
    ).toBeGreaterThan(0);
    expect(
      screen.getAllByRole("button", { name: /not granted/i }).length,
    ).toBeGreaterThan(0);
  });

  it("shows an error message when the matrix fails to load", async () => {
    authFetch.mockResolvedValueOnce({ ok: false, json: async () => ({}) });

    render(<RolePermissionsGrid />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /could not load the permission matrix/i,
    );
  });
});
