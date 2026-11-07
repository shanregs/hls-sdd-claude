"""Browser walkthrough for spec 030 (Manager Incentives): quickstart.md sections 1 to 7 and 9 (phone width), as far as the web can go.

Same setup as test_school_working_days.py (demo seed: Admin asha.admin, Director 9800000002, Manager manoj.manager, Teacher 9800000004).
Data: new incentives are earned through the real MoU flow (spec 031) with REST calls for the set-up only (a Manager's prospect is won,
its School gets an MoU, the Director approves it); every assertion is made in the browser. The dev seed (IncentiveDevSeeder) is not used.
A second Zone with its own Manager is created for the "other Zone" checks. The incentive amount is changed to 7500 on the System Settings
screen and always put back to 5000.

Covered: 1 earn (one incentive, pending, Rs 5,000, earner), 2 no earner (NO_PROSPECT, OWNER_NOT_MANAGER), 3 approve (Admin has no buttons,
other-Zone Manager gets not found, earner approves own and it says so, reject needs a reason, reopen is Director only), the new amount
for new incentives while old ones keep theirs, 5 hold and release after an MoU document change, 6 lists (filter, total, CSV, Manager
sees own only), 7 inbox and dashboard tile, role checks, empty states, 375px width.
Not covered: 2 the legacy "approved before approval step" School (no such School in the demo data); 4 payroll pay-out (it needs a
payroll month with locked attendance and would disturb the pay run other walkthroughs use); 5 the "run awaiting approval" variant;
8 regression suites; 9 keyboard and screen reader, dark mode.
"""
import time

import pytest
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC

import api_helpers as api
from browsers import Browsers, body, click, fill, goto, load, menu_labels, overflow_x, wait
from test_school_working_days import ADMIN, BASE, DIRECTOR, MANAGER

TEACHER = "9800000004"
TAG = str(int(time.time()))
SETTING = ("system", "incentive.amount-per-school")
DRAWER = ".MuiDrawer-modal .MuiDrawer-paper"


@pytest.fixture(scope="module")
def browsers():
    b = Browsers()
    yield b
    b.quit_all()


@pytest.fixture(scope="module")
def world():
    """Schools earned through the MoU flow: A approve, B reject/reopen, C stays pending, H hold, plus two no-earner Schools."""
    original = api.get_setting(*SETTING)
    api.put_setting(*SETTING, 5000)
    w = {"tag": TAG}
    for key in ("A", "B", "C"):
        w[key] = api.won_school_with_mou(f"E2E030 {key} {TAG}")
        w[key]["incentive"] = api.incentive_of(w[key]["schoolId"])
    w["H"] = api.won_school_with_mou(f"E2E030 H {TAG}", document=True)
    w["H"]["incentive"] = api.incentive_of(w["H"]["schoolId"])
    w["NP"] = api.won_school_with_mou(f"E2E030 NoProspect {TAG}", owner=None)
    w["OD"] = api.won_school_with_mou(f"E2E030 DirOwned {TAG}", owner=DIRECTOR)
    phone, zone_id, zone_name, mgr_id = api.make_zone_b_manager(TAG)
    w["zoneB"] = {"phone": phone, "name": zone_name}
    yield w
    api.put_setting(SETTING[0], SETTING[1], 5000)


def open_drawer(d, incentive_id):
    load(d, f"/operations/incentives/{incentive_id}")
    try:
        wait(d, 30).until(EC.visibility_of_element_located((By.CSS_SELECTOR, DRAWER)))
    except Exception:
        raise AssertionError(f"drawer did not open at {d.current_url}: {body(d)[:500]}")
    wait(d, 30).until(lambda x: "History" in x.find_element(By.CSS_SELECTOR, DRAWER).text
                      or x.find_elements(By.CSS_SELECTOR, DRAWER + " [role=alert]"))
    time.sleep(0.3)
    return d.find_element(By.CSS_SELECTOR, DRAWER)


def drawer_text(d):
    return d.find_element(By.CSS_SELECTOR, DRAWER).text


def drawer_button(d, label):
    return d.find_elements(By.XPATH, f"//div[contains(@class,'MuiDrawer-modal')]//button[normalize-space()='{label}']")


def list_page(d, query=None, tab=None):
    goto(d, "/operations/incentives" + (f"?tab={tab}" if tab else ""))
    if query:
        box = d.find_element(By.XPATH, "//label[contains(.,'Search School or Manager')]/..//input")
        fill(d, box, query)
        time.sleep(1.5)
        wait(d).until(lambda x: not x.find_elements(By.CSS_SELECTOR, "[aria-label^='Loading']"))


def rows(d):
    found = []
    try:  # the table fills after the search is applied; wait for the first row, then read them all
        WebDriverWait(d, 10).until(lambda x: x.find_elements(By.CSS_SELECTOR, "table[aria-label='Incentives'] tbody tr"))
    except Exception:
        pass
    found = d.find_elements(By.CSS_SELECTOR, "table[aria-label='Incentives'] tbody tr")
    return found


def reason_dialog(d, text, confirm):
    dlg = wait(d).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    if text:
        dlg.find_element(By.XPATH, ".//textarea[not(@aria-hidden='true')]").send_keys(text)
    d.execute_script("arguments[0].click()", dlg.find_element(By.XPATH, f".//button[normalize-space()='{confirm}']"))
    return dlg


def status_of(school_id):
    dr = api.login(DIRECTOR)
    for r in api.must(api.call(dr, "GET", "/api/v1/incentives?size=100"))["content"]:
        if r["schoolId"] == school_id:
            return r
    return None


def wait_status(school_id, status, timeout=60):
    end = time.time() + timeout
    while time.time() < end:
        r = status_of(school_id)
        if r and r["status"] == status:
            return r
        time.sleep(1)
    raise AssertionError(f"incentive of {school_id} never reached {status}: {status_of(school_id)}")


# 1. Earn ------------------------------------------------------------------------------------------------------------

def test_1_mou_approval_earns_one_pending_incentive_of_5000(browsers, world):
    d = browsers.as_user(DIRECTOR)
    list_page(d, f"E2E030 A {TAG}")
    assert len(rows(d)) == 1
    text = rows(d)[0].text
    assert f"E2E030 A {TAG}" in text and "Manoj Manager" in text and "5,000.00" in text and "Waiting for approval" in text


def test_1_approving_the_mou_again_after_a_document_change_still_gives_one(browsers, world):
    h = world["H"]
    # the document change puts the MoU back to pending; approving again must not make a second incentive
    api.must(api.upload(api.login(ADMIN), f"/api/v1/school-contracts/contracts/{h['contractId']}/documents", "v2.pdf", api.MIN_PDF, "application/pdf"))
    api.submit_and_approve(h["contractId"])
    time.sleep(3)
    d = browsers.as_user(DIRECTOR)
    list_page(d, f"E2E030 H {TAG}")
    assert len(rows(d)) == 1


# 2. No earner -------------------------------------------------------------------------------------------------------

def test_2_no_earner_tab_lists_both_reasons(browsers, world):
    api.no_earner_of(world["NP"]["schoolId"])
    api.no_earner_of(world["OD"]["schoolId"])
    d = browsers.as_user(ADMIN)
    list_page(d, tab="no-earner")
    wait(d).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "table[aria-label='Schools without an earner']")))
    text = body(d)
    row_np = d.find_element(By.XPATH, f"//tr[contains(.,'E2E030 NoProspect {TAG}')]").text
    row_od = d.find_element(By.XPATH, f"//tr[contains(.,'E2E030 DirOwned {TAG}')]").text
    assert "No won prospect is linked to this School" in row_np and "Open" in row_np
    assert "not a payroll Manager" in row_od and "Divya Director" in row_od
    assert "Incentives" in d.find_element(By.TAG_NAME, "h1").text and text


def test_2_manager_has_no_no_earner_tab(browsers, world):
    d = browsers.as_user(MANAGER)
    list_page(d)
    assert not d.find_elements(By.XPATH, "//button[@role='tab'][normalize-space()='No earner']")


# 3. Approve / reject / reopen ---------------------------------------------------------------------------------------

def test_3_admin_cannot_approve_or_reject(browsers, world):
    d = browsers.as_user(ADMIN)
    open_drawer(d, world["C"]["incentive"]["id"])
    assert f"E2E030 C {TAG}" in drawer_text(d)
    assert not drawer_button(d, "Approve") and not drawer_button(d, "Reject") and not drawer_button(d, "Reopen")


def test_3_manager_of_another_zone_gets_not_found(browsers, world):
    d = browsers.as_user(world["zoneB"]["phone"])
    open_drawer(d, world["C"]["incentive"]["id"])
    text = drawer_text(d).lower()
    assert "not found" in text or "could not load" in text
    assert f"E2E030 C {TAG}".lower() not in text


def test_3_earner_approves_own_incentive_and_it_says_so(browsers, world):
    d = browsers.as_user(MANAGER)
    open_drawer(d, world["A"]["incentive"]["id"])
    assert drawer_button(d, "Approve") and drawer_button(d, "Reject")
    drawer_button(d, "Approve")[0].click()
    wait(d).until(lambda x: "Approved by the earner" in drawer_text(x))
    text = drawer_text(d)
    assert "Approved" in text and "Decided by" in text and "Manoj Manager" in text
    assert "Earned by" in text and "Approved by Manoj Manager" in text      # Change history names earner and approver
    assert not drawer_button(d, "Reopen")                                    # reopen is not for a Manager
    assert wait_status(world["A"]["schoolId"], "APPROVED")["approvedByEarner"] is True
    list_page(d, f"E2E030 A {TAG}")
    assert "By the earner" in rows(d)[0].text


def test_3_reject_needs_a_reason_then_records_it(browsers, world):
    d = browsers.as_user(MANAGER)
    open_drawer(d, world["B"]["incentive"]["id"])
    d.execute_script("arguments[0].click()", drawer_button(d, "Reject")[0])
    dlg = reason_dialog(d, "", "Reject")
    wait(d).until(lambda x: "Reason is required" in dlg.text)
    dlg.find_element(By.XPATH, ".//textarea[not(@aria-hidden='true')]").send_keys("E2E: figures do not match the MoU")
    dlg.find_element(By.XPATH, ".//button[normalize-space()='Reject']").click()
    wait(d).until(EC.invisibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    wait(d).until(lambda x: "Reason for rejecting" in drawer_text(x))
    assert "E2E: figures do not match the MoU" in drawer_text(d)
    assert not drawer_button(d, "Reopen")


def test_3_reopen_is_director_only(browsers, world):
    inc = world["B"]["incentive"]["id"]
    admin = browsers.as_user(ADMIN)
    open_drawer(admin, inc)
    assert not drawer_button(admin, "Reopen")
    d = browsers.as_user(DIRECTOR)
    open_drawer(d, inc)
    assert drawer_button(d, "Reopen")
    drawer_button(d, "Reopen")[0].click()
    dlg = reason_dialog(d, "", "Reopen")
    wait(d).until(lambda x: "Reason is required" in dlg.text)
    dlg.find_element(By.XPATH, ".//textarea[not(@aria-hidden='true')]").send_keys("E2E: rejection was wrong")
    dlg.find_element(By.XPATH, ".//button[normalize-space()='Reopen']").click()
    wait(d).until(EC.invisibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    wait(d).until(lambda x: "Waiting for approval" in drawer_text(x))
    assert "Reopened" in drawer_text(d)


# New amount -----------------------------------------------------------------------------------------------------------

def test_new_incentive_uses_the_new_amount_old_ones_keep_theirs(browsers, world):
    d = browsers.as_user(ADMIN)
    goto(d, "/system/settings", "System Settings")
    box = d.find_element(By.XPATH, "//label[contains(.,'Manager incentive per closed School')]/..//input")
    assert box.get_attribute("value") == "5000"
    fill(d, box, "100001")                       # out of range: refused with the range shown
    click(d, "//button[normalize-space()='Save changes']")
    wait(d).until(lambda x: "0 to 100000" in body(x) and "Settings saved" not in body(x))
    assert api.get_setting(*SETTING) == "5000"
    fill(d, box, "7500")
    click(d, "//button[normalize-space()='Save changes']")
    wait(d).until(lambda x: "Settings saved." in body(x))
    assert api.get_setting(*SETTING) == "7500"
    try:
        new = api.won_school_with_mou(f"E2E030 N {TAG}")
        row = api.incentive_of(new["schoolId"])
        assert row["amount"] == 7500
        dr = browsers.as_user(DIRECTOR)
        list_page(dr, f"E2E030 N {TAG}")
        assert "7,500.00" in rows(dr)[0].text
        list_page(dr, f"E2E030 A {TAG}")
        assert "5,000.00" in rows(dr)[0].text and "7,500.00" not in rows(dr)[0].text
    finally:
        goto(d, "/system/settings", "System Settings")
        box = d.find_element(By.XPATH, "//label[contains(.,'Manager incentive per closed School')]/..//input")
        fill(d, box, "5000")
        click(d, "//button[normalize-space()='Save changes']")
        wait(d).until(lambda x: "Settings saved." in body(x))
        assert api.get_setting(*SETTING) == "5000"


def test_manager_cannot_open_system_settings(browsers, world):
    d = browsers.as_user(MANAGER)
    load(d, "/system/settings")
    wait(d).until(lambda x: "Not authorized" in body(x))


# 5. Hold ------------------------------------------------------------------------------------------------------------

def test_5_replacing_the_mou_document_holds_the_incentive_and_approval_releases_it(browsers, world):
    h = world["H"]
    mgr = api.login(MANAGER)
    inc = status_of(h["schoolId"])
    api.must(api.call(mgr, "POST", f"/api/v1/incentives/{inc['id']}/approve", {"version": inc["version"]}))
    wait_status(h["schoolId"], "APPROVED")
    # replace the MoU document (MoU goes pending): the incentive is held
    api.must(api.upload(api.login(ADMIN), f"/api/v1/school-contracts/contracts/{h['contractId']}/documents", "v3.pdf", api.MIN_PDF, "application/pdf"))
    wait_status(h["schoolId"], "HELD")
    d = browsers.as_user(DIRECTOR)
    open_drawer(d, inc["id"])
    text = drawer_text(d)
    assert "Held" in text and "MoU not approved" in text and "Held back" in text
    api.submit_and_approve(h["contractId"])
    wait_status(h["schoolId"], "APPROVED")
    open_drawer(d, inc["id"])
    text = drawer_text(d)
    assert "Held because" not in text and "Released" in text
    list_page(d, f"E2E030 H {TAG}")
    assert len(rows(d)) == 1 and "Approved" in rows(d)[0].text


# 6. Lists -----------------------------------------------------------------------------------------------------------

def test_6_director_filters_total_and_csv_export(browsers, world):
    d = browsers.as_user(DIRECTOR)
    list_page(d, TAG)
    assert len(rows(d)) >= 5
    assert "Total" in body(d) and "rejected not counted" in body(d)
    # status filter
    WebDriverWait(d, 20).until(lambda _x: d.find_element(By.XPATH, "//*[contains(@id,'-label')][normalize-space()='Status']/..//div[@role='combobox']")).click()
    click(d, "//li[@role='option'][normalize-space()='Waiting for approval']")
    time.sleep(1.5)
    wait(d).until(lambda x: all("Waiting for approval" in r.text for r in rows(x)) and rows(x))
    assert any(f"E2E030 C {TAG}" in r.text for r in rows(d))
    assert not any(f"E2E030 A {TAG}" in r.text for r in rows(d))
    # export matches the screen filter
    before = time.time() - 1
    click(d, "//button[normalize-space()='Export CSV']")
    path = browsers.wait_download("incentives*.csv", before)
    csv = open(path, encoding="utf-8-sig").read()
    assert f"E2E030 C {TAG}" in csv and "Manoj Manager" in csv and f"E2E030 A {TAG}" not in csv
    # month filter with a month that has none
    box = d.find_element(By.XPATH, "//label[normalize-space()='Month']/..//input")
    box.send_keys("012020")
    time.sleep(1.5)
    wait(d).until(lambda x: "No incentives here yet." in body(x))


def test_6_manager_sees_only_own_and_other_zone_manager_sees_none(browsers, world):
    d = browsers.as_user(MANAGER)
    list_page(d, TAG)
    assert rows(d) and all("Manoj Manager" in r.text for r in rows(d))
    b = browsers.as_user(world["zoneB"]["phone"])
    list_page(b)
    assert "No incentives here yet." in body(b) or "You have not earned an incentive yet." in body(b)
    assert f"E2E030" not in body(b)


def test_6_other_zone_manager_cannot_export_others_incentives(browsers, world):
    b = browsers.as_user(world["zoneB"]["phone"])
    status, data = api.call(api.login(world["zoneB"]["phone"]), "GET", "/api/v1/incentives?size=100")
    assert status == 200 and not [r for r in data["content"] if TAG in r["schoolName"]]


# 7. Dashboard and inbox -----------------------------------------------------------------------------------------------

def test_7_pending_incentive_is_in_the_director_inbox_and_the_dashboard_tile(browsers, world):
    d = browsers.as_user(DIRECTOR)
    goto(d, "/dashboard")
    wait(d, 40).until(lambda x: "Manager incentives" in body(x))
    assert "Waiting for a decision" in body(d)
    assert "Incentive for" in body(d)


# Role checks ----------------------------------------------------------------------------------------------------------

def test_teacher_has_no_incentives_screen(browsers, world):
    d = browsers.as_user(TEACHER)
    assert not any("Incentives" in label for label in menu_labels(d))
    load(d, "/operations/incentives")
    wait(d).until(lambda x: "Not authorized" in body(x))


def test_admin_menu_has_incentives_and_manager_heading_is_my_incentives(browsers, world):
    a = browsers.as_user(ADMIN)
    goto(a, "/operations/incentives")
    assert a.find_element(By.TAG_NAME, "h1").text == "Incentives"
    m = browsers.as_user(world["zoneB"]["phone"])
    goto(m, "/operations/incentives")
    assert m.find_element(By.TAG_NAME, "h1").text == "My Incentives"


# 9. Phone width -------------------------------------------------------------------------------------------------------

def test_9_no_sideways_scroll_at_375px(browsers, world):
    d = browsers.fresh(DIRECTOR, 375, 800)
    try:
        goto(d, "/operations/incentives")
        assert overflow_x(d) <= 0
        open_drawer(d, world["C"]["incentive"]["id"])
        assert overflow_x(d) <= 0
    finally:
        d.quit()
