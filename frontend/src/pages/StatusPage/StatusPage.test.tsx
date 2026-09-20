import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { StatusPage } from "./StatusPage";

function mockFetchOnce(body: unknown, delayMs = 0) {
  vi.stubGlobal(
    "fetch",
    vi.fn(
      () =>
        new Promise((resolve) =>
          setTimeout(
            () =>
              resolve({
                ok: true,
                json: () => Promise.resolve(body),
              } as Response),
            delayMs,
          ),
        ),
    ),
  );
}

const okResponse = {
  status: "OK",
  version: "0.1.0-warmup",
  serverTime: "2026-09-21T14:32:10+05:30",
  dataStoreReachable: true,
  checkDurationMs: 42,
};

const degradedResponse = {
  status: "DEGRADED",
  version: "0.1.0-warmup",
  serverTime: "2026-09-21T14:33:05+05:30",
  dataStoreReachable: false,
  checkDurationMs: 3000,
};

describe("StatusPage", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("renders OK status, version, and server time from a successful API response", async () => {
    mockFetchOnce(okResponse);
    render(<StatusPage />);

    // T012 (US1)
    expect(await screen.findByTestId("status-label")).toHaveTextContent("OK");
    expect(screen.getByTestId("status-version")).toHaveTextContent(okResponse.version);
    expect(screen.getByTestId("status-time")).not.toBeEmptyDOMElement();
  });

  it("renders a Degraded state visually distinct from OK", async () => {
    // T020 (US2) — the API's DEGRADED enum value must render as spec.md's "Degraded" label.
    mockFetchOnce(degradedResponse);
    render(<StatusPage />);

    const label = await screen.findByTestId("status-label");
    expect(label).toHaveTextContent("Degraded");
    expect(label.textContent).not.toBe("DEGRADED");

    const root = screen.getByTestId("status-page");
    expect(root.className).toContain("status-page--degraded");
    expect(root.className).not.toContain("status-page--ok");
  });

  describe("responsive layout (US3)", () => {
    // NOTE: jsdom does not perform real CSS layout, so genuine "no horizontal
    // scrolling at 375px" verification is not possible in a unit test — that is
    // covered by quickstart.md's manual verification step. This test instead
    // checks the structural precondition: the page never renders a fixed pixel
    // width wider than a phone viewport, which is what would cause overflow.
    it("does not apply any inline width wider than a typical phone viewport", async () => {
      mockFetchOnce(okResponse);
      render(<StatusPage />);
      await screen.findByTestId("status-label");

      const root = screen.getByTestId("status-page");
      const inlineWidth = root.style.width;
      if (inlineWidth) {
        expect(inlineWidth.endsWith("%")).toBe(true);
      }
    });
  });

  it("renders the status within 5 seconds of mount (SC-001 proxy)", async () => {
    // jsdom has no real network latency, so this checks the component doesn't
    // itself introduce any artificial delay beyond a generous bound — true
    // end-to-end timing is verified manually via quickstart.md.
    mockFetchOnce(okResponse, 50);
    render(<StatusPage />);

    await waitFor(() => expect(screen.getByTestId("status-label")).toHaveTextContent("OK"), {
      timeout: 5000,
    });
  });

  it("shows an error state without throwing when the status endpoint is unreachable", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.reject(new Error("network error"))),
    );
    render(<StatusPage />);

    expect(await screen.findByText(/unable to reach/i)).toBeInTheDocument();
  });
});
