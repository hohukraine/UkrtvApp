import requests
import re

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://uakino.cx/"
}

# Fetch the player iframe page
url = "https://api.ortified.ws/embed/movie/90864"
r = requests.get(url, headers=headers, timeout=15)
print(f"Status: {r.status_code}")
print(f"Content-Type: {r.headers.get('content-type')}")
print(f"Length: {len(r.text)}")
print(f"\nFirst 1000 chars:")
print(r.text[:1000])

# Search for m3u8
m3u8s = re.findall(r'https?://[^\s"\'\\]+\.m3u8[^\s"\'\\]*', r.text)
print(f"\nm3u8 found: {len(m3u8s)}")
for m in m3u8s:
    print(f"  {m}")
