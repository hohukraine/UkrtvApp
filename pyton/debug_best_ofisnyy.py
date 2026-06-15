import requests
from bs4 import BeautifulSoup
import re

url = "https://uakino.best/filmy/genre_melodrama/34360-ofisnyi-roman.html"
headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "Referer": "https://uakino.best/"}
r = requests.get(url, headers=headers, timeout=15)
html = r.text

# Check for playlists-ajax
soup = BeautifulSoup(html, "html.parser")
playlist_div = soup.select_one(".playlists-ajax")
print(f"playlists-ajax: {playlist_div is not None}")
if playlist_div:
    print(f"  data-news_id: {playlist_div.get('data-news_id')}")
    print(f"  data-xfname: {playlist_div.get('data-xfname')}")

# Check for iframe
iframe = soup.select_one("iframe")
print(f"\niframe: {iframe is not None}")
if iframe:
    print(f"  src: {iframe.get('src', 'no src')}")
    print(f"  data-src: {iframe.get('data-src', 'no data-src')}")

# Check for any video elements
print("\n=== Video-related elements ===")
for tag in soup.find_all(['iframe', 'video', 'embed']):
    print(f"  {tag.name}: src={tag.get('src', 'none')}, data-src={tag.get('data-src', 'none')}")

# Check for go-watch
go = soup.select_one("i.go-watch")
print(f"\ngo-watch: {go.get('data-link') if go else 'none'}")

# Check for m3u8 in page
m3u8s = re.findall(r'https?://[^\s"\'\\]+\.m3u8[^\s"\'\\]*', html)
print(f"\nm3u8 in page: {len(m3u8s)}")
for m in m3u8s:
    print(f"  {m}")

# Check page structure
print(f"\n=== Page title ===")
print(soup.title.string if soup.title else "None")

# Look for player section
player_section = soup.select_one(".players-section")
print(f"\nplayers-section: {player_section is not None}")
if player_section:
    # Check first 500 chars
    print(str(player_section)[:500])
