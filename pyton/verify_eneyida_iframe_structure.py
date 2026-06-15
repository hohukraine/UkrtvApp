import sys
sys.path.insert(0, 'C:\\UkrtvApp')
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

import re
import requests
from bs4 import BeautifulSoup

BASE_URL = "https://eneyida.tv"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
    "Accept-Language": "uk-UA,uk;q=0.9,en;q=0.8",
    "Referer": BASE_URL,
    "Connection": "keep-alive",
}

session = requests.Session()
session.headers.update(HEADERS)


def get_page(url: str, method: str = "GET", data=None, referer: str | None = None) -> str | None:
    try:
        headers = {}
        if referer:
            headers["Referer"] = referer
        if method.upper() == "GET":
            r = session.get(url, headers=headers, timeout=20, allow_redirects=True)
        else:
            r = session.post(url, headers=headers, data=data, timeout=20, allow_redirects=True)
        r.raise_for_status()
        return r.text
    except Exception as e:
        print(f"Помилка {method} {url}: {e}")
        return None


def extract_iframes(html: str):
    soup = BeautifulSoup(html, "html.parser")
    res = []
    for iframe in soup.select("iframe"):
        src = (iframe.get('src') or '').strip()
        data_src = (iframe.get('data-src') or '').strip()
        if src:
            res.append(src)
        elif data_src:
            res.append(data_src)
    # normalize //
    norm = []
    for u in res:
        if u.startswith("//"):
            norm.append("https:" + u)
        else:
            norm.append(u)
    return norm


def extract_m3u8(text: str):
    if not text:
        return []
    return re.findall(r'https?://[^\s"\'\\]+\.m3u8[^\s"\'\\]*', text)


def analyze_iframe_requests(page_url: str, iframe_urls: list[str]):
    print(f"\n-- page: {page_url}")

    for i, iframe_url in enumerate(iframe_urls, 1):
        if 'hdvbua.pro' not in iframe_url.lower():
            continue

        print(f"\n[{i}] iframe: {iframe_url}")

        # try GET (iframe normally works with GET)
        body = get_page(iframe_url, method="GET", referer=page_url)
        if body is None:
            print("GET body: None")
            continue

        m3u8s = extract_m3u8(body)
        print(f"m3u8 found in iframe response: {len(m3u8s)}")
        for m in m3u8s[:3]:
            print(f"  {m}")

        # simple heuristics for episodes/playlist indicators
        lowered = body.lower()
        indicators = [
            'playlist', 'm3u8', 'episode', 'episodes', 'part', 'parts', 'season', 'seasons', 'vod', 'index.m3u8', 'manifest'
        ]
        hits = [k for k in indicators if k in lowered]
        print(f"indicator_hits: {hits[:10]}")


def main():
    urls = [
        "https://eneyida.tv/7026-zzovni.html",
        "https://eneyida.tv/10037-mortal-kombat-2.html",
        "https://eneyida.tv/1024-eyforiya-2026-x.html",
    ]

    for page_url in urls:
        print(f"\n============================")
        print(f"PAGE: {page_url}")
        html = get_page(page_url)
        if not html:
            print("HTML: None")
            continue

        iframe_urls = extract_iframes(html)
        print(f"Found iframe count in page: {len(iframe_urls)}")
        analyze_iframe_requests(page_url, iframe_urls)


if __name__ == '__main__':
    main()

