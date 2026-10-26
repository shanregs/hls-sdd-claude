import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { buildMuiTheme } from "../../../theme/tokens";
import { PatternsPanel } from "../PatternsPanel";

const authFetch = vi.fn();

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const PATTERN = {
  id: "p1",
  ruleKind: "NTH_WEEKDAY",
  nth: 2,
  weekday: 6,
  dayOfMonth: null,
  startsAt: "10:00:00",
  endsAt: "13:00:00",
  topic: "Monthly review",
  trainerName: null,
  trainerContact: null,
  venueKind: "PHYSICAL",
  venue: null,
  description: null,
  defaultGroup: null,
  startMonth: "2026-11-01",
  endMonth: null,
  active: true,
  version: 3,
  skips: [],
};

function renderPanel() {
  return render(
    <ThemeProvider theme={buildMuiTheme("light")}>
      <MemoryRouter>
        <PatternsPanel onChanged={() => undefined} />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  authFetch.mockReset();
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (url.endsWith("/patterns/preview")) {
      return json([
        { month: "2026-11-01", date: "2026-11-30", note: "Day 31 does not exist in November, so the last day is used.", holidayNotice: false },
        { month: "2026-12-01", date: "2026-12-31", note: null, holidayNotice: true },
      ]);
    }
    if (init?.method === "POST" && url.endsWith("/skips")) return json({ reason: "ignored" }, 400);
    return json([PATTERN]);
  });
});

describe("Patterns (spec 024 US2)", () => {
  it("lists a pattern in words", async () => {
    renderPanel();
    expect(await screen.findByText(/Second Saturday of the month/)).toBeTruthy();
  });

  it("renders the preview with the clamp note and the holiday mark", async () => {
    renderPanel();
    await userEvent.click(await screen.findByRole("button", { name: "New pattern" }));
    await userEvent.type(screen.getByLabelText(/Topic/), "Standing review");
    await userEvent.click(screen.getByRole("button", { name: "Preview the next months" }));
    expect(await screen.findByText(/last day is used/)).toBeTruthy();
    expect(screen.getByText(/holiday or non-working day/)).toBeTruthy();
  });

  it("asks for confirmation before changing from a month", async () => {
    renderPanel();
    await userEvent.click(await screen.findByRole("button", { name: "Change" }));
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));
    expect(await screen.findByText("Confirm that you want to change the coming months.")).toBeTruthy();
    const puts = authFetch.mock.calls.filter((c) => (c[1] as RequestInit | undefined)?.method === "PUT");
    expect(puts).toHaveLength(0);
  });

  it("requires a reason to skip a month", async () => {
    renderPanel();
    await userEvent.click(await screen.findByRole("button", { name: "Skip a month" }));
    await userEvent.type(screen.getByLabelText("Month to skip"), "2026-12-01");
    await userEvent.click(screen.getByRole("button", { name: "Skip month" }));
    expect(await screen.findByText("Enter the reason for skipping.")).toBeTruthy();
    const posts = authFetch.mock.calls.filter((c) => (c[1] as RequestInit | undefined)?.method === "POST");
    expect(posts).toHaveLength(0);
  });
});
