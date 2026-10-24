import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { axe } from "vitest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ClaimDetailPage } from "../ClaimDetailPage";
import { ExpensesReviewPage } from "../ExpensesReviewPage";
import { ROW, detail, json, renderAt } from "./fixtures";

const authFetch = vi.fn();
let roles: string[] = ["DIRECTOR"];
let actions: string[] = ["VIEW", "CREATE", "EDIT", "APPROVE", "EXPORT"];
let showItem = true;

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
        roles,
        dataScope: {},
        navigation: showItem
          ? [{ section: "OPERATIONS", items: [{ label: "Expenses", route: "/operations/expenses", actions }] }]
          : [],
      },
    }),
  };
});

const ROUTES = [
  { path: "/operations/expenses", element: <ExpensesReviewPage /> },
  { path: "/operations/expenses/:id", element: <ClaimDetailPage review /> },
];

const PENDING = {
  buckets: [
    { label: "Over 14 days", count: 1, total: "500.00" },
    { label: "8-14 days", count: 0, total: "0.00" },
    { label: "3-7 days", count: 2, total: "2400.00" },
    { label: "0-2 days", count: 1, total: "100.00" },
  ],
  count: 4,
  total: "3000.00",
  oldestSubmittedAt: "2026-09-01T05:00:00Z",
};

let current = detail({ allowedActions: ["APPROVE", "REJECT", "REMOVE_BILL"] });

beforeEach(() => {
  authFetch.mockReset();
  roles = ["DIRECTOR"];
  actions = ["VIEW", "CREATE", "EDIT", "APPROVE", "EXPORT"];
  showItem = true;
  current = detail({ allowedActions: ["APPROVE", "REJECT", "REMOVE_BILL"] });
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (url.endsWith("/approve") && init?.method === "POST") {
      current = detail({ status: "APPROVED", statusLabel: "Approved", allowedActions: ["REVERSE"] });
      return json(current);
    }
    if (url.endsWith("/reject") && init?.method === "POST") {
      current = detail({ status: "REJECTED", statusLabel: "Rejected", allowedActions: [] });
      return json(current);
    }
    if (url.includes("/pending")) return json(PENDING);
    if (url.startsWith("/api/v1/expenses/c-1")) return json(current);
    return json({ content: [ROW, { ...ROW, id: "c-2", claimantName: "Tara Teacher", remainingBudget: null }], page: 0, size: 50, totalElements: 2 });
  });
});

describe("Expenses review queue (spec 015 US3)", () => {
  it("lists the queue with age, bills, link, area and remaining budget", async () => {
    renderAt("/operations/expenses", ROUTES);
    expect(await screen.findByRole("link", { name: "Meera Manager" })).toHaveAttribute("href", "/operations/expenses/c-1");
    const table = screen.getByRole("table", { name: "Claims" });
    expect(within(table).getAllByText("4 days")).toHaveLength(2);
    expect(within(table).getAllByText("₹8,000.00")).toHaveLength(1);
    expect(within(table).getByText("No budget set")).toBeInTheDocument();
    expect(within(table).getAllByText("Not linked")).toHaveLength(2);
  });

  it("shows the pending ageing block and the export button only with the Export action", async () => {
    renderAt("/operations/expenses", ROUTES);
    expect(await screen.findByText(/Waiting for a decision: 4/)).toBeInTheDocument();
    expect(screen.getByText(/2 claims, ₹2,400\.00/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Export CSV" })).toBeInTheDocument();
  });

  it("hides Export from someone without the action", async () => {
    actions = ["VIEW"];
    renderAt("/operations/expenses", ROUTES);
    await screen.findByRole("link", { name: "Meera Manager" });
    expect(screen.queryByRole("button", { name: "Export CSV" })).not.toBeInTheDocument();
  });

  it("gives a Manager or Teacher no route", async () => {
    roles = ["MANAGER"];
    showItem = false;
    renderAt("/operations/expenses", ROUTES);
    await waitFor(() => expect(screen.queryByRole("heading", { name: "Expenses" })).not.toBeInTheDocument());
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });
});

describe("Deciding a claim (spec 015 US3)", () => {
  it("approves after a confirmation", async () => {
    const user = userEvent.setup();
    renderAt("/operations/expenses/c-1", ROUTES);
    await screen.findByRole("heading", { name: /Travel and Fuel/ });
    await user.click(screen.getByRole("button", { name: "Approve" }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Approve" }));
    expect(await screen.findByText("Approved.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reverse approval" })).toBeInTheDocument();
  });

  it("will not reject without a reason, and sends it when given", async () => {
    const user = userEvent.setup();
    renderAt("/operations/expenses/c-1", ROUTES);
    await screen.findByRole("heading", { name: /Travel and Fuel/ });
    await user.click(screen.getByRole("button", { name: "Reject" }));
    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Reject" }));
    expect(within(dialog).getByText("Reason for rejecting is required.")).toBeInTheDocument();
    expect(authFetch.mock.calls.some((c) => String(c[0]).endsWith("/reject"))).toBe(false);
    await user.type(within(dialog).getByLabelText(/Reason for rejecting/), "Bill is blurred");
    await user.click(within(dialog).getByRole("button", { name: "Reject" }));
    expect(await screen.findByText("Rejected.")).toBeInTheDocument();
    const sent = authFetch.mock.calls.find((c) => String(c[0]).endsWith("/reject"));
    expect(JSON.parse(String(sent?.[1].body))).toMatchObject({ reason: "Bill is blurred", version: 2 });
  });

  it("shows no decision buttons on the reviewer's own claim", async () => {
    current = detail({ ownClaim: true, allowedActions: ["WITHDRAW"] });
    renderAt("/operations/expenses/c-1", ROUTES);
    await screen.findByRole("heading", { name: /Travel and Fuel/ });
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Reject" })).not.toBeInTheDocument();
  });

  it("offers to remove a bill with a reason", async () => {
    const user = userEvent.setup();
    renderAt("/operations/expenses/c-1", ROUTES);
    await screen.findByRole("heading", { name: /Travel and Fuel/ });
    await user.click(screen.getByRole("button", { name: "Remove fuel-bill.jpg" }));
    expect(screen.getByRole("dialog", { name: "Remove this bill" })).toBeInTheDocument();
  });
});

describe("Accessibility", () => {
  it.each(["light", "dark"] as const)("has no violations in %s mode", async (mode) => {
    for (const path of ["/operations/expenses", "/operations/expenses/c-1"]) {
      const { container, unmount } = renderAt(path, ROUTES, mode);
      await screen.findAllByRole("heading");
      await new Promise((resolve) => setTimeout(resolve, 50));
      const results = await axe(container, { runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] } });
      expect(results.violations.map((v) => `${path} ${v.id}: ${v.help}`)).toEqual([]);
      unmount();
    }
  }, 60_000);
});
