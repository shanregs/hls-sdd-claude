import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { axe } from "vitest-axe";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../../app/RouteGuard";
import { buildMuiTheme, type ThemeMode } from "../../../theme/tokens";
import { ManagerCheckInPage } from "../ManagerCheckInPage";

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

const MANAGER_NAV = [
  {
    section: "MY ATTENDANCE",
    items: [
      {
        label: "Check-in",
        route: "/manager-attendance/me",
        actions: ["VIEW", "CREATE", "DELETE"],
      },
    ],
  },
];

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const TODAY = {
  date: "2026-10-12",
  workingDay: true,
  missedCutoff: "10:30:00",
  lateAfter: "09:30:00",
  day: null,
  leave: null,
  missingCheckOutDates: [],
  plannedActivities: [],
};

const CHECKED_IN = {
  id: "d1",
  date: "2026-10-12",
  dayType: "OFFICE",
  purpose: null,
  checkInAt: "2026-10-12T04:00:00Z",
  late: false,
  checkOutAt: null,
  hasPhoto: false,
  leave: null,
  flags: [],
};

function mockGeolocation(behaviour: "ok" | "blocked") {
  Object.defineProperty(globalThis.navigator, "geolocation", {
    configurable: true,
    value: {
      getCurrentPosition: (
        ok: (p: unknown) => void,
        fail: (e: { code: number }) => void,
      ) =>
        behaviour === "ok"
          ? ok({ coords: { latitude: 12.97, longitude: 77.59, accuracy: 10 } })
          : fail({ code: 1 }),
    },
  });
}

function renderPage(mode: ThemeMode = "light") {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <MemoryRouter initialEntries={["/manager-attendance/me"]}>
        <Routes>
          <Route
            path="/manager-attendance/me"
            element={
              <RouteGuard>
                <ManagerCheckInPage />
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
  roles = ["MANAGER"];
  navigation = MANAGER_NAV;
  mockGeolocation("ok");
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method === "POST") {
      return String(url).endsWith("/check-in")
        ? json(CHECKED_IN, 201)
        : json(CHECKED_IN);
    }
    return json(TODAY);
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("Manager check-in (spec 032 US1)", () => {
  it("shows the Manager the page and the check-in button", async () => {
    renderPage();
    expect(
      await screen.findByRole("button", { name: "Check in" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Office" })).toBeChecked();
  });

  it.each(["TEACHER", "SYSTEM", "ADMIN", "DIRECTOR"])(
    "shows 'Not authorized' and fetches nothing for %s",
    async (role) => {
      roles = [role];
      navigation = [];
      renderPage();
      expect(await screen.findByText("Not authorized")).toBeInTheDocument();
      expect(authFetch).not.toHaveBeenCalled();
    },
  );

  it("sends the day type and the location on check-in", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("radio", { name: "Field" }));
    await user.click(screen.getByRole("button", { name: "Check in" }));
    await waitFor(() =>
      expect(authFetch).toHaveBeenCalledWith(
        "/api/v1/manager-attendance/me/check-in",
        expect.objectContaining({ method: "POST" }),
      ),
    );
    const call = authFetch.mock.calls.find((c) => c[1]?.method === "POST");
    expect(JSON.parse(call?.[1].body as string)).toMatchObject({
      dayType: "FIELD",
      latitude: 12.97,
      longitude: 77.59,
    });
  });

  it("explains a blocked location and sends nothing", async () => {
    mockGeolocation("blocked");
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("button", { name: "Check in" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      /location is blocked/i,
    );
    expect(authFetch.mock.calls.some((c) => c[1]?.method === "POST")).toBe(
      false,
    );
  });

  it("shows the server's refusal", async () => {
    authFetch.mockImplementation(async (_u: string, init?: RequestInit) =>
      init?.method === "POST"
        ? json({ reason: "You have already checked in today." }, 409)
        : json(TODAY),
    );
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("button", { name: "Check in" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "You have already checked in today.",
    );
  });

  it("offers check-out and a photo once checked in", async () => {
    authFetch.mockImplementation(async () =>
      json({ ...TODAY, day: CHECKED_IN }),
    );
    renderPage();
    expect(
      await screen.findByRole("button", { name: "Check out" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Photo file")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Check in" }),
    ).not.toBeInTheDocument();
  });

  it("asks for a reason when completing a missing check-out", async () => {
    authFetch.mockImplementation(async () =>
      json({ ...TODAY, missingCheckOutDates: ["2026-10-11"] }),
    );
    const user = userEvent.setup();
    renderPage();
    await user.click(
      await screen.findByRole("button", { name: /Complete check-out for/ }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "A reason is required",
    );
  });

  it("shows an error when today cannot be loaded", async () => {
    authFetch.mockResolvedValue(json({ reason: "Could not load today." }, 500));
    renderPage();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load today.",
    );
  });

  it.each(["light", "dark"] as const)(
    "has no accessibility violations in the %s theme",
    async (mode) => {
      const { container } = renderPage(mode);
      await screen.findByRole("button", { name: "Check in" });
      const results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});
