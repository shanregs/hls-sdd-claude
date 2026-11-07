"""Setup helpers for the browser walkthroughs: create uniquely named data through the REST API.

The steps under test always go through the browser; this module only prepares fixtures that the demo seed does not hold
(a second Manager, a Zone of our own, Teachers placed at our Schools) so a run never touches or counts other people's data.
Nothing here deletes anything. BASE_URL points at the frontend dev server, which proxies /api to the backend.
"""
import json
import os
import time
import urllib.error
import urllib.request

BASE = os.environ.get("API_URL", "http://localhost:8080")  # direct to the backend: setup calls skip the nginx rate limit
PASSWORD = "Password123!"
_tokens = {}


def call(method, path, body=None, token=None, raw=False):
    """Returns (status, json). `path` starts with /api/v1 unless it already does not need it."""
    url = BASE + (path if path.startswith("/api/") else "/api/v1" + path)
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, method=method, data=data, headers={
        "Content-Type": "application/json", **({"Authorization": "Bearer " + token} if token else {})})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            payload = r.read()
            return r.status, (payload if raw else json.loads(payload or b"null"))
    except urllib.error.HTTPError as e:
        text = e.read().decode(errors="replace")
        try:
            return e.code, json.loads(text)
        except ValueError:
            return e.code, text


def token_for(identifier, password=PASSWORD):
    if identifier not in _tokens:
        for _ in range(12):                      # the shared nginx allows 30 sign-ins a minute for everybody
            status, body = call("POST", "/auth/login", {"identifier": identifier, "password": password})
            if status != 429:
                break
            time.sleep(10)
        assert status == 200, f"login {identifier}: {status} {body}"
        _tokens[identifier] = body["accessToken"]
    return _tokens[identifier]


def ok(result, *expected):
    status, body = result
    assert status in (expected or (200, 201, 204)), f"{status}: {body}"
    return body


def stamp():
    """A short unique suffix for names and phone numbers."""
    return str(int(time.time() * 10))[-8:]


def iso_in(days):
    import datetime
    return (datetime.date.today() + datetime.timedelta(days=days)).isoformat()


def make_zone(admin, name):
    zone = ok(call("POST", "/zones", {"name": name}, admin))
    place = ok(call("POST", f"/zones/{zone['id']}/places", {"name": name + " Place", "pinCode": "600001", "zoneId": zone["id"]}, admin))
    return zone, place


def make_school(admin, name, place):
    return ok(call("POST", "/schools", {"name": name, "placeId": place["id"], "address": "1 Test Road", "contactPerson": "Head",
                                        "contactPhone": "9000000009", "billingContact": "school@example.com"}, admin))


def make_manager(admin, label, zone_ids=(), school_ids=()):
    """A new Manager user who can sign in with username `label` (lower case) and the demo password."""
    suffix = stamp()
    phone = "97" + suffix.zfill(8)[-8:]
    username = f"{label}.{suffix}".lower().replace(" ", ".")
    user = ok(call("POST", "/identity/users", {"displayName": f"{label} {suffix}", "phone": phone, "roles": ["MANAGER"],
                                                "username": username, "email": f"{username}@example.com",
                                                "initialPassword": PASSWORD}, admin))
    manager = ok(call("POST", "/managers", {"userId": user["id"]}, admin))
    if zone_ids:
        manager = ok(call("PUT", f"/managers/{manager['id']}/zones", {"zoneIds": list(zone_ids), "version": manager["version"]}, admin))
    for school_id in school_ids:
        ok(call("PUT", f"/schools/{school_id}/manager", {"managerId": manager["id"]}, admin))
    return {"userId": user["id"], "managerId": manager["id"], "username": username, "phone": phone, "name": user["displayName"]}


def make_teacher(admin, name, school_id=None, status="ACTIVE"):
    suffix = stamp()
    teacher = ok(call("POST", "/teachers", {"name": name, "phone": "96" + suffix.zfill(8)[-8:], "email": f"t{suffix}@example.com",
                                            "address": "Test Street", "status": status}, admin))
    if school_id:
        ok(call("POST", f"/teachers/{teacher['id']}/placements", {"schoolId": school_id, "positionId": None,
                                                                   "effectiveOn": iso_in(-1)}, admin))
    return teacher
