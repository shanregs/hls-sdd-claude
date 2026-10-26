import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { BillingOverviewPage } from "../BillingOverviewPage";
import { CollectionDialog } from "../Dialogs";
import { SchoolLedgerPage } from "../SchoolLedgerPage";
import { rupees, dmy } from "../money";

const authFetch = vi.fn();
let actionsGranted = ["VIEW", "CREATE", "EDIT", "APPROVE", "EXPORT"];

vi.mock("../../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

vi.mock("../../../../access-model/useAccessModel", () => ({
  useAccessModel: () => ({
    loading: false,
    accessModel: {
      roles: ["ADMIN"],
      dataScope: {},
      navigation: [
        {
          section: "OPERATIONS",
          items: [
            {
              label: "School Billing",
              route: "/operations/school-billing",
              actions: actionsGranted,
            },
          ],
        },
      ],
    },
  }),
}));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body, blob: async () => new Blob(["x"]) } as Response;
}

const ZERO_ROW = {
  schoolName: "Green Valley",
  zoneName: "North",
  managerName: "Meera",
  notYetDue: 0,
  b0to30: 0,
  b31to60: 10000,
  b61to90: 0,
  over90: 0,
  total: 10000,
};

const OVERVIEW = {
  month: "2026-11",
  owed: 31000,
  collected: 10000,
  outstanding: 21000,
  overdue: 5000,
  advanceCredit: 0,
  schools: [
    {
      schoolId: "s1",
      schoolName: "Green Valley",
      zoneName: "North",
      managerName: "Meera",
      owed: 31000,
      collected: 10000,
      outstanding: 21000,
      overdue: 5000,
      advanceCredit: 0,
    },
  ],
};

function routeData(url: string) {
  if (url.includes("/overview")) return json(OVERVIEW);
  if (url.includes("/ageing")) return json({ asOf: "2026-11-30", totals: ZERO_ROW, schools: [ZERO_ROW], zones: [], managers: [] });
  if (url.includes("/not-billed"))
    return json([{ schoolId: "s2", schoolName: "Hill View", teacherName: "Ravi", reason: "MOU_PENDING" }]);
  return json({}, 404);
}

const DUE = {
  id: "d1",
  reference: "MD-202611-0001",
  schoolId: "s1",
  schoolName: "Green Valley",
  month: "2026-11",
  raisedOn: "2026-11-05",
  dueOn: "2026-11-20",
  total: 31000,
  adjustments: 0,
  collected: 10000,
  balance: 21000,
  status: "PART_PAID",
  overdue: true,
  daysOverdue: 4,
  cancelReason: null,
};

const LEDGER = {
  schoolId: "s1",
  schoolName: "Green Valley",
  owed: 31000,
  collected: 10000,
  outstanding: 21000,
  overdue: 21000,
  advanceCredit: 2500,
  dues: [DUE],
  collections: [
    {
      id: "c1",
      schoolId: "s1",
      amount: 12500,
      receivedOn: "2026-11-10",
      mode: "BANK",
      reference: "UTR1",
      receiver: "Accounts",
      reversesId: null,
      reversed: false,
      reason: null,
      allocated: 10000,
      unallocated: 2500,
    },
  ],
  adjustments: [],
};

beforeEach(() => {
  authFetch.mockReset();
  actionsGranted = ["VIEW", "CREATE", "EDIT", "APPROVE", "EXPORT"];
});

function renderOverview() {
  return render(
    <MemoryRouter initialEntries={["/operations/school-billing"]}>
      <BillingOverviewPage />
    </MemoryRouter>,
  );
}

describe("money helpers", () => {
  it("shows rupees with Indian grouping and dates as DD/MM/YYYY", () => {
    expect(rupees(1234567.5)).toBe("₹12,34,567.50");
    expect(rupees(0)).toBe("₹0.00");
    expect(dmy("2026-11-05")).toBe("05/11/2026");
  });
});

describe("BillingOverviewPage", () => {
  it("shows the totals, the School rows and the ageing and not-billed tabs", async () => {
    authFetch.mockImplementation(async (url: string) => routeData(url));
    renderOverview();

    expect(await screen.findByText("Green Valley")).toBeInTheDocument();
    expect(screen.getByText("₹31,000.00", { selector: "strong" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("tab", { name: "Ageing" }));
    expect(await screen.findByRole("table", { name: "Ageing of what is outstanding" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("tab", { name: "Not billed" }));
    expect(await screen.findByText("MoU pending")).toBeInTheDocument();
  });

  it("offers raising and settings only to those who may edit, and export to those who may export", async () => {
    authFetch.mockImplementation(async (url: string) => routeData(url));
    actionsGranted = ["VIEW", "CREATE", "EXPORT"];
    renderOverview();
    await screen.findByText("Green Valley");

    expect(screen.queryByRole("button", { name: "Raise monthly dues" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Settings" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Export CSV" })).toBeInTheDocument();
  });

  it("hides export from someone without the Export action", async () => {
    authFetch.mockImplementation(async (url: string) => routeData(url));
    actionsGranted = ["VIEW"];
    renderOverview();
    await screen.findByText("Green Valley");

    expect(screen.queryByRole("button", { name: "Export CSV" })).not.toBeInTheDocument();
  });

  it("raises the monthly dues after confirmation and reports the result", async () => {
    authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
      if (init?.method === "POST") return json({ raised: [DUE], skipped: [], notBilled: [] });
      return routeData(url);
    });
    renderOverview();
    await screen.findByText("Green Valley");

    await userEvent.click(screen.getByRole("button", { name: "Raise monthly dues" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/Nothing is sent to the Schools/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Raise monthly dues" }));

    expect(await within(dialog).findByText(/1 monthly dues raised/)).toBeInTheDocument();
  });

  it("shows the server's reason when the overview cannot be loaded", async () => {
    authFetch.mockResolvedValue(json({ reason: "School not found." }, 404));
    renderOverview();

    expect(await screen.findAllByText("School not found.")).not.toHaveLength(0);
  });
});

describe("SchoolLedgerPage", () => {
  function renderLedger() {
    return render(
      <MemoryRouter initialEntries={["/operations/school-billing/s1"]}>
        <Routes>
          <Route path="/operations/school-billing/:schoolId" element={<SchoolLedgerPage />} />
        </Routes>
      </MemoryRouter>,
    );
  }

  it("lists dues, collections and the advance credit, and lets a person with Create record a collection", async () => {
    authFetch.mockImplementation(async (url: string) => (url.includes("/ledger") ? json(LEDGER) : json({}, 404)));
    renderLedger();

    expect(await screen.findByRole("heading", { name: "Green Valley" })).toBeInTheDocument();
    expect(screen.getByText("MD-202611-0001")).toBeInTheDocument();
    expect(screen.getByText("4 days overdue")).toBeInTheDocument();
    expect(screen.getByText("₹2,500.00", { selector: "strong" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Record a collection" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Allocate" })).toBeInTheDocument();
  });

  it("gives a Zone Manager no way to reverse, adjust or cancel", async () => {
    actionsGranted = ["VIEW", "CREATE", "EXPORT"];
    authFetch.mockImplementation(async (url: string) => {
      if (url.includes("/ledger")) return json(LEDGER);
      if (url.includes("/dues/d1"))
        return json({ due: DUE, lines: [], adjustmentEntries: [], allocations: [], followUpTaskId: null });
      return json({}, 404);
    });
    renderLedger();
    await screen.findByText("MD-202611-0001");

    expect(screen.queryByRole("button", { name: "Reverse" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Open" }));
    expect(await screen.findByRole("region", { name: "Monthly due detail" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Add an adjustment" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cancel this monthly due" })).not.toBeInTheDocument();
  });

  it("shows the working of each line and cancels a due with a reason", async () => {
    authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
      if (init?.method === "POST") return json({ ...DUE, status: "CANCELLED" });
      if (url.includes("/ledger")) return json(LEDGER);
      return json({
        due: DUE,
        lines: [
          {
            id: "l1",
            teacherId: "t1",
            teacherName: "Asha",
            positionNumber: 1,
            amount: 15000,
            fullMonth: false,
            working: "16000.00 x 16.0 working days at the School / 26.0 working days in the month",
          },
        ],
        adjustmentEntries: [],
        allocations: [],
        followUpTaskId: null,
      });
    });
    renderLedger();
    await screen.findByText("MD-202611-0001");

    await userEvent.click(screen.getByRole("button", { name: "Open" }));
    expect(await screen.findByText(/26.0 working days in the month/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Cancel this monthly due" }));
    const dialog = await screen.findByRole("dialog");
    const confirm = within(dialog).getByRole("button", { name: "Cancel monthly due" });
    expect(confirm).toBeDisabled();
    await userEvent.type(within(dialog).getByLabelText(/Reason/), "Raised in error");
    expect(confirm).toBeEnabled();
    await userEvent.click(confirm);

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });
});

describe("CollectionDialog", () => {
  it("warns about a likely duplicate and records it only after confirmation", async () => {
    const onSaved = vi.fn();
    authFetch
      .mockResolvedValueOnce(json({ reason: "A collection with the same details exists.", warning: "LIKELY_DUPLICATE" }, 409))
      .mockResolvedValueOnce(json({ id: "c9" }, 201));
    render(<CollectionDialog schoolId="s1" onClose={vi.fn()} onSaved={onSaved} />);

    await userEvent.type(screen.getByLabelText(/Amount/), "1000");
    await userEvent.click(screen.getByRole("button", { name: "Record collection" }));
    expect(await screen.findByText(/same details exists/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Record anyway" }));

    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    const second = JSON.parse(authFetch.mock.calls[1][1].body as string) as { confirmDuplicate: boolean };
    expect(second.confirmDuplicate).toBe(true);
  });

  it("shows other refusals as errors without offering to continue", async () => {
    authFetch.mockResolvedValueOnce(json({ reason: "The date received cannot be in the future." }, 400));
    render(<CollectionDialog schoolId="s1" onClose={vi.fn()} onSaved={vi.fn()} />);

    await userEvent.type(screen.getByLabelText(/Amount/), "1000");
    await userEvent.click(screen.getByRole("button", { name: "Record collection" }));

    expect(await screen.findByText(/cannot be in the future/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Record anyway" })).not.toBeInTheDocument();
  });
});
