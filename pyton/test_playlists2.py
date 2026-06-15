import requests
import json

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://uakino.best/"
}

# Test for the movie from the user's search result
url = "https://uakino.best/engine/ajax/playlists.php"
params = {
    "news_id": "62",
    "xfield": "playlist",
    "time": "1780908901"
}
r = requests.get(url, headers=headers, params=params, timeout=15)
print(f"Status: {r.status_code}")
print(f"Content-Type: {r.headers.get('content-type')}")

if r.status_code == 200:
    try:
        data = r.json()
        print(f"\nJSON keys: {list(data.keys())}")
        print(f"success: {data.get('success')}")
        if data.get('success'):
            html = data.get('response', '')
            print(f"\nResponse HTML length: {len(html)}")
            # Search for m3u8 URLs in the response
            import re
            m3u8_urls = re.findall(r'https?://[^\s"\'\\]+\.m3u8[^\s"\'\\]*', html)
            print(f"\nm3u8 URLs found: {len(m3u8_urls)}")
            for u in m3u8_urls:
                print(f"  {u}")
            # Also look for iframe src
            iframe_srcs = re.findall(r'src=["\']([^"\']+)["\']', html)
            print(f"\niframe/src URLs found: {len(iframe_srcs)}")
            for u in iframe_srcs[:20]:
                print(f"  {u}")
            # Save full response for inspection
            with open("C:\\UkrtvApp\\playlist_response.html", "w", encoding="utf-8") as f:
                f.write(html)
            print("\nSaved full response to playlist_response.html")
    except Exception as e:
        print(f"Error: {e}")
        print(f"Raw text (first 500): {r.text[:500]}")
