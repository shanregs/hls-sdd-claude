import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { axe } from "vitest-axe";
import { ThemeProvider } from "@mui/material/styles";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { buildMuiTheme, type ThemeMode } from "../../../theme/tokens";
import { AssignDialog } from "../AssignDialog";
import { AttendanceGrid } from "../AttendanceGrid";

const authFetch = vi.fn();

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));
vi.mock("../../../access-model/useAccessModel", () => ({
  useAccessModel: () => ({
    loading: false,
    accessModel: { roles: ["ADMIN"], dataScope: {}, navigation: [] },
  }),
}));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const ASHA = { kind: "TEACHER", id: "t1", name: "Asha Teacher" };
const RAVI = { kind: "TEACHER", id: "t2", name: "Ravi Walkin" };

let people: unknown[] = [ASHA, RAVI];
let peopleStatus = 200;

function calls(match: string, method?: string) {
  return authFetch.mock.calls.filter(
    (c) =>
      String(c[0]).includes(match) &&
      (method === undefined ||
        ((c[1] as RequestInit | undefined)?.method ?? "GET") === method),
  );
}

beforeEach(() => {
  authFetch.mockReset();
  people = [ASHA, RAVI];
  peopleStatus = 200;
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (url.includes("/training/people?")) {
      return peopleStatus === 200
        ? json(people)
        : json({ detail: "boom" }, peopleStatus);
    }
    if (url.endsWith("/assignments/preview")) {
      return json({
        newCount: 1,
        alreadyCount: 0,
        refusedCount: 0,
        people: [],
      });
    }
    if (url.endsWith("/assignments") && init?.method === "POST") {
      return json({ assigned: 1, alreadyAssigned: 0, refused: 0 });
    }
    if (url.endsWith("/walk-ins")) {
      return json({
        results: [{ personId: "t2", personKind: "TEACHER", status: "SAVED" }],
        counts: {},
      });
    }
    if (url.endsWith("/attendance")) {
      return json([
        {
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
        },
      ]);
    }
    return json({});
  });
});

function themed(mode: ThemeMode, ui: React.ReactElement) {
  return <ThemeProvider theme={buildMuiTheme(mode)}>{ui}</ThemeProvider>;
}

function assignDialog(mode: ThemeMode = "light", onAssigned = () => undefined) {
  return render(
    themed(
      mode,
      <AssignDialog
        sessionId="s1"
        onAssigned={onAssigned}
        onCancel={() => undefined}
      />,
    ),
  );
}

async function pick(label: string, name: string) {
  await userEvent.type(screen.getByRole("combobox", { name: label }), "as");
  const option = await screen.findByRole("option", { name: new RegExp(name) });
  await userEvent.click(option);
}

async function chooseSpecificPeople() {
  await userEvent.selectOptions(screen.getByLabelText("Group"), "INDIVIDUAL");
}

describe("Individual assignment (spec 024 FR-009)", () => {
  it("assigns specific people picked by name, not by id", async () => {
    const onAssigned = vi.fn();
    assignDialog("light", onAssigned);
    await chooseSpecificPeople();
    await pick("Find a person to add", "Asha Teacher");
    const list = screen.getByRole("list", { name: "People to assign" });
    expect(within(list).getByText("Asha Teacher")).toBeTruthy();

    await userEvent.click(
      screen.getByRole("button", { name: "Check who will be assigned" }),
    );
    await userEvent.click(
      await screen.findByRole("button", { name: "Assign 1 people" }),
    );
    await vi.waitFor(() => expect(onAssigned).toHaveBeenCalled());
    const post = calls("/assignments", "POST").find(
      (c) => !String(c[0]).includes("preview"),
    );
    expect(JSON.parse(String((post?.[1] as RequestInit).body))).toMatchObject({
      personKind: "TEACHER",
      ids: ["t1"],
    });
    expect(calls("/training/people?")[0][0]).toContain("kind=TEACHER");
  });

  it("asks for at least one person and lets a chosen one be removed", async () => {
    assignDialog();
    await chooseSpecificPeople();
    await userEvent.click(
      screen.getByRole("button", { name: "Check who will be assigned" }),
    );
    expect(await screen.findByText("Choose at least one person.")).toBeTruthy();
    await pick("Find a person to add", "Asha Teacher");
    await userEvent.click(
      screen.getByRole("button", { name: "Remove Asha Teacher" }),
    );
    expect(screen.getByText("Nobody chosen yet.")).toBeTruthy();
  });

  it("shows a message when the search fails", async () => {
    peopleStatus = 500;
    assignDialog();
    await chooseSpecificPeople();
    expect(
      await screen.findByText("Could not search for people."),
    ).toBeTruthy();
  });

  it("says when nobody matches", async () => {
    people = [];
    assignDialog();
    await chooseSpecificPeople();
    await userEvent.type(
      screen.getByRole("combobox", { name: "Find a person to add" }),
      "zz",
    );
    expect(
      await screen.findByText("Nobody found. Try part of the name."),
    ).toBeTruthy();
  });

  it.each<ThemeMode>(["light", "dark"])(
    "has no accessibility violations in %s mode",
    async (mode) => {
      const { container } = assignDialog(mode);
      await chooseSpecificPeople();
      await pick("Find a person to add", "Asha Teacher");
      const results = await axe(document.body, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(container).toBeTruthy();
      const summary = results.violations.map(
        (v) => `${v.id}: ${v.help} (${v.nodes.length} node(s))`,
      );
      expect(summary).toEqual([]);
    },
    30_000,
  );
});

describe("Walk-in picker (spec 024 FR-014)", () => {
  function grid() {
    return render(
      themed(
        "light",
        <AttendanceGrid
          sessionId="s1"
          onSaved={() => undefined}
          onClose={() => undefined}
        />,
      ),
    );
  }

  it("records a walk-in picked by name and leaves out people already on the roster", async () => {
    grid();
    await userEvent.click(
      await screen.findByRole("button", { name: "Add a walk-in" }),
    );
    await userEvent.type(
      screen.getByRole("combobox", { name: "Person who walked in" }),
      "a",
    );
    expect(
      await screen.findByRole("option", { name: /Ravi Walkin/ }),
    ).toBeTruthy();
    expect(screen.queryByRole("option", { name: /Asha Teacher/ })).toBeNull();
    await userEvent.click(screen.getByRole("option", { name: /Ravi Walkin/ }));
    await userEvent.click(screen.getByRole("button", { name: "Add walk-in" }));
    await vi.waitFor(() => expect(calls("/walk-ins", "POST")).toHaveLength(1));
    expect(
      JSON.parse(String((calls("/walk-ins")[0][1] as RequestInit).body)),
    ).toEqual({ personKind: "TEACHER", personId: "t2", dayValue: 1 });
  });

  it("asks to choose someone before adding", async () => {
    grid();
    await userEvent.click(
      await screen.findByRole("button", { name: "Add a walk-in" }),
    );
    await userEvent.click(screen.getByRole("button", { name: "Add walk-in" }));
    expect(
      await screen.findByText("Choose the person who walked in."),
    ).toBeTruthy();
    expect(calls("/walk-ins")).toHaveLength(0);
  });
});
