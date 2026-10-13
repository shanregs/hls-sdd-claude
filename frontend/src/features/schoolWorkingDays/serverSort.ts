import type { GridSortModel } from "@mui/x-data-grid";

/** The grid columns that the server can sort by, keyed by the field the server knows. */
const SORT_KEYS: Record<string, string> = {
  schoolName: "school",
  zoneName: "zone",
  month: "month",
  workingDays: "workingDays",
  updatedAt: "updatedAt",
};

/** The sort parameter of the list call, e.g. "month,desc", or undefined for the default order. */
export function toServerSort(model: GridSortModel): string | undefined {
  const item = model[0];
  if (!item?.sort) return undefined;
  const key = SORT_KEYS[item.field];
  return key ? `${key},${item.sort}` : undefined;
}
