import requests
from bs4 import BeautifulSoup
import time

def test_eneyida_with_better_headers(news_id, referer):
    print(f"\n{'='*20} TESTING Eneyida with Enhanced Headers (news_id={news_id}) {'='*20}")

    base_url = "https://eneyida.tv/"
    session = requests.Session()

    # 1. Get initial cookies from movie page
    print(f"Visiting movie page to get cookies: {referer}")
    init_resp = session.get(referer, timeout=10)

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "X-Requested-With": "XMLHttpRequest",
        "Referer": referer,
        "Accept": "application/json, text/javascript, */*; q=0.01",
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"
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
                resp = session.post(url, headers=headers, data=data, timeout=10)
                print(f"Status: {resp.status_code}")

                text = resp.text
                if resp.status_code == 200:
                    if "<title>Дивитися фільми" in text and "Десять років тому" in text:
                        print("  [-] Received generic page content (Redirect simulation)")
                        continue

                    if '"success":true' in text:
                        print("  [+] SUCCESS! JSON success=true")
                        try:
                            js = resp.json()
                            html = js.get("response", "")
                            soup = BeautifulSoup(html, 'html.parser')
                            items = soup.select("li")
                            print(f"  [+] Found {len(items)} list items in response")
                            for li in items[:3]:
                                print(f"    - {li.get_text(strip=True)} | file={li.get('data-file', 'N/A')}")
                            return True
                        except Exception as e:
                            print(f"    Error parsing JSON response: {e}")

                    # Direct HTML check
                    if "data-file" in text or "data-src" in text:
                        print("  [+] SUCCESS! Found data attributes in raw response")
                        return True

                    preview = text[:300].replace('\n', ' ')
                    print(f"  [-] No items found. Preview: {preview}...")
                else:
                    print(f"  [-] Failed with status {resp.status_code}")
            except Exception as e:
                print(f"  [!] Error: {e}")

    return False

if __name__ == "__main__":
    test_eneyida_with_better_headers("10053", "https://eneyida.tv/10053-influensery.html")
