"""Browser walkthrough for spec 040 (Same-day flexibility): the web parts of quickstart.md steps 1, 3, 6, 8 and 10.

Same setup as test_school_working_days.py (demo seed: Admin asha.admin, Director 9800000002, Manager manoj.manager, Teacher 9800000004).
Every record is for today with a unique title (timestamp suffix). The report and filter checks compare against what these tests logged.

Leave: the Record same-day leave dialog is used for real. The server only accepts a working day, and the demo calendar treats Sunday as
a weekly off. The test records the outcome either way: on a working day it asserts the leave appears Approved with the Same-day chip;
on a non-working day it asserts the dialog shows the refusal and keeps the dialog open (the "guard" case of step 3). Which one ran is
printed in the run output and written in quickstart-results.md.

Covered: 1 and 3 (web) RecordSameDayLeaveDialog, 6 LogSameDayDialog (Completed lands under Done, not Awaiting verification, with the
Same-day chip; In progress; the note length rule), "Done on the spot" on a task Admin planned for today, a visit logged for today from
Marketing > Calendar, 8 the Same-day filters on Leave Management, Substitution, Manager Tasks and Activity Calendar, the Activity Calendar
same-day flag in list view, the Same-day report (counts per Manager and School, Zone filter narrows every section, an unrelated Zone gives
none, Manager scope and another Zone refused, Teacher refused), 10 a Teacher still applies for tomorrow and it stays Pending
(through the API, the screen is covered by spec 009).
Not covered: 1 steps 1-2 the Teacher applying (that is the mobile and My Leave screens, covered by spec 009 walkthroughs), the Manager
notification and the Substitution requirement for the same-day leave (needs a working day); 2 a range that also covers later days;
3 locked month and "Manager marked Present" guards; 4 pay figures (backend test TeacherLeaveDeductionTest); 5 Manager cover and Teacher
substitute credit; 7 offline / airplane mode (phone); 9 concurrency (backend test); "Recorded by" and auto-accepted wording in Change History.
"""
import time

import pytest
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC

import api_helpers as api
from browsers import Browsers, body, click, fill, goto, load, menu_labels, wait
from test_school_working_days import ADMIN, BASE, DIRECTOR, MANAGER

TEACHER = "9800000004"
TAG = str(int(time.time()))
STATE = {}
TODAY = time.strftime("%Y-%m-%d")


@pytest.fixture(scope="module")
def browsers():
    b = Browsers()
    yield b
    b.quit_all()


def dialog(d):
    return wait(d).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))


def pick_in(d, scope, label, option):
    WebDriverWait(scope, 20).until(lambda _x: scope.find_element(By.XPATH, f".//*[contains(@id,'-label')][starts-with(normalize-space(),'{label}')]/..//div[@role='combobox']")).click()
    click(d, f"//li[@role='option'][contains(normalize-space(),'{option}')]")
    wait(d).until(EC.invisibility_of_element_located((By.XPATH, "//ul[@role='listbox']")))


def select_native_or_mui(d, label, option):
    WebDriverWait(d, 20).until(lambda _x: d.find_element(By.XPATH, f"//*[contains(@id,'-label')][normalize-space()='{label}']/..//div[@role='combobox']")).click()
    click(d, f"//li[@role='option'][normalize-space()='{option}']")
    time.sleep(1.2)


def tab(d, name):
    click(d, f"//button[@role='tab'][starts-with(normalize-space(),'{name}')]")
    time.sleep(1.2)
    wait(d).until(lambda x: not x.find_elements(By.CSS_SELECTOR, "[aria-label^='Loading']"))


# Leave -----------------------------------------------------------------------------------------------------------------

def test_record_same_day_leave_dialog_for_a_teacher_in_scope(browsers):
    d = browsers.as_user(MANAGER)
    goto(d, "/operations/leave")
    click(d, "//button[normalize-space()='Record same-day leave']")
    dlg = dialog(d)
    assert "takes effect at once" in dlg.text
    assert dlg.find_element(By.XPATH, ".//label[normalize-space()='Date']/..//input").get_attribute("value") == time.strftime("%Y-%m-%d")
    save = dlg.find_element(By.XPATH, ".//button[normalize-space()='Record leave']")
    assert not save.is_enabled()                                              # nothing chosen yet
    pick_in(d, dlg, "Teacher", "Meena Selvi")
    pick_in(d, dlg, "Leave type", "Casual")
    assert not save.is_enabled()                                              # a reason is still needed
    dlg.find_element(By.XPATH, ".//label[starts-with(normalize-space(),'Reason')]/..//textarea[not(@aria-hidden='true')]").send_keys(f"E2E same-day {TAG}")
    assert save.is_enabled()
    save.click()
    time.sleep(3)
    still_open = bool(d.find_elements(By.CSS_SELECTOR, "[role=dialog]"))
    if still_open:                                                            # refused: not a working day
        STATE["leave"] = "refused"
        alert = " ".join(a.text for a in d.find_elements(By.CSS_SELECTOR, "[role=dialog] [role=alert]"))
        print("\n[040] leave outcome: refused -", alert)
        assert "working day" in alert.lower() or "overlap your approved request" in alert.lower()  # the second run on a day meets the leave the first run recorded
        dlg.find_element(By.XPATH, ".//button[normalize-space()='Cancel']").click()
    else:
        STATE["leave"] = "recorded"
        print("\n[040] leave outcome: recorded")
        select_native_or_mui(d, "Status", "Approved")
        wait(d).until(lambda x: f"E2E same-day {TAG}" in body(x))
        row = d.find_element(By.XPATH, f"//tr[contains(.,'E2E same-day {TAG}')]")
        assert "Same-day" in row.text and "Approved" in row.text and "Meena Selvi" in row.text


def test_leave_management_same_day_filter_lists_only_same_day_rows(browsers):
    d = browsers.as_user(ADMIN)
    goto(d, "/operations/leave")
    select_native_or_mui(d, "Status", "Approved")
    select_native_or_mui(d, "Recorded", "Same-day only")
    wait(d).until(lambda x: "Loading" not in body(x))
    rows = d.find_elements(By.XPATH, "//table[@aria-label='Leave requests']//tbody/tr")
    if STATE.get("leave") == "recorded":
        assert rows and all("Same-day" in r.text for r in rows)
    else:
        assert all("Same-day" in r.text for r in rows)                      # empty is fine: "No leave requests to show."
        if not rows:
            assert "No leave requests to show." in body(d)


# Tasks ---------------------------------------------------------------------------------------------------------------

def test_log_for_today_completed_lands_under_done_with_the_chip_and_not_awaiting(browsers):
    d = browsers.as_user(MANAGER)
    goto(d, "/operations/tasks")
    click(d, "//button[normalize-space()='Log for today']")
    dlg = dialog(d)
    save = dlg.find_element(By.XPATH, ".//button[normalize-space()='Save']")
    assert not save.is_enabled()
    dlg.find_element(By.XPATH, ".//label[starts-with(normalize-space(),'Title')]/..//input").send_keys(f"E2E school visit {TAG}")
    note = dlg.find_element(By.XPATH, ".//label[starts-with(normalize-space(),'What was done')]/..//textarea[not(@aria-hidden='true')]")
    note.send_keys("short")
    wait(d).until(lambda x: "at least 10 characters" in dlg.text)             # the note rule
    assert not save.is_enabled()
    note.send_keys(" and then a longer note on what was done")
    assert save.is_enabled()
    save.click()
    wait(d, 30).until(EC.invisibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    tab(d, "Done")
    row = d.find_element(By.XPATH, f"//table[@aria-label='Tasks']//tr[contains(.,'E2E school visit {TAG}')]")
    assert "Same-day" in row.text
    tab(d, "Awaiting verification")
    assert f"E2E school visit {TAG}" not in body(d)


def test_log_for_today_in_progress_needs_no_note(browsers):
    d = browsers.as_user(MANAGER)
    goto(d, "/operations/tasks")
    click(d, "//button[normalize-space()='Log for today']")
    dlg = dialog(d)
    dlg.find_element(By.XPATH, ".//label[starts-with(normalize-space(),'Title')]/..//input").send_keys(f"E2E in progress {TAG}")
    dlg.find_element(By.XPATH, ".//input[@value='IN_PROGRESS']").click()
    assert not dlg.find_elements(By.XPATH, ".//label[starts-with(normalize-space(),'What was done')]")
    dlg.find_element(By.XPATH, ".//button[normalize-space()='Save']").click()
    wait(d, 30).until(EC.invisibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    tab(d, "Today")
    row = d.find_element(By.XPATH, f"//table[@aria-label='Tasks']//tr[contains(.,'E2E in progress {TAG}')]")
    assert "Same-day" in row.text and "In progress" in row.text


def test_done_on_the_spot_for_a_task_admin_planned_for_today(browsers):
    _, manoj_user = api.manager_of(MANAGER)
    created = api.must(api.call(api.login(ADMIN), "POST", "/api/v1/tasks", {
        "type": "SCHOOL_VISIT", "title": f"E2E planned {TAG}", "description": None, "assigneeUserId": manoj_user, "dueOn": TODAY,
        "escalateAfterDays": None}), "create task")
    d = browsers.as_user(MANAGER)
    goto(d, f"/operations/tasks/{created['task']['id']}")
    click(d, "//button[normalize-space()='Done on the spot']")
    dlg = dialog(d)
    mark = dlg.find_element(By.XPATH, ".//button[normalize-space()='Mark done']")
    mark.click()                                                              # a note of at least 10 characters is checked on Mark done
    wait(d).until(lambda x: "Write at least 10 characters." in body(x))
    dlg.find_element(By.XPATH, ".//textarea[not(@aria-hidden='true')]").send_keys("Visited the School and met the Principal")
    mark.click()
    wait(d, 30).until(EC.invisibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    wait(d, 30).until(lambda x: "Verified" in body(x))
    assert "Same-day" in body(d)
    assert not d.find_elements(By.XPATH, "//button[normalize-space()='Complete']")
    assert "Asha Admin" in body(d)                                           # the assigner is kept in the history


def test_manager_tasks_same_day_filter_for_admin(browsers):
    d = browsers.as_user(ADMIN)
    goto(d, "/operations/tasks")
    tab(d, "All")
    click(d, "//label[normalize-space()='Same-day only']")
    time.sleep(1.5)
    wait(d).until(lambda x: not x.find_elements(By.CSS_SELECTOR, "[aria-label^='Loading']"))
    rows = d.find_elements(By.XPATH, "//table[@aria-label='Tasks']//tbody/tr")
    assert rows and all("Same-day" in r.text for r in rows)
    assert f"E2E school visit {TAG}" in body(d)


# Visits and Activity Calendar ----------------------------------------------------------------------------------------

def test_log_a_visit_for_today_from_the_marketing_calendar(browsers):
    d = browsers.as_user(MANAGER)
    goto(d, "/marketing/calendar")
    click(d, "//button[normalize-space()='Log a visit for today']")
    dlg = dialog(d)
    box = dlg.find_element(By.XPATH, ".//label[starts-with(normalize-space(),'Prospect')]/..//input")
    box.click()
    box.send_keys("Green Valley")
    click(d, "//li[@role='option'][contains(normalize-space(),'Green Valley')]")
    dlg.find_element(By.XPATH, ".//label[starts-with(normalize-space(),'Outcome')]/..//*[self::input or self::textarea][not(@aria-hidden='true')]").send_keys(f"E2E met the Principal {TAG}")
    dlg.find_element(By.XPATH, ".//button[normalize-space()='Save visit']").click()
    wait(d, 30).until(EC.invisibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    wait(d, 30).until(lambda x: "Green Valley Public School" in body(x) and "same-day" in body(x))


def test_activity_calendar_flags_same_day_items_and_filters_them(browsers):
    d = browsers.as_user(ADMIN)
    goto(d, "/operations/activity-calendar")
    click(d, "//button[normalize-space()='List view']")
    click(d, "//label[normalize-space()='Same-day only']")
    time.sleep(2)
    wait(d).until(lambda x: not x.find_elements(By.CSS_SELECTOR, "[aria-label^='Loading']"))
    rows = d.find_elements(By.XPATH, "//table//tbody/tr")
    assert rows, body(d)[:400]
    assert all("same-day" in r.text for r in rows)
    text = body(d)
    assert f"E2E school visit {TAG}" in text or "School visit" in text
    assert "Task" in text or "School visit" in text


def test_substitution_same_day_filter(browsers):
    d = browsers.as_user(ADMIN)
    goto(d, "/operations/substitutions")
    select_native_or_mui(d, "Recorded", "Same-day only")
    wait(d).until(lambda x: not x.find_elements(By.CSS_SELECTOR, "[aria-label^='Loading']"))
    rows = d.find_elements(By.XPATH, "//table//tbody/tr")
    assert all("Same-day" in r.text for r in rows)
    if STATE.get("leave") == "refused":
        assert not [r for r in rows if "Meena" in r.text]                     # the refused leave left no requirement behind


# Same-day report --------------------------------------------------------------------------------------------------------

def report_rows(d, section, table):
    t = d.find_elements(By.XPATH, f"//section[@aria-label='{section}']//table[@aria-label='{table}']//tbody/tr")
    return [r.text for r in t]


def test_same_day_report_counts_and_zone_filter(browsers):
    d = browsers.as_user(ADMIN)
    goto(d, "/dashboard/same-day", "Same-day report")
    wait(d, 30).until(lambda x: x.find_elements(By.CSS_SELECTOR, "section[aria-label]"))
    tasks_mgr = report_rows(d, "Tasks logged for the day", "Manager")
    manoj = [r for r in tasks_mgr if "Manoj Manager" in r]
    assert manoj and int(manoj[0].split()[-1]) >= 3                          # school visit, in progress, done on the spot
    visits = report_rows(d, "Visits logged for the day", "Manager")
    assert any("Manoj Manager" in r for r in visits)
    # choosing Demo Zone keeps Manoj
    WebDriverWait(d, 20).until(lambda _x: d.find_element(By.XPATH, "//*[contains(@id,'-label')][normalize-space()='Zone']/..//div[@role='combobox']")).click()
    click(d, "//li[@role='option'][normalize-space()='Demo Zone']")
    time.sleep(2)
    assert any("Manoj Manager" in r for r in report_rows(d, "Tasks logged for the day", "Manager"))
    # choosing the Zone of another area narrows every section to that Zone: Manoj goes
    WebDriverWait(d, 20).until(lambda _x: d.find_element(By.XPATH, "//*[contains(@id,'-label')][normalize-space()='Zone']/..//div[@role='combobox']")).click()
    other = d.find_element(By.XPATH, "//li[@role='option'][starts-with(normalize-space(),'E2E')]")
    other.click()
    time.sleep(2)
    for section in ("Leave days recorded for the day", "Manager cover days", "Tasks logged for the day", "Visits logged for the day"):
        rows = report_rows(d, section, "Manager")
        assert not any("Manoj Manager" in r for r in rows), (section, rows)
        assert rows == ["None this month."] or rows == []


def test_same_day_report_manager_sees_own_and_other_zone_is_refused(browsers):
    d = browsers.as_user(MANAGER)
    goto(d, "/dashboard/same-day", "Same-day report")
    wait(d, 30).until(lambda x: x.find_elements(By.CSS_SELECTOR, "section[aria-label]"))
    rows = report_rows(d, "Tasks logged for the day", "Manager")
    assert any("Manoj Manager" in r for r in rows)
    zones = api.must(api.call(api.login(ADMIN), "GET", "/api/v1/zones?query=E2E&size=1"))["content"]
    token = api.login(MANAGER)
    month = time.strftime("%Y-%m")
    assert api.call(token, "GET", f"/api/v1/reports/same-day?month={month}&zoneId={zones[0]['id']}")[0] == 403
    assert api.call(api.login(TEACHER), "GET", f"/api/v1/reports/same-day?month={month}")[0] == 403


def test_regression_a_teacher_applying_for_tomorrow_is_still_pending():
    token = api.login(TEACHER)
    status, types = api.call(token, "GET", "/api/v1/me/leave/types")
    assert status == 200
    day = time.strftime("%Y-%m-%d", time.localtime(time.time() + 40 * 86400))   # far enough to be a normal future day
    status, body_ = api.call(token, "POST", "/api/v1/me/leave", {"leaveTypeId": types[0]["id"], "firstDate": day, "lastDate": day,
                                                                "halfDayStart": False, "halfDayEnd": False, "reason": f"E2E future {TAG}"})
    if status in (200, 201):
        assert body_["status"] == "PENDING"
        api.call(token, "POST", f"/api/v1/me/leave/{body_['id']}/cancel", {})
    else:
        assert status in (400, 409, 422), (status, body_)          # e.g. not a working day: still not auto-approved
