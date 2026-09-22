import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AssignmentsPage } from "./AssignmentsPage";
import { AuthProvider, useAuth } from "../../auth/AuthContext";

function mockFetchSequence(
  responses: Array<{ status: number; body: unknown }>,
) {
  let call = 0;
  vi.stubGlobal(
    "fetch",
    vi.fn(() => {
      const { status, body } = responses[Math.min(call, responses.length - 1)];
      call += 1;
      return Promise.resolve({
        ok: status < 400,
        status,
        json: () => Promise.resolve(body),
      } as Response);
    }),
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
      <AssignmentsPage />
    </AuthProvider>,
  );
}

describe("AssignmentsPage", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("renders the unassigned list on load (User Story 3)", async () => {
    mockFetchSequence([
      {
        status: 200,
        body: [{ itemType: "SCHOOL", itemId: "school-1", lastEndedAt: null }],
      },
    ]);
    renderAuthenticated();

    const row = await screen.findByTestId("unassigned-row");
    expect(row).toHaveTextContent("SCHOOL");
    expect(row).toHaveTextContent("never assigned");
  });

  it("assigns a School with no current manager (User Story 1, scenario 1)", async () => {
    mockFetchSequence([
      { status: 200, body: [] }, // initial unassigned load
      { status: 200, body: [] }, // assignment-history (empty — no current row)
      {
        status: 200,
        body: {
          id: "assignment-1",
          managerId: "manager-a",
          effectiveFrom: "2026-09-22T00:00:00Z",
        },
      }, // assign
      { status: 200, body: [] }, // unassigned reload after assign
    ]);
    renderAuthenticated();
    await screen.findByTestId("unassigned-section");

    await userEvent.type(screen.getByTestId("item-id-input"), "school-1");
    await userEvent.type(screen.getByTestId("manager-id-input"), "manager-a");
    await userEvent.click(screen.getByRole("button", { name: /^assign$/i }));

    expect(await screen.findByTestId("assign-message")).toHaveTextContent(
      "manager-a",
    );
  });

  it("reassigns a School that already has a current manager, naming that assignment's id (User Story 2)", async () => {
    mockFetchSequence([
      { status: 200, body: [] }, // initial unassigned load
      {
        status: 200,
        body: [
          {
            id: "assignment-old",
            managerId: "manager-a",
            effectiveFrom: "2026-09-01T00:00:00Z",
            effectiveTo: null,
            assignedBy: "director-1",
            assignedAt: "2026-09-01T00:00:00Z",
          },
        ],
      }, // assignment-history — one open row
      {
        status: 200,
        body: {
          id: "assignment-new",
          managerId: "manager-b",
          effectiveFrom: "2026-09-22T00:00:00Z",
        },
      }, // reassign
      { status: 200, body: [] },
    ]);
    renderAuthenticated();
    await screen.findByTestId("unassigned-section");

    await userEvent.type(screen.getByTestId("item-id-input"), "school-1");
    await userEvent.type(screen.getByTestId("manager-id-input"), "manager-b");
    await userEvent.click(screen.getByRole("button", { name: /^assign$/i }));

    expect(await screen.findByTestId("assign-message")).toHaveTextContent(
      "manager-b",
    );
    const fetchMock = vi.mocked(fetch);
    const assignCall = fetchMock.mock.calls.find(([url]) =>
      String(url).includes("school-assignments"),
    );
    expect(assignCall).toBeDefined();
    const requestBody = JSON.parse(
      (assignCall![1] as RequestInit).body as string,
    );
    expect(requestBody.endsAssignmentId).toBe("assignment-old");
  });

  it("shows the conflict message on a 409 response (FR-011)", async () => {
    mockFetchSequence([
      { status: 200, body: [] },
      {
        status: 200,
        body: [
          {
            id: "assignment-old",
            managerId: "manager-a",
            effectiveFrom: "2026-09-01T00:00:00Z",
            effectiveTo: null,
            assignedBy: "director-1",
            assignedAt: "2026-09-01T00:00:00Z",
          },
        ],
      },
      {
        status: 409,
        body: {
          message:
            "This assignment was already changed by someone else. Refresh and retry.",
        },
      },
    ]);
    renderAuthenticated();
    await screen.findByTestId("unassigned-section");

    await userEvent.type(screen.getByTestId("item-id-input"), "school-1");
    await userEvent.type(screen.getByTestId("manager-id-input"), "manager-b");
    await userEvent.click(screen.getByRole("button", { name: /^assign$/i }));

    expect(await screen.findByTestId("assign-error")).toHaveTextContent(
      "already changed by someone else",
    );
  });

  it("loads a Manager's portfolio (User Story 3)", async () => {
    mockFetchSequence([
      { status: 200, body: [] }, // initial unassigned load
      {
        status: 200,
        body: [
          {
            itemType: "SCHOOL",
            itemId: "school-1",
            since: "2026-09-01T00:00:00Z",
          },
        ],
      }, // portfolio
    ]);
    renderAuthenticated();
    await screen.findByTestId("unassigned-section");

    await userEvent.type(
      screen.getByTestId("portfolio-manager-id-input"),
      "manager-a",
    );
    await userEvent.click(
      screen.getByRole("button", { name: /load portfolio/i }),
    );

    const row = await screen.findByTestId("portfolio-row");
    expect(row).toHaveTextContent("SCHOOL");
    expect(row).toHaveTextContent("school-1");
  });

  it("assigns a Manager to cover a Zone (specs/006 User Story 1)", async () => {
    mockFetchSequence([
      { status: 200, body: [] }, // initial unassigned load
      {
        status: 200,
        body: {
          id: "zma-1",
          zoneId: "zone-1",
          managerId: "manager-a",
          effectiveFrom: "2026-09-22T00:00:00Z",
        },
      },
    ]);
    renderAuthenticated();
    await screen.findByTestId("unassigned-section");

    await userEvent.type(
      screen.getByTestId("zone-manager-zone-id-input"),
      "zone-1",
    );
    await userEvent.type(
      screen.getByTestId("zone-manager-manager-id-input"),
      "manager-a",
    );
    await userEvent.click(
      screen.getByRole("button", { name: /assign manager to zone/i }),
    );

    expect(await screen.findByTestId("zone-manager-message")).toHaveTextContent(
      "manager-a",
    );
  });

  it("shows the 422 rejection reason when a School's chosen Manager doesn't cover its Zone (specs/006 User Story 2)", async () => {
    mockFetchSequence([
      { status: 200, body: [] }, // initial unassigned load
      { status: 200, body: [] }, // assignment-history (empty — no current row)
      {
        status: 422,
        body: {
          message: "Manager does not currently cover this School's Zone.",
        },
      },
    ]);
    renderAuthenticated();
    await screen.findByTestId("unassigned-section");

    await userEvent.type(screen.getByTestId("item-id-input"), "school-1");
    await userEvent.type(screen.getByTestId("manager-id-input"), "manager-x");
    await userEvent.click(screen.getByRole("button", { name: /^assign$/i }));

    expect(await screen.findByTestId("assign-error")).toHaveTextContent(
      "does not currently cover",
    );
  });
});
