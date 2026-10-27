import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableRow,
  Typography,
} from "@mui/material";
import { ThemeProvider } from "@mui/material/styles";
import { ratio } from "../theme/contrast";
import {
  buildMuiTheme,
  resolveColours,
  type ThemeCatalogue,
  type ThemeMode,
  type ThemePreset,
} from "../theme/tokens";

interface Props {
  mode: ThemeMode;
  preset: ThemePreset;
  catalogue: ThemeCatalogue;
  /** The accent being edited (or already saved); not applied to the app until the user applies it. */
  customAccent: string | null;
}

/** A small sample of the app (header, button, card, table row, status chips) in the theme being chosen, with its contrast ratios (FR-011). */
export function ThemePreview({ mode, preset, catalogue, customAccent }: Props) {
  const colours = resolveColours(mode, {
    colours: preset[mode],
    customAccent,
  });
  const theme = buildMuiTheme(mode, { colours: preset[mode], customAccent });
  const chips = catalogue.statusChips[mode];
  const rows: [string, number][] = [
    ["Text on surface", ratio(colours.text, colours.surface)],
    [
      "Accent colour as text on surface",
      ratio(colours.primary, colours.surface),
    ],
    [
      "Header text on the header colour",
      ratio(colours.onPrimary, colours.primary),
    ],
  ];
  return (
    <ThemeProvider theme={theme}>
      <Box
        aria-label="Theme preview"
        role="group"
        sx={{
          bgcolor: "background.default",
          color: "text.primary",
          border: 1,
          borderColor: "divider",
          borderRadius: 2,
          overflow: "hidden",
        }}
      >
        <Box
          sx={{
            bgcolor: "primary.main",
            color: "primary.contrastText",
            px: 2,
            py: 1.5,
          }}
        >
          <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
            HLS Teacher Management System
          </Typography>
        </Box>
        <Stack spacing={2} sx={{ p: 2 }}>
          <Box>
            <Button variant="contained">Primary button</Button>
          </Box>
          <Card>
            <CardContent>
              <Typography variant="subtitle2">Sample card</Typography>
              <Typography variant="body2" color="text.secondary">
                Secondary text on the card surface.
              </Typography>
            </CardContent>
          </Card>
          <Table size="small" aria-label="Sample table">
            <TableBody>
              <TableRow>
                <TableCell>Asha Admin</TableCell>
                <TableCell>School One</TableCell>
              </TableRow>
            </TableBody>
          </Table>
          <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap", gap: 1 }}>
            {(["approved", "pending", "rejected"] as const).map((kind) => (
              <Chip
                key={kind}
                size="small"
                label={kind[0].toUpperCase() + kind.slice(1)}
                sx={{
                  color: chips[kind].text,
                  bgcolor: chips[kind].background,
                  fontWeight: 600,
                }}
              />
            ))}
          </Stack>
        </Stack>
      </Box>
      <Box component="dl" sx={{ m: 0, mt: 2 }} aria-label="Contrast ratios">
        {rows.map(([label, value]) => (
          <Box key={label} sx={{ display: "flex", gap: 1, py: 0.25 }}>
            <Typography component="dt" variant="body2" sx={{ flexGrow: 1 }}>
              {label}
            </Typography>
            <Typography component="dd" variant="body2" sx={{ m: 0 }}>
              {value.toFixed(2)} : 1
              {value >= 4.5 ? " (passes)" : " (below 4.5)"}
            </Typography>
          </Box>
        ))}
      </Box>
    </ThemeProvider>
  );
}
