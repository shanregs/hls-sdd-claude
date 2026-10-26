import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { buildMuiTheme } from "../../../theme/tokens";
import { HistoryPage } from "../HistoryPage";

const authFetch = vi.fn();

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function entry(over: Record<string, unknown>) {
  return {
    type: "SESSION",
    sessionId: "s1",
    sessionType: "MONTHLY",
    topic: "Monthly review",
    date: "2026-01-12",
    sessionStatus: "HELD",
    role: "REQUIRED",
    outcome: "PRESENT",
    dayValue: 1,
    reason: null,
    certificateNote: null,
    internalRemark: null,
    exceptionStatus: null,
    onLeave: false,
    ...over,
  };
}

let history: unknown;

function renderAt(path: string, mine = false) {
  return render(
    <ThemeProvider theme={buildMuiTheme("light")}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route
            path="/operations/training/history/:kind/:id"
            element={<HistoryPage mine={mine} />}
          />
          <Route
            path="/my-attendance/training/history"
            element={<HistoryPage mine />}
          />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  authFetch.mockReset();
  history = {
    person: { kind: "TEACHER", id: "t1", name: "Asha Teacher" },
    year: 2026,
    entries: [
      entry({}),
      entry({
        sessionId: "s2",
        topic: "Phonics",
        outcome: "ABSENT",
        reason: "Sick",
        certificateNote: "Doctor note",
      }),
    ],
    induction: [
      {
        type: "INDUCTION",
        batchId: "b1",
        batchName: "Induction 7",
        startsOn: "2020-03-02",
        endsOn: "2020-03-13",
        result: "COMPLETED",
        signedAt: "2020-03-14T00:00:00Z",
      },
    ],
    yearly: {
      expected: 2,
      attended: 1,
      expectedSessions: 2,
      attendedSessions: 1,
    },
  };
  authFetch.mockImplementation(async () => json(history));
});

describe("Training history (spec 024 US6)", () => {
  it("shows the person, the yearly figure, the induction and each session", async () => {
    renderAt("/operations/training/history/teacher/t1");
    expect(
      await screen.findByText(/Training history: Asha Teacher/),
    ).toBeTruthy();
    expect(screen.getByText(/attended 1 of 2 expected sessions/)).toBeTruthy();
    expect(screen.getByText(/Induction 7/)).toBeTruthy();
    expect(screen.getByText("Monthly review")).toBeTruthy();
    expect(screen.getByText("Phonics")).toBeTruthy();
    expect(screen.getByText("Note: Doctor note")).toBeTruthy();
  });

  it("asks the own-history endpoint for a Teacher", async () => {
    renderAt("/my-attendance/training/history");
    expect(await screen.findByText("My Training History")).toBeTruthy();
    expect(String(authFetch.mock.calls[0][0])).toContain(
      "/api/v1/training/me/history",
    );
  });

  it("shows an empty state, not an error", async () => {
    history = {
      ...(history as object),
      entries: [],
      induction: [],
      yearly: {
        expected: 0,
        attended: 0,
        expectedSessions: 0,
        attendedSessions: 0,
      },
    };
    renderAt("/operations/training/history/teacher/t1");
    expect(
      await screen.findByText(/No training recorded for 2026/),
    ).toBeTruthy();
  });

  it("reloads for the year picked", async () => {
    renderAt("/operations/training/history/teacher/t1");
    const year = await screen.findByLabelText("Year");
    await userEvent.clear(year);
    await userEvent.type(year, "2025");
    await vi.waitFor(() => {
      const urls = authFetch.mock.calls.map((c) => String(c[0]));
      expect(urls[urls.length - 1]).toContain("year=2025");
    });
  });
});
