import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ZonesPage } from "./ZonesPage";

const authFetch = vi.fn();
let grantedActions = ["VIEW", "CREATE", "EDIT", "DELETE"];

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
              label: "Zones",
              route: "/master-data/zones",
              actions: grantedActions,
            },
          ],
        },
      ],
    },
  }),
}));

const NORTH = {
  id: "z1",
  name: "North Zone",
  version: 0,
  placeCount: 2,
  schoolCount: 1,
  managerCount: 1,
};

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function page<T>(content: T[]) {
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

/** GETs return the given bodies by URL prefix; writes return `writeResponse`. */
function mockApi(
  zones: unknown[],
  writeResponse: Response = jsonResponse(null, 204),
  places: unknown[] = [],
) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method) return writeResponse;
    if (url.includes("/places?")) return page(places);
    if (url.startsWith("/api/v1/places")) return jsonResponse([]);
    return page(zones);
  });
}

describe("ZonesPage (User Story 1)", () => {
  beforeEach(() => {
    authFetch.mockReset();
    grantedActions = ["VIEW", "CREATE", "EDIT", "DELETE"];
  });

  it("lists zones with their counts", async () => {
    mockApi([NORTH]);

    render(<ZonesPage />);

    expect((await screen.findAllByText("North Zone")).length).toBeGreaterThan(
      0,
    );
    expect(screen.getAllByText("2").length).toBeGreaterThan(0);
  });

  it("shows an empty state when nothing matches and an error alert when loading fails", async () => {
    mockApi([]);
    const user = userEvent.setup();
    const { unmount } = render(<ZonesPage />);
    await user.type(screen.getByLabelText(/search zones/i), "zzz");
    expect(
      await screen.findByText(/no zones match your search/i),
    ).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    unmount();

    authFetch.mockReset();
    authFetch.mockResolvedValue(jsonResponse({}, 500));
    render(<ZonesPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      /could not load zones/i,
    );
  });

  it("creates a zone and shows the duplicate-name reason inline", async () => {
    mockApi(
      [],
      jsonResponse({ reason: "A Zone named North Zone already exists." }, 409),
    );
    const user = userEvent.setup();

    render(<ZonesPage />);
    await user.click(
      await screen.findByRole("button", { name: /create zone/i }),
    );
    await user.type(screen.getByLabelText(/zone name/i), "North Zone");
    await user.click(screen.getByRole("button", { name: /^create$/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "already exists",
    );
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("renames a zone sending the loaded version", async () => {
    mockApi([NORTH], jsonResponse({ ...NORTH, name: "North 2", version: 1 }));
    const user = userEvent.setup();

    render(<ZonesPage />);
    await screen.findAllByText("North Zone");
    await user.click(screen.getAllByRole("button", { name: /^Edit / })[0]);
    const input = screen.getByLabelText(/zone name/i);
    await user.clear(input);
    await user.type(input, "North 2");
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    const put = authFetch.mock.calls.find(
      (c) => (c[1] as RequestInit | undefined)?.method === "PUT",
    )!;
    expect(urlOf(put)).toBe("/api/v1/zones/z1");
    expect(JSON.parse((put[1] as RequestInit).body as string)).toEqual({
      name: "North 2",
      version: 0,
    });
  });

  it("shows the refusal reason when a zone cannot be deleted", async () => {
    mockApi(
      [NORTH],
      jsonResponse(
        {
          reason:
            "This Zone cannot be deleted: it still has 2 Place(s) and 1 School(s).",
        },
        409,
      ),
    );
    const user = userEvent.setup();

    render(<ZonesPage />);
    await screen.findAllByText("North Zone");
    await user.click(screen.getAllByRole("button", { name: /^Delete / })[0]);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "still has 2 Place(s)",
    );
  });

  it("opens the places panel, adds a place and validates the PIN code", async () => {
    mockApi([NORTH], jsonResponse({ id: "p1" }, 201), [
      {
        id: "p1",
        name: "Madurantakam",
        pinCode: "603306",
        zoneId: "z1",
        zoneName: "North Zone",
      },
    ]);
    const user = userEvent.setup();

    render(<ZonesPage />);
    await screen.findAllByText("North Zone");
    await user.click(screen.getAllByRole("button", { name: /^View / })[0]);
    expect((await screen.findAllByText("Madurantakam")).length).toBeGreaterThan(
      0,
    );

    await user.click(screen.getByRole("button", { name: /add place/i }));
    const dialog = await screen.findByRole("dialog");
    await user.type(
      within(dialog).getByLabelText(/place name/i),
      "Maraimalai Nagar",
    );
    await user.type(within(dialog).getByLabelText(/pin code/i), "12");
    await user.click(within(dialog).getByRole("button", { name: /^add$/i }));
    expect(
      await within(dialog).findByText(/pin code must be six digits/i),
    ).toBeInTheDocument();

    const pin = within(dialog).getByLabelText(/pin code/i);
    await user.clear(pin);
    await user.type(pin, "603209");
    await user.click(within(dialog).getByRole("button", { name: /^add$/i }));
    const post = authFetch.mock.calls.find(
      (c) => (c[1] as RequestInit | undefined)?.method === "POST",
    )!;
    expect(urlOf(post)).toBe("/api/v1/zones/z1/places");
    expect(JSON.parse((post[1] as RequestInit).body as string)).toEqual({
      name: "Maraimalai Nagar",
      pinCode: "603209",
    });
  });

  it("looks a place up by PIN code and shows its zone, or says none match", async () => {
    authFetch.mockImplementation(async (url: string) => {
      if (url.startsWith("/api/v1/places?pinCode=603306")) {
        return jsonResponse([
          {
            id: "p1",
            name: "Madurantakam",
            pinCode: "603306",
            zoneId: "z1",
            zoneName: "North Zone",
          },
        ]);
      }
      if (url.startsWith("/api/v1/places?")) return jsonResponse([]);
      return page([NORTH]);
    });
    const user = userEvent.setup();

    render(<ZonesPage />);
    await user.type(screen.getByLabelText(/^pin code$/i), "603306");
    await user.click(screen.getByRole("button", { name: /^find$/i }));
    expect(await screen.findByText(/zone: north zone/i)).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/^pin code$/i));
    await user.type(screen.getByLabelText(/^pin code$/i), "999999");
    await user.click(screen.getByRole("button", { name: /^find$/i }));
    expect(await screen.findByText(/no places match\./i)).toBeInTheDocument();
  });

  it("hides create, rename and delete when only VIEW is granted", async () => {
    grantedActions = ["VIEW"];
    mockApi([NORTH]);

    render(<ZonesPage />);
    await screen.findAllByText("North Zone");

    expect(
      screen.queryByRole("button", { name: /create zone/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /^Edit / }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /^Delete / }),
    ).not.toBeInTheDocument();
    expect(
      screen.getAllByRole("button", { name: /^View / }).length,
    ).toBeGreaterThan(0);
  });

  it("hides delete for a director who has EDIT but not DELETE", async () => {
    grantedActions = ["VIEW", "CREATE", "EDIT"];
    mockApi([NORTH]);

    render(<ZonesPage />);
    await screen.findAllByText("North Zone");

    expect(
      screen.getAllByRole("button", { name: /^Edit / }).length,
    ).toBeGreaterThan(0);
    expect(
      screen.queryByRole("button", { name: /^Delete / }),
    ).not.toBeInTheDocument();
  });
});
