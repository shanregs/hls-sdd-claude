import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuditHistoryPage } from "./AuditHistoryPage";
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
      <AuditHistoryPage />
    </AuthProvider>,
  );
}

describe("AuditHistoryPage", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("renders a record's history in order (User Story 2)", async () => {
    mockFetchOnce(200, [
      {
        id: "entry-1",
        sequenceNo: 1,
        sourceModule: "attendance",
        entityType: "AttendanceRecord",
        entityId: "att-1",
        action: "CREATED",
        summary: "created",
        beforeValue: null,
        afterValue: '{"status":"Present"}',
        actorUserId: "manager-1",
        actorRole: "MANAGER",
        occurredAt: "2026-09-22T10:00:00Z",
        requestId: null,
      },
      {
        id: "entry-2",
        sequenceNo: 2,
        sourceModule: "attendance",
        entityType: "AttendanceRecord",
        entityId: "att-1",
        action: "UPDATED",
        summary: "status: Present -> Leave",
        beforeValue: '{"status":"Present"}',
        afterValue: '{"status":"Leave"}',
        actorUserId: "manager-1",
        actorRole: "MANAGER",
        occurredAt: "2026-09-22T11:00:00Z",
        requestId: null,
      },
    ]);
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("entity-type-input"),
      "AttendanceRecord",
    );
    await userEvent.type(screen.getByTestId("entity-id-input"), "att-1");
    await userEvent.click(
      screen.getByRole("button", { name: /look up history/i }),
    );

    const rows = await screen.findAllByTestId("history-row");
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent("CREATED");
    expect(rows[1]).toHaveTextContent("status: Present -> Leave");
  });

  it("shows an empty state for a record with no history", async () => {
    mockFetchOnce(200, []);
    renderAuthenticated();

    await userEvent.type(screen.getByTestId("entity-type-input"), "Expense");
    await userEvent.type(
      screen.getByTestId("entity-id-input"),
      "never-audited",
    );
    await userEvent.click(
      screen.getByRole("button", { name: /look up history/i }),
    );

    expect(await screen.findByTestId("history-empty")).toHaveTextContent(
      "No history recorded",
    );
  });

  it("shows an access-denied message on a 403 response (FR-008)", async () => {
    mockFetchOnce(403, {});
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("entity-type-input"),
      "AttendanceRecord",
    );
    await userEvent.type(screen.getByTestId("entity-id-input"), "att-1");
    await userEvent.click(
      screen.getByRole("button", { name: /look up history/i }),
    );

    expect(await screen.findByTestId("lookup-error")).toHaveTextContent(
      "Director or Admin",
    );
  });
});
