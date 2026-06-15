import requests
import json

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://uakino.best/"
}
session = requests.Session()
session.headers.update(headers)

url = "https://uakino.best/engine/ajax/playlists.php"
params = {"news_id": "62", "xfield": "playlist", "time": "1780908901"}
r = session.get(url, params=params, timeout=15)
print(f"Status: {r.status_code}, encoding: {r.encoding}")
print(f"First 200 bytes: {r.content[:200]}")
print(f"First 200 chars: {r.text[:200]}")

try:
    data = r.json()
    print(f"JSON: {json.dumps(data, indent=2, ensure_ascii=False)[:500]}")
except json.JSONDecodeError as e:
    print(f"JSON error: {e}")
    # Try to strip BOM
    text = r.text
    if text.startswith('\ufeff'):
        text = text[1:]
    try:
        data = json.loads(text)
        print(f"JSON (stripped BOM): {json.dumps(data, indent=2, ensure_ascii=False)[:500]}")
    except json.JSONDecodeError as e2:
        print(f"Still error: {e2}")
