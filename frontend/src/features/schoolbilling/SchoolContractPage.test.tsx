import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SchoolContractPage } from "./SchoolContractPage";

const authFetch = vi.fn();
let grants: Record<string, Set<string>> = {};

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));
vi.mock("../common/useGrantedActions", () => ({
  useGrantedActions: (route: string) => grants[route] ?? new Set<string>(),
}));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const POSITIONS = [
  {
    id: "p1",
    number: 1,
    title: "Maths PGT",
    salary: "20000.00",
    teacherId: "t1",
    teacherName: "Tara Teacher",
  },
  {
    id: "p2",
    number: 2,
    title: null,
    salary: "18000.00",
    teacherId: null,
    teacherName: null,
  },
];

const ACTIVE = {
  id: "c2",
  schoolId: "s1",
  state: "ACTIVE",
  status: "ACTIVE",
  salaryMode: "PER_TEACHER",
  teacherCount: 2,
  rate: null,
  signedOn: "2026-09-20",
  startsOn: "2026-10-01",
  endsOn: null,
  version: 0,
  positions: POSITIONS,
  signatories: [
    {
      id: "g1",
      party: "SCHOOL",
      name: "R. Kumar",
      designation: "Principal",
      userId: null,
    },
    {
      id: "g2",
      party: "HLS",
      name: "Manoj Manager",
      designation: "Zone Manager",
      userId: "u1",
    },
    {
      id: "g3",
      party: "HLS",
      name: "Divya Director",
      designation: "Director",
      userId: "u2",
    },
  ],
};

const EARLIER = {
  ...ACTIVE,
  id: "c1",
  status: "ENDED",
  startsOn: "2026-04-01",
  endsOn: "2026-09-30",
  positions: [
    {
      id: "p0",
      number: 1,
      title: null,
      salary: "17000.00",
      teacherId: null,
      teacherName: null,
    },
  ],
};

const PENDING = {
  ...ACTIVE,
  id: "c3",
  state: "RATE_PENDING",
  status: "MOU_PENDING",
  salaryMode: null,
  teacherCount: null,
  signedOn: null,
  positions: [],
  signatories: [],
};

function school(contracts: unknown[], unmapped: unknown[] = []) {
  return json({
    schoolId: "s1",
    schoolName: "Demo School One",
    zoneManagerName: "Manoj Manager",
    contracts,
    unmappedTeachers: unmapped,
  });
}

const CANDIDATES = [
  { userId: "u1", name: "Manoj Manager", designation: "Zone Manager" },
  { userId: "u2", name: "Divya Director", designation: "Director" },
];

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/operations/school-contracts/schools/s1"]}>
      <Routes>
        <Route
          path="/operations/school-contracts/schools/:schoolId"
          element={<SchoolContractPage />}
        />
        <Route path="/operations/school-contracts" element={<p>List page</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("SchoolContractPage (spec 012 US1)", () => {
  beforeEach(() => {
    authFetch.mockReset();
    grants = {
      "/operations/school-contracts": new Set(["VIEW", "CREATE", "EDIT"]),
      "/master-data/teachers": new Set(["VIEW", "EDIT"]),
    };
  });

  it("shows the contract in effect with its positions, signatories and who fills each position", async () => {
    authFetch.mockResolvedValue(school([ACTIVE, EARLIER]));
    renderPage();

    const contract = await screen.findByRole("region", {
      name: "Contract in effect",
    });
    const positions = within(contract).getByRole("table", {
      name: "Positions",
    });
    expect(within(positions).getByText("1 (Maths PGT)")).toBeInTheDocument();
    expect(within(positions).getByText("Tara Teacher")).toBeInTheDocument();
    expect(within(positions).getByText("Vacant")).toBeInTheDocument();
    expect(within(positions).getByText(/20,000\.00/)).toBeInTheDocument();
    expect(
      within(contract).getByText(/Signed on 20\/09\/2026/),
    ).toBeInTheDocument();
    expect(
      within(contract).getByText(/R\. Kumar \(Principal\)/),
    ).toBeInTheDocument();
    expect(
      within(contract).getByText(
        /Manoj Manager \(Zone Manager\), Divya Director \(Director\)/,
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("History")).toBeInTheDocument();
    expect(
      screen.getByRole("region", { name: "Earlier contract" }),
    ).toBeInTheDocument();
  });

  it("shows an upcoming contract apart from the one in effect and maps Teachers to it", async () => {
    const UPCOMING = {
      ...ACTIVE,
      id: "c9",
      startsOn: "2099-01-01",
      positions: [
        {
          id: "n1",
          number: 1,
          title: null,
          salary: "25000.00",
          teacherId: null,
          teacherName: null,
        },
      ],
    };
    authFetch.mockResolvedValue(school([UPCOMING, ACTIVE]));
    renderPage();
    const user = userEvent.setup();

    expect(
      await screen.findByRole("region", { name: "Contract in effect" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("region", {
        name: /Upcoming contract \(from 01\/01\/2099\)/,
      }),
    ).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: "Map Teachers to the next MoU" }),
    );
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByLabelText(/Tara Teacher/)).toBeInTheDocument();
  });

  it("offers the actions only to roles that hold them", async () => {
    authFetch.mockResolvedValue(school([ACTIVE]));
    grants = {
      "/operations/school-contracts": new Set(["VIEW"]),
      "/master-data/teachers": new Set(["VIEW", "EDIT"]),
    };
    renderPage();

    await screen.findByRole("region", { name: "Contract in effect" });
    expect(
      screen.queryByRole("button", { name: "Record a new MoU" }),
    ).toBeNull();
    expect(
      screen.queryByRole("button", { name: "End this contract" }),
    ).toBeNull();
    expect(
      screen.queryByRole("button", { name: "Cancel this contract" }),
    ).toBeNull();
  });

  it("says a School has no MoU yet and offers to record one", async () => {
    authFetch.mockResolvedValue(school([]));
    renderPage();

    expect(
      await screen.findByText(/This School has no MoU yet/),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Record a new MoU" }),
    ).toBeInTheDocument();
  });

  it("shows a pending contract and the Teachers not mapped yet", async () => {
    authFetch.mockResolvedValue(
      school([PENDING], [{ teacherId: "t9", teacherName: "Meena Selvi" }]),
    );
    renderPage();

    expect(await screen.findByText(/no MoU recorded yet/)).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Record the MoU" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /1 Teacher is at this School but not mapped.*Meena Selvi/,
      ),
    ).toBeInTheDocument();
  });

  it("records a new MoU: names a missing item, then saves with the signing details", async () => {
    let posted: Record<string, unknown> | null = null;
    authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
      if (url.includes("signatory-candidates")) return json(CANDIDATES);
      if (init?.method === "POST") {
        posted = JSON.parse(String(init.body));
        if (!posted?.signedOn) {
          return json(
            { reason: "The date the MoU was signed is required." },
            400,
          );
        }
        return json(ACTIVE, 201);
      }
      return school([]);
    });
    renderPage();
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "Record a new MoU" }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.clear(within(dialog).getByLabelText(/Number of Teachers/));
    await user.type(within(dialog).getByLabelText(/Number of Teachers/), "4");
    await user.type(
      within(dialog).getByLabelText(/Monthly salary for each Teacher/),
      "15000",
    );
    await user.type(
      within(dialog).getByLabelText(/School signatory 1 name/),
      "R. Kumar",
    );
    await user.type(
      within(dialog).getByLabelText(/School signatory 1 designation/),
      "Principal",
    );
    await user.click(
      await within(dialog).findByLabelText("Manoj Manager (Zone Manager)"),
    );
    await user.click(within(dialog).getByRole("button", { name: "Save MoU" }));

    expect(
      await within(dialog).findByText(
        "The date the MoU was signed is required.",
      ),
    ).toBeInTheDocument();

    await user.type(within(dialog).getByLabelText(/Date signed/), "2026-09-20");
    await user.click(within(dialog).getByRole("button", { name: "Save MoU" }));

    await screen.findByText("The MoU was recorded.");
    expect(posted).toMatchObject({
      teacherCount: 4,
      salaryMode: "SAME_FOR_ALL",
      rate: "15000",
      signedOn: "2026-09-20",
      schoolSignatories: [{ name: "R. Kumar", designation: "Principal" }],
      hlsSignatories: [{ userId: "u1", designation: "Zone Manager" }],
    });
  });

  it("asks for a salary on every position when the salary differs for each Teacher", async () => {
    authFetch.mockImplementation(async (url: string) =>
      url.includes("signatory-candidates") ? json(CANDIDATES) : school([]),
    );
    renderPage();
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "Record a new MoU" }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.clear(within(dialog).getByLabelText(/Number of Teachers/));
    await user.type(within(dialog).getByLabelText(/Number of Teachers/), "3");
    await user.click(
      within(dialog).getByLabelText("Different for each Teacher"),
    );

    expect(
      within(dialog).getByLabelText(/Position 1 monthly salary/),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByLabelText(/Position 3 monthly salary/),
    ).toBeInTheDocument();
    expect(
      within(dialog).queryByLabelText(/Monthly salary for each Teacher/),
    ).toBeNull();
  });
});
