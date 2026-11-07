"""Signed-in browsers shared by the walkthroughs of specs 030, 014b, 040 and 029.

One Chrome per person, signed in once and reused for the whole file (the sign-in is rate limited by nginx, so signing in again
for every test is slow and can be refused). Each driver also saves downloads into one folder so export tests can read them.
"""
import glob
import os
import tempfile
import time

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

from test_school_working_days import BASE, sign_in


def new_driver(download_dir, width=1280, height=900):
    opts = webdriver.ChromeOptions()
    if not os.environ.get("HEADED"):
        opts.add_argument("--headless=new")
    opts.add_argument(f"--window-size={width},{height}")
    opts.add_experimental_option("prefs", {"download.default_directory": download_dir, "download.prompt_for_download": False})
    d = webdriver.Chrome(options=opts)
    d.set_window_size(width, height)
    return d


class Browsers:
    def __init__(self):
        self.folder = tempfile.mkdtemp(prefix="hls-walk-")
        self._drivers = {}

    def as_user(self, user):
        """The browser signed in as `user` (opened and signed in on first use). If the session was lost, sign in again."""
        d = self._drivers.get(user)
        if d is None:
            d = new_driver(self.folder)
            d.hls_user = user
            self._drivers[user] = d
            sign_in(d, user)
        return d

    def fresh(self, user, width=1280, height=900):
        """A separate, throw-away browser (for another window size). The caller must quit it."""
        d = new_driver(self.folder, width, height)
        d.hls_user = user
        sign_in(d, user)
        return d

    def downloads(self, pattern="*"):
        return glob.glob(os.path.join(self.folder, pattern))

    def wait_download(self, pattern, since, timeout=30):
        end = time.time() + timeout
        while time.time() < end:
            found = [f for f in self.downloads(pattern) if os.path.getmtime(f) >= since and not f.endswith(".crdownload")]
            if found:
                time.sleep(0.5)
                return found[0]
            time.sleep(0.3)
        raise AssertionError("no download matching " + pattern)

    def quit_all(self):
        for d in self._drivers.values():
            try:
                d.quit()
            except Exception:
                pass
        self._drivers.clear()


def wait(d, timeout=25):
    return WebDriverWait(d, timeout)


def body(d):
    return d.find_element(By.TAG_NAME, "body").text


def load(d, path):
    """d.get that signs in again if the page reload lost the session (the renewal cookie can be refused under load)."""
    d.get(BASE + path)
    if "/sign-in" in d.current_url and getattr(d, "hls_user", None):
        sign_in(d, d.hls_user)
        d.get(BASE + path)


def goto(d, path, heading=None):
    """Open a route and wait until the page settles (a heading, and no loading spinner)."""
    load(d, path)
    wait(d, 30).until(EC.visibility_of_element_located((By.TAG_NAME, "h1") if heading is None else (By.XPATH, f"//h1[contains(normalize-space(),'{heading}')]")))
    wait(d, 30).until(lambda x: not x.find_elements(By.CSS_SELECTOR, "[aria-label^='Loading'], .MuiCircularProgress-root"))
    time.sleep(0.4)


def click(d, xpath, timeout=20):
    el = wait(d, timeout).until(EC.element_to_be_clickable((By.XPATH, xpath)))
    d.execute_script("arguments[0].scrollIntoView({block:'center'})", el)
    el.click()
    return el


def menu_labels(d):
    return d.execute_script("return Array.from(document.querySelectorAll('nav button, nav a, nav [role=button]')).map(b => b.textContent.trim())")


def overflow_x(d):
    return d.execute_script("return document.documentElement.scrollWidth - document.documentElement.clientWidth")


def fill(d, input_el, text):
    """Replace the text of an input in a way React notices."""
    from selenium.webdriver.common.keys import Keys
    input_el.click()
    input_el.send_keys(Keys.CONTROL, "a")
    input_el.send_keys(Keys.DELETE)
    input_el.send_keys(text)
