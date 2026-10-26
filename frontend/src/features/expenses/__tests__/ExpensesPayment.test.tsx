import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { axe } from "vitest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ClaimDetailPage } from "../ClaimDetailPage";
import { detail, json, renderAt } from "./fixtures";
import type { ClaimDetail } from "../expensesApi";

const authFetch = vi.fn();
let current: ClaimDetail;

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

vi.mock("../../../access-model/useAccessModel", async () => {
  const actual = await vi.importActual<
    typeof import("../../../access-model/useAccessModel")
  >("../../../access-model/useAccessModel");
  return {
    ...actual,
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
                label: "Expenses",
                route: "/operations/expenses",
                actions: ["VIEW", "APPROVE", "PROCESS", "EXPORT"],
              },
            ],
          },
        ],
      },
    }),
  };
});

const ROUTES = [
  { path: "/operations/expenses/:id", element: <ClaimDetailPage review /> },
];

function approved(overrides: Partial<ClaimDetail> = {}): ClaimDetail {
  return detail({
    status: "APPROVED",
    statusLabel: "Approved",
    decision: {
      by: "u-9",
      byName: "Dev Director",
      at: "2026-10-14T05:00:00Z",
      reason: null,
    },
    payrollState: "WAITING",
    allowedActions: ["REVERSE", "RECORD_PAID", "REMOVE_BILL"],
    ...overrides,
  });
}

beforeEach(() => {
  authFetch.mockReset();
  current = approved();
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (url.endsWith("/record-paid") && init?.method === "POST") {
      current = detail({
        status: "PAID",
        statusLabel: "Paid",
        payment: {
          how: "OUTSIDE",
          paidOn: "2026-10-20",
          mode: "BANK_TRANSFER",
          reference: "UTR1",
          payrollRunId: null,
          payrollMonth: null,
          recordedByName: "Admin A",
        },
        allowedActions: [],
      });
      return json(current);
    }
    if (url.endsWith("/reverse") && init?.method === "POST") {
      current = detail({ allowedActions: ["APPROVE", "REJECT"] });
      return json(current);
    }
    return json(current);
  });
});

describe("Payment and reversal (spec 015 US4)", () => {
  it("shows the claim as owed with where it is in payroll, and both actions to an Admin", async () => {
    renderAt("/operations/expenses/c-1", ROUTES);
    expect(await screen.findByText(/Owed to Meera Manager/)).toHaveTextContent(
      "Waiting for the next pay run",
    );
    expect(
      screen.getByRole("button", { name: "Reverse approval" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Record as paid" }),
    ).toBeInTheDocument();
  });

  it("gives a Director Reverse but not Record as paid (the server sends the actions)", async () => {
    current = approved({ allowedActions: ["REVERSE", "REMOVE_BILL"] });
    renderAt("/operations/expenses/c-1", ROUTES);
    await screen.findByText(/Owed to Meera Manager/);
    expect(
      screen.getByRole("button", { name: "Reverse approval" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Record as paid" }),
    ).not.toBeInTheDocument();
  });

  it("explains why the actions are missing while a pay run awaiting approval holds the claim", async () => {
    current = approved({
      payrollState: "HELD_AWAITING_APPROVAL",
      allowedActions: ["REMOVE_BILL"],
    });
    renderAt("/operations/expenses/c-1", ROUTES);
    expect(
      await screen.findByText(
        /awaiting approval, so it cannot be reversed or recorded as paid now/,
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Reverse approval" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Record as paid" }),
    ).not.toBeInTheDocument();
  });

  it("tells the Admin to record a payment for a person with no pay run", async () => {
    current = approved({ payrollState: "NONE" });
    renderAt("/operations/expenses/c-1", ROUTES);
    expect(
      await screen.findByText(
        /no pay run, so it must be recorded as paid outside payroll/,
      ),
    ).toBeInTheDocument();
  });

  it("records a payment outside payroll with date, mode and reference", async () => {
    const user = userEvent.setup();
    renderAt("/operations/expenses/c-1", ROUTES);
    await screen.findByText(/Owed to Meera Manager/);
    await user.click(screen.getByRole("button", { name: "Record as paid" }));
    const dialog = screen.getByRole("dialog", {
      name: "Record as paid outside payroll",
    });
    await user.type(within(dialog).getByLabelText("Reference"), "UTR1");
    await user.click(
      within(dialog).getByRole("button", { name: "Record payment" }),
    );
    expect(await screen.findByText("Recorded as paid.")).toBeInTheDocument();
    const payment = screen.getByLabelText("Payment");
    expect(payment).toHaveTextContent("Paid outside payroll");
    expect(payment).toHaveTextContent("UTR1");
    const sent = authFetch.mock.calls.find((c) =>
      String(c[0]).endsWith("/record-paid"),
    );
    expect(JSON.parse(String(sent?.[1].body))).toMatchObject({
      mode: "BANK_TRANSFER",
      reference: "UTR1",
      version: 2,
    });
  });

  it("needs a reason to reverse an approval", async () => {
    const user = userEvent.setup();
    renderAt("/operations/expenses/c-1", ROUTES);
    await screen.findByText(/Owed to Meera Manager/);
    await user.click(screen.getByRole("button", { name: "Reverse approval" }));
    const dialog = screen.getByRole("dialog", {
      name: "Reverse this approval",
    });
    await user.click(
      within(dialog).getByRole("button", { name: "Reverse approval" }),
    );
    expect(
      within(dialog).getByText("Reason for reversing is required."),
    ).toBeInTheDocument();
    await user.type(
      within(dialog).getByLabelText(/Reason for reversing/),
      "Wrong category",
    );
    await user.click(
      within(dialog).getByRole("button", { name: "Reverse approval" }),
    );
    expect(await screen.findByText("Approval reversed.")).toBeInTheDocument();
  });

  it("shows a claim paid with payroll with its month", async () => {
    current = detail({
      status: "PAID",
      statusLabel: "Paid",
      payment: {
        how: "PAYROLL",
        paidOn: "2026-09-01",
        mode: null,
        reference: null,
        payrollRunId: "r-1",
        payrollMonth: "2026-09-01",
        recordedByName: null,
      },
      allowedActions: [],
    });
    renderAt("/operations/expenses/c-1", ROUTES);
    expect(await screen.findByText("Paid with payroll")).toBeInTheDocument();
    expect(screen.getByText("01/09/2026")).toBeInTheDocument();
  });
});

describe("Accessibility", () => {
  it.each(["light", "dark"] as const)(
    "has no violations with a dialog open in %s mode",
    async (mode) => {
      const user = userEvent.setup();
      const { container } = renderAt("/operations/expenses/c-1", ROUTES, mode);
      await screen.findByText(/Owed to Meera Manager/);
      let results = await axe(container, {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
      await user.click(screen.getByRole("button", { name: "Record as paid" }));
      results = await axe(screen.getByRole("dialog"), {
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
      });
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    60_000,
  );
});
