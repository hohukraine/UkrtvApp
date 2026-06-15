import requests
from bs4 import BeautifulSoup
import time
import json

def test_eneyida_playlists(news_id, referer):
    print(f"\n{'='*20} TESTING Eneyida Endpoints (news_id={news_id}) {'='*20}")

    base_url = "https://eneyida.tv/"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "X-Requested-With": "XMLHttpRequest",
        "Referer": referer
    }

    endpoints = [
        "index.php?do=ajax&mod=re_playlist",
        "index.php?do=ajax&mod=get_playlist",
        "index.php?do=ajax&mod=playlists"
    ]
    xfields = ["seria", "playlist"]

    for mod_url in endpoints:
        url = f"{base_url}{mod_url}"
        for xf in xfields:
            data = {
                "news_id": news_id,
                "xfield": xf,
                "time": str(int(time.time()))
            }
            try:
                print(f"\nTrying POST {url} with xfield={xf}...")
                resp = requests.post(url, headers=headers, data=data, timeout=10)
                print(f"Status: {resp.status_code}")

                text = resp.text
                if resp.status_code == 200:
                    # Check if it's a redirect to home or some generic page
                    if "<title>Дивитися фільми" in text and "10 років тому" in text:
                        print("  [-] Received generic home/page content (Redirect simulation)")
                        continue

                    if "success" in text and '"success":true' in text:
                        try:
                            js = resp.json()
                            html = js.get("response", "")
                            soup = BeautifulSoup(html, 'html.parser')
                            items = soup.select("li[data-file], li[data-src], li[data-url]")
                            if items:
                                print(f"  [+] SUCCESS! Found {len(items)} episodes in JSON response")
                                return True
                        except:
                            pass

                    # Try direct HTML parsing
                    soup = BeautifulSoup(text, 'html.parser')
                    items = soup.select("li[data-file], li[data-src], li[data-url]")
                    if items:
                        print(f"  [+] SUCCESS! Found {len(items)} episodes in HTML response")
                        return True
                    else:
                        preview = text[:200].replace('\n', ' ')
                        print(f"  [-] No items found. Preview: {preview}...")
                else:
                    print(f"  [-] Failed with status {resp.status_code}")

            except Exception as e:
                print(f"  [!] Error: {e}")

    return False

if __name__ == "__main__":
    # Test for "House of the Dragon" (9330)
    test_eneyida_playlists("9330", "https://eneyida.tv/9330-dim-drakona-2-sezon.html")
    # Test for "Influencers" (10053)
    test_eneyida_playlists("10053", "https://eneyida.tv/10053-influensery.html")
