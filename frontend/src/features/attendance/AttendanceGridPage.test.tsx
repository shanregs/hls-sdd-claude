import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AttendanceGridPage } from "./AttendanceGridPage";
import { currentMonth, dateKey, daysInMonth, todayKey } from "./monthUtils";

const authFetch = vi.fn();
let grantedActions = ["VIEW", "CREATE", "EDIT", "DELETE", "PROCESS", "EXPORT"];

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch, user: { id: "me", displayName: "Asha" } }),
}));

vi.mock("../../access-model/useAccessModel", () => ({
  useAccessModel: () => ({
    loading: false,
    accessModel: {
      roles: ["ADMIN"],
      dataScope: {},
      navigation: [
        {
          section: "OPERATIONS",
          items: [
            {
              label: "Attendance",
              route: "/operations/attendance",
              actions: grantedActions,
            },
          ],
        },
      ],
    },
  }),
}));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const ROLLUP = {
  workingDays: 26,
  daysWorked: 0,
  daysLeave: 0,
  trainingAvailable: 0,
  trainingAttended: 0,
  unmarked: 3,
  weightedTotal: 0,
  locked: false,
  frozen: false,
};

function grid() {
  const month = currentMonth();
  const today = todayKey();
  const cells = Array.from({ length: daysInMonth(month) }, (_, i) => {
    const date = dateKey(month, i + 1);
    return {
      date,
      code: null,
      dayValue: null,
      setByKind: null,
      state: date > today ? "FUTURE" : "UNMARKED",
    };
  });
  return {
    month,
    days: cells.length,
    page: 0,
    size: 50,
    totalElements: 1,
    content: [
      {
        teacherId: "t1",
        name: "Tara Teacher",
        status: "ACTIVE",
        school: { id: "s1", name: "Demo School One" },
        manager: { id: "m1", name: "Manoj" },
        locked: false,
        rollup: ROLLUP,
        cells,
      },
    ],
  };
}

function page<T>(content: T[]) {
  return json({ content, page: 0, size: 100, totalElements: content.length });
}

function mockApi() {
  authFetch.mockImplementation(async (url: string) => {
    if (url.startsWith("/api/v1/attendance/grid")) return json(grid());
    if (url.startsWith("/api/v1/zones"))
      return page([{ id: "z1", name: "North Zone" }]);
    if (url.startsWith("/api/v1/schools"))
      return page([{ id: "s1", name: "Demo School One" }]);
    if (url.startsWith("/api/v1/managers"))
      return page([{ id: "m1", displayName: "Manoj" }]);
    return json({});
  });
}

describe("AttendanceGridPage (User Story 4)", () => {
  beforeEach(() => {
    authFetch.mockReset();
    grantedActions = ["VIEW", "CREATE", "EDIT", "DELETE", "PROCESS", "EXPORT"];
  });

  it("loads the organization grid", async () => {
    mockApi();
    render(<AttendanceGridPage />);

    expect(await screen.findByText("Tara Teacher")).toBeInTheDocument();
    expect(screen.getByText(/Demo School One/)).toBeInTheDocument();
  });

  it("sends the chosen School filter with the request", async () => {
    mockApi();
    render(<AttendanceGridPage />);
    const user = userEvent.setup();

    await screen.findByText("Tara Teacher");
    await user.click(screen.getByLabelText("School"));
    await user.click(
      await screen.findByRole("option", { name: "Demo School One" }),
    );

    await vi.waitFor(() => {
      const urls = authFetch.mock.calls.map((c) => String(c[0]));
      expect(
        urls.some(
          (u) => u.includes("/attendance/grid") && u.includes("schoolId=s1"),
        ),
      ).toBe(true);
    });
  });

  describe("CSV export (User Story 8)", () => {
    const createObjectURL = vi.fn(() => "blob:csv");
    const revokeObjectURL = vi.fn();
    const downloads: string[] = [];

    /** The normal grid mocks, plus the given response for the export request. */
    function mockApiWithExport(exportResponse: Response) {
      mockApi();
      const base = authFetch.getMockImplementation()!;
      authFetch.mockImplementation(async (url: string, init?: RequestInit) =>
        url.startsWith("/api/v1/attendance/export")
          ? exportResponse
          : base(url, init),
      );
    }

    beforeEach(() => {
      createObjectURL.mockClear();
      revokeObjectURL.mockClear();
      URL.createObjectURL = createObjectURL;
      URL.revokeObjectURL = revokeObjectURL;
      downloads.length = 0;
      vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(
        function (this: HTMLAnchorElement) {
          downloads.push(this.download);
        },
      );
    });

    afterEach(() => vi.restoreAllMocks());

    it("downloads the filtered month with the grid's filters", async () => {
      mockApiWithExport({
        ok: true,
        status: 200,
        blob: async () => new Blob(["Teacher"]),
      } as unknown as Response);
      render(<AttendanceGridPage />);
      const user = userEvent.setup();

      await screen.findByText("Tara Teacher");
      await user.click(screen.getByLabelText("School"));
      await user.click(
        await screen.findByRole("option", { name: "Demo School One" }),
      );
      await user.click(screen.getByRole("button", { name: "Export CSV" }));

      await vi.waitFor(() => expect(createObjectURL).toHaveBeenCalled());
      const exportCall = authFetch.mock.calls
        .map((c) => String(c[0]))
        .find((u) => u.startsWith("/api/v1/attendance/export"));
      expect(exportCall).toContain(`month=${currentMonth()}`);
      expect(exportCall).toContain("schoolId=s1");
      expect(exportCall).not.toContain("page=");
      expect(downloads).toEqual([`attendance-${currentMonth()}.csv`]);
    });

    it("shows the refusal when the export fails", async () => {
      mockApiWithExport(json({ reason: "Not authorized" }, 403));
      render(<AttendanceGridPage />);

      await screen.findByText("Tara Teacher");
      await userEvent
        .setup()
        .click(screen.getByRole("button", { name: "Export CSV" }));

      expect(await screen.findByRole("alert")).toHaveTextContent(
        "Not authorized",
      );
      expect(createObjectURL).not.toHaveBeenCalled();
    });

    it("hides the button without the EXPORT grant", async () => {
      grantedActions = ["VIEW", "EDIT"];
      mockApi();
      render(<AttendanceGridPage />);

      await screen.findByText("Tara Teacher");
      expect(screen.queryByRole("button", { name: "Export CSV" })).toBeNull();
    });
  });

  it("shows the server refusal for a caller without access", async () => {
    authFetch.mockImplementation(async () =>
      json({ reason: "Not authorized" }, 403),
    );
    render(<AttendanceGridPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Not authorized",
    );
  });
});
