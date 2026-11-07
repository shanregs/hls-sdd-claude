"""Browser walkthrough for spec 013b (T064's missing manual walk): start, adjustment, Compute again, submit, approve, pay, files.

Needs the local stack and Chrome. Run from this folder:
    PAYROLL_WALK_MONTH="May 2026" PAYROLL_WALK_KEY=2026-05 pytest test_payroll_full_walk.py -v -s

It ends by approving the run, which can never be removed, so it needs an ended month with no live primary run; it skips
otherwise. Quickstart scenarios covered here: 1, 6, 7, 8, 9 (buttons gone), 11, 17, 19 and the payslips zip of 15.
"""
import glob
import os
import tempfile
import time

import pytest
from selenium import webdriver
from selenium.common.exceptions import TimeoutException
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support import expected_conditions as EC

from test_payroll_run import (ADMIN, BASE, DIALOG, DIRECTOR, api as _api, button, has_button, open_runs, pick_month, see, sign_in as _sign_in,
                              wait, wait_computed)

MONTH = os.environ.get("PAYROLL_WALK_MONTH", "May 2026")
KEY = os.environ.get("PAYROLL_WALK_KEY", "2026-05")
AMOUNT = "777"


def api(*args, **kwargs):
    """The shared stack allows 30 sign-ins a minute for everybody; wait and try again when it says no."""
    for _ in range(6):
        try:
            return _api(*args, **kwargs)
        except KeyError:
            time.sleep(15)
    return _api(*args, **kwargs)


def sign_in(d, user):
    for _ in range(6):
        try:
            return _sign_in(d, user)
        except TimeoutException:        # most likely the shared sign-in limit
            time.sleep(30)
    _sign_in(d, user)


def make_driver(download_dir):
    opts = webdriver.ChromeOptions()
    if not os.environ.get("HEADED"):
        opts.add_argument("--headless=new")
    opts.add_argument("--window-size=1280,1000")
    opts.add_experimental_option("prefs", {"download.default_directory": download_dir,
                                           "download.prompt_for_download": False})
    d = webdriver.Chrome(options=opts)
    d.set_window_size(1280, 1000)
    return d


def wait_file(folder, pattern, timeout=40):
    end = time.time() + timeout
    while time.time() < end:
        found = [f for f in glob.glob(os.path.join(folder, pattern)) if not f.endswith(".crdownload")]
        if found:
            return found[0]
        time.sleep(0.5)
    raise AssertionError(f"no {pattern} downloaded into {folder}")


def run_of_month():
    return [r for r in api(ADMIN, "GET", "/runs?size=100")["content"] if r["month"] == KEY]


def row_of(d, name):
    return wait(d).until(EC.visibility_of_element_located(
        (By.XPATH, f"//table[@aria-label='Lines of the pay run']//tr[td[1][starts-with(normalize-space(),'{name}')]]")))


def cells(row):
    return [c.text.strip() for c in row.find_elements(By.TAG_NAME, "td")]


def num(s):
    return float(s.replace("₹", "").replace(",", "").replace("−", "-").strip() or 0)


def mark_paid(admin, run_url, name, net):
    """Scenario 17: the Payment dialog of an approved run's line; the amounts do not change."""
    admin.get(run_url)
    see(admin, "final and locked")
    admin.find_element(By.XPATH, f"//button[@aria-label='Payment for {name}']").click()
    dlg = wait(admin).until(EC.visibility_of_element_located(DIALOG))
    wait(admin).until(lambda x: not x.find_elements(By.XPATH, "//*[normalize-space()='Loading the payment details…']"))
    switch = dlg.find_element(By.XPATH, ".//input[@type='checkbox']")
    time.sleep(1.5)                          # the dialog fills its form from the loaded payment; it opens as Paid
    if not switch.is_selected():
        switch.click()
    wait(admin).until(EC.element_to_be_clickable(
        (By.XPATH, "//div[@role='dialog']//div[@role='combobox']"))).click()
    wait(admin).until(EC.element_to_be_clickable((By.XPATH, "//li[@role='option'][normalize-space()='UPI']"))).click()
    ref = wait(admin).until(EC.visibility_of_element_located(
        (By.XPATH, "//div[@role='dialog']//label[contains(.,'Reference')]/..//input")))
    ref.send_keys(Keys.CONTROL, "a", Keys.DELETE)      # a payment already on the line pre-fills its reference
    ref.send_keys("E2E-REF-1")
    dlg.find_element(By.XPATH, ".//button[normalize-space()='Save payment']").click()
    # the dialog stays open and lists the entry in its history
    wait(admin).until(lambda x: "reference E2E-REF-1 (entered by" in x.find_element(By.CSS_SELECTOR, "[role=dialog]").text)
    dlg.find_element(By.XPATH, ".//button[normalize-space()='Close']").click()
    wait(admin).until(EC.invisibility_of_element_located(DIALOG))
    wait(admin).until(lambda x: "Paid on" in row_of(x, name).text)
    assert "UPI" in row_of(admin, name).text
    assert num(cells(row_of(admin, name))[5]) == net, "payment must not change the amounts"


def test_full_payroll_run_walk():
    live = [r for r in run_of_month() if r["status"] != "CANCELLED"]
    if live:
        pytest.skip(f"{MONTH} already has a {live[0]['status']} run; set PAYROLL_WALK_MONTH and PAYROLL_WALK_KEY")
    folder = tempfile.mkdtemp(prefix="payroll-walk-")
    admin, director = make_driver(folder), make_driver(folder)
    try:
        sign_in(admin, ADMIN)
        sign_in(director, DIRECTOR)

        # 1: start the run, it computes, lines and totals show
        open_runs(admin)
        pick_month(admin, MONTH)
        button(admin, "Start run").click()
        wait(admin).until(EC.visibility_of_element_located((By.XPATH, f"//h1[contains(.,'{MONTH}')]")))
        wait_computed(admin)
        see(admin, "Draft")
        run_url = admin.current_url
        rows = admin.find_elements(By.XPATH, "//table[@aria-label='Lines of the pay run']//tbody/tr")
        assert rows, "the run has no lines"
        name = cells(rows[0])[0].split("\n")[0]
        before = cells(row_of(admin, name))
        print("person:", name, before)

        # 11: record a correction for that person on the adjustments screen
        admin.get(f"{BASE}/operations/payroll/adjustments")
        wait(admin).until(EC.visibility_of_element_located((By.XPATH, "//h2[normalize-space()='Record an adjustment']")))
        box = admin.find_element(By.XPATH, "//label[contains(.,'Person')]/..//input")
        box.send_keys(name)
        wait(admin).until(EC.element_to_be_clickable(
            (By.XPATH, f"//li[@role='option'][starts-with(normalize-space(),'{name}')]"))).click()
        admin.find_element(By.XPATH, "//label[contains(.,'Amount')]/..//input").send_keys(AMOUNT)
        admin.find_element(By.XPATH, "//label[contains(.,'Reason')]/..//textarea[not(@aria-hidden='true')]").send_keys(
            "e2e walk correction")
        button(admin, "Record adjustment").click()
        see(admin, "The adjustment was recorded")

        # the draft says an adjustment waits; Compute again picks it up (the PR 171 bug: must end DONE, attached once)
        admin.get(run_url)
        wait_computed(admin)
        see(admin, "waiting since this run was computed")
        button(admin, "Compute again").click()
        time.sleep(1)
        wait_computed(admin)
        wait(admin, 60).until(lambda x: not x.find_elements(
            By.XPATH, "//*[contains(.,'computation failed') or contains(.,'computation stopped')]"))
        assert not admin.find_elements(By.XPATH, "//*[contains(normalize-space(),'waiting since this run was computed')]")
        after = cells(row_of(admin, name))
        print("after compute again:", after)
        assert after[4].count("777") == 1 and after[4] != before[4], f"adjustment not on the line: {after}"
        assert abs((num(after[5]) - num(before[5])) - 777) < 0.01, f"net {before[5]} -> {after[5]}"
        assert api(ADMIN, "GET", f"/runs/{run_url.rsplit('/', 1)[1]}")["computeState"] == "DONE"

        # 19: export CSV of the draft
        button(admin, "Export CSV").click()
        text = open(wait_file(folder, "*.csv"), encoding="utf-8-sig").read()
        assert name in text

        # 7 and 8: submit; the submitter cannot approve; the Director approves
        button(admin, "Submit for approval").click()
        see(admin, "waiting for approval")
        assert not has_button(admin, "Approve")
        director.get(run_url)
        see(director, "waiting for approval")
        button(director, "Approve").click()
        see(director, "final and locked")
        assert has_button(director, "Download all payslips")
        for gone in ("Compute again", "Cancel run", "Submit for approval"):   # 9
            assert not has_button(director, gone), gone

        # payslips zip
        button(director, "Download all payslips").click()
        assert os.path.getsize(wait_file(folder, "*.zip")) > 0

        # 17: mark the person paid
        mark_paid(admin, run_url, name, num(after[5]))

        # export of the approved run
        for f in glob.glob(os.path.join(folder, "*.csv")):
            os.remove(f)
        button(admin, "Export CSV").click()
        assert name in open(wait_file(folder, "*.csv"), encoding="utf-8-sig").read()
    finally:
        admin.quit()
        director.quit()


def test_mark_a_line_paid_on_an_approved_run():
    """Scenario 17 on its own, for when every ended month already has its approved run (the full walk above skips)."""
    approved = [r for r in api(ADMIN, "GET", "/runs?size=100")["content"] if r["status"] == "APPROVED"]
    if not approved:
        pytest.skip("no approved run to record a payment on")
    run = sorted(approved, key=lambda r: r["month"])[-1]
    folder = tempfile.mkdtemp(prefix="payroll-walk-")
    admin = make_driver(folder)
    try:
        sign_in(admin, ADMIN)
        run_url = f"{BASE}/operations/payroll/{run['id']}"
        admin.get(run_url)
        see(admin, "final and locked")
        wait_computed(admin)
        row = wait(admin).until(EC.visibility_of_element_located(
            (By.XPATH, "//table[@aria-label='Lines of the pay run']//tbody/tr[.//button[starts-with(@aria-label,'Payment for ')]]")))
        name = cells(row)[0].split("\n")[0]
        mark_paid(admin, run_url, name, num(cells(row_of(admin, name))[5]))
    finally:
        admin.quit()
