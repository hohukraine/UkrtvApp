import sys
sys.path.insert(0, 'C:\\UkrtvApp')
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

import requests
from bs4 import BeautifulSoup
import re
from urllib.parse import urljoin

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

DESCRIPTION_SELECTORS = [
    ".description",
    ".full-text",
    ".movie-desc",
    ".th-desc",
    ".entry-content",
    ".ftext",
    ".full-desc",
    ".fdesc",
    "#full-desc",
    "[itemprop=description]",
]

def get_page(url: str) -> str | None:
    try:
        r = session.get(url, timeout=20, allow_redirects=True)
        r.raise_for_status()
        return r.text
    except Exception as e:
        print(f"Помилка завантаження {url}: {e}")
        return None

def first_text(soup, selector: str) -> str:
    el = soup.select_one(selector)
    if not el:
        return ""
    return el.get_text(" ", strip=True)

def has_m3u8(html: str) -> int:
    return len(re.findall(r'https?://[^\s"\'\\]+\.m3u8[^\s"\'\\]*', html))

def analyze_details(url: str):
    print(f"\n=== URL: {url} ===")
    html = get_page(url)
    if not html:
        print("HTML: None")
        return

    soup = BeautifulSoup(html, "html.parser")

    desc_found = False
    desc_len = 0
    for sel in DESCRIPTION_SELECTORS:
        t = first_text(soup, sel)
        if t:
            desc_found = True
            desc_len = len(t)
            print(f"description match: {sel} len={desc_len}")
            break

    if not desc_found:
        # check for common placeholders
        text = soup.get_text(" ", strip=True)
        placeholder = any(k.lower() in text.lower() for k in ["немає опис", "опис відсут", "ще немає"])
        print(f"description: NOT FOUND (placeholder_any={placeholder})")

    # playlist indicators
    playlists_ajax = soup.select_one('.playlists-ajax')
    if playlists_ajax:
        news_id = playlists_ajax.get('data-news_id', '')
        xfname = playlists_ajax.get('data-xfname', '')
        print(f"playlists-ajax: data-news_id='{news_id}' data-xfname='{xfname}'")
    else:
        print("playlists-ajax: NOT FOUND")

    li_data_file = soup.select('li[data-file]')
    print(f"li[data-file] count: {len(li_data_file)}")

    # seasons tabs
    season_tabs = soup.select('.playlists-lists li')
    print(f"season tabs count: {len(season_tabs)}")

    # m3u8 direct in page
    print(f"direct m3u8 count in page HTML: {has_m3u8(html)}")


def main():
    urls = [
        "https://eneyida.tv/7026-zzovni.html",
        "https://eneyida.tv/10037-mortal-kombat-2.html",
        "https://eneyida.tv/1024-eyforiya-2026-x.html",
    ]

    for u in urls:
        analyze_details(u)

if __name__ == '__main__':
    main()

