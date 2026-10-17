import { Box, Button, Chip, Stack, TextField, Typography } from "@mui/material";
import type { FocusEvent, MouseEvent } from "react";
import type { FailedMonth } from "./yearEntryState";
import type { YearMonthState } from "./schoolWorkingDaysApi";
import { formatFigure, formatWhen, monthName } from "./workingDaysRules";

interface Props {
  year: number;
  state: YearMonthState;
  text: string;
  onChange: (text: string) => void;
  readOnly: boolean;
  /** A message for text that cannot be a figure, checked in the browser before sending. */
  problem: string | null;
  /** The server's refusal of this month in the last Save. */
  failed: FailedMonth | null;
  helperFilled: boolean;
  /** Replaces the typed text with the stored figure (after a refusal that shows a different stored figure). */
  onUseStored: () => void;
}

/**
 * One month of the year screen: a labelled field, the stored figure and who changed it, and the calendar count as a
 * hint that looks different from a stored figure. Laid out as a stacked block so it never needs sideways scrolling.
 */
export function MonthField({
  year,
  state,
  text,
  onChange,
  readOnly,
  problem,
  failed,
  helperFilled,
  onUseStored,
}: Props) {
  const label = `${monthName(state.month)} ${year}`;
  const message = failed?.message ?? problem;
  const showStored =
    failed !== null &&
    (failed.reason === "STALE" || failed.reason === "ALREADY_EXISTS") &&
    state.workingDays !== null;
  return (
    <Box
      component="li"
      sx={{
        listStyle: "none",
        border: 1,
        borderColor: failed || problem ? "error.main" : "divider",
        borderRadius: 1,
        p: 1.5,
        minWidth: 0,
      }}
    >
      <Stack spacing={1}>
        <Stack
          direction="row"
          spacing={1}
          sx={{ alignItems: "baseline", flexWrap: "wrap" }}
        >
          <Typography component="span" sx={{ fontWeight: 600 }}>
            {monthName(state.month)}
          </Typography>
          <Typography component="span" variant="caption" color="text.secondary">
            {state.daysInMonth} days
          </Typography>
          {helperFilled ? (
            <Chip size="small" label="Filled, not saved" color="info" />
          ) : null}
        </Stack>
        <TextField
          id={`month-${state.month}`}
          label={label}
          value={text}
          onChange={(e) => onChange(e.target.value)}
          disabled={readOnly}
          size="small"
          fullWidth
          error={Boolean(message)}
          helperText={message ?? " "}
          slotProps={{
            htmlInput: {
              inputMode: "decimal",
              "aria-label": label,
              autoComplete: "off",
              // select the figure so typing replaces it instead of adding to it ("31" + "24" is "3124")
              onFocus: (e: FocusEvent<HTMLInputElement>) => e.target.select(),
              onMouseUp: (e: MouseEvent<HTMLInputElement>) => {
                if (e.currentTarget.dataset.justFocused) {
                  e.preventDefault();
                  delete e.currentTarget.dataset.justFocused;
                }
              },
              onMouseDown: (e: MouseEvent<HTMLInputElement>) => {
                if (document.activeElement !== e.currentTarget)
                  e.currentTarget.dataset.justFocused = "1";
              },
            },
            formHelperText: { role: message ? "alert" : undefined },
          }}
        />
        <Typography variant="caption" color="text.secondary">
          Calendar: {state.calendarDerived}
          {state.workingDays !== null
            ? ` · Stored: ${formatFigure(state.workingDays)}` +
              (state.updatedBy && state.updatedAt
                ? ` · changed by ${state.updatedBy} on ${formatWhen(state.updatedAt)}`
                : "")
            : " · Nothing stored"}
        </Typography>
        {showStored ? (
          <Box>
            <Button size="small" onClick={onUseStored}>
              Use the stored figure ({formatFigure(state.workingDays ?? 0)})
            </Button>
          </Box>
        ) : null}
      </Stack>
    </Box>
  );
}
