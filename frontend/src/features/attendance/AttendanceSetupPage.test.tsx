import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AttendanceSetupPage } from "./AttendanceSetupPage";

const authFetch = vi.fn();
let grantedActions = ["VIEW", "EDIT"];

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch, user: { id: "me", displayName: "Me" } }),
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
              label: "Attendance Setup",
              route: "/operations/attendance-setup",
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

const CODES = [
  {
    id: "c1",
    shortCode: "P",
    name: "Present",
    category: "WORKED",
    weight: 1,
    active: true,
    system: true,
    inUse: true,
    version: 0,
  },
  {
    id: "c2",
    shortCode: "SICK",
    name: "Sick leave",
    category: "LEAVE",
    weight: 0,
    active: true,
    system: false,
    inUse: false,
    version: 0,
  },
];

const CALENDAR = {
  defaultWeeklyOff: ["SUN"],
  defaultVersion: 0,
  schoolOverrides: [],
  nonWorkingDates: [{ date: "2026-10-02", description: "Gandhi Jayanti" }],
};

function mockApi(writeResponse: Response = json(CALENDAR)) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method) return writeResponse;
    if (url.startsWith("/api/v1/attendance/status-codes")) return json(CODES);
    if (url === "/api/v1/attendance/calendar") return json(CALENDAR);
    return json({ content: [], page: 0, size: 100, totalElements: 0 });
  });
}

describe("AttendanceSetupPage (User Story 5)", () => {
  beforeEach(() => {
    authFetch.mockReset();
    grantedActions = ["VIEW", "EDIT"];
  });

  it("lists the status codes", async () => {
    mockApi();
    render(<AttendanceSetupPage />);

    expect(await screen.findByText("Sick leave")).toBeInTheDocument();
    expect(screen.getByText("Built-in")).toBeInTheDocument();
  });

  it("shows edit controls when EDIT is granted", async () => {
    mockApi();
    render(<AttendanceSetupPage />);

    expect(
      await screen.findByRole("button", { name: "Add status code" }),
    ).toBeInTheDocument();
  });

  it("hides every edit control for a view-only grant", async () => {
    grantedActions = ["VIEW"];
    mockApi();
    render(<AttendanceSetupPage />);

    await screen.findByText("Sick leave");
    expect(
      screen.queryByRole("button", { name: "Add status code" }),
    ).toBeNull();
    expect(screen.queryByRole("button", { name: /edit/i })).toBeNull();
  });

  it("shows the server refusal reason when adding a duplicate code", async () => {
    mockApi(
      json(
        { reason: "A status code with the short code X already exists." },
        409,
      ),
    );
    render(<AttendanceSetupPage />);
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "Add status code" }),
    );
    await user.type(screen.getByLabelText(/short code/i), "X");
    await user.type(screen.getByLabelText(/^name/i), "Extra");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "already exists",
    );
  });

  it("validates that the short code and name are required", async () => {
    mockApi();
    render(<AttendanceSetupPage />);
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "Add status code" }),
    );
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(
      await screen.findByText("The short code is required."),
    ).toBeInTheDocument();
    expect(screen.getByText("The name is required.")).toBeInTheDocument();
  });
});
