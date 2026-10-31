"""Browser walkthrough for spec 037 (School Monthly Working Days): quickstart.md steps 4, 8, 9, 10, 11 and SC-001.

Needs the local stack (docker-compose.local.yml) and Chrome. Run from this folder:
    pip install -r requirements.txt
    pytest -v                       # headless
    HEADED=1 pytest -v -s           # watch it

Uses year 2031 so it does not disturb demo data, and deletes what it adds.
"""
import os
import time

import pytest
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

BASE = os.environ.get("BASE_URL", "http://localhost:5173")
PAGE = f"{BASE}/master-data/school-working-days?year=2031"
PASSWORD = "Password123!"
ADMIN, DIRECTOR, MANAGER = "asha.admin", "9800000002", "manoj.manager"
SUBJECT = "Demo School One March 2031"
DIALOG = (By.CSS_SELECTOR, "[role=dialog]")


def make_driver(width=1280, height=900):
    opts = webdriver.ChromeOptions()
    if not os.environ.get("HEADED"):
        opts.add_argument("--headless=new")
    opts.add_argument(f"--window-size={width},{height}")
    d = webdriver.Chrome(options=opts)
    d.set_window_size(width, height)
    return d


def wait(d, timeout=15):
    return WebDriverWait(d, timeout)


def sign_in(d, user):
    d.get(f"{BASE}/sign-in")
    wait(d).until(EC.visibility_of_element_located((By.XPATH, "//input[@type='password']")))
    d.find_element(By.XPATH, "//label[contains(.,'Phone number or username')]/..//input").send_keys(user)
    d.find_element(By.XPATH, "//input[@type='password']").send_keys(PASSWORD, Keys.ENTER)
    wait(d).until(lambda x: "/sign-in" not in x.current_url)


def open_page(d):
    d.get(PAGE)
    wait(d).until(EC.visibility_of_element_located((By.XPATH, "//h1[normalize-space()='School Working Days']")))
    wait(d).until(lambda x: not x.find_elements(By.CSS_SELECTOR, ".MuiDataGrid-overlay .MuiCircularProgress-root"))
    time.sleep(0.5)


def pick(d, scope, label, option):
    """Choose an option in the MUI select with this label inside scope."""
    scope.find_element(By.XPATH, f".//*[contains(@id,'-label')][starts-with(normalize-space(),'{label}')]/..//div[@role='combobox']").click()
    wait(d).until(EC.element_to_be_clickable((By.XPATH, f"//li[@role='option'][normalize-space()='{option}']"))).click()
    wait(d).until(EC.invisibility_of_element_located((By.XPATH, "//ul[@role='listbox']")))


def add_row(d, school="Demo School One", month="March", figure="26"):
    d.find_element(By.XPATH, "//button[normalize-space()='Add working days']").click()
    dlg = wait(d).until(EC.visibility_of_element_located(DIALOG))
    pick(d, dlg, "School", school)
    pick(d, dlg, "Month", month)
    box = dlg.find_element(By.XPATH, ".//label[contains(.,'Working days')]/..//input")
    box.clear()
    box.send_keys(figure)
    dlg.find_element(By.XPATH, ".//button[normalize-space()='Save']").click()
    wait(d).until(EC.invisibility_of_element_located(DIALOG))


def row_button(d, action, subject=SUBJECT):
    return wait(d).until(EC.element_to_be_clickable((By.XPATH, f"//button[@aria-label='{action} {subject}']")))


def has_row(d, subject=SUBJECT):
    return bool(d.find_elements(By.XPATH, f"//button[@aria-label='Edit {subject}']"))


def cell_input(d, subject=SUBJECT):
    return wait(d).until(EC.visibility_of_element_located(
        (By.XPATH, f"//input[@aria-label='Working days for {subject}']")))


def replace_text(box, text, *after):
    box.send_keys(Keys.CONTROL, "a")
    box.send_keys(text, *after)


def delete_row(d, subject=SUBJECT):
    row_button(d, "Delete", subject).click()
    dlg = wait(d).until(EC.visibility_of_element_located(DIALOG))
    dlg.find_element(By.XPATH, ".//button[normalize-space()='Delete']").click()
    wait(d).until(EC.invisibility_of_element_located(DIALOG))


@pytest.fixture
def admin():
    d = make_driver()
    sign_in(d, ADMIN)
    yield d
    d.quit()


@pytest.fixture
def clean_row(admin):
    """Page open with no Demo School One / March / 2031 row; whatever the test leaves is removed afterwards."""
    open_page(admin)
    if has_row(admin):
        delete_row(admin)
    yield admin
    open_page(admin)
    if has_row(admin):
        delete_row(admin)


def test_sc001_adding_one_figure_is_quick(clean_row):
    """SC-001: under 30 s to add one figure. A script is the floor, not a person's time."""
    d = clean_row
    start = time.monotonic()
    add_row(d)
    wait(d).until(lambda x: has_row(x))
    elapsed = time.monotonic() - start
    print(f"\nSC-001 scripted add: {elapsed:.1f}s")
    assert elapsed < 30


def test_step4_edit_validation_and_cancel(clean_row):
    d = clean_row
    add_row(d)
    wait(d).until(lambda x: has_row(x))
    row_button(d, "Edit").click()
    box = cell_input(d)
    for bad in ("25.3", "32"):
        replace_text(box, bad)
        d.find_element(By.XPATH, "//button[normalize-space()='Save']").click()
        wait(d).until(EC.visibility_of_element_located((By.XPATH, "//*[contains(.,'Enter 0 to 31, in half days')]")))
    d.find_element(By.XPATH, "//button[normalize-space()='Cancel']").click()
    wait(d).until(EC.invisibility_of_element_located((By.XPATH, f"//input[@aria-label='Working days for {SUBJECT}']")))
    row = d.find_element(By.XPATH, f"//div[@role='row'][.//button[@aria-label='Edit {SUBJECT}']]")
    assert "26" in row.text


def test_step8_two_windows_stale_save_then_deleted(clean_row):
    a = clean_row
    add_row(a)
    wait(a).until(lambda x: has_row(x))
    b = make_driver()
    try:
        sign_in(b, ADMIN)
        open_page(b)
        wait(b).until(lambda x: has_row(x))

        # both windows edit; the second save is told the row changed and shown the current figure
        row_button(a, "Edit").click()
        row_button(b, "Edit").click()
        replace_text(cell_input(a), "20", Keys.ENTER)
        wait(a).until(EC.invisibility_of_element_located((By.XPATH, f"//input[@aria-label='Working days for {SUBJECT}']")))
        replace_text(cell_input(b), "21", Keys.ENTER)
        wait(b).until(EC.visibility_of_element_located(
            (By.XPATH, "//*[contains(.,'Changed by someone else') and contains(.,'20')]")))

        # delete in one window, edit in the other: refused as deleted, row leaves the grid, nothing recreated
        b.find_element(By.XPATH, "//button[normalize-space()='Cancel']").click()
        delete_row(a)
        row_button(b, "Edit").click()
        replace_text(cell_input(b), "22", Keys.ENTER)
        wait(b).until(EC.visibility_of_element_located((By.XPATH, "//*[contains(.,'deleted by someone else')]")))
        assert not has_row(b)
        open_page(b)
        assert not has_row(b)
    finally:
        b.quit()


def test_step9_director_has_no_delete():
    d = make_driver()
    try:
        sign_in(d, DIRECTOR)
        open_page(d)
        assert d.find_elements(By.XPATH, "//button[normalize-space()='Add working days']")
        assert not d.find_elements(By.XPATH, "//button[starts-with(@aria-label,'Delete ')]")
    finally:
        d.quit()


def test_step10_manager_not_authorised():
    d = make_driver()
    try:
        sign_in(d, MANAGER)
        d.get(PAGE)
        time.sleep(2)
        assert not d.find_elements(By.XPATH, "//h1[normalize-space()='School Working Days']")
        assert "not authori" in d.find_element(By.TAG_NAME, "body").text.lower()
    finally:
        d.quit()


def test_step11_phone_width_has_no_horizontal_page_scroll():
    d = make_driver(375, 800)
    try:
        sign_in(d, ADMIN)
        open_page(d)
        overflow = d.execute_script(
            "return document.documentElement.scrollWidth - document.documentElement.clientWidth")
        assert overflow <= 0, f"page scrolls sideways by {overflow}px"
    finally:
        d.quit()
