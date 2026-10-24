import { CATEGORIES, categoryInfo, hasLinkField } from "../../src/expenses/categories";
import { formatRupees, signOutWarning, STATUS_WORDS } from "../../src/expenses/expenseMessages";
import { CATEGORY_LABELS, STATUS_LABELS } from "../support/expenseFixtures";

describe("categories", () => {
  it("has the six keys with the labels the server uses", () => {
    expect(CATEGORIES.map((c) => [c.key, c.label])).toEqual(Object.entries(CATEGORY_LABELS));
  });

  it("flags Other as needing a reason, Training Stay a batch, Travel and Fuel an optional link", () => {
    expect(categoryInfo("OTHER").reasonRequired).toBe(true);
    expect(categoryInfo("TRAINING_STAY").batchRequired).toBe(true);
    expect(categoryInfo("TRAVEL_FUEL").linkOptional).toBe(true);
    expect(CATEGORIES.filter((c) => c.reasonRequired)).toHaveLength(1);
    expect(hasLinkField("MARKETING")).toBe(false);
    expect(hasLinkField("TRAINING_STAY")).toBe(true);
  });
});

describe("words", () => {
  it("uses the server's status labels", () => {
    expect(STATUS_WORDS).toEqual(STATUS_LABELS);
  });

  it("writes rupees with Indian grouping", () => {
    expect(formatRupees("850.00")).toBe("₹850");
    expect(formatRupees("1850.50")).toBe("₹1,850.50");
    expect(formatRupees("1234567.00")).toBe("₹12,34,567");
  });

  it("counts unsent claims in the sign-out warning", () => {
    expect(signOutWarning(1)).toMatch(/^1 claim is not sent yet/);
    expect(signOutWarning(3)).toMatch(/^3 claims are not sent yet/);
  });
});
