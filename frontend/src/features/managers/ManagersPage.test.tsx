import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ManagersPage } from "./ManagersPage";
import { AssignSchoolManagerDialog } from "../schools/AssignSchoolManagerDialog";
import type { SchoolSummary } from "../schools/schoolsApi";

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
              label: "Managers",
              route: "/master-data/managers",
              actions: grantedActions,
            },
          ],
        },
      ],
    },
  }),
}));

const MANOJ = {
  id: "m1",
  userId: "u1",
  displayName: "Manoj Manager",
  phone: "9800000003",
  active: true,
  version: 4,
  zones: [{ id: "z1", name: "North Zone" }],
  schoolCount: 2,
  teacherCount: 5,
};
const OTHER = {
  id: "m2",
  userId: "u2",
  displayName: "Olga Other",
  phone: "9800000009",
  active: true,
  version: 0,
  zones: [{ id: "z2", name: "South Zone" }],
  schoolCount: 0,
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
  managers: unknown[],
  writeResponse: Response = jsonResponse(null, 204),
) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method) return writeResponse;
    if (url.startsWith("/api/v1/managers/candidates")) {
      return jsonResponse([
        { userId: "u7", displayName: "Nina New", phone: "9800000007" },
      ]);
    }
    if (url.startsWith("/api/v1/zones")) {
      return page([
        {
          id: "z1",
          name: "North Zone",
          version: 0,
          placeCount: 1,
          schoolCount: 1,
        },
        {
          id: "z2",
          name: "South Zone",
          version: 0,
          placeCount: 1,
          schoolCount: 0,
        },
      ]);
    }
    return page(managers);
  });
}

describe("ManagersPage (User Story 3)", () => {
  beforeEach(() => {
    authFetch.mockReset();
    grantedActions = ["VIEW", "CREATE", "EDIT"];
  });

  it("lists managers with zone chips and counts", async () => {
    mockApi([MANOJ]);

    render(<ManagersPage />);

    expect(
      (await screen.findAllByText("Manoj Manager")).length,
    ).toBeGreaterThan(0);
    expect(screen.getAllByText("North Zone").length).toBeGreaterThan(0);
    expect(screen.getAllByText("5").length).toBeGreaterThan(0);
  });

  it("shows an empty state and an error alert", async () => {
    mockApi([]);
    const user = userEvent.setup();
    const { unmount } = render(<ManagersPage />);
    await user.type(screen.getByLabelText(/search managers/i), "zzz");
    expect(
      await screen.findByText(/no managers match your search/i),
    ).toBeInTheDocument();
    unmount();

    authFetch.mockReset();
    authFetch.mockResolvedValue(jsonResponse({}, 500));
    render(<ManagersPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      /could not load managers/i,
    );
  });

  it("creates a manager from the users who hold the Manager role", async () => {
    mockApi([]);
    const user = userEvent.setup();

    render(<ManagersPage />);
    await user.click(
      await screen.findByRole("button", { name: /create manager/i }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByLabelText(/^user/i));
    await user.click(await screen.findByRole("option", { name: /nina new/i }));
    await user.click(within(dialog).getByRole("button", { name: /^create$/i }));

    const post = authFetch.mock.calls.find(
      (c) => (c[1] as RequestInit | undefined)?.method === "POST",
    )!;
    expect(String(post[0])).toBe("/api/v1/managers");
    expect(JSON.parse((post[1] as RequestInit).body as string)).toEqual({
      userId: "u7",
    });
  });

  it("assigns zones and shows the refusal naming the stranded schools inline", async () => {
    mockApi(
      [MANOJ],
      jsonResponse(
        {
          reason:
            "This Manager still has Schools in that Zone: St Mary's. Reassign them first.",
        },
        409,
      ),
    );
    const user = userEvent.setup();

    render(<ManagersPage />);
    await screen.findAllByText("Manoj Manager");
    await user.click(
      screen.getAllByRole("button", { name: /^Edit zones of / })[0],
    );
    const dialog = await screen.findByRole("dialog");
    await user.click(
      await within(dialog).findByRole("checkbox", { name: "North Zone" }),
    );
    await user.click(within(dialog).getByRole("button", { name: /^save$/i }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      /still has schools in that zone/i,
    );
    const put = authFetch.mock.calls.find(
      (c) => (c[1] as RequestInit | undefined)?.method === "PUT",
    )!;
    expect(String(put[0])).toBe("/api/v1/managers/m1/zones");
    expect(JSON.parse((put[1] as RequestInit).body as string)).toEqual({
      zoneIds: [],
      version: 4,
    });
  });

  it("hides create and zone actions without CREATE and EDIT", async () => {
    grantedActions = ["VIEW"];
    mockApi([MANOJ]);

    render(<ManagersPage />);
    await screen.findAllByText("Manoj Manager");

    expect(
      screen.queryByRole("button", { name: /create manager/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /^Edit zones of / }),
    ).not.toBeInTheDocument();
  });
});

describe("AssignSchoolManagerDialog (FR-009)", () => {
  const school: SchoolSummary = {
    id: "s1",
    name: "St Mary's",
    place: { id: "p1", name: "Madurantakam", pinCode: "603306" },
    zone: { id: "z1", name: "North Zone" },
    address: "1 Main Road",
    contactPerson: null,
    contactPhone: null,
    billingContact: null,
    active: true,
    version: 0,
    manager: null,
  };

  beforeEach(() => {
    authFetch.mockReset();
  });

  it("offers only active managers covering the school's zone and shows the server refusal inline", async () => {
    mockApi(
      [MANOJ, OTHER],
      jsonResponse(
        {
          reason: "This Manager does not cover the School's Zone (North Zone).",
        },
        409,
      ),
    );
    const user = userEvent.setup();

    render(
      <AssignSchoolManagerDialog
        school={school}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    );
    await user.click(await screen.findByRole("combobox"));
    expect(
      await screen.findByRole("option", { name: "Manoj Manager" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("option", { name: "Olga Other" }),
    ).not.toBeInTheDocument();
    await user.click(screen.getByRole("option", { name: "Manoj Manager" }));
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /does not cover/i,
    );
    const put = authFetch.mock.calls.find(
      (c) => (c[1] as RequestInit | undefined)?.method === "PUT",
    )!;
    expect(String(put[0])).toBe("/api/v1/schools/s1/manager");
    expect(JSON.parse((put[1] as RequestInit).body as string)).toEqual({
      managerId: "m1",
    });
  });
});
