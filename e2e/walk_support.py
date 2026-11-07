"""Setup and clean-up helpers shared by the e2e walkthroughs of specs 022, 024 and 031.

Everything here goes through the HTTP API and is only used to build the data a walk starts from (a Zone, Schools, a
Manager, Teachers with logins). The steps under test always go through the browser. Names carry a timestamp so a run
never meets the data of another run or another agent, and nothing is deleted.
"""
import json
import os
import time
import urllib.error
import urllib.request
from datetime import date

BASE = os.environ.get("BASE_URL", "http://localhost:5173")
API = os.environ.get("API_URL", "http://localhost:8080")  # direct to the backend: setup calls skip the nginx rate limit
PASSWORD = "Password123!"
_tokens = {}


def stamp():
    """Nine digits that change every run; used in names and phone numbers."""
    return str(int(time.time() * 10) % 1_000_000_000).zfill(9)


def call(user, method, path, body=None, raw=None, headers=None, retry=True):
    """One API call as `user`. Returns (status, parsed json or text). Never raises on an HTTP error."""
    if user not in _tokens:
        req = urllib.request.Request(
            f"{API}/api/v1/auth/login", method="POST",
            data=json.dumps({"identifier": user, "password": PASSWORD}).encode(),
            headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req) as r:
            _tokens[user] = json.loads(r.read())["accessToken"]
    hdr = {"Authorization": f"Bearer {_tokens[user]}", **(headers or {})}
    data = raw
    if body is not None:
        data = json.dumps(body).encode()
        hdr["Content-Type"] = "application/json"
    req = urllib.request.Request(f"{API}{path}", method=method, data=data, headers=hdr)
    try:
        with urllib.request.urlopen(req) as r:
            payload = r.read()
            status = r.status
    except urllib.error.HTTPError as e:
        payload = e.read()
        status = e.code
        if status == 401 and retry:
            _tokens.pop(user, None)
            return call(user, method, path, body, raw, headers, retry=False)
    try:
        return status, json.loads(payload or b"null")
    except ValueError:
        return status, payload.decode(errors="replace")


def ok(result):
    status, body = result
    assert 200 <= status < 300, f"API setup failed: {status} {body}"
    return body


def create_user(admin, name, phone, role):
    return ok(call(admin, "POST", "/api/v1/identity/users", {
        "displayName": name, "phone": phone, "roles": [role], "initialPassword": PASSWORD}))


def build_world(tag, with_manager_b=True, teachers_a=2, teachers_b=1):
    """A private Zone A with Schools A1 and A2 under Manager A (Teachers T1, T2 ...) and, if asked, Zone B with
    School B1 under Manager B (Teacher T3). Every person can sign in with their phone number and PASSWORD."""
    admin = "asha.admin"
    n = int(tag)
    world = {"tag": tag}
    suffix = f"{tag}"

    def phone(i):
        return f"95{(n + i) % 100_000_000:08d}"

    def zone(label, i):
        z = ok(call(admin, "POST", "/api/v1/zones", {"name": f"E2E {label} {suffix}"}))
        p = ok(call(admin, "POST", f"/api/v1/zones/{z['id']}/places", {"name": f"E2E Place {label} {suffix}", "pinCode": "600001"}))
        return z, p

    def school(label, place):
        return ok(call(admin, "POST", "/api/v1/schools", {
            "name": f"E2E {label} {suffix}", "placeId": place["id"], "address": "1 Test Road",
            "contactPerson": "Head", "contactPhone": "9000000009", "billingContact": "school@example.com"}))

    def manager(label, i, z, schools):
        u = create_user(admin, f"E2E Mgr {label} {suffix}", phone(i), "MANAGER")
        m = ok(call(admin, "POST", "/api/v1/managers", {"userId": u["id"]}))
        ok(call(admin, "PUT", f"/api/v1/managers/{m['id']}/zones", {"zoneIds": [z["id"]], "version": m["version"]}))
        for sc in schools:
            ok(call(admin, "PUT", f"/api/v1/schools/{sc['id']}/manager", {"managerId": m["id"]}))
        return {"phone": u["phone"], "name": u["displayName"], "managerId": m["id"], "userId": u["id"]}

    def teacher(label, i, sch):
        u = create_user(admin, f"E2E Teacher {label} {suffix}", phone(i), "TEACHER")
        t = ok(call(admin, "POST", "/api/v1/teachers", {
            "name": f"E2E Teacher {label} {suffix}", "phone": phone(i), "email": f"t{label}{suffix}@example.com",
            "address": "Test Street", "status": "ACTIVE", "userId": u["id"]}))
        ok(call(admin, "POST", f"/api/v1/teachers/{t['id']}/placements", {
            "schoolId": sch["id"], "positionId": None, "effectiveOn": date.today().isoformat()}))
        return {"phone": u["phone"], "name": t["name"], "id": t["id"], "userId": u["id"]}

    za, pa = zone("Zone A", 0)
    world["zoneA"] = za
    world["a1"] = school("School A1", pa)
    world["a2"] = school("School A2", pa)
    world["mgrA"] = manager("A", 1, za, [world["a1"], world["a2"]])
    world["teachersA"] = [teacher(f"T{k + 1}", 10 + k, world["a1"]) for k in range(teachers_a)]
    if with_manager_b:
        zb, pb = zone("Zone B", 2)
        world["zoneB"] = zb
        world["b1"] = school("School B1", pb)
        world["mgrB"] = manager("B", 3, zb, [world["b1"]])
        world["teachersB"] = [teacher(f"T{teachers_a + k + 1}", 20 + k, world["b1"]) for k in range(teachers_b)]
    return world
