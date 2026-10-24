import { useCallback, useEffect, useState } from "react";
import { Alert, Box, Button, Typography } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  fetchArea,
  fetchTile,
  type AreaResponse,
  type DashboardFilter,
  type ManifestArea,
  type TileResult,
} from "./dashboardApi";
import { TileCard } from "./TileCard";

interface AreaSectionProps {
  area: ManifestArea;
  filter?: DashboardFilter;
  /** Bumped by the Refresh button; each bump reads the area again. */
  refreshToken: number;
  onTaken: (area: string, takenAt: string) => void;
}

type State =
  | { kind: "ready"; token: number; response: AreaResponse }
  | { kind: "error"; token: number; reason: string };

/** One area (spec 028 FR-005, FR-013): loads on its own, so it shows as soon as it is ready; each tile can be retried. */
export function AreaSection({
  area,
  filter,
  refreshToken,
  onTaken,
}: AreaSectionProps) {
  const { authFetch } = useAuth();
  const [state, setState] = useState<State | null>(null);
  const [retries, setRetries] = useState(0);
  const [tileOverrides, setTileOverrides] = useState<
    Record<string, TileResult>
  >({});
  const zoneId = filter?.zoneId;
  const period = filter?.period;
  const token = refreshToken * 1000 + retries;

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await fetchArea(authFetch, area.area, { zoneId, period });
      if (cancelled) return;
      if (result.ok) {
        setTileOverrides({});
        setState({ kind: "ready", token, response: result.data });
        onTaken(area.area, result.data.takenAt);
      } else {
        setState({ kind: "error", token, reason: result.reason });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, area.area, zoneId, period, token, onTaken]);

  const retryTile = useCallback(
    async (key: string) => {
      const result = await fetchTile(authFetch, area.area, key, {
        zoneId,
        period,
      });
      if (result.ok) {
        setTileOverrides((current) => ({ ...current, [key]: result.data }));
        if (result.data.takenAt) onTaken(area.area, result.data.takenAt);
      }
    },
    [authFetch, area.area, zoneId, period, onTaken],
  );

  const current = state !== null && state.token === token ? state : null;
  const resultsByKey = new Map<string, TileResult>();
  if (state?.kind === "ready") {
    // keep showing the previous figures while a Refresh is under way
    for (const tile of state.response.tiles) resultsByKey.set(tile.key, tile);
    for (const [key, tile] of Object.entries(tileOverrides))
      resultsByKey.set(key, tile);
  }
  const loading = current === null;
  const headingId = `dashboard-area-${area.area}`;

  return (
    <Box component="section" aria-labelledby={headingId} sx={{ mb: 3 }}>
      <Typography variant="h6" component="h3" id={headingId} gutterBottom>
        {area.title}
      </Typography>
      {loading && state?.kind !== "ready" && current === null && (
        <Typography variant="body2" color="text.secondary">
          Loading…
        </Typography>
      )}
      {current?.kind === "error" && (
        <Alert
          severity="error"
          action={
            <Button
              color="inherit"
              size="small"
              onClick={() => setRetries((n) => n + 1)}
            >
              Try again
            </Button>
          }
        >
          {current.reason}
        </Alert>
      )}
      {state?.kind === "ready" && (
        <Box
          sx={{
            display: "grid",
            gridTemplateColumns:
              "repeat(auto-fit, minmax(min(220px, 100%), 1fr))",
            gap: 2,
          }}
        >
          {area.tiles.map((tile) => (
            <TileCard
              key={tile.key}
              definition={tile}
              result={resultsByKey.get(tile.key)}
              onRetry={() => void retryTile(tile.key)}
            />
          ))}
        </Box>
      )}
    </Box>
  );
}
