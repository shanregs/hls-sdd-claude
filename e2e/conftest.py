"""One signed-in Chrome per user for the whole run.

The stack's nginx allows only 30 sign-ins a minute for everybody sharing it, so the walkthroughs of specs 015, 027, 028 and
035 reuse a browser per user instead of signing in again in every test. The older walkthroughs do not use this fixture.
"""
import time

import pytest
from selenium.common.exceptions import TimeoutException

from test_school_working_days import make_driver, sign_in


@pytest.fixture(scope="session")
def browsers():
    drivers = {}
    signed = {}

    def get(user, width=1280, height=900):
        key = (user, width)
        if key in drivers:
            try:
                drivers[key].title                  # a Chrome that died is signed in again
            except Exception:
                drivers.pop(key)
        if key in drivers and time.time() - signed[key] > 600:     # the access token lives 15 minutes
            drivers[key].delete_all_cookies()
            drivers[key].execute_script("localStorage.clear(); sessionStorage.clear();")
            sign_in(drivers[key], user)
            signed[key] = time.time()
        if key not in drivers:
            for attempt in range(5):
                d = make_driver(width, height)
                try:
                    sign_in(d, user)
                    drivers[key] = d
                    signed[key] = time.time()
                    break
                except TimeoutException:          # most likely 429 from the shared sign-in limit: wait and retry
                    d.quit()
                    time.sleep(30)
            else:
                raise AssertionError(f"could not sign in as {user}")
        return drivers[key]

    yield get
    for d in drivers.values():
        d.quit()
