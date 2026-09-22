import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { AttendanceGrid } from "./AttendanceGrid";
import type { AttendanceGridView } from "./attendanceAdminClient";

const statusCodes = [
  {
    code: "PRESENT",
    label: "Present",
    category: "WORKED" as const,
    weight: 1,
    active: true,
  },
  {
    code: "LEAVE",
    label: "Leave",
    category: "LEAVE" as const,
    weight: 0,
    active: true,
  },
];

function grid(overrides?: Partial<AttendanceGridView>): AttendanceGridView {
  return {
    period: "2026-09",
    days: ["2026-09-01", "2026-09-02", "2026-09-03"],
    rows: [
      {
        teacherId: "teacher-1",
        teacherName: "A Teacher",
        cells: {
          "2026-09-01": {
            statusCode: "PRESENT",
            category: "WORKED",
            fractionalValue: 1,
            schoolId: "school-1",
            editable: true,
          },
          "2026-09-02": {
            statusCode: null,
            category: null,
            fractionalValue: null,
            schoolId: null,
            editable: true,
          },
          "2026-09-03": {
            statusCode: null,
            category: "NON_WORKING",
            fractionalValue: null,
            schoolId: null,
            editable: false,
          },
        },
      },
    ],
    ...overrides,
  };
}

describe("AttendanceGrid", () => {
  it("renders one row per Teacher and one column per day", () => {
    render(
      <AttendanceGrid
        grid={grid()}
        statusCodes={statusCodes}
        onEditCell={vi.fn()}
      />,
    );

    expect(screen.getByTestId(`grid-row-teacher-1`)).toHaveTextContent(
      "A Teacher",
    );
    expect(
      screen.getByTestId("grid-cell-teacher-1-2026-09-01"),
    ).toHaveTextContent("PRESENT");
  });

  it("shows a clear unmarked indicator for a day with no mark", () => {
    render(
      <AttendanceGrid
        grid={grid()}
        statusCodes={statusCodes}
        onEditCell={vi.fn()}
      />,
    );

    expect(
      screen.getByTestId("grid-cell-teacher-1-2026-09-02"),
    ).toHaveTextContent("—");
  });

  it("shows a distinct display for a calendar non-working day with no mark", () => {
    render(
      <AttendanceGrid
        grid={grid()}
        statusCodes={statusCodes}
        onEditCell={vi.fn()}
      />,
    );

    expect(
      screen.getByTestId("grid-cell-teacher-1-2026-09-03"),
    ).toHaveTextContent("Non-working");
  });

  it("editing an editable cell calls onEditCell and closes the editor (FR-025, AC5)", async () => {
    const onEditCell = vi.fn().mockResolvedValue(undefined);
    render(
      <AttendanceGrid
        grid={grid()}
        statusCodes={statusCodes}
        onEditCell={onEditCell}
      />,
    );

    await userEvent.click(screen.getByTestId("grid-cell-teacher-1-2026-09-01"));
    expect(screen.getByTestId("grid-cell-editor")).toBeInTheDocument();

    await userEvent.selectOptions(
      screen.getByTestId("grid-cell-status-select"),
      "LEAVE",
    );
    await userEvent.click(screen.getByTestId("grid-cell-save"));

    expect(onEditCell).toHaveBeenCalledWith(
      "teacher-1",
      "2026-09-01",
      "school-1",
      "LEAVE",
      1,
    );
  });

  it("a non-editable cell (e.g. locked) offers no edit interaction (AC6)", async () => {
    render(
      <AttendanceGrid
        grid={grid()}
        statusCodes={statusCodes}
        onEditCell={vi.fn()}
      />,
    );

    await userEvent.click(screen.getByTestId("grid-cell-teacher-1-2026-09-03"));

    expect(screen.queryByTestId("grid-cell-editor")).not.toBeInTheDocument();
  });
});
