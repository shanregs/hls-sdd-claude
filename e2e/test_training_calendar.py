"""Browser walkthrough for spec 024 (Training Calendar): quickstart.md sections 1 to 6, task T088.

Needs the local stack (scripts/run-all.ps1 with demo data) and Chrome. Run from this folder:
    pip install -r requirements.txt
    pytest test_training_calendar.py -v                # headless
    HEADED=1 pytest test_training_calendar.py -v -s    # watch it

The tests run in order and share one private "world" built through the API at the start (two Zones, Schools A1, A2 and B1,
Manager A and Manager B, Teachers T1, T2, T4 (placed, T4 later put on leave), T5 under A and T3 under B, all able to sign
in; names carry a timestamp so nothing meets other data and nothing is deleted). Everything under test goes through the
browser: creating and moving sessions, registering, patterns, assigning (a Manager's group and Specific people with the
person picker), "I cannot attend", recording attendance (Manager A, then Admin with a walk-in found by the picker),
exceptions, history and the Activity Calendar feed.

Covered: quickstart 1.1 to 1.3, 2.1 and 2.2 (except the generation job), 3.1 and 3.2, 4.1, 4.2 (Present, Absent with reason,
walk-in, half-day correction), 5.1 (close), 5.3, 6.1, role checks (Manager, Teacher, Director views), the empty state and
phone width.
Not covered: notifications (moved date, cancelled, assigned, reminders: need the inbox of each person), running the
generation and reminder jobs (no endpoint), the clash with a school Present mark and the 008 day value / month lock
(4.2 and 4.3: need the 008 attendance screens), a Teacher with approved leave showing "On leave", the repeat flag and the
No-show rule (5.1 and 5.2: need a second held session in six months), the Field day link (6.2), timing limits and the
screen-reader and keyboard check of the attendance grid (T088 stays manual for those).
"""
import os
import time
from datetime import date, timedelta

import pytest
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import Select, WebDriverWait

import walk_support as api

BASE = api.BASE
PASSWORD = api.PASSWORD
ADMIN, DIRECTOR = "asha.admin", "9800000002"
TODAY = date.today()
S = {}  # state shared by the ordered tests


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
    for attempt in range(6):  # the nginx in front allows 30 auth calls a minute for the whole machine, so a busy minute needs a pause
        d.get(f"{BASE}/sign-in")
        wait(d).until(EC.visibility_of_element_located((By.XPATH, "//input[@type='password']")))
        d.find_element(By.XPATH, "//label[contains(.,'Phone number or username')]/..//input").send_keys(user)
        d.find_element(By.XPATH, "//input[@type='password']").send_keys(PASSWORD, Keys.ENTER)
        try:
            wait(d, 20).until(lambda x: "/sign-in" not in x.current_url)
            wait(d, 30).until(lambda x: x.find_elements(By.XPATH, "//h1 | //nav | //header"))
            time.sleep(1)
            return
        except Exception:
            if attempt == 5:
                raise AssertionError("sign-in did not finish; the page says: " + d.find_element(By.TAG_NAME, "body").text[:300])
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


def js_click(d, el):
    d.execute_script("arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", el)


def btn(d, label, exact=True):
    """The last visible, enabled button with this text (the innermost dialog's)."""
    xp = f"//button[normalize-space()='{label}']" if exact else f"//button[contains(normalize-space(),'{label}')]"

    def find(x):
        els = [e for e in x.find_elements(By.XPATH, xp) if e.is_displayed() and e.is_enabled()]
        return els[-1] if els else False
    return wait(d).until(find)


def click(d, label, exact=True):
    js_click(d, btn(d, label, exact))


def has_btn(d, label, exact=True):
    xp = f"//button[normalize-space()='{label}']" if exact else f"//button[contains(normalize-space(),'{label}')]"
    return any(e.is_displayed() for e in d.find_elements(By.XPATH, xp))


def see(d, text, timeout=20):
    return wait(d, timeout).until(EC.visibility_of_element_located((By.XPATH, f"//*[contains(normalize-space(),\"{text}\")]")))


def not_see(d, text):
    return not any(e.is_displayed() for e in d.find_elements(By.XPATH, f"//*[contains(normalize-space(),\"{text}\")]"))


def js_set(d, el, value):
    d.execute_script(
        "const el=arguments[0];const p=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;"
        "Object.getOwnPropertyDescriptor(p,'value').set.call(el,arguments[1]);"
        "el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));",
        el, value)


def field(d, label, tag="input"):
    """A visible field by its label, searched in the innermost dialog when one is open."""
    xp = f"//label[starts-with(normalize-space(),\"{label}\")]/..//{tag}[not(@aria-hidden='true')]"

    def find(x):
        els = [e for e in x.find_elements(By.XPATH, xp) if e.is_displayed() or e.get_attribute("type") in ("date", "time", "month")]
        return els[-1] if els else False
    return wait(d).until(find)


def fill(d, label, value, tag="input"):
    el = field(d, label, tag)
    if el.get_attribute("type") in ("date", "time", "month"):
        js_set(d, el, value)
    else:
        el.send_keys(Keys.CONTROL, "a")
        el.send_keys(value)


def choose(d, label, text):
    Select(field(d, label, "select")).select_by_visible_text(text)


def pick_person(d, label, name):
    box = field(d, label)
    box.click()
    box.send_keys(name)
    opt = wait(d).until(EC.element_to_be_clickable((By.XPATH, f"//li[@role='option'][contains(normalize-space(),\"{name}\")]")))
    js_click(d, opt)


def no_page_overflow(d):
    return d.execute_script("return document.documentElement.scrollWidth - document.documentElement.clientWidth") <= 0


def open_calendar(d, route="/operations/training", title="Training Calendar"):
    # the browser is shared across steps: leave the page first so it opens fresh (on the Sessions tab, current month)
    spa_goto(d, "/dashboard")
    wait(d).until(lambda x: not x.find_elements(By.XPATH, f"//h1[normalize-space()='{title}']"))
    spa_goto(d, route)
    try:
        wait(d, 40).until(EC.visibility_of_element_located((By.XPATH, f"//h1[normalize-space()='{title}']")))
    except Exception:
        raise AssertionError(f"{title} did not open at {d.current_url}; the page says: " + d.find_element(By.TAG_NAME, "body").text[:300])
    wait(d).until(lambda x: not x.find_elements(By.XPATH, "//*[@aria-label='Loading']"))


def goto_month(d, iso):
    target = date.fromisoformat(iso).strftime("%B %Y")
    for _ in range(14):
        if any(e.text == target for e in d.find_elements(By.XPATH, "//h2")):
            break
        click(d, "Next month")
        time.sleep(0.4)
    wait(d).until(lambda x: any(e.text == target for e in x.find_elements(By.XPATH, "//h2")))
    wait(d).until(lambda x: not x.find_elements(By.XPATH, "//*[@aria-label='Loading']"))


def open_session(d, topic, iso):
    goto_month(d, iso)
    js_click(d, wait(d).until(EC.presence_of_element_located((By.XPATH, f"//button[starts-with(@aria-label,\"{topic},\")]"))))
    wait(d).until(EC.visibility_of_element_located((By.XPATH, f"//*[@role='dialog']//h2[normalize-space()=\"{topic}\"]")))


def close_all_dialogs(d):
    for _ in range(4):
        if not has_btn(d, "Close"):
            break
        click(d, "Close")
        time.sleep(0.3)


def new_session(d, kind, topic, iso, start="10:00", end="13:00", seats=""):
    click(d, "New session")
    wait(d).until(EC.visibility_of_element_located((By.XPATH, "//*[@role='dialog']//h2[normalize-space()='New training session']")))
    choose(d, "Type", kind)
    fill(d, "Topic", topic)
    fill(d, "Date", iso)
    fill(d, "Starts at", start)
    fill(d, "Ends at", end)
    fill(d, "Trainer", "E2E Trainer")
    if seats:
        fill(d, "Seat limit", seats)
    click(d, "Save and publish")
    wait(d).until(EC.visibility_of_element_located((By.XPATH, f"//*[@role='dialog']//h2[normalize-space()=\"{topic}\"]")))


def teacher_row(d, topic):
    try:
        return wait(d).until(EC.visibility_of_element_located((By.XPATH, f"//li[.//strong[normalize-space()=\"{topic}\"]]")))
    except Exception:
        body = d.find_element(By.TAG_NAME, "body").text
        raise AssertionError(f"No row for {topic!r} (topic in page text: {topic in body}; rows: {len(d.find_elements(By.XPATH, '//ul/li'))}); the page says: " + body.split("Upcoming Training")[-1][:300])


def teacher_open_upcoming(d):
    # the browser is shared across steps, so leave the page first: opening the same route again would show the list loaded earlier
    spa_goto(d, "/dashboard")
    wait(d).until(lambda x: not x.find_elements(By.XPATH, "//h1[normalize-space()='Upcoming Training']"))
    spa_goto(d, "/my-attendance/training")
    try:
        wait(d).until(EC.visibility_of_element_located((By.XPATH, "//h1[normalize-space()='Upcoming Training']")))
    except Exception:
        raise AssertionError("Upcoming Training did not open; the page says: " + d.find_element(By.TAG_NAME, "body").text[:300])
    wait(d).until(lambda x: not x.find_elements(By.XPATH, "//*[@aria-label='Loading']"))


def row_button(d, topic, label):
    return teacher_row(d, topic).find_element(By.XPATH, f".//button[normalize-space()='{label}']")


def row_has(d, topic, label):
    return bool(teacher_row(d, topic).find_elements(By.XPATH, f".//button[normalize-space()='{label}']"))


@pytest.fixture(scope="module")
def world():
    w = api.build_world(api.stamp(), teachers_a=4, teachers_b=1)
    # T4 (index 2 of Zone A) is on leave, so registration is refused for them
    api.ok(api.call("asha.admin", "POST", f"/api/v1/teachers/{w['teachersA'][2]['id']}/status",
                    {"status": "ON_LEAVE", "effectiveOn": TODAY.isoformat()}))
    S["w"] = w
    S["weekend"] = f"E2E Weekend {w['tag']}"
    S["monthly"] = f"E2E Monthly {w['tag']}"
    S["pattern"] = f"E2E Pattern {w['tag']}"
    S["weekend_date"] = (TODAY + timedelta(days=14)).isoformat()
    S["weekend_moved"] = (TODAY + timedelta(days=21)).isoformat()
    return w


@pytest.fixture
def admin(world):
    yield as_user(ADMIN)


# ---------------------------------------------------------------------------------------------------------- section 1

def test_1_1_admin_creates_and_publishes_weekend_refresher(admin):
    d = admin
    open_calendar(d)
    new_session(d, "Weekend refresher", S["weekend"], S["weekend_date"], seats="3")
    see(d, "Published")
    see(d, "0 of 3 taken")
    close_all_dialogs(d)


def test_1_2_registration_seat_limit_and_cancel(world):
    w = world
    t1, t2, t4, t5 = w["teachersA"]
    t3 = w["teachersB"][0]
    topic = S["weekend"]
    # T1, T2, T3 register
    for t in (t1, t2, t3):
        d = as_user(t["phone"])
        try:
            teacher_open_upcoming(d)
            click_row = row_button(d, topic, "Register")
            js_click(d, click_row)
            wait(d).until(lambda x: row_has(x, topic, "Cancel registration"))
        finally:
            d.quit()
    # the fourth Teacher is not offered a seat (the full session is left out of their list); neither is the Teacher on leave
    for t in (w["teachersA"][3], t4):
        d = as_user(t["phone"])
        try:
            teacher_open_upcoming(d)
            wait(d).until(lambda x: x.find_elements(By.XPATH, "//ul/li") or x.find_elements(By.XPATH, "//*[contains(.,'No upcoming training')]"))
            assert not d.find_elements(By.XPATH, f"//li[.//strong[normalize-space()=\"{topic}\"]]//button[normalize-space()='Register']")
        finally:
            d.quit()
    # T3 cancels; the fourth Teacher now registers
    d = as_user(t3["phone"])
    try:
        teacher_open_upcoming(d)
        js_click(d, row_button(d, topic, "Cancel registration"))
        wait(d).until(lambda x: row_has(x, topic, "Register"))
    finally:
        d.quit()
    d = as_user(t5["phone"])
    try:
        teacher_open_upcoming(d)
        js_click(d, row_button(d, topic, "Register"))
        wait(d).until(lambda x: row_has(x, topic, "Cancel registration"))
    finally:
        d.quit()


def test_1_2b_cancelling_after_the_start_time_is_refused(world):
    """A session that started earlier today: the registered Teacher's cancel is refused."""
    t1 = world["teachersA"][0]
    topic = f"E2E Started {world['tag']}"
    status, body = api.call(ADMIN, "POST", "/api/v1/training/sessions", {
        "type": "WEEKEND", "topic": topic, "date": TODAY.isoformat(), "startsAt": "23:00", "endsAt": "23:30",
        "venueKind": "PHYSICAL", "venue": "Head office", "seatLimit": 5, "publish": True})
    sess = api.ok((status, body))
    api.ok(api.call(t1["phone"], "POST", f"/api/v1/training/sessions/{sess['id']}/registrations"))
    status, moved = api.call(ADMIN, "PUT", f"/api/v1/training/sessions/{sess['id']}", {
        **{k: sess[k] for k in ("type", "topic", "venueKind", "venue", "seatLimit")},
        "date": TODAY.isoformat(), "startsAt": "00:01", "endsAt": "00:30", "reason": "e2e start already passed",
        "version": sess["version"]})
    if status >= 300:
        pytest.skip(f"the session could not be moved to an earlier start: {status} {moved}")
    d = as_user(t1["phone"])
    try:
        teacher_open_upcoming(d)
        row = teacher_row(d, topic)
        btns = row.find_elements(By.XPATH, ".//button[normalize-space()='Cancel registration']")
        if btns:
            js_click(d, btns[0])
            see(d, "started")
        else:
            # no cancel offered at all once started
            assert not btns
    finally:
        d.quit()


def test_1_3_admin_moves_the_date_registrations_kept(admin):
    d = admin
    open_calendar(d)
    open_session(d, S["weekend"], S["weekend_date"])
    see(d, "Registered (3)")
    click(d, "Move")
    wait(d).until(EC.visibility_of_element_located((By.XPATH, "//*[@role='dialog']//h2[normalize-space()='Move this session']")))
    fill(d, "Date", S["weekend_moved"])
    click(d, "Save")  # without a reason: refused
    see(d, "Enter the reason for moving the session.")
    fill(d, "Reason for moving", "Hall not free")
    click(d, "Save")
    see(d, "moved because: Hall not free")
    see(d, "Registered (3)")
    close_all_dialogs(d)
    # the moved session is listed at its new date
    open_calendar(d)
    goto_month(d, S["weekend_moved"])
    assert d.find_elements(By.XPATH, f"//button[starts-with(@aria-label,\"{S['weekend']},\")]")


# ---------------------------------------------------------------------------------------------------------- section 2

def test_2_standing_monthly_pattern(admin):
    d = admin
    w = S["w"]
    n = int(w["tag"])
    start = f"{6 + n % 10:02d}:{n % 60:02d}"
    end = f"{7 + n % 10:02d}:{n % 60:02d}"
    first_next = (TODAY.replace(day=1) + timedelta(days=32)).replace(day=1)
    open_calendar(d)
    click(d, "Patterns")
    click(d, "New pattern")
    wait(d).until(EC.visibility_of_element_located((By.XPATH, "//*[@role='dialog']//h2[normalize-space()='New monthly pattern']")))
    choose(d, "Which", "Second")
    choose(d, "Weekday", "Saturday")
    fill(d, "Starts at", start)
    fill(d, "Ends at", end)
    fill(d, "Topic", S["pattern"])
    fill(d, "Trainer", "E2E Trainer")
    fill(d, "Start month", first_next.isoformat())
    click(d, "Preview the next months")
    wait(d).until(EC.visibility_of_element_located((By.XPATH, "//ul[@aria-label='Preview']/li")))
    assert len(d.find_elements(By.XPATH, "//ul[@aria-label='Preview']/li")) >= 3
    click(d, "Create pattern")
    see(d, S["pattern"])
    S["pattern_months"] = [(first_next + timedelta(days=31 * k)).replace(day=1) for k in range(3)]
    # three months appear on the calendar
    click(d, "Sessions")
    found = []
    for m in S["pattern_months"]:
        goto_month(d, m.isoformat())
        found.append(bool(d.find_elements(By.XPATH, f"//button[starts-with(@aria-label,\"{S['pattern']},\")]")))
    S["pattern_found"] = found
    assert found[0] and found[1], f"pattern sessions per month: {found}"
    # a duplicate rule and time is refused
    click(d, "Patterns")
    click(d, "New pattern")
    wait(d).until(EC.visibility_of_element_located((By.XPATH, "//*[@role='dialog']//h2[normalize-space()='New monthly pattern']")))
    choose(d, "Which", "Second")
    choose(d, "Weekday", "Saturday")
    fill(d, "Starts at", start)
    fill(d, "Ends at", end)
    fill(d, "Topic", S["pattern"] + " again")
    fill(d, "Start month", first_next.isoformat())
    click(d, "Create pattern")
    wait(d).until(EC.visibility_of_element_located((By.XPATH, "//*[@role='dialog']//*[@role='alert' or contains(@class,'MuiAlert')]")))
    click(d, "Cancel")


def test_2_2_edit_one_month_skip_and_stop(admin):
    d = admin
    open_calendar(d)
    m1, m2 = S["pattern_months"][0], S["pattern_months"][1]
    # edit one month's trainer: only that month changes
    goto_month(d, m1.isoformat())
    js_click(d, wait(d).until(EC.presence_of_element_located((By.XPATH, f"//button[starts-with(@aria-label,\"{S['pattern']},\")]"))))
    click(d, "Edit")
    wait(d).until(EC.visibility_of_element_located((By.XPATH, "//*[@role='dialog']//h2[normalize-space()='Edit this session']")))
    fill(d, "Trainer", "Changed Trainer")
    click(d, "Save")
    see(d, "Changed Trainer")
    close_all_dialogs(d)
    goto_month(d, m2.isoformat())
    js_click(d, wait(d).until(EC.presence_of_element_located((By.XPATH, f"//button[starts-with(@aria-label,\"{S['pattern']},\")]"))))
    see(d, "E2E Trainer")
    assert not_see(d, "Changed Trainer")
    close_all_dialogs(d)
    # skip a month with a reason (the third month)
    m3 = S["pattern_months"][2]
    click(d, "Patterns")
    pattern_item = wait(d).until(EC.visibility_of_element_located((By.XPATH, f"//li[.//strong[normalize-space()=\"{S['pattern']}\"]]")))
    js_click(d, pattern_item.find_element(By.XPATH, ".//button[normalize-space()='Skip a month']"))
    fill(d, "Month to skip", m3.isoformat())
    click(d, "Skip month")  # reason missing
    see(d, "Enter the reason for skipping.")
    fill(d, "Reason", "Exam week")
    click(d, "Skip month")
    see(d, "Exam week")
    # stop the pattern from month 3 on
    pattern_item = wait(d).until(EC.visibility_of_element_located((By.XPATH, f"//li[.//strong[normalize-space()=\"{S['pattern']}\"]]")))
    js_click(d, pattern_item.find_element(By.XPATH, ".//button[normalize-space()='Stop']"))
    fill(d, "Stop from month", m3.isoformat())
    click(d, "Stop pattern")
    time.sleep(2)
    for _ in range(3):  # the busy local stack sometimes answers 429; try again after a pause
        if not d.find_elements(By.XPATH, "//*[@role='dialog']") or not not_see(d, "Something went wrong"):
            if d.find_elements(By.XPATH, "//*[@role='dialog']"):
                time.sleep(15)
                click(d, "Stop pattern")
                time.sleep(3)
                continue
        break
    dialogs = d.find_elements(By.XPATH, "//*[@role='dialog']")
    assert not dialogs, "stop pattern was refused: " + dialogs[-1].text[:300]
    see(d, "stopped after")


# ---------------------------------------------------------------------------------------------------------- section 3

def test_3_1_assign_group_and_specific_people(admin):
    d = admin
    w = S["w"]
    S["monthly_date"] = TODAY.isoformat()
    open_calendar(d)
    new_session(d, "Monthly training", S["monthly"], S["monthly_date"])
    # group: Manager A's Teachers
    click(d, "Assign people")
    wait(d).until(EC.visibility_of_element_located((By.XPATH, "//*[@role='dialog']//h2[normalize-space()='Assign people']")))
    choose(d, "Group", "A Manager's Teachers")
    wait(d).until(lambda x: any(w["mgrA"]["name"] in o.text for o in Select(field(x, "A Manager's Teachers", "select")).options))
    choose(d, "A Manager's Teachers", w["mgrA"]["name"])
    click(d, "Check who will be assigned")
    pv = wait(d).until(EC.visibility_of_element_located((By.XPATH, "//*[@aria-live='polite' and contains(.,'new,')]")))
    assert "new" in pv.text
    S["group_preview"] = pv.text
    click(d, "Assign ", exact=False)
    wait(d).until(EC.invisibility_of_element_located((By.XPATH, "//*[@role='dialog']//h2[normalize-space()='Assign people']")))
    see(d, "Assigned (")
    # Specific people: Manager B and Manager A through the person picker
    click(d, "Assign people")
    choose(d, "Group", "Specific people")
    choose(d, "Kind of person", "Managers")
    pick_person(d, "Find a person to add", w["mgrB"]["name"])
    see(d, f"Remove {w['mgrB']['name']}")
    pick_person(d, "Find a person to add", w["mgrA"]["name"])
    click(d, "Check who will be assigned")
    pv = wait(d).until(EC.visibility_of_element_located((By.XPATH, "//*[@aria-live='polite' and contains(.,'new,')]")))
    assert pv.text.startswith("2 new"), pv.text
    click(d, "Assign ", exact=False)
    wait(d).until(EC.invisibility_of_element_located((By.XPATH, "//*[@role='dialog']//h2[normalize-space()='Assign people']")))
    # assigning T1 again: still one obligation (0 new, 1 already assigned, Assign button disabled)
    click(d, "Assign people")
    choose(d, "Group", "Specific people")
    pick_person(d, "Find a person to add", w["teachersA"][0]["name"])
    click(d, "Check who will be assigned")
    pv = wait(d).until(EC.visibility_of_element_located((By.XPATH, "//*[@aria-live='polite' and contains(.,'new,')]")))
    assert pv.text.startswith("0 new, 1 already assigned"), pv.text
    confirm = [e for e in d.find_elements(By.XPATH, "//button[starts-with(normalize-space(),'Assign') and contains(normalize-space(),'people')]") if e.text.strip().lower() != "assign people"]
    assert confirm and all(not e.is_enabled() for e in confirm), [(e.text, e.is_enabled()) for e in confirm]
    click(d, "Cancel")
    # remove one before the session (the Teacher on leave)
    t4 = w["teachersA"][2]["name"]
    if d.find_elements(By.XPATH, f"//button[normalize-space()='Remove {t4}']"):
        click(d, f"Remove {t4}")
        fill(d, "Reason for removing", "Not needed")
        click(d, "Remove")
        wait(d).until(lambda x: not x.find_elements(By.XPATH, f"//button[normalize-space()='Remove {t4}']"))
    close_all_dialogs(d)


def test_3_2_assigned_teacher_sees_required_and_cannot_attend(world):
    t1 = world["teachersA"][0]
    d = as_user(t1["phone"])
    try:
        teacher_open_upcoming(d)
        row = teacher_row(d, S["monthly"])
        assert "Required" in row.text
        js_click(d, row.find_element(By.XPATH, ".//button[normalize-space()='Details']"))
        click(d, "I cannot attend")
        click(d, "Send")
        see(d, "Enter a note.")
        fill(d, "Note for the office", "Family function")
        click(d, "Send")
        wait(d).until(lambda x: not x.find_elements(By.XPATH, "//*[@role='dialog']//h2[normalize-space()='I cannot attend']"))
    finally:
        d.quit()
    a = as_user(ADMIN)
    try:
        open_calendar(a)
        open_session(a, S["monthly"], S["monthly_date"])
        see(a, "cannot attend: Family function")  # the obligation stays
        assert t1["name"] in a.find_element(By.XPATH, "//*[@role='dialog']").text
    finally:
        a.quit()


# ---------------------------------------------------------------------------------------------------------- section 4

def grid_row(d, name):
    return wait(d).until(EC.presence_of_element_located((By.XPATH, f"//table[@aria-label='Attendance']//tr[.//td[starts-with(normalize-space(),\"{name}\")]]")))


def grid_set(d, name, outcome, reason=None, day=None, remark=None):
    row = grid_row(d, name)
    Select(row.find_element(By.XPATH, f".//label[normalize-space()='Outcome for {name}']/..//select")).select_by_visible_text(outcome)
    if day:
        Select(row.find_element(By.XPATH, f".//label[normalize-space()='Day for {name}']/..//select")).select_by_visible_text(day)
    if reason:
        row.find_element(By.XPATH, f".//label[normalize-space()='Reason for {name}']/..//input").send_keys(reason)
    if remark:
        row.find_element(By.XPATH, f".//label[normalize-space()='Internal remark for {name}']/..//input").send_keys(remark)


def test_4_1_manager_marks_only_their_own_teachers(world):
    w = world
    t1, t2 = w["teachersA"][0], w["teachersA"][1]
    d = as_user(w["mgrA"]["phone"])
    try:
        spa_goto(d, "/operations/my-training")
        wait(d).until(EC.visibility_of_element_located((By.XPATH, "//h1[normalize-space()='My Training']")))
        assert not has_btn(d, "New session")
        wait(d).until(lambda x: not x.find_elements(By.XPATH, "//*[@aria-label='Loading']"))
        open_session(d, S["monthly"], S["monthly_date"])
        click(d, "Record attendance")
        grid_row(d, t1["name"])
        names = d.find_element(By.XPATH, "//table[@aria-label='Attendance']").text
        assert t2["name"] in names
        assert w["mgrB"]["name"] not in names and w["mgrA"]["name"] not in names  # not Manager B, not themselves
        assert w["teachersB"][0]["name"] not in names
        grid_set(d, t1["name"], "Present")
        grid_set(d, t2["name"], "Absent")
        click(d, "Save attendance")  # Absent without a reason is refused before saving
        see(d, "Enter a reason for Absent or Excused.")
        grid_set(d, t2["name"], "Absent", reason="No notice given")
        click(d, "Save attendance")
        wait(d).until(lambda x: len(x.find_elements(By.XPATH, "//table[@aria-label='Attendance']//*[normalize-space()='Saved']")) >= 2)
    finally:
        d.quit()


def test_4_2_admin_marks_anyone_walk_in_and_corrects(admin):
    d = admin
    w = S["w"]
    t1, t2 = w["teachersA"][0], w["teachersA"][1]
    open_calendar(d)
    open_session(d, S["monthly"], S["monthly_date"])
    see(d, "Held")
    click(d, "Record attendance")
    grid_row(d, w["mgrA"]["name"])
    grid_set(d, w["mgrA"]["name"], "Present")
    grid_set(d, t1["name"], "Present", day="Half day", remark="Left at noon")
    click(d, "Save attendance")
    wait(d).until(lambda x: grid_row(x, w["mgrA"]["name"]).find_elements(By.XPATH, ".//*[normalize-space()='Saved']"))
    # a walk-in found with the person picker
    click(d, "Add a walk-in")
    pick_person(d, "Person who walked in", w["teachersB"][0]["name"])
    click(d, "Add walk-in")
    wait(d).until(lambda x: "Walk-in" in grid_row(x, w["teachersB"][0]["name"]).text)
    # the corrected half day is kept
    sel = Select(grid_row(d, t1["name"]).find_element(By.XPATH, f".//label[normalize-space()='Day for {t1['name']}']/..//select"))
    assert sel.first_selected_option.text == "Half day"
    click(d, "Close")
    see(d, "walk-ins 1")


# ---------------------------------------------------------------------------------------------------------- section 5

def test_5_1_director_exceptions_filter_and_close():
    w = S["w"]
    t2 = w["teachersA"][1]
    d = as_user(DIRECTOR)
    try:
        spa_goto(d, "/operations/training/exceptions")
        wait(d).until(EC.visibility_of_element_located((By.XPATH, "//h1[normalize-space()='Training Exceptions']")))
        month = TODAY.strftime("%Y-%m")
        fill(d, "Month", month)
        row = wait(d).until(EC.visibility_of_element_located((By.XPATH, f"//li[.//strong[normalize-space()=\"{t2['name']}\"]]")))
        assert "Open" in row.text and S["monthly"] in row.text
        # an empty month shows the empty state
        fill(d, "Month", (TODAY.replace(day=1) - timedelta(days=400)).strftime("%Y-%m"))
        see(d, "No exceptions.")
        fill(d, "Month", month)
        row = wait(d).until(EC.visibility_of_element_located((By.XPATH, f"//li[.//strong[normalize-space()=\"{t2['name']}\"]]")))
        js_click(d, row.find_element(By.XPATH, f".//button[normalize-space()='Close exception for {t2['name']}']"))
        click(d, "Close exception")
        see(d, "Enter a note.")
        fill(d, "Note", "Medical reason seen")
        click(d, "Close exception")
        row = wait(d).until(EC.visibility_of_element_located((By.XPATH, f"//li[.//strong[normalize-space()=\"{t2['name']}\"]]")))
        wait(d).until(lambda x: "Closed" in row.text or "Excused" in row.text)
        see(d, "Medical reason seen")
    finally:
        d.quit()


def test_5_3_history_views_and_scope(world):
    w = world
    t1 = w["teachersA"][0]
    # Admin: the history with the internal remark
    a = as_user(ADMIN)
    try:
        spa_goto(a, f"/operations/training/history/teacher/{t1['id']}")
        see(a, f"Training history: {t1['name']}")
        see(a, S["monthly"])
        see(a, "Internal remark: Left at noon")
        see(a, "attended")
    finally:
        a.quit()
    # the Teacher: own history, no internal remark
    t = as_user(t1["phone"])
    try:
        spa_goto(t, f"/my-attendance/training/history")
        see(t, "My Training History")
        see(t, S["monthly"])
        assert not_see(t, "Internal remark")
        assert not_see(t, "Left at noon")
    finally:
        t.quit()
    # Manager B opening T1: the API answers 404 (a Manager reaches only their own Zone's Teachers); Manager A is allowed
    st, _ = api.call(w["mgrB"]["phone"], "GET", f"/api/v1/training/people/teacher/{t1['id']}/history")
    assert st == 404, f"Manager B opening T1's history should be 404, got {st}"
    st, _ = api.call(w["mgrA"]["phone"], "GET", f"/api/v1/training/people/teacher/{t1['id']}/history")
    assert st == 200, f"Manager A opening T1's history should be 200, got {st}"
    b = as_user(w["mgrB"]["phone"])
    try:
        spa_goto(b, f"/operations/training/history/teacher/{t1['id']}")
        time.sleep(2)
        assert not_see(b, S["monthly"])
    finally:
        b.quit()


# ---------------------------------------------------------------------------------------------------------- section 6

def test_6_1_manager_activity_calendar_shows_training():
    w = S["w"]
    d = as_user(w["mgrA"]["phone"])
    try:
        spa_goto(d, "/operations/activity-calendar")
        wait(d).until(EC.visibility_of_element_located((By.XPATH, "//h1")))
        click(d, "List view")
        # the list shows kind, Managers, Zone and status per day (no topic): the monthly session is the Training row on its date
        # with Manager A among the Managers; attendance was saved in 4.1, so it is Done
        d_ = date.fromisoformat(S["monthly_date"]).strftime("%d/%m/%Y")
        rows = wait(d).until(lambda x: x.find_elements(By.XPATH, f"//tr[contains(.,'{d_}') and contains(.,'Training') and contains(.,\"{w['mgrA']['name']}\")]"))
        assert rows and "Done" in rows[0].text, [r.text for r in rows]
        assert not_see(d, "Training is not included yet")
    finally:
        d.quit()


# ---------------------------------------------------------------------------------------------------------- roles, states, phone

def test_roles_teacher_and_manager_views_of_the_calendar():
    w = S["w"]
    t = as_user(w["teachersA"][0]["phone"])
    try:
        spa_goto(t, f"/operations/training")
        time.sleep(2)
        assert "not authori" in t.find_element(By.TAG_NAME, "body").text.lower()
        assert not t.find_elements(By.XPATH, "//h1[normalize-space()='Training Calendar']")
    finally:
        t.quit()
    dr = as_user(DIRECTOR)
    try:
        open_calendar(dr)
        assert has_btn(dr, "New session")
    finally:
        dr.quit()


def test_empty_state_for_a_filter_with_no_sessions(admin):
    d = admin
    open_calendar(d)
    choose(d, "Status", "Cancelled")
    choose(d, "Type", "Monthly training")
    see(d, "No training sessions this month.")


def test_phone_width_has_no_horizontal_page_scroll():
    w = S["w"]
    d = as_user(ADMIN, 375)
    try:
        open_calendar(d)
        assert no_page_overflow(d)
        open_session(d, S["monthly"], S["monthly_date"])
        click(d, "Record attendance")
        grid_row(d, w["teachersA"][0]["name"])
        assert no_page_overflow(d)
    finally:
        d.quit()
    t = as_user(w["teachersA"][0]["phone"], 375)
    try:
        teacher_open_upcoming(t)
        assert no_page_overflow(t)
    finally:
        t.quit()
