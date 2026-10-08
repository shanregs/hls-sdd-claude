import { getMyMonth, saveMyMark } from "../api/attendanceApi";
import { MonthPane } from "../attendance/MonthPane";
import { allowedMonths, businessYearMonth, type RangeKind } from "../attendance/monthRange";
import { serverNow } from "../attendance/serverClock";
import { Screen } from "./Screen";

/**
 * The month a notification link asked for, when the month picker can show it: the current year, or the previous year
 * (a lock or reopen notice for December arrives in January, spec 021 research §2). Anything else, or a malformed month,
 * is ignored and the screen opens on the current month as usual.
 */
function linkedMonth(initialMonth: string | undefined): { month?: string; kind: RangeKind } {
  if (!initialMonth) return { kind: "current" };
  const now = serverNow();
  const year = businessYearMonth(now).year;
  const kind: RangeKind = Number(initialMonth.slice(0, 4)) === year - 1 ? "history" : "current";
  return allowedMonths(kind, now).includes(initialMonth) ? { month: initialMonth, kind } : { kind: "current" };
}

/**
 * The signed-in Teacher's own month. Marks can be changed only on days the server says are theirs (`editableBy: SELF`).
 * `initialMonth` ("YYYY-MM") opens that month, for the attendance links in notifications.
 */
export function MyAttendanceScreen({ initialMonth }: { initialMonth?: string } = {}) {
  const { month, kind } = linkedMonth(initialMonth);
  return (
    <Screen>
      <MonthPane
        key={month ?? "current"}
        kind={kind}
        viewer="self"
        loader={getMyMonth}
        initialMonth={month}
        actions={{ editableBy: "SELF", save: saveMyMark }}
      />
    </Screen>
  );
}
