/** One Place per line as {@code name,pinCode}; blank lines are ignored. */
export function parseRows(text: string): { name: string; pinCode: string }[] {
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .map((line) => {
      const cut = line.lastIndexOf(",");
      if (cut < 0) return { name: line, pinCode: "" };
      return {
        name: line.slice(0, cut).trim(),
        pinCode: line.slice(cut + 1).trim(),
      };
    });
}
