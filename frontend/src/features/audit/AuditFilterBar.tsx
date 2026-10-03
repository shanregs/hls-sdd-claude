import type { ReactNode } from "react";
import { Button, Stack, TextField } from "@mui/material";

export interface AuditFilters {
  from: string;
  to: string;
  userId: string;
}

interface AuditFilterBarProps {
  filters: AuditFilters;
  onChange: (filters: AuditFilters) => void;
  onExport: () => void;
  /** Extra, screen-specific filter controls (e.g. method/outcome, action type). */
  children?: ReactNode;
}

/**
 * Shared date-range + actor/user filter controls for all four audit screens (Foundational T009).
 * `from`/`to` are plain `yyyy-MM-dd` date strings; callers turn them into ISO instants when
 * building the query string.
 */
export function AuditFilterBar({
  filters,
  onChange,
  onExport,
  children,
}: AuditFilterBarProps) {
  return (
    <Stack
      direction={{ xs: "column", sm: "row" }}
      spacing={2}
      sx={{ mb: 2, alignItems: { sm: "center" } }}
    >
      <TextField
        label="From"
        type="date"
        size="small"
        value={filters.from}
        onChange={(e) => onChange({ ...filters, from: e.target.value })}
        slotProps={{ inputLabel: { shrink: true } }}
      />
      <TextField
        label="To"
        type="date"
        size="small"
        value={filters.to}
        onChange={(e) => onChange({ ...filters, to: e.target.value })}
        slotProps={{ inputLabel: { shrink: true } }}
      />
      <TextField
        label="User ID"
        size="small"
        value={filters.userId}
        onChange={(e) => onChange({ ...filters, userId: e.target.value })}
      />
      {children}
      <Button variant="outlined" onClick={onExport} sx={{ ml: { sm: "auto" } }}>
        Export CSV
      </Button>
    </Stack>
  );
}
