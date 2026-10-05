import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AttendanceCalendar } from "./attendanceApi";
import { HolidayCalendarView } from "./HolidayCalendarView";
import {
  currentMonth,
  monthLabel,
  shiftMonth,
  weekdayIndex,
} from "./monthUtils";

const download = vi.fn();

vi.mock("./holidayPdf", () => ({
  downloadHolidayPdf: (...args: unknown[]) => download(...args),
}));

const year = Number(currentMonth().slice(0, 4));

/** A date in the viewed year that falls on a Sunday, to prove a holiday beats a weekly off day. */
function aSunday(): string {
  for (let day = 1; day <= 28; day++) {
    const date = `${year}-03-${String(day).padStart(2, "0")}`;
    if (weekdayIndex(date) === 0) return date;
  }
  throw new Error("no Sunday");
}

const SUNDAY_HOLIDAY = aSunday();
const CALENDAR: AttendanceCalendar = {
  defaultWeeklyOff: ["SUN"],
  defaultVersion: 0,
  schoolOverrides: [],
  nonWorkingDates: [
    { date: `${year}-01-14`, description: "Pongal" },
    { date: `${year}-10-02`, description: "Gandhi Jayanthi" },
    { date: SUNDAY_HOLIDAY, description: "Ugadi" },
  ],
};

describe("HolidayCalendarView (User Story 9)", () => {
  beforeEach(() => {
    download.mockReset();
  });

  it("opens on the year with twelve months and the holidays highlighted", () => {
    render(<HolidayCalendarView calendar={CALENDAR} />);

    expect(screen.getAllByRole("table")).toHaveLength(12);
    expect(
      screen.getByRole("table", { name: `January ${year}` }),
    ).toBeInTheDocument();
    const holidayCells = screen.getAllByLabelText(/\(holiday\)$/);
    expect(holidayCells).toHaveLength(3);
    expect(screen.getByText(`Holidays in ${year}`)).toBeInTheDocument();
    expect(screen.getByText(/Gandhi Jayanthi/)).toBeInTheDocument();
  });

  it("shows a holiday that falls on a weekly off day as a holiday", () => {
    render(<HolidayCalendarView calendar={CALENDAR} />);

    const day = SUNDAY_HOLIDAY.split("-").reverse().join("/");
    expect(
      screen.getByLabelText(`${day}: Ugadi (holiday)`),
    ).toBeInTheDocument();
    expect(
      screen.queryByLabelText(new RegExp(`${day}: weekly off`)),
    ).toBeNull();
  });

  it("marks other Sundays as weekly off, with the weekday in their name", () => {
    render(<HolidayCalendarView calendar={CALENDAR} />);

    expect(screen.getAllByLabelText(/weekly off, Sun$/).length).toBeGreaterThan(
      40,
    );
  });

  it("switches to one month, which names each holiday, and moves between months", async () => {
    render(<HolidayCalendarView calendar={CALENDAR} />);
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "Month" }));
    expect(screen.getAllByRole("table")).toHaveLength(1);
    const month = currentMonth();
    expect(screen.getByText(monthLabel(month))).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Next month" }));
    expect(
      screen.getByText(monthLabel(shiftMonth(month, 1))),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Previous month" }));
    await user.click(screen.getByRole("button", { name: "Previous month" }));
    expect(
      screen.getByText(monthLabel(shiftMonth(month, -1))),
    ).toBeInTheDocument();
  });

  it("opens a month from the year, and a month view names the holiday in its cell", async () => {
    render(<HolidayCalendarView calendar={CALENDAR} />);
    const user = userEvent.setup();

    await user.click(
      screen.getByRole("button", { name: `Open October ${year}` }),
    );

    const table = screen.getByRole("table", { name: `October ${year}` });
    expect(within(table).getByText("Gandhi Jayanthi")).toBeInTheDocument();
    expect(screen.getByText("Holidays in October " + year)).toBeInTheDocument();
  });

  it("moves between years in the year view", async () => {
    render(<HolidayCalendarView calendar={CALENDAR} />);

    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Next year" }));

    expect(screen.getByText(String(year + 1))).toBeInTheDocument();
    expect(screen.getByText("No holidays in this period.")).toBeInTheDocument();
  });

  it("downloads the year on screen as a PDF from the icon", async () => {
    download.mockResolvedValue("holiday-calendar.pdf");
    render(<HolidayCalendarView calendar={CALENDAR} />);

    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: `Download PDF of ${year}` }));

    expect(download).toHaveBeenCalledWith(CALENDAR, { type: "year", year });
  });

  it("downloads the month on screen once Month is chosen", async () => {
    download.mockResolvedValue("holiday-calendar.pdf");
    render(<HolidayCalendarView calendar={CALENDAR} />);
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "Month" }));
    await user.click(
      screen.getByRole("button", {
        name: `Download PDF of ${monthLabel(currentMonth())}`,
      }),
    );

    expect(download).toHaveBeenCalledWith(CALENDAR, {
      type: "month",
      month: currentMonth(),
    });
  });

  it("shows an error when the PDF cannot be created", async () => {
    download.mockImplementation(() => {
      throw new Error("boom");
    });
    render(<HolidayCalendarView calendar={CALENDAR} />);

    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: `Download PDF of ${year}` }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not create the PDF",
    );
  });
});
