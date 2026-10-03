import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ThemeModeProvider, useThemeMode } from "./ThemeModeProvider";

const STORAGE_KEY = "hls-theme-mode";

function ModeProbe() {
  const { mode, toggleMode } = useThemeMode();
  return (
    <div>
      <span data-testid="mode">{mode}</span>
      <button onClick={toggleMode}>Toggle theme</button>
    </div>
  );
}

/** A plain in-memory Storage stand-in: this environment's `window.localStorage` is backed by
 * Node's own experimental global storage (not jsdom's), which behaves inconsistently across Node
 * versions — a local, fully-controlled stub avoids depending on that. */
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

describe("ThemeModeProvider (User Story 4, FR-014)", () => {
  let originalLocalStorage: Storage;

  beforeEach(() => {
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
    vi.restoreAllMocks();
  });

  it("toggling updates the mode immediately", async () => {
    const user = userEvent.setup();
    render(
      <ThemeModeProvider>
        <ModeProbe />
      </ThemeModeProvider>,
    );

    expect(screen.getByTestId("mode")).toHaveTextContent("light");
    await user.click(screen.getByRole("button", { name: /toggle theme/i }));
    expect(screen.getByTestId("mode")).toHaveTextContent("dark");
  });

  it("reads a previously persisted preference before first paint", () => {
    window.localStorage.setItem(STORAGE_KEY, "dark");

    render(
      <ThemeModeProvider>
        <ModeProbe />
      </ThemeModeProvider>,
    );

    expect(screen.getByTestId("mode")).toHaveTextContent("dark");
  });

  it("falls back to a default theme without throwing when storage is unavailable", () => {
    vi.spyOn(window.localStorage, "getItem").mockImplementation(() => {
      throw new Error("storage unavailable");
    });

    expect(() =>
      render(
        <ThemeModeProvider>
          <ModeProbe />
        </ThemeModeProvider>,
      ),
    ).not.toThrow();
    expect(screen.getByTestId("mode")).toHaveTextContent("light");
  });
});
