import type {
  AttendanceCalendar,
  DayState,
  DayView,
  EditableBy,
  MarkView,
  RollupView,
  StatusCode,
  TeacherGridPage,
  TeacherGridRow,
  TeacherMonthView,
} from "../../src/api/attendanceApi";

/**
 * Builders that copy the real response shapes of spec 008 (specs/019-mobile-attendance/data-model.md),
 * so tests exercise the same JSON the server sends.
 */

const pad = (n: number) => String(n).padStart(2, "0");

export function daysInMonth(month: string): number {
  const [year, m] = month.split("-").map(Number);
  return new Date(Date.UTC(year, m, 0)).getUTCDate();
}

export const dateOf = (month: string, day: number) => `${month}-${pad(day)}`;

export function mark(over: Partial<MarkView> = {}): MarkView {
  return {
    date: "2026-10-05",
    code: "P",
    codeName: "Present",
    category: "WORKED",
    dayValue: 1,
    schoolId: "school-1",
    schoolName: "Demo School One",
    setByKind: "SELF",
    setByUserId: "u-1",
    setByName: "Tara",
    setAt: "2026-10-05T04:00:00Z",
    note: null,
    version: 1,
    ...over,
  };
}

export function rollup(over: Partial<RollupView> = {}): RollupView {
  return {
    workingDays: 22,
    daysWorked: 3,
    daysLeave: 1,
    trainingAvailable: 0,
    trainingAttended: 0,
    unmarked: 18,
    weightedTotal: 3,
    locked: false,
    frozen: false,
    ...over,
  };
}

export interface DaySpec {
  state?: DayState;
  editableBy?: EditableBy;
  mark?: Partial<MarkView> | null;
}

/**
 * A month of days. Sundays are weekly offs, days after `today` are future, days before `placedFrom`
 * are not placed; `overrides` replaces single days. Editability follows what the server sends: the
 * Teacher may change today and the 3 days before it; a supervisor any past placed day when unlocked.
 */
export function monthView(
  month: string,
  options: {
    today?: string;
    placedFrom?: string;
    locked?: boolean;
    name?: string;
    teacherId?: string;
    viewer?: "SELF" | "SUPERVISOR";
    overrides?: Record<string, DaySpec>;
    holidays?: string[];
    rollup?: Partial<RollupView>;
  } = {},
): TeacherMonthView {
  const today = options.today ?? "2026-10-05";
  const viewer = options.viewer ?? "SELF";
  const locked = options.locked ?? false;
  const days: DayView[] = [];
  for (let d = 1; d <= daysInMonth(month); d += 1) {
    const date = dateOf(month, d);
    const weekday = new Date(`${date}T00:00:00Z`).getUTCDay();
    const spec = options.overrides?.[date];
    let state: DayState;
    if (options.placedFrom && date < options.placedFrom) state = "NOT_PLACED";
    else if (options.holidays?.includes(date)) state = "NON_WORKING";
    else if (weekday === 0) state = "WEEKLY_OFF";
    else if (date > today) state = "FUTURE";
    else state = "UNMARKED";
    if (spec?.state) state = spec.state;

    let editableBy: EditableBy = "NONE";
    if (!locked && state !== "NOT_PLACED" && state !== "FUTURE") {
      if (viewer === "SUPERVISOR") editableBy = "SUPERVISOR";
      else {
        const age = (Date.parse(`${today}T00:00:00Z`) - Date.parse(`${date}T00:00:00Z`)) / 86_400_000;
        if (age >= 0 && age <= 3) editableBy = "SELF";
      }
    }
    if (spec?.editableBy) editableBy = spec.editableBy;

    const markSpec = spec?.mark === null ? null : spec?.mark ? mark({ date, ...spec.mark }) : null;
    days.push({
      date,
      state: markSpec ? "MARKED" : state,
      mark: markSpec,
      editableBy,
    });
  }
  return {
    teacherId: options.teacherId ?? "t-1",
    name: options.name ?? "Tara",
    month,
    locked,
    state: locked ? "LOCKED" : "OPEN",
    rollup: rollup({ locked, ...options.rollup }),
    days,
  };
}

const code = (
  shortCode: string,
  name: string,
  category: StatusCode["category"],
  weight: number,
  index: number,
): StatusCode => ({
  id: `code-${shortCode}`,
  shortCode,
  name,
  category,
  weight,
  active: true,
  system: true,
  inUse: false,
  version: 1 + index * 0,
});

/** The server's active status codes in server order (Present first), plus Holiday which is non-working. */
export function statusCodes(): StatusCode[] {
  return [
    code("P", "Present", "WORKED", 1, 0),
    code("A", "Absent", "LEAVE", 0, 1),
    code("L", "Leave", "LEAVE", 0, 2),
    code("T", "Training", "TRAINING", 1, 3),
    code("S", "Substitution", "WORKED", 1, 4),
    code("H", "Holiday", "NON_WORKING", 0, 5),
  ];
}

export function calendar(over: Partial<AttendanceCalendar> = {}): AttendanceCalendar {
  return {
    defaultWeeklyOff: ["SUN"],
    defaultVersion: 1,
    schoolOverrides: [],
    nonWorkingDates: [
      { date: "2026-01-26", description: "Republic Day" },
      { date: "2026-10-02", description: "Gandhi Jayanti" },
      { date: "2026-10-20", description: "Ayudha Puja" },
      { date: "2026-11-08", description: "Deepavali" },
      { date: "2026-12-25", description: "Christmas" },
    ],
    ...over,
  };
}

export function gridRow(over: Partial<TeacherGridRow> & { teacherId: string; name: string }): TeacherGridRow {
  return {
    status: "ACTIVE",
    school: { id: "school-1", name: "Demo School One" },
    manager: { id: "m-1", name: "Manoj" },
    locked: false,
    rollup: rollup(),
    ...over,
  };
}

export function gridPage(
  content: TeacherGridRow[],
  over: Partial<TeacherGridPage> = {},
): TeacherGridPage {
  return {
    month: "2026-10",
    days: 31,
    content,
    page: 0,
    size: 25,
    totalElements: content.length,
    ...over,
  };
}
