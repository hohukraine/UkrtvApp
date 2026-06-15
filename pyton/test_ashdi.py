import requests
import re

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://uakino.best/"
}

# Test ashdi.vip URLs
urls = [
    "https://ashdi.vip/vod/263401",
    "https://ashdi.vip/vod/263402",
    "https://ashdi.vip/vod/4089",
]

for url in urls:
    print(f"\n=== Fetching: {url} ===")
    try:
        r = requests.get(url, headers=headers, timeout=15, allow_redirects=True)
        print(f"Status: {r.status_code}, Final URL: {r.url}")
        print(f"Content-Type: {r.headers.get('content-type')}")
        print(f"Length: {len(r.text)}")
        
        # Search for m3u8
        m3u8s = re.findall(r'https?://[^\s"\'\\]+\.m3u8[^\s"\'\\]*', r.text)
        print(f"m3u8 URLs found: {len(m3u8s)}")
        for m in m3u8s[:3]:
            print(f"  {m}")
        
        # Search for other video URLs
        if "m3u8" not in r.text.lower():
            # Look for mp4 or other patterns
            mp4s = re.findall(r'https?://[^\s"\'\\]+\.mp4[^\s"\'\\]*', r.text)
            print(f"mp4 URLs found: {len(mp4s)}")
            for m in mp4s[:3]:
                print(f"  {m}")
            
            # Print first 500 chars
            print(f"\nFirst 500 chars:")
            print(r.text[:500])
    except Exception as e:
        print(f"Error: {e}")
