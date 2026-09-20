import { StatusPage } from "./pages/StatusPage/StatusPage";

/**
 * Root app shell. Only one page exists so far (the status page), and per FR-001 it
 * is reachable without any authentication guard — there is no login/route-gating
 * logic to add until the Identity & Access module ships.
 */
export function App() {
  return <StatusPage />;
}
