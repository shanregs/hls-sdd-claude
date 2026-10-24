import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { access, grant, renderPage, response, route, SOURCES_OK } from "./testKit";

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
  grant("DIRECTOR", "Activity Calendar");
});

describe("Activity Calendar sources (spec 035 US5)", () => {
  it("says Training is not available yet in the kind filter and the banner", async () => {
    route(authFetch, {});
    renderPage();
    expect(await screen.findByText("Training is not included yet")).toBeInTheDocument();
    const option = await screen.findByRole("option", { name: "Training: not available yet" });
    expect(option).toBeDisabled();
  });

  it("names a failed kind in a banner and marks the counts partial", async () => {
    route(authFetch, {
      calendar: () =>
        response([], {
          partial: true,
          sources: SOURCES_OK.map((s) =>
            s.group === "TASKS"
              ? { ...s, read: false, note: "Tasks could not be loaded" }
              : s,
          ),
        }),
    });
    renderPage();
    expect(await screen.findByText("Tasks could not be loaded")).toBeInTheDocument();
    expect(screen.getByText(/Counts are partial/)).toBeInTheDocument();
  });
});
