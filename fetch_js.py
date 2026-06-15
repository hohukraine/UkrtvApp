import requests

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://uakino.best/"
}

# Fetch full player.js
print("=== Fetching player.js ===")
r = requests.get("https://uakino.best/templates/uakino/playlists/player.js?v=5141231", headers=headers, timeout=15)
print(f"Status: {r.status_code}, len: {len(r.text)}")
with open("C:\\UkrtvApp\\player.js", "w", encoding="utf-8") as f:
    f.write(r.text)
print("Saved to player.js")

# Also fetch libs.js which might contain the playlist init
print("\n=== Fetching libs.js ===")
r2 = requests.get("https://uakino.best/templates/uakino/js/libs.js?v=54134111", headers=headers, timeout=15)
print(f"Status: {r2.status_code}, len: {len(r2.text)}")
with open("C:\\UkrtvApp\\libs.js", "w", encoding="utf-8") as f:
    f.write(r2.text)
print("Saved to libs.js")
