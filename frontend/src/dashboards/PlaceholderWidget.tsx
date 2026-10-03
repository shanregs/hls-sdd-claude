import { Paper, Typography } from "@mui/material";

/** A "coming soon" placeholder card (FR-012): no fabricated data, since no business module has
 * shipped a real widget yet — specs 005+ replace these with real content. */
export function PlaceholderWidget({ title }: { title: string }) {
  return (
    <Paper
      variant="outlined"
      sx={{
        p: 2.5,
        minHeight: 96,
        display: "flex",
        flexDirection: "column",
        justifyContent: "center",
        gap: 0.5,
      }}
    >
      <Typography variant="subtitle2" color="text.secondary">
        {title}
      </Typography>
      <Typography variant="body2" color="text.disabled">
        Coming soon
      </Typography>
    </Paper>
  );
}
