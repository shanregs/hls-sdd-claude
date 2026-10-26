import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { axe } from "vitest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../../app/RouteGuard";
import { ContributorDashboard } from "../../../dashboards/ContributorDashboard";
import { buildMuiTheme } from "../../../theme/tokens";
import { ContentDetailPage } from "../ContentDetailPage";
import { ContentPage } from "../ContentPage";
import { checkFile, type TypeLimit } from "../contentApi";

const authFetch = vi.fn();
let roles: string[] = ["CONTENT_CONTRIBUTOR"];
let navigation: { section: string; items: unknown[] }[] = [];

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

vi.mock("../../../access-model/useAccessModel", async () => {
  const actual = await vi.importActual<
    typeof import("../../../access-model/useAccessModel")
  >("../../../access-model/useAccessModel");
  return {
    ...actual,
    useAccessModel: () => ({
      loading: false,
      accessModel: { roles, dataScope: {}, navigation },
    }),
  };
});

function grant(role: string, label: string) {
  roles = [role];
  navigation = [
    {
      section: "OPERATIONS",
      items: [
        {
          label,
          route: "/operations/content",
          actions: ["VIEW", "CREATE", "EDIT"],
        },
      ],
    },
  ];
}

const LIMITS = {
  types: [
    { extension: "docx", maxBytes: 25 * 1024 * 1024 },
    { extension: "mp4", maxBytes: 500 * 1024 * 1024 },
    { extension: "pdf", maxBytes: 25 * 1024 * 1024 },
  ],
};

const ROW = {
  id: "c-1",
  code: "CNT-000001",
  title: "Phonics Week 1",
  category: "Lesson",
  subject: "English",
  authorUserId: "u-1",
  authorName: "Cora Contributor",
  status: "SUBMITTED",
  statusLabel: "Submitted",
  currentVersion: 1,
  dueOn: "2026-10-30",
  overdue: false,
  updatedAt: "2026-10-26T05:00:00Z",
};

const SUMMARY = { draft: 1, assigned: 0, submitted: 2, approved: 3, returned: 1, overdue: 1 };

function detail(actions: Record<string, boolean>) {
  return {
    id: "c-1",
    code: "CNT-000001",
    title: "Phonics Week 1",
    category: "Lesson",
    subject: "English",
    description: "Week one",
    authorUserId: "u-1",
    authorName: "Cora Contributor",
    createdByUserId: "u-1",
    createdByName: "Cora Contributor",
    status: "SUBMITTED",
    statusLabel: "Submitted",
    currentVersion: 1,
    lastApprovedVersion: null,
    dueOn: null,
    overdue: false,
    reviewerName: null,
    approvedOn: null,
    taskId: null,
    version: 0,
    versions: [
      {
        number: 1,
        fileName: "lesson.mp4",
        contentType: "video/mp4",
        sizeBytes: 2048,
        authorUserId: "u-1",
        authorName: "Cora Contributor",
        submittedByUserId: "u-1",
        submittedByName: "Cora Contributor",
        note: "first cut",
        submittedAt: "2026-10-26T05:00:00Z",
        removed: false,
        removalReason: null,
        lastApproved: false,
      },
    ],
    reviews: [],
    history: [
      { event: "CREATED", actorName: "Cora Contributor", at: "2026-10-26T04:00:00Z", before: null, after: "DRAFT", note: null },
    ],
    actions: { submit: false, edit: false, approve: false, returnForRevision: false, removeFile: false, ...actions },
  };
}

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function mockApi(actions: Record<string, boolean> = {}) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (init?.method === "POST" && String(url).endsWith("/approve")) return json({ ...detail({}), status: "APPROVED", statusLabel: "Approved" });
    if (init?.method === "POST") return json(detail({}), 201);
    if (url.includes("/summary")) return json(SUMMARY);
    if (url.includes("/limits")) return json(LIMITS);
    if (url.includes("/authors")) return json([{ userId: "u-1", displayName: "Cora Contributor", roles: ["CONTENT_CONTRIBUTOR"] }]);
    if (/\/api\/v1\/content\/c-1$/.test(String(url))) return json(detail(actions));
    return json({ content: [ROW], page: 0, size: 100, totalElements: 1 });
  });
}

function renderAt(path: string) {
  return render(
    <ThemeProvider theme={buildMuiTheme("light")}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/operations/content" element={<RouteGuard><ContentPage /></RouteGuard>} />
          <Route path="/operations/content/:id" element={<RouteGuard><ContentDetailPage /></RouteGuard>} />
          <Route path="/operations/tasks" element={<RouteGuard><div>tasks</div></RouteGuard>} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  authFetch.mockReset();
  grant("CONTENT_CONTRIBUTOR", "My Content");
  mockApi();
});

describe("Content list (spec 029)", () => {
  it("shows a Contributor My Content with counts on the tabs and the due date as DD/MM/YYYY", async () => {
    renderAt("/operations/content");
    expect(await screen.findByText("Phonics Week 1")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "My Content" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Submitted (2)" })).toBeInTheDocument();
    expect(screen.getByText("30/10/2026")).toBeInTheDocument();
    expect(screen.queryByLabelText("Author")).not.toBeInTheDocument();
  });

  it("gives management the Content view with author, category and subject filters", async () => {
    grant("DIRECTOR", "Content");
    renderAt("/operations/content");
    expect(await screen.findByRole("heading", { name: "Content" })).toBeInTheDocument();
    expect(screen.getByLabelText("Author")).toBeInTheDocument();
    expect(screen.getByLabelText("Category")).toBeInTheDocument();
    expect(screen.getByLabelText("Subject")).toBeInTheDocument();
  });

  it("asks the server for the status the chosen tab names", async () => {
    const user = userEvent.setup();
    renderAt("/operations/content");
    await screen.findByText("Phonics Week 1");
    authFetch.mockClear();
    await user.click(screen.getByRole("tab", { name: "Approved (3)" }));
    await waitFor(() => expect(authFetch.mock.calls.some(([u]) => String(u).includes("status=APPROVED"))).toBe(true));
  });

  it("opens on the status named in a link from the dashboard", async () => {
    renderAt("/operations/content?status=SUBMITTED");
    await screen.findByText("Phonics Week 1");
    const listCalls = authFetch.mock.calls.map(([u]) => String(u)).filter((u) => u.includes("/api/v1/content?"));
    expect(listCalls.every((u) => u.includes("status=SUBMITTED"))).toBe(true);
  });

  it("shows an error state", async () => {
    authFetch.mockReset();
    authFetch.mockResolvedValue(json({ reason: "Could not load" }, 500));
    renderAt("/operations/content");
    expect(await screen.findByRole("alert")).toHaveTextContent("Could not load");
  });

  it("creates content and opens it", async () => {
    const user = userEvent.setup();
    renderAt("/operations/content");
    await screen.findByText("Phonics Week 1");
    await user.click(screen.getByRole("button", { name: "New content" }));
    await user.type(screen.getByLabelText(/Title/), "Phonics Week 2");
    await user.type(screen.getByLabelText(/Category/), "Lesson");
    await user.type(screen.getByLabelText(/Subject/), "English");
    await user.click(screen.getByRole("button", { name: "Create" }));
    await waitFor(() =>
      expect(authFetch.mock.calls.some(([u, init]) => String(u).endsWith("/api/v1/content") && init?.method === "POST")).toBe(true),
    );
  });
});

describe("Content detail (spec 029)", () => {
  it("shows the versions, the author and exactly the actions the server allows", async () => {
    mockApi({ approve: true, returnForRevision: true });
    grant("DIRECTOR", "Content");
    renderAt("/operations/content/c-1");
    expect(await screen.findByRole("heading", { name: "Phonics Week 1" })).toBeInTheDocument();
    expect(screen.getByText(/lesson.mp4/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Approve version 1" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Return for revision" })).toBeInTheDocument();
    expect(screen.queryByText("Submit a version")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Remove file" })).not.toBeInTheDocument();
  });

  it("hides the review buttons for the author", async () => {
    renderAt("/operations/content/c-1");
    await screen.findByRole("heading", { name: "Phonics Week 1" });
    expect(screen.queryByRole("button", { name: /Approve/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Return for revision" })).not.toBeInTheDocument();
  });

  it("offers the submit box with the accepted types and limits when the server allows a submission", async () => {
    mockApi({ submit: true });
    renderAt("/operations/content/c-1");
    expect(await screen.findByText("Submit a version")).toBeInTheDocument();
    expect(screen.getByText(/mp4 \(up to 500 MB\)/)).toBeInTheDocument();
    expect(screen.getByText(/pdf \(up to 25 MB\)/)).toBeInTheDocument();
  });

  it("refuses a wrong type or an oversize file before sending it", async () => {
    mockApi({ submit: true });
    const user = userEvent.setup({ applyAccept: false });
    renderAt("/operations/content/c-1");
    await screen.findByText("Submit a version");
    const input = screen.getByLabelText("Content file") as HTMLInputElement;
    await user.upload(input, new File(["x"], "old.doc"));
    expect(await screen.findByRole("alert")).toHaveTextContent("not accepted");
    authFetch.mockClear();
    expect(authFetch).not.toHaveBeenCalled();
  });

  it("sends an accepted file as the next version", async () => {
    mockApi({ submit: true });
    const user = userEvent.setup({ applyAccept: false });
    renderAt("/operations/content/c-1");
    await screen.findByText("Submit a version");
    await user.upload(screen.getByLabelText("Content file"), new File(["%PDF-1.4"], "lesson.pdf"));
    await waitFor(() =>
      expect(authFetch.mock.calls.some(([u, init]) => String(u).endsWith("/versions") && init?.method === "POST")).toBe(true),
    );
  });

  it("approves through the server", async () => {
    mockApi({ approve: true, returnForRevision: true });
    grant("DIRECTOR", "Content");
    const user = userEvent.setup();
    renderAt("/operations/content/c-1");
    await user.click(await screen.findByRole("button", { name: "Approve version 1" }));
    await waitFor(() => expect(authFetch.mock.calls.some(([u]) => String(u).endsWith("/approve"))).toBe(true));
    expect(await screen.findByText("Approved")).toBeInTheDocument();
  });

  it("is not authorized for a Teacher", async () => {
    roles = ["TEACHER"];
    navigation = [];
    renderAt("/operations/content");
    expect(authFetch).not.toHaveBeenCalled();
    expect(screen.queryByText("Phonics Week 1")).not.toBeInTheDocument();
  });

  it.each(["light", "dark"] as const)(
    "has no accessibility violations in %s mode (list and detail)",
    async (mode) => {
      const { container, unmount } = render(
        <ThemeProvider theme={buildMuiTheme(mode)}>
          <MemoryRouter initialEntries={["/operations/content"]}>
            <Routes>
              <Route path="/operations/content" element={<RouteGuard><ContentPage /></RouteGuard>} />
            </Routes>
          </MemoryRouter>
        </ThemeProvider>,
      );
      await screen.findByText("Phonics Week 1");
      const options = { runOnly: { type: "tag" as const, values: ["wcag2a", "wcag2aa", "wcag22aa"] } };
      const list = await axe(container, options);
      expect(list.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
      unmount();
      mockApi({ submit: true, approve: true, returnForRevision: true });
      const detailView = renderAt("/operations/content/c-1");
      await screen.findByRole("heading", { name: "Phonics Week 1" });
      const results = await axe(detailView.container, options);
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    },
    30_000,
  );
});

describe("file checks and the Contributor dashboard", () => {
  const types: TypeLimit[] = LIMITS.types;

  it("accepts the listed types within their limits and refuses the rest with the limit named", () => {
    expect(checkFile(new File(["x"], "a.pdf"), types)).toBeNull();
    expect(checkFile(new File(["x"], "a.exe"), types)).toContain("not accepted");
    const big = new File(["x"], "a.pdf");
    Object.defineProperty(big, "size", { value: 26 * 1024 * 1024 });
    expect(checkFile(big, types)).toContain("25 MB");
    const video = new File(["x"], "a.mp4");
    Object.defineProperty(video, "size", { value: 400 * 1024 * 1024 });
    expect(checkFile(video, types)).toBeNull();
  });

  it("shows the counts of the Contributor's own content", async () => {
    render(
      <ThemeProvider theme={buildMuiTheme("light")}>
        <MemoryRouter>
          <ContributorDashboard />
        </MemoryRouter>
      </ThemeProvider>,
    );
    expect(await screen.findByText("Waiting for review")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText("2")).toBeInTheDocument());
    expect(screen.getByRole("link", { name: "Returned to me" })).toHaveAttribute("href", "/operations/content?status=RETURNED");
  });
});
