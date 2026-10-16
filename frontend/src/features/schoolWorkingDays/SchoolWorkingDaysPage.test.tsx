import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useSearchParams } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../app/RouteGuard";
import { SchoolWorkingDaysPage } from "./SchoolWorkingDaysPage";
import { FINALISED_NOTICE } from "./workingDaysRules";

const authFetch = vi.fn();
let grantedActions: string[] = ["VIEW", "CREATE", "EDIT", "DELETE"];
let hasMenuItem = true;

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
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

const YEAR = 2027;

function row(overrides: Record<string, unknown> = {}) {
  return {
    id: "r1",
    schoolId: "s1",
    schoolName: "Greenfield School",
    zoneId: "z1",
    zoneName: "North",
    year: YEAR,
    month: 3,
    workingDays: 26,
    daysInMonth: 31,
    calendarDerived: 27,
    updatedBy: "Asha R",
    updatedAt: "2027-02-01T10:15:00",
    version: 2,
    ...overrides,
  };
}

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
    active: false,
  },
];

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

interface Api {
  rows: ReturnType<typeof row>[];
  total?: number;
  listFails?: boolean;
  writes: Response[];
}

let api: Api;
const calls: { url: string; method: string; body?: unknown }[] = [];

function mockApi(next: Partial<Api> = {}) {
  api = { rows: [row()], writes: [], ...next };
  calls.length = 0;
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    const method = init?.method ?? "GET";
    calls.push({
      url,
      method,
      body: init?.body ? JSON.parse(init.body as string) : undefined,
    });
    if (method !== "GET") {
      return api.writes.shift() ?? jsonResponse(row());
    }
    if (url.startsWith("/api/v1/school-working-days/schools")) {
      return jsonResponse(SCHOOLS);
    }
    if (url.includes("/history") || url.includes("/deleted")) {
      return jsonResponse([]);
    }
    if (api.listFails) {
      api.listFails = false;
      return jsonResponse({ reason: "Could not load the working days." }, 500);
    }
    if (url.includes("size=100")) {
      // the add dialog asking which months a School already has: nobody has set any yet
      return jsonResponse({ items: [], total: 0, page: 0, size: 100 });
    }
    return jsonResponse({
      items: api.rows,
      total: api.total ?? api.rows.length,
      page: 0,
      size: 25,
    });
  });
}

function renderPage(path = `/master-data/school-working-days?year=${YEAR}`) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route
          path="/master-data/school-working-days"
          element={
            <RouteGuard>
              <SchoolWorkingDaysPage />
            </RouteGuard>
          }
        />
        <Route
          path="/master-data/school-working-days/year"
          element={<YearStub />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

/** Stands in for the year screen and shows the address it was opened with. */
function YearStub() {
  const [params] = useSearchParams();
  return (
    <p>
      The year screen: school {params.get("schoolId") ?? "none"}, year{" "}
      {params.get("year")}
    </p>
  );
}

const ROW_LABEL = "Greenfield School March 2027";

describe("SchoolWorkingDaysPage (spec 037 US2)", () => {
  beforeEach(() => {
    authFetch.mockReset();
    grantedActions = ["VIEW", "CREATE", "EDIT", "DELETE"];
    hasMenuItem = true;
    mockApi();
  });

  it("lists the rows with months by name, the figures and dates as DD/MM/YYYY", async () => {
    renderPage();

    expect(await screen.findByText("Greenfield School")).toBeInTheDocument();
    expect(screen.getByText("March 2027")).toBeInTheDocument();
    expect(screen.getByText("26")).toBeInTheDocument();
    expect(screen.getByText("31")).toBeInTheDocument();
    expect(screen.getByText("27")).toBeInTheDocument();
    expect(screen.getByText("differs")).toBeInTheDocument();
    expect(screen.getByText(/Asha R, 01\/02\/2027/)).toBeInTheDocument();
  });

  it("shows Add, Edit and Delete to an Admin", async () => {
    renderPage();
    await screen.findByText("Greenfield School");

    expect(
      screen.getByRole("button", { name: "Add working days" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Copy year" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: `Edit ${ROW_LABEL}` }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: `Delete ${ROW_LABEL}` }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: `History ${ROW_LABEL}` }),
    ).toBeInTheDocument();
  });

  describe("Enter a year (spec 038 US5)", () => {
    it("opens the year screen on the School and year the grid is filtered to", async () => {
      const user = userEvent.setup();
      renderPage(`/master-data/school-working-days?year=${YEAR}&schoolId=s1`);

      await user.click(
        await screen.findByRole("button", { name: "Enter a year" }),
      );

      expect(
        await screen.findByText(`The year screen: school s1, year ${YEAR}`),
      ).toBeInTheDocument();
    });

    it("opens it with the year alone when no School is chosen", async () => {
      const user = userEvent.setup();
      renderPage();
      await screen.findByText("Greenfield School");

      await user.click(screen.getByRole("button", { name: "Enter a year" }));

      expect(
        await screen.findByText(`The year screen: school none, year ${YEAR}`),
      ).toBeInTheDocument();
    });

    it("is offered to a user with Create or Edit and hidden from one with View only", async () => {
      grantedActions = ["VIEW", "EDIT"];
      const { unmount } = renderPage();
      await screen.findByText("Greenfield School");
      expect(
        screen.getByRole("button", { name: "Enter a year" }),
      ).toBeInTheDocument();
      unmount();

      grantedActions = ["VIEW"];
      renderPage();
      await screen.findByText("Greenfield School");
      expect(screen.queryByRole("button", { name: "Enter a year" })).toBeNull();
    });
  });

  it("hides Delete from a Director, who has View, Create and Edit", async () => {
    grantedActions = ["VIEW", "CREATE", "EDIT"];
    renderPage();
    await screen.findByText("Greenfield School");

    expect(
      screen.getByRole("button", { name: "Add working days" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: `Edit ${ROW_LABEL}` }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Delete / })).toBeNull();
  });

  it("hides every write action from a view-only user but keeps history", async () => {
    grantedActions = ["VIEW"];
    renderPage();
    await screen.findByText("Greenfield School");

    expect(
      screen.queryByRole("button", { name: "Add working days" }),
    ).toBeNull();
    expect(screen.queryByRole("button", { name: "Copy year" })).toBeNull();
    expect(screen.queryByRole("button", { name: /^Edit / })).toBeNull();
    expect(screen.queryByRole("button", { name: /^Delete / })).toBeNull();
    expect(
      screen.getByRole("button", { name: `History ${ROW_LABEL}` }),
    ).toBeInTheDocument();
  });

  it("shows the not-authorized page, and never loads data, to a user without View", async () => {
    hasMenuItem = false;
    renderPage();

    await waitFor(() =>
      expect(
        screen.queryByRole("heading", { name: "School Working Days" }),
      ).toBeNull(),
    );
    expect(authFetch).not.toHaveBeenCalled();
  });

  it("explains how to add the first row, and hides that hint when the user cannot add", async () => {
    mockApi({ rows: [] });
    const { unmount } = renderPage();
    expect(
      await screen.findByText(/Use Add working days to enter the first one/),
    ).toBeInTheDocument();
    unmount();

    grantedActions = ["VIEW"];
    mockApi({ rows: [] });
    renderPage();
    expect(
      await screen.findByText("No working days entered for this year."),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Use Add working days/)).toBeNull();
  });

  it("shows an error with a retry when the list cannot be loaded", async () => {
    const user = userEvent.setup();
    mockApi({ listFails: true });
    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load the working days.",
    );
    await user.click(screen.getByRole("button", { name: "Retry" }));
    expect(await screen.findByText("Greenfield School")).toBeInTheDocument();
  });

  it("keeps the filters when moving to the next page", async () => {
    const user = userEvent.setup();
    mockApi({ rows: [row()], total: 60 });
    renderPage(
      `/master-data/school-working-days?year=${YEAR}&schoolId=s1&zoneId=z1`,
    );
    await screen.findByText("March 2027");

    await user.click(screen.getByRole("button", { name: /next page/i }));

    await waitFor(() => {
      const listCalls = calls.filter(
        (c) =>
          c.method === "GET" &&
          c.url.startsWith("/api/v1/school-working-days?"),
      );
      const last = listCalls[listCalls.length - 1].url;
      expect(last).toContain("page=1");
      expect(last).toContain("schoolId=s1");
      expect(last).toContain("zoneId=z1");
      expect(last).toContain(`year=${YEAR}`);
    });
  });

  it("validates inline, shows the finalised-month notice, and Cancel leaves the figure untouched", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Greenfield School");

    await user.click(screen.getByRole("button", { name: `Edit ${ROW_LABEL}` }));
    const input = screen.getByRole("textbox", {
      name: `Working days for ${ROW_LABEL}`,
    });
    expect(screen.getByText(FINALISED_NOTICE)).toBeInTheDocument();

    await user.clear(input);
    await user.type(input, "25.3");
    await user.click(screen.getByRole("button", { name: "Save" }));
    expect(
      await screen.findByText("Enter 0 to 31, in half days."),
    ).toBeInTheDocument();
    await user.clear(input);
    await user.type(input, "32");
    await user.click(screen.getByRole("button", { name: "Save" }));
    expect(
      await screen.findByText("Enter 0 to 31, in half days."),
    ).toBeInTheDocument();
    expect(calls.some((c) => c.method === "PUT")).toBe(false);

    await user.click(screen.getByRole("button", { name: "Cancel" }));
    expect(
      screen.queryByRole("textbox", { name: /Working days for/ }),
    ).toBeNull();
    expect(screen.getByText("26")).toBeInTheDocument();
    expect(calls.some((c) => c.method === "PUT")).toBe(false);
  });

  it("saves an edited figure with the version it loaded", async () => {
    const user = userEvent.setup();
    mockApi({ writes: [jsonResponse(row({ workingDays: 25.5, version: 3 }))] });
    renderPage();
    await screen.findByText("Greenfield School");

    await user.click(screen.getByRole("button", { name: `Edit ${ROW_LABEL}` }));
    const input = screen.getByRole("textbox", {
      name: `Working days for ${ROW_LABEL}`,
    });
    await user.clear(input);
    await user.type(input, "25.5");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("25.5")).toBeInTheDocument();
    const put = calls.find((c) => c.method === "PUT");
    expect(put?.url).toBe("/api/v1/school-working-days/r1");
    expect(put?.body).toEqual({ workingDays: 25.5, version: 2 });
  });

  it("tells the user the row changed, shows the current figure and keeps the entry", async () => {
    const user = userEvent.setup();
    mockApi({
      writes: [
        jsonResponse(
          {
            code: "STALE",
            reason: "This figure was changed by someone else.",
            current: row({ workingDays: 24, version: 5 }),
          },
          409,
        ),
      ],
    });
    renderPage();
    await screen.findByText("Greenfield School");

    await user.click(screen.getByRole("button", { name: `Edit ${ROW_LABEL}` }));
    const input = screen.getByRole("textbox", {
      name: `Working days for ${ROW_LABEL}`,
    });
    await user.clear(input);
    await user.type(input, "25");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(
      await screen.findByText(
        /Changed by someone else\. The current figure is 24/,
      ),
    ).toBeInTheDocument();
    expect(input).toHaveValue("25");
  });

  it("removes a row that someone else already deleted and says so", async () => {
    const user = userEvent.setup();
    mockApi({
      writes: [
        jsonResponse(
          {
            code: "ROW_DELETED",
            reason: "This figure was deleted by someone else.",
          },
          404,
        ),
      ],
    });
    renderPage();
    await screen.findByText("Greenfield School");

    await user.click(screen.getByRole("button", { name: `Edit ${ROW_LABEL}` }));
    const input = screen.getByRole("textbox", {
      name: `Working days for ${ROW_LABEL}`,
    });
    await user.clear(input);
    await user.type(input, "20");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(
      await screen.findByText("That figure was deleted by someone else."),
    ).toBeInTheDocument();
    expect(screen.queryByText("Greenfield School")).toBeNull();
  });

  it("names the School and month in the delete confirmation and removes nothing until confirmed", async () => {
    const user = userEvent.setup();
    mockApi({
      writes: [
        { ok: true, status: 204, json: async () => undefined } as Response,
      ],
    });
    renderPage();
    await screen.findByText("Greenfield School");

    await user.click(
      screen.getByRole("button", { name: `Delete ${ROW_LABEL}` }),
    );
    const dialog = await screen.findByRole("dialog");
    expect(dialog).toHaveTextContent("Greenfield School in March 2027");
    expect(dialog).toHaveTextContent(
      "calendar-derived count (27 working days)",
    );
    expect(dialog).toHaveTextContent(FINALISED_NOTICE);
    expect(calls.some((c) => c.method === "DELETE")).toBe(false);

    await user.click(within(dialog).getByRole("button", { name: "Delete" }));
    await waitFor(() =>
      expect(screen.queryByText("Greenfield School")).toBeNull(),
    );
    expect(calls.find((c) => c.method === "DELETE")?.url).toBe(
      "/api/v1/school-working-days/r1?version=2",
    );
  });

  it("keeps the row when the delete confirmation is cancelled", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Greenfield School");

    await user.click(
      screen.getByRole("button", { name: `Delete ${ROW_LABEL}` }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Cancel" }));

    expect(screen.getByText("Greenfield School")).toBeInTheDocument();
    expect(calls.some((c) => c.method === "DELETE")).toBe(false);
  });

  it("offers to edit the existing row when the School and month already have a figure", async () => {
    const user = userEvent.setup();
    mockApi({
      writes: [
        jsonResponse(
          {
            code: "DUPLICATE",
            reason:
              "A figure already exists for this School and month. Edit it instead.",
            existing: row(),
          },
          409,
        ),
      ],
    });
    renderPage();
    await screen.findByText("Greenfield School");

    await user.click(screen.getByRole("button", { name: "Add working days" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByLabelText(/^School/));
    await user.click(
      await screen.findByRole("option", { name: "Greenfield School" }),
    );
    await user.click(within(dialog).getByLabelText(/^Month/));
    await user.click(await screen.findByRole("option", { name: "March" }));
    await user.type(within(dialog).getByLabelText(/Working days/), "20");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "already exists",
    );
    await user.click(within(dialog).getByRole("button", { name: "Edit it" }));
    expect(
      await screen.findByRole("textbox", {
        name: `Working days for ${ROW_LABEL}`,
      }),
    ).toBeInTheDocument();
  });

  it("keeps the user's entry in the add dialog when the save fails", async () => {
    const user = userEvent.setup();
    mockApi({
      writes: [jsonResponse({ reason: "Year must be 2022 to 2032." }, 400)],
    });
    renderPage();
    await screen.findByText("Greenfield School");

    await user.click(screen.getByRole("button", { name: "Add working days" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByLabelText(/^School/));
    await user.click(
      await screen.findByRole("option", { name: "Greenfield School" }),
    );
    await user.click(within(dialog).getByLabelText(/^Month/));
    await user.click(await screen.findByRole("option", { name: "April" }));
    await user.type(within(dialog).getByLabelText(/Working days/), "22");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "Year must be 2022 to 2032.",
    );
    expect(within(dialog).getByLabelText(/Working days/)).toHaveValue("22");
  });
});
