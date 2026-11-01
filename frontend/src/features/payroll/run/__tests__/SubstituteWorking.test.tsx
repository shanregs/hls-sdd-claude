import { render, screen, within } from "@testing-library/react";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter } from "react-router-dom";
import { axe } from "vitest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { buildMuiTheme } from "../../../../theme/tokens";
import { RunLineWorkingDialog } from "../RunLineWorkingDialog";

const authFetch = vi.fn();

vi.mock("../../../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch, user: { id: "u-admin" } }),
}));

const POLICY = { id: "p-1", effectiveOn: "2000-01-01", rounding: "NEAREST_RUPEE", halfDayFraction: "0.500" };

const SUBSTITUTE_LINE = {
  id: "l-1",
  personKind: "TEACHER",
  personId: "t-1",
  name: "Sita Substitute",
  label: "Substitute Teacher",
  state: "PAYABLE",
  reason: null,
  payableDays: 3,
  workingDays: "2.50",
  unpaidDays: "0.00",
  salary: "800.00",
  earned: "2000.00",
  deduction: "0.00",
  adjustments: "0.00",
  net: "2000.00",
  warning: null,
  computedAt: "2026-11-01T05:00:00Z",
  payment: null,
  adjustmentLines: [],
  working: {
    state: "PAYABLE",
    policy: POLICY,
    salaryRows: [],
    payableDates: ["2026-10-03", "2026-10-04", "2026-10-05"],
    days: [],
    unpaid: [],
    earned: { unrounded: "2000.0000", rounded: "2000.00" },
    deduction: { unrounded: "0", rounded: "0.00" },
    adjustments: [],
    manager: null,
    substituteDays: [
      { dayId: "d-1", school: "Sunrise School", date: "2026-10-03", dayValue: "1.00", rate: "800.00", amount: "800.0000" },
      { dayId: "d-2", school: "Sunrise School", date: "2026-10-04", dayValue: "1.00", rate: "800.00", amount: "800.0000" },
      { dayId: "d-3", school: "Lotus School", date: "2026-10-05", dayValue: "0.50", rate: "800.00", amount: "400.0000" },
    ],
    rateRows: [{ days: "2.50", rate: "800.00" }],
  },
};

function renderDialog(mode: "light" | "dark" = "light") {
  authFetch.mockResolvedValue({ ok: true, status: 200, json: async () => SUBSTITUTE_LINE } as Response);
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <MemoryRouter>
        <RunLineWorkingDialog runId="r1" lineId="l-1" onClose={() => undefined} />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => authFetch.mockReset());

describe("A Substitute Teacher's line working (spec 017b)", () => {
  it("shows the days worked with the rate and no salary or deduction section", async () => {
    renderDialog();
    const dialog = await screen.findByRole("dialog");

    expect(await within(dialog).findByText("Days worked")).toBeInTheDocument();
    expect(within(dialog).getByText("03/10/2026")).toBeInTheDocument();
    expect(within(dialog).getByText("Lotus School")).toBeInTheDocument();
    expect(within(dialog).getByText("2.50 days at ₹800.00")).toBeInTheDocument();
    expect(within(dialog).getAllByText("₹2,000.00", { selector: "dd" }).length).toBeGreaterThanOrEqual(2);
    expect(within(dialog).queryByText("Salary")).not.toBeInTheDocument();
    expect(within(dialog).queryByText("Deduction for days without pay")).not.toBeInTheDocument();
  });

  it.each(["light", "dark"] as const)(
    "has no WCAG 2.2 AA violations in %s mode",
    async (mode) => {
      const options = { runOnly: { type: "tag" as const, values: ["wcag2a", "wcag2aa", "wcag22aa"] } };
      renderDialog(mode);
      await screen.findByText("Days worked");

      expect((await axe(document.body, options)).violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});
