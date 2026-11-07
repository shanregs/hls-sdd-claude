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
        if key in drivers and "/sign-in" in drivers[key].current_url:      # the session was dropped (a refresh raced): sign in again
            signed[key] = 0
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
                    d.hls_user = user
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


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item, call):
    """On a failure, print the URL and the visible text of every open browser so the cause is in the report."""
    outcome = yield
    rep = outcome.get_result()
    if rep.when == "call" and rep.failed and "browsers" in item.funcargs:
        try:
            for (user, _), drv in item.funcargs["browsers"].__closure__[0].cell_contents.items():
                try:
                    t = drv.find_element("tag name", "body").text
                    rep.sections.append((f"browser {user}", drv.current_url + chr(10) + t[:300] + chr(10) + "..." + chr(10) + t[-1500:]))
                except Exception:
                    pass
        except Exception:
            pass
