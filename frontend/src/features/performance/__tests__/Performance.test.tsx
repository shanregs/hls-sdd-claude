import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RouteGuard } from "../../../app/RouteGuard";
import { buildMuiTheme } from "../../../theme/tokens";
import { FeedbackPage } from "../FeedbackPage";
import { IssuePage, IssuesPage } from "../IssuePage";
import { ObservationForm } from "../ObservationForm";
import { ObservationsPage } from "../ObservationsPage";
import { PlannedPage } from "../PlannedPage";
import { TeacherPerformancePage } from "../TeacherPerformancePage";

const authFetch = vi.fn();
let roles: string[] = ["MANAGER"];
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

const ROUTE = "/operations/teacher-performance";

function grant(role: string, actions = ["VIEW", "CREATE", "EDIT"]) {
  roles = [role];
  navigation = [
    {
      section: "OPERATIONS",
      items: [{ label: "Teacher Performance", route: ROUTE, actions }],
    },
  ];
}

function noAccess(role: string) {
  roles = [role];
  navigation = [{ section: "ACCOUNT", items: [] }];
}

const OPTIONS = {
  areas: [
    { value: "ENGLISH_COMMUNICATION", label: "English communication" },
    { value: "TEACHING_ABILITY", label: "Teaching ability" },
  ],
  results: [
    { value: "GOOD", label: "Good" },
    { value: "NEEDS_IMPROVEMENT", label: "Needs improvement" },
    { value: "UNSUITABLE", label: "Unsuitable" },
  ],
  notHeldReasons: [
    { value: "PRINCIPAL_DECLINED", label: "Principal declined" },
    { value: "OTHER", label: "Other" },
  ],
  permissionWays: [{ value: "PHONE", label: "Phone" }],
  feedbackCategories: [{ value: "TEACHING", label: "Teaching issue" }],
  planReasons: [{ value: "MANAGEMENT_PLAN", label: "Management plan" }],
};

const ROW = {
  id: "o-1",
  rootId: "o-1",
  versionNo: 1,
  teacherId: "t-1",
  teacherName: "Asha Teacher",
  schoolId: "s-1",
  schoolName: "Green Valley School",
  observerName: "Meera Manager",
  observedOn: "2026-10-12",
  status: "SUBMITTED",
  overallResult: "GOOD",
  averageRating: 4,
  notHeldReason: null,
  principalDeclined: false,
  permissionRecorded: false,
  issueId: null,
};

const DECLINED = {
  ...ROW,
  id: "o-2",
  rootId: "o-2",
  status: "NOT_HELD",
  overallResult: null,
  averageRating: null,
  notHeldReason: "PRINCIPAL_DECLINED",
  principalDeclined: true,
};

function observation(
  actions: Record<string, boolean>,
  patch: Record<string, unknown> = {},
) {
  return {
    id: "o-1",
    rootId: "o-1",
    versionNo: 1,
    current: true,
    teacherId: "t-1",
    teacherName: "Asha Teacher",
    schoolId: "s-1",
    schoolName: "Green Valley School",
    observerUserId: "u-1",
    observerName: "Meera Manager",
    observedOn: "2026-10-12",
    className: "Class 5",
    subject: "English",
    durationMinutes: 40,
    status: "SUBMITTED",
    notHeldReason: null,
    notHeldReasonLabel: null,
    notHeldText: null,
    principalDeclined: false,
    overallResult: "GOOD",
    averageRating: 4,
    suggestedResult: "GOOD",
    ratings: { ENGLISH_COMMUNICATION: 4, TEACHING_ABILITY: 4 },
    comments: null,
    strengths: "Clear voice",
    improvements: null,
    principalComments: null,
    permissionRecorded: false,
    permissionBy: null,
    permissionWay: null,
    permissionOn: null,
    permissionLabel: "Permission not recorded",
    defaultPrincipalName: "Mrs Rao",
    issueId: null,
    issueStatus: null,
    followupOfIssueId: null,
    nextStep: null,
    correctionReason: null,
    submittedAt: "2026-10-12T05:00:00Z",
    attachments: [],
    actions: {
      edit: false,
      submit: false,
      notHeld: false,
      correct: false,
      attach: false,
      ...actions,
    },
    version: 3,
    ...patch,
  };
}

function issue(actions: Record<string, boolean>, patch = {}) {
  return {
    id: "i-1",
    teacherId: "t-1",
    teacherName: "Asha Teacher",
    schoolId: "s-1",
    schoolName: "Green Valley School",
    managerUserId: "u-1",
    managerName: "Meera Manager",
    sourceType: "OBSERVATION",
    sourceRootId: "o-1",
    category: "NEEDS_IMPROVEMENT",
    status: "UNDER_CORRECTION",
    resolution: null,
    correctionRounds: 2,
    openedBy: "u-1",
    openedByName: "Meera Manager",
    openedOn: "2026-10-01",
    closedOn: null,
    closingReason: null,
    needsCorrectiveAction: true,
    followUpOn: null,
    links: [
      {
        type: "OBSERVATION",
        rootId: "o-1",
        id: "o-1",
        date: "2026-10-01",
        result: "NEEDS_IMPROVEMENT",
        summary: "Observation: Needs improvement",
      },
    ],
    correctiveActions: [
      {
        round: 1,
        discussed: "Homework checking",
        requiredChange: "Check daily",
        followUpOn: "2026-10-15",
        recordedBy: "u-1",
        recordedByName: "Meera Manager",
        recordedOn: "2026-10-01",
        followUpObservationRootId: "o-1",
      },
    ],
    referral: null,
    actions: {
      correctiveAction: false,
      refer: false,
      cancel: false,
      closeContinue: false,
      escalate: false,
      ...actions,
    },
    version: 5,
    ...patch,
  };
}

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

type Handler = (url: string, init?: RequestInit) => Response | undefined;

/** Answers the endpoints the pages call; `extra` handlers run first so a test can override one. */
function mockApi(...extra: Handler[]) {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    for (const handler of extra) {
      const answer = handler(String(url), init);
      if (answer) return answer;
    }
    if (url.includes("/options")) return json(OPTIONS);
    if (url.includes("/observations/preview"))
      return json({ average: 4, suggestedResult: "GOOD" });
    if (url.includes("/observations?") || url.endsWith("/observations"))
      return json({
        content: [ROW, DECLINED],
        page: 0,
        size: 50,
        totalElements: 2,
      });
    if (url.includes("/api/v1/teachers"))
      return json({
        content: [
          {
            id: "t-1",
            name: "Asha Teacher",
            school: { id: "s-1", name: "Green Valley School" },
          },
        ],
        page: 0,
        size: 200,
        totalElements: 1,
      });
    return json({ reason: "unexpected " + url }, 404);
  });
}

function renderAt(path: string) {
  const guarded = (el: React.ReactElement) => <RouteGuard>{el}</RouteGuard>;
  return render(
    <ThemeProvider theme={buildMuiTheme("light")}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path={ROUTE} element={guarded(<ObservationsPage />)} />
          <Route path={`${ROUTE}/planned`} element={guarded(<PlannedPage />)} />
          <Route
            path={`${ROUTE}/feedback`}
            element={guarded(<FeedbackPage />)}
          />
          <Route path={`${ROUTE}/issues`} element={guarded(<IssuesPage />)} />
          <Route
            path={`${ROUTE}/issues/:id`}
            element={guarded(<IssuePage />)}
          />
          <Route
            path={`${ROUTE}/observations/:id`}
            element={guarded(<ObservationForm />)}
          />
          <Route
            path={`${ROUTE}/teachers/:teacherId`}
            element={guarded(<TeacherPerformancePage />)}
          />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  authFetch.mockReset();
  grant("MANAGER");
  mockApi();
});

describe("Teacher Performance access (spec 026 FR-030)", () => {
  it.each(["ADMIN", "DIRECTOR", "MANAGER"])(
    "shows %s the observations and the Start observation button",
    async (role) => {
      grant(role);
      renderAt(ROUTE);
      expect(
        await screen.findByRole("heading", { name: "Teacher Performance" }),
      ).toBeInTheDocument();
      expect(
        (await screen.findAllByRole("link", { name: "12/10/2026" }))[0],
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: "Start observation" }),
      ).toBeInTheDocument();
      for (const tab of [
        "Observations",
        "Planned",
        "Principal feedback",
        "Issues",
      ]) {
        expect(screen.getByRole("tab", { name: tab })).toBeInTheDocument();
      }
    },
  );

  it.each(["TEACHER", "SYSTEM"])(
    "gives %s no screen and fetches nothing",
    async (role) => {
      noAccess(role);
      renderAt(ROUTE);
      await waitFor(() =>
        expect(
          screen.queryByRole("heading", { name: "Teacher Performance" }),
        ).not.toBeInTheDocument(),
      );
      expect(authFetch).not.toHaveBeenCalled();
    },
  );

  it("hides Start observation when the grant is View only", async () => {
    grant("MANAGER", ["VIEW"]);
    renderAt(ROUTE);
    await screen.findAllByRole("link", { name: "12/10/2026" });
    expect(
      screen.queryByRole("button", { name: "Start observation" }),
    ).not.toBeInTheDocument();
  });
});

describe("Observation list", () => {
  it("flags a visit where the Principal declined and a submitted one with no permission recorded", async () => {
    renderAt(ROUTE);
    await screen.findAllByRole("link", { name: "12/10/2026" });
    expect(screen.getByText("Principal declined")).toBeInTheDocument();
    expect(screen.getByText("Permission not recorded")).toBeInTheDocument();
    expect(screen.getByText("2 observations")).toBeInTheDocument();
  });

  it("asks the server for only the declined visits when that filter is ticked", async () => {
    const user = userEvent.setup();
    renderAt(ROUTE);
    await screen.findAllByRole("link", { name: "12/10/2026" });
    authFetch.mockClear();
    await user.click(screen.getByLabelText("Principal declined only"));
    await waitFor(() =>
      expect(
        authFetch.mock.calls.some(([u]) =>
          String(u).includes("principalDeclined=true"),
        ),
      ).toBe(true),
    );
  });

  it("shows an empty state and an error state", async () => {
    mockApi((url) =>
      url.includes("/observations?")
        ? json({ content: [], page: 0, size: 50, totalElements: 0 })
        : undefined,
    );
    const { unmount } = renderAt(ROUTE);
    expect(
      await screen.findByText("No observations here."),
    ).toBeInTheDocument();
    unmount();
    authFetch.mockReset();
    authFetch.mockResolvedValue(json({ reason: "Could not load" }, 500));
    renderAt(ROUTE);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load",
    );
  });
});

describe("Observation form", () => {
  it("shows a finished observation with Permission not recorded and no editing buttons", async () => {
    mockApi((url) =>
      url.endsWith("/observations/o-1") ? json(observation({})) : undefined,
    );
    renderAt(`${ROUTE}/observations/o-1`);
    expect(
      await screen.findByRole("heading", {
        name: "Observation of Asha Teacher",
      }),
    ).toBeInTheDocument();
    expect(screen.getByText("Permission not recorded")).toBeInTheDocument();
    for (const name of [
      "Save draft",
      "Submit observation",
      "Visit not held",
      "Correct",
      "Attach file",
    ]) {
      expect(screen.queryByRole("button", { name })).not.toBeInTheDocument();
    }
    expect(screen.getByLabelText("Overall result")).toHaveAttribute(
      "aria-disabled",
      "true",
    );
  });

  it("offers Correct only when the server allows it, and then asks for a reason", async () => {
    const user = userEvent.setup();
    mockApi((url, init) => {
      if (url.endsWith("/observations/o-1/correct") && init?.method === "POST")
        return json(
          observation({}, { versionNo: 2, correctionReason: "Wrong ratings" }),
        );
      if (url.endsWith("/observations/o-1"))
        return json(observation({ correct: true }));
      return undefined;
    });
    renderAt(`${ROUTE}/observations/o-1`);
    await user.click(await screen.findByRole("button", { name: "Correct" }));
    authFetch.mockClear();
    await user.click(screen.getByRole("button", { name: "Save correction" }));
    expect(
      screen.getByText("Write the reason for the correction."),
    ).toBeInTheDocument();
    expect(authFetch.mock.calls.some(([, i]) => i?.method === "POST")).toBe(
      false,
    );
    await user.type(
      screen.getByLabelText(/Reason for the correction/),
      "Wrong ratings",
    );
    await user.click(screen.getByRole("button", { name: "Save correction" }));
    await waitFor(() => {
      const call = authFetch.mock.calls.find(([u]) =>
        String(u).endsWith("/correct"),
      );
      expect(call).toBeTruthy();
      const sent = JSON.parse(call![1].body as string);
      expect(sent.reason).toBe("Wrong ratings");
      expect(sent.form.version).toBe(3);
    });
  });

  it("shows a draft with the permission block optional and Save, Submit and Not held for its observer", async () => {
    mockApi((url) =>
      url.endsWith("/observations/o-1")
        ? json(
            observation(
              { edit: true, submit: true, notHeld: true, attach: true },
              {
                status: "DRAFT",
                overallResult: null,
                ratings: {},
                strengths: null,
                averageRating: null,
                suggestedResult: null,
              },
            ),
          )
        : undefined,
    );
    renderAt(`${ROUTE}/observations/o-1`);
    expect(await screen.findByLabelText("English communication")).toBeEnabled();
    expect(screen.getByText(/Optional\. Leave it blank/)).toBeInTheDocument();
    for (const name of [
      "Save draft",
      "Submit observation",
      "Visit not held",
      "Attach file",
    ]) {
      expect(screen.getByRole("button", { name })).toBeInTheDocument();
    }
    expect(
      screen.queryByRole("button", { name: "Correct" }),
    ).not.toBeInTheDocument();
  });

  it("names the missing parts inline and sends nothing while the form is not complete", async () => {
    const user = userEvent.setup();
    mockApi((url) =>
      url.endsWith("/observations/o-1")
        ? json(
            observation(
              { edit: true, submit: true, notHeld: true },
              {
                status: "DRAFT",
                overallResult: null,
                ratings: {},
                strengths: null,
                averageRating: null,
                suggestedResult: null,
              },
            ),
          )
        : undefined,
    );
    renderAt(`${ROUTE}/observations/o-1`);
    await screen.findByLabelText("English communication");
    authFetch.mockClear();
    await user.click(
      screen.getByRole("button", { name: "Submit observation" }),
    );
    expect(
      screen.getByText("Rate at least one teaching area."),
    ).toBeInTheDocument();
    expect(authFetch.mock.calls.some(([, i]) => i?.method === "POST")).toBe(
      false,
    );
  });

  it("submits the ratings that were given and leaves the others out as Not observed", async () => {
    const user = userEvent.setup();
    mockApi((url, init) => {
      if (url.endsWith("/observations/o-1/submit") && init?.method === "POST")
        return json(observation({}));
      if (url.endsWith("/observations/o-1"))
        return json(
          observation(
            { edit: true, submit: true, notHeld: true },
            {
              status: "DRAFT",
              overallResult: null,
              ratings: {},
              strengths: null,
              averageRating: null,
              suggestedResult: null,
            },
          ),
        );
      return undefined;
    });
    renderAt(`${ROUTE}/observations/o-1`);
    await user.click(await screen.findByLabelText("Teaching ability"));
    await user.click(await screen.findByRole("option", { name: "5" }));
    await user.click(screen.getByLabelText("Overall result"));
    await user.click(await screen.findByRole("option", { name: "Good" }));
    await user.type(screen.getByLabelText("Strengths"), "Clear voice");
    await user.click(
      screen.getByRole("button", { name: "Submit observation" }),
    );
    await waitFor(() => {
      const call = authFetch.mock.calls.find(([u]) =>
        String(u).endsWith("/submit"),
      );
      expect(call).toBeTruthy();
      const sent = JSON.parse(call![1].body as string);
      expect(sent.ratings).toEqual({ TEACHING_ABILITY: 5 });
      expect(sent.overallResult).toBe("GOOD");
      expect(sent.permission).toBeNull();
      expect(sent.version).toBe(3);
    });
  });

  it("records a visit not held with the Principal declined reason", async () => {
    const user = userEvent.setup();
    mockApi((url, init) => {
      if (url.endsWith("/not-held") && init?.method === "POST")
        return json(
          observation(
            {},
            {
              status: "NOT_HELD",
              notHeldReason: "PRINCIPAL_DECLINED",
              notHeldReasonLabel: "Principal declined",
              principalDeclined: true,
            },
          ),
        );
      if (url.endsWith("/observations/o-1"))
        return json(
          observation(
            { edit: true, submit: true, notHeld: true },
            { status: "DRAFT" },
          ),
        );
      return undefined;
    });
    renderAt(`${ROUTE}/observations/o-1`);
    await user.click(
      await screen.findByRole("button", { name: "Visit not held" }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByLabelText("Why"));
    await user.click(
      await screen.findByRole("option", { name: "Principal declined" }),
    );
    await user.click(
      within(dialog).getByRole("button", { name: "Record visit not held" }),
    );
    expect(
      await screen.findByText(
        /The visit did not take place: Principal declined/,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Principal declined", { selector: ".MuiChip-label" }),
    ).toBeInTheDocument();
  });

  it("points a Needs improvement result at the corrective action", async () => {
    mockApi((url) =>
      url.endsWith("/observations/o-1")
        ? json(
            observation(
              {},
              {
                overallResult: "NEEDS_IMPROVEMENT",
                issueId: "i-1",
                issueStatus: "OPEN",
                nextStep: "CORRECTIVE_ACTION",
              },
            ),
          )
        : undefined,
    );
    renderAt(`${ROUTE}/observations/o-1`);
    expect(
      await screen.findByRole("link", { name: "Record a corrective action" }),
    ).toHaveAttribute("href", `${ROUTE}/issues/i-1`);
  });
});

describe("Issues", () => {
  it("shows only the buttons the server allows", async () => {
    mockApi((url) =>
      url.endsWith("/issues/i-1")
        ? json(issue({ correctiveAction: true, refer: true, escalate: true }))
        : undefined,
    );
    renderAt(`${ROUTE}/issues/i-1`);
    expect(await screen.findByText("Round 2")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Record corrective action" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Refer for transfer or replacement" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Cancel issue" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Close as continue" }),
    ).not.toBeInTheDocument();
    expect(screen.getByText(/still needs improvement/)).toBeInTheDocument();
  });

  it("gives management the cancel and close buttons its actions allow, and a Manager without Edit none", async () => {
    grant("DIRECTOR");
    mockApi((url) =>
      url.endsWith("/issues/i-1")
        ? json(
            issue(
              { cancel: true, closeContinue: true },
              { status: "REFERRED" },
            ),
          )
        : undefined,
    );
    const { unmount } = renderAt(`${ROUTE}/issues/i-1`);
    expect(
      await screen.findByRole("button", { name: "Cancel issue" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Close as continue" }),
    ).toBeInTheDocument();
    unmount();
    grant("MANAGER", ["VIEW"]);
    renderAt(`${ROUTE}/issues/i-1`);
    await screen.findByText("Round 2");
    expect(
      screen.queryByRole("button", { name: "Cancel issue" }),
    ).not.toBeInTheDocument();
  });

  it("checks the follow-up date and the referral reason before sending anything", async () => {
    const user = userEvent.setup();
    mockApi((url) =>
      url.endsWith("/issues/i-1")
        ? json(issue({ correctiveAction: true, refer: true }))
        : undefined,
    );
    renderAt(`${ROUTE}/issues/i-1`);
    await user.click(
      await screen.findByRole("button", { name: "Record corrective action" }),
    );
    let dialog = await screen.findByRole("dialog");
    await user.type(
      within(dialog).getByLabelText(/What was discussed/),
      "Talked it through",
    );
    await user.type(
      within(dialog).getByLabelText(/What must change/),
      "Check homework",
    );
    await user.type(
      within(dialog).getByLabelText(/Follow-up date/),
      "2099-01-01",
    );
    authFetch.mockClear();
    await user.click(
      within(dialog).getByRole("button", { name: "Record corrective action" }),
    );
    expect(
      within(dialog).getByText(
        /follow-up date must be 1 to 60 days from today/,
      ),
    ).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Close" }));

    await user.click(
      screen.getByRole("button", { name: "Refer for transfer or replacement" }),
    );
    dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByLabelText(/Recommendation/));
    await user.click(
      await screen.findByRole("option", { name: "Replace the Teacher" }),
    );
    await user.type(within(dialog).getByLabelText(/Reason/), "Too short");
    await user.click(within(dialog).getByRole("button", { name: "Refer" }));
    expect(
      within(dialog).getByText("The reason must be at least 20 characters."),
    ).toBeInTheDocument();
    expect(authFetch.mock.calls.some(([, i]) => i?.method === "POST")).toBe(
      false,
    );
  });

  it("lists the Open issues and the Referred issues from the server", async () => {
    const user = userEvent.setup();
    mockApi((url) =>
      url.includes("/issues?")
        ? json({
            content: [
              {
                id: "i-1",
                teacherId: "t-1",
                teacherName: "Asha Teacher",
                schoolId: "s-1",
                schoolName: "Green Valley School",
                category: "NEEDS_IMPROVEMENT",
                status: "UNDER_CORRECTION",
                resolution: null,
                correctionRounds: 2,
                openedOn: "2026-10-01",
                closedOn: null,
                managerName: "Meera Manager",
              },
            ],
            page: 0,
            size: 50,
            totalElements: 1,
          })
        : undefined,
    );
    renderAt(`${ROUTE}/issues`);
    expect(
      await screen.findByRole("link", { name: "01/10/2026" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Under correction")).toBeInTheDocument();
    authFetch.mockClear();
    await user.click(screen.getByRole("tab", { name: "Referred" }));
    await waitFor(() =>
      expect(
        authFetch.mock.calls.some(([u]) => String(u).includes("view=referred")),
      ).toBe(true),
    );
  });
});

describe("Planned observations", () => {
  const PLAN = {
    id: "p-1",
    teacherId: "t-1",
    teacherName: "Asha Teacher",
    schoolId: "s-1",
    schoolName: "Green Valley School",
    plannedOn: "2026-10-09",
    reason: "MANAGEMENT_PLAN",
    reasonLabel: "Management plan",
    reasonText: null,
    status: "OPEN",
    plannedBy: "u-2",
    plannedByName: "D. Director",
    sourceFeedbackRootId: null,
    issueId: null,
    managerUserId: "u-1",
    managerName: "Meera Manager",
    overdue: true,
    daysOverdue: 3,
    lastResult: "NEEDS_IMPROVEMENT",
    created: false,
    actions: { edit: true, cancel: true },
    version: 0,
  };

  it("shows days overdue and the last result, and asks for the Overdue list when that tab is chosen", async () => {
    const user = userEvent.setup();
    mockApi((url) => (url.includes("/plans") ? json([PLAN]) : undefined));
    renderAt(`${ROUTE}/planned`);
    expect(await screen.findByText("09/10/2026")).toBeInTheDocument();
    expect(screen.getByText("3 days overdue")).toBeInTheDocument();
    expect(screen.getByText("Needs improvement")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Change" })).toBeInTheDocument();
    authFetch.mockClear();
    await user.click(screen.getByRole("tab", { name: "Overdue" }));
    await waitFor(() =>
      expect(
        authFetch.mock.calls.some(([u]) => String(u).includes("view=overdue")),
      ).toBe(true),
    );
  });

  it("hides Change and Cancel plan when the plan does not allow them, and Plan an observation without Create", async () => {
    grant("MANAGER", ["VIEW"]);
    mockApi((url) =>
      url.includes("/plans")
        ? json([{ ...PLAN, actions: { edit: false, cancel: false } }])
        : undefined,
    );
    renderAt(`${ROUTE}/planned`);
    await screen.findByText("09/10/2026");
    expect(
      screen.queryByRole("button", { name: "Change" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Cancel plan" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Plan an observation" }),
    ).not.toBeInTheDocument();
  });
});

describe("Principal feedback", () => {
  const ROW_F = {
    id: "f-1",
    rootId: "f-1",
    teacherId: "t-1",
    teacherName: "Asha Teacher",
    schoolId: "s-1",
    schoolName: "Green Valley School",
    principalName: "Mrs Rao",
    givenOn: "2026-10-10",
    category: "TEACHING",
    categoryLabel: "Teaching issue",
    actionRequired: true,
    status: "RECORDED",
    issueId: null,
  };

  function feedbackView(actions: Record<string, boolean>, patch = {}) {
    return {
      ...ROW_F,
      versionNo: 1,
      current: true,
      comments: "Homework is not being checked",
      observationRootId: null,
      recordedBy: "u-1",
      recordedByName: "Meera Manager",
      withdrawnReason: null,
      withdrawnAt: null,
      issueStatus: null,
      offerIssue: true,
      correctionReason: null,
      actions: {
        correct: false,
        withdraw: false,
        openIssue: false,
        planObservation: false,
        ...actions,
      },
      version: 2,
      ...patch,
    };
  }

  it("lists feedback with the action-required flag", async () => {
    mockApi((url) =>
      url.includes("/feedback?")
        ? json({ content: [ROW_F], page: 0, size: 50, totalElements: 1 })
        : undefined,
    );
    renderAt(`${ROUTE}/feedback`);
    expect(await screen.findByText("Action required")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "10/10/2026" }),
    ).toBeInTheDocument();
  });

  it("offers an issue and a plan when action is required, and hides Withdraw when it is not allowed", async () => {
    const user = userEvent.setup();
    mockApi((url) => {
      if (url.includes("/feedback?"))
        return json({ content: [ROW_F], page: 0, size: 50, totalElements: 1 });
      if (url.endsWith("/feedback/f-1"))
        return json(feedbackView({ openIssue: true, planObservation: true }));
      return undefined;
    });
    renderAt(`${ROUTE}/feedback`);
    await user.click(await screen.findByRole("button", { name: "10/10/2026" }));
    const dialog = await screen.findByRole("dialog");
    expect(
      await within(dialog).findByRole("button", {
        name: "Open performance issue",
      }),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole("button", { name: "Plan an observation" }),
    ).toBeInTheDocument();
    expect(
      within(dialog).queryByRole("button", { name: "Withdraw" }),
    ).not.toBeInTheDocument();
  });

  it("shows Withdraw only to someone the server allows and asks for a reason", async () => {
    const user = userEvent.setup();
    mockApi((url) => {
      if (url.includes("/feedback?"))
        return json({ content: [ROW_F], page: 0, size: 50, totalElements: 1 });
      if (url.endsWith("/feedback/f-1"))
        return json(feedbackView({ withdraw: true }, { offerIssue: false }));
      return undefined;
    });
    renderAt(`${ROUTE}/feedback`);
    await user.click(await screen.findByRole("button", { name: "10/10/2026" }));
    await user.click(await screen.findByRole("button", { name: "Withdraw" }));
    const reason = (await screen.findAllByRole("dialog")).at(-1)!;
    authFetch.mockClear();
    await user.click(within(reason).getByRole("button", { name: "Withdraw" }));
    expect(
      within(reason).getByText("Write at least 5 characters."),
    ).toBeInTheDocument();
    expect(authFetch).not.toHaveBeenCalled();
  });
});

describe("Teacher page", () => {
  it("lists the history with the latest result first and a planned observation", async () => {
    mockApi((url) =>
      url.includes("/teachers/t-1")
        ? json({
            teacherId: "t-1",
            teacherName: "Asha Teacher",
            status: "ACTIVE",
            schoolId: "s-1",
            schoolName: "Green Valley School",
            managerName: "Meera Manager",
            latestResult: "GOOD",
            openPlan: { plannedOn: "2026-11-01" },
            entries: [
              {
                type: "OBSERVATION",
                id: "o-2",
                date: "2026-10-12",
                title: "Observation",
                result: "GOOD",
                status: "SUBMITTED",
              },
              {
                type: "ISSUE",
                id: "i-1",
                date: "2026-10-01",
                title: "Performance issue: NEEDS_IMPROVEMENT",
                result: null,
                status: "RESOLVED",
              },
            ],
            observations: [],
            feedback: [],
            issues: [],
          })
        : undefined,
    );
    renderAt(`${ROUTE}/teachers/t-1`);
    expect(
      await screen.findByRole("heading", { name: "Asha Teacher: performance" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Observation planned for 01/11/2026"),
    ).toBeInTheDocument();
    const items = within(
      screen.getByRole("list", { name: "Performance history" }),
    ).getAllByRole("listitem");
    expect(items[0]).toHaveTextContent("Observation");
    expect(items[0]).toHaveTextContent("12/10/2026");
    expect(items[1]).toHaveTextContent("Performance issue");
    expect(
      screen.getByRole("button", { name: "Start observation" }),
    ).toBeInTheDocument();
  });

  it("shows a not-found style error when the server answers 404", async () => {
    mockApi((url) =>
      url.includes("/teachers/t-9")
        ? json({ reason: "Teacher not found." }, 404)
        : undefined,
    );
    renderAt(`${ROUTE}/teachers/t-9`);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Teacher not found.",
    );
  });
});
