import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../app/RouteGuard";
import type {
  YearMonthResult,
  YearMonthState,
  YearSaveResult,
  YearView,
} from "./schoolWorkingDaysApi";
import { YearEntryPage } from "./YearEntryPage";
import { FINALISED_NOTICE } from "./workingDaysRules";

const authFetch = vi.fn();
let grantedActions: string[] = ["VIEW", "CREATE", "EDIT", "DELETE"];
let hasMenuItem = true;

// a renewed sign-in token gives the page a new authFetch function on every render
let renewedToken = false;
vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({
    authFetch: renewedToken
      ? (...args: unknown[]) => authFetch(...args)
      : authFetch,
  }),
}));

vi.mock("../../access-model/useAccessModel", async () => {
  const actual = await vi.importActual<
    typeof import("../../access-model/useAccessModel")
  >("../../access-model/useAccessModel");
  return {
    ...actual,
    useAccessModel: () => ({
      loading: false,
      accessModel: {
        roles: ["ADMIN"],
        dataScope: {},
        navigation: hasMenuItem
          ? [
              {
                section: "MASTER DATA",
                items: [
                  {
                    label: "School Working Days",
                    route: "/master-data/school-working-days",
                    actions: grantedActions,
                  },
                ],
              },
            ]
          : [],
      },
    }),
  };
});

const YEAR = new Date().getFullYear() + 1;
const PATH = `/master-data/school-working-days/year?schoolId=s1&year=${YEAR}`;

const SCHOOLS = [
  {
    id: "s1",
    name: "Greenfield School",
    zoneId: "z1",
    zoneName: "North",
    active: true,
  },
  {
    id: "s2",
    name: "Hilltop School",
    zoneId: "z2",
    zoneName: "South",
    active: true,
  },
];

function empty(month: number): YearMonthState {
  return {
    month,
    daysInMonth: new Date(YEAR, month, 0).getDate(),
    calendarDerived: 24,
    id: null,
    workingDays: null,
    version: null,
    updatedBy: null,
    updatedAt: null,
  };
}

function stored(month: number, days: number, version = 1): YearMonthState {
  return {
    ...empty(month),
    id: `id-${month}`,
    workingDays: days,
    version,
    updatedBy: "Asha R",
    updatedAt: `${YEAR}-01-04T10:15:00`,
  };
}

function yearView(overrides: Record<number, YearMonthState> = {}): YearView {
  return {
    schoolId: "s1",
    schoolName: "Greenfield School",
    zoneName: "North",
    year: YEAR,
    months: Array.from(
      { length: 12 },
      (_, i) => overrides[i + 1] ?? empty(i + 1),
    ),
  };
}

function ok(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function result(months: YearMonthResult[]): YearSaveResult {
  const count = (s: string) => months.filter((m) => m.status === s).length;
  return {
    created: count("CREATED"),
    updated: count("UPDATED"),
    unchanged: count("UNCHANGED"),
    notSaved: count("NOT_SAVED"),
    months,
  };
}

const calls: {
  url: string;
  method: string;
  body?: { schoolId?: string; year?: number; months?: unknown };
}[] = [];
let view: YearView;
let saveAnswers: Response[];
let loadFailures = 0;

function mockApi(next: Partial<{ view: YearView; saves: Response[] }> = {}) {
  view = next.view ?? yearView();
  saveAnswers = next.saves ?? [];
  calls.length = 0;
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    const method = init?.method ?? "GET";
    calls.push({
      url,
      method,
      body: init?.body ? JSON.parse(init.body as string) : undefined,
    });
    if (url.startsWith("/api/v1/school-working-days/schools"))
      return ok(SCHOOLS);
    if (
      url.startsWith("/api/v1/school-working-days/year") &&
      method === "GET"
    ) {
      if (loadFailures > 0) {
        loadFailures -= 1;
        return ok({ reason: "Could not load the year." }, 500);
      }
      return ok(view);
    }
    if (
      url.startsWith("/api/v1/school-working-days/year") &&
      method === "PUT"
    ) {
      return saveAnswers.shift() ?? ok({ reason: "no answer queued" }, 500);
    }
    return ok({});
  });
}

function renderPage(path = PATH) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route
          path="/master-data/school-working-days/year"
          element={
            <RouteGuard>
              <YearEntryPage />
            </RouteGuard>
          }
        />
        <Route
          path="/master-data/school-working-days"
          element={<p>The grid page</p>}
        />
      </Routes>
    </MemoryRouter>,
  );
}

const field = (month: string) => screen.getByLabelText(`${month} ${YEAR}`);
const puts = () => calls.filter((c) => c.method === "PUT");

beforeEach(() => {
  grantedActions = ["VIEW", "CREATE", "EDIT", "DELETE"];
  hasMenuItem = true;
  renewedToken = false;
  loadFailures = 0;
  mockApi();
});
afterEach(() => vi.restoreAllMocks());

describe("YearEntryPage", () => {
  it("shows twelve months with the calendar hint and the stored figure", async () => {
    mockApi({ view: yearView({ 1: stored(1, 26) }) });
    renderPage();

    expect(await screen.findByLabelText(`January ${YEAR}`)).toHaveValue("26");
    expect(field("February")).toHaveValue("");
    expect(screen.getAllByText(/Calendar: 24/)).toHaveLength(12);
    // a stacked list, never a table, so it cannot scroll the page sideways at phone width (FR-025)
    expect(screen.queryByRole("table")).toBeNull();
    expect(
      screen.getByText(/Stored: 26 · changed by Asha R on/),
    ).toBeInTheDocument();
    expect(screen.getAllByText(/Nothing stored/)).toHaveLength(11);
    expect(
      screen.getByRole("list", { name: new RegExp(`${YEAR}`) }).children,
    ).toHaveLength(12);
  });

  it("shows the finalised-month notice once beside Save", async () => {
    renderPage();
    await screen.findByLabelText(`January ${YEAR}`);
    expect(screen.getAllByText(FINALISED_NOTICE)).toHaveLength(1);
  });

  it("keeps Save off, with 'Nothing to save', until something changes and again when it is changed back", async () => {
    mockApi({ view: yearView({ 1: stored(1, 26) }) });
    const user = userEvent.setup();
    renderPage();
    const january = await screen.findByLabelText(`January ${YEAR}`);
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
    expect(screen.getByText("Nothing to save")).toBeInTheDocument();

    await user.clear(january);
    await user.type(january, "25");
    expect(screen.getByRole("button", { name: "Save" })).toBeEnabled();

    await user.clear(january);
    await user.type(january, "26.0");
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
  });

  it("sends only the changed months with their versions, then saves again without a reload", async () => {
    mockApi({
      view: yearView({ 1: stored(1, 26, 4) }),
      saves: [
        ok(
          result([
            { month: 1, status: "UPDATED", current: stored(1, 25.5, 5) },
            { month: 3, status: "CREATED", current: stored(3, 27, 0) },
          ]),
        ),
        ok(
          result([{ month: 3, status: "UPDATED", current: stored(3, 28, 1) }]),
        ),
      ],
    });
    const user = userEvent.setup();
    renderPage();
    const january = await screen.findByLabelText(`January ${YEAR}`);
    await user.clear(january);
    await user.type(january, "25.5");
    await user.type(field("March"), "27");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(
      await screen.findByText(/1 created, 1 updated, 0 unchanged/),
    ).toBeInTheDocument();
    expect(puts()[0].body).toEqual({
      schoolId: "s1",
      year: YEAR,
      months: [
        { month: 1, workingDays: 25.5, version: 4 },
        { month: 3, workingDays: 27, version: null },
      ],
    });
    expect(field("March")).toHaveValue("27");

    // the next Save uses the versions the answer returned
    await user.clear(field("March"));
    await user.type(field("March"), "28");
    await user.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(puts()).toHaveLength(2));
    expect(puts()[1].body?.months).toEqual([
      { month: 3, workingDays: 28, version: 0 },
    ]);
    expect(
      calls.filter((c) => c.method === "GET" && c.url.includes("/year")),
    ).toHaveLength(1);
  });

  it("sends nothing for a blank field on a stored month", async () => {
    mockApi({ view: yearView({ 1: stored(1, 26) }) });
    const user = userEvent.setup();
    renderPage();
    const january = await screen.findByLabelText(`January ${YEAR}`);
    await user.clear(january);
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
    expect(puts()).toHaveLength(0);
  });

  it("holds back a figure that cannot be valid and still saves the others", async () => {
    mockApi({
      saves: [
        ok(
          result([{ month: 7, status: "CREATED", current: stored(7, 24, 0) }]),
        ),
      ],
    });
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText(`January ${YEAR}`);
    await user.type(field("April"), "31");
    await user.type(field("July"), "24");
    expect(
      await screen.findByText("Enter 0 to 30, in half days."),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(puts()).toHaveLength(1));
    expect(puts()[0].body?.months).toEqual([
      { month: 7, workingDays: 24, version: null },
    ]);
  });

  it("shows a loading state and an error with Retry", async () => {
    loadFailures = 1;
    const user = userEvent.setup();
    renderPage();
    expect(
      await screen.findByText("Could not load the year."),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Retry" }));
    expect(await screen.findByLabelText(`January ${YEAR}`)).toBeInTheDocument();
  });

  it("asks for a School before showing any month", async () => {
    renderPage(`/master-data/school-working-days/year?year=${YEAR}`);
    expect(
      await screen.findByText(/Choose a School to see its twelve months/),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText(`January ${YEAR}`)).toBeNull();
  });

  describe("when some months are not saved", () => {
    const partial = () =>
      ok(
        result([
          { month: 1, status: "CREATED", current: stored(1, 26, 0) },
          {
            month: 4,
            status: "NOT_SAVED",
            reason: "INVALID_FIGURE",
            message: "Working days for April must be 0 to 30, in half days.",
            current: empty(4),
          },
          {
            month: 3,
            status: "NOT_SAVED",
            reason: "STALE",
            message:
              "This figure was changed by someone else. The current figure is shown.",
            current: { ...stored(3, 27, 3), updatedBy: "Ravi K" },
          },
        ]),
      );

    it("reports the failed months in calendar order, keeps what was typed and focuses the first", async () => {
      mockApi({ view: yearView({ 3: stored(3, 26, 2) }), saves: [partial()] });
      const user = userEvent.setup();
      renderPage();
      await screen.findByLabelText(`January ${YEAR}`);
      await user.type(field("January"), "26");
      await user.clear(field("March"));
      await user.type(field("March"), "25");
      await user.type(field("April"), "30");
      await user.click(screen.getByRole("button", { name: "Save" }));

      const summary = await screen.findByText(
        /1 created, 0 updated, 0 unchanged, 2 not saved: March, April/,
      );
      expect(summary).toBeInTheDocument();
      expect(field("March")).toHaveValue("25"); // the user's own entry is kept
      expect(field("April")).toHaveValue("30");
      expect(screen.getByText(/changed by someone else/)).toBeInTheDocument();
      expect(screen.getByText(/April must be 0 to 30/)).toBeInTheDocument();
      expect(
        screen.getByText(/Stored: 27 · changed by Ravi K/),
      ).toBeInTheDocument();
      expect(field("March")).toHaveFocus();
    });

    it("offers the stored figure for a month someone else changed", async () => {
      mockApi({ view: yearView({ 3: stored(3, 26, 2) }), saves: [partial()] });
      const user = userEvent.setup();
      renderPage();
      await screen.findByLabelText(`January ${YEAR}`);
      await user.type(field("January"), "26");
      await user.clear(field("March"));
      await user.type(field("March"), "25");
      await user.type(field("April"), "30");
      await user.click(screen.getByRole("button", { name: "Save" }));
      await screen.findByText(/Use the stored figure \(27\)/);

      await user.click(
        screen.getByRole("button", { name: /Use the stored figure \(27\)/ }),
      );

      expect(field("March")).toHaveValue("27");
    });

    it("clears a month's message when the user edits it", async () => {
      mockApi({ view: yearView({ 3: stored(3, 26, 2) }), saves: [partial()] });
      const user = userEvent.setup();
      renderPage();
      await screen.findByLabelText(`January ${YEAR}`);
      await user.type(field("January"), "26");
      await user.type(field("April"), "30");
      await user.click(screen.getByRole("button", { name: "Save" }));
      await screen.findByText(/April must be 0 to 30/);

      await user.clear(field("April"));
      await user.type(field("April"), "29");

      expect(screen.queryByText(/April must be 0 to 30/)).toBeNull();
    });
  });

  it("keeps every typed value and shows nothing as saved when the whole Save fails", async () => {
    mockApi({
      saves: [
        ok({ reason: "Could not save the year." }, 500),
        ok(
          result([{ month: 2, status: "CREATED", current: stored(2, 24, 0) }]),
        ),
      ],
    });
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText(`January ${YEAR}`);
    await user.type(field("February"), "24");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(
      await screen.findByText("Could not save the year."),
    ).toBeInTheDocument();
    expect(field("February")).toHaveValue("24");
    expect(screen.queryByText(/created/)).toBeNull();

    // a retry sends the same body
    await user.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(puts()).toHaveLength(2));
    expect(puts()[1].body).toEqual(puts()[0].body);
  });

  describe("by role", () => {
    it("lets Admin and Director change every month and use the helpers", async () => {
      mockApi({ view: yearView({ 1: stored(1, 26) }) });
      renderPage();
      await screen.findByLabelText(`January ${YEAR}`);
      expect(field("January")).toBeEnabled();
      expect(field("February")).toBeEnabled();
      expect(
        screen.getByRole("button", { name: "Use calendar counts" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", {
          name: "Same figure for all empty months",
        }),
      ).toBeInTheDocument();
    });

    it("shows everything read-only, with no Save and no helpers, to a user with View only", async () => {
      grantedActions = ["VIEW"];
      mockApi({ view: yearView({ 1: stored(1, 26) }) });
      renderPage();
      await screen.findByLabelText(`January ${YEAR}`);
      expect(field("January")).toBeDisabled();
      expect(field("February")).toBeDisabled();
      expect(screen.queryByRole("button", { name: "Save" })).toBeNull();
      expect(
        screen.queryByRole("button", { name: "Use calendar counts" }),
      ).toBeNull();
    });

    it("lets a user with Create but not Edit change only empty months", async () => {
      grantedActions = ["VIEW", "CREATE"];
      mockApi({ view: yearView({ 1: stored(1, 26) }) });
      renderPage();
      await screen.findByLabelText(`January ${YEAR}`);
      expect(field("January")).toBeDisabled();
      expect(field("February")).toBeEnabled();
    });

    it("lets a user with Edit but not Create change only stored months, without the helpers", async () => {
      grantedActions = ["VIEW", "EDIT"];
      mockApi({ view: yearView({ 1: stored(1, 26) }) });
      renderPage();
      await screen.findByLabelText(`January ${YEAR}`);
      expect(field("January")).toBeEnabled();
      expect(field("February")).toBeDisabled();
      expect(
        screen.queryByRole("button", { name: "Use calendar counts" }),
      ).toBeNull();
    });

    it("shows the not-authorized page to a user without the menu item", async () => {
      hasMenuItem = false;
      renderPage();
      expect(screen.queryByLabelText(`January ${YEAR}`)).toBeNull();
      expect(authFetch).not.toHaveBeenCalled();
    });
  });

  describe("helpers", () => {
    it("fills only the empty months with the calendar counts and saves nothing", async () => {
      mockApi({ view: yearView({ 1: stored(1, 26), 2: stored(2, 20) }) });
      const user = userEvent.setup();
      renderPage();
      await screen.findByLabelText(`January ${YEAR}`);
      await user.type(field("May"), "21");

      await user.click(
        screen.getByRole("button", { name: "Use calendar counts" }),
      );

      expect(field("January")).toHaveValue("26");
      expect(field("February")).toHaveValue("20");
      expect(field("May")).toHaveValue("21");
      expect(field("March")).toHaveValue("24");
      expect(screen.getAllByText("Filled, not saved").length).toBeGreaterThan(
        0,
      );
      expect(puts()).toHaveLength(0);
      expect(screen.getByRole("button", { name: "Save" })).toBeEnabled();
    });

    it("fills one figure into the empty months it fits and lists the others", async () => {
      const user = userEvent.setup();
      renderPage();
      await screen.findByLabelText(`January ${YEAR}`);

      await user.click(
        screen.getByRole("button", {
          name: "Same figure for all empty months",
        }),
      );
      const dialog = await screen.findByRole("dialog");
      await user.type(within(dialog).getByLabelText("Working days"), "30");
      await user.click(within(dialog).getByRole("button", { name: "Fill" }));

      expect(field("March")).toHaveValue("30");
      expect(field("February")).toHaveValue("");
      expect(
        screen.getByText(/Not filled \(30 does not fit\): February/),
      ).toBeInTheDocument();
      expect(puts()).toHaveLength(0);
    });
  });

  describe("unsaved changes", () => {
    it("asks before changing the School and keeps the figures when the user keeps editing", async () => {
      const user = userEvent.setup();
      renderPage();
      await screen.findByLabelText(`January ${YEAR}`);
      await user.type(field("February"), "24");

      await user.click(screen.getByRole("combobox", { name: "School" }));
      await user.click(
        await screen.findByRole("option", { name: "Hilltop School" }),
      );

      const dialog = await screen.findByRole("dialog");
      expect(
        within(dialog).getByText("Discard your changes?"),
      ).toBeInTheDocument();
      await user.click(
        within(dialog).getByRole("button", { name: "Keep editing" }),
      );
      expect(field("February")).toHaveValue("24");
    });

    it("changes the School without asking when nothing was typed", async () => {
      const user = userEvent.setup();
      renderPage();
      await screen.findByLabelText(`January ${YEAR}`);

      await user.click(screen.getByRole("combobox", { name: "School" }));
      await user.click(
        await screen.findByRole("option", { name: "Hilltop School" }),
      );

      await waitFor(() =>
        expect(calls.some((c) => c.url.includes("schoolId=s2"))).toBe(true),
      );
      expect(screen.queryByText("Discard your changes?")).toBeNull();
    });

    it("asks before going back to the grid and goes back when confirmed", async () => {
      const user = userEvent.setup();
      renderPage();
      await screen.findByLabelText(`January ${YEAR}`);
      await user.type(field("February"), "24");

      await user.click(
        screen.getByRole("button", { name: "Back to the grid" }),
      );
      await user.click(
        await screen.findByRole("button", { name: "Discard and go back" }),
      );

      expect(await screen.findByText("The grid page")).toBeInTheDocument();
    });

    it("goes back to the grid at once when nothing was typed", async () => {
      const user = userEvent.setup();
      renderPage();
      await screen.findByLabelText(`January ${YEAR}`);
      await user.click(
        screen.getByRole("button", { name: "Back to the grid" }),
      );
      expect(await screen.findByText("The grid page")).toBeInTheDocument();
    });

    it("warns when the tab is closed with unsaved changes and stops warning when there are none", async () => {
      const add = vi.spyOn(window, "addEventListener");
      const remove = vi.spyOn(window, "removeEventListener");
      const user = userEvent.setup();
      renderPage();
      await screen.findByLabelText(`January ${YEAR}`);
      expect(
        add.mock.calls.filter(([type]) => type === "beforeunload"),
      ).toHaveLength(0);

      await user.type(field("February"), "24");
      expect(
        add.mock.calls.filter(([type]) => type === "beforeunload"),
      ).toHaveLength(1);

      await user.clear(field("February"));
      expect(
        remove.mock.calls.filter(([type]) => type === "beforeunload"),
      ).toHaveLength(1);
    });
  });
});

describe("YearEntryPage usability", () => {
  it("says there is nothing to fill, and disables the helpers, when every month has a figure", async () => {
    const all: Record<number, YearMonthState> = {};
    for (let m = 1; m <= 12; m++) all[m] = stored(m, 20);
    mockApi({ view: yearView(all) });
    renderPage();
    await screen.findByLabelText(`January ${YEAR}`);

    expect(
      screen.getByText(/Every month already has a figure/),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Use calendar counts" }),
    ).toBeDisabled();
    // a future year can still be set again with the replace option
    expect(
      screen.getByRole("button", { name: "Same figure for all empty months" }),
    ).toBeEnabled();
  });

  it("does not say it while some month is empty", async () => {
    renderPage();
    await screen.findByLabelText(`January ${YEAR}`);

    expect(
      screen.queryByText(/Every month already has a figure/),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Same figure for all empty months" }),
    ).toBeEnabled();
  });

  it("selects a stored figure on focus so typing replaces it", async () => {
    mockApi({ view: yearView({ 3: stored(3, 31) }) });
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText(`March ${YEAR}`);

    await user.click(field("March"));
    await user.keyboard("24");

    expect(field("March")).toHaveValue("24");
  });

  it("keeps what the user typed when the sign-in token is renewed", async () => {
    const user = userEvent.setup();
    const { rerender } = renderPage();
    await screen.findByLabelText(`January ${YEAR}`);
    await user.type(field("February"), "22");

    renewedToken = true;
    rerender(
      <MemoryRouter initialEntries={[PATH]}>
        <Routes>
          <Route
            path="/master-data/school-working-days/year"
            element={
              <RouteGuard>
                <YearEntryPage />
              </RouteGuard>
            }
          />
        </Routes>
      </MemoryRouter>,
    );
    await waitFor(() => expect(field("February")).toHaveValue("22"));
    expect(
      calls.filter((c) => c.method === "GET" && c.url.includes("/year")),
    ).toHaveLength(1);
  });

  describe("setting a future year again", () => {
    async function openSameFigure(user: ReturnType<typeof userEvent.setup>) {
      await user.click(
        screen.getByRole("button", {
          name: "Same figure for all empty months",
        }),
      );
      return screen.findByRole("dialog");
    }

    it("replaces stored and typed figures in every month when asked, and leaves Save ready", async () => {
      const all: Record<number, YearMonthState> = {};
      for (let m = 1; m <= 12; m++) all[m] = stored(m, 20);
      mockApi({ view: yearView(all) });
      const user = userEvent.setup();
      renderPage();
      await screen.findByLabelText(`January ${YEAR}`);

      const dialog = await openSameFigure(user);
      await user.type(within(dialog).getByLabelText("Working days"), "22");
      await user.click(
        within(dialog).getByRole("checkbox", {
          name: "Also replace months that already have a figure",
        }),
      );
      await user.click(within(dialog).getByRole("button", { name: "Fill" }));

      expect(field("January")).toHaveValue("22");
      expect(field("June")).toHaveValue("22");
      expect(field("December")).toHaveValue("22");
      expect(await screen.findByRole("button", { name: "Save" })).toBeEnabled();
      expect(puts()).toHaveLength(0);
    });

    it("keeps stored figures when the box is not ticked", async () => {
      mockApi({ view: yearView({ 1: stored(1, 20) }) });
      const user = userEvent.setup();
      renderPage();
      await screen.findByLabelText(`January ${YEAR}`);

      const dialog = await openSameFigure(user);
      await user.type(within(dialog).getByLabelText("Working days"), "22");
      await user.click(within(dialog).getByRole("button", { name: "Fill" }));

      expect(field("January")).toHaveValue("20");
      expect(field("February")).toHaveValue("22");
    });

    it("is not offered for the current year or to a user who cannot edit", async () => {
      const current = new Date().getFullYear();
      const user = userEvent.setup();
      const first = renderPage(
        `/master-data/school-working-days/year?schoolId=s1&year=${current}`,
      );
      await screen.findByLabelText(`January ${current}`);
      const dialog = await openSameFigure(user);
      expect(within(dialog).queryByRole("checkbox")).not.toBeInTheDocument();
      first.unmount();

      grantedActions = ["VIEW", "CREATE"];
      renderPage();
      await screen.findByLabelText(`January ${YEAR}`);
      const second = await openSameFigure(user);
      expect(within(second).queryByRole("checkbox")).not.toBeInTheDocument();
    });

    it("opens the dialog empty each time, so a second figure is not added to the first", async () => {
      const user = userEvent.setup();
      renderPage();
      await screen.findByLabelText(`January ${YEAR}`);

      let dialog = await openSameFigure(user);
      await user.type(within(dialog).getByLabelText("Working days"), "20");
      await user.click(within(dialog).getByRole("button", { name: "Fill" }));
      await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());

      dialog = await openSameFigure(user);
      expect(within(dialog).getByLabelText("Working days")).toHaveValue("");
    });
  });
});
