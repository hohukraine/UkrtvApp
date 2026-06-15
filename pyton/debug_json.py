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

# Search for any JSON data embedded in the page
print("=== Searching for JSON data ===")
# Look for common patterns
patterns = [
    r'var\s+\w+\s*=\s*(\{[^;]+\})',
    r'window\.\w+\s*=\s*(\{[^;]+\})',
    r'data:\s*(\{[^;]+\})',
    r'playlist.*?\[.*?\]',
    r'playlists.*?\[.*?\]',
]

for pat in patterns:
    matches = re.findall(pat, html, re.DOTALL | re.IGNORECASE)
    for m in matches[:5]:
        if len(m) < 5000 and ('playlist' in m.lower() or 'player' in m.lower() or 'file' in m.lower()):
            print(f"Match: {m[:500]}")
            print("---")

# Search for any iframe src patterns that might be hidden
print("\n=== Searching for iframe-like URLs ===")
urls = re.findall(r'https?://[^\s"\'\\>]+\.(?:html|php|asp|aspx)[^\s"\'\\<]*', html)
for u in urls[:20]:
    if 'player' in u.lower() or 'embed' in u.lower() or 'video' in u.lower() or 'ashdi' in u.lower():
        print(u)

# Look for the #pre div and any related scripts
print("\n=== #pre div context ===")
soup = BeautifulSoup(html, "html.parser")
pre_div = soup.select_one("#pre")
if pre_div:
    print(f"Parent: {pre_div.parent.name}, class: {pre_div.parent.get('class')}")
    print(f"pre div attrs: {pre_div.attrs}")
    # Look for scripts that reference #pre
    for script in soup.select("script"):
        txt = script.string or ""
        if "#pre" in txt or "pre" in txt.lower():
            print(f"Script referencing pre: {txt[:300]}")

# Try the AJAX endpoint with POST
print("\n=== Trying POST to playlists endpoint ===")
try:
    r2 = requests.post(
        "https://uakino.best/engine/ajax/controller.php?mod=playlists&xfname=playlist&news_id=62",
        headers=headers,
        data={"xfname": "playlist", "news_id": "62"},
        timeout=10
    )
    print(f"POST status: {r2.status_code}, len: {len(r2.text)}")
    if r2.status_code == 200:
        print(f"Content: {r2.text[:500]}")
except Exception as e:
    print(f"Error: {e}")

# Try with skin parameter
print("\n=== Trying with skin parameter ===")
try:
    r3 = requests.get(
        "https://uakino.best/engine/ajax/controller.php?mod=playlists&xfname=playlist&news_id=62&skin=uakino",
        headers=headers,
        timeout=10
    )
    print(f"Status: {r3.status_code}, len: {len(r3.text)}")
    if r3.status_code == 200:
        print(f"Content: {r3.text[:500]}")
except Exception as e:
    print(f"Error: {e}")
