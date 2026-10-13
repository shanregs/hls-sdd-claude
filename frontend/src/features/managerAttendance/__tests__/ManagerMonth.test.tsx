import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { axe } from "vitest-axe";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../../app/RouteGuard";
import { buildMuiTheme, type ThemeMode } from "../../../theme/tokens";
import { MyManagerMonthPage } from "../MyManagerMonthPage";

const authFetch = vi.fn();
let navigation: { section: string; items: unknown[] }[] = [];
let roles: string[] = ["MANAGER"];

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

vi.mock("../../../access-model/useAccessModel", async () => {
  const actual = await vi.importActual<
    typeof import("../../../access-model/useAccessModel")
  >("../../../access-model/useAccessModel");
  return {
    ...actual,
    useAccessModel: () => ({
      loading: false,
      accessModel: { roles, dataScope: {}, navigation },
    }),
  };
});

const MONTH_ROUTE = "/manager-attendance/me/month";

const nav = (actions: string[]) => [
  {
    section: "MY ATTENDANCE",
    items: [{ label: "My Month", route: MONTH_ROUTE, actions }],
  },
];

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const thisMonth = new Date().toISOString().slice(0, 7);

const day = (date: string, status: string, extra: object = {}) => ({
  date,
  status,
  id: null,
  dayType: null,
  purpose: null,
  checkInAt: null,
  checkOutAt: null,
  late: false,
  hasPhoto: false,
  leave: null,
  flags: [],
  activities: [],
  checkInPlace: null,
  checkOutPlace: null,
  history: null,
  ...extra,
});

const MONTH = {
  month: thisMonth,
  managerId: "m1",
  managerName: "Asha Manager",
  locked: false,
  figures: {
    workingDays: 22,
    daysWorked: 18,
    leavePaid: 1,
    leaveUnpaid: 0.5,
    unpaid: 2.5,
    fieldDays: 3,
    pending: [],
  },
  figuresUnavailable: null,
  days: [
    day(`${thisMonth}-01`, "WORKED", {
      id: "d1",
      dayType: "OFFICE",
      checkInAt: "2026-10-01T12:00:00Z",
      checkOutAt: "2026-10-01T13:00:00Z",
      flags: ["CORRECTED"],
    }),
    day(`${thisMonth}-02`, "ABSENT"),
    day(`${thisMonth}-03`, "HOLIDAY"),
  ],
};

const REQUEST = {
  id: "r1",
  date: `${thisMonth}-02`,
  proposedTime: "09:15:00",
  dayType: "OFFICE",
  purpose: null,
  reason: "Phone was off",
  status: "PENDING",
  decisionReason: null,
};

function respond(
  overrides: { month?: object; requests?: object[]; post?: Response } = {},
) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method === "POST") {
      return overrides.post ?? json(REQUEST, 201);
    }
    if (init?.method === "DELETE") return json({}, 204);
    if (String(url).includes("/regularizations")) {
      return json(overrides.requests ?? []);
    }
    return json({ ...MONTH, ...overrides.month });
  });
}

function renderPage(mode: ThemeMode = "light") {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <MemoryRouter initialEntries={[MONTH_ROUTE]}>
        <Routes>
          <Route
            path={MONTH_ROUTE}
            element={
              <RouteGuard>
                <MyManagerMonthPage />
              </RouteGuard>
            }
          />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const posted = () => authFetch.mock.calls.find((c) => c[1]?.method === "POST");

beforeEach(() => {
  authFetch.mockReset();
  roles = ["MANAGER"];
  navigation = nav(["VIEW", "CREATE", "DELETE"]);
  respond();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("My month (spec 032 US5)", () => {
  it("shows a status for each day and the month figures", async () => {
    renderPage();
    const table = await screen.findByRole("table", {
      name: "Days of the month",
    });
    expect(within(table).getByText("Worked")).toBeInTheDocument();
    expect(within(table).getByText("Absent")).toBeInTheDocument();
    expect(within(table).getByText("Holiday")).toBeInTheDocument();
    expect(within(table).getByText("Corrected")).toBeInTheDocument();
    expect(screen.getByText("Days worked")).toBeInTheDocument();
    expect(screen.getByText("18")).toBeInTheDocument();
    expect(screen.getByText("2.5")).toBeInTheDocument();
  });

  it("says why the figures are missing instead of showing zeros", async () => {
    respond({
      month: {
        figures: null,
        figuresUnavailable: "The joining date has not been recorded.",
      },
    });
    renderPage();
    expect(
      await screen.findByText(/joining date has not been recorded/),
    ).toBeInTheDocument();
    expect(screen.queryByText("Days worked")).not.toBeInTheDocument();
  });

  it("shows a locked month as locked", async () => {
    respond({ month: { locked: true } });
    renderPage();
    expect(await screen.findByText(/locked for payroll/)).toBeInTheDocument();
  });

  it("refuses an incomplete request before sending", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(
      await screen.findByRole("button", { name: "Send request" }),
    );
    expect(
      await screen.findByText(/Choose the day you forgot/),
    ).toBeInTheDocument();
    expect(posted()).toBeUndefined();
  });

  it("requires a reason and a purpose for a Field day", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("button", { name: "Send request" });
    await user.type(screen.getByLabelText("Day"), `${thisMonth}-01`);
    await user.click(screen.getByRole("radio", { name: "Field" }));
    await user.click(screen.getByRole("button", { name: "Send request" }));
    expect(await screen.findByText(/needs a purpose/)).toBeInTheDocument();
    await user.type(screen.getByLabelText("Purpose"), "School visit");
    await user.click(screen.getByRole("button", { name: "Send request" }));
    expect(await screen.findByText(/reason is required/i)).toBeInTheDocument();
    expect(posted()).toBeUndefined();
  });

  it("sends a complete request and reloads", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("button", { name: "Send request" });
    await user.type(screen.getByLabelText("Day"), `${thisMonth}-01`);
    await user.type(screen.getByLabelText("Reason"), "Phone was off");
    await user.click(screen.getByRole("button", { name: "Send request" }));
    await waitFor(() => expect(posted()).toBeDefined());
    const sent = JSON.parse(posted()![1].body as string);
    expect(sent).toMatchObject({
      date: `${thisMonth}-01`,
      dayType: "OFFICE",
      purpose: null,
      reason: "Phone was off",
    });
  });

  it("shows the server's refusal", async () => {
    respond({
      post: json({ reason: "That month is locked." }, 409),
    });
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("button", { name: "Send request" });
    await user.type(screen.getByLabelText("Day"), `${thisMonth}-01`);
    await user.type(screen.getByLabelText("Reason"), "Phone was off");
    await user.click(screen.getByRole("button", { name: "Send request" }));
    expect(
      await screen.findByText("That month is locked."),
    ).toBeInTheDocument();
  });

  it("lists the requests and cancels a pending one", async () => {
    respond({ requests: [REQUEST] });
    const user = userEvent.setup();
    renderPage();
    await user.click(
      await screen.findByRole("button", { name: /Cancel the request for/ }),
    );
    await waitFor(() =>
      expect(
        authFetch.mock.calls.some(
          (c) =>
            c[1]?.method === "DELETE" &&
            String(c[0]).endsWith("/me/regularizations/r1"),
        ),
      ).toBe(true),
    );
  });

  it("hides the request form and the cancel button without CREATE and DELETE", async () => {
    navigation = nav(["VIEW"]);
    respond({ requests: [REQUEST] });
    renderPage();
    await screen.findByRole("table", { name: "Days of the month" });
    expect(
      screen.queryByRole("button", { name: "Send request" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Cancel the request for/ }),
    ).not.toBeInTheDocument();
  });

  it("is refused for a role without the page", async () => {
    roles = ["TEACHER"];
    navigation = [];
    renderPage();
    await waitFor(() =>
      expect(
        screen.queryByRole("table", { name: "Days of the month" }),
      ).not.toBeInTheDocument(),
    );
    expect(
      authFetch.mock.calls.some((c) => String(c[0]).includes("/me/months/")),
    ).toBe(false);
  });

  it.each<ThemeMode>(["light", "dark"])(
    "has no accessibility violations in the %s theme",
    async (mode) => {
      respond({ requests: [REQUEST] });
      const { container } = renderPage(mode);
      await screen.findByRole("table", { name: "Days of the month" });
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});

describe("My month keyboard order and errors (spec 032 T070)", () => {
  it("moves through the request form in reading order and announces its error", async () => {
    const user = userEvent.setup();
    renderPage();
    const day = await screen.findByLabelText("Day");
    day.focus();
    const names: string[] = [];
    for (let i = 0; i < 4; i++) {
      await user.tab();
      const el = document.activeElement as HTMLInputElement;
      names.push(el.labels?.[0]?.textContent ?? el.textContent ?? "");
    }
    expect(names.join("|")).toMatch(
      /Time you arrived.*Office.*Reason.*Send request/,
    );
    await user.keyboard("{Enter}");
    expect(await screen.findByRole("alert")).toHaveTextContent(
      /Choose the day you forgot/,
    );
  });
});
