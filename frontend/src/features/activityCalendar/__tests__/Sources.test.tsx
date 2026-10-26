import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { access, FILTER_CHOICES, grant, renderPage, response, route, SOURCES_OK } from "./testKit";

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
  it("offers Training as a normal kind now that training sessions are read", async () => {
    route(authFetch, {});
    renderPage();
    const option = await screen.findByRole("option", { name: "Training" });
    expect(option).toBeEnabled();
    expect(screen.queryByText(/not included yet/)).not.toBeInTheDocument();
  });

  it("still says so when a source reports it is unavailable", async () => {
    route(authFetch, {
      calendar: () =>
        response([], {
          sources: SOURCES_OK.map((s) =>
            s.group === "TRAINING"
              ? { ...s, available: false, read: false, note: "Training is not included yet" }
              : s,
          ),
        }),
      filters: {
        ...FILTER_CHOICES,
        kinds: FILTER_CHOICES.kinds.map((k) =>
          k.kind === "TRAINING" ? { ...k, available: false } : k,
        ),
      },
    });
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
