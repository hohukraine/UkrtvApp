import re
import sys
from urllib.parse import urljoin, urlparse

import requests


def extract_key_urls(m3u8_text: str, base_url: str):
    key_uris = []
    for line in m3u8_text.splitlines():
        line = line.strip()
        if not line:
            continue
        if line.startswith('#EXT-X-KEY'):
            m = re.search(r'URI="([^"]+)"', line)
            if m:
                key_uris.append(urljoin(base_url, m.group(1)))
    return key_uris


def extract_url_lines(m3u8_text: str, base_url: str):
    """Extract all non-comment URLs from m3u8.

    Can include variant/media playlists (*.m3u8) and/or segments (*.ts/*.m4s).
    """
    urls = []
    for line in m3u8_text.splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue

        # common media/playlist file patterns
        if re.search(r'\.(m4s|ts|m3u8|mp4)($|[?#])', line, re.IGNORECASE):
            urls.append(urljoin(base_url, line))
    return urls


def pick_referer_origin(page_url: str):
    if not page_url:
        page_url = 'https://uakino.best/'
    parsed = urlparse(page_url)
    origin = f"{parsed.scheme}://{parsed.netloc}" if parsed.scheme and parsed.netloc else 'https://uakino.best'
    return page_url, origin


def looks_like_html(body: bytes, content_type: str | None) -> bool:
    if content_type and 'text/html' in content_type.lower():
        return True
    # if body starts with '<' it's probably HTML
    try:
        head = body[:200].lstrip()
        return head.startswith(b'<')
    except Exception:
        return False


def fetch_url(session: requests.Session, url: str, headers: dict, timeout: int = 20):
    rr = session.get(url, headers=headers, timeout=timeout, allow_redirects=True)
    ctype = rr.headers.get('Content-Type')
    clen = len(rr.content)
    preview = rr.text[:160] if clen > 0 and not looks_like_html(rr.content, ctype) else ''
    snippet = ''
    if looks_like_html(rr.content, ctype):
        try:
            snippet = rr.text[:200].replace('\n', ' ').strip()
        except Exception:
            snippet = ''
    return rr.status_code, rr.url, ctype, clen, preview, snippet


def main():
    if len(sys.argv) < 2:
        print('Usage: python verify_hls_headers.py <index_m3u8_url> [page_url_referer]')
        sys.exit(2)

    index_m3u8_url = sys.argv[1]
    page_url = sys.argv[2] if len(sys.argv) >= 3 else 'https://uakino.best/'

    user_agent = "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36"

    referer, origin = pick_referer_origin(page_url)

    headers = {
        'Referer': referer,
        'Origin': origin,
        'User-Agent': user_agent,
        'Accept': '*/*',
    }

    timeout = 20
    s = requests.Session()

    print('== Fetching playlist ==')
    r = s.get(index_m3u8_url, headers=headers, timeout=timeout, allow_redirects=True)
    print('index.m3u8', r.status_code, 'final=', r.url)
    print('Content-Type:', r.headers.get('Content-Type'))
    print('Length:', len(r.content))

    text = r.text
    if 'EXTM3U' not in text[:2000]:
        print('WARN: playlist does not look like m3u8 (missing #EXTM3U early).')
        print('First 300 chars:\n', text[:300])

    # Base for resolving relative URIs
    base_url = r.url
    key_urls = extract_key_urls(text, base_url=base_url)
    url_lines = extract_url_lines(text, base_url=base_url)

    print('\n== Extracted ==')
    print('keys:', len(key_urls))
    print('url entries (playlist or segments):', len(url_lines))

    print('\n== Fetching samples (keys + first 5 url entries) ==')

    sample = [('KEY', u) for u in key_urls[:3]]
    sample += [('ENTRY', u) for u in url_lines[:5]]

    if not sample:
        print('No keys/url entries extracted. Printing first 500 chars for inspection:')
        print(text[:500])
        sys.exit(0)

    for kind, u in sample:
        try:
            status, final_url, ctype, clen, preview, snippet = fetch_url(s, u, headers=headers, timeout=timeout)
            print(f'{kind}: {status} len={clen} ctype={ctype} final={final_url}')
            if snippet:
                print('  -> Looks like HTML challenge/block. Snippet:', snippet[:200])
        except Exception as e:
            print(kind, 'ERROR', repr(e))


if __name__ == '__main__':
    main()

