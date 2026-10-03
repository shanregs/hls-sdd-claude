import { useCallback } from "react";
import { useAuth } from "../../auth/useAuth";

/**
 * Triggers a streamed CSV download from an audit export endpoint (Foundational T009), reused by
 * all four audit pages. `from`/`to` are required by every export endpoint
 * (contracts/audit-api.md) — the caller is responsible for not calling this without them set.
 */
export function useAuditExport() {
  const { authFetch } = useAuth();

  return useCallback(
    async (endpoint: string, params: Record<string, string | undefined>) => {
      const query = new URLSearchParams();
      for (const [key, value] of Object.entries(params)) {
        if (value) {
          query.set(key, value);
        }
      }
      const response = await authFetch(`${endpoint}?${query.toString()}`);
      if (!response.ok) {
        throw new Error("Could not export.");
      }
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = "export.csv";
      link.click();
      URL.revokeObjectURL(url);
    },
    [authFetch],
  );
}
