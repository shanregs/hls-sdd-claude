// Shared Jest setup (spec 018). Native modules are replaced by in-memory fakes so tests run
// without a device.
import "react-native-gesture-handler/jestSetup";

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

// Controllable fake of expo-location: tests change `mockLocationState` through the module's
// `__state` property.
const mockLocationState = {
  permission: "granted" as "granted" | "denied" | "undetermined",
  canAskAgain: true,
  servicesEnabled: true,
  position: { latitude: 12.971599, longitude: 77.594566, accuracy: 18.5 },
  delayMs: 0,
  neverResolves: false,
  failWith: null as Error | null,
  onRequestPermission: "granted" as "granted" | "denied",
};
jest.mock("expo-location", () => ({
  Accuracy: { Balanced: 3 },
  __state: mockLocationState,
  getForegroundPermissionsAsync: jest.fn(async () => ({
    status: mockLocationState.permission,
    granted: mockLocationState.permission === "granted",
    canAskAgain: mockLocationState.canAskAgain,
  })),
  requestForegroundPermissionsAsync: jest.fn(async () => {
    if (mockLocationState.permission === "undetermined") {
      mockLocationState.permission = mockLocationState.onRequestPermission;
    }
    return {
      status: mockLocationState.permission,
      granted: mockLocationState.permission === "granted",
      canAskAgain: mockLocationState.canAskAgain,
    };
  }),
  hasServicesEnabledAsync: jest.fn(async () => mockLocationState.servicesEnabled),
  getCurrentPositionAsync: jest.fn(async () => {
    if (mockLocationState.neverResolves) return new Promise(() => undefined);
    if (mockLocationState.delayMs > 0) {
      await new Promise((resolve) => setTimeout(resolve, mockLocationState.delayMs));
    }
    if (mockLocationState.failWith) throw mockLocationState.failWith;
    return { coords: { ...mockLocationState.position }, timestamp: Date.now() };
  }),
}));

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

beforeEach(async () => {
  await AsyncStorage.clear();
  Object.assign(mockLocationState, {
    permission: "granted",
    canAskAgain: true,
    servicesEnabled: true,
    position: { latitude: 12.971599, longitude: 77.594566, accuracy: 18.5 },
    delayMs: 0,
    neverResolves: false,
    failWith: null,
    onRequestPermission: "granted",
  });
  resetLocationGate();
  (SecureStore as unknown as { __reset: () => void }).__reset();
  setAccessToken(null);
});
