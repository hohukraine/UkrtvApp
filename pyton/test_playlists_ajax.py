import requests
import json

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://uakino.best/"
}

# Test the playlists AJAX endpoint
url = "https://uakino.best/engine/ajax/playlists.php"
params = {
    "news_id": "62",
    "xfield": "playlist",
    "time": "1780908901"
}
r = requests.get(url, headers=headers, params=params, timeout=15)
print(f"Status: {r.status_code}")
print(f"Content-Type: {r.headers.get('content-type')}")
print(f"Length: {len(r.text)}")

if r.status_code == 200:
    try:
        data = r.json()
        print(f"\nJSON keys: {data.keys()}")
        print(f"success: {data.get('success')}")
        if data.get('success'):
            html = data.get('response', '')
            print(f"\nResponse HTML length: {len(html)}")
            print(f"\nFirst 2000 chars of response:")
            print(html[:2000])
    except Exception as e:
        print(f"JSON parse error: {e}")
        print(f"Raw text (first 500): {r.text[:500]}")
