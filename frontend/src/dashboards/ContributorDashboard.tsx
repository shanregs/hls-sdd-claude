import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { Box, Link, Typography } from "@mui/material";
import { useAuth } from "../auth/useAuth";
import {
  MY_CONTENT_ROUTE,
  getSummary,
  type ContentSummary,
} from "../features/content/contentApi";

const CARDS: { key: keyof ContentSummary; label: string; status?: string }[] = [
  { key: "draft", label: "Drafts", status: "DRAFT" },
  { key: "assigned", label: "Assigned to me", status: "ASSIGNED" },
  { key: "submitted", label: "Waiting for review", status: "SUBMITTED" },
  { key: "returned", label: "Returned to me", status: "RETURNED" },
  { key: "approved", label: "Approved", status: "APPROVED" },
  { key: "overdue", label: "Overdue" },
];

/** Content Contributor landing dashboard (spec 029 FR-020): the counts of their own content by status. */
export function ContributorDashboard() {
  const { authFetch } = useAuth();
  const [summary, setSummary] = useState<ContentSummary | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    void getSummary(authFetch).then((result) => {
      if (result.ok) setSummary(result.data);
      else setFailed(true);
    });
  }, [authFetch]);

  return (
    <Box>
      <Typography variant="h6" component="h2" gutterBottom>
        My content
      </Typography>
      {failed && <Typography color="error">Could not load your counts.</Typography>}
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))",
          gap: 2,
        }}
      >
        {CARDS.map((card) => (
          <Box key={card.key} sx={{ p: 2, border: 1, borderColor: "divider", borderRadius: 1 }}>
            <Typography variant="h4" component="p">
              {summary ? summary[card.key] : "-"}
            </Typography>
            <Link
              component={RouterLink}
              to={card.status ? `${MY_CONTENT_ROUTE}?status=${card.status}` : MY_CONTENT_ROUTE}
            >
              {card.label}
            </Link>
          </Box>
        ))}
      </Box>
    </Box>
  );
}
