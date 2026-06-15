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


def extract_iframe_embed_urls(page_html: str):
    soup = BeautifulSoup(page_html, "html.parser")
    out = []
    for iframe in soup.select('iframe'):
        src = (iframe.get('src') or '').strip()
        if src and 'hdvbua.pro/embed' in src.lower():
            out.append(src)
    # dedup
    uniq = []
    for u in out:
        if u not in uniq:
            uniq.append(u)
    return uniq


def extract_m3u8(text: str):
    if not text:
        return []
    return re.findall(r'https?://[^\s"\'\\]+\.m3u8[^\s"\'\\]*', text)


def probe_regexes(m3u8_urls: list[str]):
    regexes = [
        ("sDD eDD (from.s01e01)", r"from\\.s(?P<s>\\d{1,3})e(?P<e>\\d{1,3})"),
        ("sDD eDD (any sXXeYY)", r"s(?P<s>\\d{1,3})e(?P<e>\\d{1,3})"),
        ("slash sDD eDD", r"/(?P<s>\\d{1,3})e(?P<e>\\d{1,3})"),
        ("euphoria_sDDeYY", r"euphoria_?(?P<s>\\w+?)s(?P<snum>\\d{1,3})e(?P<e>\\d{1,3})"),
    ]

    for label, pat in regexes:
        rgx = re.compile(pat, re.IGNORECASE)
        hits = []
        for u in m3u8_urls:
            m = rgx.search(u)
            if m:
                if 's' in m.groupdict() and 'e' in m.groupdict():
                    hits.append((m.group('s'), m.group('e')))
                else:
                    # fallback if groups named differently
                    d = m.groupdict()
                    if 'snum' in d and 'e' in d:
                        hits.append((d['snum'], d['e']))
        uniq = sorted(set(hits), key=lambda x: (int(x[0]), int(x[1]))) if hits else []
        print(f"{label}: total_hits={len(hits)} unique_pairs={len(uniq)}")
        if uniq:
            print("  first_pairs:", uniq[:10])


def main():
    for page_url in [
        "https://eneyida.tv/7026-zzovni.html",
        "https://eneyida.tv/1024-eyforiya-2026-x.html",
    ]:
        print("\n==============================")
        print("PAGE:", page_url)
        html = get_page(page_url)
        if not html:
            continue
        embed_iframes = extract_iframe_embed_urls(html)
        print("embed_iframes:", embed_iframes)
        if not embed_iframes:
            continue
        all_m3u8 = []
        for iframe in embed_iframes:
            iframe_body = get_page(iframe)
            if not iframe_body:
                continue
            m3u8s = extract_m3u8(iframe_body)
            print("iframe m3u8 count:", len(m3u8s))
            all_m3u8.extend(m3u8s)
        # dedup
        all_m3u8 = list(dict.fromkeys(all_m3u8))
        print("dedup m3u8 count:", len(all_m3u8))
        probe_regexes(all_m3u8[:200])


if __name__ == '__main__':
    main()

