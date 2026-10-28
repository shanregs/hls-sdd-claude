/**
 * WCAG contrast maths for spec 039 (research.md sections 3 and 4). Pure and deterministic: the same
 * algorithm as `ContrastRules.java` and the web `contrast.ts`, all tested against
 * `specs/039-user-themes/contracts/contrast-vectors.json`.
 */

/** AA for normal text. */
export const MIN_TEXT_RATIO = 4.5;
export const WHITE = "#FFFFFF";
export const DARK_HEADER_TEXT = "#0B1B33";

const HEX = /^#[0-9A-Fa-f]{6}$/;

export function isHex(value: string | null | undefined): value is string {
  return typeof value === "string" && HEX.test(value);
}

export function normaliseHex(hex: string): string {
  return hex.toUpperCase();
}

function channel(value: number): number {
  const c = value / 255;
  return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
}

export function luminance(hex: string): number {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

export function ratio(a: string, b: string): number {
  const la = luminance(a);
  const lb = luminance(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

export function round2(value: number): number {
  return Math.round(value * 100) / 100;
}

/** White or dark header text, whichever reads better on the colour. */
export function headerTextOn(primary: string): string {
  return ratio(WHITE, primary) >= ratio(DARK_HEADER_TEXT, primary)
    ? WHITE
    : DARK_HEADER_TEXT;
}

export interface AccentRatios {
  accentOnSurface: number;
  accentOnBackground: number;
  headerTextOnAccent: number;
}

export function accentRatios(
  accent: string,
  surface: string,
  background: string,
): AccentRatios {
  return {
    accentOnSurface: round2(ratio(accent, surface)),
    accentOnBackground: round2(ratio(accent, background)),
    headerTextOnAccent: round2(ratio(headerTextOn(accent), accent)),
  };
}

/** True when the accent reads as text on the surface and the background and carries header text. */
export function passes(
  accent: string,
  surface: string,
  background: string,
): boolean {
  return (
    ratio(accent, surface) >= MIN_TEXT_RATIO &&
    ratio(accent, background) >= MIN_TEXT_RATIO &&
    ratio(headerTextOn(accent), accent) >= MIN_TEXT_RATIO
  );
}

function toHsl(hex: string): [number, number, number] {
  const r = parseInt(hex.slice(1, 3), 16) / 255;
  const g = parseInt(hex.slice(3, 5), 16) / 255;
  const b = parseInt(hex.slice(5, 7), 16) / 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const l = (max + min) / 2;
  const d = max - min;
  if (d === 0) return [0, 0, l];
  const s = d / (1 - Math.abs(2 * l - 1));
  let h: number;
  if (max === r) {
    h = ((g - b) / d) % 6;
    if (h < 0) h += 6;
  } else if (max === g) {
    h = (b - r) / d + 2;
  } else {
    h = (r - g) / d + 4;
  }
  return [h * 60, s, l];
}

function toHex(h: number, s: number, l: number): string {
  const c = (1 - Math.abs(2 * l - 1)) * s;
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
  const m = l - c / 2;
  let rgb: [number, number, number];
  switch (Math.floor(h / 60) % 6) {
    case 0:
      rgb = [c, x, 0];
      break;
    case 1:
      rgb = [x, c, 0];
      break;
    case 2:
      rgb = [0, c, x];
      break;
    case 3:
      rgb = [0, x, c];
      break;
    case 4:
      rgb = [x, 0, c];
      break;
    default:
      rgb = [c, 0, x];
  }
  const part = (v: number) =>
    Math.round((v + m) * 255)
      .toString(16)
      .toUpperCase()
      .padStart(2, "0");
  return `#${part(rgb[0])}${part(rgb[1])}${part(rgb[2])}`;
}

/**
 * The nearest passing colour: same hue and saturation, lightness moved 1 percent at a time (darker
 * for a light variant, lighter for a dark one). Null when none passes.
 */
export function adjustAccent(
  accent: string,
  darkVariant: boolean,
  surface: string,
  background: string,
): string | null {
  const [h, s, l0] = toHsl(accent);
  for (let step = 0; step <= 100; step++) {
    const l = darkVariant ? l0 + step * 0.01 : l0 - step * 0.01;
    if (l < 0 || l > 1) return null;
    const candidate = toHex(h, s, l);
    if (passes(candidate, surface, background)) return candidate;
  }
  return null;
}

/** The accent to use in one variant: as entered when it passes, otherwise the adjusted colour. */
export function accentForVariant(
  accent: string,
  darkVariant: boolean,
  surface: string,
  background: string,
): string {
  const hex = normaliseHex(accent);
  if (passes(hex, surface, background)) return hex;
  return adjustAccent(hex, darkVariant, surface, background) ?? hex;
}
