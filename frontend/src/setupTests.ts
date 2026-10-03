import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Vitest doesn't auto-register React Testing Library's cleanup the way Jest does, so without
// this, DOM from one test's render() lingers into the next test's queries.
afterEach(() => {
  cleanup();
});

// jsdom has no ResizeObserver; MUI X DataGrid needs one to measure its container and render rows.
if (typeof window.ResizeObserver === "undefined") {
  window.ResizeObserver = class ResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
}
