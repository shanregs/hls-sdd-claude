import { render } from "@testing-library/react";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import type { ReactElement } from "react";
import { RouteGuard } from "../../../app/RouteGuard";
import { buildMuiTheme, type ThemeMode } from "../../../theme/tokens";
import type { ClaimDetail, ClaimRow } from "../expensesApi";

export function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

export const ROW: ClaimRow = {
  id: "c-1",
  claimantId: "u-1",
  claimantName: "Meera Manager",
  expenseDate: "2026-10-12",
  amount: "1200.00",
  category: "TRAVEL_FUEL",
  categoryLabel: "Travel and Fuel",
  zoneId: null,
  areaName: "Head office",
  status: "SUBMITTED",
  statusLabel: "Submitted",
  paidBy: "CLAIMANT",
  billCount: 1,
  noBillReason: null,
  linkLabel: null,
  note: "Fuel for school visits",
  submittedAt: "2026-10-13T05:00:00Z",
  ageDays: 4,
  remainingBudget: "8000.00",
  allowedActions: [],
};

export function detail(overrides: Partial<ClaimDetail> = {}): ClaimDetail {
  return {
    id: "c-1",
    claimantId: "u-1",
    claimantName: "Meera Manager",
    expenseDate: "2026-10-12",
    amount: "1200.00",
    category: "TRAVEL_FUEL",
    categoryLabel: "Travel and Fuel",
    zoneId: null,
    areaName: "Head office",
    note: "Fuel for school visits",
    paidBy: "CLAIMANT",
    reason: null,
    noBillReason: null,
    link: null,
    status: "SUBMITTED",
    statusLabel: "Submitted",
    submittedAt: "2026-10-13T05:00:00Z",
    ageDays: 4,
    decision: null,
    payment: null,
    payrollState: "NONE",
    bills: [
      {
        id: "b-1",
        name: "fuel-bill.jpg",
        contentType: "image/jpeg",
        sizeBytes: 1000,
        addedBy: "u-1",
        addedByName: "Meera Manager",
        addedAt: "2026-10-13T05:00:00Z",
        removed: false,
        removedByName: null,
        removedAt: null,
        removalReason: null,
      },
    ],
    history: [
      {
        at: "2026-10-13T05:00:00Z",
        actorId: "u-1",
        actorName: "Meera Manager",
        event: "SUBMITTED",
        reason: null,
        detail: null,
      },
    ],
    allowedActions: [],
    ownClaim: false,
    version: 2,
    ...overrides,
  };
}

/** Renders the element at a path inside the theme, router and route guard (the access model is mocked by the test). */
export function renderAt(
  path: string,
  routes: { path: string; element: ReactElement }[],
  mode: ThemeMode = "light",
) {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          {routes.map((r) => (
            <Route
              key={r.path}
              path={r.path}
              element={<RouteGuard>{r.element}</RouteGuard>}
            />
          ))}
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}
