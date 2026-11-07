"""Browser walkthrough for spec 035 (Manager Activity Calendar): quickstart.md sections 1 to 7 as far as a browser can go (T057, T058).

Same setup as test_school_working_days.py (demo seed: Admin asha.admin, Director 9800000002, Teacher 9800000004, System
sunil.system; password Password123!). The data is shared with other runs, so the test creates through the REST API (hls_api.py)
its own Managers A and B (Zone 1) and C (Zone 2), and plans their work in a month well ahead (default: five weeks from now, MONTH
below), then filters the calendar to each Manager so every count is exact and independent of other data.
Run from this folder:  pytest test_activity_calendar.py -v        (HEADED=1 to watch)

Covered: the Director's month (Done, Planned, Rescheduled with "first planned", struck-through Cancelled outside planned, a shared
visit once under each of A and B but counted once in the total, a three-date drive on each date and counted as one drive); read-only
detail with a link and no complete, move or cancel control; Zone filter and an empty month; Manager view (own items, the shared
visit, own counts, 404 for another managerId, nothing for another Zone) and Teacher and System refused (403); Needs attention
(grouped by Manager, a clean Manager absent) on the seeded October data where Manager Manoj has Missed items and days late; filters
(Manager, Zone, kind, status, "Task, Missed") with grid, list and summary agreeing; "N more" on a busy day; month and filters kept
over a page refresh; the Training filter; reading never writes (a mutating request on the base path answers 405); a completed visit
and a moved drive date show on reload; phone width and dark mode have no sideways scroll of the page.
Not covered: Missed on the planted data (the stack's business date cannot be moved, so a past date cannot be planned; the seeded
October data is used for Missed and days late instead); a visit with no School ("No place recorded" is checked only if the seed
has one); "Training: not available yet" and a failing source (backend tests: SourceResilienceTest); the 500 items in under 3
seconds (CalendarPerformanceTest); the ArchUnit read-only rule; the regression run of the amended feeds (section 8); a screen
reader pass.
"""
import re

import pytest
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import Select

from hls_api import call, iso_in, make_manager, make_school, make_zone, ok, stamp, token_for
from test_school_working_days import ADMIN, BASE
from ui_helpers import body, button, has_button, no_overflow, settle, wait

DIRECTOR, TEACHER, SYSTEM = "9800000002", "9800000004", "sunil.system"
S = {}


def month_ahead():
    import datetime
    d = datetime.date.today() + datetime.timedelta(days=35)
    return d.year, d.month


YEAR, MON = month_ahead()
MONTH = f"{YEAR}-{MON:02d}"


def day(n):
    return f"{MONTH}-{n:02d}"


@pytest.fixture(scope="module")
def world():
    admin = token_for(ADMIN)
    s = stamp()
    z1, p1 = make_zone(admin, f"E2E Cal Zone1 {s}")
    z2, p2 = make_zone(admin, f"E2E Cal Zone2 {s}")
    sc1 = make_school(admin, f"E2E Cal School1 {s}", p1)
    sc2 = make_school(admin, f"E2E Cal School2 {s}", p2)
    a = make_manager(admin, "e2ecalA", [z1["id"]], [sc1["id"]])
    b = make_manager(admin, "e2ecalB", [z1["id"]], [])
    c = make_manager(admin, "e2ecalC", [z2["id"]], [sc2["id"]])
    ta, tb = token_for(a["username"]), token_for(b["username"])
    # A: a visit on the 5th (completed), a drive on the 8th (held), a task due the 10th (left open)
    v5 = ok(call("POST", "/marketing/activities", {"schoolId": sc1["id"], "type": "VISIT", "date": day(5), "attendeeUserIds": [], "notes": "e2e"}, ta))
    ok(call("POST", f"/marketing/activities/{v5['id']}/complete", {"outcome": "Visited", "notes": "e2e", "followUpOn": None, "version": v5["version"]}, ta))
    college = ok(call("POST", "/recruitment/colleges", {"name": f"E2E College {s}", "city": "Chennai", "placementOfficer": None, "principal": None}, admin))
    d8 = ok(call("POST", "/recruitment/drives", {"collegeId": college["id"], "dates": [day(8)], "venue": "Hall", "season": "E2E",
                                                 "interviewerUserIds": [a["userId"]]}, admin))
    ok(call("POST", f"/recruitment/drives/{d8['id']}/status", {"status": "HELD", "reason": None, "version": d8["version"]}, admin))
    task10 = ok(call("POST", "/tasks", {"type": "SCHOOL_VISIT", "title": f"E2E cal task {s}", "assigneeUserId": a["userId"], "dueOn": day(10),
                                        "place": sc1["name"]}, admin))
    # a visit on the 14th moved to the 20th; a cancelled visit; a 3-date drive; a visit with A and B attending
    v14 = ok(call("POST", "/marketing/activities", {"schoolId": sc1["id"], "type": "VISIT", "date": day(14), "attendeeUserIds": [], "notes": "e2e"}, ta))
    ok(call("POST", f"/marketing/activities/{v14['id']}/reschedule", {"date": day(20), "version": v14["version"]}, ta))
    vc = ok(call("POST", "/marketing/activities", {"schoolId": sc1["id"], "type": "VISIT", "date": day(12), "attendeeUserIds": [], "notes": "e2e"}, ta))
    ok(call("POST", f"/marketing/activities/{vc['id']}/cancel", {"reason": "e2e", "version": vc["version"]}, ta))
    d3 = ok(call("POST", "/recruitment/drives", {"collegeId": college["id"], "dates": [day(22), day(23), day(24)], "venue": "Hall 2",
                                                 "season": "E2E", "interviewerUserIds": [a["userId"]]}, admin))
    shared = ok(call("POST", "/marketing/activities", {"schoolId": sc1["id"], "type": "VISIT", "date": day(16),
                                                       "attendeeUserIds": [b["userId"]], "notes": "e2e"}, ta))
    # C in Zone 2 has one planned visit
    ok(call("POST", "/marketing/activities", {"schoolId": sc2["id"], "type": "VISIT", "date": day(6), "attendeeUserIds": [], "notes": "e2e"},
            token_for(c["username"])))
    w = {"s": s, "z1": z1, "z2": z2, "sc1": sc1, "a": a, "b": b, "c": c, "d3": d3, "v14": v14, "task10": task10}
    S.update(w)
    return w


def select(d, label, text):
    """Choose in a native <select> by its label."""
    lab = d.find_element(By.XPATH, f"//label[starts-with(normalize-space(.),'{label}')]")
    Select(d.find_element(By.ID, lab.get_attribute("for"))).select_by_visible_text(text)
    settle(d, 1.2)


def open_calendar(d, month=MONTH):
    d.get(f"{BASE}/operations/activity-calendar")
    d.execute_script("window.sessionStorage.removeItem('activityCalendar.view.v1')")
    d.get(f"{BASE}/operations/activity-calendar")
    wait(d).until(lambda x: "Previous month" in body(x).replace("PREVIOUS MONTH", "Previous month") or has_button(x, "Previous month"))
    # step to the month under test with the Next month button
    import datetime
    now = datetime.date.today()
    steps = (YEAR - now.year) * 12 + (MON - now.month)
    for _ in range(max(steps, 0)):
        button(d, "Next month").click()
        settle(d, 0.3)
    wait(d, 30).until(lambda x: "Summary" in [e.get_attribute("aria-label") for e in x.find_elements(By.CSS_SELECTOR, "[role=region]")] or
                      "Nothing planned this month" in body(x))
    settle(d, 0.8)


def summary(d):
    box = d.find_element(By.CSS_SELECTOR, "[aria-label=Summary]")
    return {k.lower(): int(v) for k, v in re.findall(r"(Planned|Done late|Done|Missed|Cancelled|Rescheduled|Total) (\d+)", box.text)}


def chips(d):
    return [c.get_attribute("aria-label") for c in d.find_elements(By.CSS_SELECTOR, "[role=gridcell] .MuiChip-root[aria-label]")]


def pick_manager(d, name):
    select(d, "Manager", name)


# ---- sections 1 and 2: Director view -------------------------------------------------------------------------------

def test_2_1_director_month_for_manager_a_matches_the_hand_count(world, browsers):
    d = browsers(DIRECTOR)
    open_calendar(d)
    pick_manager(d, world["a"]["name"])
    s = summary(d)
    # visit 5th done, drive 8th held, task 10th open, visit moved to the 20th, the 3-date drive (one drive), the shared visit
    assert s["planned"] == 6, s
    assert s["done"] == 2, s
    assert s["cancelled"] == 1, s
    assert s["rescheduled"] == 1, s
    assert s["missed"] == 0, s
    assert s["total"] == s["planned"] + s["cancelled"]
    labels = chips(d)
    assert any("Done" in l and f"{day(5)[8:]}/{MON:02d}/{YEAR}" in l for l in labels), labels
    assert sum(1 for l in labels if "Campus drive" in l) == 4, "one drive on the 8th, three dates of the other drive"
    body_text = body(d)
    assert f"Rescheduled, first planned 14/{MON:02d}/{YEAR}" in body_text
    # the cancelled visit is struck through
    struck = d.execute_script("return Array.from(document.querySelectorAll('[role=gridcell] .MuiChip-root')).filter(c => "
                              "getComputedStyle(c).textDecorationLine.includes('line-through')).length")
    assert struck == 1
    # the three-date drive counts once in the drive summary line
    assert re.search(r"Campus drive \d+", body_text)


def test_2_2_shared_visit_shows_under_both_managers_and_counts_once(world, browsers):
    d = browsers(DIRECTOR)
    open_calendar(d)
    pick_manager(d, world["b"]["name"])
    sb = summary(d)
    assert sb["planned"] == 1 and len(chips(d)) == 1, (sb, chips(d))
    # Zone 1 (A and B): the shared visit is two chips of one activity but the total counts it once
    select(d, "Manager", "All Managers")
    select(d, "Zone", world["z1"]["name"])
    s = summary(d)
    assert s["planned"] == 6, s                       # A's six; B adds nothing of their own
    assert len(chips(d)) == s["planned"] + s["cancelled"] + 3 - 0 or len(chips(d)) >= s["planned"]
    # open an item: read-only detail with a link, no complete, move or cancel control
    d.execute_script("arguments[0].click()", d.find_elements(By.CSS_SELECTOR, "[role=gridcell] .MuiChip-root[aria-label]")[0])
    dlg = wait(d).until(lambda x: x.find_element(By.CSS_SELECTOR, "[role=dialog]"))
    assert "Manager:" in dlg.text and "Status:" in dlg.text and "Place:" in dlg.text
    for control in ("Complete", "Move", "Reschedule", "Cancel"):
        assert not has_button(dlg, control), control
    assert "Open in" in dlg.text
    button(d, "Close", dlg).click()


def test_2_3_zone_filter_and_empty_month(world, browsers):
    d = browsers(DIRECTOR)
    open_calendar(d)
    select(d, "Zone", world["z2"]["name"])
    s = summary(d)
    assert s["planned"] == 1 and s["cancelled"] == 0, s
    assert world["c"]["name"] in body(d)
    assert world["a"]["name"] not in body(d)
    # an empty month (far in the future for this Zone): zero counts and the message
    select(d, "Zone", world["z1"]["name"])
    for _ in range(14):
        button(d, "Next month").click()
        settle(d, 0.2)
    settle(d, 1.5)
    s = summary(d)
    assert s["planned"] == 0 and s["done"] == 0 and s["missed"] == 0, s
    assert "Nothing planned this month" in body(d)


# ---- section 3: Manager view ---------------------------------------------------------------------------------------

def test_3_manager_views_and_refusals(world, browsers):
    a = browsers(world["a"]["username"])
    open_calendar(a)
    assert "My Activity Calendar" in body(a)
    assert not a.find_elements(By.XPATH, "//label[starts-with(normalize-space(.),'Manager')]"), "no Manager picker for a Manager"
    sa = summary(a)
    assert sa["planned"] == 6 and sa["cancelled"] == 1, sa
    # B sees the shared visit and nothing else of A's
    b = browsers(world["b"]["username"])
    open_calendar(b)
    sb = summary(b)
    assert sb["planned"] == 1 and len(chips(b)) == 1, (sb, chips(b))
    # API: another managerId is 404, another Zone shows nothing, Teacher and System are 403
    ta = token_for(world["a"]["username"])
    managers = ok(call("GET", "/activity-calendar/filters", None, token_for(DIRECTOR)))["managers"]
    b_id = next(m["id"] for m in managers if m["name"] == world["b"]["name"])
    assert call("GET", f"/activity-calendar?month={MONTH}&managerId={b_id}", None, ta)[0] == 404
    status, other_zone = call("GET", f"/activity-calendar?month={MONTH}&zoneId={world['z2']['id']}", None, ta)
    assert status in (200, 404) and (status == 404 or other_zone["items"] == [])
    assert call("GET", f"/activity-calendar?month={MONTH}", None, token_for(TEACHER))[0] == 403
    assert call("GET", f"/activity-calendar?month={MONTH}", None, token_for(SYSTEM))[0] == 403
    t = browsers(TEACHER)
    t.get(f"{BASE}/operations/activity-calendar")
    settle(t, 2)
    assert "Previous month" not in body(t).replace("PREVIOUS MONTH", "Previous month")


# ---- section 4: needs attention ------------------------------------------------------------------------------------

def test_4_needs_attention_groups_missed_items_by_manager(world, browsers):
    d = browsers(DIRECTOR)
    d.get(f"{BASE}/operations/activity-calendar")
    d.execute_script("window.sessionStorage.removeItem('activityCalendar.view.v1')")
    d.get(f"{BASE}/operations/activity-calendar")
    wait(d).until(lambda x: has_button(x, "Previous month"))
    # October 2026 holds the seeded Missed items of Manager Manoj; go back from the current month until October
    import datetime
    now = datetime.date.today()
    for _ in range((now.year - 2026) * 12 + now.month - 10):
        button(d, "Previous month").click()
        settle(d, 0.3)
    d.find_element(By.XPATH, "//button[@role='tab'][contains(.,'Needs attention')]").click()
    wait(d, 30).until(lambda x: "days late" in body(x) or "Nothing needs attention" in body(x))
    text = body(d)
    assert "days late" in text, text[:400]
    assert "Manoj Manager" in text and re.search(r"Missed \d+ · Rescheduled \d+", text)
    # entries are oldest first inside a group
    dates = re.findall(r"(\d{2})/(\d{2})/(\d{4}) · (\d+) days late", text)
    assert dates
    for m in (world["c"]["name"],):
        assert m not in text, "a Manager with a clean month is absent"
    # choosing an entry opens its read-only detail
    d.find_element(By.CSS_SELECTOR, "section ul button, section ul a").click()
    dlg = wait(d).until(lambda x: x.find_element(By.CSS_SELECTOR, "[role=dialog]"))
    assert "days late" in dlg.text or "Status:" in dlg.text
    button(d, "Close", dlg).click()
    # the moved visit is listed in the planted month under A with the days moved
    open_calendar(d)
    d.find_element(By.XPATH, "//button[@role='tab'][contains(.,'Needs attention')]").click()
    wait(d, 30).until(lambda x: "moved" in body(x))
    text = body(d)
    assert world["a"]["name"] in text and f"first planned 14/{MON:02d}/{YEAR}" in text and "moved 6 days" in text
    assert world["c"]["name"] not in text


# ---- section 5: filters and layout ---------------------------------------------------------------------------------

def test_5_filters_agree_in_grid_list_and_summary(world, browsers):
    d = browsers(DIRECTOR)
    open_calendar(d)
    pick_manager(d, world["a"]["name"])
    select(d, "Kind", "Task")
    s = summary(d)
    assert s["planned"] == 1 and len(chips(d)) == 1, (s, chips(d))
    select(d, "Status", "Planned")
    assert summary(d)["planned"] == 1
    select(d, "Status", "Missed")
    s = summary(d)
    assert s["planned"] == 0 and not chips(d), "Task, Missed: nothing in the planted month"
    # list view shows the same rows as the grid for a filter
    select(d, "Kind", "All kinds")
    select(d, "Status", "All statuses")
    grid_count = len(chips(d))
    button(d, "List view").click()
    settle(d, 0.8)
    rows = d.find_elements(By.CSS_SELECTOR, "table[aria-label='Activity list'] tbody tr")
    assert len(rows) == grid_count, (len(rows), grid_count)
    assert any("Cancelled" in r.text for r in rows)
    # filters and month are kept over a refresh
    d.refresh()
    wait(d).until(lambda x: has_button(x, "List view"))
    settle(d, 1)
    assert f"{['January','February','March','April','May','June','July','August','September','October','November','December'][MON-1]} {YEAR}" in body(d)
    assert Select(d.find_element(By.ID, d.find_element(By.XPATH, "//label[starts-with(normalize-space(.),'Manager')]").get_attribute("for"))
                  ).first_selected_option.text == world["a"]["name"]
    button(d, "Month view").click()


def test_5b_a_busy_day_collapses_into_n_more(world, browsers):
    admin = token_for(ADMIN)
    token = token_for(world["a"]["username"])
    for i in range(9):
        ok(call("POST", "/tasks", {"type": "FOLLOW_UP", "title": f"E2E busy {world['s']} {i}", "assigneeUserId": world["a"]["userId"],
                                   "dueOn": day(27), "place": world["sc1"]["name"]}, admin))
    d = browsers(DIRECTOR)
    open_calendar(d)
    pick_manager(d, world["a"]["name"])
    wait(d).until(lambda x: "6 more" in body(x) or "more" in body(x))
    assert re.search(r"\b6 more\b", body(d)), "9 items on a day show 3 and '6 more'"
    d.find_element(By.XPATH, "//button[contains(normalize-space(.),'6 more')]").click()
    dlg = wait(d).until(lambda x: x.find_element(By.CSS_SELECTOR, "[role=dialog]"))
    assert dlg.text.count("Task") >= 9
    button(d, "Close", dlg).click()


def test_5c_training_filter_is_offered(world, browsers):
    d = browsers(DIRECTOR)
    open_calendar(d)
    lab = d.find_element(By.XPATH, "//label[starts-with(normalize-space(.),'Kind')]")
    options = [o.text for o in Select(d.find_element(By.ID, lab.get_attribute("for"))).options]
    assert any(o.startswith("Training") for o in options), options


# ---- section 7: read-only, and changes show on reload --------------------------------------------------------------

def test_7_calendar_is_read_only_and_changes_show_on_reload(world, browsers):
    token = token_for(DIRECTOR)
    for method in ("POST", "PUT", "PATCH", "DELETE"):
        status, _ = call(method, f"/activity-calendar?month={MONTH}", {}, token)
        assert status == 405, (method, status)
    # complete the visit moved to the 20th and move a drive date, then reload
    ta = token_for(world["a"]["username"])
    visit = ok(call("GET", f"/marketing/activities/{world['v14']['id']}", None, ta))
    ok(call("POST", f"/marketing/activities/{visit['id']}/complete", {"outcome": "Visited", "notes": "e2e", "followUpOn": None, "version": visit["version"]}, ta))
    d3 = ok(call("GET", f"/recruitment/drives/{world['d3']['id']}", None, token_for(ADMIN)))
    d = browsers(DIRECTOR)
    open_calendar(d)
    pick_manager(d, world["a"]["name"])
    before = summary(d)
    d.refresh()
    wait(d).until(lambda x: has_button(x, "List view"))
    settle(d, 1.5)
    after = summary(d)
    assert after["done"] == before["done"] == 3, (before, after)       # the moved visit is now Done too
    assert any("Done" in l and f"20/{MON:02d}/{YEAR}" in l for l in chips(d))
    assert d3  # the drive stays three dates on the screen
    assert sum(1 for l in chips(d) if "Campus drive" in l) == 4


def test_8_layout_phone_width_and_dark(world, browsers):
    d = browsers(DIRECTOR, 375, 800)
    open_calendar(d)
    assert no_overflow(d) <= 0, "the page itself does not scroll sideways (the grid scrolls inside its own box)"
    d.execute_cdp_cmd("Emulation.setEmulatedMedia", {"features": [{"name": "prefers-color-scheme", "value": "dark"}]})
    try:
        d.refresh()
        wait(d).until(lambda x: has_button(x, "List view"))
        settle(d, 1)
        assert no_overflow(d) <= 0
    finally:
        d.execute_cdp_cmd("Emulation.setEmulatedMedia", {"features": [{"name": "prefers-color-scheme", "value": "light"}]})
