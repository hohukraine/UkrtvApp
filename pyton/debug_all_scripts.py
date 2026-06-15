import requests
from bs4 import BeautifulSoup
import re
import json

url = "https://uakino.best/filmy/genre_drama/62-syayvo-yevropeyske-vidannya.html"
headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://uakino.best/"
}
r = requests.get(url, headers=headers, timeout=15)
html = r.text
soup = BeautifulSoup(html, "html.parser")

# Look for ALL script tags, even those without src
print("=== All script content ===")
for i, script in enumerate(soup.find_all("script")):
    if script.get("src"):
        continue
    txt = (script.string or script.get_text() or "").strip()
    if not txt:
        continue
    if len(txt) > 100:
        print(f"\n--- Script {i} ({len(txt)} chars) ---")
        print(txt)
        print("--- END ---")
