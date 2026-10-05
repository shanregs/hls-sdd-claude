import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MarketingCalendar } from "./MarketingCalendar";
import { MarketingDashboard } from "./MarketingDashboard";
import { MarketingSettingsPage } from "./MarketingSettingsPage";
import { PipelineBoard } from "./PipelineBoard";
import { ProposalForm } from "./ProposalForm";
import { ProspectDetailPage } from "./ProspectDetailPage";
import { ProspectsPage } from "./ProspectsPage";
import { WinDialog } from "./WinDialog";

const authFetch = vi.fn();
let user = { id: "u-admin", displayName: "Asha Admin", roles: ["ADMIN"] };
let grants: Record<string, string[]> = {};

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch, user }),
}));
vi.mock("../common/useGrantedActions", () => ({
  useGrantedActions: (route: string) => new Set(grants[route] ?? []),
}));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const ROW = {
  id: "p1",
  name: "Green Valley School",
  board: "CBSE",
  zoneId: "z1",
  zoneName: "Demo Zone",
  owner: { userId: "u-admin", name: "Asha Admin" },
  stage: "FINAL_STAGE",
  effectiveStage: "FINAL_STAGE",
  expectedTeachers: 4,
  won: false,
  followUpOverdue: true,
  mouOverdue: false,
  schoolId: null,
  contactPerson: "Mrs Rao",
  version: 2,
};

const STATUS = {
  available: true,
  status: "NOT_WON",
  contract: null,
  proposal: null,
  differences: [],
};

function detail(extra: object = {}, row: object = {}) {
  return {
    row: { ...ROW, ...row },
    address: "5 Lake Road",
    designation: "Principal",
    phone: "9111111111",
    email: "rao@school.test",
    lostReason: null,
    wonAt: null,
    placeId: null,
    stageHistory: [],
    ownerHistory: [],
    proposal: null,
    contractStatus: STATUS,
    ...extra,
  };
}

const PROPOSAL = {
  id: "r1",
  revision: 1,
  label: "Proposal (not a contract)",
  teacherCount: 4,
  startMonth: "2026-12-01",
  salaryMode: "SAME_FOR_ALL",
  rate: "15000.00",
  positions: [],
  monthlyTotal: "60000.00",
  notes: null,
  createdBy: { userId: "u-admin", name: "Asha Admin" },
  createdAt: "2026-10-05T10:00:00Z",
};

const ACTIVITY = {
  id: "a1",
  type: "VISIT",
  status: "PLANNED",
  effectiveStatus: "MISSED",
  rescheduled: true,
  date: "2026-10-01",
  notes: "Meet the principal",
  outcome: null,
  followUpOn: null,
  followUpOverdue: false,
  cancelReason: null,
  prospect: { id: "p1", name: "Green Valley School" },
  school: null,
  attendees: [{ userId: "u-admin", name: "Asha Admin" }],
  attachments: [
    {
      fileId: "f1",
      name: "board photo.png",
      sizeBytes: 2048,
      contentType: "image/png",
    },
  ],
  dateHistory: [{ from: "2026-09-28", to: "2026-10-01" }],
  version: 0,
};

function route(table: Record<string, unknown>) {
  authFetch.mockImplementation(async (input: string) => {
    for (const [fragment, body] of Object.entries(table)) {
      if (String(input).includes(fragment)) return json(body);
    }
    return json({});
  });
}

beforeEach(() => {
  authFetch.mockReset();
  user = { id: "u-admin", displayName: "Asha Admin", roles: ["ADMIN"] };
  grants = {};
});

describe("ProspectsPage (spec 023 US1)", () => {
  it("lists prospects with their stage and flags and offers adding only to those who may create", async () => {
    grants["/marketing/prospects"] = ["VIEW", "CREATE", "EDIT"];
    route({
      "/prospects": { content: [ROW], page: 0, size: 25, totalElements: 1 },
    });

    render(
      <MemoryRouter>
        <ProspectsPage />
      </MemoryRouter>,
    );

    const table = await screen.findByRole("table", { name: "Prospects" });
    expect(within(table).getByText(/Green Valley School/)).toBeInTheDocument();
    expect(within(table).getByText("Final Stage")).toBeInTheDocument();
    expect(within(table).getByText("Follow-up overdue")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Add prospect" }),
    ).toBeInTheDocument();
  });

  it("hides adding for a read-only grant and shows the empty state", async () => {
    grants["/marketing/prospects"] = ["VIEW"];
    route({
      "/prospects": { content: [], page: 0, size: 25, totalElements: 0 },
    });

    render(
      <MemoryRouter>
        <ProspectsPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText(/No prospects match/)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Add prospect" }),
    ).not.toBeInTheDocument();
  });

  it("shows a duplicate refusal inside the add dialog", async () => {
    grants["/marketing/prospects"] = ["VIEW", "CREATE"];
    authFetch.mockImplementation(async (input: string, init?: RequestInit) => {
      if (init?.method === "POST")
        return json(
          {
            reason:
              "This School is already a prospect (existing prospect abc).",
          },
          409,
        );
      if (String(input).includes("/zones"))
        return json({
          content: [
            {
              id: "z1",
              name: "Demo Zone",
              version: 0,
              placeCount: 1,
              schoolCount: 1,
            },
          ],
          page: 0,
          size: 100,
          totalElements: 1,
        });
      return json({ content: [], page: 0, size: 25, totalElements: 0 });
    });

    render(
      <MemoryRouter>
        <ProspectsPage />
      </MemoryRouter>,
    );
    await userEvent.click(
      await screen.findByRole("button", { name: "Add prospect" }),
    );
    const dialog = await screen.findByRole("dialog");
    await userEvent.type(
      within(dialog).getByLabelText(/School name/),
      "Green Valley School",
    );
    await userEvent.click(within(dialog).getByLabelText(/Zone/));
    await userEvent.click(
      await screen.findByRole("option", { name: "Demo Zone" }),
    );
    await userEvent.click(
      within(dialog).getByRole("button", { name: "Add prospect" }),
    );

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "already a prospect",
    );
  });
});

describe("ProspectDetailPage (spec 023 US1 to US4)", () => {
  function renderDetail() {
    return render(
      <MemoryRouter initialEntries={["/marketing/prospects/p1"]}>
        <Routes>
          <Route
            path="/marketing/prospects/:id"
            element={<ProspectDetailPage />}
          />
        </Routes>
      </MemoryRouter>,
    );
  }

  it("shows the proposal as not a contract, the visits with their files and the missed and rescheduled marks", async () => {
    grants["/marketing/prospects"] = ["VIEW", "CREATE", "EDIT"];
    route({
      "/prospects/p1/proposals": [PROPOSAL],
      "/activities": { content: [ACTIVITY], drives: [] },
      "/prospects/p1": detail({ proposal: PROPOSAL }),
    });

    renderDetail();

    expect(
      await screen.findByRole("heading", { name: "Green Valley School" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Proposal (not a contract)" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Current: Revision 1: 4 Teachers/),
    ).toBeInTheDocument();
    expect(screen.getByText(/Missed, rescheduled/)).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Download board photo.png" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/Earlier dates: 28\/09\/2026/)).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Complete" }),
    ).toBeInTheDocument();
  });

  it("shows the review buttons only to a role that holds the approve action", async () => {
    grants["/marketing/prospects"] = ["VIEW", "CREATE", "EDIT"];
    route({
      "/prospects/p1/proposals": [PROPOSAL],
      "/activities": { content: [], drives: [] },
      "/prospects/p1": detail(),
    });
    const first = renderDetail();
    await screen.findByRole("heading", { name: "Green Valley School" });
    expect(
      screen.queryByRole("button", { name: "Approve (won)" }),
    ).not.toBeInTheDocument();
    first.unmount();

    grants["/marketing/prospects"] = ["VIEW", "CREATE", "EDIT", "APPROVE"];
    renderDetail();
    expect(
      await screen.findByRole("button", { name: "Approve (won)" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reject" })).toBeInTheDocument();
  });

  it("offers creating the School only to Admin or Director on a won prospect and the MoU hand-off after it", async () => {
    grants["/marketing/prospects"] = ["VIEW", "CREATE", "EDIT", "APPROVE"];
    const won = { won: true, effectiveStage: "WON" };
    route({
      "/prospects/p1/proposals": [PROPOSAL],
      "/activities": { content: [], drives: [] },
      "/interviewers": [{ userId: "u-admin", name: "Asha Admin" }],
      "/prospects/p1": detail(
        {
          proposal: PROPOSAL,
          contractStatus: { ...STATUS, status: "NOT_RECORDED" },
        },
        won,
      ),
    });
    const first = renderDetail();
    expect(
      await screen.findByRole("button", { name: "Create the School" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/The School and the MoU are to be recorded/),
    ).toBeInTheDocument();
    first.unmount();

    user = { id: "u-mgr", displayName: "Manoj Manager", roles: ["MANAGER"] };
    renderDetail();
    await screen.findByRole("heading", { name: "Green Valley School" });
    expect(
      screen.queryByRole("button", { name: "Create the School" }),
    ).not.toBeInTheDocument();
  });

  it("shows the MoU status and what the signed MoU changed from the proposal", async () => {
    grants["/marketing/prospects"] = ["VIEW"];
    route({
      "/prospects/p1/proposals": [PROPOSAL],
      "/activities": { content: [], drives: [] },
      "/prospects/p1": detail(
        {
          proposal: PROPOSAL,
          contractStatus: {
            available: true,
            status: "MOU",
            contract: {
              startsOn: "2026-12-01",
              endsOn: null,
              teacherCount: 5,
              salaryMode: "SAME_FOR_ALL",
              rate: "16000.00",
              positions: [],
              filled: 0,
              vacant: 5,
            },
            proposal: PROPOSAL,
            differences: ["Teachers: proposal 4, MoU 5"],
          },
        },
        { won: true, effectiveStage: "MOU", schoolId: "s1" },
      ),
    });

    renderDetail();

    expect(
      await screen.findByText(
        /MoU recorded: 01\/12\/2026 onwards; 5 Teachers, 0 filled, 5 vacant/,
      ),
    ).toBeInTheDocument();
    expect(screen.getByText(/Teachers: proposal 4, MoU 5/)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Record the MoU" }),
    ).not.toBeInTheDocument();
  });
});

describe("ProposalForm (spec 023 US3)", () => {
  it("validates the salary mode and sends one amount per position", async () => {
    authFetch.mockResolvedValue(json({ ...PROPOSAL, revision: 2 }, 201));
    const onSaved = vi.fn();
    render(
      <ProposalForm prospectId="p1" onClose={vi.fn()} onSaved={onSaved} />,
    );

    expect(screen.getByText(/Proposal \(not a contract\)/)).toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText(/Number of Teachers/));
    await userEvent.type(screen.getByLabelText(/Number of Teachers/), "2");
    await userEvent.type(screen.getByLabelText(/Start month/), "2026-12");
    await userEvent.click(screen.getByLabelText("Different for each Teacher"));
    await userEvent.type(
      screen.getByLabelText(/Position 1 monthly salary/),
      "14000",
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Save revision" }),
    );
    expect(
      await screen.findByText(/salary for every position/),
    ).toBeInTheDocument();

    await userEvent.type(
      screen.getByLabelText(/Position 2 monthly salary/),
      "15000.50",
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Save revision" }),
    );

    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    const body = JSON.parse(
      (authFetch.mock.calls[0][1] as RequestInit).body as string,
    );
    expect(body).toMatchObject({
      teacherCount: 2,
      salaryMode: "PER_TEACHER",
      startMonth: "2026-12",
    });
    expect(body.positions.map((p: { salary: string }) => p.salary)).toEqual([
      "14000",
      "15000.50",
    ]);
  });
});

describe("PipelineBoard (spec 023 US2)", () => {
  const board = {
    columns: {
      PROSPECT: [
        {
          ...ROW,
          stage: "PROSPECT",
          effectiveStage: "PROSPECT",
          followUpOverdue: false,
        },
      ],
      FINAL_STAGE: [ROW],
      WON: [
        {
          ...ROW,
          id: "p3",
          name: "Won School",
          won: true,
          effectiveStage: "WON",
          stage: "FINAL_STAGE",
          mouOverdue: true,
        },
      ],
    },
    counts: { PROSPECT: 1, FINAL_STAGE: 1, WON: 1 },
  };

  it("shows the columns with counts, the MoU flag, and review buttons only with the approve action", async () => {
    grants["/marketing/pipeline"] = ["VIEW", "CREATE", "EDIT"];
    route({ "/pipeline": board });
    const first = render(
      <MemoryRouter>
        <PipelineBoard />
      </MemoryRouter>,
    );
    expect(
      await screen.findByRole("region", { name: "Final Stage, 1" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Won, 1" })).toBeInTheDocument();
    expect(screen.getByText("MoU not recorded")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Approve Green Valley/ }),
    ).not.toBeInTheDocument();
    first.unmount();

    grants["/marketing/pipeline"] = ["VIEW", "CREATE", "EDIT", "APPROVE"];
    render(
      <MemoryRouter>
        <PipelineBoard />
      </MemoryRouter>,
    );
    expect(
      await screen.findByRole("button", {
        name: "Approve Green Valley School",
      }),
    ).toBeInTheDocument();
  });

  it("shows the server's refusal when a move is not allowed", async () => {
    grants["/marketing/pipeline"] = ["VIEW", "EDIT"];
    authFetch.mockImplementation(async (_input: string, init?: RequestInit) =>
      init?.method === "POST"
        ? json(
            {
              reason:
                "Record a proposal before moving the prospect to Final Stage.",
            },
            409,
          )
        : json(board),
    );
    render(
      <MemoryRouter>
        <PipelineBoard />
      </MemoryRouter>,
    );

    await userEvent.click(
      await screen
        .findByLabelText(/Move Green Valley School/, { selector: "input,div" })
        .catch(() => screen.getAllByRole("combobox")[0]),
    );
    await userEvent.click(
      await screen.findByRole("option", { name: "Final Stage" }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Record a proposal",
    );
  });
});

describe("WinDialog (spec 023 US4)", () => {
  it("creates the School and navigates to the MoU form with the proposal in the router state", async () => {
    authFetch.mockImplementation(async (input: string, init?: RequestInit) => {
      if (init?.method === "POST") {
        return json({
          prospect: {},
          handoff: {
            schoolId: "s9",
            path: "/operations/school-contracts/schools/s9",
            proposal: PROPOSAL,
            available: true,
          },
        });
      }
      if (String(input).includes("/places"))
        return json({
          content: [
            {
              id: "pl1",
              name: "Adyar",
              pinCode: "600020",
              zoneId: "z1",
              zoneName: "Demo Zone",
            },
          ],
          page: 0,
          size: 100,
          totalElements: 1,
        });
      return json({});
    });
    const target = vi.fn();
    function Target() {
      target();
      return <p>MoU page</p>;
    }
    render(
      <MemoryRouter>
        <Routes>
          <Route
            path="/"
            element={
              <WinDialog
                prospect={detail({}, { won: true }) as never}
                onClose={vi.fn()}
                onDone={vi.fn()}
              />
            }
          />
          <Route
            path="/operations/school-contracts/schools/:id"
            element={<Target />}
          />
        </Routes>
      </MemoryRouter>,
    );

    await userEvent.click(await screen.findByLabelText(/Place/));
    await userEvent.click(await screen.findByRole("option", { name: /Adyar/ }));
    await userEvent.click(
      screen.getByRole("button", { name: "Create School" }),
    );

    expect(await screen.findByText("MoU page")).toBeInTheDocument();
    const body = JSON.parse(
      (
        authFetch.mock.calls.find(
          ([, i]) => (i as RequestInit | undefined)?.method === "POST",
        )![1] as RequestInit
      ).body as string,
    );
    expect(body).toMatchObject({ placeId: "pl1" });
  });

  it("offers to link an existing School of the same name instead of creating a duplicate", async () => {
    authFetch.mockImplementation(async (input: string, init?: RequestInit) => {
      if (init?.method === "POST") {
        return json(
          {
            reason:
              "A School with this name already exists in this Place; link it instead (School 11111111-2222-3333-4444-555555555555).",
          },
          409,
        );
      }
      if (String(input).includes("/places"))
        return json({
          content: [
            {
              id: "pl1",
              name: "Adyar",
              pinCode: "600020",
              zoneId: "z1",
              zoneName: "Demo Zone",
            },
          ],
          page: 0,
          size: 100,
          totalElements: 1,
        });
      return json({});
    });
    render(
      <MemoryRouter>
        <WinDialog
          prospect={detail({}, { won: true }) as never}
          onClose={vi.fn()}
          onDone={vi.fn()}
        />
      </MemoryRouter>,
    );

    await userEvent.click(await screen.findByLabelText(/Place/));
    await userEvent.click(await screen.findByRole("option", { name: /Adyar/ }));
    await userEvent.click(
      screen.getByRole("button", { name: "Create School" }),
    );

    expect(
      await screen.findByRole("button", { name: "Link the existing School" }),
    ).toBeInTheDocument();
  });
});

describe("MarketingCalendar (spec 023 US1)", () => {
  it("lists activities with missed and rescheduled marks next to the person's drives", async () => {
    grants["/marketing/calendar"] = ["VIEW", "CREATE", "EDIT"];
    const today = new Date();
    const iso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-05`;
    route({
      "/activities": {
        content: [{ ...ACTIVITY, date: iso }],
        drives: [
          {
            kind: "CAMPUS_DRIVE",
            id: "d1",
            ownerUserId: "u-admin",
            date: iso,
            place: "Demo College, Chennai",
            status: "PLANNED",
          },
        ],
      },
    });

    render(
      <MemoryRouter>
        <MarketingCalendar />
      </MemoryRouter>,
    );

    const list = await screen.findByRole("list", { name: /Activities in/ });
    expect(within(list).getByText(/Missed, rescheduled/)).toBeInTheDocument();
    expect(
      within(list).getByText(/Campus drive - Demo College, Chennai/),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Plan an activity" }),
    ).toBeInTheDocument();
  });

  it("hides planning and the row actions for a read-only grant", async () => {
    grants["/marketing/calendar"] = ["VIEW"];
    route({
      "/activities": {
        content: [
          { ...ACTIVITY, date: new Date().toISOString().slice(0, 8) + "05" },
        ],
        drives: [],
      },
    });

    render(
      <MemoryRouter>
        <MarketingCalendar />
      </MemoryRouter>,
    );

    await screen.findByRole("list", { name: /Activities in/ });
    expect(
      screen.queryByRole("button", { name: "Plan an activity" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /^Complete / }),
    ).not.toBeInTheDocument();
  });
});

describe("MarketingDashboard and settings (spec 023 US4, US5)", () => {
  const data = {
    period: "2026-10",
    visits: { planned: 3, completed: 5, missed: 1, cancelled: 0 },
    prospectsByStage: {
      PROSPECT: 2,
      CONTACTED: 1,
      VISIT: 0,
      WON: 1,
      MOU: 0,
      ACTIVE: 0,
      LOST: 1,
    },
    won: 1,
    lost: 1,
    winRate: "0.50",
    wonPerZone: [{ name: "Demo Zone", won: 1 }],
    wonPerOwner: [{ name: "Asha Admin", won: 1 }],
    demand: 9,
    supply: null,
    shortfall: null,
  };

  it("shows demand against a supply that is not available, and the win rate", async () => {
    route({ "/dashboard": data });
    render(<MarketingDashboard />);

    expect(
      await screen.findByText("Demand: vacant positions at won Schools"),
    ).toBeInTheDocument();
    expect(screen.getAllByText("Not available")).toHaveLength(2);
    expect(screen.getByText("50%")).toBeInTheDocument();
    expect(
      screen.getByRole("table", { name: "Schools won per Zone" }),
    ).toBeInTheDocument();
  });

  it("shows the empty state when there is nothing yet", async () => {
    route({
      "/dashboard": {
        ...data,
        visits: { planned: 0, completed: 0, missed: 0, cancelled: 0 },
        prospectsByStage: { PROSPECT: 0 },
        won: 0,
        lost: 0,
        winRate: null,
        wonPerZone: [],
        wonPerOwner: [],
        demand: 0,
      },
    });
    render(<MarketingDashboard />);

    expect(
      await screen.findByText(/No prospects or visits yet/),
    ).toBeInTheDocument();
  });

  it("lets a holder of the edit action change the limit and refuses a value outside 1 to 90", async () => {
    grants["/marketing/settings"] = ["VIEW", "EDIT"];
    authFetch.mockImplementation(async (_input: string, init?: RequestInit) =>
      init?.method === "PUT"
        ? json({ mouOverdueDays: 21, version: 2 })
        : json({ mouOverdueDays: 14, version: 1 }),
    );
    render(<MarketingSettingsPage />);

    const field = await screen.findByLabelText(/Days until a won prospect/);
    await userEvent.clear(field);
    await userEvent.type(field, "120");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("1 to 90");
    await userEvent.clear(field);
    await userEvent.type(field, "21");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Saved.")).toBeInTheDocument();
    const body = JSON.parse(
      (
        authFetch.mock.calls.find(
          ([, i]) => (i as RequestInit | undefined)?.method === "PUT",
        )![1] as RequestInit
      ).body as string,
    );
    expect(body).toEqual({ mouOverdueDays: 21, version: 1 });
  });

  it("is read-only without the edit action", async () => {
    grants["/marketing/settings"] = ["VIEW"];
    route({ "/settings": { mouOverdueDays: 14, version: 1 } });
    render(<MarketingSettingsPage />);

    expect(
      await screen.findByLabelText(/Days until a won prospect/),
    ).toBeDisabled();
    expect(
      screen.queryByRole("button", { name: "Save" }),
    ).not.toBeInTheDocument();
  });
});
