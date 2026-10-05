import {
  type CalendarFacts,
  holidaysIn,
  kindOf,
  MONTH_NAMES,
  monthsOfYear,
  monthWeeks,
} from "./holidayCalendarModel";
import {
  formatDate,
  parseMonth,
  WEEKDAY_LABELS,
  weekdayIndex,
} from "./monthUtils";

/** What to download: a whole year or one month ("YYYY-MM"). */
export type PdfScope =
  { type: "year"; year: number } | { type: "month"; month: string };

export function pdfFilename(scope: PdfScope): string {
  return scope.type === "year"
    ? `holiday-calendar-${scope.year}.pdf`
    : `holiday-calendar-${scope.month}.pdf`;
}

// RGB fills that match the on-screen tints: amber for holidays, light blue for weekly off days.
const HOLIDAY_FILL: [number, number, number] = [255, 213, 128];
const OFF_FILL: [number, number, number] = [215, 236, 248];

/**
 * Draws the year or month on screen as a PDF and saves it (spec 008 FR-026): the title, the days with
 * holidays and weekly off days filled, a legend and the holiday list. The PDF library is loaded on
 * demand so it never weighs on the first page load.
 */
export async function downloadHolidayPdf(
  facts: CalendarFacts,
  scope: PdfScope,
): Promise<string> {
  const { jsPDF } = await import("jspdf");
  const doc = new jsPDF({
    orientation: scope.type === "year" ? "portrait" : "landscape",
    unit: "mm",
    format: "a4",
  });
  const page = {
    w: doc.internal.pageSize.getWidth(),
    h: doc.internal.pageSize.getHeight(),
  };
  const margin = 12;
  let y = margin;

  const title =
    scope.type === "year"
      ? `Holiday Calendar ${scope.year}`
      : `Holiday Calendar - ${MONTH_NAMES[parseMonth(scope.month).month - 1]} ${parseMonth(scope.month).year}`;
  doc.setFont("helvetica", "bold");
  doc.setFontSize(16);
  doc.text(title, margin, y + 5);
  y += 12;

  const drawMonth = (
    month: string,
    x: number,
    top: number,
    width: number,
    cell: number,
    big: boolean,
  ) => {
    const { month: m, year } = parseMonth(month);
    doc.setFont("helvetica", "bold");
    doc.setFontSize(big ? 12 : 9);
    doc.text(`${MONTH_NAMES[m - 1]} ${year}`, x, top);
    const colW = width / 7;
    let rowY = top + (big ? 6 : 4);
    doc.setFontSize(big ? 9 : 6);
    WEEKDAY_LABELS.forEach((label, i) => {
      doc.text(big ? label : label[0], x + i * colW + colW / 2, rowY, {
        align: "center",
      });
    });
    rowY += 1.5;
    doc.setFont("helvetica", "normal");
    for (const week of monthWeeks(month)) {
      week.forEach((date, i) => {
        if (!date) return;
        const kind = kindOf(date, facts);
        const left = x + i * colW;
        if (kind === "holiday" || kind === "off") {
          doc.setFillColor(...(kind === "holiday" ? HOLIDAY_FILL : OFF_FILL));
          doc.rect(left, rowY, colW, cell, "F");
        }
        doc.setDrawColor(200, 200, 200);
        doc.rect(left, rowY, colW, cell, "S");
        doc.setFontSize(big ? 10 : 7);
        doc.text(
          String(Number(date.slice(8))),
          left + colW / 2,
          rowY + (big ? 5 : cell / 2 + 1),
          { align: "center" },
        );
        if (big && kind === "holiday") {
          const name =
            facts.nonWorkingDates.find((d) => d.date === date)?.description ??
            "";
          doc.setFontSize(6.5);
          doc.text(
            doc.splitTextToSize(name, colW - 2).slice(0, 3),
            left + 1,
            rowY + 9,
          );
        }
      });
      rowY += cell;
    }
    return rowY;
  };

  if (scope.type === "year") {
    const gap = 6;
    const colWidth = (page.w - 2 * margin - 2 * gap) / 3;
    monthsOfYear(scope.year).forEach((month, index) => {
      const col = index % 3;
      const row = Math.floor(index / 3);
      drawMonth(
        month,
        margin + col * (colWidth + gap),
        y + row * 52,
        colWidth,
        5.5,
        false,
      );
    });
    y += 4 * 52 + 2;
  } else {
    y =
      drawMonth(scope.month, margin, y + 2, page.w - 2 * margin, 22, true) + 4;
  }

  // Legend.
  doc.setFontSize(9);
  doc.setFont("helvetica", "normal");
  doc.setFillColor(...HOLIDAY_FILL);
  doc.rect(margin, y, 5, 4, "F");
  doc.text("Holiday", margin + 7, y + 3.2);
  doc.setFillColor(...OFF_FILL);
  doc.rect(margin + 30, y, 5, 4, "F");
  doc.text("Weekly off", margin + 37, y + 3.2);
  y += 9;

  // The holiday list, on a new page when it does not fit.
  const prefix = scope.type === "year" ? String(scope.year) : scope.month;
  const holidays = holidaysIn(prefix, facts);
  doc.setFont("helvetica", "bold");
  doc.setFontSize(11);
  doc.text(holidays.length === 0 ? "No holidays." : "Holidays", margin, y);
  y += 6;
  doc.setFont("helvetica", "normal");
  doc.setFontSize(9);
  for (const holiday of holidays) {
    if (y > page.h - margin) {
      doc.addPage();
      y = margin;
    }
    doc.text(
      `${formatDate(holiday.date)}  ${WEEKDAY_LABELS[weekdayIndex(holiday.date)]}`,
      margin,
      y,
    );
    doc.text(holiday.description, margin + 38, y);
    y += 5;
  }

  const filename = pdfFilename(scope);
  doc.save(filename);
  return filename;
}
