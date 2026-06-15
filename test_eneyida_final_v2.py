import requests
from bs4 import BeautifulSoup
import time

def test_eneyida_ultimate(news_id, referer):
    print(f"\n{'='*20} ULTIMATE Eneyida TEST (news_id={news_id}) {'='*20}")

    base_url = "https://eneyida.tv/"
    session = requests.Session()

    # Pre-request to get session/cookies
    print(f"[*] Visiting {referer}...")
    page_resp = session.get(referer, timeout=10)
    page_soup = BeautifulSoup(page_resp.text, 'html.parser')

    # 1. Try to find static data-file on page
    print("[*] Checking for static playlist on page...")
    items = page_soup.select("li[data-file], li[data-src], li[data-url]")
    if items:
        print(f"  [+] SUCCESS! Found {len(items)} items directly on page.")
        return True

    # 2. Try AJAX with specific mod for Eneyida
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "X-Requested-With": "XMLHttpRequest",
        "Referer": referer,
        "Accept": "application/json, text/javascript, */*; q=0.01"
    }

    # Note: Eneyida often uses 'mod=playlists' but redirects if referer/cookies aren't perfect.
    # Also trying 'mod=get_playlist' which is used in some templates.
    endpoints = [
        "index.php?do=ajax&mod=re_playlist",
        "index.php?do=ajax&mod=get_playlist",
        "index.php?do=ajax&mod=playlists"
    ]
    xfields = ["seria", "playlist"]

    for mod in endpoints:
        url = f"{base_url}{mod}"
        for xf in xfields:
            data = {"news_id": news_id, "xfield": xf, "time": str(int(time.time()))}
            print(f"[*] Trying POST {url} (xf={xf})...")
            try:
                resp = session.post(url, headers=headers, data=data, timeout=10)
                if resp.status_code == 200:
                    text = resp.text
                    if "<title>" in text and "10 років тому" in text:
                        print("  [-] Redirected to main page content.")
                        continue

                    if "data-file" in text or "data-src" in text or "success" in text:
                        print(f"  [+] SUCCESS! Found payload at {mod} with {xf}")
                        # Print start of payload
                        print(f"      Payload: {text[:100]}...")
                        return True
                    else:
                        print(f"  [-] Empty or invalid response. ({len(text)} bytes)")
                else:
                    print(f"  [-] Status {resp.status_code}")
            except Exception as e:
                print(f"  [!] Error: {e}")

    return False

if __name__ == "__main__":
    test_eneyida_ultimate("10053", "https://eneyida.tv/10053-influensery.html")
    test_eneyida_ultimate("7026", "https://eneyida.tv/7026-zzovni.html")
