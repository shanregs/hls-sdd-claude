import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ThemeSettingsPage } from "./ThemeSettingsPage";
import {
  BUILTIN_CATALOGUE,
  CLASSIC_PRESET,
  type ThemeCatalogue,
} from "../theme/tokens";
import { DEFAULT_PREFERENCE, type ThemePreference } from "../theme/themeApi";

const savePreference = vi.fn();
const resetPreference = vi.fn();
const retrySave = vi.fn();
let preference: ThemePreference = DEFAULT_PREFERENCE;
let mode: "light" | "dark" = "light";
let unsaved = false;

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

vi.mock("../theme/ThemeModeProvider", () => ({
  useThemeMode: () => ({
    mode,
    toggleMode: vi.fn(),
    preference,
    catalogue: CATALOGUE,
    savePreference,
    resetPreference,
    unsaved,
    retrySave,
  }),
}));

describe("ThemeSettingsPage (spec 039 US1 to US3)", () => {
  beforeEach(() => {
    savePreference.mockReset().mockResolvedValue({ ok: true });
    resetPreference.mockReset().mockResolvedValue(true);
    retrySave.mockReset();
    preference = DEFAULT_PREFERENCE;
    mode = "light";
    unsaved = false;
  });

  it("offers every preset and marks the one in use", () => {
    render(<ThemeSettingsPage />);

    const group = screen.getByRole("radiogroup", { name: "Theme" });
    const classic = within(group).getByRole("radio", { name: /Classic/ });
    const teal = within(group).getByRole("radio", { name: /Teal and Coral/ });
    expect(classic).toHaveAttribute("aria-checked", "true");
    expect(teal).toHaveAttribute("aria-checked", "false");
  });

  it("saves the chosen preset", async () => {
    const user = userEvent.setup();
    render(<ThemeSettingsPage />);

    await user.click(screen.getByRole("radio", { name: /Teal and Coral/ }));

    expect(savePreference).toHaveBeenCalledWith({ preset: "teal-coral" });
  });

  it("saves the chosen mode", async () => {
    const user = userEvent.setup();
    render(<ThemeSettingsPage />);

    await user.click(screen.getByRole("button", { name: "Dark" }));

    expect(savePreference).toHaveBeenCalledWith({ mode: "dark" });
  });

  it("accepts a readable accent and saves it", async () => {
    const user = userEvent.setup();
    render(<ThemeSettingsPage />);

    await user.type(screen.getByLabelText("Accent colour"), "#1E5AA8");
    expect(
      await screen.findByText(/Easy to read in light mode/),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Use this colour" }));

    expect(savePreference).toHaveBeenCalledWith({ customAccent: "#1E5AA8" });
  });

  it("refuses an unreadable accent as entered and offers an adjusted colour", async () => {
    const user = userEvent.setup();
    render(<ThemeSettingsPage />);

    await user.type(screen.getByLabelText("Accent colour"), "#FFEB3B");

    expect(
      await screen.findByText(/#FFEB3B is hard to read/),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Use this colour" }),
    ).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "Use #786C00" }));
    expect(screen.getByLabelText("Accent colour")).toHaveValue("#786C00");
    expect(
      screen.getByRole("button", { name: "Use this colour" }),
    ).toBeEnabled();
  });

  it("flags text that is not a colour", async () => {
    const user = userEvent.setup();
    render(<ThemeSettingsPage />);

    await user.type(screen.getByLabelText("Accent colour"), "blue");

    expect(
      screen.getByText("Enter a colour like #1E5AA8."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Use this colour" }),
    ).toBeDisabled();
  });

  it("shows the server's refusal when it disagrees", async () => {
    savePreference.mockResolvedValue({
      ok: false,
      code: "ACCENT_UNREADABLE",
      reason: "no",
      suggested: "#123456",
    });
    const user = userEvent.setup();
    render(<ThemeSettingsPage />);

    await user.type(screen.getByLabelText("Accent colour"), "#1E5AA8");
    await user.click(screen.getByRole("button", { name: "Use this colour" }));

    expect(
      await screen.findByText("That colour is hard to read. Try #123456."),
    ).toBeInTheDocument();
  });

  it("clears a saved accent", async () => {
    preference = { ...DEFAULT_PREFERENCE, customAccent: "#1E5AA8" };
    const user = userEvent.setup();
    render(<ThemeSettingsPage />);

    await user.click(screen.getByRole("button", { name: "Clear" }));

    expect(savePreference).toHaveBeenCalledWith({ customAccent: null });
  });

  it("previews the theme with its contrast ratios", () => {
    render(<ThemeSettingsPage />);

    const preview = screen.getByRole("group", { name: "Theme preview" });
    expect(within(preview).getByText("Primary button")).toBeInTheDocument();
    expect(within(preview).getByText("Approved")).toBeInTheDocument();
    expect(within(preview).getByText("Pending")).toBeInTheDocument();
    expect(within(preview).getByText("Rejected")).toBeInTheDocument();
    const ratios = screen.getByLabelText("Contrast ratios");
    expect(within(ratios).getAllByText(/passes/)).toHaveLength(3);
  });

  it("resets only after a confirmation", async () => {
    const user = userEvent.setup();
    render(<ThemeSettingsPage />);

    await user.click(screen.getByRole("button", { name: "Reset to default" }));
    expect(resetPreference).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Cancel" }));
    expect(resetPreference).not.toHaveBeenCalled();
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );

    await user.click(screen.getByRole("button", { name: "Reset to default" }));
    await user.click(screen.getByRole("button", { name: "Reset" }));
    await waitFor(() => expect(resetPreference).toHaveBeenCalledTimes(1));
  });

  it("tells the user when a change could not be saved and offers a retry", async () => {
    unsaved = true;
    const user = userEvent.setup();
    render(<ThemeSettingsPage />);

    expect(screen.getByRole("alert")).toHaveTextContent(
      "could not be saved yet",
    );
    await user.click(screen.getByRole("button", { name: "Retry" }));
    expect(retrySave).toHaveBeenCalled();
  });

  it("saves the darker sidebar option", async () => {
    const user = userEvent.setup();
    render(<ThemeSettingsPage />);

    await user.click(screen.getByRole("checkbox", { name: /Darker sidebar/ }));

    expect(savePreference).toHaveBeenCalledWith({ sidebarDark: true });
  });
});
