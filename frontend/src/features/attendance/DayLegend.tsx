import { Box, Stack, Typography } from "@mui/material";
import { DAY_KIND_LABELS, dayBackground, type DayKind } from "./dayStyle";

const SAMPLES: { kind: Exclude<DayKind, "plain">; text: string }[] = [
  { kind: "off", text: " " },
  { kind: "holiday", text: "H" },
  { kind: "leave", text: "L / A" },
];

/** Explains the day colours; the label beside each swatch names it, and day cells keep accessible names (SC-010). */
export function DayLegend({
  kinds = ["off", "holiday", "leave"],
}: {
  kinds?: Exclude<DayKind, "plain">[];
}) {
  return (
    <Stack
      direction="row"
      spacing={2}
      component="ul"
      aria-label="Day colours"
      sx={{ listStyle: "none", m: 0, p: 0, flexWrap: "wrap", gap: 1 }}
    >
      {SAMPLES.filter((s) => kinds.includes(s.kind)).map(({ kind, text }) => (
        <Stack
          key={kind}
          component="li"
          direction="row"
          spacing={1}
          sx={{ alignItems: "center" }}
        >
          <Box
            component="span"
            sx={{
              minWidth: 40,
              px: 0.75,
              py: 0.25,
              textAlign: "center",
              borderRadius: 0.5,
              border: 1,
              borderColor: "divider",
              bgcolor: dayBackground(kind),
              fontSize: "0.75rem",
              fontWeight: 700,
            }}
          >
            {text}
          </Box>
          <Typography variant="caption">{DAY_KIND_LABELS[kind]}</Typography>
        </Stack>
      ))}
    </Stack>
  );
}
