import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { axe } from "vitest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../../app/RouteGuard";
import { buildMuiTheme } from "../../../theme/tokens";
import { SubstitutePayoutsPage } from "../SubstitutePayoutsPage";

const authFetch = vi.fn();
let actions: string[] = ["VIEW", "EDIT", "APPROVE", "EXPORT"];

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

vi.mock("../../../access-model/useAccessModel", async () => {
  const actual = await vi.importActual<typeof import("../../../access-model/useAccessModel")>(
    "../../../access-model/useAccessModel",
  );
  return {
    ...actual,
    useAccessModel: () => ({
      loading: false,
      accessModel: {
        roles: ["ADMIN"],
        dataScope: {},
        navigation: [
          {
            section: "PAYROLL & FINANCE",
            items: [{ label: "Substitute Payouts", route: "/operations/substitute-payouts", actions }],
          },
        ],
      },
    }),
  };
});

function day(overrides: Record<string, unknown> = {}) {
  return {
    id: "d-1",
    substituteTeacherId: "t-1",
    substituteName: "Sita Substitute",
    schoolId: "s-1",
    schoolName: "Sunrise School",
    zoneId: "z-1",
    zoneName: "North Zone",
    date: "2026-10-12",
    dayValue: 1,
    live: true,
    attendance: "NOT_MARKED",
    status: "ALLOCATED",
    statusText: "Allocated, attendance not marked",
    mismatchReason: null,
    rate: 800,
    amount: 800,
    runMonth: null,
    workMonth: "2026-10-01",
    paidAmount: null,
    needsReview: false,
    locked: false,
    version: 2,
    actions: ["MARK"],
    ...overrides,
  };
}

const TOTALS = { allocated: 1, attended: 0, paid: 0, unpaidByStatus: {}, amountPaid: 0 };

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function mockApi(rows: unknown[] = [day()]) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    const path = String(url);
    if (init?.method === "PUT" && path.endsWith("/attendance")) {
      return json(day({ attendance: "PRESENT", status: "ATTENDED", statusText: "Attended, month not locked yet", version: 3 }));
    }
    if (init?.method === "POST" && path.includes("/months/lock")) {
      return json({ locked: 2, alreadyLocked: 0, blocked: [{ substituteTeacherId: "t-9", reason: "Attendance is not marked for: 2026-10-14." }] });
    }
    if (init?.method === "POST" && path.endsWith("/reopen")) return { ok: true, status: 200 } as Response;
    return json({ content: rows, page: 0, size: 100, totalElements: rows.length, totals: TOTALS });
  });
}

function renderPage(mode: "light" | "dark" = "light") {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <MemoryRouter initialEntries={["/operations/substitute-payouts"]}>
        <Routes>
          <Route
            path="/operations/substitute-payouts"
            element={
              <RouteGuard>
                <SubstitutePayoutsPage />
              </RouteGuard>
            }
          />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  authFetch.mockReset();
  actions = ["VIEW", "EDIT", "APPROVE", "EXPORT"];
  mockApi();
});

describe("Substitute Payouts", () => {
  it("lists the days with the totals, dates as DD/MM/YYYY and amounts in rupees", async () => {
    renderPage();

    expect(await screen.findByText("Sita Substitute")).toBeInTheDocument();
    expect(screen.getByText("12/10/2026")).toBeInTheDocument();
    expect(screen.getByText("Allocated, attendance not marked")).toBeInTheDocument();
    expect(screen.getAllByText("₹800.00").length).toBeGreaterThan(0);
    expect(screen.getByText(/Allocated/, { selector: "p" })).toHaveTextContent("Amount paid ₹0.00");
  });

  it("marks a day present and reloads the list", async () => {
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: /Mark Sita Substitute present on 12\/10\/2026/ }));

    await waitFor(() =>
      expect(authFetch).toHaveBeenCalledWith(
        expect.stringContaining("/days/d-1/attendance"),
        expect.objectContaining({ method: "PUT", body: JSON.stringify({ attendance: "PRESENT", version: 2 }) }),
      ),
    );
  });

  it("offers no mark buttons when the server sent no MARK action", async () => {
    mockApi([day({ actions: [], locked: true })]);
    renderPage();

    await screen.findByText("Sita Substitute");
    expect(screen.queryByRole("button", { name: /Mark Sita/ })).not.toBeInTheDocument();
  });

  it("locks every complete substitute for the month and reports who was not ready", async () => {
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: /Lock all complete substitutes/ }));

    expect(await screen.findByText(/2 locked, 0 already locked, 1 not ready/)).toBeInTheDocument();
  });

  it("hides the lock and reopen controls without the Approve right", async () => {
    actions = ["VIEW", "EDIT"];
    mockApi([day({ locked: true, actions: [] })]);
    renderPage();

    await screen.findByText("Sita Substitute");
    expect(screen.queryByRole("button", { name: /Lock all/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Reopen/ })).not.toBeInTheDocument();
  });

  it("reopens a locked month only with a reason", async () => {
    mockApi([day({ locked: true, actions: [], attendance: "PRESENT", status: "LOCKED_WAITING_RUN" })]);
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: /Reopen Sita Substitute for 2026-10/ }));

    const reopen = screen.getByRole("button", { name: "Reopen" });
    expect(reopen).toBeDisabled();
    await userEvent.type(screen.getByLabelText(/Reason/), "Wrong day marked");
    expect(reopen).toBeEnabled();
    await userEvent.click(reopen);

    await waitFor(() =>
      expect(authFetch).toHaveBeenCalledWith(
        expect.stringContaining("/months/t-1/2026-10/reopen"),
        expect.objectContaining({ body: JSON.stringify({ reason: "Wrong day marked" }) }),
      ),
    );
  });

  it("shows an error and an empty message", async () => {
    mockApi([]);
    renderPage();
    expect(await screen.findByText("No substitute days for these filters.")).toBeInTheDocument();

    authFetch.mockImplementation(async () => json({ reason: "Nope" }, 500));
    renderPage();
    expect((await screen.findAllByRole("alert")).length).toBeGreaterThan(0);
  });

  it.each(["light", "dark"] as const)(
    "has no WCAG 2.2 AA violations in %s mode",
    async (mode) => {
      const options = { runOnly: { type: "tag" as const, values: ["wcag2a", "wcag2aa", "wcag22aa"] } };
      const { container } = renderPage(mode);
      await screen.findByText("Sita Substitute");

      expect((await axe(container, options)).violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});
