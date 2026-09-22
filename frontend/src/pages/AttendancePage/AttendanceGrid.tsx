import { useState } from "react";
import type { AttendanceStatusCodeView } from "../MyAttendancePage/attendanceClient";
import type { AttendanceGridView } from "./attendanceAdminClient";
import "./AttendanceGrid.css";

interface AttendanceGridProps {
  grid: AttendanceGridView;
  statusCodes: AttendanceStatusCodeView[];
  onEditCell: (
    teacherId: string,
    date: string,
    schoolId: string,
    statusCode: string,
    fractionalValue: number,
  ) => Promise<void>;
}

/**
 * User Story 5 (specs/011-attendance): Teacher rows × day columns, one cell
 * per day (FR-019). A day covered by the shared Non-Working Calendar but with
 * no individual mark shows "Non-working" with no status code (research.md
 * §10) — distinct from a truly unmarked cell ("—"). A cell with
 * `editable: true` opens an inline status/fractional-value editor on click
 * (FR-025); `editable: false` renders read-only with a locked-month tooltip.
 */
export function AttendanceGrid({
  grid,
  statusCodes,
  onEditCell,
}: AttendanceGridProps) {
  const [editing, setEditing] = useState<{
    teacherId: string;
    date: string;
  } | null>(null);
  const [editStatusCode, setEditStatusCode] = useState("PRESENT");
  const [editFractionalValue, setEditFractionalValue] = useState("1.00");
  const [editSchoolId, setEditSchoolId] = useState("");
  const [saveError, setSaveError] = useState<string | null>(null);

  function openEditor(
    teacherId: string,
    date: string,
    existingSchoolId: string | null,
  ) {
    setEditing({ teacherId, date });
    setEditStatusCode("PRESENT");
    setEditFractionalValue("1.00");
    // An already-marked day carries its own school assignment forward; a
    // previously-unmarked day needs the caller to supply one (data-model.md).
    setEditSchoolId(existingSchoolId ?? "");
    setSaveError(null);
  }

  async function handleSave() {
    if (!editing) return;
    try {
      await onEditCell(
        editing.teacherId,
        editing.date,
        editSchoolId,
        editStatusCode,
        Number(editFractionalValue),
      );
      setEditing(null);
    } catch (e) {
      setSaveError(
        e instanceof Error ? e.message : "Unable to save this cell.",
      );
    }
  }

  return (
    <div className="attendance-grid" data-testid="attendance-grid">
      <table>
        <thead>
          <tr>
            <th>Teacher</th>
            {grid.days.map((day) => (
              <th key={day}>{day.slice(-2)}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {grid.rows.map((row) => (
            <tr key={row.teacherId} data-testid={`grid-row-${row.teacherId}`}>
              <td>{row.teacherName}</td>
              {grid.days.map((day) => {
                const cell = row.cells[day];
                const isEditingThis =
                  editing?.teacherId === row.teacherId && editing?.date === day;
                return (
                  <td
                    key={day}
                    data-testid={`grid-cell-${row.teacherId}-${day}`}
                    className={cell.editable ? "editable" : "not-editable"}
                    title={cell.editable ? undefined : "Locked or not editable"}
                    onClick={() =>
                      cell.editable &&
                      openEditor(row.teacherId, day, cell.schoolId)
                    }
                  >
                    {cell.statusCode ? (
                      cell.statusCode
                    ) : cell.category === "NON_WORKING" ? (
                      <em>Non-working</em>
                    ) : (
                      "—"
                    )}
                    {isEditingThis && (
                      <div
                        data-testid="grid-cell-editor"
                        onClick={(e) => e.stopPropagation()}
                      >
                        <select
                          data-testid="grid-cell-status-select"
                          value={editStatusCode}
                          onChange={(e) => setEditStatusCode(e.target.value)}
                        >
                          {(statusCodes.length > 0
                            ? statusCodes
                            : [
                                {
                                  code: "PRESENT",
                                  label: "Present",
                                } as AttendanceStatusCodeView,
                              ]
                          ).map((code) => (
                            <option key={code.code} value={code.code}>
                              {code.label}
                            </option>
                          ))}
                        </select>
                        <input
                          data-testid="grid-cell-fractional-value-input"
                          value={editFractionalValue}
                          onChange={(e) =>
                            setEditFractionalValue(e.target.value)
                          }
                        />
                        {!cell.schoolId && (
                          <input
                            data-testid="grid-cell-school-id-input"
                            placeholder="School ID"
                            value={editSchoolId}
                            onChange={(e) => setEditSchoolId(e.target.value)}
                          />
                        )}
                        <button
                          data-testid="grid-cell-save"
                          onClick={handleSave}
                        >
                          Save
                        </button>
                        <button
                          data-testid="grid-cell-cancel"
                          onClick={() => setEditing(null)}
                        >
                          Cancel
                        </button>
                        {saveError && (
                          <p data-testid="grid-cell-error">{saveError}</p>
                        )}
                      </div>
                    )}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
