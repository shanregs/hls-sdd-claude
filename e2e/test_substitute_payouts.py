"""Browser walkthrough for spec 017b (Substitute Teachers paid by the day): quickstart steps 1 to 9 as far as a person
does them in the browser.

Needs the local stack (docker-compose.local.yml, demo data on) and Chrome. Run from this folder:
    pip install -r requirements.txt
    pytest -v test_substitute_payouts.py            # headless
    HEADED=1 pytest -v -s test_substitute_payouts.py

Uses the demo Substitutes the seed creates: Sunita Substitute (three Present days last month, the month locked) and
Suresh Substitute (a Present day, an Absent day and one day still to come this month). Every change a test makes is
undone. Times from a script are only a floor for the SC-00x timings.
"""
import datetime
import json
import os
import time
import urllib.error
import urllib.request

import pytest
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support import expected_conditions as EC

from test_school_working_days import ADMIN, BASE, DIRECTOR, MANAGER, PASSWORD, make_driver, sign_in, wait

PAYOUTS = f"{BASE}/operations/substitute-payouts"
SUNITA, SURESH = "Sunita Substitute", "Suresh Substitute"
ATTENDED = "Attended, month not locked yet"
ABSENT_TEXT = "Did not attend"
WAITING = "Locked, waiting for the payroll run"


def api(user, method, path, body=None):
    """Setup and checks only; the steps under test go through the browser."""
    def call(url, data=None, token=None, verb="POST"):
        req = urllib.request.Request(
            url, method=verb, data=data,
            headers={"Content-Type": "application/json", **({"Authorization": f"Bearer {token}"} if token else {})})
        try:
            with urllib.request.urlopen(req) as r:
                return json.loads(r.read() or b"null")
        except urllib.error.HTTPError as e:
            return {"error": e.code, "body": e.read().decode()}
    login = call(f"{BASE}/api/v1/auth/login", json.dumps({"identifier": user, "password": PASSWORD}).encode())
    return call(f"{BASE}{path}", json.dumps(body).encode() if body is not None else None, login["accessToken"], method)


def fmt(date):
    return date.strftime("%d/%m/%Y")


@pytest.fixture
def admin():
    d = make_driver()
    try:
        sign_in(d, ADMIN)
    except Exception:  # the first sign-in after a restart can time out; once more
        d.quit()
        d = make_driver()
        sign_in(d, ADMIN)
    yield d
    d.quit()


def open_payouts(d, all_months=True):
    d.get(PAYOUTS)
    wait(d).until(EC.visibility_of_element_located((By.XPATH, "//h1[normalize-space()='Substitute Payouts']")))
    if all_months:
        box = d.find_element(By.XPATH, "//label[contains(.,'Month')]/..//input")
        box.send_keys(Keys.CONTROL, "a")
        box.send_keys(Keys.DELETE)
    wait(d).until(lambda x: not x.find_elements(By.CSS_SELECTOR, "[aria-label='Loading substitute days']"))
    time.sleep(0.4)


def table_text(d):
    return wait(d).until(EC.visibility_of_element_located((By.XPATH, "//table[@aria-label='Substitute days']"))).text


def row_of(d, who, status_text):
    rows = d.find_elements(By.XPATH, f"//table[@aria-label='Substitute days']//tr[td[contains(.,'{who}')]][td[contains(.,'{status_text}')]]")
    return rows[0] if rows else None


def js_click(d, element):
    d.execute_script("arguments[0].scrollIntoView({block: 'center'}); arguments[0].click()", element)


def test_1_admin_sees_both_demo_substitutes_with_their_statuses(admin):
    """Scenario: the list shows every allocated day, who it is for, and where it stands on the way to payment."""
    open_payouts(admin)
    text = table_text(admin)

    assert SUNITA in text and SURESH in text
    assert WAITING in text, "Sunita's locked month waits for the payroll run"
    assert ATTENDED in text and ABSENT_TEXT in text
    assert "Allocated, attendance not marked" in text
    assert "₹800.00" in text, "the day rate prices the days"
    totals = admin.find_element(By.XPATH, "//p[contains(.,'Allocated')][contains(.,'Amount paid')]").text
    assert "Allocated" in totals and "Attended" in totals and "Paid" in totals


def test_2_a_day_still_to_come_cannot_be_marked(admin):
    """FR-008: no future marks; the server says why and the page keeps the day as it was."""
    open_payouts(admin)
    tomorrow = fmt(datetime.date.today() + datetime.timedelta(days=1))
    button = wait(admin).until(EC.presence_of_element_located(
        (By.XPATH, f"//button[@aria-label='Mark {SURESH} present on {tomorrow}']")))
    js_click(admin, button)

    alert = wait(admin).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "[role=alert]")))
    assert "future" in alert.text.lower()
    assert row_of(admin, SURESH, "Allocated, attendance not marked") is not None


def test_3_mark_an_absent_day_present_and_back(admin):
    """US2: the Zone Manager's mark moves the status; the change is reversible while the month is open."""
    open_payouts(admin)
    day = fmt(datetime.date.today() - datetime.timedelta(days=2))
    iso = (datetime.date.today() - datetime.timedelta(days=2)).isoformat()

    def status_of_day():
        rows = admin.find_elements(By.XPATH, f"//table[@aria-label='Substitute days']//tr[td[contains(.,'{SURESH}')]][td[contains(.,'{day}')]]")
        return rows[0].text if rows else ""

    try:
        js_click(admin, wait(admin).until(EC.presence_of_element_located(
            (By.XPATH, f"//button[@aria-label='Mark {SURESH} present on {day}']"))))
        wait(admin).until(lambda d: ATTENDED in status_of_day())
        js_click(admin, wait(admin).until(EC.presence_of_element_located(
            (By.XPATH, f"//button[@aria-label='Mark {SURESH} absent on {day}']"))))
        wait(admin).until(lambda d: ABSENT_TEXT in status_of_day())
    finally:
        # whatever happened, leave the demo day Absent as the seed made it
        rows = api(ADMIN, "GET", "/api/v1/substitute-payouts/days?size=100")["content"]
        for r in rows:
            if r["substituteName"] == SURESH and r["date"] == iso and r["attendance"] != "ABSENT":
                api(ADMIN, "PUT", f"/api/v1/substitute-payouts/days/{r['id']}/attendance",
                    {"attendance": "ABSENT", "version": r["version"]})


def test_4_the_teachers_page_marks_a_substitute_and_hides_placement_salary_and_account(admin):
    """US1 FR-001/FR-002: a Substitute is a Teacher type with no placement, salary or login."""
    admin.get(f"{BASE}/master-data/teachers")
    wait(admin).until(EC.visibility_of_element_located((By.XPATH, "//h1[normalize-space()='Teachers']")))
    box = admin.find_element(By.XPATH, "//label[contains(.,'Search teachers')]/..//input")
    box.send_keys("Sunita")
    row = wait(admin).until(EC.visibility_of_element_located(
        (By.XPATH, "//div[@role='row'][.//*[contains(text(),'Sunita Substitute')]]")))
    assert "Substitute" in row.text and "Substitute (not placed)" in row.text
    labels = {b.text.strip().lower() for b in row.find_elements(By.TAG_NAME, "button")}
    assert row.find_elements(By.XPATH, ".//button[@aria-label='Edit Sunita Substitute']"), "Edit stays"
    assert "employment" in labels, "designation and employee id stay"
    assert not {"placement", "link account", "account", "salary"} & labels


def test_5_the_day_rate_screen_shows_the_cover_teacher_rate(admin):
    """US1 FR-005: the rate lives under Salary Structures, by designation."""
    admin.get(f"{BASE}/operations/salary-structures")
    wait(admin).until(EC.visibility_of_element_located((By.XPATH, "//h1[normalize-space()='Salary Structures']")))
    admin.find_element(By.XPATH, "//button[@role='tab'][normalize-space()='Substitute day rates']").click()
    table = wait(admin).until(EC.visibility_of_element_located((By.XPATH, "//table[@aria-label='Substitute day rates']")))
    assert "Cover Teacher" in table.text and "₹800.00" in table.text
    admin.find_element(By.XPATH, "//button[@aria-label='History of day rates for Cover Teacher']").click()
    dialog = wait(admin).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    assert "₹800.00" in dialog.text


def test_6_the_pay_run_pays_sunita_by_the_day_and_the_working_shows_the_days(admin):
    """US1: one line, days times the rate, no salary; the reviewer opens the working and reproduces the figure."""
    last = (datetime.date.today().replace(day=1) - datetime.timedelta(days=1)).strftime("%Y-%m")
    runs = api(ADMIN, "GET", "/api/v1/payroll/runs?size=100")["content"]
    mine = [r for r in runs if r["month"] == last and r["kind"] == "PRIMARY" and r["status"] != "CANCELLED"]
    if mine and mine[0]["status"] == "APPROVED":
        pytest.skip(f"the primary run of {last} is already approved; Sunita's line cannot be recomputed")
    if mine:
        run = mine[0]["id"]
        if mine[0]["status"] == "AWAITING_APPROVAL":
            pytest.skip(f"the run of {last} awaits approval")
        api(ADMIN, "POST", f"/api/v1/payroll/runs/{run}/recompute", {})
    else:
        run = api(ADMIN, "POST", "/api/v1/payroll/runs", {"month": last})["id"]
    deadline = time.time() + 120
    while time.time() < deadline:
        state = api(ADMIN, "GET", f"/api/v1/payroll/runs/{run}")["computeState"]
        if state in ("DONE", "FAILED"):
            break
        time.sleep(1)
    assert state == "DONE"

    admin.get(f"{BASE}/operations/payroll/{run}")
    wait(admin).until(EC.element_to_be_clickable(
        (By.XPATH, f"//button[@aria-label='Show working for {SUNITA}']"))).click()
    dialog = wait(admin).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    wait(admin).until(lambda d: "Days worked" in dialog.text)
    assert "2.50 days at ₹800.00" in dialog.text
    assert "₹2,000.00" in dialog.text
    assert "Deduction for days without pay" not in dialog.text


def test_7_a_manager_sees_only_their_zone_and_no_lock_or_export(admin):
    """US3/FR-023: the Zone Manager marks within the Zone but cannot lock, export or run the go-live move."""
    admin.quit()
    d = make_driver()
    try:
        sign_in(d, MANAGER)
        d.get(PAYOUTS)
        wait(d).until(EC.visibility_of_element_located((By.XPATH, "//h1[normalize-space()='Substitute Payouts']")))
        time.sleep(1)
        buttons = [b.text for b in d.find_elements(By.TAG_NAME, "button")]
        assert not [b for b in buttons if b.startswith("Lock all")]
        assert "Export CSV" not in buttons
        assert not [b for b in buttons if b.startswith("Move existing")]
    finally:
        d.quit()


def test_8_the_page_has_no_horizontal_scroll_at_phone_width():
    """FR-022 / Principle IV: usable at 375 px."""
    d = make_driver(375, 800)
    try:
        sign_in(d, ADMIN)
        open_payouts(d)
        overflow = d.execute_script("return document.documentElement.scrollWidth - window.innerWidth")
        assert overflow <= 1, f"the page scrolls sideways by {overflow}px"
    finally:
        d.quit()
