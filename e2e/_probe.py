import time
import api_helpers as api
from browsers import *
from test_school_working_days import BASE
b = Browsers()
d = b.as_user("asha.admin")
row = api.must(api.call(api.login("asha.admin"), "GET", "/api/v1/incentives?size=100"))["content"][0]
d.get(f"{BASE}/operations/incentives/{row['id']}")
time.sleep(6)
for e in d.find_elements("css selector", ".MuiDrawer-modal .MuiDrawer-paper"):
    print("PAPER", e.is_displayed(), repr(e.text[:800]))
b.quit_all()
