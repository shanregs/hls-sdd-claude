import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TeachersPage } from "./TeachersPage";
import type { TeacherSummary } from "./teachersApi";

const authFetch = vi.fn();
let grantedActions = ["VIEW", "CREATE", "EDIT"];

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
          section: "MASTER DATA",
          items: [
            {
              label: "Teachers",
              route: "/master-data/teachers",
              actions: grantedActions,
            },
          ],
        },
      ],
    },
  }),
}));

const TARA: TeacherSummary = {
  id: "t1",
  name: "Tara Teacher",
  phone: "9800000004",
  email: null,
  address: null,
  status: "ACTIVE",
  statusEffectiveOn: "2026-04-01",
  allowedNextStatuses: ["EXITED", "ON_LEAVE"],
  userId: null,
  version: 2,
  school: { id: "s1", name: "St Mary's" },
  manager: { id: "m1", displayName: "Manoj Manager" },
  pendingPlacement: {
    schoolId: "s2",
    schoolName: "Holy Cross",
    startsOn: "2026-11-15",
  },
  placements: null,
};

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function page(content: unknown[]) {
  return jsonResponse({
    content,
    page: 0,
    size: 25,
    totalElements: content.length,
  });
}

function mockApi(
  teachers: unknown[],
  writeResponse: Response = jsonResponse(TARA),
) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method) return writeResponse;
    if (url.startsWith("/api/v1/schools")) {
      return page([
        {
          id: "s2",
          name: "Holy Cross",
          zone: { id: "z1", name: "North Zone" },
          place: { id: "p1", name: "P", pinCode: "603306" },
          active: true,
        },
      ]);
    }
    return page(teachers);
  });
}

describe("TeachersPage (User Story 4)", () => {
  beforeEach(() => {
    authFetch.mockReset();
    grantedActions = ["VIEW", "CREATE", "EDIT"];
  });

  it("lists teachers with status, interim placement, scheduled move and manager", async () => {
    mockApi([TARA]);

    render(<TeachersPage />);

    expect((await screen.findAllByText("Tara Teacher")).length).toBeGreaterThan(
      0,
    );
    expect(screen.getAllByText("Active").length).toBeGreaterThan(0);
    expect(screen.getAllByText("St Mary's").length).toBeGreaterThan(0);
    expect(
      screen.getAllByText(/scheduled: holy cross from 15\/11\/2026/i).length,
    ).toBeGreaterThan(0);
    expect(screen.getAllByText("Manoj Manager").length).toBeGreaterThan(0);
    expect(screen.getAllByText(/interim placement/i).length).toBeGreaterThan(0);
  });

  it("shows an empty state and an error alert", async () => {
    mockApi([]);
    const user = userEvent.setup();
    const { unmount } = render(<TeachersPage />);
    await user.type(screen.getByLabelText(/search teachers/i), "zzz");
    expect(
      await screen.findByText(/no teachers match your search/i),
    ).toBeInTheDocument();
    unmount();

    authFetch.mockReset();
    authFetch.mockResolvedValue(jsonResponse({}, 500));
    render(<TeachersPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      /could not load teachers/i,
    );
  });

  it("creates a teacher and shows the server validation inline", async () => {
    mockApi([], jsonResponse({ reason: "Teacher name is required." }, 400));
    const user = userEvent.setup();

    render(<TeachersPage />);
    await user.click(
      await screen.findByRole("button", { name: /create teacher/i }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(/teacher name/i), "Nina");
    await user.click(within(dialog).getByRole("button", { name: /^create$/i }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      /name is required/i,
    );
    const post = authFetch.mock.calls.find(
      (c) => (c[1] as RequestInit | undefined)?.method === "POST",
    )!;
    expect(String(post[0])).toBe("/api/v1/teachers");
    expect(JSON.parse((post[1] as RequestInit).body as string)).toMatchObject({
      name: "Nina",
      status: "IN_TRAINING",
    });
  });

  it("offers only the allowed next statuses and warns that exit is final", async () => {
    mockApi([TARA]);
    const user = userEvent.setup();

    render(<TeachersPage />);
    await screen.findAllByText("Tara Teacher");
    await user.click(screen.getAllByRole("button", { name: "Status" })[0]);
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByLabelText(/new status/i));
    const options = await screen.findAllByRole("option");
    expect(options.map((o) => o.textContent)).toEqual(["Exited", "On leave"]);
    await user.click(screen.getByRole("option", { name: "Exited" }));

    expect(within(dialog).getByText(/exit is final/i)).toBeInTheDocument();
    await user.click(
      within(dialog).getByRole("button", { name: /change status/i }),
    );
    const post = authFetch.mock.calls.find(
      (c) => (c[1] as RequestInit | undefined)?.method === "POST",
    )!;
    expect(String(post[0])).toBe("/api/v1/teachers/t1/status");
    expect(JSON.parse((post[1] as RequestInit).body as string)).toMatchObject({
      status: "EXITED",
    });
  });

  it("places a teacher with an effective date, shows the scheduled move and refusals inline", async () => {
    mockApi(
      [TARA],
      jsonResponse(
        {
          reason:
            "The date cannot be earlier than the start of the current placement (2026-04-01).",
        },
        409,
      ),
    );
    const user = userEvent.setup();

    render(<TeachersPage />);
    await screen.findAllByText("Tara Teacher");
    await user.click(screen.getAllByRole("button", { name: "Placement" })[0]);
    const dialog = await screen.findByRole("dialog");
    expect(
      within(dialog).getByText(/scheduled: holy cross from 15\/11\/2026/i),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole("button", { name: /cancel scheduled move/i }),
    ).toBeInTheDocument();
    await user.click(within(dialog).getByLabelText(/^school/i));
    await user.click(
      await screen.findByRole("option", { name: /holy cross/i }),
    );
    await user.click(
      within(dialog).getByRole("button", { name: /save placement/i }),
    );

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      /earlier than the start/i,
    );
    const post = authFetch.mock.calls.find(
      (c) => (c[1] as RequestInit | undefined)?.method === "POST",
    )!;
    expect(String(post[0])).toBe("/api/v1/teachers/t1/placements");
    expect(JSON.parse((post[1] as RequestInit).body as string)).toMatchObject({
      schoolId: "s2",
    });
  });

  it("cancels a scheduled move", async () => {
    mockApi([TARA], jsonResponse(null, 204));
    const user = userEvent.setup();

    render(<TeachersPage />);
    await screen.findAllByText("Tara Teacher");
    await user.click(screen.getAllByRole("button", { name: "Placement" })[0]);
    const dialog = await screen.findByRole("dialog");
    await user.click(
      within(dialog).getByRole("button", { name: /cancel scheduled move/i }),
    );

    const del = authFetch.mock.calls.find(
      (c) => (c[1] as RequestInit | undefined)?.method === "DELETE",
    )!;
    expect(String(del[0])).toBe("/api/v1/teachers/t1/placements/pending");
  });

  it("gives a Manager contact editing only: no create, status or placement controls and a read-only name", async () => {
    grantedActions = ["VIEW", "EDIT"];
    mockApi([TARA]);
    const user = userEvent.setup();

    render(<TeachersPage />);
    await screen.findAllByText("Tara Teacher");

    expect(
      screen.queryByRole("button", { name: /create teacher/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Status" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Placement" }),
    ).not.toBeInTheDocument();
    await user.click(screen.getAllByRole("button", { name: "Edit" })[0]);
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByLabelText(/teacher name/i)).toBeDisabled();
    expect(within(dialog).getByLabelText(/phone/i)).toBeEnabled();
  });

  it("hides every write control when only VIEW is granted", async () => {
    grantedActions = ["VIEW"];
    mockApi([TARA]);

    render(<TeachersPage />);
    await screen.findAllByText("Tara Teacher");

    expect(
      screen.queryByRole("button", { name: "Edit" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /create teacher/i }),
    ).not.toBeInTheDocument();
  });
});
