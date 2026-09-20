import { useEffect, useState } from "react";
import "./StatusPage.css";

type Status = "OK" | "DEGRADED";

interface StatusApiResponse {
  status: Status;
  version: string;
  serverTime: string;
  dataStoreReachable: boolean;
  checkDurationMs: number;
}

// Maps the API's enum values (data-model.md, contracts/status-api.yaml) to the
// spec's user-facing display labels (FR-002, User Stories) — the two are not
// required to be spelled the same way.
const STATUS_LABELS: Record<Status, string> = {
  OK: "OK",
  DEGRADED: "Degraded",
};

export function StatusPage() {
  const [data, setData] = useState<StatusApiResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    // cache: "no-store" plus a fresh fetch on every mount means a page reload
    // always reflects a brand-new check — SC-002 rules out ever showing a
    // stale "OK" once a fresh check has run.
    fetch("/api/status", { cache: "no-store" })
      .then((response) => response.json() as Promise<StatusApiResponse>)
      .then((json) => {
        if (!cancelled) setData(json);
      })
      .catch(() => {
        if (!cancelled) setError("Unable to reach the status endpoint.");
      });

    return () => {
      cancelled = true;
    };
  }, []);

  if (error) {
    return (
      <div className="status-page status-page--error" data-testid="status-page">
        {error}
      </div>
    );
  }

  if (!data) {
    return (
      <div className="status-page" data-testid="status-page">
        Checking system status…
      </div>
    );
  }

  const isOk = data.status === "OK";
  const serverTimeIst = new Date(data.serverTime).toLocaleString("en-IN", {
    timeZone: "Asia/Kolkata",
    dateStyle: "medium",
    timeStyle: "medium",
  });

  return (
    <div
      className={`status-page ${isOk ? "status-page--ok" : "status-page--degraded"}`}
      data-testid="status-page"
    >
      <h1 className="status-page__status" data-testid="status-label">
        {STATUS_LABELS[data.status]}
      </h1>
      <dl className="status-page__details">
        <dt>Version</dt>
        <dd data-testid="status-version">{data.version}</dd>
        <dt>Server time (IST)</dt>
        <dd data-testid="status-time">{serverTimeIst}</dd>
      </dl>
    </div>
  );
}
