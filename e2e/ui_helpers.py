"""Small Selenium helpers shared by the walkthroughs of specs 015, 027, 028 and 035 (MUI forms, selects, dialogs)."""
import time

from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

DIALOG = (By.CSS_SELECTOR, "[role=dialog]")


def wait(d, timeout=25):
    return WebDriverWait(d, timeout)


def body(d):
    return d.find_element(By.TAG_NAME, "body").text


def set_value(d, el, value):
    """Set a React-controlled input or textarea (also date and month inputs) and fire the event React listens to."""
    d.execute_script(
        "const e=arguments[0];const proto=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;"
        "Object.getOwnPropertyDescriptor(proto,'value').set.call(e,arguments[1]);"
        "e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));", el, value)


def field(scope, label):
    """The input or textarea of the MUI TextField whose label starts with `label` (a required mark is ignored)."""
    lab = scope.find_element(By.XPATH, f".//label[starts-with(normalize-space(.),'{label}')]")
    return scope.find_element(By.XPATH, f".//*[@id='{lab.get_attribute('for')}']")


def fill(d, scope, label, value):
    set_value(d, field(scope, label), value)


def pick(d, scope, label, option):
    """Choose `option` in the MUI select labelled `label` inside `scope` (a `div[role=combobox]`)."""
    box = scope.find_element(By.XPATH, f".//*[contains(@id,'-label')][starts-with(normalize-space(.),'{label}')]/..//div[@role='combobox']")
    d.execute_script("arguments[0].scrollIntoView({block:'center'})", box)
    box.click()
    item = wait(d).until(EC.element_to_be_clickable(
        (By.XPATH, f"//li[@role='option'][starts-with(normalize-space(.),'{option}')]")))
    d.execute_script("arguments[0].scrollIntoView({block:'nearest'}); arguments[0].click();", item)   # a long list: the item may sit under the page edge
    wait(d).until(EC.invisibility_of_element_located((By.XPATH, "//ul[@role='listbox']")))


def options_of(d, scope, label):
    """Open the select and return its option texts, then close it again."""
    scope.find_element(By.XPATH, f".//*[contains(@id,'-label')][starts-with(normalize-space(.),'{label}')]/..//div[@role='combobox']").click()
    items = wait(d).until(EC.presence_of_all_elements_located((By.XPATH, "//li[@role='option']")))
    names = [i.get_attribute("textContent").strip() for i in items]
    from selenium.webdriver.common.keys import Keys
    d.switch_to.active_element.send_keys(Keys.ESCAPE)
    wait(d).until(EC.invisibility_of_element_located((By.XPATH, "//ul[@role='listbox']")))
    return names


def click(d, xpath, timeout=25):
    el = wait(d, timeout).until(EC.element_to_be_clickable((By.XPATH, xpath)))
    d.execute_script("arguments[0].scrollIntoView({block:'center'})", el)
    el.click()
    return el


def button(d, label, scope=None):
    root = scope or d
    return wait(d).until(lambda x: next((b for b in root.find_elements(By.XPATH, ".//button")
                                         if b.text.strip().lower() == label.lower() and b.is_displayed() and b.is_enabled()), None))


def has_button(scope, label):
    return any(b.text.strip().lower() == label.lower() and b.is_displayed() for b in scope.find_elements(By.XPATH, ".//button"))


def dialog(d):
    return wait(d).until(EC.visibility_of_element_located(DIALOG))


def see(d, text, timeout=25):
    return wait(d, timeout).until(EC.visibility_of_element_located((By.XPATH, f"//*[contains(normalize-space(.),\"{text}\")]")))


def page_text_has(d, text, timeout=25):
    wait(d, timeout).until(lambda x: text in body(x))
    return True


def no_overflow(d):
    return d.execute_script("return document.documentElement.scrollWidth - document.documentElement.clientWidth")


def settle(d, seconds=0.6):
    time.sleep(seconds)
