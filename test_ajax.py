import requests
from bs4 import BeautifulSoup
import time

def test_playlist_endpoints(base_url, news_id, page_url):
    print(f"\n--- Testing Playlist Endpoints for {base_url} (news_id={news_id}, url={page_url}) ---")
    endpoints = [
        f"{base_url}engine/ajax/playlists.php",
        f"{base_url}engine/ajax/controller.php?mod=playlists",
        f"{base_url}index.php?do=ajax&mod=playlists"
    ]
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "X-Requested-With": "XMLHttpRequest",
        "Referer": page_url
    }
    xfields = ["playlist", "seria", "video"]

    for url in endpoints:
        for xf in xfields:
            data = {
                "news_id": news_id,
                "xfield": xf,
                "time": str(int(time.time()))
            }
            try:
                print(f"Trying {url} with xfield='{xf}'...")
                resp = requests.post(url, headers=headers, data=data, timeout=10)
                if resp.status_code == 200:
                    text = resp.text
                    if "success" in text:
                        try:
                            js = resp.json()
                            if js.get("success"):
                                print(f"  SUCCESS! Found JSON playlist at {url} ({xf})")
                                return
                            else:
                                print(f"  Result: success=false ({js.get('response') or js.get('message')})")
                        except:
                            print(f"  Result: 200 but invalid JSON")
                    elif "playlists-lists" in text or "data-file" in text:
                        print(f"  SUCCESS (HTML)! Found playlist at {url} ({xf})")
                        return
                    else:
                        print(f"  Result: 200 but no playlist markers. Text snippet: {text[:100]}")
                else:
                    print(f"  Status: {resp.status_code}")
            except Exception as e:
                print(f"  Error: {e}")

if __name__ == "__main__":
    # Eneyida Serial: House of the Dragon
    test_playlist_endpoints("https://eneyida.tv/", "9330", "https://eneyida.tv/9330-dim-drakona-2-sezon.html")
