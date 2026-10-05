import { beforeEach, describe, expect, it, vi } from "vitest";

const texts: string[] = [];
const fills: unknown[][] = [];
const save = vi.fn();
const addPage = vi.fn();

vi.mock("jspdf", () => ({
  jsPDF: class {
    internal = { pageSize: { getWidth: () => 210, getHeight: () => 297 } };
    setFont() {}
    setFontSize() {}
    setFillColor(...rgb: unknown[]) {
      fills.push(rgb);
    }
    setDrawColor() {}
    rect() {}
    splitTextToSize(text: string) {
      return [text];
    }
    text(value: string | string[]) {
      texts.push(...(Array.isArray(value) ? value : [value]));
    }
    addPage = addPage;
    save = save;
  },
}));

const FACTS = {
  defaultWeeklyOff: ["SUN"],
  nonWorkingDates: [
    { date: "2026-10-02", description: "Gandhi Jayanthi" },
    { date: "2026-10-19", description: "Ayudha Pooja" },
    { date: "2026-11-08", description: "Deepavali" },
  ],
};

describe("downloadHolidayPdf", () => {
  beforeEach(() => {
    texts.length = 0;
    fills.length = 0;
    save.mockClear();
    addPage.mockClear();
  });

  it("saves the year with its title, all holidays and a legend", async () => {
    const { downloadHolidayPdf, pdfFilename } = await import("./holidayPdf");

    const name = await downloadHolidayPdf(FACTS, { type: "year", year: 2026 });

    expect(name).toBe("holiday-calendar-2026.pdf");
    expect(pdfFilename({ type: "year", year: 2026 })).toBe(name);
    expect(save).toHaveBeenCalledWith("holiday-calendar-2026.pdf");
    expect(texts).toContain("Holiday Calendar 2026");
    expect(texts).toContain("October 2026");
    expect(texts).toContain("Holiday");
    expect(texts).toContain("Weekly off");
    expect(texts.join("|")).toContain("02/10/2026");
    expect(texts).toContain("Gandhi Jayanthi");
    expect(texts).toContain("Deepavali");
  });

  it("saves one month listing only that month's holidays", async () => {
    const { downloadHolidayPdf } = await import("./holidayPdf");

    const name = await downloadHolidayPdf(FACTS, {
      type: "month",
      month: "2026-10",
    });

    expect(name).toBe("holiday-calendar-2026-10.pdf");
    expect(texts).toContain("Holiday Calendar - October 2026");
    expect(texts).toContain("Ayudha Pooja");
    expect(texts).not.toContain("Deepavali");
  });

  it("fills holiday days amber and weekly off days light blue", async () => {
    const { downloadHolidayPdf } = await import("./holidayPdf");

    await downloadHolidayPdf(FACTS, { type: "month", month: "2026-10" });

    expect(fills).toContainEqual([255, 213, 128]);
    expect(fills).toContainEqual([215, 236, 248]);
  });

  it("says so when there are no holidays", async () => {
    const { downloadHolidayPdf } = await import("./holidayPdf");

    await downloadHolidayPdf(
      { defaultWeeklyOff: ["SUN"], nonWorkingDates: [] },
      { type: "year", year: 2030 },
    );

    expect(texts).toContain("No holidays.");
    expect(save).toHaveBeenCalledWith("holiday-calendar-2030.pdf");
  });

  it("starts a new page when the holiday list is long", async () => {
    const { downloadHolidayPdf } = await import("./holidayPdf");
    const many = Array.from({ length: 80 }, (_, i) => ({
      date: `2026-${String((i % 12) + 1).padStart(2, "0")}-${String((i % 27) + 1).padStart(2, "0")}`,
      description: `Holiday ${i}`,
    }));

    await downloadHolidayPdf(
      { defaultWeeklyOff: ["SUN"], nonWorkingDates: many },
      { type: "year", year: 2026 },
    );

    expect(addPage).toHaveBeenCalled();
  });
});
