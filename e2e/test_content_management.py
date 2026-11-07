"""Browser walkthrough for spec 029 (Content Management): quickstart.md steps 1 to 11 (and the web part of 12), in the order of the quickstart.

Same setup as test_school_working_days.py (demo seed: Admin asha.admin, Director 9800000002, Manager manoj.manager, Teacher 9800000004).
The tests depend on each other (one story told in order) and share the state in STATE. A new Content Contributor is created on the User
Management screen (step 1); every other step is done in the browser. Real files are written to a temporary folder: a 20 MB mp4, a 30 MB
and a small pdf, a docx, a .doc and a Windows program renamed .pdf.

Covered: 1 create a Contributor-only user, 2 their menu and dashboard and a refused screen, 3 create and submit a 20 MB mp4 (version 1,
Submitted, no task), 4 the PDF size limit, a .doc and an .exe renamed .pdf are refused, 5 Director downloads and returns with a comment
(Contributor notified, read through the notifications API), 6 version 2 docx, approval, history, 7 content for a Manager with a due date
(task Completed then Verified), 8 a Director cannot approve their own content, 9 a new version of approved content returns it to Submitted
and keeps the earlier approved version marked, 10 Admin removes a file version with a reason, 11 the permission matrix has six role columns.
Not covered: 8 the "no reviewer" notice and granting Approve to Admin in the matrix (that changes permissions for everybody on the shared
stack); 12 the Android sign-in being refused and the Director dashboard tile count (phone); 13 expense bill and MoU upload limits
(other screens, API-level); large-upload behaviour behind Cloudflare; dark mode.
"""
import os
import tempfile
import time
import zipfile

import pytest
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC

import api_helpers as api
from browsers import Browsers, body, click, fill, goto, load, menu_labels, wait
from test_school_working_days import ADMIN, BASE, DIRECTOR, MANAGER

TAG = str(int(time.time()))
PHONE = "94" + TAG[-8:]
NAME = f"E2E Contributor {TAG}"
TITLE = f"Phonics Week 1 {TAG}"
STATE = {}
MB = 1024 * 1024


@pytest.fixture(scope="module")
def browsers():
    b = Browsers()
    yield b
    b.quit_all()


@pytest.fixture(scope="module")
def files():
    folder = tempfile.mkdtemp(prefix="hls-content-")

    def make(name, head, size):
        path = os.path.join(folder, name)
        with open(path, "wb") as f:
            f.write(head)
            f.write(b"\0" * max(0, size - len(head)))
        return path

    docx = os.path.join(folder, "lesson.docx")
    with zipfile.ZipFile(docx, "w") as z:
        z.writestr("[Content_Types].xml", '<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"/>')
        z.writestr("word/document.xml", "<w:document/>")
    return {
        "mp4": make("phonics.mp4", b"\0\0\0\x18ftypmp42\0\0\0\0mp42isom", 20 * MB),
        "pdf30": make("big.pdf", b"%PDF-1.4\n", 30 * MB),
        "pdf": make("notes.pdf", b"%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n", 2000),
        "doc": make("old.doc", b"\xd0\xcf\x11\xe0\xa1\xb1\x1a\xe1", 3000),
        "exe": make("setup.pdf", b"MZ\x90\x00\x03\x00\x00\x00", 3000),
        "docx": docx,
    }


def attach(d, path, settle=True):
    """Pick a file in the (hidden) Content file input; the page uploads it at once."""
    box = d.find_element(By.CSS_SELECTOR, "input[aria-label='Content file']")
    d.execute_script("arguments[0].hidden=false; arguments[0].style.display='block';", box)
    box.send_keys(path)
    if settle:
        time.sleep(1)
        wait(d, 180).until(lambda x: "Uploading" not in body(x))


def alert_text(d):
    return " ".join(a.text for a in d.find_elements(By.CSS_SELECTOR, "[role=alert]"))


def open_content(d, content_id):
    goto(d, f"/operations/content/{content_id}")
    wait(d).until(lambda x: x.find_elements(By.XPATH, "//h2[normalize-space()='Versions']"))


def open_by_title(d, title):
    goto(d, "/operations/content")
    click(d, f"//table[@aria-label='Content']//button[normalize-space()='{title}'] | //table[@aria-label='Content']//a[normalize-space()='{title}']")
    wait(d).until(lambda x: x.find_elements(By.XPATH, "//h2[normalize-space()='Versions']"))


def notifications(user):
    status, data = api.call(api.login(user), "GET", "/api/v1/notifications?size=50")
    return str(data) if status == 200 else ""


def version_count(d):
    return len(d.find_elements(By.XPATH, "//p[starts-with(normalize-space(),'Version ')]"))


# 1. Create the user ---------------------------------------------------------------------------------------------------

def test_1_admin_creates_a_user_with_only_the_content_contributor_role(browsers):
    d = browsers.as_user(ADMIN)
    goto(d, "/identity/users")
    click(d, "//button[normalize-space()='Create user']")
    dlg = wait(d).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    assert "CONTENT_CONTRIBUTOR" in dlg.text                                  # the role is in the picker
    assert "Manager record" not in dlg.text and "Teacher record" not in dlg.text    # no Manager or Teacher record is asked for
    dlg.find_element(By.XPATH, ".//label[contains(.,'Display name')]/..//input").send_keys(NAME)
    dlg.find_element(By.XPATH, ".//label[contains(.,'Phone number')]/..//input").send_keys(PHONE)
    dlg.find_element(By.XPATH, ".//label[contains(.,'Initial password')]/..//input").send_keys(api.PASSWORD)
    dlg.find_element(By.XPATH, ".//label[normalize-space()='CONTENT_CONTRIBUTOR']//input").click()
    dlg.find_element(By.XPATH, ".//button[normalize-space()='Create user']").click()
    wait(d, 30).until(EC.invisibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    status, data = api.call(api.login(ADMIN), "GET", f"/api/v1/identity/users?query={PHONE}")
    assert status == 200 and PHONE in str(data) and "CONTENT_CONTRIBUTOR" in str(data) and "MANAGER" not in str(data) and "TEACHER" not in str(data)


# 2. The Contributor's world -------------------------------------------------------------------------------------------

def test_2_contributor_menu_dashboard_and_refused_screen(browsers):
    d = browsers.as_user(PHONE)
    labels = [x for x in menu_labels(d) if x]
    assert any("Content" in x for x in labels), labels
    for forbidden in ("Manager Tasks", "My Tasks", "Payroll Report", "Incentives", "School Contracts", "User Management"):
        assert forbidden not in labels, (forbidden, labels)
    goto(d, "/dashboard")
    wait(d, 30).until(lambda x: "My content" in body(x))
    for card in ("Returned to me", "Approved"):
        assert card in body(d)
    load(d, "/operations/tasks")
    wait(d).until(lambda x: "Not authorized" in body(x))


# 3 and 4. Create, submit, refusals ------------------------------------------------------------------------------------

def test_3_create_content_and_submit_a_20mb_mp4(browsers, files):
    d = browsers.as_user(PHONE)
    goto(d, "/operations/content")
    click(d, "//button[normalize-space()='New content']")
    dlg = wait(d).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    dlg.find_element(By.XPATH, ".//label[starts-with(normalize-space(),'Title')]/..//input").send_keys(TITLE)
    dlg.find_element(By.XPATH, ".//label[starts-with(normalize-space(),'Category')]/..//input").send_keys("Lesson")
    dlg.find_element(By.XPATH, ".//label[starts-with(normalize-space(),'Subject')]/..//input").send_keys("English")
    dlg.find_element(By.XPATH, ".//button[normalize-space()='Create']").click()
    wait(d, 30).until(lambda x: "/operations/content/" in x.current_url and x.current_url.rsplit("/", 1)[-1] != "content")
    STATE["id"] = d.current_url.rsplit("/", 1)[-1]
    wait(d).until(lambda x: x.find_elements(By.XPATH, "//h2[normalize-space()='Versions']"))
    attach(d, files["mp4"])
    wait(d, 60).until(lambda x: "Version 1" in body(x))
    assert "Submitted" in d.find_element(By.XPATH, "//h1/following-sibling::*[contains(@class,'MuiChip')]").text
    assert "phonics.mp4" in body(d)
    assert "Linked task" not in body(d)                                       # a Contributor's own content has no task
    assert version_count(d) == 1


def test_4_oversize_pdf_doc_and_renamed_program_are_refused(browsers, files):
    d = browsers.as_user(PHONE)
    open_content(d, STATE["id"])
    # a new version slot is offered again only when the content is returned; this item is Submitted, so use a second, draft item for refusals
    goto(d, "/operations/content")
    click(d, "//button[normalize-space()='New content']")
    dlg = wait(d).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    dlg.find_element(By.XPATH, ".//label[starts-with(normalize-space(),'Title')]/..//input").send_keys(f"Refusals {TAG}")
    dlg.find_element(By.XPATH, ".//label[starts-with(normalize-space(),'Category')]/..//input").send_keys("Lesson")
    dlg.find_element(By.XPATH, ".//label[starts-with(normalize-space(),'Subject')]/..//input").send_keys("English")
    dlg.find_element(By.XPATH, ".//button[normalize-space()='Create']").click()
    wait(d, 30).until(lambda x: x.current_url.rsplit("/", 1)[-1] not in ("content", ""))
    wait(d).until(lambda x: x.find_elements(By.XPATH, "//h2[normalize-space()='Versions']"))
    for key, expect in (("pdf30", "25"), ("doc", "not accepted"), ("exe", "")):
        attach(d, files[key])
        wait(d, 30).until(lambda x: alert_text(x) != "")
        text = alert_text(d)
        assert text, key
        if expect:
            assert expect in text, (key, text)
        assert "Version 1" not in body(d), key                                # nothing was stored
        d.refresh()
        wait(d).until(lambda x: x.find_elements(By.XPATH, "//h2[normalize-space()='Versions']"))
    assert "Nothing has been submitted yet." in body(d)


# 5 and 6. Review ------------------------------------------------------------------------------------------------------

def test_5_director_downloads_and_returns_with_a_comment(browsers):
    d = browsers.as_user(DIRECTOR)
    open_by_title(d, TITLE)
    before = time.time() - 1
    click(d, "//button[normalize-space()='Download']")
    path = browsers.wait_download("phonics*.mp4", before, 60)
    assert os.path.getsize(path) > 19 * MB
    click(d, "//button[normalize-space()='Return for revision']")
    dlg = wait(d).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    dlg.find_element(By.XPATH, ".//textarea[not(@aria-hidden='true')]").send_keys("Please add the phonics chart")
    dlg.find_element(By.XPATH, ".//button[normalize-space()='Return']").click()
    wait(d, 30).until(lambda x: "Returned" in x.find_element(By.TAG_NAME, "h1").find_element(By.XPATH, "following-sibling::*").text)
    assert "Please add the phonics chart" in body(d)
    time.sleep(2)
    assert TITLE in notifications(PHONE)


def test_6_version_2_docx_approved_history_shows_both(browsers, files):
    d = browsers.as_user(PHONE)
    open_content(d, STATE["id"])
    attach(d, files["docx"])
    wait(d, 60).until(lambda x: "Version 2" in body(x))
    dr = browsers.as_user(DIRECTOR)
    open_content(dr, STATE["id"])
    click(dr, "//button[starts-with(normalize-space(),'Approve version')]")
    wait(dr, 30).until(lambda x: "Approved on" in body(x))
    text = body(dr)
    assert "Version 1" in text and "Version 2" in text and "Please add the phonics chart" in text
    assert "returned by" in text and "approved by" in text


# 7. Content for a Manager ---------------------------------------------------------------------------------------------

def test_7_content_for_a_manager_creates_a_task_that_follows_the_content(browsers, files):
    d = browsers.as_user(DIRECTOR)
    goto(d, "/operations/content")
    click(d, "//button[normalize-space()='New content']")
    dlg = wait(d).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    dlg.find_element(By.XPATH, ".//label[starts-with(normalize-space(),'Title')]/..//input").send_keys(f"Manager guide {TAG}")
    dlg.find_element(By.XPATH, ".//label[starts-with(normalize-space(),'Category')]/..//input").send_keys("Guide")
    dlg.find_element(By.XPATH, ".//label[starts-with(normalize-space(),'Subject')]/..//input").send_keys("Visits")
    dlg.find_element(By.XPATH, ".//label[starts-with(normalize-space(),'Author')]/..//div[@role='combobox']").click()
    click(d, "//li[@role='option'][contains(normalize-space(),'Manoj Manager')]")
    due = time.strftime("%Y-%m-%d", time.localtime(time.time() + 3 * 86400))
    day = dlg.find_element(By.XPATH, ".//label[starts-with(normalize-space(),'Due date')]/..//input")
    day.click()
    day.send_keys(due[8:10] + due[5:7] + due[0:4])
    dlg.find_element(By.XPATH, ".//button[normalize-space()='Create']").click()
    wait(d, 30).until(lambda x: x.current_url.rsplit("/", 1)[-1] not in ("content", ""))
    STATE["mgr_id"] = d.current_url.rsplit("/", 1)[-1]
    wait(d).until(lambda x: "Linked task" in body(x))
    # the Manager sees the task, submits a pdf, the task shows Completed
    m = browsers.as_user(MANAGER)
    goto(m, "/operations/tasks")
    click(m, "//button[@role='tab'][starts-with(normalize-space(),'All')]")
    wait(m).until(lambda x: f"Manager guide {TAG}" in body(x))
    open_content(m, STATE["mgr_id"])
    attach(m, files["pdf"])
    wait(m, 60).until(lambda x: "Version 1" in body(x))
    m.find_element(By.XPATH, "//a[normalize-space()='Linked task']").click()
    wait(m).until(lambda x: "Completed" in body(x))
    # the Director approves, the task shows Verified
    open_content(d, STATE["mgr_id"])
    click(d, "//button[starts-with(normalize-space(),'Approve version')]")
    wait(d, 30).until(lambda x: "Approved on" in body(x))
    d.find_element(By.XPATH, "//a[normalize-space()='Linked task']").click()
    wait(d).until(lambda x: "Verified" in body(x))


# 8. Own content -------------------------------------------------------------------------------------------------------

def test_8_director_cannot_approve_their_own_content(browsers, files):
    d = browsers.as_user(DIRECTOR)
    goto(d, "/operations/content")
    click(d, "//button[normalize-space()='New content']")
    dlg = wait(d).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    dlg.find_element(By.XPATH, ".//label[starts-with(normalize-space(),'Title')]/..//input").send_keys(f"Director own {TAG}")
    dlg.find_element(By.XPATH, ".//label[starts-with(normalize-space(),'Category')]/..//input").send_keys("Guide")
    dlg.find_element(By.XPATH, ".//label[starts-with(normalize-space(),'Subject')]/..//input").send_keys("Policy")
    dlg.find_element(By.XPATH, ".//button[normalize-space()='Create']").click()
    wait(d, 30).until(lambda x: x.current_url.rsplit("/", 1)[-1] not in ("content", ""))
    wait(d).until(lambda x: x.find_elements(By.XPATH, "//h2[normalize-space()='Versions']"))
    STATE["own_id"] = d.current_url.rsplit("/", 1)[-1]
    attach(d, files["pdf"])
    wait(d, 60).until(lambda x: "Version 1" in body(x))
    assert not d.find_elements(By.XPATH, "//button[starts-with(normalize-space(),'Approve version')]")
    assert not d.find_elements(By.XPATH, "//button[normalize-space()='Return for revision']")


# 9 and 10. Versions and removal ---------------------------------------------------------------------------------------

def test_9_new_version_of_approved_content_returns_it_to_submitted_and_keeps_the_approved_mark(browsers, files):
    d = browsers.as_user(PHONE)
    open_content(d, STATE["id"])
    attach(d, files["pdf"])
    wait(d, 60).until(lambda x: "Version 3" in body(x))
    assert "Submitted" in d.find_element(By.XPATH, "//h1/following-sibling::*[contains(@class,'MuiChip')]").text
    approved_marks = d.find_elements(By.XPATH, "//*[contains(@class,'MuiChip')][normalize-space()='Approved']")
    assert approved_marks, "the earlier approved version should still be marked Approved"


def test_10_admin_removes_a_file_version_with_a_reason(browsers):
    d = browsers.as_user(ADMIN)
    open_content(d, STATE["id"])
    removes = d.find_elements(By.XPATH, "//button[normalize-space()='Remove file']")
    assert removes
    removes[-1].click()                                                       # the oldest version is listed last
    dlg = wait(d).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "[role=dialog]")))
    dlg.find_element(By.XPATH, ".//textarea[not(@aria-hidden='true')]").send_keys("E2E: wrong file")
    dlg.find_element(By.XPATH, ".//button[normalize-space()='Remove']").click()
    wait(d, 30).until(lambda x: "File removed" in body(x))
    assert "Removed: E2E: wrong file" in body(d)
    assert "Version 1" in body(d)                                             # the row stays


# 11. Permission matrix -------------------------------------------------------------------------------------------------

def test_11_permission_matrix_has_six_role_columns(browsers):
    d = browsers.as_user(ADMIN)
    goto(d, "/identity/permissions")
    wait(d, 30).until(lambda x: "CONTENT_CONTRIBUTOR" in body(x) or "Content Contributor" in body(x))
    text = body(d)
    for role in ("SYSTEM", "ADMIN", "DIRECTOR", "MANAGER", "TEACHER"):
        assert role in text.upper()
    assert "CONTENT" in text.upper()
