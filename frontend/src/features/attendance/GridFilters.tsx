import { useEffect, useState } from "react";
import { MenuItem, TextField } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { listManagers } from "../managers/managersApi";
import { listSchools } from "../schools/schoolsApi";
import { listZones } from "../zones/zonesApi";
import type { GridFilterValues } from "./gridFilterValues";

interface Option {
  id: string;
  name: string;
}

const STATUSES = [
  ["IN_TRAINING", "In training"],
  ["ACTIVE", "Active"],
  ["ON_LEAVE", "On leave"],
  ["EXITED", "Exited"],
];

/** Zone, School, Manager and Teacher status filters for the organization grid (spec 008 FR-013). */
export function GridFilters({
  value,
  onChange,
}: {
  value: GridFilterValues;
  onChange: (next: GridFilterValues) => void;
}) {
  const { authFetch } = useAuth();
  const [zones, setZones] = useState<Option[]>([]);
  const [schools, setSchools] = useState<Option[]>([]);
  const [managers, setManagers] = useState<Option[]>([]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const [z, s, m] = await Promise.all([
        listZones(authFetch, "", 0, 100),
        listSchools(authFetch, {
          query: "",
          zoneId: "",
          active: "",
          page: 0,
          size: 100,
        }),
        listManagers(authFetch, "", 0, 100),
      ]);
      if (cancelled) return;
      if (z.ok)
        setZones(z.data.content.map((x) => ({ id: x.id, name: x.name })));
      if (s.ok)
        setSchools(s.data.content.map((x) => ({ id: x.id, name: x.name })));
      if (m.ok)
        setManagers(
          m.data.content.map((x) => ({ id: x.id, name: x.displayName })),
        );
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  const select = (
    label: string,
    field: keyof GridFilterValues,
    options: [string, string][],
  ) => (
    <TextField
      select
      size="small"
      label={label}
      value={value[field]}
      onChange={(e) => onChange({ ...value, [field]: e.target.value })}
      sx={{ minWidth: 160 }}
    >
      <MenuItem value="">All</MenuItem>
      {options.map(([id, name]) => (
        <MenuItem key={id} value={id}>
          {name}
        </MenuItem>
      ))}
    </TextField>
  );

  return (
    <>
      {select(
        "Zone",
        "zoneId",
        zones.map((z) => [z.id, z.name]),
      )}
      {select(
        "School",
        "schoolId",
        schools.map((s) => [s.id, s.name]),
      )}
      {select(
        "Manager",
        "managerId",
        managers.map((m) => [m.id, m.name]),
      )}
      {select("Status", "status", STATUSES as [string, string][])}
    </>
  );
}
