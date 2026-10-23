import { useCallback, useEffect, useState } from "react";
import { Alert, Box, Button, Stack, Typography } from "@mui/material";
import RefreshIcon from "@mui/icons-material/Refresh";
import { useAuth } from "../../auth/useAuth";
import {
  fetchManifest,
  type DashboardFilter,
  type Manifest,
} from "./dashboardApi";
import { DashboardFilters } from "./DashboardFilters";
import { AreaSection } from "./AreaSection";
import { formatTime } from "./format";

type ManifestState =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; manifest: Manifest };

/**
 * The Admin and Director overview (spec 028 US1). The server decides what appears: the manifest lists the areas and tiles
 * the caller may see, nothing here tests a role. Figures are read on load and on Refresh only.
 */
export function ManagementDashboard({ title }: { title: string }) {
  const { authFetch } = useAuth();
  const [state, setState] = useState<ManifestState>({ kind: "loading" });
  const [refreshToken, setRefreshToken] = useState(0);
  const [filter, setFilter] = useState<DashboardFilter>({});
  const [taken, setTaken] = useState<Record<string, string>>({});

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await fetchManifest(authFetch);
      if (cancelled) return;
      setState(
        result.ok
          ? { kind: "ready", manifest: result.data }
          : { kind: "error", reason: result.reason },
      );
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  const onTaken = useCallback((area: string, takenAt: string) => {
    setTaken((current) =>
      current[area] === takenAt ? current : { ...current, [area]: takenAt },
    );
  }, []);

  const latest = Object.values(taken).sort().at(-1);

  return (
    <Box>
      <Stack
        direction="row"
        spacing={2}
        sx={{ alignItems: "center", flexWrap: "wrap", mb: 2 }}
      >
        <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>
          {title}
        </Typography>
        {state.kind === "ready" && state.manifest.areas.length > 0 && (
          <>
            <DashboardFilters
              filters={state.manifest.filters}
              value={filter}
              onChange={setFilter}
            />
            {latest && (
              <Typography variant="body2" color="text.secondary">
                {`Figures taken at ${formatTime(latest)}`}
              </Typography>
            )}
            <Button
              variant="outlined"
              size="small"
              startIcon={<RefreshIcon />}
              onClick={() => setRefreshToken((n) => n + 1)}
            >
              Refresh
            </Button>
          </>
        )}
      </Stack>
      {state.kind === "loading" && (
        <Typography variant="body2" color="text.secondary">
          Loading…
        </Typography>
      )}
      {state.kind === "error" && <Alert severity="error">{state.reason}</Alert>}
      {state.kind === "ready" && state.manifest.areas.length === 0 && (
        <Typography variant="body2" color="text.secondary">
          There is nothing to show here yet.
        </Typography>
      )}
      {state.kind === "ready" &&
        state.manifest.areas.map((area) => (
          <AreaSection
            key={area.area}
            area={area}
            filter={filter}
            refreshToken={refreshToken}
            onTaken={onTaken}
          />
        ))}
    </Box>
  );
}
