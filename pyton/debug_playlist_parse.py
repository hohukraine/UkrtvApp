import requests
import json
import re

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://uakino.best/"
}

session = requests.Session()
session.headers.update(headers)

# Get playlist AJAX response
url = "https://uakino.best/engine/ajax/playlists.php"
params = {"news_id": "62", "xfield": "playlist", "time": "1780908901"}
r = session.get(url, params=params, timeout=15)
data = r.json()
html = data["response"]

# Save to file for inspection
with open("C:\\UkrtvApp\\playlist_response_debug.html", "w", encoding="utf-8") as f:
    f.write(html)
print("Saved to playlist_response_debug.html")

# Check the raw HTML
print(f"\nHTML length: {len(html)}")
print(f"\nFirst 1000 chars:")
print(html[:1000])

# Check if data-file exists
matches = re.findall(r'data-file=["\'](.*?)["\']', html)
print(f"\ndata-file URLs found with regex: {len(matches)}")
for m in matches:
    print(f"  {m}")

# Try BeautifulSoup
from bs4 import BeautifulSoup
soup = BeautifulSoup(html, "html.parser")
lis = soup.select("li[data-file]")
print(f"\nli[data-file] elements found: {len(lis)}")
for li in lis:
    print(f"  data-file: {li.get('data-file')}")
    print(f"  text: {li.get_text(strip=True)[:50]}")
