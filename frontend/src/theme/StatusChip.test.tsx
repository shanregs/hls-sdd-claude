import { render, screen } from "@testing-library/react";
import { ThemeProvider } from "@mui/material/styles";
import { describe, expect, it } from "vitest";
import { StatusChip } from "./StatusChip";
import { ratio } from "./contrast";
import { BUILTIN_CATALOGUE, buildMuiTheme, type ThemeMode } from "./tokens";

function chipOf(label: string): HTMLElement {
  return screen.getByText(label).closest(".MuiChip-root") as HTMLElement;
}

describe.each(["light", "dark"] as const satisfies readonly ThemeMode[])(
  "StatusChip in %s mode (spec 039 US5)",
  (mode) => {
    const chips = BUILTIN_CATALOGUE.statusChips[mode];

    it.each(["approved", "pending", "rejected"] as const)(
      "shows %s in its semantic colours, whatever the preset",
      (kind) => {
        render(
          <ThemeProvider theme={buildMuiTheme(mode)}>
            <StatusChip kind={kind} label={`Chip ${kind}`} />
          </ThemeProvider>,
        );

        expect(chipOf(`Chip ${kind}`)).toHaveStyle({
          color: chips[kind].text,
          backgroundColor: chips[kind].background,
        });
      },
    );

    it("keeps the text readable on every chip (4.5 : 1)", () => {
      for (const kind of ["approved", "pending", "rejected"] as const) {
        expect(
          ratio(chips[kind].text, chips[kind].background),
          `${mode} ${kind}`,
        ).toBeGreaterThanOrEqual(4.5);
      }
    });

    it("always shows the label, so colour is never the only signal", () => {
      render(
        <ThemeProvider theme={buildMuiTheme(mode)}>
          <StatusChip kind="rejected" label="Over budget" />
        </ThemeProvider>,
      );
      expect(screen.getByText("Over budget")).toBeInTheDocument();
    });
  },
);

describe("StatusChip fallbacks", () => {
  it("renders a plain chip for a neutral status", () => {
    render(
      <ThemeProvider theme={buildMuiTheme("light")}>
        <StatusChip kind="neutral" label="Ended" />
      </ThemeProvider>,
    );
    expect(screen.getByText("Ended")).toBeInTheDocument();
  });

  it("works without the app theme (built-in colours)", () => {
    render(<StatusChip kind="approved" label="Approved" />);
    expect(chipOf("Approved")).toHaveStyle({
      color: BUILTIN_CATALOGUE.statusChips.light.approved.text,
    });
  });
});
