import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import type { TileTable, TileValue } from "../dashboardApi";
import { ManagerComparisonTable } from "../ManagerComparisonTable";

function count(label: string, value: number, id: string): TileValue {
  return {
    label,
    value,
    unit: "COUNT",
    link: { path: "/operations/tasks", query: { view: "overdue", assigneeUserId: id } },
  };
}

const table: TileTable = {
  columns: ["Manager", "Overdue"],
  rows: [
    [{ label: "Manager", value: "Asha", unit: "TEXT", link: null }, count("Overdue", 1, "a")],
    [{ label: "Manager", value: "Bala", unit: "TEXT", link: null }, count("Overdue", 12, "b")],
  ],
};

function names() {
  return screen
    .getAllByRole("row")
    .slice(1)
    .map((r) => within(r).getAllByRole("cell")[0].textContent);
}

describe("ManagerComparisonTable (spec 028 US2)", () => {
  it("lists one row per Manager and links each figure to that Manager's list", () => {
    render(
      <MemoryRouter>
        <ManagerComparisonTable table={table} />
      </MemoryRouter>,
    );
    expect(names()).toEqual(["Asha", "Bala"]);
    const link = screen.getByRole("link", { name: "Bala: Overdue 12" });
    expect(link).toHaveAttribute("href", "/operations/tasks?view=overdue&assigneeUserId=b");
  });

  it("sorts by a column from the keyboard, descending first for figures", async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <ManagerComparisonTable table={table} />
      </MemoryRouter>,
    );
    await user.tab();
    await user.tab();
    await user.keyboard("{Enter}");
    expect(names()).toEqual(["Bala", "Asha"]);
    await user.keyboard("{Enter}");
    expect(names()).toEqual(["Asha", "Bala"]);
  });
});
