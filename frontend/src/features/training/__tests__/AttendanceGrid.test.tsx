import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { buildMuiTheme } from "../../../theme/tokens";
import { AttendanceGrid } from "../AttendanceGrid";

const authFetch = vi.fn();
const access = { roles: ["MANAGER"] as string[] };

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));
vi.mock("../../../access-model/useAccessModel", () => ({
  useAccessModel: () => ({
    loading: false,
    accessModel: { roles: access.roles, dataScope: {}, navigation: [] },
  }),
}));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function row(over: Record<string, unknown>) {
  return {
    entryId: null,
    personKind: "TEACHER",
    personId: "t1",
    name: "Asha Teacher",
    basis: "ASSIGNED",
    assigned: true,
    registered: false,
    outcome: null,
    dayValue: null,
    reason: null,
    certificateNote: null,
    internalRemark: null,
    onLeave: false,
    syncState: null,
    clashNote: null,
    version: null,
    history: [],
    ...over,
  };
}

let rows: unknown[] = [];
let saveResult: unknown = { results: [], counts: {} };

function renderGrid() {
  return render(
    <ThemeProvider theme={buildMuiTheme("light")}>
      <MemoryRouter>
        <AttendanceGrid
          sessionId="s1"
          onSaved={() => undefined}
          onClose={() => undefined}
        />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  authFetch.mockReset();
  access.roles = ["MANAGER"];
  rows = [row({})];
  saveResult = { results: [], counts: {} };
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method === "PUT") return json(saveResult);
    return json(rows);
  });
});

describe("Attendance grid (spec 024 US4)", () => {
  it("lists only the rows the server returned for a Manager", async () => {
    rows = [row({ name: "Asha Teacher" })];
    renderGrid();
    expect(await screen.findByText("Asha Teacher")).toBeTruthy();
    expect(screen.queryByText("Divya Other")).toBeNull();
  });

  it("requires a reason for Absent and Excused", async () => {
    renderGrid();
    await userEvent.selectOptions(
      await screen.findByLabelText("Outcome for Asha Teacher"),
      "ABSENT",
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Save attendance" }),
    );
    expect(
      await screen.findByText("Enter a reason for Absent or Excused."),
    ).toBeTruthy();
    const puts = authFetch.mock.calls.filter(
      (c) => (c[1] as RequestInit | undefined)?.method === "PUT",
    );
    expect(puts).toHaveLength(0);
  });

  it("shows the result of each row after saving, including a refusal from the lock", async () => {
    rows = [row({}), row({ personId: "t2", name: "Beena Teacher" })];
    saveResult = {
      results: [
        {
          personId: "t1",
          personKind: "TEACHER",
          status: "SAVED",
          code: null,
          message: null,
        },
        {
          personId: "t2",
          personKind: "TEACHER",
          status: "REFUSED",
          code: "MONTH_LOCKED",
          message:
            "This month is locked. Attendance can only change after it is reopened.",
        },
      ],
      counts: {},
    };
    renderGrid();
    await userEvent.selectOptions(
      await screen.findByLabelText("Outcome for Asha Teacher"),
      "PRESENT",
    );
    await userEvent.selectOptions(
      screen.getByLabelText("Outcome for Beena Teacher"),
      "PRESENT",
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Save attendance" }),
    );
    expect(await screen.findByText("Saved")).toBeTruthy();
    expect(screen.getByText(/Not saved: This month is locked/)).toBeTruthy();
    const put = authFetch.mock.calls.find(
      (c) => (c[1] as RequestInit | undefined)?.method === "PUT",
    );
    const sent = JSON.parse((put?.[1] as RequestInit).body as string) as {
      rows: unknown[];
    };
    expect(sent.rows).toHaveLength(2);
  });

  it("shows the on-leave and clash badges and the staff-only remark column", async () => {
    rows = [
      row({
        onLeave: true,
        syncState: "CLASH",
        clashNote: "Already marked Present at school",
      }),
    ];
    renderGrid();
    expect(await screen.findByText("On leave")).toBeTruthy();
    expect(screen.getByText("Clash")).toBeTruthy();
    expect(screen.getByText("Already marked Present at school")).toBeTruthy();
    expect(screen.getByText("Internal remark (staff only)")).toBeTruthy();
  });
});
