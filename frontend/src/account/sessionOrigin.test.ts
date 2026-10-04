import { describe, expect, it } from "vitest";
import { describeOrigin, formatDateTime } from "./sessionOrigin";

const web = (deviceDescription: string | null) => ({
  clientType: "WEB" as const,
  appVersion: null,
  deviceDescription,
});

describe("describeOrigin", () => {
  it("names the Android app and its version", () => {
    expect(
      describeOrigin({
        clientType: "ANDROID",
        appVersion: "1.2.0",
        deviceDescription: "okhttp/4.12",
      }),
    ).toBe("Android app 1.2.0");
    expect(
      describeOrigin({
        clientType: "ANDROID",
        appVersion: null,
        deviceDescription: null,
      }),
    ).toBe("Android app");
  });

  it("reads the browser and operating system from the user agent", () => {
    const chromeWindows =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36";
    const edge = `${chromeWindows} Edg/154.0.0.0`;
    const firefoxLinux =
      "Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0";
    const safariIphone =
      "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
    expect(describeOrigin(web(chromeWindows))).toBe("Chrome on Windows");
    expect(describeOrigin(web(edge))).toBe("Edge on Windows");
    expect(describeOrigin(web(firefoxLinux))).toBe("Firefox on Linux");
    expect(describeOrigin(web(safariIphone))).toBe("Safari on iOS");
  });

  it("labels automated clients and falls back to the raw text", () => {
    expect(
      describeOrigin(
        web(
          "Mozilla/5.0 (Windows NT 10.0) HeadlessChrome/133.0.0.0 Safari/537.36",
        ),
      ),
    ).toBe("Chrome (headless) on Windows");
    expect(describeOrigin(web("curl/8.10.1"))).toBe("curl");
    expect(describeOrigin(web("SomeCustomClient 1.0"))).toBe(
      "SomeCustomClient 1.0",
    );
    expect(describeOrigin(web(null))).toBe("Unknown client");
  });
});

describe("formatDateTime", () => {
  it("shows DD/MM/YYYY and a 24-hour time", () => {
    const iso = new Date(2026, 9, 4, 14, 5, 9).toISOString();
    expect(formatDateTime(iso)).toBe("04/10/2026 14:05:09");
  });
});
