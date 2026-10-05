import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { DriveDetailPage } from "./DriveDetailPage";
import { DrivesPage } from "./DrivesPage";
import { InductionPage } from "./InductionPage";
import { OfferAcceptDialog } from "./OfferAcceptDialog";
import { OffersPage } from "./OffersPage";
import { RecruitmentDashboard } from "./RecruitmentDashboard";

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

const DRIVE = {
  id: "d1",
  college: { id: "c1", name: "Demo College", city: "Chennai" },
  season: "2026-27",
  venue: "Hall",
  status: "PLANNED",
  cancelReason: null,
  dates: ["2026-10-02"],
  interviewers: [{ userId: "u-owner", name: "Owner Manager" }],
  scheduledBy: "u-owner",
  candidates: 1,
  outcomes: { SELECTED: 1 },
  heldNoCandidates: false,
  version: 0,
};

const CANDIDATE = {
  id: "k1",
  driveId: "d1",
  name: "Asha Student",
  phone: "9555500001",
  email: null,
  degree: "B.A.",
  year: "Final",
  notes: null,
  outcome: "SELECTED",
  outcomeAt: null,
  teacherId: null,
  assessment: null,
  version: 0,
};

const OFFER = {
  id: "o1",
  candidateId: "k1",
  candidateName: "Asha Student",
  college: "Demo College",
  role: "Trainee / English Trainer",
  monthlySalary: "16000.00",
  allowances: null,
  terms: null,
  expectedJoining: null,
  offerDate: "2026-10-01",
  responseDeadline: "2026-10-20",
  status: "ISSUED",
  supersedesId: null,
  declineReason: null,
  issuedByName: "Divya",
  issuedAt: null,
  decidedAt: null,
  teacherId: null,
  version: 0,
};

function route(table: Record<string, unknown>) {
  authFetch.mockImplementation(async (input: string) => {
    for (const [fragment, body] of Object.entries(table)) {
      if (input.includes(fragment)) return json(body);
    }
    return json({});
  });
}

beforeEach(() => {
  authFetch.mockReset();
  user = { id: "u-admin", displayName: "Asha Admin", roles: ["ADMIN"] };
  grants = {};
});

describe("DrivesPage (spec 016 US1)", () => {
  it("lists drives and colleges and offers scheduling only to those who may create", async () => {
    grants["/recruitment/drives"] = ["VIEW", "CREATE", "EDIT"];
    route({
      "/colleges": [
        {
          id: "c1",
          name: "Demo College",
          city: "Chennai",
          placementOfficer: { name: "Ms Placement" },
          principal: { name: "Dr Principal" },
          drives: 1,
          version: 0,
        },
      ],
      "/drives": [DRIVE],
    });

    render(
      <MemoryRouter>
        <DrivesPage />
      </MemoryRouter>,
    );

    expect(
      await screen.findByRole("table", { name: "Campus drives" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Ms Placement")).toBeInTheDocument();
    expect(screen.getByText("Dr Principal")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Schedule drive" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Add college" }),
    ).toBeInTheDocument();
  });

  it("hides the write buttons when only VIEW is granted and shows the empty state", async () => {
    grants["/recruitment/drives"] = ["VIEW"];
    route({ "/colleges": [], "/drives": [] });

    render(
      <MemoryRouter>
        <DrivesPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText(/No drives yet/)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Schedule drive" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Add college" }),
    ).not.toBeInTheDocument();
  });
});

describe("DriveDetailPage (spec 016 US1)", () => {
  function renderDetail() {
    return render(
      <MemoryRouter initialEntries={["/recruitment/drives/d1"]}>
        <Routes>
          <Route path="/recruitment/drives/:id" element={<DriveDetailPage />} />
        </Routes>
      </MemoryRouter>,
    );
  }

  it("hides write controls for a Zone Manager on another person's drive", async () => {
    user = { id: "u-other", displayName: "Other Manager", roles: ["MANAGER"] };
    grants["/recruitment/drives"] = ["VIEW", "CREATE", "EDIT"];
    route({
      "/recruitment/drives/d1": DRIVE,
      "/recruitment/candidates": [CANDIDATE],
    });

    renderDetail();

    expect(
      await screen.findByRole("table", { name: "Candidates" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/another person's drive/)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Add candidate" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Import CSV" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Set outcome of/ }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "History of Asha Student" }),
    ).toBeInTheDocument();
  });

  it("shows write controls to an interviewer of the drive and to Admin", async () => {
    user = { id: "u-owner", displayName: "Owner Manager", roles: ["MANAGER"] };
    grants["/recruitment/drives"] = ["VIEW", "CREATE", "EDIT"];
    route({
      "/recruitment/drives/d1": DRIVE,
      "/recruitment/candidates": [CANDIDATE],
    });

    renderDetail();

    expect(
      await screen.findByRole("button", { name: "Add candidate" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Set outcome of Asha Student" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Assess Asha Student" }),
    ).toBeInTheDocument();
  });

  it("shows the import result with each invalid row", async () => {
    grants["/recruitment/drives"] = ["VIEW", "CREATE", "EDIT"];
    authFetch.mockImplementation(async (input: string, init?: RequestInit) => {
      if (input.includes("/candidates/import") && init?.method === "POST") {
        return json({
          saved: 9,
          skipped: 0,
          errors: [{ row: 5, reason: "Phone must have at least 10 digits." }],
        });
      }
      if (input.includes("/recruitment/candidates")) return json([CANDIDATE]);
      return json(DRIVE);
    });

    renderDetail();
    await userEvent.click(
      await screen.findByRole("button", { name: "Import CSV" }),
    );
    const dialog = await screen.findByRole("dialog");
    const file = new File(["name,phone\nA,1"], "c.csv", { type: "text/csv" });
    await userEvent.upload(within(dialog).getByLabelText("CSV file"), file);
    await userEvent.click(
      within(dialog).getByRole("button", { name: "Import" }),
    );

    expect(await within(dialog).findByText(/Saved 9/)).toBeInTheDocument();
    expect(
      within(dialog).getByText(/Row 5: Phone must have at least 10 digits/),
    ).toBeInTheDocument();
  });
});

describe("OffersPage and OfferAcceptDialog (spec 016 US2, US3)", () => {
  it("shows Director actions on an issued offer and only the letter to a read-only role", async () => {
    grants["/recruitment/offers"] = ["VIEW", "CREATE", "EDIT"];
    route({ "/recruitment/offers": [OFFER] });
    const first = render(
      <MemoryRouter>
        <OffersPage />
      </MemoryRouter>,
    );
    expect(
      await screen.findByRole("button", {
        name: "Accept offer of Asha Student",
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Replace offer of Asha Student" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Decline offer of Asha Student" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Rs. 16,000.00".replace("Rs. ", "₹"), { exact: false }),
    ).toBeInTheDocument();
    first.unmount();

    grants["/recruitment/offers"] = ["VIEW"];
    render(
      <MemoryRouter>
        <OffersPage />
      </MemoryRouter>,
    );
    expect(
      await screen.findByRole("button", { name: "Letter for Asha Student" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Accept offer of/ }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "New offer" }),
    ).not.toBeInTheDocument();
  });

  it("names the existing Teacher when acceptance is refused", async () => {
    authFetch.mockResolvedValue(
      json(
        { reason: "This person is already a Teacher: Tara Teacher (ACTIVE)." },
        409,
      ),
    );
    render(
      <OfferAcceptDialog
        offer={OFFER as never}
        onClose={vi.fn()}
        onAccepted={vi.fn()}
      />,
    );

    await userEvent.click(screen.getByRole("button", { name: "Accept offer" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Tara Teacher");
    expect(
      screen.getByRole("button", { name: "Accept offer" }),
    ).toBeInTheDocument();
  });

  it("asks to confirm a new record for an exited match and then sends the confirmation", async () => {
    const onAccepted = vi.fn();
    authFetch
      .mockResolvedValueOnce(
        json(
          {
            reason:
              "An earlier Teacher record of Came Back has exited. Confirm to create a new Teacher record.",
          },
          409,
        ),
      )
      .mockResolvedValueOnce(
        json({ ...OFFER, status: "ACCEPTED", teacherId: "t1" }),
      );
    render(
      <OfferAcceptDialog
        offer={OFFER as never}
        onClose={vi.fn()}
        onAccepted={onAccepted}
      />,
    );

    await userEvent.click(screen.getByRole("button", { name: "Accept offer" }));
    const confirm = await screen.findByRole("button", {
      name: "Create a new Teacher record",
    });
    await userEvent.click(confirm);

    await waitFor(() => expect(onAccepted).toHaveBeenCalled());
    const body = JSON.parse(
      (authFetch.mock.calls[1][1] as RequestInit).body as string,
    );
    expect(body).toEqual({ confirmNewRecord: true });
  });
});

describe("InductionPage (spec 016 US4)", () => {
  const BATCH = {
    id: "b1",
    name: "October Induction",
    startsOn: "2026-10-01",
    endsOn: "2026-10-31",
    trainer: "Tina",
    venueType: "PHYSICAL",
    venue: "HO",
    seatLimit: 10,
    enrolled: 1,
    status: "RUNNING",
    version: 0,
  };
  const ROSTER = [
    {
      enrolmentId: "e1",
      teacherId: "t1",
      name: "Ready Rani",
      result: null,
      remarks: null,
      followUp: null,
      days: [
        { date: "2026-10-02", status: "PRESENT", dayValue: 1, reason: null },
      ],
    },
    {
      enrolmentId: "e2",
      teacherId: "t3",
      name: "Slow Sam",
      result: "NOT_COMPLETED",
      remarks: null,
      followUp: null,
      days: [],
    },
  ];

  it("shows the roster, attendance and sign-off actions, and the ready-to-deploy list", async () => {
    grants["/recruitment/induction"] = ["VIEW", "CREATE", "EDIT"];
    route({
      "/roster": ROSTER,
      "/batches": [BATCH],
      "/ready-to-deploy": [
        { teacherId: "t2", name: "Done Divya", status: "ACTIVE" },
      ],
      "/to-be-enrolled": [],
    });

    render(
      <MemoryRouter>
        <InductionPage />
      </MemoryRouter>,
    );

    expect(
      await screen.findByRole("table", { name: "Roster" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Attendance of Ready Rani" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Sign off Ready Rani" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Move Slow Sam to the next batch" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Release Slow Sam" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Done Divya")).toBeInTheDocument();
  });

  it("offers no write buttons for a read-only grant", async () => {
    grants["/recruitment/induction"] = ["VIEW"];
    route({
      "/roster": ROSTER,
      "/batches": [BATCH],
      "/ready-to-deploy": [],
      "/to-be-enrolled": [],
    });

    render(
      <MemoryRouter>
        <InductionPage />
      </MemoryRouter>,
    );

    expect(
      await screen.findByRole("table", { name: "Roster" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Create batch" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Sign off/ }),
    ).not.toBeInTheDocument();
  });
});

describe("RecruitmentDashboard (spec 016 US5)", () => {
  const COUNTS = {
    drivesScheduled: 2,
    drivesHeld: 1,
    interviewed: 10,
    assessed: 8,
    selected: 4,
    offered: 3,
    accepted: 2,
    inducted: 1,
    readyToDeploy: 1,
    placed: 0,
    active: 1,
    joiningRatio: "0.50",
  };

  it("shows the funnel per college with the joining ratio", async () => {
    route({
      "/dashboard": {
        colleges: [
          {
            collegeId: "c1",
            name: "Demo College",
            city: "Chennai",
            counts: COUNTS,
          },
        ],
        totals: COUNTS,
        readyToDeployTotal: 1,
      },
    });

    render(<RecruitmentDashboard />);

    const table = await screen.findByRole("table", {
      name: "Recruitment funnel by college",
    });
    expect(
      within(table).getByText("Demo College, Chennai"),
    ).toBeInTheDocument();
    expect(screen.getByText(/Joining ratio 50%/)).toBeInTheDocument();
  });

  it("explains how to start when there is no data", async () => {
    route({
      "/dashboard": {
        colleges: [],
        totals: { ...COUNTS, interviewed: 0, joiningRatio: null },
        readyToDeployTotal: 0,
      },
    });

    render(<RecruitmentDashboard />);

    expect(
      await screen.findByText(/Schedule a campus drive/),
    ).toBeInTheDocument();
  });
});
