import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter } from "react-router-dom";
import { axe } from "vitest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { buildMuiTheme } from "../../../theme/tokens";
import { DayRatesTab } from "../DayRatesTab";

const authFetch = vi.fn();

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const LIST = [
  { designationId: "d-1", name: "Cover Teacher", retired: false, currentAmount: "800.00", currentEffectiveOn: "2026-09-01" },
  { designationId: "d-2", name: "Senior Cover Teacher", retired: false, currentAmount: null, currentEffectiveOn: null },
];

const HISTORY = [
  { id: "r-2", amount: "800.00", effectiveOn: "2026-09-01", note: "Revised", recordedBy: "u-1", recordedAt: "2026-09-01T05:00:00Z" },
  { id: "r-1", amount: "700.00", effectiveOn: "2026-01-01", note: null, recordedBy: "u-1", recordedAt: "2026-01-01T05:00:00Z" },
];

function mockApi(refuse?: string) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    const path = String(url);
    if (init?.method === "POST") {
      return refuse ? json({ reason: refuse }, 400) : json(HISTORY[0], 201);
    }
    if (/day-rates\/d-1$/.test(path)) return json(HISTORY);
    return json(LIST);
  });
}

function renderTab(mode: "light" | "dark" = "light") {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <MemoryRouter>
        <DayRatesTab />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  authFetch.mockReset();
  mockApi();
});

describe("Substitute day rates", () => {
  it("lists designations with the current rate and says when there is none", async () => {
    renderTab();

    expect(await screen.findByText("Cover Teacher")).toBeInTheDocument();
    expect(screen.getByText("₹800.00")).toBeInTheDocument();
    expect(screen.getByText("01/09/2026")).toBeInTheDocument();
    expect(screen.getByText("No rate yet")).toBeInTheDocument();
  });

  it("keeps Save disabled until there is a positive amount, then posts the rate", async () => {
    renderTab();
    await userEvent.click(await screen.findByRole("button", { name: "Set day rate for Senior Cover Teacher" }));
    const dialog = await screen.findByRole("dialog");
    const save = within(dialog).getByRole("button", { name: "Save rate" });
    expect(save).toBeDisabled();

    await userEvent.type(within(dialog).getByLabelText(/Rate per full day/), "950");
    expect(save).toBeEnabled();
    await userEvent.click(save);

    await waitFor(() =>
      expect(authFetch).toHaveBeenCalledWith(
        expect.stringContaining("/designation-day-rates/d-2"),
        expect.objectContaining({ method: "POST" }),
      ),
    );
    const body = JSON.parse(String(authFetch.mock.calls.find(([, init]) => init?.method === "POST")?.[1]?.body)) as {
      amount: number;
    };
    expect(body.amount).toBe(950);
  });

  it("shows the server's refusal inside the dialog", async () => {
    mockApi("The amount must be more than zero.");
    renderTab();
    await userEvent.click(await screen.findByRole("button", { name: "Set day rate for Cover Teacher" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.type(within(dialog).getByLabelText(/Rate per full day/), "5");
    await userEvent.click(within(dialog).getByRole("button", { name: "Save rate" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent("The amount must be more than zero.");
  });

  it("shows the rate history, newest first", async () => {
    renderTab();
    await userEvent.click(await screen.findByRole("button", { name: "History of day rates for Cover Teacher" }));
    const dialog = await screen.findByRole("dialog");

    expect(await within(dialog).findByText("₹700.00")).toBeInTheDocument();
    expect(within(dialog).getByText("Revised")).toBeInTheDocument();
  });

  it.each(["light", "dark"] as const)(
    "has no WCAG 2.2 AA violations in %s mode",
    async (mode) => {
      const options = { runOnly: { type: "tag" as const, values: ["wcag2a", "wcag2aa", "wcag22aa"] } };
      const { container } = renderTab(mode);
      await screen.findByText("Cover Teacher");

      expect((await axe(container, options)).violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});
