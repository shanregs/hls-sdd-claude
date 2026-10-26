import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { axe } from "vitest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ClaimDetailPage } from "../ClaimDetailPage";
import { ClaimFormPage } from "../ClaimFormPage";
import { MyExpensesPage } from "../MyExpensesPage";
import { ROW, detail, json, renderAt } from "./fixtures";

const authFetch = vi.fn();
let roles: string[] = ["MANAGER"];

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

vi.mock("../../../access-model/useAccessModel", async () => {
  const actual = await vi.importActual<
    typeof import("../../../access-model/useAccessModel")
  >("../../../access-model/useAccessModel");
  return {
    ...actual,
    useAccessModel: () => ({
      loading: false,
      accessModel: {
        roles,
        dataScope: {},
        navigation: [
          {
            section: "OPERATIONS",
            items: [
              {
                label: "My Expenses",
                route: "/operations/my-expenses",
                actions: ["VIEW", "CREATE", "EDIT", "DELETE"],
              },
            ],
          },
        ],
      },
    }),
  };
});

const ROUTES = [
  { path: "/operations/my-expenses", element: <MyExpensesPage /> },
  { path: "/operations/my-expenses/new", element: <ClaimFormPage /> },
  { path: "/operations/my-expenses/:id", element: <ClaimDetailPage /> },
];

function mockApi(
  overrides: (url: string, init?: RequestInit) => Response | null = () => null,
) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    const custom = overrides(url, init);
    if (custom) return custom;
    if (url.includes("/areas"))
      return json([
        { id: null, name: "Head office" },
        { id: "z-1", name: "North" },
      ]);
    if (url.includes("/link-options")) return json([]);
    if (url.startsWith("/api/v1/my-expenses/c-1"))
      return json(detail({ ownClaim: true, allowedActions: ["WITHDRAW"] }));
    return json({
      content: [ROW],
      page: 0,
      size: 50,
      totalElements: 1,
      owed: "1200.00",
    });
  });
}

beforeEach(() => {
  authFetch.mockReset();
  roles = ["MANAGER"];
  mockApi();
});

describe("My Expenses list (spec 015 US7)", () => {
  it("shows own claims with Indian grouping, DD/MM/YYYY and what is still owed", async () => {
    renderAt("/operations/my-expenses", ROUTES);
    expect(await screen.findByText("12/10/2026")).toBeInTheDocument();
    expect(
      screen.getByText(/₹1,200\.00/, { selector: "td" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/You are still owed ₹1,200\.00/),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "12/10/2026" })).toHaveAttribute(
      "href",
      "/operations/my-expenses/c-1",
    );
  });

  it("asks for the status and month through the query", async () => {
    const user = userEvent.setup();
    renderAt("/operations/my-expenses", ROUTES);
    await screen.findByText("12/10/2026");
    await user.click(screen.getByRole("combobox", { name: "Status" }));
    await user.click(screen.getByRole("option", { name: "Approved" }));
    await waitFor(() =>
      expect(
        authFetch.mock.calls.some((c) =>
          String(c[0]).includes("status=APPROVED"),
        ),
      ).toBe(true),
    );
  });

  it("shows an empty state", async () => {
    mockApi((url) =>
      url.startsWith("/api/v1/my-expenses?")
        ? json({
            content: [],
            page: 0,
            size: 50,
            totalElements: 0,
            owed: "0.00",
          })
        : null,
    );
    renderAt("/operations/my-expenses", ROUTES);
    expect(
      await screen.findByText("You have no claims yet."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Nothing is owed to you right now."),
    ).toBeInTheDocument();
  });

  it("shows an error state", async () => {
    mockApi((url) =>
      url.startsWith("/api/v1/my-expenses?")
        ? json({ reason: "boom" }, 500)
        : null,
    );
    renderAt("/operations/my-expenses", ROUTES);
    expect(await screen.findByRole("alert")).toBeInTheDocument();
  });

  it.each(["MANAGER", "TEACHER", "ADMIN"])(
    "renders for the role %s",
    async (role) => {
      roles = [role];
      renderAt("/operations/my-expenses", ROUTES);
      expect(
        await screen.findByRole("heading", { name: "My Expenses" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("link", { name: "New claim" }),
      ).toBeInTheDocument();
    },
  );
});

describe("Claim form (spec 015 US1)", () => {
  it("names the rule when the amount is missing and sends nothing", async () => {
    const user = userEvent.setup();
    renderAt("/operations/my-expenses/new", ROUTES);
    await screen.findByRole("heading", { name: "New claim" });
    authFetch.mockClear();
    await user.click(screen.getByRole("button", { name: "Submit" }));
    expect(
      screen.getByText("The amount must be more than zero."),
    ).toBeInTheDocument();
    expect(authFetch).not.toHaveBeenCalled();
  });

  it("refuses more than two decimals and asks for a bill or a reason", async () => {
    const user = userEvent.setup();
    renderAt("/operations/my-expenses/new", ROUTES);
    await screen.findByRole("heading", { name: "New claim" });
    await user.type(screen.getByLabelText(/Amount/), "10.555");
    await user.click(screen.getByRole("button", { name: "Save draft" }));
    expect(
      screen.getByText("The amount can have at most two decimal places."),
    ).toBeInTheDocument();
    await user.clear(screen.getByLabelText(/Amount/));
    await user.type(screen.getByLabelText(/Amount/), "10.55");
    await user.click(screen.getByRole("button", { name: "Submit" }));
    expect(
      screen.getByText("Attach a bill, or give the reason there is no bill."),
    ).toBeInTheDocument();
  });

  it("checks the bill type before sending it", async () => {
    const user = userEvent.setup({ applyAccept: false });
    renderAt("/operations/my-expenses/new", ROUTES);
    await screen.findByRole("heading", { name: "New claim" });
    await user.upload(
      screen.getByLabelText("Add a bill"),
      new File(["x"], "notes.docx"),
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      "A bill must be a jpg, png or pdf file.",
    );
    await user.upload(
      screen.getByLabelText("Add a bill"),
      new File([new Uint8Array(11 * 1024 * 1024)], "big.jpg"),
    );
    expect(screen.getByRole("alert")).toHaveTextContent("at most 10 MB");
    await user.upload(
      screen.getByLabelText("Add a bill"),
      new File(["x"], "ok.pdf"),
    );
    expect(screen.getByText("ok.pdf")).toBeInTheDocument();
  });

  it("asks for a reason when the category is Other", async () => {
    const user = userEvent.setup();
    renderAt("/operations/my-expenses/new", ROUTES);
    await screen.findByRole("heading", { name: "New claim" });
    await user.click(screen.getByRole("combobox", { name: "Category" }));
    await user.click(screen.getByRole("option", { name: "Other" }));
    await user.type(screen.getByLabelText(/Amount/), "40");
    await user.type(
      screen.getByLabelText("Reason there is no bill"),
      "No receipt",
    );
    await user.click(screen.getByRole("button", { name: "Submit" }));
    expect(
      screen.getByText("A reason is required for the category Other."),
    ).toBeInTheDocument();
  });

  it("offers induction batches and training sessions for a Training Stay claim and asks for one", async () => {
    const user = userEvent.setup();
    mockApi((url) =>
      url.includes("/link-options")
        ? json([
            {
              kind: "INDUCTION_BATCH",
              ref: "b-1",
              date: "2026-10-01",
              label: "Induction 7 (2026-10-01 to 2026-10-12)",
            },
            {
              kind: "TRAINING_SESSION",
              ref: "s-1",
              date: "2026-10-10",
              label: "Training session - Phonics (2026-10-10)",
            },
          ])
        : null,
    );
    renderAt("/operations/my-expenses/new", ROUTES);
    await screen.findByRole("heading", { name: "New claim" });
    await user.click(screen.getByRole("combobox", { name: "Category" }));
    await user.click(screen.getByRole("option", { name: "Training Stay" }));
    await user.type(screen.getByLabelText(/Amount/), "1500");
    await user.type(
      screen.getByLabelText("Reason there is no bill"),
      "Receipt lost",
    );
    await user.click(screen.getByRole("button", { name: "Submit" }));
    expect(
      screen.getByText(
        "Choose the induction batch or training session for a Training Stay claim.",
      ),
    ).toBeInTheDocument();
    await user.click(
      screen.getByRole("combobox", {
        name: /Induction batch or training session/,
      }),
    );
    expect(
      screen.getByRole("option", { name: /Training session - Phonics/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("option", { name: /Induction 7/ }),
    ).toBeInTheDocument();
  });

  it("warns about a likely duplicate and submits again only when confirmed", async () => {
    const user = userEvent.setup();
    let submits = 0;
    mockApi((url, init) => {
      if (url === "/api/v1/my-expenses" && init?.method === "POST")
        return json(detail({ status: "DRAFT", version: 0 }), 201);
      if (url.endsWith("/submit")) {
        submits += 1;
        const body = JSON.parse(String(init?.body));
        return body.confirmDuplicate
          ? json(detail({ ownClaim: true }))
          : json(
              {
                reason:
                  "You already have a claim with the same date, amount and category.",
                matches: ["c-0"],
              },
              409,
            );
      }
      return null;
    });
    renderAt("/operations/my-expenses/new", ROUTES);
    await screen.findByRole("heading", { name: "New claim" });
    await user.type(screen.getByLabelText(/Amount/), "1200");
    await user.type(
      screen.getByLabelText("Reason there is no bill"),
      "Toll, no receipt",
    );
    await user.click(screen.getByRole("button", { name: "Submit" }));
    expect(
      await screen.findByText("This looks like a claim you already made"),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Submit anyway" }));
    await waitFor(() => expect(submits).toBe(2));
    const sent = authFetch.mock.calls.filter((c) =>
      String(c[0]).endsWith("/submit"),
    );
    expect(JSON.parse(String(sent[1][1].body)).confirmDuplicate).toBe(true);
  });

  it("keeps one request id for the life of the form", async () => {
    const user = userEvent.setup();
    const ids: string[] = [];
    mockApi((url, init) => {
      if (url === "/api/v1/my-expenses" && init?.method === "POST") {
        ids.push(JSON.parse(String(init.body)).clientRequestId);
        return json({ reason: "Network down" }, 500);
      }
      return null;
    });
    renderAt("/operations/my-expenses/new", ROUTES);
    await screen.findByRole("heading", { name: "New claim" });
    await user.type(screen.getByLabelText(/Amount/), "75");
    await user.click(screen.getByRole("button", { name: "Save draft" }));
    await screen.findByText("Network down");
    await user.click(screen.getByRole("button", { name: "Save draft" }));
    await waitFor(() => expect(ids).toHaveLength(2));
    expect(ids[0]).toBe(ids[1]);
    expect(ids[0]).toBeTruthy();
  });
});

describe("Claim detail (spec 015 US7)", () => {
  it("shows bills, history and only the buttons the server allowed", async () => {
    renderAt("/operations/my-expenses/c-1", ROUTES);
    expect(
      await screen.findByRole("heading", { name: /Travel and Fuel/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "fuel-bill.jpg" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Withdraw" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Submit" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Approve" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("list", { name: "History" })).toHaveTextContent(
      "submitted by Meera Manager",
    );
  });

  it("shows the rejection reason", async () => {
    mockApi((url) =>
      url.startsWith("/api/v1/my-expenses/c-1")
        ? json(
            detail({
              status: "REJECTED",
              statusLabel: "Rejected",
              ownClaim: true,
              allowedActions: ["EDIT", "SUBMIT"],
              decision: {
                by: "u-2",
                byName: "Admin A",
                at: "2026-10-14T05:00:00Z",
                reason: "Bill is not legible",
              },
            }),
          )
        : null,
    );
    renderAt("/operations/my-expenses/c-1", ROUTES);
    expect(await screen.findByText(/Bill is not legible/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Submit" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Edit" })).toBeInTheDocument();
  });
});

describe("Accessibility", () => {
  it.each(["light", "dark"] as const)(
    "has no violations on the list, the form and a claim in %s mode",
    async (mode) => {
      for (const path of [
        "/operations/my-expenses",
        "/operations/my-expenses/new",
        "/operations/my-expenses/c-1",
      ]) {
        const { container, unmount } = renderAt(path, ROUTES, mode);
        await waitFor(() =>
          expect(container.querySelector("h1")).not.toBeNull(),
        );
        await screen.findAllByRole("heading");
        await new Promise((resolve) => setTimeout(resolve, 50));
        const results = await axe(container, {
          runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag22aa"] },
        });
        expect(
          results.violations.map((v) => `${path} ${v.id}: ${v.help}`),
        ).toEqual([]);
        unmount();
      }
    },
    60_000,
  );
});
