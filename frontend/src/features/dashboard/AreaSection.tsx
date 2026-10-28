import { useCallback, useEffect, useState } from "react";
import { Alert, Box, Button, Collapse, Typography } from "@mui/material";
import ExpandLess from "@mui/icons-material/ExpandLess";
import ExpandMore from "@mui/icons-material/ExpandMore";
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

const COLLAPSED_AREAS_KEY = "hls.dashboard.collapsedAreas";

function loadCollapsedAreas(): Set<string> {
  try {
    const raw = window.localStorage.getItem(COLLAPSED_AREAS_KEY);
    return new Set(raw ? (JSON.parse(raw) as string[]) : []);
  } catch {
    return new Set();
  }
}

function saveCollapsedArea(area: string, collapsed: boolean) {
  try {
    const next = loadCollapsedAreas();
    if (collapsed) next.add(area);
    else next.delete(area);
    window.localStorage.setItem(COLLAPSED_AREAS_KEY, JSON.stringify([...next]));
  } catch {
    // Storage can be unavailable (private windows); the choice then lasts for this visit only.
  }
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
  const [collapsed, setCollapsed] = useState(() =>
    loadCollapsedAreas().has(area.area),
  );
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
  const bodyId = `dashboard-area-body-${area.area}`;
  // An area holding a table needs the whole row; the others sit side by side on wide screens.
  const hasTable = [...resultsByKey.values()].some((t) => t.table);
  const toggle = () => {
    setCollapsed((c) => {
      saveCollapsedArea(area.area, !c);
      return !c;
    });
  };

  return (
    <Box
      component="section"
      aria-labelledby={headingId}
      sx={{ minWidth: 0, ...(hasTable ? { gridColumn: "1 / -1" } : {}) }}
    >
      <Typography variant="h6" component="h3" id={headingId} gutterBottom>
        <Button
          color="inherit"
          onClick={toggle}
          aria-expanded={!collapsed}
          aria-controls={bodyId}
          endIcon={collapsed ? <ExpandMore /> : <ExpandLess />}
          sx={{ font: "inherit", textTransform: "none", px: 0 }}
        >
          {area.title}
        </Button>
      </Typography>
      <Collapse in={!collapsed} id={bodyId}>
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
      </Collapse>
    </Box>
  );
}
