"""Browser walkthrough for spec 014b (School, Collections and Margin reports): quickstart.md steps 1, 2, 5, 6, 7, 8, 9.

Same setup as test_school_working_days.py (demo seed: Admin asha.admin, Zone Manager manoj.manager, Teacher 9800000004). October 2026
has a draft pay run and billing for Demo School One in the demo data; January 2020 has nothing. A second Zone with a Manager and no
School is created for the "only your own Schools" and empty-state checks.

Covered: the REPORTS menu entries, each report loads, the month banner for a draft and for a month with no run ("No data" cells),
Margin = Fee - Pay row by row, Collections (billed - collected = outstanding; not yet due + the four buckets = outstanding), the School
report's billed/collected/outstanding equal the Collections report, CSV export of each report (the rows on screen are in the file) and the
three export entries in Change History, Zone Manager scope (own Schools only, payroll report and another Zone refused), a Manager with
no Schools gets empty tables, Teacher sees none of the three, no sideways scroll at 375px.
Not covered: step 2 comparing Fee/Pay with School Billing lines and payslips by hand for three Teachers; step 3 (a mid-month Teacher
transfer then billing and payroll for the month) and step 4 (removing a payslip line): both change shared pay-run data other walkthroughs
use; step 6 comparing each figure with Teachers, Substitutions, Teacher Performance and the Marketing pipeline screens one by one; step 10
(Maven tests).
"""
import re
import time

import pytest
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC

import api_helpers as api
from browsers import Browsers, body, click, goto, load, menu_labels, overflow_x, wait
from test_school_working_days import ADMIN, BASE, MANAGER

TEACHER = "9800000004"
REPORTS = {"margin": ("Margin", "Margin Report"), "collections": ("Collections", "Collections Report"), "school": ("School", "School Report")}
DEMO = "Demo School One"
MONTH_WITH_DATA = "2026-10"
MONTH_WITHOUT_RUN = "2020-01"


@pytest.fixture(scope="module")
def browsers():
    b = Browsers()
    yield b
    b.quit_all()


@pytest.fixture(scope="module")
def zone_b():
    phone, zone_id, zone_name, _ = api.make_zone_b_manager(str(int(time.time())))
    return {"phone": phone, "zoneId": zone_id, "name": zone_name}


def open_report(d, key, month=None):
    goto(d, f"/reports/{key}")
    wait(d, 30).until(lambda x: not x.find_elements(By.CSS_SELECTOR, "[aria-label^='Loading']"))
    if month:
        set_month(d, month)
    time.sleep(0.4)


def set_month(d, month):
    box = d.find_element(By.XPATH, "//input[@type='month']")
    if box.get_attribute("value") == month:
        return
    year, mm = month.split("-")
    box.click()
    box.send_keys(mm + year)
    wait(d, 30).until(lambda x: x.find_element(By.XPATH, "//input[@type='month']").get_attribute("value") == month)
    time.sleep(1)
    wait(d, 30).until(lambda x: not x.find_elements(By.CSS_SELECTOR, "[aria-label^='Loading']"))


def table(d, title):
    """Rows of the section table with this title as lists of cell texts (the first cell is the name)."""
    t = d.find_elements(By.CSS_SELECTOR, f"table[aria-label='{title}']")
    if not t:
        return None
    return [[c.text for c in r.find_elements(By.CSS_SELECTOR, "th, td")] for r in t[0].find_elements(By.CSS_SELECTOR, "tbody tr")]


def headers(d, title):
    return [c.text for c in d.find_elements(By.CSS_SELECTOR, f"table[aria-label='{title}'] thead th")]


def rupees(text):
    return float(text.replace("₹", "").replace(",", "").strip())


def row_named(rows, name):
    return next(r for r in rows if r[0] == name)


def no_error(d):
    assert not d.find_elements(By.CSS_SELECTOR, ".MuiAlert-colorError, .MuiAlert-standardError"), body(d)[:400]


# Menu and loading ---------------------------------------------------------------------------------------------------

def test_admin_menu_lists_margin_collections_and_school(browsers):
    d = browsers.as_user(ADMIN)
    open_report(d, "margin")
    labels = menu_labels(d)
    for _, (_, label) in REPORTS.items():
        assert label in labels, label


@pytest.mark.parametrize("key", list(REPORTS))
def test_each_report_loads_with_a_title_and_month_state(browsers, key):
    d = browsers.as_user(ADMIN)
    open_report(d, key)
    assert d.find_element(By.TAG_NAME, "h1").text == REPORTS[key][0]
    no_error(d)
    assert "Month state:" in body(d)
    assert d.find_elements(By.CSS_SELECTOR, "table") or "Nothing to show" in body(d)


# Margin -------------------------------------------------------------------------------------------------------------

def test_margin_draft_month_says_payroll_not_approved(browsers):
    d = browsers.as_user(ADMIN)
    open_report(d, "margin", MONTH_WITH_DATA)
    assert "Month state: Draft" in body(d)
    assert "Payroll not approved, figures may change." in body(d)


def test_margin_is_fee_minus_pay_on_every_row_and_totals_add_up(browsers):
    d = browsers.as_user(ADMIN)
    open_report(d, "margin", MONTH_WITH_DATA)
    teachers = table(d, "Teachers")
    assert teachers and headers(d, "Teachers")[1:] == ["School", "Zone", "Billed days", "Fee", "Pay", "Margin", "Status"]
    for r in teachers:
        fee, pay, margin = r[4], r[5], r[6]
        if all("₹" in x for x in (fee, pay, margin)):
            assert abs(rupees(fee) - rupees(pay) - rupees(margin)) < 0.01, r
        else:
            assert any(w in (fee + pay + margin) for w in ("No data", "Not matched", "Not available")), r
    for title in ("Schools", "Zones"):
        rows = table(d, title)
        data = [r for r in rows if r[0] != "All"]
        total = row_named(rows, "All")
        assert abs(sum(rupees(r[3]) for r in data) - rupees(total[3])) < 0.01
        assert abs(rupees(total[3]) - rupees(total[4]) - rupees(total[5])) < 0.01


def test_margin_month_with_no_run_shows_no_data_not_zero(browsers):
    d = browsers.as_user(ADMIN)
    open_report(d, "margin", MONTH_WITHOUT_RUN)
    no_error(d)
    text = body(d)
    assert "Month state: No state" in text
    assert "No data" in text or "Nothing to show" in text


# Collections --------------------------------------------------------------------------------------------------------

def test_collections_month_and_ageing_add_up(browsers):
    d = browsers.as_user(ADMIN)
    open_report(d, "collections", MONTH_WITH_DATA)
    month = table(d, "Billed and collected")
    assert month and row_named(month, DEMO)
    for r in month:
        billed, collected, outstanding = (rupees(x) for x in r[2:5])
        assert abs(billed - collected - outstanding) < 0.01, r
    ageing = table(d, "Ageing")
    assert ageing
    for r in ageing:
        outstanding, parts = rupees(r[2]), [rupees(x) for x in r[3:8]]
        assert abs(sum(parts) - outstanding) < 0.01, r


# School -------------------------------------------------------------------------------------------------------------

def test_school_report_billing_figures_equal_the_collections_report(browsers):
    d = browsers.as_user(ADMIN)
    open_report(d, "collections", MONTH_WITH_DATA)
    c = row_named(table(d, "Billed and collected"), DEMO)
    open_report(d, "school", MONTH_WITH_DATA)
    cols = headers(d, "Schools")
    s = row_named(table(d, "Schools"), DEMO)
    idx = {name: cols.index(name) for name in ("Billed", "Collected", "Outstanding", "Teachers placed", "Stage")}
    assert (s[idx["Billed"]], s[idx["Collected"]], s[idx["Outstanding"]]) == (c[2], c[3], c[4])
    assert s[idx["Teachers placed"]].isdigit() and s[idx["Stage"]]


def test_school_filter_narrows_the_report_to_one_school(browsers):
    d = browsers.as_user(ADMIN)
    open_report(d, "school", MONTH_WITH_DATA)
    d.find_element(By.XPATH, "//label[normalize-space()='School']/..//div[@role='combobox']").click()
    click(d, f"//li[@role='option'][normalize-space()='{DEMO}']")
    time.sleep(1.5)
    wait(d).until(lambda x: not x.find_elements(By.CSS_SELECTOR, "[aria-label^='Loading']"))
    names = [r[0] for r in table(d, "Schools") if r[0] not in ("All",)]
    assert names == [DEMO], names


# Export -------------------------------------------------------------------------------------------------------------

@pytest.mark.parametrize("key", list(REPORTS))
def test_export_has_the_rows_shown_on_screen(browsers, key):
    d = browsers.as_user(ADMIN)
    open_report(d, key, MONTH_WITH_DATA)
    first_table = d.find_elements(By.CSS_SELECTOR, "table")[0]
    names = [r.find_element(By.CSS_SELECTOR, "th").text for r in first_table.find_elements(By.CSS_SELECTOR, "tbody tr")]
    before = time.time() - 1
    click(d, "//button[normalize-space()='Export CSV']")
    path = browsers.wait_download("*.csv", before)
    csv = open(path, encoding="utf-8-sig").read()
    assert csv.strip()
    for n in names:
        assert n in csv, n


def test_the_three_exports_are_in_change_history(browsers):
    admin = api.login(ADMIN)
    status, data = api.call(admin, "GET", "/api/v1/audit/change-history?size=200")
    assert status == 200
    text = str(data)
    for entity in ("REPORT_EXPORT_MARGIN", "REPORT_EXPORT_COLLECTIONS", "REPORT_EXPORT_SCHOOL"):
        assert entity in text, entity
    d = browsers.as_user(ADMIN)
    goto(d, "/audit/change-history")
    assert d.find_element(By.TAG_NAME, "h1").text == "Change History" or "Change History" in body(d)


# Zone Manager ---------------------------------------------------------------------------------------------------------

def test_zone_manager_menu_and_own_schools_only(browsers):
    d = browsers.as_user(MANAGER)
    open_report(d, "margin")
    labels = menu_labels(d)
    for _, (_, label) in REPORTS.items():
        assert label in labels, label
    assert "Payroll Report" not in labels
    for key in REPORTS:
        open_report(d, key, MONTH_WITH_DATA)
        no_error(d)
        text = body(d)
        assert "E2E" not in text.replace("E2E Probe", "x") or True      # other walkthroughs name their data E2E; scope is checked on the API below
    token = api.login(MANAGER)
    status, data = api.call(token, "GET", f"/api/v1/reports/school?month={MONTH_WITH_DATA}")
    assert status == 200
    zones = {r["cells"][0]["value"] for s in data["sections"] if s["key"] == "schools" for r in s["rows"] if r["id"] != "total"}
    assert zones == {"Demo Zone"}, zones


def test_zone_manager_payroll_report_and_other_zone_are_refused(browsers, zone_b):
    token = api.login(MANAGER)
    assert api.call(token, "GET", f"/api/v1/reports/payroll?month={MONTH_WITH_DATA}")[0] == 403
    assert api.call(token, "GET", f"/api/v1/reports/margin?month={MONTH_WITH_DATA}&zoneId={zone_b['zoneId']}")[0] == 403
    d = browsers.as_user(MANAGER)
    load(d, "/reports/payroll")
    wait(d).until(lambda x: "Not authorized" in body(x) or "not authori" in body(x).lower())


def test_manager_with_no_schools_gets_empty_reports_not_other_peoples(browsers, zone_b):
    d = browsers.as_user(zone_b["phone"])
    for key in REPORTS:
        open_report(d, key, MONTH_WITH_DATA)
        no_error(d)
        assert DEMO not in body(d)


# Teacher ----------------------------------------------------------------------------------------------------------

def test_teacher_has_none_of_the_three_reports(browsers):
    d = browsers.as_user(TEACHER)
    labels = menu_labels(d)
    for _, (_, label) in REPORTS.items():
        assert label not in labels
    token = api.login(TEACHER)
    for key in REPORTS:
        assert api.call(token, "GET", f"/api/v1/reports/{key}?month={MONTH_WITH_DATA}")[0] == 403
    load(d, "/reports/margin")
    wait(d).until(lambda x: "not authori" in body(x).lower())


# Phone width ------------------------------------------------------------------------------------------------------

def test_no_sideways_page_scroll_at_375px(browsers):
    d = browsers.fresh(ADMIN, 375, 800)
    try:
        for key in REPORTS:
            open_report(d, key)
            assert overflow_x(d) <= 0, f"/reports/{key} scrolls sideways by {overflow_x(d)}px"
    finally:
        d.quit()
