import { Box, Button, Typography } from "@mui/material";
import { useNavigate } from "react-router-dom";

/** Generic "not authorized" page (FR-010): reveals nothing about the blocked screen, consistent
 * with spec 001's practice of not revealing information to unauthorized parties. */
export function NotAuthorizedPage() {
  const navigate = useNavigate();

  return (
    <Box sx={{ p: 4, maxWidth: 480 }}>
      <Typography variant="h5" component="h1" gutterBottom>
        Not authorized
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        You don&apos;t have access to this page.
      </Typography>
      <Button
        variant="contained"
        onClick={() => navigate("/dashboard", { replace: true })}
      >
        Back to my dashboard
      </Button>
    </Box>
  );
}
