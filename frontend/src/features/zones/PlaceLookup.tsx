import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  List,
  ListItem,
  ListItemText,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { lookupPlaces, type PlaceSummary } from "./zonesApi";

/** Finds every Place with a given PIN code and/or name, with its Zone (FR-002). */
export function PlaceLookup() {
  const { authFetch } = useAuth();
  const [pinCode, setPinCode] = useState("");
  const [name, setName] = useState("");
  const [results, setResults] = useState<PlaceSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const search = async () => {
    setError(null);
    const result = await lookupPlaces(authFetch, pinCode, name);
    if (!result.ok) {
      setResults(null);
      setError(result.reason);
      return;
    }
    setResults(result.data);
  };

  return (
    <Box sx={{ mb: 3 }}>
      <Typography variant="h6" component="h2" gutterBottom>
        Find a place
      </Typography>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
        <TextField
          label="PIN code"
          size="small"
          value={pinCode}
          onChange={(e) => setPinCode(e.target.value)}
        />
        <TextField
          label="Place name"
          size="small"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <Button variant="outlined" onClick={search}>
          Find
        </Button>
      </Stack>
      {error && (
        <Alert severity="error" sx={{ mt: 2 }} role="alert">
          {error}
        </Alert>
      )}
      {results && results.length === 0 && (
        <Typography sx={{ mt: 2 }}>No places match.</Typography>
      )}
      {results && results.length > 0 && (
        <List dense>
          {results.map((place) => (
            <ListItem key={place.id}>
              <ListItemText
                primary={`${place.name} - ${place.pinCode}`}
                secondary={`Zone: ${place.zoneName}`}
              />
            </ListItem>
          ))}
        </List>
      )}
    </Box>
  );
}
