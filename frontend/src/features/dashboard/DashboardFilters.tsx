import { MenuItem, Stack, TextField } from "@mui/material";
import type { DashboardFilter, Manifest } from "./dashboardApi";

interface DashboardFiltersProps {
  filters: Manifest["filters"];
  value: DashboardFilter;
  onChange: (next: DashboardFilter) => void;
}

/** The Zone and month pickers (spec 028 US8). Only Zones the manifest lists are offered; the month defaults to the current one. */
export function DashboardFilters({
  filters,
  value,
  onChange,
}: DashboardFiltersProps) {
  return (
    <Stack direction="row" spacing={2} sx={{ flexWrap: "wrap", rowGap: 1 }}>
      {filters.zones.length > 0 && (
        <TextField
          select
          size="small"
          label="Zone"
          value={value.zoneId ?? ""}
          onChange={(e) =>
            onChange({ ...value, zoneId: e.target.value || undefined })
          }
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">All Zones</MenuItem>
          {filters.zones.map((zone) => (
            <MenuItem key={zone.id} value={zone.id}>
              {zone.name}
            </MenuItem>
          ))}
        </TextField>
      )}
      <TextField
        type="month"
        size="small"
        label="Month"
        value={value.period ?? filters.defaultPeriod ?? ""}
        onChange={(e) =>
          onChange({ ...value, period: e.target.value || undefined })
        }
        slotProps={{ inputLabel: { shrink: true } }}
      />
    </Stack>
  );
}
