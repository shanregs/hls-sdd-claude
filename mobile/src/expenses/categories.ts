import type { CategoryKey } from "../api/expensesApi";

/**
 * The six expense categories (spec 036 research §9, gap G1: the server has no list). The flags are early hints only;
 * the server's refusals decide.
 */
export interface CategoryInfo {
  key: CategoryKey;
  label: string;
  /** Other needs a reason. */
  reasonRequired: boolean;
  /** Training Stay needs an induction batch. */
  batchRequired: boolean;
  /** Travel and Fuel may link planned work. */
  linkOptional: boolean;
}

export const CATEGORIES: readonly CategoryInfo[] = [
  { key: "RENT_UTILITIES", label: "Rent and Utilities", reasonRequired: false, batchRequired: false, linkOptional: false },
  { key: "TRAVEL_FUEL", label: "Travel and Fuel", reasonRequired: false, batchRequired: false, linkOptional: true },
  { key: "TRAINING_STAY", label: "Training Stay", reasonRequired: false, batchRequired: true, linkOptional: false },
  { key: "MARKETING", label: "Marketing", reasonRequired: false, batchRequired: false, linkOptional: false },
  { key: "OFFICE_SUPPLIES", label: "Office Supplies", reasonRequired: false, batchRequired: false, linkOptional: false },
  { key: "OTHER", label: "Other", reasonRequired: true, batchRequired: false, linkOptional: false },
];

export function categoryInfo(key: CategoryKey): CategoryInfo {
  return CATEGORIES.find((c) => c.key === key) ?? CATEGORIES[CATEGORIES.length - 1];
}

/** Whether the category offers a link field at all (planned work or an induction batch). */
export const hasLinkField = (key: CategoryKey): boolean => categoryInfo(key).linkOptional || categoryInfo(key).batchRequired;
