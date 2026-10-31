"""Browser walkthrough for spec 026 (Teacher Observation and Performance): quickstart.md sections 1, 2, 3 and 6.

Same setup as test_school_working_days.py. Needs the demo seed (Director 9800000002, Manager manoj.manager, Teacher
9800000004; Karthik Raja is a Teacher in Manoj's scope). The run adds demo records (plans, an observation, an issue);
reset with `docker compose -f docker-compose.local.yml down -v` when wanted. Plans it creates are cancelled afterwards.

Not covered: Manager B scope (the demo seed has one Manager), section 4 feedback, section 5 referral, section 7.
"""
import datetime
import time

import pytest
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC

from test_school_working_days import BASE, DIRECTOR, MANAGER, make_driver, sign_in, wait

TEACHER = "Karthik Raja"
ROUTE = f"{BASE}/operations/teacher-performance"
DIALOG = (By.CSS_SELECTOR, "[role=dialog]")


def js_click(d, element):
    d.execute_script("arguments[0].scrollIntoView({block: 'center'}); arguments[0].click()", element)


def pick(d, scope, label, option, contains=False):
    scope.find_element(
        By.XPATH, f".//*[contains(@id,'-label')][starts-with(normalize-space(),'{label}')]/..//div[@role='combobox']"
    ).click()
    test = f"contains(normalize-space(),'{option}')" if contains else f"normalize-space()='{option}'"
    wait(d).until(EC.element_to_be_clickable((By.XPATH, f"//li[@role='option'][{test}]"))).click()
    wait(d).until(EC.invisibility_of_element_located((By.XPATH, "//ul[@role='listbox']")))


def open_route(d, path, h1=None):
    d.get(f"{ROUTE}{path}")
    wait(d).until(EC.visibility_of_element_located((By.XPATH, h1 or "//h1")))
    wait(d).until(lambda x: not x.find_elements(By.CSS_SELECTOR, "[aria-label^='Loading']"))
    time.sleep(0.4)


def plan_rows(d):
    return d.find_elements(By.XPATH, f"//table[@aria-label='Planned observations']//tr[.//a[contains(.,'{TEACHER}')]]")


def cancel_plans(d):
    open_route(d, "/planned")
    while plan_rows(d):
        js_click(d, plan_rows(d)[0].find_element(By.XPATH, ".//button[normalize-space()='Cancel plan']"))
        dlg = wait(d).until(EC.visibility_of_element_located(DIALOG))
        dlg.find_element(By.XPATH, ".//textarea[not(@aria-hidden)] | .//input[@type='text']").send_keys("walkthrough cleanup")
        dlg.find_element(By.XPATH, ".//button[normalize-space()='Cancel plan']").click()
        wait(d).until(EC.invisibility_of_element_located(DIALOG))
        time.sleep(0.6)
        d.refresh()
        time.sleep(1)


def plan(d, on, reason="Management plan"):
    js_click(d, wait(d).until(EC.element_to_be_clickable((By.XPATH, "//button[normalize-space()='Plan an observation']"))))
    dlg = wait(d).until(EC.visibility_of_element_located(DIALOG))
    pick(d, dlg, "Teacher", TEACHER, contains=True)
    box = dlg.find_element(By.XPATH, ".//input[@type='date']")
    # a native date box takes keystrokes in the browser's locale order, so set the value the way React expects
    d.execute_script(
        "var s = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;"
        "s.call(arguments[0], arguments[1]); arguments[0].dispatchEvent(new Event('input', {bubbles: true}));",
        box, on.isoformat())
    pick(d, dlg, "Reason", reason)
    js_click(d, dlg.find_element(By.XPATH, ".//button[normalize-space()='Plan observation']"))
    wait(d).until(EC.invisibility_of_element_located(DIALOG))


@pytest.fixture
def director():
    d = make_driver()
    sign_in(d, DIRECTOR)
    cancel_plans(d)
    yield d
    cancel_plans(d)
    d.quit()


def test_section1_plan_and_replan_keeps_one_plan(director):
    d = director
    open_route(d, "/planned")
    plan(d, datetime.date.today() + datetime.timedelta(days=3))
    wait(d).until(lambda x: len(plan_rows(x)) == 1)
    first = plan_rows(d)[0].text
    assert "Management plan" in first

    plan(d, datetime.date.today() + datetime.timedelta(days=5), reason="School feedback")   # same Teacher again
    time.sleep(1)
    open_route(d, "/planned")
    rows = plan_rows(d)
    assert len(rows) == 1, "planning again must not add a second plan"
    assert rows[0].text != first                                   # date and reason changed


def test_sections2_and_3_manager_records_needs_improvement_and_issue_opens(director):
    d = director
    open_route(d, "/planned")
    plan(d, datetime.date.today() + datetime.timedelta(days=3))
    m = make_driver()
    try:
        sign_in(m, MANAGER)
        open_route(m, "/planned")
        wait(m).until(lambda x: len(plan_rows(x)) == 1)             # the Manager sees the task's plan
        js_click(m, plan_rows(m)[0].find_element(By.XPATH, f".//a[contains(.,'{TEACHER}')]"))
        wait(m).until(EC.visibility_of_element_located((By.XPATH, f"//h1[contains(.,'{TEACHER}')]")))
        js_click(m, m.find_element(By.XPATH, "//button[normalize-space()='Start observation']"))
        wait(m).until(EC.visibility_of_element_located((By.XPATH, "//h2[normalize-space()='Ratings']")))
        time.sleep(1)

        rated = 0
        for box in m.find_elements(By.XPATH, "//div[@role='combobox']"):     # page order: areas, then Overall result
            label = m.execute_script(
                "var e = document.getElementById(arguments[0].getAttribute('aria-labelledby').split(' ')[0]);"
                "return e ? e.textContent : ''", box)
            if label.startswith("Overall result"):
                break
            m.execute_script("arguments[0].scrollIntoView({block: 'center'})", box)
            box.click()                                             # a select needs a real mouse event
            wait(m).until(EC.element_to_be_clickable((By.XPATH, "//li[@role='option'][normalize-space()='2']"))).click()
            wait(m).until(EC.invisibility_of_element_located((By.XPATH, "//ul[@role='listbox']")))
            rated += 1
        assert rated >= 3, f"only {rated} areas found"
        wait(m).until(EC.visibility_of_element_located((By.XPATH, "//*[contains(.,'Average: 2')]")))

        pick(m, m, "Overall result", "Needs improvement")
        for label in ("Strengths", "Areas for improvement"):
            field = m.find_element(By.XPATH, f"//label[contains(.,'{label}')]/..//textarea[not(@aria-hidden)]")
            field.send_keys("Walkthrough note")
        js_click(m, m.find_element(By.XPATH, "//button[normalize-space()='Submit observation']"))
        wait(m).until(EC.visibility_of_element_located((By.XPATH, "//*[contains(.,'Submitted')]")))
        assert m.find_elements(By.XPATH, "//*[contains(.,'Permission not recorded')]")

        open_route(m, "/issues")                                    # section 3: an issue opens at once
        wait(m).until(EC.visibility_of_element_located((By.XPATH, f"//*[contains(.,'{TEACHER}')]")))
        assert m.find_elements(By.XPATH, f"//tr[.//*[contains(.,'{TEACHER}')]][.//*[contains(.,'Open')]]")

        open_route(m, "/planned")                                   # the submitted observation completed the task
        assert not plan_rows(m)
    finally:
        m.quit()


def test_section6_teacher_account_has_no_access():
    d = make_driver()
    try:
        sign_in(d, "9800000004")
        d.get(f"{ROUTE}/planned")
        time.sleep(2)
        assert "not authori" in d.find_element(By.TAG_NAME, "body").text.lower()
    finally:
        d.quit()


def test_lists_have_no_horizontal_page_scroll_at_375px():
    d = make_driver(375, 800)
    try:
        sign_in(d, DIRECTOR)
        for path in ("", "/planned", "/issues", "/feedback"):
            open_route(d, path)
            overflow = d.execute_script(
                "return document.documentElement.scrollWidth - document.documentElement.clientWidth")
            assert overflow <= 0, f"{path or '/'} scrolls sideways by {overflow}px"
    finally:
        d.quit()
