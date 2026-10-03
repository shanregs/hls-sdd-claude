import { useEffect, useState } from "react";
import { Autocomplete, TextField } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { lookupPlaces, type PlaceSummary } from "../zones/zonesApi";

interface PlacePickerProps {
  value: PlaceSummary | null;
  onChange: (place: PlaceSummary | null) => void;
  error?: string;
}

/** Searches Places by name (two or more characters) and shows each with its PIN code and Zone. */
export function PlacePicker({ value, onChange, error }: PlacePickerProps) {
  const { authFetch } = useAuth();
  const [input, setInput] = useState("");
  const [options, setOptions] = useState<PlaceSummary[]>([]);

  useEffect(() => {
    if (input.trim().length < 2) {
      return;
    }
    let cancelled = false;
    (async () => {
      const isPin = /^[0-9]{6}$/.test(input.trim());
      const result = await lookupPlaces(
        authFetch,
        isPin ? input : "",
        isPin ? "" : input,
      );
      if (!cancelled && result.ok) {
        setOptions(result.data);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, input]);

  return (
    <Autocomplete
      options={value ? [value, ...options] : options}
      value={value}
      onChange={(_event, place) => onChange(place)}
      onInputChange={(_event, text) => setInput(text)}
      filterOptions={(all) => all}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      getOptionLabel={(place) =>
        `${place.name} - ${place.pinCode} (${place.zoneName})`
      }
      noOptionsText="Type at least two letters of a place name, or a PIN code"
      renderInput={(params) => (
        <TextField
          {...params}
          label="Place"
          required
          error={Boolean(error)}
          helperText={error}
        />
      )}
    />
  );
}
