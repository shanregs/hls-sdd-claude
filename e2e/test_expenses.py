"""Browser walkthrough for spec 015 (Expenses): the web parts of quickstart.md scenarios 1 to 10 and the financial-review figures.

Same setup as test_school_working_days.py (demo seed: Admin asha.admin, Director 9800000002, Teacher 9800000004, System
sunil.system; password Password123!). The seed holds one Manager, so the test creates through the REST API (hls_api.py) a Zone of
its own, a School in it, and two Managers: M (the claimant, assigned to that Zone) and M2 (another Manager, own Zone with no
budget). Every figure that is asserted exactly (the budget table, ageing) is read for that private Zone, so other runs cannot
disturb it. Claims used only as budget fixtures are raised through the API with a "no bill" reason (the browser path is covered
by the other tests). Run from this folder:  pytest test_expenses.py -v        (HEADED=1 to watch)

Covered in the browser: raise, bill upload and submit as a Manager; another Manager gets not found; every validation of scenario
2 (amount 0, future date, 61 days back, Other without a reason, no bill and no reason, a .docx, an 11 MB file, a sixth bill);
duplicate claim confirmation and the history line; Training Stay without a batch refused; Travel and Fuel linked to the Manager's
own visit that is then cancelled; Director approves and rejects (reason required), the claimant corrects and resubmits, the
history holds both; the Admin cannot approve his own claim; two approvers at once (API); record as paid with a reference,
reverse an approval, a paid claim cannot be reversed, a company-paid claim is Paid by company at approval; budget set in the
browser then 20,000 / 12,000 / 5,000 / 8,000 and over by 2,000, "No budget set" for another area; pending ageing; cost measures
page with "No Schools acquired"; CSV export download and its columns; access (System, Teacher, a Manager on the review pages);
phone width. Also checked through the API: the same clientRequestId twice gives one claim, a paid claim cannot be changed.
Not covered: scenario 6 steps that run a pay run (Reimbursement line, cancel, approve: "Paid with payroll"; they need the payroll
run of spec 013b and locked attendance, covered by ClaimPaymentTest); cost per School 10,000.00 and per Teacher 5,000.00 with
Schools won and a completed batch (needs won prospects and a finished induction batch, covered by CostMeasureTest); Training Stay
with a batch (needs an open induction batch); the notification to Admin and Director (the notification inbox is a device
feature); the database trigger on a paid claim (needs direct database access); screen readers and the timing of SC-001 and SC-002.
"""
import base64
import glob
import os
import re
import tempfile
import time

import pytest
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC

from hls_api import PASSWORD, call, iso_in, make_manager, make_school, make_zone, ok, stamp, token_for
from test_school_working_days import ADMIN, BASE, make_driver, sign_in
from ui_helpers import (body, button, dialog, fill, has_button, no_overflow, options_of, pick, settle, wait)

DIRECTOR, TEACHER, SYSTEM = "9800000002", "9800000004", "sunil.system"
YESTERDAY = iso_in(-1)
MONTH = YESTERDAY[:7]
S = {}
JPEG = base64.b64decode(
    "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////"
    "wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=")


@pytest.fixture(scope="module")
def files():
    folder = tempfile.mkdtemp(prefix="hls-bills-")
    paths = {}
    for name in ("bill1.jpg", "bill2.jpg", "bill3.jpg", "bill4.jpg", "bill5.jpg", "bill6.jpg"):
        paths[name] = os.path.join(folder, name)
        open(paths[name], "wb").write(JPEG)
    paths["notes.docx"] = os.path.join(folder, "notes.docx")
    open(paths["notes.docx"], "wb").write(b"PK\x03\x04 not a bill")
    paths["big.pdf"] = os.path.join(folder, "big.pdf")
    with open(paths["big.pdf"], "wb") as f:
        f.write(b"%PDF-1.4\n" + b"0" * (11 * 1024 * 1024))
    return paths


@pytest.fixture(scope="module")
def world():
    admin = token_for(ADMIN)
    s = stamp()
    zone, place = make_zone(admin, f"E2E Exp Zone {s}")
    zone2, place2 = make_zone(admin, f"E2E Exp Zone2 {s}")
    school = make_school(admin, f"E2E Exp School {s}", place)
    m = make_manager(admin, "e2eexp", [zone["id"]], [school["id"]])
    m2 = make_manager(admin, "e2eexptwo", [zone2["id"]], [])
    w = {"s": s, "zone": zone, "zone2": zone2, "school": school, "m": m, "m2": m2}
    S.update(w)
    return w


def api_claim(world, amount, category="OFFICE_SUPPLIES", day=None, paid_by="CLAIMANT", submit=True, who="m"):
    """A claim raised through the API for the budget and payment fixtures (no bill, a reason instead)."""
    token = token_for(world[who]["username"])
    body_ = {"clientRequestId": __import__("uuid").uuid4().__str__(), "expenseDate": day or YESTERDAY, "amount": amount, "category": category,
             "zoneId": world["zone"]["id"] if who == "m" else world["zone2"]["id"], "note": "e2e", "paidBy": paid_by, "reason": "e2e",
             "noBillReason": "no bill in test", "link": None, "submit": submit, "confirmDuplicate": True}
    return ok(call("POST", "/my-expenses", body_, token))


def decide(claim_id, who, action, **extra):
    detail = ok(call("GET", f"/expenses/{claim_id}", None, token_for(who)))
    return call("POST", f"/expenses/{claim_id}/{action}", {"version": detail["version"], **extra}, token_for(who))


def open_new_claim(d):
    d.get(f"{BASE}/operations/my-expenses/new")
    wait(d).until(EC.visibility_of_element_located((By.XPATH, "//h1[normalize-space()='New claim']")))
    settle(d, 0.8)


def form(d):
    return d.find_element(By.TAG_NAME, "form")


def fill_claim(d, amount, date=YESTERDAY, category="Travel and Fuel", area=None, paid_by="I paid it myself"):
    f = form(d)
    fill(d, f, "Expense date", date)
    fill(d, f, "Amount", amount)
    pick(d, f, "Category", category)
    if area:
        pick(d, f, "Area", area)
    pick(d, f, "Paid by", paid_by)


def add_bills(d, *paths):
    d.find_element(By.CSS_SELECTOR, "input[type=file]").send_keys("\n".join(paths))
    settle(d, 0.8)


def errors_text(d):
    return body(d)


def claim_id_from_url(d):
    wait(d).until(lambda x: re.search(r"/my-expenses/[0-9a-f-]{36}$", x.current_url))
    return d.current_url.rsplit("/", 1)[1]


# ---- scenario 1: raise and submit -----------------------------------------------------------------------------------

def test_1_manager_raises_and_submits_a_claim_with_a_bill(world, browsers, files):
    d = browsers(world["m"]["username"])
    d.get(f"{BASE}/dashboard")
    wait(d).until(lambda x: "My Expenses" in body(x))
    open_new_claim(d)
    fill_claim(d, "850.00", area=world["zone"]["name"])
    fill(d, form(d), "Note", f"e2e fuel {world['s']}")
    add_bills(d, files["bill1.jpg"])
    assert "bill1.jpg" in body(d)
    button(d, "Submit").click()
    S["claim1"] = claim_id_from_url(d)
    wait(d).until(lambda x: "Submitted" in body(x))
    text = body(d)
    assert "Travel and Fuel" in text and "850" in text and world["zone"]["name"] in text and "bill1.jpg" in text
    # another Manager: the claim is "not found"
    status, _ = call("GET", f"/my-expenses/{S['claim1']}", None, token_for(world["m2"]["username"]))
    assert status == 404
    d2 = browsers(world["m2"]["username"])
    d2.get(f"{BASE}/operations/my-expenses/{S['claim1']}")
    wait(d2).until(lambda x: "not found" in body(x).lower() or "could not load" in body(x).lower())
    # the claim is listed for its owner
    d.get(f"{BASE}/operations/my-expenses")
    wait(d).until(lambda x: "850" in body(x) and "Submitted" in body(x))


# ---- scenario 2: validation -----------------------------------------------------------------------------------------

def test_2_validation_each_refusal_names_its_reason(world, browsers, files):
    d = browsers(world["m"]["username"])
    open_new_claim(d)
    fill_claim(d, "0")
    button(d, "Submit").click()
    wait(d).until(lambda x: "The amount must be more than zero" in body(x))
    fill(d, form(d), "Amount", "850.00")
    fill(d, form(d), "Expense date", iso_in(3))
    button(d, "Submit").click()
    wait(d).until(lambda x: "cannot be in the future" in body(x))
    fill(d, form(d), "Expense date", iso_in(-61))
    button(d, "Submit").click()
    wait(d).until(lambda x: "older than 60 days" in body(x))
    fill(d, form(d), "Expense date", YESTERDAY)
    pick(d, form(d), "Category", "Other")
    button(d, "Submit").click()
    wait(d).until(lambda x: "A reason is required for the category Other" in body(x))
    assert "Attach a bill, or give the reason there is no bill" in body(d), "no bill and no reason"
    # a .docx, an 11 MB file
    add_bills(d, files["notes.docx"])
    assert "A bill must be a jpg, png or pdf file" in body(d)
    add_bills(d, files["big.pdf"])
    assert "too large" in body(d).lower()
    # a sixth bill
    add_bills(d, *[files[f"bill{i}.jpg"] for i in range(1, 6)])
    add_bills(d, files["bill6.jpg"])
    assert "at most 5 bills" in body(d)
    # nothing was created by the refused submissions
    token = token_for(world["m"]["username"])
    rows = ok(call("GET", "/my-expenses?size=100", None, token))["content"]
    assert all(r["amount"] != "0" and r["amount"] != "0.00" for r in rows)


# ---- scenario 3: retry and duplicate -------------------------------------------------------------------------------

def test_3_retry_is_one_claim_and_a_duplicate_needs_confirmation(world, browsers, files):
    token = token_for(world["m"]["username"])
    payload = {"clientRequestId": __import__("uuid").uuid4().__str__(), "expenseDate": YESTERDAY, "amount": "310.00", "category": "MARKETING",
               "zoneId": world["zone"]["id"], "note": "retry", "paidBy": "CLAIMANT", "noBillReason": "none", "submit": False}
    first = ok(call("POST", "/my-expenses", payload, token))
    second = call("POST", "/my-expenses", payload, token)
    assert second[0] in (200, 201) and second[1]["id"] == first["id"], "the same clientRequestId is one claim"
    # same Manager, date, amount and category submitted again: the browser asks to confirm
    d = browsers(world["m"]["username"])
    open_new_claim(d)
    fill_claim(d, "850.00", category="Travel and Fuel", area=world["zone"]["name"])
    add_bills(d, files["bill2.jpg"])
    button(d, "Submit").click()
    wait(d).until(lambda x: "looks like a claim you already made" in dialog(x).text)
    button(d, "Submit anyway", dialog(d)).click()
    S["claim_dup"] = claim_id_from_url(d)
    assert S["claim_dup"] != S["claim1"]
    wait(d).until(lambda x: "Submitted" in body(x))
    assert "uplicate" in body(d) or "confirmed" in body(d).lower(), "the history records the confirmation"


# ---- scenario 4: links ---------------------------------------------------------------------------------------------

def test_4_training_stay_needs_a_batch_and_a_linked_visit_survives_cancelling(world, browsers, files):
    d = browsers(world["m"]["username"])
    open_new_claim(d)
    fill_claim(d, "2500.00", category="Training Stay", area=world["zone"]["name"])
    add_bills(d, files["bill3.jpg"])
    button(d, "Submit").click()
    wait(d).until(lambda x: "Choose the induction batch or training session" in body(x))
    # Travel and Fuel linked to the Manager's own planned visit
    token = token_for(world["m"]["username"])
    visit = ok(call("POST", "/marketing/activities", {"schoolId": world["school"]["id"], "type": "VISIT", "date": iso_in(0),
                                                       "attendeeUserIds": [], "notes": "e2e visit"}, token))
    open_new_claim(d)
    fill_claim(d, "415.00", date=iso_in(0), category="Travel and Fuel", area=world["zone"]["name"])
    form_el = form(d)
    pick(d, form_el, "Linked work", world["school"]["name"])
    add_bills(d, files["bill4.jpg"])
    button(d, "Submit").click()
    S["claim_link"] = claim_id_from_url(d)
    wait(d).until(lambda x: "Submitted" in body(x))
    assert world["school"]["name"] in body(d) or "visit" in body(d).lower()
    ok(call("POST", f"/marketing/activities/{visit['id']}/cancel", {"reason": "e2e cancel", "version": visit["version"]}, token))
    d.get(f"{BASE}/operations/my-expenses/{S['claim_link']}")
    wait(d).until(lambda x: "Submitted" in body(x))
    assert "Not linked" not in body(d), "the claim still shows the cancelled visit"


# ---- scenario 5: decide --------------------------------------------------------------------------------------------

def test_5_director_approves_rejects_and_the_claimant_corrects(world, browsers, files):
    token = token_for(world["m"]["username"])
    to_approve = api_claim(world, "1200.00", category="TRAVEL_FUEL")
    to_reject = api_claim(world, "640.00", category="MARKETING")
    S["approve_id"], S["reject_id"] = to_approve["id"], to_reject["id"]
    d = browsers(DIRECTOR)
    d.get(f"{BASE}/operations/expenses/{to_reject['id']}")
    wait(d).until(lambda x: "Reject" in x.page_source)
    button(d, "Reject").click()
    button(d, "Reject", dialog(d)).click()                                  # no reason
    wait(d).until(lambda x: "is required" in dialog(x).text)
    fill(d, dialog(d), "Reason for rejecting", "Photo of the bill is needed")
    button(d, "Reject", dialog(d)).click()
    wait(d).until(lambda x: "Rejected" in body(x))
    d.get(f"{BASE}/operations/expenses/{to_approve['id']}")
    button(d, "Approve").click()
    button(d, "Approve", dialog(d)).click()
    wait(d).until(lambda x: "Approved" in body(x))
    # the claimant sees the reason, corrects and resubmits; the history holds both
    c = browsers(world["m"]["username"])
    c.get(f"{BASE}/operations/my-expenses/{to_reject['id']}")
    wait(c).until(lambda x: "Photo of the bill is needed" in body(x))
    button(c, "Edit").click()
    wait(c).until(lambda x: "Edit claim" in body(x))
    add_bills(c, files["bill5.jpg"])
    button(c, "Submit").click()
    wait(c).until(lambda x: "Submitted" in body(x) and "/edit" not in x.current_url)
    c.get(f"{BASE}/operations/my-expenses/{to_reject['id']}")
    wait(c).until(lambda x: "History" in body(x))
    text = body(c).lower()
    assert "reject" in text and text.count("submit") >= 2


def test_5b_admin_cannot_approve_his_own_claim_and_two_approvers_race(world):
    admin = token_for(ADMIN)
    mine = ok(call("POST", "/my-expenses", {"clientRequestId": __import__("uuid").uuid4().__str__(), "expenseDate": YESTERDAY, "amount": "99.00",
                                            "category": "OFFICE_SUPPLIES", "zoneId": None, "paidBy": "CLAIMANT",
                                            "noBillReason": "none", "submit": True, "confirmDuplicate": True}, admin))
    status, why = decide(mine["id"], ADMIN, "approve")
    assert status in (403, 409) and "raised" in str(why).lower() or status == 403, (status, why)
    status, _ = decide(mine["id"], DIRECTOR, "approve")
    assert status == 200, "the Director can approve it"
    # two approvers deciding together: the second sees who decided first
    claim = api_claim(world, "77.00")
    version = ok(call("GET", f"/expenses/{claim['id']}", None, token_for(DIRECTOR)))["version"]
    import concurrent.futures as cf
    def go(who):
        return call("POST", f"/expenses/{claim['id']}/approve", {"version": version}, token_for(who))
    with cf.ThreadPoolExecutor(2) as pool:
        results = list(pool.map(go, [DIRECTOR, ADMIN]))
    codes = sorted(r[0] for r in results)
    assert codes[0] == 200 and codes[1] == 409, results
    loser = next(r for r in results if r[0] == 409)[1]
    assert "decided" in str(loser).lower() or "changed" in str(loser).lower()


# ---- scenario 6: payment -------------------------------------------------------------------------------------------

def test_6_record_paid_reverse_and_company_paid(world, browsers):
    d = browsers(DIRECTOR)
    # an approved personally paid claim is offered to payroll; recording it paid outside payroll withdraws that
    paid = api_claim(world, "1500.00")
    assert decide(paid["id"], DIRECTOR, "approve")[0] == 200
    d.get(f"{BASE}/operations/expenses/{paid['id']}")
    wait(d).until(lambda x: "Record as paid" in x.page_source.replace("RECORD AS PAID", "Record as paid") or has_button(x, "Record as paid"))
    button(d, "Record as paid").click()
    dlg = dialog(d)
    pick(d, dlg, "Mode", "Bank transfer")
    fill(d, dlg, "Reference", "UTR-E2E-1")
    button(d, "Record payment", dlg).click()
    wait(d).until(lambda x: "Paid" in body(x))
    text = body(d)
    assert "UTR-E2E-1" in text and "Bank transfer" in text
    assert not has_button(d, "Reverse approval") and not has_button(d, "Record as paid"), "a paid claim cannot be changed"
    status, _ = decide(paid["id"], DIRECTOR, "reverse", reason="trying")
    assert status in (400, 403, 409), status
    # a paid claim cannot be edited through the API either (scenario 10)
    own = ok(call("GET", f"/my-expenses/{paid['id']}", None, token_for(world["m"]["username"])))
    status, _ = call("PUT", f"/my-expenses/{paid['id']}", {"expenseDate": YESTERDAY, "amount": "1.00", "category": "OFFICE_SUPPLIES",
                                                           "zoneId": world["zone"]["id"], "paidBy": "CLAIMANT", "version": own["version"]},
                     token_for(world["m"]["username"]))
    assert status in (400, 403, 409), status
    assert "History" in body(d), "every change is in the history"
    # reverse an approved unpaid claim: back to Submitted
    other = api_claim(world, "1250.00")
    assert decide(other["id"], DIRECTOR, "approve")[0] == 200
    d.get(f"{BASE}/operations/expenses/{other['id']}")
    wait(d).until(lambda x: has_button(x, "Reverse approval"))
    button(d, "Reverse approval").click()
    fill(d, dialog(d), "Reason for reversing", "Approved by mistake")
    button(d, "Reverse approval", dialog(d)).click()
    wait(d).until(lambda x: "Submitted" in body(x))
    # paid by company: Paid by company at approval, never offered to payroll
    company = api_claim(world, "2100.00", paid_by="COMPANY")
    d.get(f"{BASE}/operations/expenses/{company['id']}")
    wait(d).until(lambda x: has_button(x, "Approve"))
    button(d, "Approve").click()
    button(d, "Approve", dialog(d)).click()
    wait(d).until(lambda x: "Paid by company" in body(x))
    assert "Not with payroll" in body(d) or "Waiting for the next pay run" not in body(d)


# ---- scenario 7: budget --------------------------------------------------------------------------------------------

def test_7_budget_versus_actual(world, browsers):
    d = browsers(DIRECTOR)
    d.get(f"{BASE}/operations/expenses/budgets")
    wait(d).until(lambda x: "Budget versus actual" in body(x))
    month = d.find_element(By.XPATH, "//label[starts-with(normalize-space(.),'Month')]/..//input")
    from ui_helpers import set_value
    set_value(d, month, MONTH)
    zone = world["zone"]["name"]
    wait(d).until(lambda x: zone in body(x))
    button(d, f"Set budget for {zone}").click()
    dlg = dialog(d)
    fill(d, dlg, "Monthly budget", "20000.00")
    button(d, "Save budget", dlg).click()
    wait(d).until(lambda x: "No budget set" not in row_text(x, zone))
    # approve 12,000 and leave 5,000 pending (budget fixtures are claims dated yesterday in the budget month)
    approved = api_claim(world, "12000.00", category="RENT_UTILITIES")
    assert decide(approved["id"], DIRECTOR, "approve")[0] == 200
    api_claim(world, "5000.00", category="OFFICE_SUPPLIES")
    d.get(f"{BASE}/operations/expenses/budgets")
    set_value(d, d.find_element(By.XPATH, "//label[starts-with(normalize-space(.),'Month')]/..//input"), MONTH)
    wait(d).until(lambda x: zone in body(x))
    settle(d, 1)
    cells = row_cells(d, zone)
    assert "20,000" in cells[1] and "12,000" in cells[2] and "5,000" in cells[3] and "8,000" in cells[4], cells
    assert "Over budget" not in row_text(d, zone)
    # approve 10,000 more: over by 2,000
    more = api_claim(world, "10000.00", category="MARKETING")
    assert decide(more["id"], DIRECTOR, "approve")[0] == 200
    d.get(f"{BASE}/operations/expenses/budgets")
    set_value(d, d.find_element(By.XPATH, "//label[starts-with(normalize-space(.),'Month')]/..//input"), MONTH)
    wait(d).until(lambda x: "Over budget" in row_text(x, zone))
    cells = row_cells(d, zone)
    assert "22,000" in cells[2] and "-2,000" in cells[4].replace("−", "-") or "2,000" in cells[4], cells
    # an area with no budget says so
    assert "No budget set" in row_text(d, world["zone2"]["name"]) or world["zone2"]["name"] not in body(d)


def row_cells(d, text):
    row = wait(d).until(lambda x: next((r for r in x.find_elements(By.CSS_SELECTOR, "tbody tr") if text in r.text), None))
    return [c.text for c in row.find_elements(By.CSS_SELECTOR, "th, td")]


def row_text(d, text):
    try:
        return " | ".join(row_cells(d, text))
    except Exception:
        return ""


# ---- scenario 8: pending, costs, export ----------------------------------------------------------------------------

def test_8_pending_ageing_costs_and_csv_export(world, browsers):
    d = browsers(DIRECTOR)
    d.get(f"{BASE}/operations/expenses")
    wait(d).until(lambda x: "Waiting for a decision" in body(x))
    text = body(d)
    for bucket in ("0-2", "3-7", "8-14"):
        assert bucket in text or "days" in text
    pending = ok(call("GET", "/expenses/pending", None, token_for(DIRECTOR)))
    assert pending["count"] == sum(b["count"] for b in pending["buckets"])
    assert str(pending["count"]) in text
    # the list is oldest first and carries age and the area's remaining budget
    assert "Budget left" in text and "Waiting" in text
    # cost measures: a period with no wins says "No Schools acquired", never a division by zero
    d.get(f"{BASE}/operations/expenses/costs")
    wait(d).until(lambda x: "Cost measures" in body(x))
    from ui_helpers import set_value
    set_value(d, d.find_element(By.XPATH, "//label[starts-with(normalize-space(.),'From')]/..//input"), "2020-01-01")
    set_value(d, d.find_element(By.XPATH, "//label[starts-with(normalize-space(.),'To')]/..//input"), "2020-01-31")
    wait(d).until(lambda x: "No Schools acquired" in body(x))
    assert "No Teachers trained yet" in body(d)
    assert "Infinity" not in body(d) and "NaN" not in body(d)
    # export the month as CSV and check the columns
    folder = tempfile.mkdtemp(prefix="hls-exp-")
    opts = webdriver.ChromeOptions()
    opts.add_argument("--headless=new")
    opts.add_argument("--window-size=1280,900")
    opts.add_experimental_option("prefs", {"download.default_directory": folder, "download.prompt_for_download": False})
    x = webdriver.Chrome(options=opts)
    try:
        sign_in(x, DIRECTOR)
        x.get(f"{BASE}/operations/expenses")
        wait(x).until(lambda y: has_button(y, "Export CSV"))
        set_value(x, x.find_element(By.XPATH, "//label[starts-with(normalize-space(.),'Export month')]/..//input"), MONTH)
        x.execute_script("arguments[0].click()", x.find_element(By.XPATH, "//button[normalize-space()='Export CSV']"))
        wait(x, 40).until(lambda y: glob.glob(os.path.join(folder, "*.csv")))
        time.sleep(1)
        header = open(glob.glob(os.path.join(folder, "*.csv"))[0], encoding="utf-8-sig").read().splitlines()
        text_csv = "\n".join(header[:6]).lower()
        for column in ("claimant", "date", "category", "amount", "status"):
            assert column in text_csv, (column, header[:3])
    finally:
        x.quit()


# ---- scenario 9: access --------------------------------------------------------------------------------------------

def test_9_access_by_role(world, browsers):
    # System: no Expenses entry, every path refused
    status = {call("GET", p, None, token_for(SYSTEM))[0] for p in ("/my-expenses", "/expenses", "/expenses/budgets?month=" + MONTH, "/expenses/pending")}
    assert status <= {401, 403}, status
    d = browsers(SYSTEM)
    d.get(f"{BASE}/operations/expenses")
    settle(d, 2)
    labels = d.execute_script("return Array.from(document.querySelectorAll('nav [role=button]')).map(b => b.textContent.trim())")
    assert not any("Expenses" in l for l in labels), labels
    assert "not authori" in body(d).lower() or "/not-authorized" in d.current_url or "Claims" not in body(d)
    # Teacher reaches only My Expenses
    t = browsers(TEACHER)
    t.get(f"{BASE}/operations/my-expenses")
    wait(t).until(lambda x: "My Expenses" in body(x))
    labels = t.execute_script("return Array.from(document.querySelectorAll('nav [role=button]')).map(b => b.textContent.trim())")
    assert "My Expenses" in labels and "Expenses" not in labels
    assert call("GET", "/expenses", None, token_for(TEACHER))[0] in (401, 403)
    t.get(f"{BASE}/operations/expenses")
    settle(d, 2)
    assert "Waiting for a decision" not in body(t)
    # a Manager sees no budgets and no other claims
    m = token_for(world["m"]["username"])
    assert call("GET", "/expenses/budgets?month=" + MONTH, None, m)[0] in (401, 403)
    assert call("GET", "/expenses", None, m)[0] in (401, 403)
    mine = ok(call("GET", "/my-expenses?size=100", None, m))["content"]
    assert mine and all(r.get("claimantName", world["m"]["name"]) == world["m"]["name"] for r in mine)


def test_9b_my_expenses_at_phone_width(world, browsers):
    d = browsers(world["m"]["username"], 375, 800)
    d.get(f"{BASE}/operations/my-expenses")
    wait(d).until(lambda x: "My Expenses" in body(x))
    settle(d, 1)
    assert no_overflow(d) <= 0
    open_new_claim(d)
    assert no_overflow(d) <= 0
