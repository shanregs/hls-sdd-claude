"""REST helpers the browser walkthroughs use to set up data the web screens cannot create in one step.

The assertions in the walkthroughs are always made in the browser; this module only prepares state (a won prospect, a School with an
approved MoU, a second Zone and Zone Manager, a Teacher leave day) with the same endpoints the screens call.
Run it as a script to try an endpoint:  python api_helpers.py manoj.manager GET /api/v1/incentives/mine
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

B = os.environ.get("BASE_URL", "http://localhost:5173")
PASSWORD = "Password123!"
DEMO_ZONE = "198d37e3-39ee-4201-8ae4-3ee3d391949b"   # looked up by name below; this is only a hint


_SESSIONS = {}   # nginx rate-limits sign-ins, so a token is reused for 10 minutes (it lives 15)


def login_full(user):
    cached = _SESSIONS.get(user)
    if cached and time.time() - cached[0] < 600:
        return cached[1]
    for attempt in range(6):
        r = urllib.request.Request(B + "/api/v1/auth/login", data=json.dumps({"identifier": user, "password": PASSWORD}).encode(),
                                   headers={"Content-Type": "application/json"})
        try:
            data = json.load(urllib.request.urlopen(r))
            _SESSIONS[user] = (time.time(), data)
            return data
        except urllib.error.HTTPError as e:
            if e.code != 429:
                raise
            time.sleep(10 * (attempt + 1))
    raise AssertionError("sign-in kept being rate limited for " + user)


def login(user):
    return login_full(user)["accessToken"]


def call(tok, method, path, body=None):
    r = urllib.request.Request(B + path, data=json.dumps(body).encode() if body is not None else None, method=method,
                               headers={"Content-Type": "application/json", "Authorization": "Bearer " + tok})
    try:
        x = urllib.request.urlopen(r)
        t = x.read().decode()
        return x.status, (json.loads(t) if t else None)
    except urllib.error.HTTPError as e:
        text = e.read().decode()
        try:
            return e.code, json.loads(text)
        except ValueError:
            return e.code, text[:600]


def must(result, what=""):
    status, body = result
    assert 200 <= status < 300, f"{what} failed: {status} {str(body)[:300]}"
    return body


def upload(tok, path, name, data, content_type):
    boundary = uuid.uuid4().hex
    payload = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{name}\"\r\nContent-Type: {content_type}\r\n\r\n").encode() \
        + data + f"\r\n--{boundary}--\r\n".encode()
    r = urllib.request.Request(B + path, data=payload, method="POST",
                               headers={"Content-Type": f"multipart/form-data; boundary={boundary}", "Authorization": "Bearer " + tok})
    try:
        x = urllib.request.urlopen(r)
        return x.status, json.loads(x.read().decode() or "null")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:400]


MIN_PDF = b"%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n"


def demo_zone(adm):
    zones = must(call(adm, "GET", "/api/v1/zones?query=Demo%20Zone&size=20"), "zones")["content"]
    return next(z for z in zones if z["name"] == "Demo Zone")


def demo_place(adm, zone_id):
    return must(call(adm, "GET", f"/api/v1/zones/{zone_id}/places?size=5"), "places")["content"][0]["id"]


def manager_of(user):
    """(managerId, userId) of the Manager who signs in as this user."""
    info = login_full(user)
    adm = login("asha.admin")
    for m in must(call(adm, "GET", "/api/v1/managers?size=100"), "managers")["content"]:
        if m["userId"] == info["user"]["id"]:
            return m["id"], m["userId"]
    raise AssertionError("no Manager record for " + user)


def director_id():
    return login_full("9800000002")["user"]["id"]


def make_zone_b_manager(tag):
    """A second Zone (own Zone) with a Manager who can sign in. Returns (phone, zoneId, zoneName, managerId)."""
    adm = login("asha.admin")
    phone = "95" + tag[-8:]
    user = must(call(adm, "POST", "/api/v1/identity/users", {"displayName": "E2E Zone B Mgr " + tag, "phone": phone, "roles": ["MANAGER"],
                                                           "username": None, "email": None, "initialPassword": PASSWORD}), "create user")
    zone = must(call(adm, "POST", "/api/v1/zones", {"name": "E2E Zone B " + tag}), "create zone")
    mgr = must(call(adm, "POST", "/api/v1/managers", {"userId": user["id"]}), "create manager")
    must(call(adm, "PUT", f"/api/v1/managers/{mgr['id']}/zones", {"zoneIds": [zone["id"]], "version": mgr["version"]}), "manager zones")
    return phone, zone["id"], zone["name"], mgr["id"]


def won_school_with_mou(name, owner="manoj.manager", approve=True, document=False):
    """Prospect (owned by `owner`, or none when owner is None) -> won -> School with Zone Manager Manoj -> MoU submitted and,
    when `approve`, approved by the Director. Returns dict(schoolId, name, contractId)."""
    adm, dr = login("asha.admin"), login("9800000002")
    zone = demo_zone(adm)["id"]
    place = demo_place(adm, zone)
    manoj_mgr_id, _ = manager_of("manoj.manager")
    if owner is None:
        school = must(call(adm, "POST", "/api/v1/schools", {"name": name, "address": "1 Test Road", "contactPerson": "Pat", "phone": "9444400101",
                                                             "billingContact": "billing@example.com", "placeId": place}), "create school")
        school_id = school["id"]
    else:
        tok = login(owner)
        p = must(call(tok, "POST", "/api/v1/marketing/prospects", {"name": name, "board": "CBSE", "address": "1 Test Road", "zoneId": zone,
                                                                   "contactPerson": "Pat", "designation": "Principal", "phone": "9444400101",
                                                                   "expectedTeachers": 2}), "create prospect")
        pid = p["row"]["id"]
        must(call(tok, "POST", f"/api/v1/marketing/prospects/{pid}/proposals",
                  {"teacherCount": 2, "startMonth": "2026-12", "salaryMode": "SAME_FOR_ALL", "rate": "15000", "notes": "e2e"}), "proposal")
        must(call(tok, "POST", f"/api/v1/marketing/prospects/{pid}/stage", {"stage": "FINAL_STAGE"}), "final stage")
        must(call(dr, "POST", f"/api/v1/marketing/prospects/{pid}/review", {"decision": "APPROVE"}), "review")
        win = must(call(adm, "POST", f"/api/v1/marketing/prospects/{pid}/win", {"placeId": place, "billingContact": "billing@example.com"}), "win")
        school_id = win["handoff"]["schoolId"]
    must(call(adm, "PUT", f"/api/v1/schools/{school_id}/manager", {"managerId": manoj_mgr_id}), "assign manager")
    today = time.strftime("%Y-%m-%d")
    signed = time.strftime("%Y-%m-%d", time.localtime(time.time() - 2 * 86400))   # the server may be a day behind this clock
    contract = must(call(adm, "POST", f"/api/v1/school-contracts/schools/{school_id}/contracts", {
        "startsOn": today, "endsOn": None, "teacherCount": 2, "salaryMode": "SAME_FOR_ALL", "rate": "15000", "signedOn": signed,
        "schoolSignatories": [{"name": "Pat Principal", "designation": "Principal"}],
        "hlsSignatories": [{"userId": director_id(), "designation": "Director"}]}), "contract")
    cid = contract["id"]
    if document:
        must(upload(adm, f"/api/v1/school-contracts/contracts/{cid}/documents", "mou.pdf", MIN_PDF, "application/pdf"), "document")
    if approve:
        submit_and_approve(cid)
    return {"schoolId": school_id, "name": name, "contractId": cid}


def approval_version(tok, cid):
    return must(call(tok, "GET", f"/api/v1/school-contracts/contracts/{cid}/approval"), "approval")["version"]


def submit_and_approve(cid):
    adm, dr = login("asha.admin"), login("9800000002")
    state = must(call(adm, "GET", f"/api/v1/school-contracts/contracts/{cid}/approval"), "approval")["state"]
    if state != "PENDING":      # a document change on an approved MoU already puts it back to pending
        must(call(adm, "POST", f"/api/v1/school-contracts/contracts/{cid}/submit", {"version": approval_version(adm, cid)}), "submit")
    approve_mou(cid)


def approve_mou(cid):
    dr = login("9800000002")
    must(call(dr, "POST", f"/api/v1/school-contracts/contracts/{cid}/approve", {"version": approval_version(dr, cid)}), "approve MoU")


def incentive_of(school_id, timeout=60):
    """Waits for the incentive the MoU approval earned (it is created after the commit) and returns its list row."""
    dr = login("9800000002")
    end = time.time() + timeout
    while time.time() < end:
        for row in must(call(dr, "GET", "/api/v1/incentives?size=100"), "incentives")["content"]:
            if row["schoolId"] == school_id:
                return row
        time.sleep(1)
    raise AssertionError("no incentive appeared for School " + school_id)


def no_earner_of(school_id, timeout=60):
    dr = login("9800000002")
    end = time.time() + timeout
    while time.time() < end:
        for row in must(call(dr, "GET", "/api/v1/incentives/no-earner"), "no-earner"):
            if row["schoolId"] == school_id:
                return row
        time.sleep(1)
    raise AssertionError("no no-earner row for School " + school_id)


def get_setting(group, key):
    adm = login("asha.admin")
    return next(s["value"] for s in must(call(adm, "GET", f"/api/v1/settings/{group}"), "settings")["settings"] if s["key"] == key)


def put_setting(group, key, value):
    adm = login("asha.admin")
    return must(call(adm, "PUT", f"/api/v1/settings/{group}", {"values": {key: str(value)}}), "save setting")


if __name__ == "__main__":
    t = login(sys.argv[1])
    s, b = call(t, sys.argv[2], sys.argv[3], json.loads(sys.argv[4]) if len(sys.argv) > 4 else None)
    print(s)
    print(json.dumps(b, indent=1)[:int(sys.argv[5]) if len(sys.argv) > 5 else 3000])
