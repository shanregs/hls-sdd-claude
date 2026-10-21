import { Link as RouterLink, useLocation } from "react-router-dom";
import { Tab, Tabs } from "@mui/material";
import { PERFORMANCE_ROUTE } from "./performanceApi";

const TABS = [
  { label: "Observations", to: PERFORMANCE_ROUTE },
  { label: "Planned", to: `${PERFORMANCE_ROUTE}/planned` },
  { label: "Principal feedback", to: `${PERFORMANCE_ROUTE}/feedback` },
  { label: "Issues", to: `${PERFORMANCE_ROUTE}/issues` },
];

/** The four lists of OPERATIONS -> Teacher Performance, as links so each has its own address. */
export function PerformanceNav() {
  const { pathname } = useLocation();
  const current =
    TABS.slice(1).find(
      (t) => pathname === t.to || pathname.startsWith(`${t.to}/`),
    )?.to ?? PERFORMANCE_ROUTE;
  return (
    <Tabs
      value={current}
      variant="scrollable"
      aria-label="Teacher performance lists"
      sx={{ mb: 2 }}
    >
      {TABS.map((t) => (
        <Tab
          key={t.to}
          value={t.to}
          label={t.label}
          component={RouterLink}
          to={t.to}
        />
      ))}
    </Tabs>
  );
}
