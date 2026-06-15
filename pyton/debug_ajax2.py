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

# Find external JS files
print("=== External JS files ===")
for script in soup.select("script[src]"):
    src = script.get("src", "")
    print(src)

# Try to find playlists JS
print("\n=== Fetching playlists JS ===")
js_urls = [
    "https://uakino.best/templates/uakino/playlists/playlists.js",
    "https://uakino.best/templates/uakino/playlists/js/playlists.js",
    "https://uakino.best/engine/ajax/controller.php",
]
for js_url in js_urls:
    try:
        r2 = requests.get(js_url, headers=headers, timeout=10)
        print(f"\n{js_url}: status={r2.status_code}, len={len(r2.text)}")
        if r2.status_code == 200 and len(r2.text) < 5000:
            print(r2.text[:1000])
    except Exception as e:
        print(f"Error: {e}")

# Try common AJAX endpoints for playlists
print("\n=== Trying AJAX endpoints ===")
endpoints = [
    "https://uakino.best/engine/ajax/controller.php?mod=playlist&news_id=62",
    "https://uakino.best/engine/ajax/controller.php?mod=playlists&news_id=62",
    "https://uakino.best/engine/ajax/controller.php?mod=playlist&id=62",
    "https://uakino.best/engine/ajax/controller.php?mod=playlists&id=62",
    "https://uakino.best/engine/ajax/playlist.php?news_id=62",
    "https://uakino.best/engine/ajax/playlists.php?news_id=62",
]
for ep in endpoints:
    try:
        r3 = requests.get(ep, headers=headers, timeout=10)
        print(f"{ep}: status={r3.status_code}, len={len(r3.text)}")
        if r3.status_code == 200 and r3.text.strip():
            print(f"  Content: {r3.text[:500]}")
    except Exception as e:
        print(f"Error: {e}")
