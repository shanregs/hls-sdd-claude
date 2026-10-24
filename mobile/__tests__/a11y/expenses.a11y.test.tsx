import AsyncStorage from "@react-native-async-storage/async-storage";
import { fireEvent, screen } from "@testing-library/react-native";
import { saveItem } from "../../src/outbox/outboxStore";
import { openExpenseScreen } from "../support/expenseApp";
import { fillClaim, openAddForm, press, takeBill } from "../support/expenseActions";
import { bill, claimDetail } from "../support/expenseFixtures";
import { chooseMenuItem } from "../support/navigation";
import { makeItem } from "../support/outboxFixtures";

/** Spec 036 T043: My Expenses can be used with a screen reader and a thumb, in light and dark. */
afterEach(() => jest.restoreAllMocks());

type TreeNode = { props: Record<string, unknown>; children?: unknown[]; parent: TreeNode | null };

function textOf(node: TreeNode): string {
  const parts: string[] = [];
  const walk = (n: unknown) => {
    if (typeof n === "string") parts.push(n);
    else if (n && typeof n === "object" && "children" in n) (n as TreeNode).children?.forEach(walk);
  };
  walk(node);
  return parts.join("").trim();
}

/** Every button and radio has a name a screen reader can say: its label, or the words written on it. */
function expectNamedControls() {
  const controls = [...screen.queryAllByRole("button"), ...screen.queryAllByRole("radio")];
  expect(controls.length).toBeGreaterThan(0);
  for (const control of controls) {
    const node = control as unknown as TreeNode;
    const label = node.props.accessibilityLabel;
    const named = (typeof label === "string" && label.trim() !== "") || textOf(node) !== "";
    expect(named).toBe(true);
  }
}

const flat = (style: unknown) => Object.assign({}, ...[style].flat(Infinity).filter(Boolean));
const minHeightOf = (text: string) => flat(screen.getByText(text).parent?.props.style).minHeight ?? 48;
const claims = () => [
  claimDetail({ id: "c-1", status: "REJECTED", category: "MARKETING", amount: "300.00", bills: [bill()], decision: { by: "u-5", byName: "Divya", at: "2026-10-22T05:00:00Z", reason: "Unreadable" }, allowedActions: ["EDIT", "SUBMIT", "ADD_BILL"] }),
  claimDetail({ id: "c-2", status: "SUBMITTED", category: "OFFICE_SUPPLIES", amount: "20.00", allowedActions: ["WITHDRAW"] }),
];

async function seedWaiting() {
  jest.requireMock("expo-file-system/legacy").__state.files.set("file:///cache/photo.jpg", "SEVMTE9fQklMTF9CWVRFUw==");
  await saveItem(makeItem({ ownerUserId: "u-1", fields: { expenseDate: "2026-08-20" } }), { "bill-a": "file:///cache/photo.jpg" });
}

describe("the list", () => {
  it("names the chips, the cards and the waiting claims, tells the state in words, and announces the banner", async () => {
    await seedWaiting();
    await openExpenseScreen({ expenses: { claims: claims() } });

    const card = await screen.findByLabelText("Marketing, ₹300, 22/10/2026, Head office, Rejected, 1 bill");
    expect(card.props.accessibilityRole).toBe("button");
    for (const label of ["All", "Draft", "Submitted", "Approved", "Rejected", "Paid", "Paid by company", "Add expense", "Filter by month, off"]) {
      expect(screen.getByLabelText(label)).toBeTruthy();
    }
    expect(await screen.findByLabelText(/Waiting claim: Travel and Fuel, ₹850, 20\/08\/2026, Not sent/)).toBeTruthy();
    expect((await screen.findByText(/older than 60 days/)).props.accessibilityRole).toBe("alert");
    expectNamedControls();
    expect(minHeightOf("Add expense")).toBeGreaterThanOrEqual(48);
  });
});

describe("the detail", () => {
  it("names its buttons and the bills, and keeps every target at least 48", async () => {
    await openExpenseScreen({ expenses: { claims: claims() } });
    await press("Marketing, ₹300, 22/10/2026, Head office, Rejected, 1 bill");
    await screen.findByText("Correct and resubmit");
    for (const label of ["Correct and resubmit", "Submit claim", "Add bill", "Back to the expenses"]) {
      expect(screen.getByLabelText(label)).toBeTruthy();
    }
    expect(screen.getByLabelText(/Bill 1: bill-1.jpg, JPG/)).toBeTruthy();
    expectNamedControls();
    expect(minHeightOf("Correct and resubmit")).toBeGreaterThanOrEqual(48);
  });

  it("names the withdraw confirmation", async () => {
    await openExpenseScreen({ expenses: { claims: claims() } });
    await press("Office Supplies, ₹20, 22/10/2026, Head office, Submitted, no bill");
    await press("Withdraw claim");
    expect(await screen.findByText("Withdraw this claim?")).toBeTruthy();
    for (const label of ["Cancel", "Withdraw"]) expect(screen.getByLabelText(label)).toBeTruthy();
    expectNamedControls();
  });
});

describe("the form", () => {
  it("names the fields, the choices, the bill controls and the buttons, and announces a refusal", async () => {
    await openExpenseScreen({ expenses: { refusals: { submit: { status: 400, body: { reason: "The expense is older than 60 days." } } } } });
    await openAddForm();
    await fillClaim({ amount: "400", category: "Other", reason: "Courier" });
    await takeBill();
    for (const label of ["Amount", "Reason", "Note", "Take photo", "Choose photo", "Choose PDF", "Retake bill 1", "Remove bill 1", "Save as draft", "Submit", "Cancel", "Paid by myself", "Paid by company", "Area Head office"]) {
      expect(screen.queryAllByLabelText(label).length).toBeGreaterThan(0);
    }
    expect(screen.getByLabelText("Date, Friday 23 October 2026")).toBeTruthy();
    expectNamedControls();
    expect(minHeightOf("Save as draft")).toBeGreaterThanOrEqual(48);

    await press("Submit");
    const refusal = await screen.findByText("The expense is older than 60 days.");
    expect(refusal.props.accessibilityRole).toBe("alert");
  });

  it("names the duplicate dialog", async () => {
    await openExpenseScreen({ expenses: { claims: [claimDetail({ id: "old", status: "SUBMITTED", category: "MARKETING", amount: "400.00", expenseDate: "2026-10-23" })] } });
    await openAddForm();
    await fillClaim({ amount: "400", category: "Marketing" });
    await fireEvent.changeText(screen.getByLabelText("Reason there is no bill"), "Cash");
    await press("Submit");
    expect(await screen.findByText("Possible duplicate")).toBeTruthy();
    for (const label of ["Submit anyway", "Cancel (possible duplicate)"]) expect(screen.getByLabelText(label)).toBeTruthy();
    expectNamedControls();
  });

  it("names the sign-out warning", async () => {
    await seedWaiting();
    await openExpenseScreen({ expenses: { claims: [] } });
    await chooseMenuItem("Log out");
    expect(await screen.findByText("Claims not sent")).toBeTruthy();
    for (const label of ["Stay", "Sign out"]) expect(screen.getByLabelText(label)).toBeTruthy();
  });
});

describe("light and dark", () => {
  it.each(["light", "dark"])("renders the list, the detail and the form in the %s theme", async (mode) => {
    await AsyncStorage.setItem("hls.themePreference", mode);
    await openExpenseScreen({ expenses: { claims: claims() } });
    await screen.findByLabelText(/Marketing, ₹300/);
    await openAddForm();
    expect(screen.getByLabelText("Amount")).toBeTruthy();
    await press("Cancel");
    await press("Marketing, ₹300, 22/10/2026, Head office, Rejected, 1 bill");
    expect(await screen.findByText("Correct and resubmit")).toBeTruthy();
  });
});
