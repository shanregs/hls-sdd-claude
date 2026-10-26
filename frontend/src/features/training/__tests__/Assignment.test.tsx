import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { buildMuiTheme } from "../../../theme/tokens";
import { SessionDetail } from "../SessionDetail";

const authFetch = vi.fn();

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function view(over: Record<string, unknown> = {}) {
  return {
    id: "s1",
    type: "MONTHLY",
    topic: "Monthly review",
    date: "2026-11-14",
    startsAt: "10:00:00",
    endsAt: "13:00:00",
    trainerName: null,
    trainerContact: null,
    venueKind: "PHYSICAL",
    venue: null,
    seatLimit: null,
    description: null,
    status: "PUBLISHED",
    patternId: null,
    patternMonth: null,
    handEdited: false,
    cancelReason: null,
    skipReason: null,
    version: 0,
    registered: 0,
    seatsLeft: null,
    counts: {
      expected: 1,
      assigned: 1,
      registeredOnly: 0,
      attended: 0,
      absent: 0,
      excused: 0,
      walkIns: 0,
      noShows: 0,
      onLeave: 0,
      rate: null,
    },
    actions: ["ASSIGN"],
    holidayNotice: false,
    attendanceNotRecorded: false,
    groupNotFullyAssigned: null,
    earlierDates: [],
    history: [],
    my: null,
    ...over,
  };
}

const ROW = {
  id: "a1",
  personKind: "TEACHER",
  personId: "t1",
  name: "Asha Teacher",
  source: "GROUP",
  groupKind: "SCHOOL",
  status: "REQUIRED",
  assignedAt: "2026-11-01T00:00:00Z",
  cannotAttendNote: null,
};

let session = view();
let rows: unknown[] = [ROW];

function renderDetail() {
  return render(
    <ThemeProvider theme={buildMuiTheme("light")}>
      <MemoryRouter>
        <SessionDetail
          sessionId="s1"
          onChanged={() => undefined}
          onClose={() => undefined}
        />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  authFetch.mockReset();
  session = view();
  rows = [ROW];
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (url.endsWith("/assignments/preview")) {
      return json({
        newCount: 2,
        alreadyCount: 1,
        refusedCount: 1,
        people: [
          {
            kind: "TEACHER",
            id: "x",
            name: "Divya",
            state: "REFUSED",
            reason: "This Teacher has left.",
          },
        ],
      });
    }
    if (url.includes("/assignments") && init?.method === "DELETE")
      return json(undefined, 200);
    if (url.includes("/assignments/me/cannot-attend"))
      return json(undefined, 200);
    if (url.endsWith("/assignments")) return json(rows);
    if (url.endsWith("/registrations")) return json([]);
    return json(session);
  });
});

describe("Assignment (spec 024 US3)", () => {
  it("lists the assigned people with a remove button", async () => {
    renderDetail();
    expect(await screen.findByText("Asha Teacher")).toBeTruthy();
    expect(
      screen.getByRole("button", { name: "Remove Asha Teacher" }),
    ).toBeTruthy();
  });

  it("requires a reason before removing someone", async () => {
    renderDetail();
    await userEvent.click(
      await screen.findByRole("button", { name: "Remove Asha Teacher" }),
    );
    await userEvent.click(screen.getByRole("button", { name: "Remove" }));
    expect(await screen.findByText("Enter the reason.")).toBeTruthy();
    const deletes = authFetch.mock.calls.filter(
      (c) => (c[1] as RequestInit | undefined)?.method === "DELETE",
    );
    expect(deletes).toHaveLength(0);
  });

  it("shows the counts and the refusals before assigning", async () => {
    renderDetail();
    await userEvent.click(
      await screen.findByRole("button", { name: "Assign people" }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Check who will be assigned" }),
    );
    expect(
      await screen.findByText(
        /2 new, 1 already assigned, 1 cannot be assigned/,
      ),
    ).toBeTruthy();
    expect(screen.getByText(/Divya: This Teacher has left/)).toBeTruthy();
    expect(
      screen.getByRole("button", { name: "Assign 2 people" }),
    ).toBeTruthy();
  });

  it("offers to add the group again when people are missing", async () => {
    session = view({ groupNotFullyAssigned: 3 });
    renderDetail();
    expect(
      await screen.findByText(/3 people in the group are not\s+assigned/),
    ).toBeTruthy();
    expect(
      screen.getByRole("button", { name: "Add the group again" }),
    ).toBeTruthy();
  });

  it("lets an assigned person say they cannot attend", async () => {
    session = view({ counts: null, actions: ["CANNOT_ATTEND"] });
    renderDetail();
    await userEvent.click(
      await screen.findByRole("button", { name: "I cannot attend" }),
    );
    await userEvent.click(screen.getByRole("button", { name: "Send" }));
    expect(await screen.findByText("Enter a note.")).toBeTruthy();
    await userEvent.type(
      screen.getByLabelText("Note for the office"),
      "Family function",
    );
    await userEvent.click(screen.getByRole("button", { name: "Send" }));
    await vi.waitFor(() => {
      const posted = authFetch.mock.calls.filter((c) =>
        String(c[0]).includes("/cannot-attend"),
      );
      expect(posted).toHaveLength(1);
    });
  });
});
