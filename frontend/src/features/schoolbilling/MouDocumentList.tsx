import { useState } from "react";
import { Alert, Button, Stack, Typography } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { downloadMouDocument, type Approval } from "./mouApprovalApi";
import { formatInstant, formatSize } from "./mouFormat";

/**
 * The versions of the signed document, newest first, with the current one marked, a download for each version that is
 * not removed, and the removal record (who, when, why) for the others (spec 031 US1).
 */
export function MouDocumentList({
  approval,
  onRemove,
}: {
  approval: Approval;
  /** Present only when the caller may remove versions. */
  onRemove?: (documentId: string) => void;
}) {
  const { authFetch } = useAuth();
  const [error, setError] = useState<string | null>(null);

  if (approval.documents.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        No signed document attached yet.
      </Typography>
    );
  }
  return (
    <Stack spacing={1}>
      {error && (
        <Alert severity="error" role="alert" onClose={() => setError(null)}>
          {error}
        </Alert>
      )}
      <ul style={{ margin: 0, paddingLeft: 20 }} aria-label="Document versions">
        {approval.documents.map((d) => (
          <li key={d.id}>
            <Stack
              direction="row"
              spacing={1}
              sx={{ alignItems: "center", flexWrap: "wrap" }}
            >
              <Typography
                variant="body2"
                sx={d.removed ? { textDecoration: "line-through" } : undefined}
              >
                Version {d.versionNo}: {d.name} ({formatSize(d.sizeBytes)}),
                added by {d.addedBy.name} on {formatInstant(d.addedAt)}
              </Typography>
              {d.current && (
                <Typography
                  variant="caption"
                  sx={{ fontWeight: 700 }}
                  component="span"
                >
                  Current
                </Typography>
              )}
              {!d.removed && (
                <Button
                  size="small"
                  aria-label={`Download version ${d.versionNo}`}
                  onClick={() => {
                    void (async () => {
                      const result = await downloadMouDocument(
                        authFetch,
                        approval.contractId,
                        d.id,
                        d.name,
                      );
                      if (!result.ok) {
                        setError(result.reason);
                      }
                    })();
                  }}
                >
                  Download
                </Button>
              )}
              {!d.removed && onRemove && (
                <Button
                  size="small"
                  color="error"
                  aria-label={`Remove version ${d.versionNo}`}
                  onClick={() => onRemove(d.id)}
                >
                  Remove
                </Button>
              )}
            </Stack>
            {d.removed && (
              <Typography variant="caption" color="text.secondary">
                Removed by {d.removed.by.name} on {formatInstant(d.removed.at)}:{" "}
                {d.removed.reason}
              </Typography>
            )}
          </li>
        ))}
      </ul>
    </Stack>
  );
}
