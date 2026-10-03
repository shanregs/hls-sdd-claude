/** Converts the AuditFilterBar's `yyyy-MM-dd` date strings to ISO instants for the audit API's
 * `from`/`to` query params (start of day / end of day, UTC). */
export function dateRangeToInstants(
  from: string,
  to: string,
): { from?: string; to?: string } {
  return {
    from: from ? `${from}T00:00:00Z` : undefined,
    to: to ? `${to}T23:59:59Z` : undefined,
  };
}
