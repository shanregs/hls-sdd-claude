import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { access, grant, renderPage, response, route, COUNTS } from "./testKit";

const authFetch = vi.fn();

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

vi.mock("../../../access-model/useAccessModel", async () => {
  const actual = await vi.importActual<
    typeof import("../../../access-model/useAccessModel")
  >("../../../access-model/useAccessModel");
  return {
    ...actual,
    useAccessModel: () => ({
      loading: false,
      accessModel: {
        roles: access.roles,
        dataScope: {},
        navigation: access.navigation,
      },
    }),
  };
});

beforeEach(() => {
  authFetch.mockReset();
  window.sessionStorage.clear();
});

describe("Activity Calendar Manager view (spec 035 US2)", () => {
  it("hides the Manager picker, shows own counts and no per-Manager table", async () => {
    grant("MANAGER", "My Activity Calendar");
    route(authFetch, {
      calendar: () =>
        response([], {
          summary: { ...COUNTS, planned: 2, done: 1, missed: 1, byKind: {} },
        }),
      filters: {
        managers: [{ id: "u1", name: "Meera Manager" }],
        zones: [{ id: "z1", name: "North" }],
        kinds: [],
        statuses: [],
      },
    });
    renderPage();

    expect(
      await screen.findByRole("heading", { name: "My Activity Calendar", level: 1 }),
    ).toBeInTheDocument();
    expect(await screen.findByText("Planned 2")).toBeInTheDocument();
    expect(screen.getByText("Missed 1")).toBeInTheDocument();
    expect(screen.queryByLabelText("Manager")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Zone")).toBeInTheDocument();
    expect(screen.queryByText(/per Manager|By Manager/i)).not.toBeInTheDocument();
  });

  it("shows the Manager picker to the Director", async () => {
    grant("DIRECTOR", "Activity Calendar");
    route(authFetch, {});
    renderPage();
    expect(await screen.findByLabelText("Manager")).toBeInTheDocument();
  });
});
