import sys
sys.path.insert(0, 'C:\\UkrtvApp')
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

import re
import requests
from bs4 import BeautifulSoup
from collections import defaultdict

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


def extract_iframe_urls(html: str):
    soup = BeautifulSoup(html, "html.parser")
    urls = []
    for iframe in soup.select("iframe"):
        src = (iframe.get('src') or '').strip()
        if src:
            if src not in urls:
                urls.append(src)
        data_src = (iframe.get('data-src') or '').strip()
        if data_src:
            if data_src.startswith('//'):
                data_src = 'https:' + data_src
            if data_src not in urls:
                urls.append(data_src)
    return urls


def extract_m3u8(text: str):
    if not text:
        return []
    return re.findall(r'https?://[^\s"\'\\]+\.m3u8[^\s"\'\\]*', text)


def group_by_sxe(urls: list[str]):
    groups = defaultdict(list)
    # try patterns:
    # s01e02, s1e2 etc (case-insensitive)
    pat = re.compile(r"s(?P<s>\\d{1,2})e(?P<e>\\d{1,2})", re.IGNORECASE)
    for u in urls:
        m = pat.search(u)
        if not m:
            groups[('unknown', 'unknown')].append(u)
            continue
        s = int(m.group('s'))
        e = int(m.group('e'))
        groups[(s, e)].append(u)
    return groups


def analyze_page(page_url: str):
    print(f"\n====================\nPAGE: {page_url}")
    html = get_page(page_url)
    if not html:
        return

    iframe_urls = extract_iframe_urls(html)
    embed_iframes = [u for u in iframe_urls if 'hdvbua.pro/embed' in u.lower()]
    print(f"iframes total={len(iframe_urls)}, embed_iframes={len(embed_iframes)}")

    all_m3u8 = []
    for iframe in embed_iframes:
        try:
            body = get_page(iframe)  # GET
            if not body:
                continue
            m3u8s = extract_m3u8(body)
            print(f"embed iframe: {iframe}\n  m3u8s={len(m3u8s)}")
            all_m3u8.extend(m3u8s)
        except Exception as e:
            print(f"iframe error: {e}")

    # dedup
    all_m3u8 = list(dict.fromkeys(all_m3u8))
    print(f"total dedup m3u8s from embed={len(all_m3u8)}")

    grouped = group_by_sxe(all_m3u8)
    known = [(k, v) for k, v in grouped.items() if k != ('unknown','unknown')]
    unknown_cnt = len(grouped.get(('unknown','unknown'), []))
    print(f"known sXe groups={len(known)}, unknown_urls={unknown_cnt}")

    # show up to 10 groups
    shown = 0
    for (s, e), lst in sorted(known, key=lambda kv: (int(kv[0][0]), int(kv[0][1]))):
        print(f"  s{s:02d}e{e:02d}: {len(lst)} urls")
        for u in lst[:1]:
            print(f"    sample: {u}")
        shown += 1
        if shown >= 10:
            break


def main():
    urls = [
        "https://eneyida.tv/7026-zzovni.html",
        "https://eneyida.tv/1024-eyforiya-2026-x.html",
    ]
    for u in urls:
        analyze_page(u)


if __name__ == '__main__':
    main()

