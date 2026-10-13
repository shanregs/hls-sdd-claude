import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { DeletedRowsDialog } from "./DeletedRowsDialog";
import { HistoryDialog } from "./HistoryDialog";
import type { SchoolOption, WorkingDaysRow } from "./schoolWorkingDaysApi";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

const ROW: WorkingDaysRow = {
  id: "r1",
  schoolId: "s1",
  schoolName: "Greenfield School",
  zoneId: "z1",
  zoneName: "North",
  year: 2027,
  month: 3,
  workingDays: 25.5,
  daysInMonth: 31,
  calendarDerived: 27,
  updatedBy: "Asha R",
  updatedAt: "2027-02-01T10:15:00",
  version: 2,
};

const SCHOOLS: SchoolOption[] = [
  {
    id: "s1",
    name: "Greenfield School",
    zoneId: "z1",
    zoneName: "North",
    active: true,
  },
];

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

describe("Row history and Deleted rows (spec 037 US4)", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("shows a row's changes newest first with the old and new figures", async () => {
    authFetch.mockResolvedValue(
      jsonResponse([
        {
          action: "EDIT",
          oldValue: 26,
          newValue: 25.5,
          actorName: "Asha R",
          actorRoles: "ADMIN",
          occurredAt: "2027-02-05T09:30:00",
        },
        {
          action: "CREATE",
          oldValue: null,
          newValue: 26,
          actorName: "Dev D",
          actorRoles: "DIRECTOR",
          occurredAt: "2027-02-01T10:15:00",
        },
      ]),
    );
    render(<HistoryDialog row={ROW} onClose={() => {}} />);

    const table = await screen.findByRole("table", { name: "Change history" });
    const rows = within(table).getAllByRole("row");
    expect(rows).toHaveLength(3);
    expect(rows[1]).toHaveTextContent("Changed");
    expect(rows[1]).toHaveTextContent("26");
    expect(rows[1]).toHaveTextContent("25.5");
    expect(rows[1]).toHaveTextContent("05/02/2027");
    expect(rows[1]).toHaveTextContent("Asha R (ADMIN)");
    expect(rows[2]).toHaveTextContent("Added");
    expect(authFetch).toHaveBeenCalledWith(
      "/api/v1/school-working-days/history?schoolId=s1&year=2027&month=3",
      expect.anything(),
    );
    expect(
      screen.getByText(/History: Greenfield School, March 2027/),
    ).toBeInTheDocument();
  });

  it("says so when a row has no recorded changes and when the history cannot load", async () => {
    authFetch.mockResolvedValueOnce(jsonResponse([]));
    const { unmount } = render(<HistoryDialog row={ROW} onClose={() => {}} />);
    expect(await screen.findByText("No changes recorded.")).toBeInTheDocument();
    unmount();

    authFetch.mockResolvedValueOnce(
      jsonResponse({ reason: "Could not load the history." }, 500),
    );
    render(<HistoryDialog row={ROW} onClose={() => {}} />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load the history.",
    );
  });

  it("lists each deleted month with its last figure, who deleted it and when", async () => {
    authFetch.mockResolvedValue(
      jsonResponse([
        {
          month: 6,
          lastValue: 22.5,
          deletedByName: "Asha R",
          deletedAt: "2027-03-02T11:00:00",
        },
        {
          month: 3,
          lastValue: 26,
          deletedByName: "Dev D",
          deletedAt: "2027-03-01T08:00:00",
        },
      ]),
    );
    render(
      <DeletedRowsDialog
        schools={SCHOOLS}
        schoolId="s1"
        year={2027}
        onClose={() => {}}
      />,
    );

    const table = await screen.findByRole("table", { name: "Deleted rows" });
    const rows = within(table).getAllByRole("row");
    expect(rows).toHaveLength(3);
    expect(rows[1]).toHaveTextContent("June");
    expect(rows[1]).toHaveTextContent("22.5");
    expect(rows[1]).toHaveTextContent("Asha R");
    expect(rows[1]).toHaveTextContent("02/03/2027");
    expect(authFetch).toHaveBeenCalledWith(
      "/api/v1/school-working-days/deleted?schoolId=s1&year=2027",
      expect.anything(),
    );
  });

  it("asks for a School when none is selected and shows an empty state after choosing one", async () => {
    const user = userEvent.setup();
    authFetch.mockResolvedValue(jsonResponse([]));
    render(
      <DeletedRowsDialog
        schools={SCHOOLS}
        schoolId=""
        year={2027}
        onClose={() => {}}
      />,
    );

    expect(
      screen.getByText("Choose a School to see its deleted rows."),
    ).toBeInTheDocument();
    expect(authFetch).not.toHaveBeenCalled();

    await user.click(screen.getByLabelText("School"));
    await user.click(
      await screen.findByRole("option", { name: "Greenfield School" }),
    );
    expect(
      await screen.findByText("No rows were deleted for this School in 2027."),
    ).toBeInTheDocument();
  });
});
