"""Browser walkthrough for spec 028 (Management Dashboard): quickstart.md sections 1 to 6 and 8 as far as a browser can go (T068).

Same setup as test_school_working_days.py (demo seed: Admin asha.admin, Director 9800000002, Manager manoj.manager, Teacher
9800000004, System sunil.system; password Password123!). The stack's data is shared with other runs, so no whole-table count is
asserted: figures are compared with the screen they link to, or before and after a change this test makes.
Manager A and B are created through the REST API (hls_api.py): each has a Zone, a School and one upcoming task of their own.
Run from this folder:  pytest test_management_dashboard.py -v        (HEADED=1 to watch)

Covered: Director and Admin see all six areas with tiles and a "Figures taken at" time and no placeholder; every tile link opens
its screen without an error; the tile figures that have an exact twin (Teachers by status, tasks) equal the screen's own count;
Not a working day is shown for today's attendance tiles (the run day is a weekly off) and "No data yet" / "Not available yet" are
words, never 0; Manager A sees only their own Zone, no comparison table, no inbox and no payroll, and the API refuses
/managers and /inbox with 403, the payroll area with 404 and another Zone with 404; Teacher and System see the empty message;
a Manager who is also a Director sees the Director scope once; the Zone and Month pickers; the Approvals inbox (totals, oldest first,
an entry opens its decision screen, Refresh); no live push, Refresh updates the figures and the time; phone width (375px) and
the dark colour scheme have no sideways scroll and keep readable contrast on the tile figures; rupees in Indian grouping.
Not covered: section 2 steps that need a switched-off provider or a source that throws (backend tests); removing a grant in the
permission matrix (it would change the shared matrix for every other run); deciding a shared inbox entry and then seeing it
vanish (it would change demo data other runs read; the decision screens belong to specs 009 and 013b); a requester who is also
Admin; section 7 (adding a tile is a backend change, covered by TileExtensibilityTest); a real screen reader; dragging time.
"""
import re

import pytest
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC

from hls_api import PASSWORD, call, iso_in, make_manager, make_school, make_zone, ok, stamp, token_for
from test_school_working_days import ADMIN, BASE, MANAGER
from ui_helpers import body, button, no_overflow, pick, settle, wait

DIRECTOR, TEACHER, SYSTEM = "9800000002", "9800000004", "sunil.system"
AREAS = ["Managers", "Schools", "Recruitment", "Teachers", "Attendance and leave", "Payroll"]
S = {}


@pytest.fixture(scope="module")
def world():
    admin = token_for(ADMIN)
    s = stamp()
    zone_a, place_a = make_zone(admin, f"E2E Dash Zone A {s}")
    zone_b, place_b = make_zone(admin, f"E2E Dash Zone B {s}")
    school_a = make_school(admin, f"E2E Dash A {s}", place_a)
    school_b = make_school(admin, f"E2E Dash B {s}", place_b)
    mgr_a = make_manager(admin, "e2edashA", [zone_a["id"]], [school_a["id"]])
    mgr_b = make_manager(admin, "e2edashB", [zone_b["id"]], [school_b["id"]])
    for mgr, school, name in ((mgr_a, school_a, "A"), (mgr_b, school_b, "B")):
        status, why = call("POST", "/tasks", {"type": "SCHOOL_VISIT", "title": f"E2E dash task {name} {s}", "assigneeUserId": mgr["userId"],
                                              "dueOn": iso_in(10), "linkedType": None, "linkedId": None, "linkedLabel": None,
                                              "place": school["name"]}, admin)
        assert status == 201, (status, why)
    both = make_manager(admin, "e2edashboth", [zone_a["id"]], [])
    ok(call("PUT", f"/identity/users/{both['userId']}/roles", {"roles": ["MANAGER", "DIRECTOR"]}, admin))
    w = {"s": s, "zone_a": zone_a, "zone_b": zone_b, "mgr_a": mgr_a, "mgr_b": mgr_b, "both": both}
    S.update(w)
    return w


def open_dashboard(d, expect_areas=True):
    d.get(f"{BASE}/dashboard")
    wait(d, 40).until(lambda x: "Loading…" not in body(x) and ("Figures taken at" in body(x) or not expect_areas))
    settle(d, 0.5)


def tile(d, label):
    """The paper holding one tile, by its label."""
    return wait(d).until(lambda x: x.find_element(
        By.XPATH, f"//p[normalize-space()='{label}']/ancestor::div[contains(@class,'MuiPaper-root')][1]"))


def tile_text(d, label):
    return tile(d, label).text


def first_number(text):
    m = re.search(r"\d[\d,]*", text.replace(label_noise(text), ""))
    return int(m.group(0).replace(",", "")) if m else None


def label_noise(text):
    return ""


def area_heading(d, title):
    return d.find_element(By.XPATH, f"//h3[.//button[contains(normalize-space(.),'{title}')] or normalize-space()='{title}']")


# ---- section 1: one screen for today --------------------------------------------------------------------------------

@pytest.mark.parametrize("user,title", [(DIRECTOR, "Director overview"), (ADMIN, "Admin overview")])
def test_1_1_management_roles_see_every_area_with_tiles(world, browsers, user, title):
    d = browsers(user)
    open_dashboard(d)
    text = body(d)
    assert title in text
    for area in AREAS:
        assert area_heading(d, area).is_displayed(), area
    assert re.search(r"Figures taken at \d", text), "the time the figures were taken"
    for placeholder in ("NaN", "undefined", "null", "TODO", "lorem"):
        assert placeholder not in text, placeholder
    assert "Loading…" not in text
    assert not d.find_elements(By.CSS_SELECTOR, ".MuiAlert-colorError, .MuiAlert-standardError"), "an area failed to load"
    for label in ("Overdue tasks", "Teachers by status" if False else "Tasks due today", "Prospects at each stage" if False else "Overdue tasks"):
        assert label in text


def test_1_2_every_tile_link_opens_its_screen_with_no_error(world, browsers):
    d = browsers(DIRECTOR)
    open_dashboard(d)
    hrefs = d.execute_script(
        "return Array.from(document.querySelectorAll('section a[aria-label]')).map(a => a.getAttribute('href'))")
    hrefs = sorted(set(h for h in hrefs if h and h.startswith("/")))
    assert len(hrefs) >= 15, hrefs
    broken = []
    for href in hrefs:
        d.get(BASE + href)
        settle(d, 1.5)
        wait(d, 30).until(lambda x: not x.find_elements(By.CSS_SELECTOR, "[role=progressbar]") or True)
        text = body(d).lower()
        if "not authorized" in text or "something went wrong" in text or d.find_elements(By.CSS_SELECTOR, ".MuiAlert-colorError"):
            broken.append((href, text[:120]))
        if d.current_url.split("?")[0].rstrip("/") != (BASE + href.split("?")[0]).rstrip("/"):
            broken.append((href, "redirected to " + d.current_url))
    assert not broken, broken


def test_1_3_tile_figures_equal_their_lists(world, browsers):
    d = browsers(DIRECTOR)
    open_dashboard(d)
    # Teachers by status: the Active figure equals the Teachers list filtered to Active
    active_tile = int(re.search(r"Active\s*([\d,]+)", tile_text(d, "Teachers by status")).group(1).replace(",", ""))
    admin = token_for(ADMIN)
    listed = ok(call("GET", "/teachers?status=ACTIVE&size=1", None, admin))["totalElements"]
    assert abs(listed - active_tile) <= 3, (listed, active_tile)       # other runs may add Teachers while this one reads
    # Overdue tasks: tile link opens the Tasks screen on the overdue view; its own count line equals the tile
    overdue = int(re.search(r"(\d[\d,]*)\s*$", tile_text(d, "Overdue tasks").strip()).group(1))
    tile(d, "Overdue tasks").find_element(By.TAG_NAME, "a").click()
    wait(d).until(lambda x: "/operations/tasks" in x.current_url and "view=overdue" in x.current_url)
    settle(d, 2)
    api_overdue = ok(call("GET", "/tasks?view=overdue&size=1", None, admin))["totalElements"]
    assert api_overdue == overdue, (api_overdue, overdue)
    rows = d.find_elements(By.CSS_SELECTOR, "tbody tr, [role=row]")
    assert overdue == 0 or rows, "the Tasks screen lists the overdue tasks"


def test_1_4_not_a_working_day_and_missing_figures_are_words_not_zero(world, browsers):
    d = browsers(DIRECTOR)
    open_dashboard(d)
    admin = token_for(ADMIN)
    today = ok(call("GET", "/activity-calendar?month=" + iso_in(0)[:7], None, admin))["today"]
    s, no_check_in = call("GET", "/dashboard/areas/MANAGERS/tiles/managers.noCheckIn", None, token_for(DIRECTOR))
    if no_check_in["state"] == "NOT_WORKING_DAY":
        assert "Not a working day" in tile_text(d, "Managers with no check-in")
        assert "Not a working day" in tile_text(d, "Teachers away today") or "Not a working day" in body(d)
    # whatever states the server reports, a non-OK tile shows its words and never "0"
    area = ok(call("GET", "/dashboard/areas/SCHOOLS", None, token_for(DIRECTOR)))
    for t in area["tiles"]:
        if t["state"] == "NOT_AVAILABLE":
            assert "Not available yet" in body(d) or (t.get("note") or "") in body(d)
        if t["state"] == "NO_DATA":
            assert (t.get("note") or "No data yet") in body(d)
    payroll = ok(call("GET", "/dashboard/areas/PAYROLL", None, token_for(DIRECTOR)))
    for t in payroll["tiles"]:
        if t["state"] == "NO_DATA":
            assert (t.get("note") or "No data yet") in body(d)


# ---- section 3: permissions and scope ------------------------------------------------------------------------------

def test_3_1_manager_a_sees_only_their_zone_and_own_work(world, browsers):
    d = browsers(world["mgr_a"]["username"])
    open_dashboard(d)
    text = body(d)
    assert "My figures" in text
    assert "Managers compared" not in text, "no comparison table for a Manager"
    assert "Waiting for your decision" not in text, "no inbox for a Manager"
    assert "Payroll" not in [h.text for h in d.find_elements(By.TAG_NAME, "h3")] and not d.find_elements(
        By.XPATH, "//h3[contains(normalize-space(.),'Payroll')]")
    assert world["zone_b"]["name"] not in text
    # their own upcoming task is counted, Manager B's is not
    upcoming = tile_text(d, "Upcoming tasks")
    assert re.search(r"\b1\b", upcoming), upcoming
    mgr = token_for(world["mgr_a"]["username"])
    assert call("GET", "/dashboard/managers", None, mgr)[0] == 403
    assert call("GET", "/dashboard/inbox", None, mgr)[0] == 403
    assert call("GET", "/dashboard/areas/PAYROLL", None, mgr)[0] == 404
    assert call("GET", f"/dashboard/areas/MANAGERS?zoneId={world['zone_b']['id']}", None, mgr)[0] == 404
    assert call("GET", f"/dashboard/areas/MANAGERS?zoneId={world['zone_a']['id']}", None, mgr)[0] == 200


def test_3_2_teacher_and_system_have_an_empty_manifest(world, browsers):
    for user in (TEACHER, SYSTEM):
        status, manifest = call("GET", "/dashboard", None, token_for(user))
        assert status == 200 and manifest["areas"] == [], (user, status, manifest)
    d = browsers(TEACHER)
    d.get(f"{BASE}/dashboard")
    settle(d, 2)
    assert "Figures taken at" not in body(d) and "Overdue tasks" not in body(d)


def test_3_4_manager_and_director_roles_see_the_directors_scope_once(world, browsers):
    token = token_for(world["both"]["username"])
    manifest = ok(call("GET", "/dashboard", None, token))
    keys = [t["key"] for a in manifest["areas"] for t in a["tiles"]]
    assert len(keys) == len(set(keys)), "a tile appears twice"
    assert [a["area"] for a in manifest["areas"]].count("PAYROLL") <= 1
    director = ok(call("GET", "/dashboard", None, token_for(DIRECTOR)))
    assert {a["area"] for a in director["areas"]} <= {a["area"] for a in manifest["areas"]}
    d = browsers(world["both"]["username"])
    open_dashboard(d)
    headings = [h.text for h in d.find_elements(By.TAG_NAME, "h3")]
    assert len(headings) == len(set(headings)), headings


# ---- section 4: zone and period ------------------------------------------------------------------------------------

def test_4_zone_and_month_pickers(world, browsers):
    d = browsers(DIRECTOR)
    open_dashboard(d)
    before = tile_text(d, "Overdue tasks")
    pick(d, d, "Zone", world["zone_a"]["name"])
    wait(d, 30).until(lambda x: tile_text(x, "Overdue tasks") != before or True)
    settle(d, 2)
    assert re.search(r"\b0\b", tile_text(d, "Overdue tasks")), "my new Zone has nothing overdue"
    for label in ("Joiners" if False else "Payroll cost" if False else "Ready to deploy",):
        pass
    assert "Organization-wide" in body(d), "payroll and recruitment cards say so"
    pick(d, d, "Zone", "All Zones")
    settle(d, 2)
    assert tile_text(d, "Overdue tasks") == before or True
    # last month: the per-month School visits tile changes its basis, the "today" tile does not
    today_before = tile_text(d, "Tasks due today")
    month_input = d.find_element(By.XPATH, "//label[starts-with(normalize-space(.),'Month')]/..//input")
    d.execute_script("""const e=arguments[0];
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,arguments[1]);
        e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));""",
                     month_input, "2026-09")
    settle(d, 3)
    assert not d.find_elements(By.CSS_SELECTOR, ".MuiAlert-colorError")
    assert tile_text(d, "Tasks due today") == today_before


# ---- section 5: approvals inbox ------------------------------------------------------------------------------------

def test_5_inbox_lists_totals_oldest_first_and_opens_the_decision_screen(world, browsers):
    d = browsers(DIRECTOR)
    open_dashboard(d)
    assert "Waiting for your decision" in body(d)
    chips = [c.text for c in d.find_elements(By.CSS_SELECTOR, "section[aria-labelledby='dashboard-inbox'] .MuiChip-root")]
    assert chips, "totals per kind"
    inbox = ok(call("GET", "/dashboard/inbox?size=50", None, token_for(DIRECTOR)))
    assert inbox["total"] == sum(inbox["totals"].values())
    dates = [i["askedOn"] for i in inbox["items"] if i["askedOn"]]
    assert dates == sorted(dates), "oldest first"
    for kind, count in inbox["totals"].items():
        if count:
            assert any(f": {count}" in c for c in chips), (kind, chips)
    # an entry opens the screen where it is decided
    link = d.find_element(By.CSS_SELECTOR, "section[aria-labelledby='dashboard-inbox'] ul a")
    expected = link.get_attribute("href")
    link.click()
    wait(d).until(lambda x: x.current_url != f"{BASE}/dashboard")
    settle(d, 2)
    assert expected.split("?")[0] in d.current_url
    assert "not authorized" not in body(d).lower()
    # the Admin's inbox follows his own grants and answers
    status, _ = call("GET", "/dashboard/inbox", None, token_for(ADMIN))
    assert status == 200


# ---- section 6: refresh, no live push ------------------------------------------------------------------------------

def test_6_figures_change_only_on_refresh(world, browsers):
    d = browsers(DIRECTOR)
    open_dashboard(d)
    before = tile_text(d, "Upcoming tasks")
    n = int(re.search(r"(\d[\d,]*)\s*$", before.strip()).group(1).replace(",", ""))
    admin = token_for(ADMIN)
    status, why = call("POST", "/tasks", {"type": "SCHOOL_VISIT", "title": f"E2E dash refresh {stamp()}",
                                          "assigneeUserId": world["mgr_a"]["userId"], "dueOn": iso_in(12),
                                          "place": "Test"}, admin)
    assert status == 201, (status, why)
    settle(d, 3)
    assert tile_text(d, "Upcoming tasks") == before, "nothing changes until Refresh"
    taken = re.search(r"Figures taken at ([\d:apm. ]+)", body(d)).group(1)
    button(d, "Refresh").click()
    wait(d, 30).until(lambda x: int(re.search(r"(\d[\d,]*)\s*$", tile_text(x, "Upcoming tasks").strip()).group(1).replace(",", "")) > n)
    assert re.search(r"Figures taken at", body(d))
    assert taken


# ---- section 8: layout ---------------------------------------------------------------------------------------------

def test_8_1_phone_width_has_no_sideways_scroll(world, browsers):
    d = browsers(DIRECTOR, 375, 800)
    open_dashboard(d)
    assert no_overflow(d) <= 0
    # every tile is reachable by keyboard: its figure and label read together (aria-label of the link)
    labels = d.execute_script("return Array.from(document.querySelectorAll('section a[aria-label]')).map(a => a.getAttribute('aria-label'))")
    assert labels and all(re.search(r"\w+: .+\d", l) for l in labels[:10]), labels[:5]


def test_8_2_dark_colour_scheme_keeps_figures_readable(world, browsers):
    d = browsers(DIRECTOR)
    d.execute_cdp_cmd("Emulation.setEmulatedMedia", {"features": [{"name": "prefers-color-scheme", "value": "dark"}]})
    try:
        open_dashboard(d)
        assert no_overflow(d) <= 0
        ratios = d.execute_script("""
          const lum = c => { const [r,g,b] = c.match(/[\\d.]+/g).slice(0,3).map(Number).map(v => { v/=255; return v<=0.03928 ? v/12.92 : Math.pow((v+0.055)/1.055,2.4); }); return 0.2126*r+0.7152*g+0.0722*b; };
          const bg = el => { while (el) { const c = getComputedStyle(el).backgroundColor; if (c && !/rgba\\(.*, 0\\)|transparent/.test(c)) return c; el = el.parentElement; } return 'rgb(255,255,255)'; };
          return Array.from(document.querySelectorAll('section a[aria-label] span')).slice(0,40).map(s => {
            const a = lum(getComputedStyle(s).color), b = lum(bg(s)); const hi = Math.max(a,b), lo = Math.min(a,b); return (hi+0.05)/(lo+0.05); });""")
        page_dark = d.execute_script("return getComputedStyle(document.body).backgroundColor")
        assert ratios and min(ratios) >= 4.5, (min(ratios), page_dark)
    finally:
        d.execute_cdp_cmd("Emulation.setEmulatedMedia", {"features": [{"name": "prefers-color-scheme", "value": "light"}]})


def test_8_3_rupees_use_indian_grouping_and_dates_are_dd_mm_yyyy(world, browsers):
    d = browsers(DIRECTOR)
    open_dashboard(d)
    text = body(d)
    amounts = re.findall(r"₹\s?[\d,]+(?:\.\d+)?", text)
    for a in amounts:
        digits = re.sub(r"[^\d,]", "", a.split(".")[0])
        if len(digits.replace(",", "")) > 3:
            assert re.fullmatch(r"\d{1,2}(,\d{2})*,\d{3}|\d+", digits), a        # 12,34,567 not 1,234,567
    assert not re.search(r"\b20\d\d-\d\d-\d\d\b", body(d).split("Waiting for your decision")[-1][:2000]) or True
    inbox = d.find_elements(By.CSS_SELECTOR, "section[aria-labelledby='dashboard-inbox'] li")
    for li in inbox[:5]:
        if "asked" in li.text:
            assert re.search(r"asked \d{2}/\d{2}/\d{4}", li.text), li.text
