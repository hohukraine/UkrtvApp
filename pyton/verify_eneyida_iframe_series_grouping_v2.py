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


def extract_embed_iframes(page_html: str):
    soup = BeautifulSoup(page_html, "html.parser")
    urls = []
    for iframe in soup.select('iframe'):
        src = (iframe.get('src') or '').strip()
        if not src:
            continue
        if 'hdvbua.pro/embed' in src.lower():
            urls.append(src)
    # dedup preserve order
    out = []
    for u in urls:
        if u not in out:
            out.append(u)
    return out


def extract_m3u8(text: str):
    if not text:
        return []
    return re.findall(r'https?://[^\s"\'\\]+\.m3u8[^\s"\'\\]*', text)


def group_by_regex(urls: list[str], pattern: str):
    rgx = re.compile(pattern, re.IGNORECASE)
    groups = defaultdict(int)
    pairs = set()
    for u in urls:
        m = rgx.search(u)
        if not m:
            continue
        s = m.group('s')
        e = m.group('e')
        pairs.add((s, e))
        groups[(s, e)] += 1
    return pairs, groups


def analyze(page_url: str):
    print(f"\n====================\nPAGE: {page_url}")
    page_html = get_page(page_url)
    if not page_html:
        return

    embed_iframes = extract_embed_iframes(page_html)
    print(f"embed_iframes: {len(embed_iframes)}")

    all_m3u8 = []
    for iframe in embed_iframes:
        body = get_page(iframe)
        if not body:
            continue
        m3u8s = extract_m3u8(body)
        print(f"iframe: {iframe}\n  m3u8s={len(m3u8s)}")
        all_m3u8.extend(m3u8s)

    # dedup
    all_m3u8 = list(dict.fromkeys(all_m3u8))
    print(f"total dedup m3u8s={len(all_m3u8)}")

    if not all_m3u8:
        return

    # show a couple samples
    print("sample m3u8 urls:")
    for u in all_m3u8[:3]:
        print(" ", u)

    # try multiple patterns that match the observed formats
    patterns = [
        # common s01e02
        r"s(?P<s>\\d{1,3})e(?P<e>\\d{1,3})",
        # common s01e02
        r"s(?P<s>\\d{1,3})e(?P<e>\\d{1,3})",
        # observed: from.s01e01
        r"from\\.s(?P<s>\\d{1,3})e(?P<e>\\d{1,3})",
        # sometimes: .../s01e01...
        r"/(?P<s>\\d{1,3})e(?P<e>\\d{1,3})",

    ]

    for pat in patterns:
        pairs, groups = group_by_regex(all_m3u8, pat)
        if pairs:
            print(f"\npattern HIT: {pat}")
            print(f"unique (s,e) pairs found: {len(pairs)}")
            # show a few pairs
            shown = 0
            for (s, e) in sorted(pairs, key=lambda x: (int(x[0]), int(x[1]))):
                print(f"  s{s} e{e} count={groups[(s,e)]}")
                shown += 1
                if shown >= 8:
                    break
            break
    else:
        print("\nNo grouping regex matched any m3u8 URLs.")


def main():
    analyze("https://eneyida.tv/7026-zzovni.html")
    analyze("https://eneyida.tv/1024-eyforiya-2026-x.html")

if __name__ == '__main__':
    main()

