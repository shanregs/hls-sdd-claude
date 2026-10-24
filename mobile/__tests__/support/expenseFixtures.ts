import type { AccessModel } from "../../src/api/accessModelApi";
import type { Area, BillRef, ClaimDetail, ClaimRow, LinkOption, MyList } from "../../src/api/expensesApi";

const item = (label: string, route: string, actions = ["VIEW"]) => ({ label, route, actions });

export const EXPENSES_ROUTE = "/operations/my-expenses";
export const REVIEW_ROUTE = "/operations/expenses";

interface Grants {
  create?: boolean;
  edit?: boolean;
  delete?: boolean;
}

const grants = (g: Grants = {}) => [
  "VIEW",
  ...(g.create === false ? [] : ["CREATE"]),
  ...(g.edit === false ? [] : ["EDIT"]),
  ...(g.delete === false ? [] : ["DELETE"]),
];

const account = {
  section: "ACCOUNT",
  items: [
    item("Profile", "/account/profile", ["VIEW", "EDIT"]),
    item("Notifications", "/account/notifications", ["VIEW", "DELETE"]),
    item("Settings", "/account/settings"),
  ],
};

/** The menu of a user with OPERATIONS → My Expenses (module MY_EXPENSES), as the server's catalog gives it. */
export function expenseModel(options: { grants?: Grants; roles?: string[]; withReview?: boolean } = {}): AccessModel {
  return {
    roles: options.roles ?? ["MANAGER"],
    navigation: [
      { section: "Dashboard", items: [item("Dashboard", "/dashboard")] },
      {
        section: "OPERATIONS",
        items: [
          item("My Expenses", EXPENSES_ROUTE, grants(options.grants)),
          ...(options.withReview ? [item("Expenses", REVIEW_ROUTE, ["VIEW", "APPROVE"])] : []),
        ],
      },
      account,
    ],
    dataScope: { MY_EXPENSES: "OWN" },
  };
}

export const managerExpenseModel = (): AccessModel => expenseModel({ roles: ["MANAGER"] });
export const teacherExpenseModel = (): AccessModel => expenseModel({ roles: ["TEACHER"] });
/** A Director also holds the review item on the web; the phone must still show only My Expenses. */
export const directorExpenseModel = (): AccessModel => expenseModel({ roles: ["DIRECTOR"], withReview: true });
/** System has no expense path at all. */
export const systemExpenseModel = (): AccessModel => ({
  roles: ["SYSTEM"],
  navigation: [{ section: "Dashboard", items: [item("Dashboard", "/dashboard")] }, account],
  dataScope: {},
});

export const ME = { id: "u-1", displayName: "Tara", roles: ["TEACHER"] };

export function claimRow(overrides: Partial<ClaimRow> = {}): ClaimRow {
  const status = overrides.status ?? "DRAFT";
  return {
    id: "c-1",
    claimantId: ME.id,
    claimantName: "Tara",
    expenseDate: "2026-10-22",
    amount: "850.00",
    category: "TRAVEL_FUEL",
    categoryLabel: CATEGORY_LABELS[overrides.category ?? "TRAVEL_FUEL"],
    zoneId: null,
    areaName: "Head office",
    status,
    statusLabel: STATUS_LABELS[status],
    paidBy: "CLAIMANT",
    billCount: 0,
    noBillReason: null,
    linkLabel: null,
    note: null,
    submittedAt: null,
    ageDays: null,
    allowedActions: ["EDIT", "SUBMIT", "DELETE", "ADD_BILL"],
    ...overrides,
  };
}

export const STATUS_LABELS = {
  DRAFT: "Draft",
  SUBMITTED: "Submitted",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  PAID: "Paid",
  PAID_BY_COMPANY: "Paid by company",
} as const;

export const CATEGORY_LABELS = {
  RENT_UTILITIES: "Rent and Utilities",
  TRAVEL_FUEL: "Travel and Fuel",
  TRAINING_STAY: "Training Stay",
  MARKETING: "Marketing",
  OFFICE_SUPPLIES: "Office Supplies",
  OTHER: "Other",
} as const;

export function bill(overrides: Partial<BillRef> = {}): BillRef {
  return {
    id: "b-1",
    name: "bill-1.jpg",
    contentType: "image/jpeg",
    sizeBytes: 340_000,
    addedBy: ME.id,
    addedByName: "Tara",
    addedAt: "2026-10-23T04:00:00Z",
    removed: false,
    removedByName: null,
    removedAt: null,
    removalReason: null,
    ...overrides,
  };
}

export function claimDetail(overrides: Partial<ClaimDetail> = {}): ClaimDetail {
  const row = claimRow({ billCount: (overrides.bills ?? []).filter((b) => !b.removed).length, ...overrides });
  return {
    ...row,
    reason: null,
    link: null,
    decision: null,
    payment: null,
    payrollState: null,
    bills: [],
    history: [{ at: "2026-10-23T04:00:00Z", actorId: ME.id, actorName: "Tara", event: "CREATED", reason: null, detail: null }],
    ownClaim: true,
    version: 0,
    createdAt: "2026-10-23T04:00:00Z",
    ...overrides,
  };
}

export function linkOption(overrides: Partial<LinkOption> = {}): LinkOption {
  return {
    kind: "SCHOOL_VISIT",
    kindLabel: "School visit",
    ref: "link-visit-1",
    date: "2026-10-22",
    label: "School visit: Green Valley Public School",
    ...overrides,
  };
}

export function area(overrides: Partial<Area> = {}): Area {
  return { id: "zone-1", name: "Demo Zone", ...overrides };
}

export function myList(overrides: Partial<MyList> = {}): MyList {
  const content = overrides.content ?? [];
  return { content, page: 0, size: 50, totalElements: content.length, owed: "0.00", ...overrides };
}
