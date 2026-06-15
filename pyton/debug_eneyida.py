import requests
import re

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://eneyida.tv/"
}

# Fetch the player iframe
url = "https://hdvbua.pro/embed/1010808/b0c42c552"
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

# Search for other video URLs
print("\n=== All URLs ===")
urls = re.findall(r'https?://[^\s"\'\\>]+', r.text)
for u in urls[:30]:
    if 'video' in u.lower() or 'stream' in u.lower() or 'play' in u.lower() or 'cdn' in u.lower():
        print(f"  {u}")
