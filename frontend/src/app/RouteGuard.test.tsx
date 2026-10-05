import { useEffect } from "react";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { RouteGuard } from "./RouteGuard";
import { isAuthorizedPath } from "../access-model/useAccessModel";

const mockAccessModel = vi.fn();
const fetchSpy = vi.fn();

vi.mock("../access-model/useAccessModel", async () => {
  const actual = await vi.importActual<
    typeof import("../access-model/useAccessModel")
  >("../access-model/useAccessModel");
  return {
    ...actual,
    useAccessModel: () => mockAccessModel(),
  };
});

function GuardedScreen() {
  useEffect(() => {
    fetchSpy();
  }, []);
  return <div>Guarded screen content</div>;
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route
          path="/authorized"
          element={
            <RouteGuard>
              <GuardedScreen />
            </RouteGuard>
          }
        />
        <Route
          path="/forbidden"
          element={
            <RouteGuard>
              <GuardedScreen />
            </RouteGuard>
          }
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe("RouteGuard (User Story 2, FR-010)", () => {
  it("renders the guarded screen when the route is authorized", () => {
    mockAccessModel.mockReturnValue({
      accessModel: {
        roles: ["ADMIN"],
        navigation: [
          {
            section: "Dashboard",
            items: [
              { label: "Dashboard", route: "/authorized", actions: ["VIEW"] },
            ],
          },
        ],
        dataScope: {},
      },
      loading: false,
    });

    renderAt("/authorized");

    expect(screen.getByText("Guarded screen content")).toBeInTheDocument();
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });

  it("renders NotAuthorizedPage and never mounts (or fetches data for) an unauthorized route", () => {
    fetchSpy.mockClear();
    mockAccessModel.mockReturnValue({
      accessModel: {
        roles: ["MANAGER"],
        navigation: [
          {
            section: "Dashboard",
            items: [
              { label: "Dashboard", route: "/authorized", actions: ["VIEW"] },
            ],
          },
        ],
        dataScope: {},
      },
      loading: false,
    });

    renderAt("/forbidden");

    expect(
      screen.queryByText("Guarded screen content"),
    ).not.toBeInTheDocument();
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(screen.getByText(/not authorized/i)).toBeInTheDocument();
  });

  it("renders nothing yet while the access model is still loading, rather than a premature rejection", () => {
    mockAccessModel.mockReturnValue({ accessModel: null, loading: true });

    const { container } = renderAt("/authorized");

    expect(
      screen.queryByText("Guarded screen content"),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/not authorized/i)).not.toBeInTheDocument();
    expect(container).toBeEmptyDOMElement();
  });
});

describe("isAuthorizedPath", () => {
  const routes = new Set([
    "/operations/school-contracts",
    "/recruitment/drives",
    "/dashboard",
  ]);

  it("authorizes an exact route and a detail page below it", () => {
    expect(isAuthorizedPath("/recruitment/drives", routes)).toBe(true);
    expect(isAuthorizedPath("/recruitment/drives/abc", routes)).toBe(true);
    expect(
      isAuthorizedPath("/operations/school-contracts/schools/s1", routes),
    ).toBe(true);
  });

  it("does not authorize a sibling that only shares a prefix, or another menu", () => {
    expect(isAuthorizedPath("/recruitment/drivesX", routes)).toBe(false);
    expect(isAuthorizedPath("/recruitment/offers", routes)).toBe(false);
    expect(isAuthorizedPath("/identity/users", routes)).toBe(false);
  });
});
