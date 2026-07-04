import requests
import re
from bs4 import BeautifulSoup

url = "https://uakino.best/seriesss/fantastic_series/33511-zzovni-4-sezon.html"
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://uakino.best/'
}

s = requests.Session()
resp = s.get(url, headers=headers)
html = resp.text

news_id = re.search(r'news_id\s*[:=]\s*["\']?(\d+)', html)
user_hash = re.search(r'dle_login_hash\s*=\s*[\'"]([^\'"]+)[\'"]', html)

print(f"News ID: {news_id.group(1) if news_id else 'NOT FOUND'}")
print(f"Hash: {user_hash.group(1) if user_hash else 'NOT FOUND'}")

if news_id and user_hash:
    nid = news_id.group(1)
    uhash = user_hash.group(1)

    endpoint = "https://uakino.best/engine/ajax/playlists.php"
    # Uakino sometimes uses 'id' instead of 'news_id' or both
    # And sometimes it's 'action': 'playlists' or 'get_playlist'

    for action in ['playlists', 'get_playlist']:
        for field in ['playlist', 'seria', 'video', 'playlist_ua']:
            data = {
                'news_id': nid,
                'id': nid,
                'area': 'news',
                'action': action,
                'xfield': field,
                'user_hash': uhash
            }
            r = s.post(endpoint, data=data, headers={'Referer': url, 'X-Requested-With': 'XMLHttpRequest'})
            print(f"Action: {action}, Field: {field}, Status: {r.status_code}, Length: {len(r.text)}")
            if len(r.text) > 10:
                print(f"Preview: {r.text[:200]}")
