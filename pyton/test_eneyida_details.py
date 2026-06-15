import requests
from bs4 import BeautifulSoup
import re

url = "https://eneyida.tv/10037-mortal-kombat-2.html"
headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Referer": "https://eneyida.tv/"
}

r = requests.get(url, headers=headers, timeout=15)
html = r.text
soup = BeautifulSoup(html, 'html.parser')

# 1. Check for .playlists-ajax
ajax_div = soup.select_one(".playlists-ajax")
if ajax_div:
    print(f"Found AJAX playlist div: news_id={ajax_div.get('data-news_id')}, xfname={ajax_div.get('data-xfname')}")
else:
    print("AJAX playlist div NOT found")

# 2. Check for .playlists-lists li
season_tabs = soup.select(".playlists-lists li")
print(f"Season tabs found: {len(season_tabs)}")

# 3. Check for iframes
iframes = soup.select("iframe")
print(f"Iframes found: {len(iframes)}")
for i, iframe in enumerate(iframes):
    src = iframe.get('data-src') or iframe.get('src')
    print(f"  Iframe {i+1} src: {src}")

# 4. Check for direct m3u8
m3u8_links = re.findall(r'https?://[^\s\'"]+\.m3u8[^\s\'"]*', html)
print(f"Direct m3u8 links found: {len(m3u8_links)}")
for link in m3u8_links[:3]:
    print(f"  {link}")
