"""Browser walkthrough for spec 013b (Payroll Run): quickstart.md scenarios 1, 2, 3, 6, 7, 8, 16 and 19 (the export button).

Needs the local stack (docker-compose.local.yml) and Chrome. Run from this folder:
    pip install -r requirements.txt
    pytest test_payroll_run.py -v                  # headless
    HEADED=1 pytest test_payroll_run.py -v -s      # watch it

Uses one ended month with no live run (PAYROLL_MONTH, default July 2026). By default the run it starts is returned and
cancelled at the end, so nothing final is left behind. An approved run can never be removed, so approving it is opt-in:
    APPROVE=1 pytest test_payroll_run.py -v
"""
import json
import os
import urllib.error
import urllib.request

import pytest
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

BASE = os.environ.get("BASE_URL", "http://localhost:5173")
PASSWORD = "Password123!"
ADMIN, DIRECTOR, MANAGER = "asha.admin", "9800000002", "manoj.manager"
MONTH = os.environ.get("PAYROLL_MONTH", "July 2026")
MONTH_KEY = {"July 2026": "2026-07"}.get(MONTH, os.environ.get("PAYROLL_MONTH_KEY", "2026-07"))
EXISTING_MONTH = os.environ.get("EXISTING_MONTH", "September 2026")  # a month that already has a primary run
APPROVE = bool(os.environ.get("APPROVE"))
DIALOG = (By.CSS_SELECTOR, "[role=dialog]")


def make_driver(width=1280, height=900):
    opts = webdriver.ChromeOptions()
    if not os.environ.get("HEADED"):
        opts.add_argument("--headless=new")
    opts.add_argument(f"--window-size={width},{height}")
    d = webdriver.Chrome(options=opts)
    d.set_window_size(width, height)
    return d


def wait(d, timeout=20):
    return WebDriverWait(d, timeout)


def sign_in(d, user):
    d.get(f"{BASE}/sign-in")
    wait(d).until(EC.visibility_of_element_located((By.XPATH, "//input[@type='password']")))
    d.find_element(By.XPATH, "//label[contains(.,'Phone number or username')]/..//input").send_keys(user)
    d.find_element(By.XPATH, "//input[@type='password']").send_keys(PASSWORD, Keys.ENTER)
    wait(d).until(lambda x: "/sign-in" not in x.current_url)


def open_runs(d):
    d.get(f"{BASE}/operations/payroll")
    wait(d).until(EC.visibility_of_element_located((By.XPATH, "//h1[normalize-space()='Payroll']")))
    wait(d).until(lambda x: not x.find_elements(By.XPATH, "//*[normalize-space()='Loading the pay runs…']"))


def button(d, label):
    return wait(d).until(EC.element_to_be_clickable((By.XPATH, f"//button[normalize-space()='{label}']")))


def has_button(d, label):
    return bool(d.find_elements(By.XPATH, f"//button[normalize-space()='{label}']"))


def see(d, text):
    return wait(d).until(EC.visibility_of_element_located((By.XPATH, f"//*[contains(normalize-space(),'{text}')]")))


MONTH_BOX = (By.XPATH, "//*[starts-with(normalize-space(),'Month')][contains(@id,'-label')]/..//div[@role='combobox']")


def pick_month(d, month):
    wait(d).until(EC.element_to_be_clickable(MONTH_BOX)).click()
    wait(d).until(EC.element_to_be_clickable((By.XPATH, f"//li[@role='option'][normalize-space()='{month}']"))).click()
    wait(d).until(EC.invisibility_of_element_located((By.XPATH, "//ul[@role='listbox']")))


def pick_month_listing(d, month):
    """Open the Month select, return every option offered, then choose `month`."""
    wait(d).until(EC.element_to_be_clickable(MONTH_BOX)).click()
    items = wait(d).until(EC.presence_of_all_elements_located((By.XPATH, "//li[@role='option']")))
    names = [i.text for i in items]
    wait(d).until(EC.element_to_be_clickable((By.XPATH, f"//li[@role='option'][normalize-space()='{month}']"))).click()
    wait(d).until(EC.invisibility_of_element_located((By.XPATH, "//ul[@role='listbox']")))
    return names


def wait_computed(d):
    """The run page shows 'Computing: x of y' until the computation is done; Submit/Compute again enable then."""
    wait(d, 60).until(lambda x: not x.find_elements(By.XPATH, "//*[starts-with(normalize-space(),'Computing:')]"))
    wait(d, 60).until(lambda x: x.find_element(By.XPATH, "//h1").is_displayed())


def give_reason(d, label, text, confirm):
    dlg = wait(d).until(EC.visibility_of_element_located(DIALOG))
    dlg.find_element(By.XPATH, f".//label[contains(.,'{label}')]/..//textarea[not(@aria-hidden='true')]").send_keys(text)
    dlg.find_element(By.XPATH, f".//button[normalize-space()='{confirm}']").click()
    wait(d).until(EC.invisibility_of_element_located(DIALOG))


def api(user, method, path, body=None):
    """Setup and cleanup only; the steps under test go through the browser."""
    def call(url, data=None, token=None):
        req = urllib.request.Request(url, method=method if token else "POST", data=data,
                                     headers={"Content-Type": "application/json",
                                              **({"Authorization": f"Bearer {token}"} if token else {})})
        try:
            with urllib.request.urlopen(req) as r:
                return json.loads(r.read() or b"null")
        except urllib.error.HTTPError as e:
            return {"error": e.code}
    token = call(f"{BASE}/api/v1/auth/login", json.dumps({"identifier": user, "password": PASSWORD}).encode())["accessToken"]
    return call(f"{BASE}/api/v1/payroll{path}", json.dumps(body).encode() if body is not None else None, token)


def runs_of_month():
    return [r for r in api(ADMIN, "GET", "/runs?size=100")["content"] if r["month"] == MONTH_KEY]


@pytest.fixture
def clean_month():
    """The test month has no live run before and after; skip if an earlier approved or waiting run is in the way."""
    def clear():
        for r in runs_of_month():
            if r["status"] == "AWAITING_APPROVAL":
                api(DIRECTOR, "POST", f"/runs/{r['id']}/return", {"reason": "e2e cleanup"})
            if r["status"] in ("DRAFT", "AWAITING_APPROVAL"):
                api(ADMIN, "POST", f"/runs/{r['id']}/cancel", {"reason": "e2e cleanup"})
    clear()
    if any(r["status"] == "APPROVED" for r in runs_of_month()):
        pytest.skip(f"{MONTH} already has an approved run; set PAYROLL_MONTH to another ended month")
    yield
    clear()


@pytest.fixture
def admin():
    d = make_driver()
    sign_in(d, ADMIN)
    yield d
    d.quit()


@pytest.fixture
def director():
    d = make_driver()
    sign_in(d, DIRECTOR)
    yield d
    d.quit()


def test_current_month_is_not_offered_and_a_second_run_is_refused(admin):
    """Scenarios 2 and 3."""
    open_runs(admin)
    offered = pick_month_listing(admin, EXISTING_MONTH)
    assert offered, "no month offered"
    assert "October 2026" not in offered, "the current month must not be offered"
    button(admin, "Start run").click()
    alert = wait(admin).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "[role=alert]")))
    assert alert.text.strip(), "a refusal message should be shown"
    assert admin.find_element(By.XPATH, "//h1").text == "Payroll", "should stay on the list after a refusal"


def test_run_review_submit_return_resubmit_and_approval(admin, director, clean_month):
    """Scenarios 1, 6, 7, 8 and 19 (the export button)."""
    open_runs(admin)
    pick_month(admin, MONTH)
    button(admin, "Start run").click()

    # 1: the draft run opens and finishes computing; totals and lines are shown
    wait(admin).until(EC.visibility_of_element_located((By.XPATH, f"//h1[contains(.,'{MONTH}')]")))
    wait_computed(admin)
    for label in ("Earned", "Deductions", "Adjustments", "Net pay", "People paid", "Cannot be paid"):
        assert admin.find_elements(By.XPATH, f"//dt[normalize-space()='{label}']"), f"total '{label}' missing"
    see(admin, "Draft")
    assert has_button(admin, "Export CSV"), "export is offered for a draft (scenario 19)"
    run_url = admin.current_url

    # 6: open a line's working
    shows = admin.find_elements(By.XPATH, "//button[starts-with(@aria-label,'Show working for ')]")
    if shows:
        shows[0].click()
        dlg = wait(admin).until(EC.visibility_of_element_located(DIALOG))
        wait(admin).until(lambda x: not x.find_elements(By.XPATH, "//*[normalize-space()='Loading the working…']"))
        for heading in ("Earned pay", "Net pay"):
            assert dlg.find_elements(By.XPATH, f".//*[normalize-space()='{heading}' or @aria-label='{heading}']"), heading
        dlg.send_keys(Keys.ESCAPE)
        wait(admin).until(EC.invisibility_of_element_located(DIALOG))

    # 7: the submitter has no Approve button once the run waits
    button(admin, "Submit for approval").click()
    see(admin, "waiting for approval")
    assert not has_button(admin, "Approve"), "the submitter must not be offered Approve"
    assert not has_button(admin, "Cancel run"), "a waiting run cannot be cancelled"

    # 8: the Director returns it with a reason; the Admin sees the reason
    director.get(run_url)
    see(director, "waiting for approval")
    button(director, "Return to draft").click()
    give_reason(director, "Reason for returning", "Check the Manager figures", "Return to draft")
    admin.get(run_url)
    see(admin, "Check the Manager figures")
    wait_computed(admin)

    button(admin, "Submit for approval").click()
    see(admin, "waiting for approval")

    director.get(run_url)
    see(director, "waiting for approval")
    assert has_button(director, "Approve"), "the Director who did not submit is offered Approve"
    if APPROVE:
        button(director, "Approve").click()
        see(director, "final and locked")
        assert has_button(director, "Download all payslips")
        assert not has_button(director, "Compute again"), "an approved run cannot be recomputed"
        assert not has_button(director, "Cancel run"), "an approved run cannot be cancelled"
    # otherwise the clean_month fixture returns and cancels it


def test_manager_has_no_payroll_screen():
    """Scenario 16 (the Teacher is covered by PayrollAuthorizationTest)."""
    d = make_driver()
    try:
        sign_in(d, MANAGER)
        d.get(f"{BASE}/operations/payroll")
        wait(d).until(lambda x: x.find_element(By.TAG_NAME, "body").text.strip() != "")
        assert not d.find_elements(By.XPATH, "//table[@aria-label='Pay runs']")
        assert not d.find_elements(By.XPATH, "//h2[normalize-space()='Start a pay run']")
        assert "₹" not in d.find_element(By.TAG_NAME, "body").text, "no pay figures for a Manager"
    finally:
        d.quit()
