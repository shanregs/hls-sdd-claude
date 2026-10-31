import { useEffect, useState } from "react";
import { Autocomplete, TextField } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  PERSON_KIND_LABELS,
  searchPeople,
  type FoundPerson,
} from "./trainingApi";

interface Props {
  label: string;
  value: FoundPerson | null;
  onChange: (person: FoundPerson | null) => void;
  /** Restrict to one kind; leave out for Teachers and Managers. */
  kind?: FoundPerson["kind"];
  /** Ids to leave out, for people already chosen. */
  exclude?: string[];
  disabled?: boolean;
}

/** Search-as-you-type picker of Teachers and Managers within the caller's scope (the server applies the scope). */
export function PersonPicker({
  label,
  value,
  onChange,
  kind,
  exclude = [],
  disabled,
}: Props) {
  const { authFetch } = useAuth();
  const [text, setText] = useState("");
  const [options, setOptions] = useState<FoundPerson[]>([]);
  const [loading, setLoading] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- shows the loading state while the search runs
    setLoading(true);
    const timer = setTimeout(async () => {
      const result = await searchPeople(authFetch, text, kind);
      if (cancelled) return;
      setLoading(false);
      if (result.ok) {
        setFailure(null);
        setOptions(result.data);
      } else {
        setOptions([]);
        setFailure(result.reason);
      }
    }, 250);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [authFetch, text, kind]);

  return (
    <Autocomplete
      options={options.filter((o) => !exclude.includes(o.id))}
      value={value}
      loading={loading}
      disabled={disabled}
      onChange={(_, next) => onChange(next)}
      onInputChange={(_, next, reason) => {
        if (reason !== "reset") setText(next);
      }}
      getOptionLabel={(p) => p.name}
      getOptionKey={(p) => `${p.kind}-${p.id}`}
      isOptionEqualToValue={(a, b) => a.kind === b.kind && a.id === b.id}
      filterOptions={(all) => all}
      loadingText="Searching"
      noOptionsText="Nobody found. Try part of the name."
      renderOption={(props, p) => (
        <li {...props} key={`${p.kind}-${p.id}`}>
          {p.name} ({PERSON_KIND_LABELS[p.kind]})
        </li>
      )}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          error={failure !== null}
          helperText={failure ?? "Type part of a name to search."}
        />
      )}
    />
  );
}
