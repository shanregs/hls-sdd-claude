import { Box, Typography } from "@mui/material";
import { SessionsCard } from "./SessionsCard";

/** Sessions (every signed-in user): the devices signed in as you, with a way to end any other one. */
export function SessionsPage() {
  return (
    <Box sx={{ maxWidth: 720 }}>
      <Typography variant="h5" component="h1" gutterBottom>
        My sessions
      </Typography>
      <SessionsCard />
    </Box>
  );
}
