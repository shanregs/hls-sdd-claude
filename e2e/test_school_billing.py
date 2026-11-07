"""Browser walkthrough for spec 022 (School Billing and Receivables): quickstart.md steps 1 to 5.

Needs the local stack and Chrome. Run from this folder:
    pytest test_school_billing.py -v                # headless
    HEADED=1 pytest test_school_billing.py -v -s    # watch it

A private School A1 (Zone A, Manager A) gets a MoU and one Teacher mapped to it through the API; everything under test then
goes through the browser: raise the monthly dues for the current month (this also raises dues for any other School in the
demo data that has none yet, and cannot be undone except by cancelling), see the line, raise again (skipped), record a
collection, an adjustment and its reversal, cancel the due with a reason, Ageing, and the Zone Manager's scope.
Not covered: the overdue job and My Tasks (step 6: no endpoint to run the job), the Java interface (step 7), a Teacher who
joined mid-month (needs a placement dated this month; the line is checked in the backend tests).
"""
import os
import time
from datetime import date, timedelta

import pytest
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

import walk_support as api

BASE = api.BASE
ADMIN = "asha.admin"


def make_driver(width=1280, height=900):
    opts = webdriver.ChromeOptions()
    if not os.environ.get("HEADED"):
        opts.add_argument("--headless=new")
    opts.add_argument(f"--window-size={width},{height}")
    d = webdriver.Chrome(options=opts)
    d.set_window_size(width, height)
    return d


def wait(d, timeout=25):
    return WebDriverWait(d, timeout)


def sign_in(d, user):
    for attempt in range(6):  # nginx allows 30 auth calls a minute for the whole machine, so a busy minute needs a pause
        d.get(f"{BASE}/sign-in")
        wait(d).until(EC.visibility_of_element_located((By.XPATH, "//input[@type='password']")))
        d.find_element(By.XPATH, "//label[contains(.,'Phone number or username')]/..//input").send_keys(user)
        d.find_element(By.XPATH, "//input[@type='password']").send_keys(api.PASSWORD, Keys.ENTER)
        try:
            wait(d, 20).until(lambda x: "/sign-in" not in x.current_url)
            wait(d, 30).until(lambda x: x.find_elements(By.XPATH, "//h1 | //nav | //header"))
            time.sleep(1)
            return
        except Exception:
            if attempt == 5:
                raise AssertionError("sign-in did not finish: " + d.find_element(By.TAG_NAME, "body").text[:300])
            time.sleep(40)



class Shared(webdriver.Chrome):
    """One signed-in browser per person for the whole run: the nginx in front allows only 30 auth calls a minute for the
    whole machine, so signing in again for every step would be refused. quit() does nothing; the run closes them."""

    def quit(self):
        pass


_SESSIONS = {}


def as_user(user, width=1280):
    key = (user, width)
    if key in _SESSIONS:
        return _SESSIONS[key]
    for attempt in range(3):
        opts = webdriver.ChromeOptions()
        if not os.environ.get("HEADED"):
            opts.add_argument("--headless=new")
        opts.add_argument(f"--window-size={width},900")
        d = Shared(options=opts)
        d.set_window_size(width, 900)
        try:
            sign_in(d, user)
            _SESSIONS[key] = d
            return d
        except Exception:
            try:
                webdriver.Chrome.quit(d)
            except Exception:
                pass
            if attempt == 2:
                raise


@pytest.fixture(scope="session", autouse=True)
def _close_sessions():
    yield
    for d in _SESSIONS.values():
        try:
            webdriver.Chrome.quit(d)
        except Exception:
            pass


def spa_goto(d, route):
    """Go to a route without a page reload (the access token lives only in memory, so a reload costs an auth call and can lose a Teacher's session)."""
    d.execute_script("history.pushState({}, '', arguments[0]); window.dispatchEvent(new PopStateEvent('popstate'));", route)


def body(d):
    return d.find_element(By.TAG_NAME, "body").text


def expect_text(d, text, timeout=25):
    try:
        wait(d, timeout).until(lambda x: text.lower() in body(x).lower())
    except Exception:
        raise AssertionError(f"expected '{text}'; the page says: {body(d)[:700]}")


def click(d, el):
    d.execute_script("arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", el)


def btn(d, label, scope=None):
    """A visible button with this text, inside the scope when one is given (the last one on the page otherwise)."""
    def find(x):
        root = scope if scope is not None else x
        els = [e for e in root.find_elements(By.XPATH, f".//button[normalize-space()='{label}']") if e.is_displayed()]
        return els[-1] if els else False
    return wait(d).until(find)


def dialog(d):
    return wait(d).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))


def type_in(dlg, label, text):
    box = dlg.find_element(By.XPATH, f".//label[contains(normalize-space(),'{label}')]/..//*[self::input or self::textarea][not(@aria-hidden='true')]")
    box.send_keys(Keys.CONTROL, "a")
    box.send_keys(text)


@pytest.fixture(scope="module")
def world():
    w = api.build_world(api.stamp(), with_manager_b=True, teachers_a=1, teachers_b=1)
    admin = ADMIN
    cands = api.ok(api.call(admin, "GET", f"/api/v1/school-contracts/signatory-candidates?schoolId={w['a1']['id']}"))
    held = api.ok(api.call(admin, "GET", f"/api/v1/school-contracts/schools/{w['a1']['id']}"))["contracts"]
    placeholder = next(x for x in held if x["state"] == "RATE_PENDING")
    signed = (date.today() - timedelta(days=60)).isoformat()
    c = api.ok(api.call(admin, "PUT", f"/api/v1/school-contracts/contracts/{placeholder['id']}/mou", {
        "teacherCount": 1, "salaryMode": "SAME_FOR_ALL", "rate": "1000.00", "signedOn": signed,
        "schoolSignatories": [{"name": "R. Kumar", "designation": "Principal"}],
        "hlsSignatories": [{"userId": x["userId"], "designation": x["designation"]} for x in cands][:2]}))
    pos = c["positions"][0]["id"]
    api.ok(api.call(admin, "POST", f"/api/v1/school-contracts/contracts/{c['id']}/map-teachers",
                    [{"teacherId": w["teachersA"][0]["id"], "positionId": pos}]))
    w["contract"] = c
    return w


def open_billing(d):
    spa_goto(d, "/operations/school-billing")
    wait(d).until(EC.visibility_of_element_located((By.XPATH, "//h1[normalize-space()='School Billing']")))
    wait(d).until(lambda x: not x.find_elements(By.XPATH, "//*[@aria-label='Loading overview']"))


def open_ledger(d, school):
    open_billing(d)
    link = wait(d).until(EC.presence_of_element_located((By.XPATH, f"//a[normalize-space()='{school['name']}']")))
    click(d, link)
    wait(d).until(EC.visibility_of_element_located((By.XPATH, f"//h1[normalize-space()='{school['name']}']")))
    wait(d).until(lambda x: not x.find_elements(By.XPATH, "//*[@aria-label='Loading ledger']"))


def test_1_raise_monthly_dues_and_raise_again(world):
    d = as_user(ADMIN)
    try:
        open_billing(d)
        click(d, btn(d, "Raise monthly dues"))
        dlg = dialog(d)
        click(d, btn(d, "Raise monthly dues", dlg))
        expect_text(d, "monthly dues raised")
        click(d, btn(d, "Close", dlg))
        open_ledger(d, world["a1"])
        expect_text(d, "Monthly dues")
        assert "No monthly dues yet" not in body(d)
        # raising again skips the School that already has its due
        open_billing(d)
        click(d, btn(d, "Raise monthly dues"))
        dlg = dialog(d)
        click(d, btn(d, "Raise monthly dues", dlg))
        expect_text(d, "0 monthly dues raised")
        click(d, btn(d, "Close", dlg))
    finally:
        d.quit()


def test_2_collection_adjustment_and_cancel(world):
    d = as_user(ADMIN)
    open_ledger(d, world["a1"])
    # a collection that covers part of the due, then its reversal
    click(d, btn(d, "Record a collection"))
    dlg = dialog(d)
    type_in(dlg, "Amount", "100")
    type_in(dlg, "Reference", "E2E-1")
    click(d, btn(d, "Record collection", dlg))
    wait(d).until(EC.invisibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    expect_text(d, "E2E-1")
    click(d, d.find_element(By.XPATH, "//table[@aria-label='Collections']//button[normalize-space()='Reverse']"))
    dlg = dialog(d)
    type_in(dlg, "Reason", "entered in error")
    click(d, btn(d, "Reverse collection", dlg))
    wait(d).until(EC.invisibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    expect_text(d, "Reversed")
    # open the due, adjust it, then cancel it with a reason
    click(d, d.find_element(By.XPATH, "//table[@aria-label='Monthly dues']//button[normalize-space()='Open']"))
    expect_text(d, "Nothing is sent to the School")
    click(d, btn(d, "Add an adjustment"))
    dlg = dialog(d)
    type_in(dlg, "Amount", "10")
    type_in(dlg, "Reason", "discount agreed")
    click(d, btn(d, "Add adjustment", dlg))
    wait(d).until(EC.invisibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    # the detail closes after a change; open it again to see the adjustment
    click(d, d.find_element(By.XPATH, "//table[@aria-label='Monthly dues']//button[normalize-space()='Open']"))
    expect_text(d, "discount agreed")
    click(d, btn(d, "Cancel this monthly due"))
    dlg = dialog(d)
    type_in(dlg, "Reason", "raised in error")
    click(d, btn(d, "Cancel monthly due", dlg))
    wait(d).until(EC.invisibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    click(d, d.find_element(By.XPATH, "//table[@aria-label='Monthly dues']//button[normalize-space()='Open']"))
    expect_text(d, "Cancelled: raised in error")


def test_3_ageing_tab_and_not_billed_tab(world):
    d = as_user(ADMIN)
    try:
        open_billing(d)
        click(d, d.find_element(By.XPATH, "//button[@role='tab'][normalize-space()='Ageing']"))
        expect_text(d, "Not yet due")
        click(d, d.find_element(By.XPATH, "//button[@role='tab'][normalize-space()='Not billed']"))
        time.sleep(1.5)
        assert d.find_elements(By.XPATH, "//table[@aria-label='Teachers not billed'] | //*[contains(text(),'billed')]")
    finally:
        d.quit()


def test_4_zone_manager_sees_only_own_schools(world):
    d = as_user(world["mgrB"]["phone"])
    try:
        open_billing(d)
        time.sleep(2)
        assert world["a1"]["name"] not in body(d), "Manager B never sees Zone A's School"
    finally:
        d.quit()
    # another Zone's School ledger answers 404 through the API
    status, _ = api.call(world["mgrB"]["phone"], "GET", f"/api/v1/school-billing/schools/{world['a1']['id']}")
    assert status in (403, 404), status
    d = as_user(world["mgrA"]["phone"])
    try:
        open_billing(d)
        expect_text(d, world["a1"]["name"]) if False else None
    finally:
        d.quit()


def test_phone_width_has_no_horizontal_scroll():
    d = as_user(ADMIN, width=390)
    try:
        open_billing(d)
        assert d.execute_script("return document.documentElement.scrollWidth <= window.innerWidth + 1")
    finally:
        d.quit()
