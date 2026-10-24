import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MovementHistory } from "../MovementHistory";
import { ReplacementForm } from "../ReplacementForm";
import { RequestDetail } from "../RequestDetail";
import { TransferForm } from "../TransferForm";
import { TransfersPage } from "../TransfersPage";
import type { MovementRequest } from "../transfersApi";

const authFetch = vi.fn();
let actionsGranted = ["VIEW", "CREATE", "EDIT", "APPROVE"];

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

vi.mock("../../../access-model/useAccessModel", () => ({
  useAccessModel: () => ({
    loading: false,
    accessModel: {
      roles: ["DIRECTOR"],
      dataScope: {},
      navigation: [
        {
          section: "OPERATIONS",
          items: [
            {
              label: "Transfers & Replacements",
              route: "/operations/transfers",
              actions: actionsGranted,
            },
          ],
        },
      ],
    },
  }),
}));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const REQUEST: MovementRequest = {
  id: "r1",
  kind: "TRANSFER",
  status: "PENDING",
  statusNote: null,
  teacher: { id: "t1", name: "Asha Teacher" },
  fromSchool: { id: "s1", name: "Green Valley" },
  toSchool: { id: "s2", name: "Hill View" },
  position: null,
  replacementTeacher: null,
  outgoingDestinationSchool: null,
  effectiveOn: "2026-11-03",
  reasonCategory: "MANAGER_REBALANCING",
  note: null,
  requestedBy: { id: "u1", name: "Meera Manager" },
  requestedAt: "2026-10-23T05:00:00Z",
  decidedBy: null,
  decidedAt: null,
  decisionReason: null,
  version: 3,
  actions: ["APPROVE", "REJECT", "CANCEL", "CHANGE"],
  checks: [{ ok: false, text: "The receiving School is inactive." }],
  history: [
    {
      action: "RAISED",
      fromStatus: null,
      toStatus: "PENDING",
      actor: "Meera Manager",
      note: null,
      at: "2026-10-23T05:00:00Z",
    },
  ],
};

beforeEach(() => {
  authFetch.mockReset();
  actionsGranted = ["VIEW", "CREATE", "EDIT", "APPROVE"];
});

describe("RequestDetail", () => {
  it("shows buttons only for the actions the server sent", () => {
    render(
      <RequestDetail
        request={{ ...REQUEST, actions: ["CANCEL"] }}
        onClose={() => {}}
        onChanged={() => {}}
      />,
    );
    expect(
      screen.getByRole("button", { name: "Cancel request" }),
    ).toBeVisible();
    expect(screen.queryByRole("button", { name: "Approve" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Reject" })).toBeNull();
  });

  it("shows the checks and the history with dates as DD/MM/YYYY", () => {
    render(
      <RequestDetail
        request={REQUEST}
        onClose={() => {}}
        onChanged={() => {}}
      />,
    );
    expect(
      screen.getByText(/Problem: The receiving School is inactive/),
    ).toBeVisible();
    expect(screen.getByText(/Effective: 03\/11\/2026/)).toBeVisible();
    expect(screen.getByText(/raised by Meera Manager/)).toBeVisible();
  });

  it("requires a reason to reject and sends the version", async () => {
    authFetch.mockResolvedValue(
      json({ ...REQUEST, status: "REJECTED", actions: [] }),
    );
    const onChanged = vi.fn();
    render(
      <RequestDetail
        request={REQUEST}
        onClose={() => {}}
        onChanged={onChanged}
      />,
    );
    await userEvent.click(screen.getByRole("button", { name: "Reject" }));
    await userEvent.click(
      screen.getByRole("button", { name: "Confirm rejection" }),
    );
    expect(screen.getByText("Give a reason.")).toBeVisible();
    expect(authFetch).not.toHaveBeenCalled();
    await userEvent.type(
      screen.getByLabelText(/Reason for rejecting/),
      "Not needed",
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Confirm rejection" }),
    );
    await waitFor(() => expect(onChanged).toHaveBeenCalled());
    const [url, init] = authFetch.mock.calls[0];
    expect(url).toBe("/api/v1/transfers/r1/reject");
    expect(JSON.parse(init.body)).toEqual({ version: 3, reason: "Not needed" });
  });

  it("requires a reason to cancel", async () => {
    render(
      <RequestDetail
        request={REQUEST}
        onClose={() => {}}
        onChanged={() => {}}
      />,
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Cancel request" }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Confirm cancellation" }),
    );
    expect(screen.getByText("Give a reason.")).toBeVisible();
  });

  it("asks to reload when the version changed", async () => {
    authFetch.mockResolvedValue(
      json({ reason: "This request changed. Reload and try again." }, 409),
    );
    render(
      <RequestDetail
        request={REQUEST}
        onClose={() => {}}
        onChanged={() => {}}
      />,
    );
    await userEvent.click(screen.getByRole("button", { name: "Approve" }));
    expect(
      await screen.findByText("This request changed, reload"),
    ).toBeVisible();
  });
});

describe("TransferForm", () => {
  beforeEach(() => {
    authFetch.mockImplementation(async (url: string) => {
      if (url.startsWith("/api/v1/teachers"))
        return json({
          content: [
            {
              id: "t1",
              name: "Asha Teacher",
              school: { id: "s1", name: "Green Valley" },
            },
          ],
          page: 0,
          size: 200,
          totalElements: 1,
        });
      if (url.startsWith("/api/v1/schools"))
        return json({
          content: [
            { id: "s1", name: "Green Valley" },
            { id: "s2", name: "Hill View" },
          ],
          page: 0,
          size: 200,
          totalElements: 2,
        });
      if (url.includes("/vacancies"))
        return json([{ positionId: "p1", number: 2, salary: null }]);
      if (url.endsWith("/check"))
        return json({
          ok: false,
          problems: ["That position is already filled on those dates."],
        });
      return json({});
    });
  });

  it("asks for the required fields and a note for a School request", async () => {
    render(<TransferForm onClose={() => {}} onSaved={() => {}} />);
    await userEvent.click(
      screen.getByRole("button", { name: "Request transfer" }),
    );
    expect(screen.getByText("Choose the Teacher.")).toBeVisible();
  });

  it("shows the problems from the server check and lists vacant positions", async () => {
    render(<TransferForm onClose={() => {}} onSaved={() => {}} />);
    await userEvent.click(screen.getByLabelText(/^Teacher/));
    await userEvent.click(
      await screen.findByRole("option", { name: /Asha Teacher/ }),
    );
    await userEvent.click(screen.getByLabelText(/Receiving School/));
    await userEvent.click(
      await screen.findByRole("option", { name: "Hill View" }),
    );
    const date = screen.getByLabelText(/Effective date/);
    await userEvent.type(date, "2099-01-05");
    expect(await screen.findByText(/already filled/)).toBeVisible();
    expect(await screen.findByLabelText("Position")).toBeInTheDocument();
  });

  it("is reachable by keyboard: fields are labelled and focusable", () => {
    render(<TransferForm onClose={() => {}} onSaved={() => {}} />);
    for (const label of [
      /^Teacher/,
      /Receiving School/,
      /Effective date/,
      /^Reason/,
      /Note/,
    ]) {
      const field = screen.getByLabelText(label);
      field.focus();
      expect(field).toHaveFocus();
    }
  });
});

describe("ReplacementForm", () => {
  it("shows the free candidates and the optional destination", async () => {
    authFetch.mockImplementation(async (url: string) => {
      if (url.startsWith("/api/v1/teachers"))
        return json({ content: [], page: 0, size: 200, totalElements: 0 });
      if (url.startsWith("/api/v1/schools"))
        return json({
          content: [{ id: "s1", name: "Green Valley" }],
          page: 0,
          size: 200,
          totalElements: 1,
        });
      if (url.includes("/candidates"))
        return json([{ id: "c1", name: "Free Teacher", status: "ACTIVE" }]);
      return json({ ok: true, problems: [] });
    });
    render(<ReplacementForm onClose={() => {}} onSaved={() => {}} />);
    expect(
      screen.getByLabelText(/Where the replaced Teacher goes/),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText(/^School/));
    await userEvent.click(
      await screen.findByRole("option", { name: "Green Valley" }),
    );
    await userEvent.type(screen.getByLabelText(/Effective date/), "2099-01-05");
    await waitFor(() =>
      expect(
        authFetch.mock.calls.some((c) => String(c[0]).includes("/candidates")),
      ).toBe(true),
    );
    await userEvent.click(screen.getByLabelText(/Replacement Teacher/));
    expect(
      await screen.findByRole("option", { name: "Free Teacher" }),
    ).toBeVisible();
  });

  it("asks for the Teacher to replace first", async () => {
    authFetch.mockResolvedValue(
      json({ content: [], page: 0, size: 200, totalElements: 0 }),
    );
    render(<ReplacementForm onClose={() => {}} onSaved={() => {}} />);
    await userEvent.click(
      screen.getByRole("button", { name: "Request replacement" }),
    );
    expect(screen.getByText("Choose the Teacher to replace.")).toBeVisible();
  });
});

describe("TransfersPage", () => {
  function listing(rows: MovementRequest[]) {
    authFetch.mockImplementation(async () =>
      json({ content: rows, page: 0, size: 50, totalElements: rows.length }),
    );
  }

  it("defaults to Pending and Approved and shows the rows", async () => {
    listing([REQUEST]);
    render(<TransfersPage />);
    expect(await screen.findByText("Asha Teacher")).toBeVisible();
    expect(authFetch.mock.calls[0][0]).toContain("status=PENDING%2CAPPROVED");
  });

  it("changes the filter and shows the empty state", async () => {
    listing([]);
    render(<TransfersPage />);
    expect(await screen.findByText("No requests here.")).toBeVisible();
    await userEvent.click(screen.getByLabelText("Status"));
    await userEvent.click(
      await screen.findByRole("option", { name: "Rejected" }),
    );
    await waitFor(() =>
      expect(
        authFetch.mock.calls.some((c) =>
          String(c[0]).includes("status=REJECTED"),
        ),
      ).toBe(true),
    );
  });

  it("shows an error", async () => {
    authFetch.mockResolvedValue(json({ reason: "Nope" }, 500));
    render(<TransfersPage />);
    expect(await screen.findByText("Nope")).toBeVisible();
  });

  it("hides the create buttons without CREATE", async () => {
    actionsGranted = ["VIEW"];
    listing([]);
    render(<TransfersPage />);
    await screen.findByText("No requests here.");
    expect(
      screen.queryByRole("button", { name: "Request a transfer" }),
    ).toBeNull();
    expect(
      screen.queryByRole("button", { name: "Replace a Teacher" }),
    ).toBeNull();
  });

  it("shows both create buttons with CREATE", async () => {
    listing([]);
    render(<TransfersPage />);
    expect(
      await screen.findByRole("button", { name: "Request a transfer" }),
    ).toBeVisible();
    expect(
      screen.getByRole("button", { name: "Replace a Teacher" }),
    ).toBeVisible();
  });
});

describe("MovementHistory", () => {
  const LINES = [
    {
      teacher: { id: "t1", name: "Asha Teacher" },
      school: { id: "s1", name: "Green Valley" },
      positionNumber: 1,
      from: "2026-04-01",
      to: "2026-11-02",
      kind: "INITIAL",
      requestId: null,
      reason: null,
      requestedBy: null,
      approvedBy: null,
      otherTeacher: null,
      otherSchool: null,
    },
    {
      teacher: { id: "t1", name: "Asha Teacher" },
      school: { id: "s2", name: "Hill View" },
      positionNumber: null,
      from: "2026-11-03",
      to: null,
      kind: "TRANSFER",
      requestId: "r1",
      reason: "MANAGER_REBALANCING",
      requestedBy: "Meera Manager",
      approvedBy: "Dev Director",
      otherTeacher: null,
      otherSchool: null,
    },
  ];

  it("renders the lines with dates as DD/MM/YYYY", async () => {
    authFetch.mockResolvedValue(json(LINES));
    render(<MovementHistory teacherId="t1" />);
    const list = await screen.findByRole("list", { name: "Movement history" });
    expect(
      within(list).getByText(
        /01\/04\/2026 to 02\/11\/2026 - Initial assignment/,
      ),
    ).toBeVisible();
    expect(
      within(list).getByText(/03\/11\/2026 to now - Transfer/),
    ).toBeVisible();
    expect(authFetch.mock.calls[0][0]).toBe(
      "/api/v1/transfers/teachers/t1/movements",
    );
  });

  it("is hidden without the View grant", () => {
    actionsGranted = [];
    const { container } = render(<MovementHistory teacherId="t1" />);
    expect(container).toBeEmptyDOMElement();
    expect(authFetch).not.toHaveBeenCalled();
  });
});
