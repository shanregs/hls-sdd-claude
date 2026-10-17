import { formatFileType } from "../../src/formats/manager";
import { historyEventText, moreNeededText, overdueText, taskStatusText } from "../../src/tasks/taskMessages";
import { visitKindText, visitStatusText } from "../../src/visits/visitMessages";

/** Spec 034 T013: the words the visit and task screens use. */
describe("visit words", () => {
  it("names each status the server can give, including Missed", () => {
    expect((["PLANNED", "COMPLETED", "CANCELLED", "MISSED"] as const).map(visitStatusText)).toEqual([
      "Planned",
      "Completed",
      "Cancelled",
      "Missed",
    ]);
  });

  it("names each kind", () => {
    expect((["VISIT", "CALL", "PROPOSAL_MEETING", "FOLLOW_UP"] as const).map(visitKindText)).toEqual([
      "Visit",
      "Call",
      "Proposal meeting",
      "Follow-up",
    ]);
  });
});

describe("task words", () => {
  it("names each status", () => {
    expect(
      (["ASSIGNED", "ACKNOWLEDGED", "IN_PROGRESS", "COMPLETED", "VERIFIED", "CANCELLED"] as const).map(taskStatusText),
    ).toEqual(["Assigned", "Acknowledged", "In progress", "Completed", "Verified", "Cancelled"]);
  });

  it("says how many days overdue only when the server says it is overdue", () => {
    expect(overdueText({ overdue: true, daysOverdue: 5 })).toBe("5 days overdue");
    expect(overdueText({ overdue: true, daysOverdue: 1 })).toBe("1 day overdue");
    expect(overdueText({ overdue: false, daysOverdue: 0 })).toBeNull();
  });

  it("says how many more characters a text needs, and nothing once it is long enough", () => {
    expect(moreNeededText(0, 5)).toBe("5 more characters needed");
    expect(moreNeededText(4, 5)).toBe("1 more character needed");
    expect(moreNeededText(5, 5)).toBeNull();
    expect(moreNeededText(12, 10)).toBeNull();
  });

  it("words history events and falls back to the event name", () => {
    expect(historyEventText("CREATED")).toBe("Created");
    expect(historyEventText("OVERDUE_REASON")).toBe("Reason given for being overdue");
    expect(historyEventText("SOMETHING_NEW")).toBe("Something new");
  });
});

describe("file types", () => {
  it.each([
    ["image/jpeg", "JPG"],
    ["image/png", "PNG"],
    ["application/pdf", "PDF"],
    ["application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word"],
    ["application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel"],
    ["text/plain", "Text"],
  ])("says %s as %s", (type, text) => {
    expect(formatFileType(type)).toBe(text);
  });
});
