import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ZonesPage } from "./ZonesPage";
import { AuthProvider, useAuth } from "../../auth/AuthContext";

function mockFetchOnce(status: number, body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn(() =>
      Promise.resolve({
        ok: status < 400,
        status,
        json: () => Promise.resolve(body),
      } as Response),
    ),
  );
}

function LogInWithFakeToken() {
  const { setTokens } = useAuth();
  useEffect(() => {
    setTokens({ accessToken: "fake-token", expiresIn: 900 });
  }, [setTokens]);
  return null;
}

function renderAuthenticated() {
  return render(
    <AuthProvider>
      <LogInWithFakeToken />
      <ZonesPage />
    </AuthProvider>,
  );
}

describe("ZonesPage", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("creates a Zone (User Story 1)", async () => {
    mockFetchOnce(200, { id: "zone-1", name: "North Chennai" });
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("zone-name-input"),
      "North Chennai",
    );
    await userEvent.click(screen.getByRole("button", { name: /^create$/i }));

    expect(await screen.findByTestId("create-zone-message")).toHaveTextContent(
      "zone-1",
    );
  });

  it("assigns a School to a Zone (User Story 2)", async () => {
    mockFetchOnce(200, {
      id: "assignment-1",
      zoneId: "zone-1",
      effectiveFrom: "2026-09-22T00:00:00Z",
    });
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("assign-school-id-input"),
      "school-1",
    );
    await userEvent.type(screen.getByTestId("assign-zone-id-input"), "zone-1");
    await userEvent.click(screen.getByRole("button", { name: /^assign$/i }));

    expect(await screen.findByTestId("assign-message")).toHaveTextContent(
      "school-1",
    );
  });

  it("shows the conflict message when a School already has a Zone", async () => {
    mockFetchOnce(409, {
      message:
        "This School's Zone assignment was already changed by someone else. Refresh and retry.",
    });
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("assign-school-id-input"),
      "school-1",
    );
    await userEvent.type(screen.getByTestId("assign-zone-id-input"), "zone-2");
    await userEvent.click(screen.getByRole("button", { name: /^assign$/i }));

    expect(await screen.findByTestId("assign-error")).toHaveTextContent(
      "already changed by someone else",
    );
  });

  it("looks up a Zone's Schools (User Story 3)", async () => {
    mockFetchOnce(200, ["school-1", "school-2"]);
    renderAuthenticated();

    await userEvent.type(screen.getByTestId("lookup-zone-id-input"), "zone-1");
    await userEvent.click(
      screen.getAllByRole("button", { name: /look up/i })[0],
    );

    const rows = await screen.findAllByTestId("zone-school-row");
    expect(rows).toHaveLength(2);
  });

  it("looks up a School's Zone, reporting unassigned when none (User Story 3)", async () => {
    mockFetchOnce(200, { state: "UNASSIGNED" });
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("lookup-school-id-input"),
      "school-3",
    );
    await userEvent.click(
      screen.getAllByRole("button", { name: /look up/i })[1],
    );

    expect(await screen.findByTestId("school-zone-result")).toHaveTextContent(
      "unassigned",
    );
  });

  it("adds a Place to a Zone (specs/008 User Story 1)", async () => {
    mockFetchOnce(200, {
      id: "place-1",
      zoneId: "zone-1",
      name: "Ambattur",
      pincode: "600053",
    });
    renderAuthenticated();

    await userEvent.type(screen.getByTestId("place-zone-id-input"), "zone-1");
    await userEvent.type(screen.getByTestId("place-name-input"), "Ambattur");
    await userEvent.type(screen.getByTestId("place-pincode-input"), "600053");
    await userEvent.click(screen.getByRole("button", { name: /add place/i }));

    expect(await screen.findByTestId("add-place-message")).toHaveTextContent(
      "Ambattur",
    );
  });

  it("finds places by PIN code, showing every matching Zone (specs/008 User Story 2)", async () => {
    mockFetchOnce(200, [
      { id: "place-1", zoneId: "zone-a", name: "Ambattur", pincode: "600053" },
      {
        id: "place-2",
        zoneId: "zone-b",
        name: "Neighboring Village",
        pincode: "600053",
      },
    ]);
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("find-places-value-input"),
      "600053",
    );
    await userEvent.click(screen.getByRole("button", { name: /^find$/i }));

    const rows = await screen.findAllByTestId("found-place-row");
    expect(rows).toHaveLength(2);
  });

  it("finds places by name (specs/008 User Story 2)", async () => {
    mockFetchOnce(200, [
      { id: "place-1", zoneId: "zone-a", name: "Ambattur", pincode: "600053" },
    ]);
    renderAuthenticated();

    await userEvent.click(screen.getByRole("radio", { name: /by name/i }));
    await userEvent.type(
      screen.getByTestId("find-places-value-input"),
      "Ambattur",
    );
    await userEvent.click(screen.getByRole("button", { name: /^find$/i }));

    const rows = await screen.findAllByTestId("found-place-row");
    expect(rows).toHaveLength(1);
  });

  it("reports no match, not an error, for an unrecorded PIN code (specs/008 User Story 2)", async () => {
    mockFetchOnce(200, []);
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("find-places-value-input"),
      "999999",
    );
    await userEvent.click(screen.getByRole("button", { name: /^find$/i }));

    expect(await screen.findByTestId("found-place-empty")).toHaveTextContent(
      "No matching place.",
    );
  });

  it("lists every Place recorded under a Zone (specs/008 User Story 3)", async () => {
    mockFetchOnce(200, [
      { id: "place-1", zoneId: "zone-1", name: "Place One", pincode: "600001" },
      { id: "place-2", zoneId: "zone-1", name: "Place Two", pincode: "600002" },
      {
        id: "place-3",
        zoneId: "zone-1",
        name: "Place Three",
        pincode: "600003",
      },
    ]);
    renderAuthenticated();

    await userEvent.type(screen.getByTestId("zone-places-id-input"), "zone-1");
    await userEvent.click(
      screen.getAllByRole("button", { name: /look up/i })[2],
    );

    const rows = await screen.findAllByTestId("zone-place-row");
    expect(rows).toHaveLength(3);
  });

  it("bulk-imports an all-valid batch, showing the success count (specs/010 User Story 1)", async () => {
    mockFetchOnce(200, {
      results: [
        {
          index: 0,
          succeeded: true,
          place: {
            id: "place-1",
            zoneId: "zone-1",
            name: "Mettupalayam",
            pincode: "641301",
          },
        },
        {
          index: 1,
          succeeded: true,
          place: {
            id: "place-2",
            zoneId: "zone-1",
            name: "Annur",
            pincode: "641653",
          },
        },
      ],
      successCount: 2,
      failureCount: 0,
    });
    renderAuthenticated();

    fireEvent.change(screen.getByTestId("bulk-import-textarea"), {
      target: {
        value: JSON.stringify([
          { zoneId: "zone-1", name: "Mettupalayam", pincode: "641301" },
          { zoneId: "zone-1", name: "Annur", pincode: "641653" },
        ]),
      },
    });
    await userEvent.click(screen.getByRole("button", { name: /^import$/i }));

    expect(await screen.findByTestId("bulk-import-summary")).toHaveTextContent(
      "2 succeeded, 0 failed",
    );
  });

  it("bulk-imports a mixed batch, showing each failed row's reason (specs/010 User Story 2)", async () => {
    mockFetchOnce(200, {
      results: [
        {
          index: 0,
          succeeded: true,
          place: {
            id: "place-1",
            zoneId: "zone-1",
            name: "Valparai",
            pincode: "642127",
          },
        },
        {
          index: 1,
          succeeded: false,
          reason: "zone 00000000-0000-0000-0000-000000000000 does not exist",
        },
      ],
      successCount: 1,
      failureCount: 1,
    });
    renderAuthenticated();

    fireEvent.change(screen.getByTestId("bulk-import-textarea"), {
      target: {
        value: JSON.stringify([
          { zoneId: "zone-1", name: "Valparai", pincode: "642127" },
        ]),
      },
    });
    await userEvent.click(screen.getByRole("button", { name: /^import$/i }));

    expect(await screen.findByTestId("bulk-import-summary")).toHaveTextContent(
      "1 succeeded, 1 failed",
    );
    expect(
      await screen.findByTestId("bulk-import-failure-row"),
    ).toHaveTextContent("does not exist");
  });
});
