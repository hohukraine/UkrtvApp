import requests
import json

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Accept": "application/json, text/javascript, */*; q=0.01",
    "X-Requested-With": "XMLHttpRequest",
    "Referer": "https://uakino.best/"
}

session = requests.Session()
session.headers.update(headers)

# Test playlist for series
news_id = "5642"
xfname = "playlist"
edittime = "1780908901"

url = "https://uakino.best/engine/ajax/playlists.php"
params = {"news_id": news_id, "xfield": xfname, "time": edittime}

r = session.get(url, params=params, timeout=15)
print(f"Status: {r.status_code}")

if r.status_code == 200:
    try:
        data = r.json()
        print(f"success: {data.get('success')}")
        if data.get('success'):
            html = data['response']
            # Unescape
            html = html.replace('\\"', '"').replace('\\n', '\n').replace('\\t', '\t').replace('\\/', '/')
            print(f"\nResponse length: {len(html)}")
            print(f"\nFirst 2000 chars:")
            print(html[:2000])
            
            # Save to file
            with open("C:\\UkrtvApp\\series_playlist.html", "w", encoding="utf-8") as f:
                f.write(html)
            print("\nSaved to series_playlist.html")
    except Exception as e:
        print(f"Error: {e}")
        print(f"Response: {r.text[:200]}")
