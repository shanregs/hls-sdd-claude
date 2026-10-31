"""Browser walkthrough for specs 014a and 014c (REPORTS): the 014a quickstart "Run it" steps 2 to 8 and the 014c menu check.

Same setup as test_school_working_days.py (demo seed: Admin, Manager manoj.manager, Teacher 9800000004).
Not covered: step 9 (switching a grant off in Role & Permissions) and figure-by-figure comparison with other screens.
"""
import glob
import os
import tempfile
import time

import pytest
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC

from test_school_working_days import ADMIN, BASE, MANAGER, make_driver, sign_in, wait

TEACHER = "9800000004"
ADMIN_REPORTS = {   # key: the heading on the page (the menu label differs)
    "attendance": "Attendance and LOP", "payroll": "Payroll cost", "recruitment": "Recruitment",
    "teacher": "Teachers", "manager": "Manager productivity", "expenses": "Expenses",
}


def open_report(d, key):
    d.get(f"{BASE}/reports/{key}")
    wait(d, 20).until(EC.visibility_of_element_located((By.TAG_NAME, "h1")))
    wait(d, 30).until(lambda x: not x.find_elements(By.CSS_SELECTOR, "[aria-label^='Loading']"))
    time.sleep(0.4)


MENU = {"attendance": "Attendance Report", "payroll": "Payroll Report", "recruitment": "Recruitment Report",
        "teacher": "Teacher Report", "manager": "Manager Report", "expenses": "Expenses Report", "my": "My Report"}


def has_link(d, key):
    """The menu entry for a report (the menu items are buttons, listed in the nav)."""
    labels = d.execute_script(
        "return Array.from(document.querySelectorAll('nav button, nav [role=button]')).map(b => b.textContent.trim())")
    return MENU[key] in labels


def body(d):
    return d.find_element(By.TAG_NAME, "body").text


@pytest.fixture
def admin():
    d = make_driver()
    sign_in(d, ADMIN)
    yield d
    d.quit()


@pytest.mark.parametrize("key,title", list(ADMIN_REPORTS.items()))
def test_admin_report_loads_with_no_error(admin, key, title):
    open_report(admin, key)
    assert admin.find_element(By.TAG_NAME, "h1").text == title
    assert not admin.find_elements(By.CSS_SELECTOR, ".MuiAlert-colorError, .MuiAlert-standardError"), body(admin)[:400]
    assert admin.find_elements(By.CSS_SELECTOR, "table") or "Nothing to show" in body(admin)


def test_admin_menu_has_all_six_reports(admin):
    open_report(admin, "attendance")
    for key in ADMIN_REPORTS:
        assert has_link(admin, key), key


def test_attendance_banner_and_payroll_no_data(admin):
    open_report(admin, "attendance")
    assert "Month state:" in body(admin)
    open_report(admin, "payroll")
    assert "Month state:" in body(admin)


def test_attendance_export_downloads_a_csv_with_a_header_block():
    folder = tempfile.mkdtemp(prefix="hls-export-")
    opts = webdriver.ChromeOptions()
    opts.add_argument("--headless=new")
    opts.add_argument("--window-size=1280,900")
    opts.add_experimental_option("prefs", {"download.default_directory": folder,
                                           "download.prompt_for_download": False})
    d = webdriver.Chrome(options=opts)
    try:
        sign_in(d, ADMIN)
        open_report(d, "attendance")
        d.execute_script("arguments[0].click()",
                         d.find_element(By.XPATH, "//button[normalize-space()='Export CSV']"))
        wait(d, 30).until(lambda x: glob.glob(os.path.join(folder, "*.csv")))
        time.sleep(1)
        text = open(glob.glob(os.path.join(folder, "*.csv"))[0], encoding="utf-8-sig").read()
        assert "Attendance" in text.splitlines()[0] or "Attendance" in text[:300]
    finally:
        d.quit()


def test_manager_sees_own_reports_only():
    d = make_driver()
    try:
        sign_in(d, MANAGER)
        open_report(d, "attendance")
        assert has_link(d, "attendance")
        assert not has_link(d, "payroll") and not has_link(d, "expenses")
        d.get(f"{BASE}/reports/payroll")                          # a direct request is refused
        time.sleep(2)
        assert "not authori" in body(d).lower() or not d.find_elements(By.CSS_SELECTOR, "table")
        for key in ("recruitment", "teacher", "manager"):          # 014c: Zone Manager menu
            assert has_link(d, key), key
    finally:
        d.quit()


def test_teacher_sees_only_my_report():
    d = make_driver()
    try:
        sign_in(d, TEACHER)
        open_report(d, "my")
        assert d.find_element(By.TAG_NAME, "h1").text.lower() == "my report"
        assert not has_link(d, "attendance") and not has_link(d, "payroll")
        assert not d.find_elements(By.XPATH, "//label[contains(.,'Teacher')]")   # no filter to ask for another Teacher
    finally:
        d.quit()


def test_reports_have_no_horizontal_page_scroll_at_375px():
    d = make_driver(375, 800)
    try:
        sign_in(d, ADMIN)
        for key in ADMIN_REPORTS:
            open_report(d, key)
            overflow = d.execute_script(
                "return document.documentElement.scrollWidth - document.documentElement.clientWidth")
            assert overflow <= 0, f"/reports/{key} scrolls sideways by {overflow}px"
    finally:
        d.quit()
