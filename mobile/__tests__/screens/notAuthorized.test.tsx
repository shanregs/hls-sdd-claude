import { act, fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import * as SecureStore from "expo-secure-store";
import { AppState, type AppStateStatus } from "react-native";
import App from "../../App";
import { ACCESS_MODEL_REFRESH_AFTER_BACKGROUND_MS } from "../../src/config/constants";
import { DIRECTOR_MODEL, MANAGER_MODEL, TEACHER_MODEL } from "../support/fixtures";
import { authResult, newServer, TARA, type FakeServer } from "../support/fakeServer";
import { chooseMenuItem } from "../support/navigation";

let server: FakeServer;
let handler: ((state: AppStateStatus) => void) | undefined;

beforeEach(async () => {
  handler = undefined;
  jest.spyOn(AppState, "addEventListener").mockImplementation(((
    _type: string,
    listener: (s: AppStateStatus) => void,
  ) => {
    handler = listener;
    return { remove: () => undefined };
  }) as never);
  server = newServer();
  server.on("POST /api/v1/auth/renew", { status: 200, body: authResult() });
  await SecureStore.setItemAsync("hls.renewalCredential", "stored");
});

afterEach(() => jest.restoreAllMocks());

describe("Not authorized (spec FR-013)", () => {
  it("replaces a screen the user no longer has, with a way back home", async () => {
    await render(<App />);
    await screen.findByText("Welcome, Tara");
    await chooseMenuItem("My Profile");
    await screen.findByText("Appearance");

    // Permissions changed on the web while the user was on Profile; the model is refreshed.
    server.on("GET /api/v1/me/access-model", {
      status: 200,
      body: { ...TEACHER_MODEL, navigation: TEACHER_MODEL.navigation.filter((s) => s.section === "Dashboard") },
    });
    const realNow = Date.now();
    jest.spyOn(Date, "now").mockReturnValue(realNow);
    await act(async () => handler?.("background"));
    jest.spyOn(Date, "now").mockReturnValue(realNow + ACCESS_MODEL_REFRESH_AFTER_BACKGROUND_MS);
    await act(async () => handler?.("active"));

    await screen.findByText("You do not have access to that page.");
    expect(screen.queryByText("Appearance")).toBeNull();

    await fireEvent.press(screen.getByLabelText("Go to home"));
    await screen.findByText("Welcome, Tara");
    expect(screen.queryByText("You do not have access to that page.")).toBeNull();
  });
});

describe("Home (spec FR-015)", () => {
  async function homeFor(model: typeof TEACHER_MODEL) {
    server.on("GET /api/v1/me/access-model", { status: 200, body: model });
    await render(<App />);
    await screen.findByText(`Welcome, ${TARA.displayName}`);
  }

  it("shows the name, the roles and one skeleton card per business section of the menu", async () => {
    await homeFor(DIRECTOR_MODEL);

    expect(screen.getByLabelText("Roles: DIRECTOR")).toBeTruthy();
    expect(screen.getByText("Director")).toBeTruthy();
    // MASTER DATA (Holiday Calendar) and OPERATIONS (Leave Management) now have screens; SYSTEM has none.
    expect(screen.getByLabelText("SYSTEM, coming to the app soon")).toBeTruthy();
    // The home and the account area are not previewed as business sections.
    expect(screen.queryByLabelText(/^Dashboard, coming/)).toBeNull();
    expect(screen.queryByLabelText(/^ACCOUNT, coming/)).toBeNull();
  });

  it("renders the same way for a Teacher and a Manager: only the sections differ", async () => {
    await homeFor(MANAGER_MODEL);
    expect(screen.queryByLabelText(/coming to the app soon/)).toBeNull();
  });

  it("does not preview a section that already has a screen in the app (T041)", async () => {
    await homeFor(DIRECTOR_MODEL);

    // MASTER DATA has the Holiday Calendar and OPERATIONS has Leave Management (spec 020); SYSTEM has none.
    expect(screen.queryByLabelText("MASTER DATA, coming to the app soon")).toBeNull();
    expect(screen.queryByLabelText("OPERATIONS, coming to the app soon")).toBeNull();
    expect(screen.getByLabelText("SYSTEM, coming to the app soon")).toBeTruthy();
  });

  it("shows no coming-soon card for a Teacher, whose sections all have screens (T041)", async () => {
    await homeFor(TEACHER_MODEL);

    expect(screen.queryByLabelText("MY ATTENDANCE, coming to the app soon")).toBeNull();
    expect(screen.queryByLabelText("MASTER DATA, coming to the app soon")).toBeNull();
    expect(screen.queryByLabelText(/coming to the app soon/)).toBeNull();
  });

  it("shows no cards when the menu has no business sections", async () => {
    await homeFor({ ...MANAGER_MODEL, navigation: MANAGER_MODEL.navigation.filter((s) => ["Dashboard", "ACCOUNT"].includes(s.section)) });

    expect(screen.queryByLabelText(/coming to the app soon/)).toBeNull();
    await waitFor(() => expect(screen.getByLabelText("Roles: MANAGER")).toBeTruthy());
  });
});
