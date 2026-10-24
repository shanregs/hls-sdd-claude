// Shared Jest setup (spec 018). Native modules are replaced by in-memory fakes so tests run
// without a device.
import { configure } from "@testing-library/react-native";
import "react-native-gesture-handler/jestSetup";

// Screens load data through the in-memory server; under a busy parallel run the default 1 s wait is too tight.
configure({ asyncUtilTimeout: 5000 });

jest.mock("@react-native-async-storage/async-storage", () =>
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  require("@react-native-async-storage/async-storage/jest/async-storage-mock"),
);

const mockSecureStore = new Map<string, string>();
jest.mock("expo-secure-store", () => ({
  setItemAsync: jest.fn(async (k: string, v: string) => {
    mockSecureStore.set(k, v);
  }),
  getItemAsync: jest.fn(async (k: string) => mockSecureStore.get(k) ?? null),
  deleteItemAsync: jest.fn(async (k: string) => {
    mockSecureStore.delete(k);
  }),
  __reset: () => mockSecureStore.clear(),
  __dump: () => Object.fromEntries(mockSecureStore),
}));

jest.mock("expo-application", () => ({
  nativeApplicationVersion: "1.0.0",
  nativeBuildVersion: "1",
}));

jest.mock("expo-device", () => ({ modelName: "Test Phone", osVersion: "14" }), {
  virtual: true,
});

// Controllable fake of expo-location. The state lives inside the factory (not in a top-level const)
// because imports below load the module before top-level consts are initialised; tests reach it
// through `jest.requireMock("expo-location").__state`.
jest.mock("expo-location", () => {
  const state = {
    permission: "granted" as "granted" | "denied" | "undetermined",
    canAskAgain: true,
    servicesEnabled: true,
    position: { latitude: 12.971599, longitude: 77.594566, accuracy: 18.5 },
    delayMs: 0,
    neverResolves: false,
    failWith: null as Error | null,
    onRequestPermission: "granted" as "granted" | "denied",
  };
  const permission = () => ({
    status: state.permission,
    granted: state.permission === "granted",
    canAskAgain: state.canAskAgain,
  });
  return {
    Accuracy: { Balanced: 3 },
    __state: state,
    getForegroundPermissionsAsync: jest.fn(async () => permission()),
    requestForegroundPermissionsAsync: jest.fn(async () => {
      if (state.permission === "undetermined") state.permission = state.onRequestPermission;
      return permission();
    }),
    hasServicesEnabledAsync: jest.fn(async () => state.servicesEnabled),
    getCurrentPositionAsync: jest.fn(async () => {
      if (state.neverResolves) return new Promise(() => undefined);
      if (state.delayMs > 0) await new Promise((resolve) => setTimeout(resolve, state.delayMs));
      if (state.failWith) throw state.failWith;
      return { coords: { ...state.position }, timestamp: Date.now() };
    }),
  };
});

jest.mock("expo-screen-capture", () => ({
  preventScreenCaptureAsync: jest.fn(async () => undefined),
  allowScreenCaptureAsync: jest.fn(async () => undefined),
}));

jest.mock("jail-monkey", () => ({
  __esModule: true,
  default: { isJailBroken: jest.fn(() => false), canMockLocation: jest.fn(() => false) },
}));

jest.mock("react-native-safe-area-context", () =>
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  require("react-native-safe-area-context/jest/mock").default,
);

// Every test starts with an empty secure store and no in-memory token.
import AsyncStorage from "@react-native-async-storage/async-storage";
import * as SecureStore from "expo-secure-store";
import { resetLocationGate } from "./src/location/LocationGate";
import { setAccessToken } from "./src/security/memoryToken";

// Controllable fake of expo-image-picker (spec 033): tests set what the camera and the gallery return, and whether the
// camera permission is granted, through `jest.requireMock("expo-image-picker").__state`.
jest.mock("expo-image-picker", () => {
  const state = {
    cameraPermission: "granted" as "granted" | "denied",
    camera: {
      canceled: false,
      assets: [{ uri: "file:///cache/camera.jpg", fileName: "camera.jpg", mimeType: "image/jpeg", fileSize: 348_160 }],
    } as { canceled: boolean; assets?: { uri: string; fileName?: string | null; mimeType?: string | null; fileSize?: number | null }[] },
    library: {
      canceled: false,
      assets: [{ uri: "file:///cache/gallery.png", fileName: "gallery.png", mimeType: "image/png", fileSize: 1_258_291 }],
    } as { canceled: boolean; assets?: { uri: string; fileName?: string | null; mimeType?: string | null; fileSize?: number | null }[] },
  };
  return {
    __state: state,
    requestCameraPermissionsAsync: jest.fn(async () => ({
      status: state.cameraPermission,
      granted: state.cameraPermission === "granted",
      canAskAgain: true,
    })),
    launchCameraAsync: jest.fn(async () => state.camera),
    launchImageLibraryAsync: jest.fn(async () => state.library),
  };
});

// In-memory fakes of the native modules spec 036 uses (offline capture and bill photos). Tests reach their state through
// `jest.requireMock(name).__state`; every test starts with the defaults (see the beforeEach at the end).

// expo-file-system (the legacy string API): a map of file uri to base64 content. `full` makes every write fail (a full
// phone); `sizes` lets a test declare the size a file reports without holding that many bytes.
jest.mock("expo-file-system/legacy", () => {
  const state = {
    files: new Map<string, string>(),
    dirs: new Set<string>(),
    sizes: new Map<string, number>(),
    full: false,
  };
  const sizeOf = (uri: string): number => {
    const override = state.sizes.get(uri);
    if (override !== undefined) return override;
    const content = state.files.get(uri) ?? "";
    return Math.floor((content.replace(/=+$/, "").length * 3) / 4);
  };
  const trim = (uri: string) => uri.replace(/\/$/, "");
  const children = (dir: string): string[] => {
    const prefix = `${trim(dir)}/`;
    const names = new Set<string>();
    for (const key of [...state.files.keys(), ...state.dirs]) {
      if (key.startsWith(prefix) && key.length > prefix.length) names.add(key.slice(prefix.length).split("/")[0]);
    }
    return [...names];
  };
  return {
    __state: state,
    documentDirectory: "file:///doc/",
    cacheDirectory: "file:///cache/",
    EncodingType: { UTF8: "utf8", Base64: "base64" },
    getInfoAsync: jest.fn(async (uri: string) => {
      if (state.files.has(uri) || state.sizes.has(uri)) return { exists: true, isDirectory: false, uri, size: sizeOf(uri) };
      if (state.dirs.has(trim(uri)) || children(uri).length > 0) return { exists: true, isDirectory: true, uri };
      return { exists: false, isDirectory: false, uri };
    }),
    readAsStringAsync: jest.fn(async (uri: string) => {
      const content = state.files.get(uri);
      if (content === undefined) {
        // A picked file nobody seeded: a few stand-in bytes.
        if (uri.startsWith("file:///cache/")) return "/9j/4AAQSkZJRg==";
        throw new Error(`No such file: ${uri}`);
      }
      return content;
    }),
    writeAsStringAsync: jest.fn(async (uri: string, contents: string) => {
      if (state.full) throw new Error("No space left on device");
      state.files.set(uri, contents);
    }),
    deleteAsync: jest.fn(async (uri: string) => {
      const base = trim(uri);
      for (const key of [...state.files.keys()]) {
        if (key === uri || key.startsWith(`${base}/`)) {
          state.files.delete(key);
          state.sizes.delete(key);
        }
      }
      for (const dir of [...state.dirs]) if (dir === base || dir.startsWith(`${base}/`)) state.dirs.delete(dir);
    }),
    makeDirectoryAsync: jest.fn(async (uri: string) => {
      if (state.full) throw new Error("No space left on device");
      state.dirs.add(trim(uri));
    }),
    readDirectoryAsync: jest.fn(async (uri: string) => children(uri)),
    copyAsync: jest.fn(async ({ from, to }: { from: string; to: string }) => {
      if (state.full) throw new Error("No space left on device");
      state.files.set(to, state.files.get(from) ?? "/9j/4AAQSkZJRg==");
    }),
  };
});

// expo-crypto: predictable ids, and bytes that differ from call to call.
jest.mock("expo-crypto", () => {
  const state = { uuid: 0, bytes: 0 };
  return {
    __state: state,
    randomUUID: jest.fn(() => {
      state.uuid += 1;
      return `00000000-0000-4000-8000-${String(state.uuid).padStart(12, "0")}`;
    }),
    getRandomBytes: jest.fn((count: number) => {
      const out = new Uint8Array(count);
      for (let i = 0; i < count; i += 1) {
        state.bytes += 1;
        out[i] = (state.bytes * 73 + i * 31 + 7) % 256;
      }
      return out;
    }),
  };
});

// expo-image-manipulator: records what was asked and returns a small jpg. The source size of a picked uri is in
// `sources` (default 4000 x 3000); the size the result reports is the next of `outputBytes` (default 700 000).
jest.mock("expo-image-manipulator", () => {
  type Resize = { resize?: { width?: number; height?: number } };
  const state = {
    calls: [] as { uri: string; actions: Resize[]; options: { compress?: number; format?: string } }[],
    sources: new Map<string, { width: number; height: number }>(),
    outputBytes: [] as number[],
    fail: false,
    counter: 0,
  };
  return {
    __state: state,
    SaveFormat: { JPEG: "jpeg", PNG: "png", WEBP: "webp" },
    manipulateAsync: jest.fn(async (uri: string, actions: Resize[], options: { compress?: number; format?: string }) => {
      state.calls.push({ uri, actions, options });
      if (state.fail) throw new Error("Cannot decode image");
      const source = state.sources.get(uri) ?? { width: 4000, height: 3000 };
      let { width, height } = source;
      const resize = actions.find((a) => a.resize)?.resize;
      if (resize?.width) {
        height = Math.round((height * resize.width) / width);
        width = resize.width;
      } else if (resize?.height) {
        width = Math.round((width * resize.height) / height);
        height = resize.height;
      }
      state.counter += 1;
      const out = `file:///cache/manipulated-${state.counter}.jpg`;
      // eslint-disable-next-line @typescript-eslint/no-require-imports
      const fs = require("expo-file-system/legacy");
      fs.__state.files.set(out, "/9j/4AAQSkZJRg==");
      fs.__state.sizes.set(out, state.outputBytes.length > 0 ? (state.outputBytes.shift() as number) : 700_000);
      return { uri: out, width, height };
    }),
  };
});

jest.mock("expo-document-picker", () => {
  const state = {
    result: {
      canceled: false,
      assets: [{ uri: "file:///cache/bill.pdf", name: "bill.pdf", mimeType: "application/pdf", size: 120_000 }],
    } as { canceled: boolean; assets?: { uri: string; name: string; mimeType?: string; size?: number }[] },
  };
  return { __state: state, getDocumentAsync: jest.fn(async () => state.result) };
});

// @react-native-community/netinfo: `__emit` fires a connectivity change to every listener.
jest.mock("@react-native-community/netinfo", () => {
  type Info = { isConnected: boolean | null; isInternetReachable: boolean | null };
  const listeners = new Set<(s: Info) => void>();
  const state: Info = { isConnected: true, isInternetReachable: true };
  const addEventListener = jest.fn((listener: (s: Info) => void) => {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  });
  const fetch = jest.fn(async () => ({ ...state }));
  return {
    __esModule: true,
    default: { addEventListener, fetch },
    addEventListener,
    fetch,
    __state: state,
    __emit: (next: Partial<Info>) => {
      Object.assign(state, next);
      listeners.forEach((listener) => listener({ ...state }));
    },
    __listenerCount: () => listeners.size,
  };
});

// React Native's FormData takes a plain {uri, name, type} object as a file part and can list its parts; Node's does
// neither. This stand-in has the part of that behaviour the upload tests need (spec 033).
class NativeFormData {
  private parts: { fieldName: string; [key: string]: unknown }[] = [];
  append(name: string, value: unknown): void {
    this.parts.push({ ...(typeof value === "object" && value !== null ? value : { string: String(value) }), fieldName: name });
  }
  getParts(): { fieldName: string; [key: string]: unknown }[] {
    return this.parts;
  }
}
globalThis.FormData = NativeFormData as unknown as typeof FormData;

beforeEach(async () => {
  await AsyncStorage.clear();
  Object.assign(jest.requireMock("expo-location").__state, {
    permission: "granted",
    canAskAgain: true,
    servicesEnabled: true,
    position: { latitude: 12.971599, longitude: 77.594566, accuracy: 18.5 },
    delayMs: 0,
    neverResolves: false,
    failWith: null,
    onRequestPermission: "granted",
  });
  Object.assign(jest.requireMock("expo-image-picker").__state, {
    cameraPermission: "granted",
    camera: {
      canceled: false,
      assets: [{ uri: "file:///cache/camera.jpg", fileName: "camera.jpg", mimeType: "image/jpeg", fileSize: 348_160 }],
    },
    library: {
      canceled: false,
      assets: [{ uri: "file:///cache/gallery.png", fileName: "gallery.png", mimeType: "image/png", fileSize: 1_258_291 }],
    },
  });
  resetLocationGate();
  (SecureStore as unknown as { __reset: () => void }).__reset();
  setAccessToken(null);
});
