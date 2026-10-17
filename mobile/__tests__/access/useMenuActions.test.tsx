import { renderHook } from "@testing-library/react-native";
import type { ReactNode } from "react";
import { useMenuActions } from "../../src/access/useMenuActions";
import type { AccessModel } from "../../src/api/accessModelApi";

/** Spec 034 T009: the granted actions of a menu route, read from the access model and nothing else. */
let mockModel: AccessModel | null = null;
jest.mock("../../src/access/AccessModelProvider", () => ({
  useAccessModel: () => ({ model: mockModel, status: "ready", refresh: jest.fn(), canOpen: () => true }),
}));

const modelWith = (...sections: { section: string; items: { label: string; route: string; actions: string[] }[] }[]): AccessModel => ({
  roles: ["MANAGER"],
  navigation: sections,
  dataScope: {},
});
const wrapper = ({ children }: { children: ReactNode }) => <>{children}</>;
const actionsAt = async (route: string) => (await renderHook(() => useMenuActions(route), { wrapper })).result.current;

describe("useMenuActions", () => {
  it.each([
    [["VIEW"], { canCreate: false, canEdit: false, canDelete: false }],
    [["VIEW", "CREATE"], { canCreate: true, canEdit: false, canDelete: false }],
    [["VIEW", "EDIT"], { canCreate: false, canEdit: true, canDelete: false }],
    [["VIEW", "DELETE"], { canCreate: false, canEdit: false, canDelete: true }],
    [["VIEW", "CREATE", "EDIT", "DELETE"], { canCreate: true, canEdit: true, canDelete: true }],
  ])("reads %j", async (actions, expected) => {
    mockModel = modelWith({ section: "OPERATIONS", items: [{ label: "My Tasks", route: "/operations/tasks", actions }] });

    expect(await actionsAt("/operations/tasks")).toEqual(expected);
  });

  it("grants nothing for a route the menu does not have, or with no model", async () => {
    mockModel = modelWith({ section: "OPERATIONS", items: [{ label: "My Tasks", route: "/operations/tasks", actions: ["VIEW", "CREATE"] }] });
    expect(await actionsAt("/marketing/calendar")).toEqual({ canCreate: false, canEdit: false, canDelete: false });
    mockModel = null;
    expect(await actionsAt("/operations/tasks")).toEqual({ canCreate: false, canEdit: false, canDelete: false });
  });

  it("joins the actions when the same route is in two sections", async () => {
    mockModel = modelWith(
      { section: "A", items: [{ label: "x", route: "/r", actions: ["VIEW", "CREATE"] }] },
      { section: "B", items: [{ label: "y", route: "/r", actions: ["EDIT"] }] },
    );

    expect(await actionsAt("/r")).toEqual({ canCreate: true, canEdit: true, canDelete: false });
  });
});
