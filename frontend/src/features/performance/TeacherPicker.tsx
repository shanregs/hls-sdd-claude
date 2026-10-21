import { useEffect, useState } from "react";
import { MenuItem, TextField } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { listTeachers, type TeacherSummary } from "../teachers/teachersApi";

/** Chooses one of the Teachers the caller may reach (the server scopes the list). */
export function TeacherPicker({
  value,
  onChange,
  error,
  label = "Teacher",
}: {
  value: string;
  onChange: (teacherId: string) => void;
  error?: string | null;
  label?: string;
}) {
  const { authFetch } = useAuth();
  const [teachers, setTeachers] = useState<TeacherSummary[]>([]);
  const [problem, setProblem] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      const result = await listTeachers(authFetch, {
        query: "",
        status: "",
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
      error={Boolean(error || problem)}
      helperText={error ?? problem ?? undefined}
    >
      {teachers.map((t) => (
        <MenuItem key={t.id} value={t.id}>
          {t.name}
          {t.school ? ` (${t.school.name})` : ""}
        </MenuItem>
      ))}
    </TextField>
  );
}
