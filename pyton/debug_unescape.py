import requests
import json
import re
from urllib.parse import unquote

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://uakino.best/"
}

session = requests.Session()
session.headers.update(headers)

url = "https://uakino.best/engine/ajax/playlists.php"
params = {"news_id": "62", "xfield": "playlist", "time": "1780908901"}
r = session.get(url, params=params, timeout=15)
data = r.json()
html = data["response"]

# The HTML contains escaped slashes and unicode escapes, so unescape it first
html_unescaped = html.encode('utf-8').decode('unicode_escape')
html_unescaped = html_unescaped.replace('\\/', '/')

# Now extract data-file URLs
matches = re.findall(r'data-file="(https?://[^"]+)"', html_unescaped)
print(f"Found {len(matches)} player URLs:")
for m in matches:
    print(f"  {m}")

# Save for inspection
with open("C:\\UkrtvApp\\playlist_unescaped.html", "w", encoding="utf-8") as f:
    f.write(html_unescaped)
print("\nSaved unescaped HTML to playlist_unescaped.html")
