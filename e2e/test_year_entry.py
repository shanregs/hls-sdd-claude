"""Browser walkthrough for spec 038 (Enter a whole year on one screen): quickstart.md steps 1 to 12.

Same setup as test_school_working_days.py. Uses Demo School Two and year 2031 and deletes the rows afterwards.
Not covered (needs a test grant of Create without Edit): the last sentence of step 11.
"""
import time

import pytest
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support import expected_conditions as EC

from test_school_working_days import (
    ADMIN, BASE, DIRECTOR, MANAGER, make_driver, pick, replace_text, sign_in, wait,
)

SCHOOL = "Demo School Two"
YEAR = 2031
MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October",
          "November", "December"]
YEAR_URL = f"{BASE}/master-data/school-working-days/year?year={YEAR}"
GRID_URL = f"{BASE}/master-data/school-working-days?year={YEAR}&q=" + SCHOOL.replace(" ", "%20")
H1 = "//h1[normalize-space()='Enter a year of working days']"


def month_input(d, month):
    return wait(d).until(EC.visibility_of_element_located(
        (By.XPATH, f"//input[@aria-label='{MONTHS[month - 1]} {YEAR}']")))


def open_year(d):
    d.get(YEAR_URL)
    wait(d).until(EC.visibility_of_element_located((By.XPATH, H1)))
    pick(d, d, "School", SCHOOL)
    month_input(d, 1)


def js_click(d, element):
    """Click through the sticky app bar that can cover a button after scrolling."""
    d.execute_script("arguments[0].scrollIntoView({block: 'center'}); arguments[0].click()", element)


def save_button(d):
    return d.find_element(By.XPATH, "//button[normalize-space()='Save' or normalize-space()='Saving…']")


def summary(d):
    return wait(d).until(EC.visibility_of_element_located(
        (By.XPATH, "//*[@role='status']//*[contains(.,' created, ')]"))).text


def type_months(d, figures):
    for month, figure in figures.items():
        replace_text(month_input(d, month), str(figure))


def save_and_summary(d):
    js_click(d, wait(d).until(EC.element_to_be_clickable((By.XPATH, "//button[normalize-space()='Save']"))))
    time.sleep(0.4)
    wait(d).until(EC.presence_of_element_located((By.XPATH, "//button[normalize-space()='Save']")))
    wait(d).until(lambda x: _summary_or_none(x) is not None)
    return _summary_or_none(d)


def _summary_or_none(d):
    found = d.find_elements(By.XPATH, "//*[@role='status']//*[contains(.,' created, ')]")
    return found[0].text if found else None


def grid_rows(d):
    d.get(GRID_URL)
    wait(d).until(EC.visibility_of_element_located((By.XPATH, "//h1[normalize-space()='School Working Days']")))
    wait(d).until(lambda x: not x.find_elements(By.CSS_SELECTOR, ".MuiDataGrid-overlay .MuiCircularProgress-root"))
    time.sleep(0.5)
    return d.find_elements(By.XPATH, "//button[starts-with(@aria-label,'Edit ')]")


def delete_all(d):
    while grid_rows(d):
        d.find_element(By.XPATH, "//button[starts-with(@aria-label,'Delete ')]").click()
        dlg = wait(d).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
        dlg.find_element(By.XPATH, ".//button[normalize-space()='Delete']").click()
        wait(d).until(EC.invisibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
        time.sleep(0.3)


@pytest.fixture
def admin():
    d = make_driver()
    sign_in(d, ADMIN)
    delete_all(d)
    yield d
    delete_all(d)
    d.quit()


def enter_full_year(d, figure=20):
    type_months(d, {m: figure for m in range(1, 13)})
    return save_and_summary(d)


def test_steps_1_to_5_open_enter_change_and_bad_figures(admin):
    d = admin
    start = time.monotonic()
    open_year(d)                                                   # steps 1, 2
    load = time.monotonic() - start
    assert len(d.find_elements(By.XPATH, "//*[contains(.,'Nothing stored')]")) >= 12
    assert not d.find_elements(By.XPATH, "//*[contains(.,' · Stored: ')]")

    typing = time.monotonic()
    assert enter_full_year(d).startswith("12 created")             # step 3
    took = time.monotonic() - typing
    print(f"\nscripted: open+load {load:.1f}s, type and save 12 months {took:.1f}s")
    assert len(grid_rows(d)) == 12
    open_year(d)

    type_months(d, {3: 21, 5: 22})                                 # step 4
    assert save_and_summary(d).startswith("0 created, 2 updated, 0 unchanged")

    type_months(d, {1: 18, 4: 31, 6: 25.3})                        # step 5: April has 30 days
    # the browser stops the bad figures (April has 30 days; 25.3 is not a half day) and saves the rest
    assert save_and_summary(d).startswith("0 created, 1 updated, 0 unchanged")
    assert month_input(d, 4).get_attribute("value") == "31" and month_input(d, 6).get_attribute("value") == "25.3"
    assert len(d.find_elements(By.XPATH, "//*[@role='alert'][contains(.,'half days')]")) >= 2
    assert d.find_elements(By.XPATH, "//*[contains(.,'Fix the figures marked in red.')]")


def test_step6_two_windows(admin):
    a = admin
    open_year(a)
    enter_full_year(a)
    b = make_driver()
    try:
        sign_in(b, ADMIN)
        open_year(b)
        type_months(a, {3: 21})
        save_and_summary(a)
        type_months(b, {3: 22, 5: 24})
        text = save_and_summary(b)
        assert "1 updated" in text and "1 not saved: March" in text
        b.find_element(By.XPATH, "//button[starts-with(normalize-space(),'Use the stored figure (21)')]")
        # same figure as the other window: reported unchanged
        type_months(a, {4: 23})
        save_and_summary(a)
        type_months(b, {4: 23})
        assert "unchanged" in save_and_summary(b)
    finally:
        b.quit()


def test_step7_deleted_meanwhile_is_not_recreated(admin):
    a = admin
    open_year(a)
    enter_full_year(a)
    b = make_driver()
    try:
        sign_in(b, ADMIN)
        open_year(b)
        assert len(grid_rows(a)) == 12
        a.find_element(By.XPATH, "//button[@aria-label='Delete Demo School Two March 2031']").click()
        dlg = wait(a).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
        dlg.find_element(By.XPATH, ".//button[normalize-space()='Delete']").click()
        wait(a).until(EC.invisibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
        type_months(b, {3: 22})
        assert "1 not saved: March" in save_and_summary(b)
        assert b.find_elements(By.XPATH, "//*[@role='alert'][contains(.,'delet')]")
        time.sleep(1)
        assert len(grid_rows(a)) == 11
    finally:
        b.quit()


def test_step8_phone_width_no_horizontal_scroll():
    d = make_driver(375, 800)
    try:
        sign_in(d, ADMIN)
        open_year(d)
        overflow = d.execute_script(
            "return document.documentElement.scrollWidth - document.documentElement.clientWidth")
        assert overflow <= 0, f"page scrolls sideways by {overflow}px"
        for month in (1, 12):                                      # first and last field reachable
            field = month_input(d, month)
            d.execute_script("arguments[0].scrollIntoView()", field)
            assert field.is_displayed()
        d.execute_script("window.scrollTo(0, document.body.scrollHeight)")
        assert save_button(d).is_displayed()
    finally:
        d.quit()


def test_step9_helpers(admin):
    d = admin
    open_year(d)
    type_months(d, {1: 20, 2: 20, 3: 20})
    js_click(d, d.find_element(By.XPATH, "//button[normalize-space()='Use calendar counts']"))
    assert month_input(d, 1).get_attribute("value") == "20"      # only empty months filled
    assert month_input(d, 4).get_attribute("value") != ""
    for m in (5, 6, 7):
        replace_text(month_input(d, m), Keys.DELETE)
    js_click(d, d.find_element(By.XPATH, "//button[normalize-space()='Same figure for all empty months']"))
    dlg = wait(d).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    dlg.find_element(By.XPATH, ".//label[contains(.,'Working days')]/..//input").send_keys("30")
    dlg.find_element(By.XPATH, ".//button[normalize-space()='Fill']").click()
    wait(d).until(EC.invisibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    assert not grid_rows_stored_hint(d)                            # nothing saved yet
    assert month_input(d, 5).get_attribute("value") == "30"


def grid_rows_stored_hint(d):
    return d.find_elements(By.XPATH, "//*[contains(.,' · Stored: ')]")


def test_step10_leaving_with_unsaved_figures_asks(admin):
    d = admin
    open_year(d)
    type_months(d, {1: 20})
    pick(d, d, "School", "Demo School One")
    dlg = wait(d).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    assert "Discard your changes?" in dlg.text
    dlg.find_element(By.XPATH, ".//button[normalize-space()='Keep editing']").click()
    assert month_input(d, 1).get_attribute("value") == "20"
    # headless Chrome shows no reload prompt, so check the page asks for one
    asks = d.execute_script(
        "var e = new Event('beforeunload', {cancelable: true}); window.dispatchEvent(e); return e.defaultPrevented")
    assert asks


def test_step11_permissions():
    d = make_driver()
    try:
        sign_in(d, DIRECTOR)
        open_year(d)
        assert save_button(d)
    finally:
        d.quit()
    d = make_driver()
    try:
        sign_in(d, MANAGER)
        d.get(YEAR_URL)
        time.sleep(2)
        assert "not authori" in d.find_element(By.TAG_NAME, "body").text.lower()
    finally:
        d.quit()


def test_step12_retry_after_offline_writes_nothing_twice(admin):
    d = admin
    open_year(d)
    type_months(d, {m: 20 for m in range(1, 13)})
    d.execute_cdp_cmd("Network.enable", {})
    d.execute_cdp_cmd("Network.emulateNetworkConditions",
                      {"offline": True, "latency": 0, "downloadThroughput": 0, "uploadThroughput": 0})
    wait(d).until(EC.element_to_be_clickable((By.XPATH, "//button[normalize-space()='Save']"))).click()
    wait(d).until(EC.visibility_of_element_located((By.XPATH, "//*[contains(@class,'MuiAlert-message')][contains(.,'')]")))
    assert _summary_or_none(d) is None                             # nothing saved while offline
    d.execute_cdp_cmd("Network.emulateNetworkConditions",
                      {"offline": False, "latency": 0, "downloadThroughput": -1, "uploadThroughput": -1})
    assert save_and_summary(d).startswith("12 created")
    assert save_and_summary_again_is_noop(d)
    assert len(grid_rows(d)) == 12


def save_and_summary_again_is_noop(d):
    """Typing the same figures and saving again writes nothing: all unchanged."""
    open_year(d)
    type_months(d, {m: 20 for m in range(1, 13)})
    btn = d.find_elements(By.XPATH, "//button[normalize-space()='Save' and not(@disabled)]")
    if not btn:
        return True                                                # Save is disabled: nothing to send
    return "12 unchanged" in save_and_summary(d)
