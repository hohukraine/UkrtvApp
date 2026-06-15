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


def extract_embed_iframes(page_html: str):
    soup = BeautifulSoup(page_html, "html.parser")
    urls = []
    for iframe in soup.select('iframe'):
        src = (iframe.get('src') or '').strip()
        if src and 'hdvbua.pro/embed' in src.lower():
            urls.append(src)
    out = []
    for u in urls:
        if u not in out:
            out.append(u)
    return out


def extract_m3u8(text: str):
    if not text:
        return []
    return re.findall(r'https?://[^\s"\'\\]+\.m3u8[^\s"\'\\]*', text)


def extract_tokens(s: str):
    # very broad: things like s01e01, s1e2, s01-e01 etc
    tokens = []
    broad = re.findall(r'([sS]\d{1,3}[^0-9a-zA-Z]?[eE]\d{1,3})', s)
    tokens.extend(broad)

    # also try raw substrings containing s and e nearby
    alt = re.findall(r'(s\d{1,3}e\d{1,3})', s, flags=re.IGNORECASE)
    tokens.extend(alt)

    # normalize
    uniq = []
    for t in tokens:
        t2 = t.lower()
        if t2 not in uniq:
            uniq.append(t2)
    return uniq


def analyze(page_url: str):
    print(f"\n==============================\nPAGE: {page_url}")
    html = get_page(page_url)
    if not html:
        return

    embed_iframes = extract_embed_iframes(html)
    print(f"embed_iframes: {len(embed_iframes)}")

    all_m3u8 = []
    for iframe in embed_iframes:
        body = get_page(iframe)
        if not body:
            continue
        m3u8s = extract_m3u8(body)
        print(f"iframe: {iframe}\n  m3u8 count: {len(m3u8s)}")
        all_m3u8.extend(m3u8s)

    # dedup preserving order
    seen = set()
    dedup = []
    for u in all_m3u8:
        if u not in seen:
            seen.add(u)
            dedup.append(u)
    all_m3u8 = dedup

    print(f"dedup m3u8 count: {len(all_m3u8)}")
    if not all_m3u8:
        return

    # show first 10
    print("\nFirst 10 m3u8 URLs:")
    for u in all_m3u8[:10]:
        print(" ", u)

    print("\nExtracted sXeY-like tokens from first 10 URLs:")
    for u in all_m3u8[:10]:
        toks = extract_tokens(u)
        print(" ", toks, "from:", u.split('/')[-5:])


def main():
    for page_url in [
        "https://eneyida.tv/7026-zzovni.html",
        "https://eneyida.tv/1024-eyforiya-2026-x.html",
    ]:
        analyze(page_url)


if __name__ == '__main__':
    main()

