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

# Full players-section HTML
print("=== players-section full HTML ===")
ps = soup.select_one(".players-section")
if ps:
    print(ps.prettify()[:3000])
else:
    print("Not found")

# playlists-videos
print("\n=== playlists-videos ===")
pv = soup.select_one(".playlists-videos")
if pv:
    print(pv.prettify()[:3000])

# Any element with data-*
print("\n=== All data-* attributes ===")
for el in soup.find_all(True):
    attrs = {k: v for k, v in el.attrs.items() if k.startswith('data-')}
    if attrs:
        print(f"{el.name}: {attrs}")

# Look for ashdi.vip references
print("\n=== ashdi.vip references ===")
for text in soup.find_all(string=re.compile('ashdi', re.I)):
    print(text.strip()[:200])

# All script contents that might contain URLs
print("\n=== All scripts with URLs ===")
for i, script in enumerate(soup.select("script")):
    txt = script.string or ""
    if "http" in txt and ("ashdi" in txt or "m3u8" in txt or "player" in txt):
        print(f"Script {i}:")
        print(txt[:1000])
        print("---")
