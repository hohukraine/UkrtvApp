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


def get_page(url: str) -> str | None:
    try:
        r = session.get(url, timeout=20, allow_redirects=True)
        r.raise_for_status()
        return r.text
    except Exception as e:
        print(f"Помилка завантаження {url}: {e}")
        return None


def find_iframe_sources(html: str):
    soup = BeautifulSoup(html, "html.parser")
    sources = []
    for iframe in soup.select("iframe"):
        src = iframe.get("src") or ""
        data_src = iframe.get("data-src") or ""
        if src:
            sources.append(("src", src))
        if data_src:
            sources.append(("data-src", data_src))
    return sources


def snippet_around(html: str, pattern: str, radius: int = 250):
    idx = html.lower().find(pattern.lower())
    if idx < 0:
        return ""
    start = max(0, idx - radius)
    end = min(len(html), idx + radius)
    return html[start:end]


def analyze(url: str):
    print(f"\n=== URL: {url} ===")
    html = get_page(url)
    if not html:
        print("HTML: None")
        return

    soup = BeautifulSoup(html, "html.parser")

    playlists_ajax = soup.select_one('.playlists-ajax')
    if playlists_ajax:
        news_id = playlists_ajax.get('data-news_id', '')
        xfname = playlists_ajax.get('data-xfname', '')
        print(f"playlists-ajax: FOUND data-news_id='{news_id}' data-xfname='{xfname}'")
    else:
        print("playlists-ajax: NOT FOUND")

    li_data_file = soup.select('li[data-file]')
    print(f"li[data-file] count: {len(li_data_file)}")

    season_tabs = soup.select('.playlists-lists li')
    print(f"season tabs count: {len(season_tabs)}")

    iframe_sources = find_iframe_sources(html)
    print(f"iframe count: {len(iframe_sources)}")
    hdvbua_iframes = [s for s in iframe_sources if 'hdvbua' in (s[1] or '').lower()]
    print(f"iframe hdvbua.* count: {len(hdvbua_iframes)}")
    if hdvbua_iframes:
        for kind, src in hdvbua_iframes[:3]:
            print(f"  iframe {kind}: {src}")

    # direct mention checks in HTML
    patterns = ["playlists.php", "news_id", "playlist-", "data-news_id", "ashdi", "hdvbua.pro", "vod/"]
    for p in patterns:
        has = p in html
        print(f"  contains '{p}': {has}")

    # print small snippets for key endpoints
    for p in ["playlists.php", "news_id", "hdvbua.pro", "ashdi.vip", "playlist-"]:
        snip = snippet_around(html, p)
        if snip:
            print(f"  --- snippet around '{p}' ---")
            print(snip[:600].replace('\n',' '))


def main():
    urls = [
        "https://eneyida.tv/7026-zzovni.html",
        "https://eneyida.tv/10037-mortal-kombat-2.html",
        "https://eneyida.tv/1024-eyforiya-2026-x.html",
    ]
    for u in urls:
        analyze(u)


if __name__ == '__main__':
    main()

