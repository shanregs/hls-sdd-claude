import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { buildMuiTheme } from "../../../theme/tokens";
import { ExceptionsPage } from "../ExceptionsPage";

const authFetch = vi.fn();
const access = { roles: ["DIRECTOR"] as string[] };

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

function exception(over: Record<string, unknown> = {}) {
  return {
    id: "e1",
    personKind: "TEACHER",
    personId: "t1",
    name: "Asha Teacher",
    sessionId: "s1",
    topic: "Monthly review",
    date: "2026-10-10",
    status: "OPEN",
    closureKind: null,
    closureNote: null,
    makeupSessionId: null,
    closedBy: null,
    closedAt: null,
    raisedAt: "2026-10-10T10:00:00Z",
    version: 2,
    repeat: false,
    countInWindow: 1,
    managerId: "m1",
    managerName: "Meera Manager",
    zoneId: "z1",
    zoneName: "North",
    ...over,
  };
}

let rows: unknown[] = [];

function renderPage(embedded = false) {
  return render(
    <ThemeProvider theme={buildMuiTheme("light")}>
      <MemoryRouter>
        <ExceptionsPage embedded={embedded} />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  authFetch.mockReset();
  access.roles = ["DIRECTOR"];
  rows = [exception()];
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method === "POST") {
      return json(exception({ status: "CLOSED", closureKind: "EXCUSED" }));
    }
    return json({
      content: rows,
      page: 0,
      size: 200,
      totalElements: rows.length,
    });
  });
});

describe("Training exceptions (spec 024 US5)", () => {
  it("shows the repeat mark, the Manager and the Zone", async () => {
    rows = [exception({ repeat: true, countInWindow: 3 })];
    renderPage();
    expect(await screen.findByText("Repeat (3)")).toBeTruthy();
    expect(screen.getByText(/Manager: Meera Manager/)).toBeTruthy();
    expect(screen.getByText(/Zone: North/)).toBeTruthy();
  });

  it("asks for a note before closing as excused", async () => {
    renderPage();
    await userEvent.click(
      await screen.findByRole("button", {
        name: "Close exception for Asha Teacher",
      }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Close exception" }),
    );
    expect(await screen.findByText("Enter a note.")).toBeTruthy();
    const posts = authFetch.mock.calls.filter(
      (c) => (c[1] as RequestInit | undefined)?.method === "POST",
    );
    expect(posts).toHaveLength(0);
    await userEvent.type(screen.getByLabelText("Note"), "Medical certificate");
    await userEvent.click(
      screen.getByRole("button", { name: "Close exception" }),
    );
    await vi.waitFor(() => {
      const sent = authFetch.mock.calls.filter(
        (c) => (c[1] as RequestInit | undefined)?.method === "POST",
      );
      expect(sent).toHaveLength(1);
      expect(
        JSON.parse((sent[0][1] as RequestInit).body as string),
      ).toMatchObject({
        kind: "EXCUSED",
        note: "Medical certificate",
        version: 2,
      });
    });
  });

  it("gives a Manager the list without close or export", async () => {
    access.roles = ["MANAGER"];
    renderPage(true);
    expect(await screen.findByText("Asha Teacher")).toBeTruthy();
    expect(
      screen.queryByRole("button", { name: /Close exception for/ }),
    ).toBeNull();
    expect(screen.queryByRole("button", { name: "Export CSV" })).toBeNull();
  });

  it("filters by status in the request", async () => {
    renderPage();
    await userEvent.selectOptions(
      await screen.findByLabelText("Status"),
      "CLOSED",
    );
    await vi.waitFor(() => {
      const urls = authFetch.mock.calls.map((c) => String(c[0]));
      expect(urls[urls.length - 1]).toMatch(/status=CLOSED/);
    });
  });
});
