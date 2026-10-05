import { render, screen } from "@testing-library/react-native";
import * as SecureStore from "expo-secure-store";
import App from "../../App";
import type { AccessModel } from "../../src/api/accessModelApi";
import { DIRECTOR_MODEL, MANAGER_MODEL, TEACHER_MODEL } from "../support/fixtures";
import { authResult, newServer } from "../support/fakeServer";
import { openMenu } from "../support/navigation";

const ENTRIES = ["Apply Leave", "My Leave History", "Leave Management"];

async function drawerFor(model: AccessModel) {
  const server = newServer();
  server.on("GET /api/v1/me/access-model", { status: 200, body: model });
  server.on("POST /api/v1/auth/renew", { status: 200, body: authResult() });
  await SecureStore.setItemAsync("hls.renewalCredential", "stored");
  await render(<App />);
  await screen.findByText("Welcome, Tara");
  await openMenu();
  await screen.findByText("ACCOUNT");
}

const shown = () => ENTRIES.filter((label) => screen.queryAllByLabelText(label).length > 0);

describe("leave menu entries come only from the server (spec 020 US5)", () => {
  it("a Teacher sees Apply Leave and My Leave History and no Leave Management", async () => {
    await drawerFor(TEACHER_MODEL);
    expect(shown()).toEqual(["Apply Leave", "My Leave History"]);
    expect(screen.queryByText("LEAVE")).toBeTruthy();
  });

  it("a Manager sees Leave Management and no LEAVE section", async () => {
    await drawerFor(MANAGER_MODEL);
    expect(shown()).toEqual(["Leave Management"]);
    expect(screen.queryByText("LEAVE")).toBeNull();
  });

  it("a Director sees Leave Management and no LEAVE section", async () => {
    await drawerFor(DIRECTOR_MODEL);
    expect(shown()).toEqual(["Leave Management"]);
    expect(screen.queryByText("LEAVE")).toBeNull();
  });

  it("a user with Teacher and Manager roles gets the union with no duplicates", async () => {
    const both: AccessModel = {
      roles: ["TEACHER", "MANAGER"],
      navigation: [
        ...TEACHER_MODEL.navigation.filter((s) => s.section !== "ACCOUNT"),
        ...MANAGER_MODEL.navigation.filter((s) => s.section !== "Dashboard"),
      ],
      dataScope: {},
    };
    await drawerFor(both);

    expect(shown()).toEqual(ENTRIES);
    for (const label of ENTRIES) expect(screen.getAllByLabelText(label)).toHaveLength(1);
  });

  it("does not show an entry the server did not offer, even though the screen exists", async () => {
    await drawerFor({
      ...TEACHER_MODEL,
      navigation: TEACHER_MODEL.navigation.map((section) =>
        section.section === "LEAVE"
          ? { ...section, items: section.items.filter((i) => i.label !== "My Leave History") }
          : section,
      ),
    });

    expect(screen.queryByLabelText("My Leave History")).toBeNull();
    expect(screen.getByLabelText("Apply Leave")).toBeTruthy();
  });
});
