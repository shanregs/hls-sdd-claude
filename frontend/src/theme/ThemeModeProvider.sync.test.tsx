import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ThemeModeProvider, useThemeMode } from "./ThemeModeProvider";
import {
  BUILTIN_CATALOGUE,
  CLASSIC_PRESET,
  type ThemeCatalogue,
} from "./tokens";

const authFetch = vi.fn();
let signedIn = true;

vi.mock("../auth/useAuth", () => ({
  useOptionalAuth: () => ({
    authFetch,
    user: signedIn ? { id: "u1", displayName: "Tara" } : null,
  }),
}));

const TEAL = {
  id: "teal-coral",
  name: "Teal and Coral",
  light: { ...CLASSIC_PRESET.light, primary: "#00796B" },
  dark: { ...CLASSIC_PRESET.dark, primary: "#4DB6AC" },
};

const CATALOGUE: ThemeCatalogue = {
  ...BUILTIN_CATALOGUE,
  presets: [CLASSIC_PRESET, TEAL],
};

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function themeResponse(
  preference: Record<string, unknown> = {},
  isDefault = false,
) {
  return {
    preference: {
      preset: "classic",
      mode: "device",
      customAccent: null,
      sidebarDark: false,
      changedAt: null,
      ...preference,
    },
    isDefault,
    applied: true,
    catalogue: CATALOGUE,
  };
}

function Probe() {
  const {
    mode,
    preference,
    unsaved,
    savePreference,
    resetPreference,
    retrySave,
  } = useThemeMode();
  return (
    <div>
      <span data-testid="mode">{mode}</span>
      <span data-testid="preset">{preference.preset}</span>
      <span data-testid="accent">{preference.customAccent ?? "none"}</span>
      <span data-testid="unsaved">{String(unsaved)}</span>
      <button onClick={() => void savePreference({ preset: "teal-coral" })}>
        Pick teal
      </button>
      <button onClick={() => void savePreference({ mode: "dark" })}>
        Pick dark
      </button>
      <button onClick={() => void savePreference({ customAccent: "#FFEB3B" })}>
        Pick yellow
      </button>
      <button onClick={() => void resetPreference()}>Reset</button>
      <button onClick={() => void retrySave()}>Retry</button>
    </div>
  );
}

function createMemoryStorage(): Storage {
  const store = new Map<string, string>();
  return {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => {
      store.set(key, value);
    },
    removeItem: (key: string) => {
      store.delete(key);
    },
    clear: () => store.clear(),
    key: (index: number) => Array.from(store.keys())[index] ?? null,
    get length() {
      return store.size;
    },
  };
}

function writes() {
  return authFetch.mock.calls
    .filter(([, init]) => init?.method)
    .map(([url, init]) => ({
      url: String(url),
      method: String(init.method),
      body: init.body ? JSON.parse(init.body) : null,
    }));
}

describe("ThemeModeProvider and the saved theme (spec 039 US1, US4)", () => {
  let originalLocalStorage: Storage;

  beforeEach(() => {
    signedIn = true;
    authFetch.mockReset();
    originalLocalStorage = window.localStorage;
    Object.defineProperty(window, "localStorage", {
      value: createMemoryStorage(),
      configurable: true,
    });
  });

  afterEach(() => {
    Object.defineProperty(window, "localStorage", {
      value: originalLocalStorage,
      configurable: true,
    });
  });

  function renderProbe() {
    return render(
      <ThemeModeProvider>
        <Probe />
      </ThemeModeProvider>,
    );
  }

  it("applies the saved theme after sign-in", async () => {
    authFetch.mockResolvedValue(
      json(themeResponse({ preset: "teal-coral", mode: "dark" })),
    );
    renderProbe();

    await waitFor(() =>
      expect(screen.getByTestId("preset")).toHaveTextContent("teal-coral"),
    );
    expect(screen.getByTestId("mode")).toHaveTextContent("dark");
  });

  it("applies a change at once, caches it and saves it with the variant in use", async () => {
    authFetch.mockImplementation(async (_url: string, init?: RequestInit) =>
      init?.method === "PUT"
        ? json(themeResponse({ preset: "teal-coral" }))
        : json(themeResponse({}, true)),
    );
    const user = userEvent.setup();
    renderProbe();
    await waitFor(() => expect(authFetch).toHaveBeenCalled());

    await user.click(screen.getByRole("button", { name: "Pick teal" }));

    expect(screen.getByTestId("preset")).toHaveTextContent("teal-coral");
    await waitFor(() => expect(writes()).toHaveLength(1));
    expect(writes()[0]).toMatchObject({
      url: "/api/v1/me/theme",
      method: "PUT",
      body: { preset: "teal-coral", mode: "device", variant: "light" },
    });
    expect(window.localStorage.getItem("hls-theme-preference")).toContain(
      "teal-coral",
    );
  });

  it("uses the cached theme before the server answers (no flash)", () => {
    window.localStorage.setItem(
      "hls-theme-preference",
      JSON.stringify({
        preference: {
          preset: "teal-coral",
          mode: "dark",
          customAccent: null,
          sidebarDark: false,
          changedAt: null,
        },
        catalogue: CATALOGUE,
      }),
    );
    authFetch.mockReturnValue(new Promise(() => {}));
    renderProbe();

    expect(screen.getByTestId("preset")).toHaveTextContent("teal-coral");
    expect(screen.getByTestId("mode")).toHaveTextContent("dark");
  });

  it("uploads the old light or dark choice once for a user with no saved theme", async () => {
    window.localStorage.setItem("hls-theme-mode", "dark");
    authFetch.mockImplementation(async (_url: string, init?: RequestInit) =>
      init?.method === "PUT"
        ? json(themeResponse({ mode: "dark" }))
        : json(themeResponse({}, true)),
    );
    renderProbe();

    await waitFor(() => expect(writes()).toHaveLength(1));
    expect(writes()[0].body).toMatchObject({ mode: "dark" });
    await waitFor(() =>
      expect(window.localStorage.getItem("hls-theme-mode")).toBeNull(),
    );
    expect(screen.getByTestId("mode")).toHaveTextContent("dark");
  });

  it("reverts a colour the server finds unreadable and reports it", async () => {
    authFetch.mockImplementation(async (_url: string, init?: RequestInit) =>
      init?.method === "PUT"
        ? json(
            {
              code: "ACCENT_UNREADABLE",
              reason: "That colour is hard to read.",
              suggested: "#786C00",
            },
            422,
          )
        : json(themeResponse({}, true)),
    );
    const user = userEvent.setup();
    renderProbe();
    await waitFor(() => expect(authFetch).toHaveBeenCalled());

    await user.click(screen.getByRole("button", { name: "Pick yellow" }));

    await waitFor(() => expect(writes()).toHaveLength(1));
    await waitFor(() =>
      expect(screen.getByTestId("accent")).toHaveTextContent("none"),
    );
    expect(screen.getByTestId("unsaved")).toHaveTextContent("false");
  });

  it("keeps a change when the save fails, shows it as unsaved, and clears it on retry", async () => {
    let fail = true;
    authFetch.mockImplementation(async (_url: string, init?: RequestInit) => {
      if (init?.method === "PUT") {
        if (fail) throw new Error("offline");
        return json(themeResponse({ mode: "dark" }));
      }
      return json(themeResponse({}, true));
    });
    const user = userEvent.setup();
    renderProbe();
    await waitFor(() => expect(authFetch).toHaveBeenCalled());

    await user.click(screen.getByRole("button", { name: "Pick dark" }));
    await waitFor(() =>
      expect(screen.getByTestId("unsaved")).toHaveTextContent("true"),
    );
    expect(screen.getByTestId("mode")).toHaveTextContent("dark");

    fail = false;
    await user.click(screen.getByRole("button", { name: "Retry" }));
    await waitFor(() =>
      expect(screen.getByTestId("unsaved")).toHaveTextContent("false"),
    );
  });

  it("resets to Classic on the same-as-device mode and deletes the saved copy", async () => {
    authFetch.mockImplementation(async (_url: string, init?: RequestInit) =>
      init?.method === "DELETE"
        ? ({ ok: true, status: 204 } as Response)
        : json(themeResponse({ preset: "teal-coral", mode: "dark" })),
    );
    const user = userEvent.setup();
    renderProbe();
    await waitFor(() =>
      expect(screen.getByTestId("preset")).toHaveTextContent("teal-coral"),
    );

    await user.click(screen.getByRole("button", { name: "Reset" }));

    await waitFor(() =>
      expect(screen.getByTestId("preset")).toHaveTextContent("classic"),
    );
    expect(writes().at(-1)).toMatchObject({ method: "DELETE" });
  });
});
