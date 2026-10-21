import { Link as RouterLink } from "react-router-dom";
import { Box, Button, Chip, Link, Paper, Typography } from "@mui/material";
import type { TileDefinition, TileLink, TileResult } from "./dashboardApi";
import { formatValue } from "./format";

interface TileCardProps {
  definition: TileDefinition;
  /** Undefined while the area is still loading. */
  result?: TileResult;
  onRetry: () => void;
}

function target(link: TileLink) {
  const search = new URLSearchParams(link.query).toString();
  return { pathname: link.path, search: search === "" ? "" : `?${search}` };
}

const STATE_TEXT = {
  NO_DATA: "No data yet",
  NOT_AVAILABLE: "Not available yet",
  NOT_WORKING_DAY: "Not a working day",
  ERROR: "Could not be read",
} as const;

/**
 * One tile (spec 028 FR-006, FR-007, FR-012): its figures as links to the list that holds the same records, or a plain
 * statement when the figure is missing. A missing figure is never drawn as 0.
 */
export function TileCard({ definition, result, onRetry }: TileCardProps) {
  return (
    <Paper variant="outlined" sx={{ p: 2, minWidth: 0 }}>
      <Typography variant="subtitle2" color="text.secondary" component="p">
        {definition.label}
      </Typography>
      {result === undefined && (
        <Typography variant="body2" color="text.secondary">
          Loading…
        </Typography>
      )}
      {result?.state === "OK" &&
        result.values.map((value, index) => {
          const link = value.link ?? definition.link;
          const figure = (
            <>
              <Typography component="span" variant="body2" sx={{ mr: 1 }}>
                {value.label}
              </Typography>
              <Typography
                component="span"
                variant="h5"
                sx={{ fontWeight: 600 }}
              >
                {formatValue(value)}
              </Typography>
            </>
          );
          return (
            <Box key={`${value.label}-${index}`} sx={{ mt: 0.5 }}>
              {link ? (
                <Link
                  component={RouterLink}
                  to={target(link)}
                  underline="hover"
                  aria-label={`${definition.label}: ${value.label} ${formatValue(value)}`}
                  sx={{
                    display: "inline-flex",
                    alignItems: "baseline",
                    flexWrap: "wrap",
                  }}
                >
                  {figure}
                </Link>
              ) : (
                <Box sx={{ display: "inline-flex", alignItems: "baseline" }}>
                  {figure}
                </Box>
              )}
            </Box>
          );
        })}
      {result !== undefined && result.state !== "OK" && (
        <Box sx={{ mt: 0.5 }}>
          <Typography
            variant="body2"
            color={result.state === "ERROR" ? "error" : "text.secondary"}
          >
            {result.note ?? STATE_TEXT[result.state]}
          </Typography>
          {result.state === "ERROR" && (
            <Button size="small" onClick={onRetry} sx={{ mt: 0.5 }}>
              Retry
            </Button>
          )}
        </Box>
      )}
      {result?.organizationWide && (
        <Chip
          size="small"
          variant="outlined"
          label="Organization-wide"
          sx={{ mt: 1 }}
        />
      )}
    </Paper>
  );
}
