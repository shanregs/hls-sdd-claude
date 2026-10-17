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
