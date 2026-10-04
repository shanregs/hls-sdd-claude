import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SchoolsPage } from "./SchoolsPage";
import type { SchoolSummary } from "./schoolsApi";

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
              label: "Schools",
              route: "/master-data/schools",
              actions: grantedActions,
            },
          ],
        },
      ],
    },
  }),
}));

const STMARY: SchoolSummary = {
  id: "s1",
  name: "St Mary's",
  place: { id: "p1", name: "Madurantakam", pinCode: "603306" },
  zone: { id: "z1", name: "North Zone" },
  address: "1 Main Road",
  contactPerson: "Head",
  contactPhone: "9000000000",
  billingContact: "bill@example.com",
  active: true,
  version: 3,
  manager: null,
  teacherCount: 2,
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

function urlOf(call: unknown[]): string {
  return String(call[0]);
}

function mockApi(
  schools: unknown[],
  writeResponse: Response = jsonResponse(null, 204),
) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method) return writeResponse;
    if (url.startsWith("/api/v1/places?")) {
      return jsonResponse([
        {
          id: "p2",
          name: "Maraimalai Nagar",
          pinCode: "603209",
          zoneId: "z2",
          zoneName: "South Zone",
        },
      ]);
    }
    return page(schools);
  });
}

describe("SchoolsPage (User Story 2)", () => {
  beforeEach(() => {
    authFetch.mockReset();
    grantedActions = ["VIEW", "CREATE", "EDIT"];
  });

  it("lists schools with place, zone and a needs-manager flag", async () => {
    mockApi([STMARY]);

    render(<SchoolsPage />);

    expect((await screen.findAllByText("St Mary's")).length).toBeGreaterThan(0);
    expect(
      screen.getAllByText(/madurantakam \(603306\)/i).length,
    ).toBeGreaterThan(0);
    expect(screen.getAllByText("North Zone").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Needs a Manager").length).toBeGreaterThan(0);
  });

  it("shows an empty state when nothing matches and an error alert when loading fails", async () => {
    mockApi([]);
    const user = userEvent.setup();
    const { unmount } = render(<SchoolsPage />);
    await user.type(screen.getByLabelText(/search schools/i), "zzz");
    expect(
      await screen.findByText(/no schools match your search/i),
    ).toBeInTheDocument();
    unmount();

    authFetch.mockReset();
    authFetch.mockResolvedValue(jsonResponse({}, 500));
    render(<SchoolsPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      /could not load schools/i,
    );
  });

  it("requires a place when creating a school", async () => {
    mockApi([]);
    const user = userEvent.setup();

    render(<SchoolsPage />);
    await user.click(
      await screen.findByRole("button", { name: /create school/i }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.type(
      within(dialog).getByLabelText(/school name/i),
      "New School",
    );
    await user.type(within(dialog).getByLabelText(/address/i), "Road 1");
    await user.click(within(dialog).getByRole("button", { name: /^create$/i }));

    expect(
      await within(dialog).findByText(
        /choose the place this school is located in/i,
      ),
    ).toBeInTheDocument();
    expect(
      authFetch.mock.calls.some(
        (c) => (c[1] as RequestInit | undefined)?.method === "POST",
      ),
    ).toBe(false);
  });

  it("edits a school sending the loaded version and shows a stale-version conflict inline", async () => {
    mockApi(
      [STMARY],
      jsonResponse(
        {
          reason:
            "This record was changed by someone else. Reload and try again.",
        },
        409,
      ),
    );
    const user = userEvent.setup();

    render(<SchoolsPage />);
    await screen.findAllByText("St Mary's");
    await user.click(screen.getAllByRole("button", { name: /^Edit / })[0]);
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: /^save$/i }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      /changed by someone else/i,
    );
    const put = authFetch.mock.calls.find(
      (c) => (c[1] as RequestInit | undefined)?.method === "PUT",
    )!;
    expect(urlOf(put)).toBe("/api/v1/schools/s1");
    expect(JSON.parse((put[1] as RequestInit).body as string)).toMatchObject({
      version: 3,
    });
  });

  it("moves a school to a place and shows the refusal reason inline", async () => {
    mockApi(
      [STMARY],
      jsonResponse(
        { reason: "The School's Manager must cover the School's Zone." },
        409,
      ),
    );
    const user = userEvent.setup();

    render(<SchoolsPage />);
    await screen.findAllByText("St Mary's");
    await user.click(screen.getAllByRole("button", { name: "Move" })[0]);
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(/place/i), "Mara");
    await user.click(await screen.findByText(/maraimalai nagar - 603209/i));
    await user.click(within(dialog).getByRole("button", { name: /^move$/i }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      /must cover the school's zone/i,
    );
  });

  it("deactivates a school and shows a refusal reason", async () => {
    mockApi(
      [STMARY],
      jsonResponse(
        { reason: "This School still has Teachers placed in it." },
        409,
      ),
    );
    const user = userEvent.setup();

    render(<SchoolsPage />);
    await screen.findAllByText("St Mary's");
    await user.click(screen.getAllByRole("button", { name: "Deactivate" })[0]);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /still has teachers/i,
    );
  });

  it("shows a Manager name/place/billing read-only and hides create, move and deactivate", async () => {
    grantedActions = ["VIEW", "EDIT"];
    mockApi([{ ...STMARY, manager: { id: "m1", displayName: "Manoj" } }]);
    const user = userEvent.setup();

    render(<SchoolsPage />);
    await screen.findAllByText("St Mary's");

    expect(
      screen.queryByRole("button", { name: /create school/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Move" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Deactivate" }),
    ).not.toBeInTheDocument();

    await user.click(screen.getAllByRole("button", { name: /^Edit / })[0]);
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByLabelText(/school name/i)).toBeDisabled();
    expect(within(dialog).getByLabelText(/billing contact/i)).toBeDisabled();
    expect(within(dialog).getByLabelText(/contact person/i)).toBeEnabled();
    expect(within(dialog).getByLabelText(/contact phone/i)).toBeEnabled();
    expect(within(dialog).getByLabelText(/address/i)).toBeEnabled();
  });

  it("hides every write control when only VIEW is granted", async () => {
    grantedActions = ["VIEW"];
    mockApi([STMARY]);

    render(<SchoolsPage />);
    await screen.findAllByText("St Mary's");

    expect(
      screen.queryByRole("button", { name: /^Edit / }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /create school/i }),
    ).not.toBeInTheDocument();
  });
});
