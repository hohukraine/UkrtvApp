import requests
from bs4 import BeautifulSoup

def test_eneyida_get_playlists(news_id, referer):
    print(f"\n{'='*20} TESTING Eneyida GET discovery (news_id={news_id}) {'='*20}")

    base_url = "https://eneyida.tv/"
    session = requests.Session()

    # 1. Get initial cookies
    session.get(referer, timeout=10)

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "X-Requested-With": "XMLHttpRequest",
        "Referer": referer
    }

    endpoints = [
        "index.php?do=ajax&mod=playlists",
        "engine/ajax/playlists.php"
    ]
    xfields = ["seria", "playlist"]

    for mod_url in endpoints:
        for xf in xfields:
            url = f"{base_url}{mod_url}&news_id={news_id}&xfield={xf}"
            if "engine/ajax" in url:
                url = f"{base_url}{mod_url}?news_id={news_id}&xfield={xf}"

            try:
                print(f"\nTrying GET {url}...")
                resp = session.get(url, headers=headers, timeout=10)
                print(f"Status: {resp.status_code}")

                text = resp.text
                if resp.status_code == 200:
                    if "<title>Дивитися фільми" in text and "Десять років тому" in text:
                        print("  [-] Received generic page content (Redirect simulation)")
                        continue

                    if '"success":true' in text:
                        print("  [+] SUCCESS! JSON success=true")
                        return True

                    if "data-file" in text or "data-src" in text:
                        print("  [+] SUCCESS! Found attributes in HTML response")
                        return True

                    preview = text[:200].replace('\n', ' ')
                    print(f"  [-] No items. Preview: {preview}...")
            except Exception as e:
                print(f"  [!] Error: {e}")

    return False

if __name__ == "__main__":
    test_eneyida_get_playlists("10053", "https://eneyida.tv/10053-influensery.html")
