import requests
import json

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://uakino.best/",
    "Accept": "application/json, text/javascript, */*; q=0.01",
    "X-Requested-With": "XMLHttpRequest",
}

url = "https://uakino.best/engine/ajax/playlists.php"
params = {
    "news_id": "62",
    "xfield": "playlist",
    "time": "1780908901"
}

print(f"Requesting: {url}")
print(f"Params: {params}")

session = requests.Session()
session.headers.update(headers)

try:
    r = session.get(url, params=params, timeout=15)
    print(f"\nStatus: {r.status_code}")
    print(f"Content-Type: {r.headers.get('content-type')}")
    print(f"Length: {len(r.text)}")
    print(f"\nFirst 1000 chars:")
    print(r.text[:1000])
    
    if r.status_code == 200:
        try:
            data = r.json()
            print(f"\nParsed JSON: {json.dumps(data, indent=2, ensure_ascii=False)[:2000]}")
        except json.JSONDecodeError as e:
            print(f"\nJSON parse error: {e}")
except Exception as e:
    print(f"Request error: {e}")
