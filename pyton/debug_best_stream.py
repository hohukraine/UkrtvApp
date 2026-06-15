import requests
from bs4 import BeautifulSoup
import re

url = "https://uakino.best/filmy/genre_melodrama/34360-ofisnyi-roman.html"
headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "Referer": "https://uakino.best/"}
r = requests.get(url, headers=headers, timeout=15)
html = r.text
soup = BeautifulSoup(html, "html.parser")

# Test the exact logic from UakinoBestProvider.get_stream()
iframe = soup.select_one("iframe[src*='ashdi.vip']")
print(f"iframe found: {iframe is not None}")
if iframe:
    iframe_src = iframe.get("src", "")
    print(f"  src: {iframe_src}")
    if iframe_src.startswith("//"):
        iframe_src = "https:" + iframe_src
    print(f"  resolved: {iframe_src}")
    
    r2 = requests.get(iframe_src, headers={"Referer": url}, timeout=15)
    print(f"  iframe status: {r2.status_code}")
    m3u8s = re.findall(r'https?://[^\s"\'\\]+\.m3u8[^\s"\'\\]*', r2.text)
    print(f"  m3u8 found: {len(m3u8s)}")
    for m in m3u8s:
        print(f"    {m}")
