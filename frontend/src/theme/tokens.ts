import { createTheme, type Theme } from "@mui/material/styles";

/**
 * One token set drives both themes (Constitution Principle IV: "one design system ... design
 * tokens for color, spacing, and type"). Spec 002 reuses these unchanged for the app shell.
 */
export const spacingUnit = 8;

export const typography = {
  fontFamily: '"Inter", "Segoe UI", Roboto, Helvetica, Arial, sans-serif',
};

const lightPalette = {
  mode: "light" as const,
  primary: { main: "#1E5AA8" },
  secondary: { main: "#0F7B6C" },
  error: { main: "#B3261E" },
  background: { default: "#F5F7FA", paper: "#FFFFFF" },
};

const darkPalette = {
  mode: "dark" as const,
  primary: { main: "#8AB4F8" },
  secondary: { main: "#5FD6C0" },
  error: { main: "#F2B8B5" },
  background: { default: "#111418", paper: "#1B1F24" },
};

/** A light hairline for table and grid cells, readable in both themes. */
const cellBorder = {
  light: "rgba(0, 0, 0, 0.12)",
  dark: "rgba(255, 255, 255, 0.16)",
};

export type ThemeMode = "light" | "dark";

export function buildMuiTheme(mode: ThemeMode): Theme {
  return createTheme({
    palette: mode === "light" ? lightPalette : darkPalette,
    typography,
    spacing: spacingUnit,
    shape: { borderRadius: 8 },
    components: {
      // Every plain table (attendance grids, audit lists, status codes) gets bordered cells.
      MuiTableCell: {
        styleOverrides: {
          root: { border: `1px solid ${cellBorder[mode]}` },
        },
      },
      // The MUI X DataGrid is styled by class name so no theme augmentation is needed.
      MuiCssBaseline: {
        styleOverrides: {
          ".MuiDataGrid-columnHeader, .MuiDataGrid-cell": {
            borderRight: `1px solid ${cellBorder[mode]}`,
          },
          ".MuiDataGrid-cell": {
            borderBottom: `1px solid ${cellBorder[mode]}`,
          },
        },
      },
    },
  });
}
