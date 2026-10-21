import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import type { TileDefinition, TileResult } from "../dashboardApi";
import { TileCard } from "../TileCard";

const definition: TileDefinition = {
  key: "managers.tasks.overdue",
  label: "Overdue tasks",
  periodKind: "TODAY",
  zoneAware: true,
  link: { path: "/operations/tasks", query: { view: "overdue" } },
};

function result(overrides: Partial<TileResult>): TileResult {
  return {
    key: definition.key,
    state: "OK",
    values: [],
    organizationWide: false,
    note: null,
    takenAt: "2026-10-21T06:30:12Z",
    table: null,
    ...overrides,
  };
}

function renderCard(tile: TileResult | undefined, onRetry = vi.fn()) {
  render(
    <MemoryRouter>
      <TileCard definition={definition} result={tile} onRetry={onRetry} />
    </MemoryRouter>,
  );
  return onRetry;
}

describe("TileCard (spec 028 US1)", () => {
  it("is a keyboard-reachable link that reads the label and the figure together", async () => {
    renderCard(
      result({
        values: [
          {
            label: "Overdue",
            value: 7,
            unit: "COUNT",
            link: { path: "/operations/tasks", query: { view: "overdue" } },
          },
        ],
      }),
    );

    const link = screen.getByRole("link", {
      name: /overdue tasks.*overdue.*7/i,
    });
    expect(link).toHaveAttribute("href", "/operations/tasks?view=overdue");
    await userEvent.tab();
    expect(link).toHaveFocus();
  });

  it("falls back to the tile's own link when a value has none", () => {
    renderCard(
      result({
        values: [{ label: "Open", value: 3, unit: "COUNT", link: null }],
      }),
    );
    expect(screen.getByRole("link")).toHaveAttribute(
      "href",
      "/operations/tasks?view=overdue",
    );
  });

  it("shows rupees in Indian grouping", () => {
    renderCard(
      result({
        values: [
          { label: "Net pay", value: 125000, unit: "RUPEES", link: null },
        ],
      }),
    );
    expect(screen.getByText("₹1,25,000")).toBeInTheDocument();
  });

  it("labels a tile that cannot be split by Zone as organization-wide", () => {
    renderCard(
      result({
        organizationWide: true,
        values: [{ label: "Open", value: 3, unit: "COUNT", link: null }],
      }),
    );
    expect(screen.getByText(/organization-wide/i)).toBeInTheDocument();
  });

  it("shows no figure, and no zero, for a tile that is not ok", () => {
    renderCard(result({ state: "NO_DATA", note: "No data yet" }));
    expect(screen.getByText("No data yet")).toBeInTheDocument();
    expect(screen.queryByText("0")).not.toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("offers Retry on an error", async () => {
    const onRetry = renderCard(
      result({ state: "ERROR", note: "Could not be read" }),
    );
    expect(screen.getByText("Could not be read")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /retry/i }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it("shows a loading placeholder before the result arrives", () => {
    renderCard(undefined);
    expect(screen.getByText("Overdue tasks")).toBeInTheDocument();
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });
});
