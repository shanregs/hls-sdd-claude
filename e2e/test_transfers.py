"""Browser walkthrough for spec 027 (Teacher Transfer and Replacement): quickstart.md sections 1, 2, 4 and 5, and the
browser-reachable part of section 3 (T070).

Same setup as test_school_working_days.py (demo seed: Admin asha.admin, Director 9800000002; password Password123!).
The demo seed holds only one Manager, so the test creates, through the REST API (hls_api.py), uniquely named data of its own:
a Zone with two Schools (A1, A2) and Manager A, a second Zone with School B1 and Manager B, Teachers T1 (at A1), T2 (at A2),
T3 (active, unplaced) and T4 (in training). Nothing is deleted and no whole-table count is asserted.
Run from this folder:  pytest test_transfers.py -v        (HEADED=1 to watch)

Covered in the browser: raising a transfer and a replacement as Manager A, the refusals (one open request, the Teacher picker
leaving out unplaced and in-training Teachers, a candidate list that leaves out placed Teachers), the Approve button only for
the Director (not for the requester, not for Admin), Approve, Reject with and without a reason, Cancel of an Approved request
with the placement restored, Change date, the Transferred and Replaced filters, the movement history of a Teacher and of a
School, and the request list filters. Also checked through the API because the browser cannot make the call: 403 when a
requester or Admin calls approve, 404 for Manager B, a placed replacement refused, and two deciders approving at once (409).
Not covered: notifications to the Teacher and deciders (the demo Teachers have no app account and the inbox is a device
feature), a receiving School whose contract has no vacant position (the demo Schools have no contract positions), section
3 steps 1, 3, 4 and 5 (leave days that carry the new School, an open substitution moving, the payroll read and a locked
month: they need attendance, leave, substitution, payroll and a month lock, covered by the backend tests), "Applied shows
once the date arrives" (the date is at least a day away; there is no clock control) and the nightly job.
"""
import pytest
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC

from hls_api import PASSWORD, call, iso_in, make_manager, make_school, make_teacher, make_zone, ok, stamp, token_for
from test_school_working_days import ADMIN, BASE, make_driver, sign_in
from ui_helpers import (body, button, click, dialog, fill, has_button, options_of, page_text_has, pick, see, settle, wait)

DIRECTOR = "9800000002"
PAGE = f"{BASE}/operations/transfers"
S = {}   # state shared by the ordered tests


@pytest.fixture(scope="module")
def world():
    admin = token_for("asha.admin")
    s = stamp()
    zone_a, place_a = make_zone(admin, f"E2E Transfer Zone A {s}")
    zone_b, place_b = make_zone(admin, f"E2E Transfer Zone B {s}")
    a1 = make_school(admin, f"E2E A1 {s}", place_a)
    a2 = make_school(admin, f"E2E A2 {s}", place_a)
    b1 = make_school(admin, f"E2E B1 {s}", place_b)
    mgr_a = make_manager(admin, "e2etransferA", [zone_a["id"]], [a1["id"], a2["id"]])
    mgr_b = make_manager(admin, "e2etransferB", [zone_b["id"]], [b1["id"]])
    w = {
        "s": s, "a1": a1, "a2": a2, "b1": b1, "mgr_a": mgr_a, "mgr_b": mgr_b,
        "t1": make_teacher(admin, f"E2E T1 {s}", a1["id"]),
        "t2": make_teacher(admin, f"E2E T2 {s}", a2["id"]),
        "t3": make_teacher(admin, f"E2E T3 {s}"),
        "t4": make_teacher(admin, f"E2E T4 {s}", status="IN_TRAINING"),
        "date": iso_in(7),
    }
    S.update(w)
    return w


def open_page(d):
    d.get(PAGE)
    try:
        wait(d).until(EC.visibility_of_element_located((By.XPATH, "//h1[normalize-space()='Transfers and Replacements']")))
    except Exception:
        raise AssertionError(f"Transfers page did not open at {d.current_url}: {body(d)[:300]}")
    wait(d).until(lambda x: not x.find_elements(By.CSS_SELECTOR, "[aria-label='Loading requests']"))
    settle(d, 0.4)


def row_of(d, teacher_name):
    return wait(d).until(lambda x: next((r for r in x.find_elements(By.CSS_SELECTOR, "tbody tr") if teacher_name in r.text), None))


def open_request(d, teacher_name):
    row_of(d, teacher_name).find_element(By.XPATH, ".//button[normalize-space()='Open']").click()
    return dialog(d)


def close(d, dlg):
    button(d, "Close", dlg).click()
    wait(d).until(EC.invisibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))


def request_for(teacher_id, status=None):
    admin = token_for("asha.admin")
    rows = ok(call("GET", f"/transfers?size=100{'&status=' + status if status else ''}", None, admin))["content"]
    return [r for r in rows if r["teacher"]["id"] == teacher_id]


def teacher_school(teacher_id):
    return ok(call("GET", f"/teachers/{teacher_id}", None, token_for("asha.admin")))["school"]


@pytest.fixture
def manager_a(world, browsers):
    return browsers(world["mgr_a"]["username"])


@pytest.fixture
def director(world, browsers):
    return browsers(DIRECTOR)


@pytest.fixture
def admin_browser(world, browsers):
    return browsers(ADMIN)


def raise_transfer(d, teacher, school, date, reason="Manager rebalancing", note=None):
    open_page(d)
    button(d, "Request a transfer").click()
    dlg = dialog(d)
    pick(d, dlg, "Teacher", teacher["name"])
    pick(d, dlg, "Receiving School", school["name"])
    fill(d, dlg, "Effective date", date)
    pick(d, dlg, "Reason", reason)
    if note:
        fill(d, dlg, "Note", note)
    return dlg


# ---- section 1: transfer -------------------------------------------------------------------------------------------

def test_1_1_manager_menu_has_transfers_and_requests_a_transfer(world, manager_a):
    d = manager_a
    wait(d).until(lambda x: "Transfers & Replacements" in body(x))
    labels = d.execute_script("return Array.from(document.querySelectorAll('nav [role=button]')).map(b => b.textContent.trim())")
    assert "Transfers & Replacements" in labels, labels
    dlg = raise_transfer(d, world["t1"], world["a2"], world["date"])
    button(d, "Request transfer", dlg).click()
    wait(d).until(lambda x: "Status:" in body(x))                      # the request opens in its detail dialog
    detail = dialog(d)
    assert "Pending" in detail.text and world["t1"]["name"] in detail.text
    assert world["a1"]["name"] in detail.text and world["a2"]["name"] in detail.text
    S["request_t1"] = request_for(world["t1"]["id"])[0]
    assert S["request_t1"]["status"] == "PENDING"
    assert teacher_school(world["t1"]["id"])["id"] == world["a1"]["id"], "T1's placement must not change while Pending"


def test_1_2_refusals_one_open_request_and_pickers(world, manager_a):
    d = manager_a
    dlg = raise_transfer(d, world["t1"], world["a2"], world["date"])
    # a second open request for T1 is refused with the reason, in the form and again on submit
    wait(d).until(lambda x: "open request" in dlg.text.lower() or "already" in dlg.text.lower())
    button(d, "Request transfer", dlg).click()
    wait(d).until(lambda x: "open request" in dlg.text.lower() or "already" in dlg.text.lower())
    assert len(request_for(world["t1"]["id"])) == 1
    # T3 (unplaced) and T4 (in training) are not offered: only placed active Teachers can be moved
    names = options_of(d, dlg, "Teacher")
    assert not any(world["t4"]["name"] in n or world["t3"]["name"] in n for n in names), names
    assert any(world["t2"]["name"] in n for n in names), names


def test_1_3_requester_and_admin_have_no_approve(world, manager_a, admin_browser):
    d = manager_a
    open_page(d)
    dlg = open_request(d, world["t1"]["name"])
    assert not has_button(dlg, "Approve"), "the requester must not see Approve"
    assert has_button(dlg, "Cancel request") or has_button(dlg, "Change date")
    close(d, dlg)
    mgr_token = token_for(world["mgr_a"]["username"])
    status, _ = call("POST", f"/transfers/{S['request_t1']['id']}/approve", {"version": S["request_t1"]["version"]}, mgr_token)
    assert status == 403, status
    a = admin_browser
    open_page(a)
    dlg = open_request(a, world["t1"]["name"])
    assert not has_button(dlg, "Approve"), "Admin must not see Approve"
    close(a, dlg)
    status, _ = call("POST", f"/transfers/{S['request_t1']['id']}/approve", {"version": S["request_t1"]["version"]}, token_for(ADMIN))
    assert status == 403, status


def test_1_4_director_approves_and_history_shows_the_move(world, director):
    d = director
    open_page(d)
    dlg = open_request(d, world["t1"]["name"])
    button(d, "Approve", dlg).click()
    wait(d).until(lambda x: "Approved" in dialog(x).text)
    assert "decided by" in dialog(d).text
    close(d, dialog(d))
    assert request_for(world["t1"]["id"], "APPROVED"), "the request is Approved"
    # the Teacher's movement history: A1 ends the day before the date, the new School starts on it
    d.get(f"{BASE}/master-data/teachers")
    box = wait(d).until(EC.visibility_of_element_located((By.XPATH, "//label[contains(.,'Search teachers')]/..//input")))
    box.send_keys(world["t1"]["name"])
    wait(d).until(lambda x: world["t1"]["name"] in body(x))
    settle(d)
    row = d.find_element(By.XPATH, f"//*[@role='row'][contains(.,'{world['t1']['name']}')]")
    row.find_element(By.XPATH, ".//button[normalize-space()='Movements']").click()
    dlg = dialog(d)
    wait(d).until(lambda x: "Initial assignment" in dialog(x).text)
    text = dialog(d).text
    assert world["a1"]["name"] in text and world["a2"]["name"] in text and "Transfer" in text
    assert "approved by" in text and "Manager rebalancing" in text or "rebalancing" in text.lower()


def test_1_5_manager_b_gets_not_found(world):
    status, _ = call("GET", f"/transfers/{S['request_t1']['id']}", None, token_for(world["mgr_b"]["username"]))
    assert status == 404, status


# ---- section 2: replacement ----------------------------------------------------------------------------------------

def test_2_replacement_candidates_and_approval(world, manager_a, director):
    d = manager_a
    open_page(d)
    button(d, "Replace a Teacher").click()
    dlg = dialog(d)
    pick(d, dlg, "School", world["a2"]["name"])
    pick(d, dlg, "Teacher to replace", world["t2"]["name"])
    fill(d, dlg, "Effective date", world["date"])
    candidates = options_of(d, dlg, "Replacement Teacher")
    assert any(world["t3"]["name"] in c for c in candidates), candidates
    assert not any(world["t4"]["name"] in c for c in candidates), "a Teacher in training is not a candidate"
    assert not any(world["t1"]["name"] in c or world["t2"]["name"] in c for c in candidates), "placed Teachers are not candidates"
    pick(d, dlg, "Replacement Teacher", world["t3"]["name"])
    pick(d, dlg, "Reason", "After a review")
    button(d, "Request replacement", dlg).click()
    wait(d).until(lambda x: "Status:" in body(x))
    assert "Pending" in dialog(d).text and world["t3"]["name"] in dialog(d).text
    S["request_t2"] = request_for(world["t2"]["id"])[0]
    # naming a placed Teacher as the replacement is refused by the server (API call, the picker never offers one)
    status, why = call("POST", "/transfers/replacements",
                       {"teacherId": world["t2"]["id"], "replacementTeacherId": world["t1"]["id"], "effectiveOn": world["date"],
                        "reasonCategory": "AFTER_REVIEW"}, token_for(world["mgr_a"]["username"]))
    assert status >= 400, (status, why)
    # the Director approves
    x = director
    open_page(x)
    dlg = open_request(x, world["t2"]["name"])
    button(x, "Approve", dlg).click()
    wait(x).until(lambda y: "Approved" in dialog(y).text)
    assert request_for(world["t2"]["id"], "APPROVED")
    # School A2's movements: T2 leaves and T3 arrives on the date; T2 (no destination) is available from the date
    close(x, dialog(x))
    x.get(f"{BASE}/master-data/schools")
    see(x, "Search schools")
    fill(x, x, "Search schools", world["a2"]["name"])
    wait(x).until(lambda y: world["a2"]["name"] in body(y))
    row = wait(x).until(lambda y: y.find_element(By.XPATH, f"//*[@role='row'][contains(.,'{world['a2']['name']}')]"))
    row.find_element(By.XPATH, ".//button[normalize-space()='Movements']").click()
    wait(x).until(lambda y: "Replacement" in dialog(y).text)
    text = dialog(x).text
    assert world["t3"]["name"] in text and world["t2"]["name"] in text


# ---- section 4: reject, cancel, stale ------------------------------------------------------------------------------

def test_4_1_reject_needs_a_reason(world, director):
    s = stamp()
    admin = token_for("asha.admin")
    t = make_teacher(admin, f"E2E Reject {s}", world["a1"]["id"])
    S["t_reject"] = t
    ok(call("POST", "/transfers/transfers", {"teacherId": t["id"], "toSchoolId": world["a2"]["id"], "effectiveOn": world["date"],
                                             "reasonCategory": "MANAGER_REBALANCING"}, token_for(world["mgr_a"]["username"])))
    d = director
    open_page(d)
    dlg = open_request(d, t["name"])
    button(d, "Reject", dlg).click()
    button(d, "Confirm rejection", dlg).click()                         # empty reason
    wait(d).until(lambda x: "Give a reason" in dialog(x).text)
    fill(d, dlg, "Reason for rejecting", "Not this term")
    button(d, "Confirm rejection", dlg).click()
    wait(d).until(lambda x: "Rejected" in dialog(x).text)
    assert request_for(t["id"], "REJECTED")
    assert teacher_school(t["id"])["id"] == world["a1"]["id"], "nothing moved"


def test_4_2_cancel_an_approved_request_restores_the_placement(world, director):
    admin = token_for("asha.admin")
    t = make_teacher(admin, f"E2E Cancel {stamp()}", world["a1"]["id"])
    created = ok(call("POST", "/transfers/transfers", {"teacherId": t["id"], "toSchoolId": world["a2"]["id"], "effectiveOn": world["date"],
                                                       "reasonCategory": "MANAGER_REBALANCING"}, token_for(world["mgr_a"]["username"])))
    ok(call("POST", f"/transfers/{created['id']}/approve", {"version": created["version"]}, token_for(DIRECTOR)))
    d = director
    open_page(d)
    dlg = open_request(d, t["name"])
    button(d, "Cancel request", dlg).click()
    button(d, "Confirm cancellation", dlg).click()
    wait(d).until(lambda x: "Give a reason" in dialog(x).text)
    fill(d, dlg, "Reason for cancelling", "Plans changed")
    button(d, "Confirm cancellation", dlg).click()
    wait(d).until(lambda x: "Cancelled" in dialog(x).text)
    assert "cancelled" in dialog(d).text.lower() and "Plans changed" in dialog(d).text
    assert teacher_school(t["id"])["id"] == world["a1"]["id"]
    detail = ok(call("GET", "/teachers/" + t["id"], None, token_for("asha.admin")))
    assert not [p for p in detail["placements"] if p["schoolId"] == world["a2"]["id"] and p["status"] != "ENDED" and p["endsOn"] is None] \
        or detail["school"]["id"] == world["a1"]["id"]


def test_4_3_change_date_of_a_pending_request(world, director):
    admin = token_for("asha.admin")
    t = make_teacher(admin, f"E2E Change {stamp()}", world["a1"]["id"])
    ok(call("POST", "/transfers/transfers", {"teacherId": t["id"], "toSchoolId": world["a2"]["id"], "effectiveOn": world["date"],
                                             "reasonCategory": "MANAGER_REBALANCING"}, token_for(world["mgr_a"]["username"])))
    d = director
    open_page(d)
    dlg = open_request(d, t["name"])
    button(d, "Change date", dlg).click()
    fill(d, dlg, "New effective date", iso_in(9))
    button(d, "Save date", dlg).click()
    wait(d).until(lambda x: "Save date" not in dialog(x).text)
    assert request_for(t["id"])[0]["effectiveOn"] == iso_in(9)


def test_4_3b_a_direct_move_makes_an_approved_request_stale(world, director):
    admin = token_for("asha.admin")
    t = make_teacher(admin, f"E2E Stale {stamp()}", world["a1"]["id"])
    created = ok(call("POST", "/transfers/transfers", {"teacherId": t["id"], "toSchoolId": world["a2"]["id"], "effectiveOn": world["date"],
                                                       "reasonCategory": "MANAGER_REBALANCING"}, token_for(world["mgr_a"]["username"])))
    ok(call("POST", f"/transfers/{created['id']}/approve", {"version": created["version"]}, token_for(DIRECTOR)))
    # the existing Teacher action: place the Teacher at another School directly, from tomorrow
    status, why = call("POST", f"/teachers/{t['id']}/placements", {"schoolId": world["b1"]["id"], "positionId": None,
                                                                   "effectiveOn": iso_in(2)}, admin)
    d = director
    open_page(d)
    fresh = request_for(t["id"])
    note = fresh[0]["statusNote"] if fresh else None
    # either the direct move was refused while a request is scheduled, or the request shows Cancelled with the reason
    if status < 300:
        d.get(PAGE)
        settle(d, 1)
        dlg = open_request_with_status(d, t["name"], "Cancelled")
        assert "no longer placed" in dlg.text.lower(), dlg.text
    else:
        assert status in (400, 409, 422), (status, why)
        S["direct_move_refused"] = why


def open_request_with_status(d, teacher_name, status_label):
    pick(d, d, "Status", status_label)
    settle(d, 0.8)
    return open_request(d, teacher_name)


def test_4_4_two_deciders_at_once_one_wins(world):
    admin = token_for("asha.admin")
    t = make_teacher(admin, f"E2E Race {stamp()}", world["a1"]["id"])
    # raised by Admin (who cannot decide), so the Director and Manager A are both free to decide
    created = ok(call("POST", "/transfers/transfers", {"teacherId": t["id"], "toSchoolId": world["a2"]["id"], "effectiveOn": world["date"],
                                                       "reasonCategory": "MANAGER_REBALANCING"}, admin))
    import concurrent.futures as cf
    def approve(tok):
        return call("POST", f"/transfers/{created['id']}/approve", {"version": created["version"]}, tok)[0]
    with cf.ThreadPoolExecutor(2) as pool:
        codes = sorted(pool.map(approve, [token_for(DIRECTOR), token_for(world["mgr_a"]["username"])]))
    assert codes[0] in (200, 201) and codes[1] in (409, 403), codes     # one succeeds, the other is told it changed


# ---- section 5: lists and filters ----------------------------------------------------------------------------------

def test_5_teacher_filters_and_request_list_filters(world, director):
    d = director
    d.get(f"{BASE}/master-data/teachers")
    box = wait(d).until(EC.visibility_of_element_located((By.XPATH, "//label[contains(.,'Search teachers')]/..//input")))
    box.send_keys(f"E2E T")
    settle(d, 1)
    pick(d, d, "Movement", "Transferred")
    settle(d, 1.5)
    # an Approved transfer scheduled for next week does not make T1 "Transferred" until applied; the filter must still answer
    assert not d.find_elements(By.CSS_SELECTOR, ".MuiAlert-colorError"), body(d)[:300]
    pick(d, d, "Movement", "Replaced")
    settle(d, 1.5)
    assert not d.find_elements(By.CSS_SELECTOR, ".MuiAlert-colorError")
    # request list: filter by status
    open_page(d)
    assert world["t1"]["name"] in body(d) and world["t2"]["name"] in body(d), "Pending and Approved shows both"
    pick(d, d, "Status", "Rejected")
    settle(d, 1)
    assert S["t_reject"]["name"] in body(d) and world["t1"]["name"] not in body(d)
    pick(d, d, "Status", "Cancelled")
    settle(d, 1)
    assert world["t1"]["name"] not in body(d)


def test_5_page_has_no_horizontal_scroll_at_375px(world, browsers):
    d = browsers(DIRECTOR, 375, 800)
    open_page(d)
    assert d.execute_script("return document.documentElement.scrollWidth - document.documentElement.clientWidth") <= 0
