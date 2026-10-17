import { listVisits, type EffectiveVisitStatus, type Visit } from "../api/marketingApi";
import { useMonthView, type MonthViewState } from "../attendance/useMonthView";
import { monthBounds } from "../attendance/monthRange";

/** The status filter of the list; "ALL" shows every visit. */
export type VisitFilter = "ALL" | EffectiveVisitStatus;

export const VISIT_FILTERS: { value: VisitFilter; label: string }[] = [
  { value: "ALL", label: "All" },
  { value: "PLANNED", label: "Planned" },
  { value: "COMPLETED", label: "Completed" },
  { value: "CANCELLED", label: "Cancelled" },
  { value: "MISSED", label: "Missed" },
];

/** One month of the Manager's visits (spec 034 FR-001). A failed load drops the rows; a quiet reload keeps them until the new ones arrive. */
export function useVisits(month: string): MonthViewState<Visit[]> {
  return useMonthView((m) => {
    const { from, to } = monthBounds(m);
    return listVisits(from, to);
  }, month);
}

export interface VisitGroups {
  today: Visit[];
  later: Visit[];
  earlier: Visit[];
}

/**
 * Today's visits first, then later ones in date order, then earlier ones newest first. Only the visit's date against
 * today's date is used, to order the list: the status shown, and Missed, are the server's.
 */
export function groupVisits(visits: Visit[], today: string): VisitGroups {
  const byDate = (a: Visit, b: Visit) => a.date.localeCompare(b.date);
  return {
    today: visits.filter((v) => v.date === today).sort(byDate),
    later: visits.filter((v) => v.date > today).sort(byDate),
    earlier: visits.filter((v) => v.date < today).sort((a, b) => byDate(b, a)),
  };
}

/** Keeps the visits with the chosen effective status; a display filter, the server has no status parameter. */
export function filterVisits(visits: Visit[], filter: VisitFilter): Visit[] {
  return filter === "ALL" ? visits : visits.filter((v) => v.effectiveStatus === filter);
}
