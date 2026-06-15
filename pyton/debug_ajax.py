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
soup = BeautifulSoup(r.text, "html.parser")

# Look for AJAX/controller URLs in scripts
print("=== Searching for AJAX URLs ===")
for i, script in enumerate(soup.select("script")):
    txt = script.string or ""
    if "ajax" in txt.lower() or "controller" in txt.lower() or "playlist" in txt.lower():
        print(f"Script {i}:")
        # Find URLs
        urls = re.findall(r'["\']([^"\']*(?:ajax|controller|playlist)[^"\']*)["\']', txt, re.I)
        for u in urls[:10]:
            print(f"  URL: {u}")
        print("---")

# Try to find the playlists-ajax init script
print("\n=== playlists-ajax related scripts ===")
for i, script in enumerate(soup.select("script")):
    txt = script.string or ""
    if "playlists-ajax" in txt or "playlists_ajax" in txt or "playlist" in txt.lower():
        print(f"Script {i}:")
        print(txt[:2000])
        print("---")

# Check for any inline scripts with URLs
print("\n=== All inline scripts ===")
for i, script in enumerate(soup.select("script")):
    if script.get("src"):
        continue
    txt = (script.string or "").strip()
    if txt and len(txt) > 20:
        print(f"Script {i} ({len(txt)} chars): {txt[:200]}")
