import requests
from bs4 import BeautifulSoup
import time

def verify_eneyida_payload(news_id, referer):
    print(f"\nVerifying payload for {news_id}...")
    session = requests.Session()
    session.get(referer)

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "X-Requested-With": "XMLHttpRequest",
        "Referer": referer
    }

    url = "https://eneyida.tv/index.php?do=ajax&mod=re_playlist"
    data = {"news_id": news_id, "xfield": "seria", "time": str(int(time.time()))}

    resp = session.post(url, headers=headers, data=data)
    text = resp.text

    if "data-file" in text or "data-src" in text or "li" in text:
        soup = BeautifulSoup(text, 'html.parser')
        items = soup.select("li[data-file], li[data-src], li[data-url], li")
        print(f"Found {len(items)} elements.")
        for li in items[:5]:
            file = li.get('data-file') or li.get('data-src') or li.get('data-url')
            print(f"  Item: {li.get_text(strip=True)} | file={file}")

    if "<title>" in text:
        print("WARNING: Response looks like a full HTML page (possible redirect).")
        print(f"Title: {BeautifulSoup(text, 'html.parser').title.string}")

if __name__ == "__main__":
    verify_eneyida_payload("10053", "https://eneyida.tv/10053-influensery.html")
    verify_eneyida_payload("7026", "https://eneyida.tv/7026-zzovni.html")
