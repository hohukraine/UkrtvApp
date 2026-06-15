import requests

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://uakino.best/"
}

# Fetch player.js
print("=== player.js ===")
r = requests.get("https://uakino.best/templates/uakino/playlists/player.js?v=5141231", headers=headers, timeout=15)
print(f"Status: {r.status_code}, len: {len(r.text)}")
print(r.text[:3000])

# Try AJAX with xfield name
print("\n=== Trying AJAX with xfname ===")
endpoints = [
    "https://uakino.best/engine/ajax/controller.php?mod=playlists&xfname=playlist&news_id=62",
    "https://uakino.best/engine/ajax/controller.php?mod=playlists&xfname=playlist&id=62",
    "https://uakino.best/engine/ajax/controller.php?mod=playlist&xfname=playlist&news_id=62",
]
for ep in endpoints:
    r2 = requests.get(ep, headers=headers, timeout=10)
    print(f"{ep}: status={r2.status_code}, len={len(r2.text)}")
    if r2.status_code == 200 and r2.text.strip() and r2.text.strip() != 'error':
        print(f"  Content: {r2.text[:1000]}")
