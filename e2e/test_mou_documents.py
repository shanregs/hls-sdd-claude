"""Browser walkthrough for spec 031 (MoU Documents and Approval): quickstart.md sections 1 to 3, task T056.

Needs the local stack and Chrome. Run from this folder:
    pytest test_mou_documents.py -v                # headless
    HEADED=1 pytest test_mou_documents.py -v -s    # watch it

One private world is built through the API (Zone A with Schools A1 and A2 under Manager A, Zone B with School B1 under
Manager B); each School gets a recorded MoU through the API. Uploads, submit, reject, resubmit, approve and the lists go
through the browser. Not covered: section 4 (a Java interface, covered by an integration test), the notification inboxes,
the 12 MB file, the two-browser race (2.10) and section 5 (needs the 008 attendance screens).
"""
import os
import struct
import time
import zlib
from datetime import date, timedelta

import pytest
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

import walk_support as api

BASE = api.BASE
ADMIN, DIRECTOR = "asha.admin", "9800000002"
S = {}


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


def click(d, el):
    d.execute_script("arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", el)


def button(d, label):
    return wait(d).until(EC.presence_of_element_located((By.XPATH, f"//button[normalize-space()='{label}']")))


def has_button(d, label):
    return bool(d.find_elements(By.XPATH, f"//button[normalize-space()='{label}']"))


def open_school(d, school):
    spa_goto(d, "/dashboard")  # leave the page first so that it loads fresh data
    wait(d).until(lambda x: "/dashboard" in x.current_url)
    time.sleep(0.5)
    spa_goto(d, f"/operations/school-contracts/schools/{school['id']}")
    wait(d).until(EC.visibility_of_element_located((By.XPATH, f"//h1[normalize-space()='{school['name']}']")))
    wait(d).until(lambda x: not x.find_elements(By.XPATH, "//*[contains(text(),'Loading approval')]"))
    time.sleep(0.5)


def expect_text(d, text, timeout=20):
    try:
        wait(d, timeout).until(lambda x: text in body(x))
    except Exception:
        raise AssertionError(f"expected '{text}'; the page says: {body(d)[:600]}")


def upload(d, path, expect):
    d.find_element(By.XPATH, "//input[@aria-label='Signed MoU document']").send_keys(path)
    if expect:
        expect_text(d, expect)


def confirm(d, label):
    dlg = wait(d).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    dlg.find_element(By.XPATH, f".//button[normalize-space()='{label}']").click()
    wait(d).until(EC.invisibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))


def make_files(folder):
    pdf = os.path.join(folder, "mou-signed.pdf")
    with open(pdf, "wb") as f:
        f.write(b"%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
                b"3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n")

    def chunk(t, data):
        c = struct.pack(">I", len(data)) + t + data
        return c + struct.pack(">I", zlib.crc32(t + data) & 0xFFFFFFFF)
    png = os.path.join(folder, "mou-scan.png")
    with open(png, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(b"\x00\xff\x00\x00")) + chunk(b"IEND", b""))
    docx = os.path.join(folder, "terms.docx")
    with open(docx, "wb") as f:
        f.write(b"PK\x03\x04 not really a document")
    fake = os.path.join(folder, "program.pdf")
    with open(fake, "wb") as f:
        f.write(b"MZ\x90\x00 this is a program renamed to pdf" * 5)
    return {"pdf": pdf, "png": png, "docx": docx, "fake": fake}


def record_mou(school, rate="1200.00"):
    """A new School has a "MoU pending" placeholder contract; record the MoU on it (the 012 flow)."""
    admin = "asha.admin"
    cands = api.ok(api.call(admin, "GET", f"/api/v1/school-contracts/signatory-candidates?schoolId={school['id']}"))
    hls = [{"userId": c["userId"], "designation": c["designation"]} for c in cands][:2]
    held = api.ok(api.call(admin, "GET", f"/api/v1/school-contracts/schools/{school['id']}"))["contracts"]
    placeholder = next((c for c in held if c["state"] == "RATE_PENDING"), None)
    signed = (date.today() - timedelta(days=5)).isoformat()
    terms = {"teacherCount": 2, "salaryMode": "SAME_FOR_ALL", "rate": rate, "signedOn": signed,
             "schoolSignatories": [{"name": "R. Kumar", "designation": "Principal"}], "hlsSignatories": hls}
    if placeholder:
        return api.ok(api.call(admin, "PUT", f"/api/v1/school-contracts/contracts/{placeholder['id']}/mou", terms))
    return api.ok(api.call(admin, "POST", f"/api/v1/school-contracts/schools/{school['id']}/contracts", {**terms, "startsOn": signed}))


@pytest.fixture(scope="module")
def world(tmp_path_factory):
    w = api.build_world(api.stamp(), teachers_a=1, teachers_b=1)
    w["files"] = make_files(str(tmp_path_factory.mktemp("mou")))
    w["c_a1"] = record_mou(w["a1"])
    w["c_a2"] = record_mou(w["a2"], rate="1000.00")
    w["c_b1"] = record_mou(w["b1"], rate="900.00")
    S["w"] = w
    return w


@pytest.fixture
def admin(world):
    yield as_user(ADMIN)


def test_1_attach_documents_and_refusals(admin, world):
    open_school(admin, world["a1"])
    expect_text(admin, "Signed document and approval")
    expect_text(admin, "No signed document attached yet")
    upload(admin, world["files"]["pdf"], "Version 1: mou-signed.pdf")
    upload(admin, world["files"]["png"], "Version 2: mou-scan.png")
    assert "Version 1: mou-signed.pdf" in body(admin), "version 1 stays listed"
    upload(admin, world["files"]["docx"], "Only PDF, JPG or PNG")
    upload(admin, world["files"]["fake"], "")  # a program renamed .pdf: the server refuses it
    time.sleep(2)
    assert "Version 3" not in body(admin), "no third version was stored"
    assert "Version 2: mou-scan.png" in body(admin)


def test_1_remove_version_with_reason(admin, world):
    open_school(admin, world["a1"])
    remove = admin.find_elements(By.XPATH, "//button[contains(normalize-space(),'Remove')]")
    assert remove, "Admin can remove a version"
    click(admin, remove[-1])  # the oldest version is listed last
    dlg = wait(admin).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    dlg.find_element(By.XPATH, ".//textarea[not(@aria-hidden='true')] | .//input[@type='text']").send_keys("wrong file")
    dlg.find_element(By.XPATH, ".//button[starts-with(normalize-space(),'Remove')]").click()
    expect_text(admin, "wrong file")
    expect_text(admin, "The version was removed")


def test_1_manager_sees_documents_but_cannot_upload_and_other_zone_gets_404(world):
    d = as_user(world["mgrA"]["phone"])
    try:
        open_school(d, world["a1"])
        expect_text(d, "Version 2: mou-scan.png")
        assert not d.find_elements(By.XPATH, "//input[@aria-label='Signed MoU document']"), "no Upload for a Manager"
        assert not has_button(d, "Send for approval")
    finally:
        d.quit()
    cid = world["c_a2"]["id"]
    boundary = "e2eboundary"
    crlf = "\r\n"
    multipart = (f"--{boundary}{crlf}Content-Disposition: form-data; name=\"file\"; filename=\"x.pdf\"{crlf}"
                 f"Content-Type: application/pdf{crlf}{crlf}%PDF-1.4 x{crlf}--{boundary}--{crlf}").encode()
    status, _ = api.call(world["mgrA"]["phone"], "POST", f"/api/v1/school-contracts/contracts/{cid}/documents",
                         raw=multipart, headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
    assert status == 403, status
    status, _ = api.call(world["mgrB"]["phone"], "GET", f"/api/v1/school-contracts/contracts/{cid}/approval")
    assert status == 404


def test_2_submit_reject_resubmit_approve(world):
    admin = as_user(ADMIN)
    mgr = as_user(world["mgrA"]["phone"])
    director = as_user(DIRECTOR)
    try:
        open_school(admin, world["a2"])
        upload(admin, world["files"]["pdf"], "Version 1: mou-signed.pdf")
        click(admin, button(admin, "Send for approval"))
        confirm(admin, "Send for approval")
        expect_text(admin, "The MoU was sent for approval")
        expect_text(admin, "Waiting for approval")
        assert not has_button(admin, "Approve"), "Admin cannot approve"

        open_school(mgr, world["a2"])
        click(mgr, button(mgr, "Reject"))
        dlg = wait(mgr).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
        dlg.find_element(By.XPATH, ".//button[normalize-space()='Reject']").click()
        expect_text(mgr, "Give a reason")
        dlg.find_element(By.XPATH, ".//textarea[not(@aria-hidden='true')]").send_keys("Rate does not match the signed copy")
        dlg.find_element(By.XPATH, ".//button[normalize-space()='Reject']").click()
        expect_text(mgr, "The MoU was rejected")

        open_school(admin, world["a2"])
        expect_text(admin, "Rate does not match the signed copy")
        upload(admin, world["files"]["png"], "Version 2: mou-scan.png")
        click(admin, button(admin, "Send for approval"))
        confirm(admin, "Send for approval")
        expect_text(admin, "Round 2")
        expect_text(admin, "Rate does not match the signed copy")  # the rejection stays in the history

        spa_goto(mgr, "/operations/school-contracts/approvals")
        wait(mgr).until(EC.visibility_of_element_located((By.XPATH, "//h1[normalize-space()='MoU Approvals']")))
        expect_text(mgr, world["a2"]["name"])

        open_school(director, world["a2"])
        click(director, button(director, "Approve"))
        confirm(director, "Approve")
        expect_text(director, "The MoU was approved")

        open_school(admin, world["a2"])
        expect_text(admin, "sends it back for approval")
        upload(admin, world["files"]["pdf"], "Version 3")
        expect_text(admin, "Waiting for approval")
    finally:
        for d in (admin, mgr, director):
            d.quit()


def test_2_submit_without_document_then_director_approves(world):
    admin = as_user(ADMIN)
    director = as_user(DIRECTOR)
    try:
        open_school(admin, world["b1"])
        expect_text(admin, "No signed document attached yet")
        click(admin, button(admin, "Send for approval"))
        confirm(admin, "Send for approval")
        expect_text(admin, "The MoU was sent for approval")
        open_school(director, world["b1"])
        click(director, button(director, "Approve"))
        confirm(director, "Approve")
        expect_text(director, "The MoU was approved")
        expect_text(director, "has no signed document attached")
    finally:
        admin.quit()
        director.quit()


def test_3_lists_and_manager_scope(world):
    admin = as_user(ADMIN)
    try:
        spa_goto(admin, "/operations/school-contracts")
        wait(admin).until(EC.visibility_of_element_located((By.XPATH, "//h1")))
        expect_text(admin, "Approval")
        expect_text(admin, "Document")
    finally:
        admin.quit()
    mgr_b = as_user(world["mgrB"]["phone"])
    try:
        spa_goto(mgr_b, "/operations/school-contracts/approvals")
        wait(mgr_b).until(EC.visibility_of_element_located((By.XPATH, "//h1[normalize-space()='MoU Approvals']")))
        time.sleep(1.5)
        assert world["a2"]["name"] not in body(mgr_b), "Manager B never sees Zone A MoUs"
    finally:
        mgr_b.quit()


def test_phone_width_has_no_horizontal_scroll(world):
    d = as_user(ADMIN, width=390)
    try:
        open_school(d, world["a2"])
        assert d.execute_script("return document.documentElement.scrollWidth <= window.innerWidth + 1")
    finally:
        d.quit()
