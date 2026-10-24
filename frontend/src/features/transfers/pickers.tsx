import { useEffect, useState } from "react";
import { MenuItem, TextField } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { listSchools, type SchoolSummary } from "../schools/schoolsApi";
import { listTeachers, type TeacherSummary } from "../teachers/teachersApi";

/** Active Teachers the caller may reach; with {@code placedOnly} only those placed at a School. */
export function TeacherSelect({
  label,
  value,
  onChange,
  placedOnly = false,
  exclude,
}: {
  label: string;
  value: string;
  onChange: (id: string) => void;
  placedOnly?: boolean;
  exclude?: string;
}) {
  const { authFetch } = useAuth();
  const [teachers, setTeachers] = useState<TeacherSummary[]>([]);
  const [problem, setProblem] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      const result = await listTeachers(authFetch, {
        query: "",
        status: "ACTIVE",
        page: 0,
        size: 200,
      });
      if (result.ok) setTeachers(result.data.content);
      else setProblem(result.reason);
    })();
  }, [authFetch]);

  return (
    <TextField
      select
      required
      label={label}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      error={Boolean(problem)}
      helperText={problem ?? undefined}
    >
      {teachers
        .filter((t) => t.id !== exclude && (!placedOnly || t.school))
        .map((t) => (
          <MenuItem key={t.id} value={t.id}>
            {t.name}
            {t.school ? ` (${t.school.name})` : ""}
          </MenuItem>
        ))}
    </TextField>
  );
}

/** Active Schools, optionally leaving one out (the School the Teacher is leaving). */
export function SchoolSelect({
  label,
  value,
  onChange,
  exclude,
  required = true,
  allowNone = false,
}: {
  label: string;
  value: string;
  onChange: (id: string) => void;
  exclude?: string;
  required?: boolean;
  allowNone?: boolean;
}) {
  const { authFetch } = useAuth();
  const [schools, setSchools] = useState<SchoolSummary[]>([]);
  const [problem, setProblem] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      const result = await listSchools(authFetch, {
        query: "",
        zoneId: "",
        active: "true",
        page: 0,
        size: 200,
      });
      if (result.ok) setSchools(result.data.content);
      else setProblem(result.reason);
    })();
  }, [authFetch]);

  return (
    <TextField
      select
      required={required}
      label={label}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      error={Boolean(problem)}
      helperText={problem ?? undefined}
    >
      {allowNone && (
        <MenuItem value="">None (the Teacher is available)</MenuItem>
      )}
      {schools
        .filter((s) => s.id !== exclude)
        .map((s) => (
          <MenuItem key={s.id} value={s.id}>
            {s.name}
          </MenuItem>
        ))}
    </TextField>
  );
}
