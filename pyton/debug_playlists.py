import requests
import re

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://uakino.best/"
}

# Search for playlists-related JS files
print("=== Searching for playlists JS ===")
base = "https://uakino.best"
paths = [
    "/templates/uakino/playlists/playlists.js",
    "/templates/uakino/playlists/js/playlists.js",
    "/templates/uakino/playlists/playlists.min.js",
    "/templates/uakino/js/playlists.js",
    "/engine/modules/playlists/playlists.js",
    "/engine/modules/playlists.js",
]

for p in paths:
    try:
        r = requests.get(base + p, headers=headers, timeout=10)
        print(f"{p}: {r.status_code}, len={len(r.text)}")
    except Exception as e:
        print(f"{p}: error {e}")

# Try to find any module that handles playlists
print("\n=== Searching HTML for playlist module ===")
url = "https://uakino.best/filmy/genre_drama/62-syayvo-yevropeyske-vidannya.html"
r = requests.get(url, headers=headers, timeout=15)
html = r.text

# Look for include/module patterns
patterns = [
    r'["\']([^"\']*playlist[^"\']*)["\']',
    r'["\']([^"\']*playlists[^"\']*)["\']',
    r'src=["\']([^"\']*\.js[^"\']*)["\']',
]

for pat in patterns:
    matches = re.findall(pat, html, re.I)
    for m in matches[:10]:
        if 'playlist' in m.lower() or 'player' in m.lower():
            print(m)

# Try common AJAX endpoints
print("\n=== Trying more AJAX endpoints ===")
endpoints = [
    f"https://uakino.best/engine/ajax/controller.php?mod=playlists&xfname=playlist&news_id=62",
    f"https://uakino.best/engine/ajax/controller.php?mod=getplaylist&news_id=62",
    f"https://uakino.best/engine/ajax/controller.php?mod=get_playlist&news_id=62",
    f"https://uakino.best/engine/ajax/controller.php?mod=playlist_get&news_id=62",
]

for ep in endpoints:
    try:
        r2 = requests.get(ep, headers=headers, timeout=10)
        ct = r2.headers.get('content-type', '')
        print(f"{ep}: {r2.status_code}, len={len(r2.text)}, type={ct}")
        if r2.status_code == 200 and 'json' in ct:
            print(f"  JSON: {r2.text[:500]}")
    except Exception as e:
        print(f"Error: {e}")
