import { Stack, TextField, Typography } from "@mui/material";

export interface ContactValue {
  name: string;
  phone?: string | null;
  email?: string | null;
}

interface ContactFieldsProps {
  /** The role of the person ("Principal", "Accountant", "Placement officer"); used in every field label. */
  label: string;
  value: ContactValue;
  onChange: (next: ContactValue) => void;
}

/** A named contact person: name, phone and email, grouped and labelled for assistive technology. */
export function ContactFields({ label, value, onChange }: ContactFieldsProps) {
  return (
    <Stack spacing={1} role="group" aria-label={label}>
      <Typography variant="subtitle2">{label}</Typography>
      <TextField
        size="small"
        label={`${label} name`}
        value={value.name}
        onChange={(e) => onChange({ ...value, name: e.target.value })}
      />
      <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
        <TextField
          size="small"
          label={`${label} phone`}
          value={value.phone ?? ""}
          onChange={(e) => onChange({ ...value, phone: e.target.value })}
          fullWidth
        />
        <TextField
          size="small"
          label={`${label} email`}
          value={value.email ?? ""}
          onChange={(e) => onChange({ ...value, email: e.target.value })}
          fullWidth
        />
      </Stack>
    </Stack>
  );
}
