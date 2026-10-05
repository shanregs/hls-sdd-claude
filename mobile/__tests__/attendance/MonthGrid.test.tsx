import { render, screen } from "@testing-library/react-native";
import { PaperProvider } from "react-native-paper";
import { MonthGrid } from "../../src/attendance/MonthGrid";
import { monthView } from "../support/attendanceFixtures";

const days = monthView("2026-10").days;

describe("MonthGrid", () => {
  it("starts the week on Sunday", async () => {
    await render(
      <PaperProvider>
        <MonthGrid days={days} fontScale={1} />
      </PaperProvider>,
    );

    // The header row is hidden from screen readers (each day announces its own weekday).
    const headers = screen
      .getAllByText(/^(Sun|Mon|Tue|Wed|Thu|Fri|Sat)$/, { includeHiddenElements: true })
      .map((n) => n.props.children)
      .filter((child) => typeof child === "string");
    expect(headers).toEqual(["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]);
  });

  it("lists days one per row at a large text size", async () => {
    await render(
      <PaperProvider>
        <MonthGrid days={days} fontScale={1.5} />
      </PaperProvider>,
    );

    expect(screen.queryByText("Sun")).toBeNull();
    expect(screen.getByLabelText("Thursday 1 October 2026, not marked")).toBeTruthy();
  });
});
