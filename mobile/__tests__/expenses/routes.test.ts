import { EXPENSES_ROUTE, screenFor } from "../../src/access/screenRegistry";
import { buildMenu } from "../../src/access/useMenu";
import { directorExpenseModel, expenseModel, systemExpenseModel, teacherExpenseModel } from "../support/expenseFixtures";

const labels = (model: ReturnType<typeof expenseModel>) =>
  buildMenu(model).flatMap((section) => section.items.map((item) => `${section.section}/${item.label}`));

/** Spec 036 T017: only the claimant's own item is known to the app; the review item never shows. */
describe("expense routes", () => {
  it("maps the My Expenses route to its screen", () => {
    expect(EXPENSES_ROUTE).toBe("/operations/my-expenses");
    expect(screenFor("/operations/my-expenses")).toBe("myExpenses");
  });

  it("still shows nothing for the review route the web has", () => {
    expect(screenFor("/operations/expenses")).toBeUndefined();
  });

  it("shows My Expenses and never the review item, even when the server's menu offers both", () => {
    expect(labels(directorExpenseModel())).toContain("OPERATIONS/My Expenses");
    expect(labels(directorExpenseModel())).not.toContain("OPERATIONS/Expenses");
  });

  it("shows the entry for a Teacher and only when the model has it", () => {
    expect(labels(teacherExpenseModel())).toContain("OPERATIONS/My Expenses");
    expect(labels(systemExpenseModel()).join(" ")).not.toMatch(/Expenses/);
  });
});
