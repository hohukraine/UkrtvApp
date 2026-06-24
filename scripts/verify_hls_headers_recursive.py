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
    """Return all non-comment URL-ish lines from an m3u8.

    Includes variant/media playlists and segments.
    """
    urls = []
    for line in m3u8_text.splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue

        if re.search(r'\.(m4s|ts|m3u8|mp4)($|[?#])', line, re.IGNORECASE):
            urls.append(urljoin(base_url, line))
    return urls


def is_playlist_url(url: str):
    return url.lower().split('?')[0].endswith('.m3u8')


def looks_like_html(body: bytes, content_type: str | None) -> bool:
    if content_type and 'text/html' in content_type.lower():
        return True
    try:
        head = body[:200].lstrip()
        return head.startswith(b'<')
    except Exception:
        return False


def pick_referer_origin(page_url: str):
    if not page_url:
        page_url = 'https://uakino.best/'
    parsed = urlparse(page_url)
    origin = f"{parsed.scheme}://{parsed.netloc}" if parsed.scheme and parsed.netloc else 'https://uakino.best'
    return page_url, origin


def fetch(session: requests.Session, url: str, headers: dict, timeout: int = 20):
    rr = session.get(url, headers=headers, timeout=timeout, allow_redirects=True)
    ctype = rr.headers.get('Content-Type')
    clen = len(rr.content)
    return rr.status_code, rr.url, ctype, clen, rr.content


def describe_sample(body: bytes, ctype: str | None):
    if looks_like_html(body, ctype):
        try:
            return body.decode('utf-8', errors='replace')[:220].replace('\n', ' ').strip()
        except Exception:
            return '<html>'
    return ''


def main():
    if len(sys.argv) < 2:
        print('Usage: python verify_hls_headers_recursive.py <index_m3u8_url> [page_url_referer]')
        sys.exit(2)

    index_m3u8_url = sys.argv[1]
    page_url = sys.argv[2] if len(sys.argv) >= 3 else 'https://uakino.best/filmy/'

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

    # BFS/DFS through playlists up to max depth
    max_depth = 5
    max_nodes = 30
    visited = set()

    def log_entry(prefix: str, label: str, status: int, final_url: str, ctype: str | None, clen: int, body: bytes):
        html_snip = describe_sample(body, ctype)
        extra = ''
        if html_snip:
            extra = f"\n  HTML/blocked snippet: {html_snip[:200]}"
        print(f'{prefix}{label}: {status} len={clen} ctype={ctype} final={final_url}{extra}')

    queue = [(index_m3u8_url, 0)]
    nodes = 0

    while queue and nodes < max_nodes:
        url, depth = queue.pop(0)
        if url in visited:
            continue
        visited.add(url)
        nodes += 1

        if depth > max_depth:
            continue

        print(f'\n== [{depth}] GET {url} ==')
        status, final_url, ctype, clen, body = fetch(s, url, headers=headers, timeout=timeout)
        log_entry('  ', 'PLAYLIST', status, final_url, ctype, clen, body)

        # If not text m3u8, stop recursion
        try:
            text = body.decode('utf-8', errors='replace')
        except Exception:
            continue

        if '#EXTM3U' not in text[:4000]:
            continue

        base_for_relative = final_url

        key_urls = extract_key_urls(text, base_for_relative)
        media_entries = extract_url_lines(text, base_for_relative)

        # sample keys (if any)
        for i, ku in enumerate(key_urls[:3]):
            print(f'\n== [{depth}] sample KEY {i} ==')
            st, fu, kt, l, kb = fetch(s, ku, headers=headers, timeout=timeout)
            log_entry('  ', 'KEY', st, fu, kt, l, kb)

        # Now sample entries: first few playlists & first few segments
        pl_entries = [u for u in media_entries if is_playlist_url(u)]
        seg_entries = [u for u in media_entries if not is_playlist_url(u)]

        print(f'  extracted: keys={len(key_urls)} playlists={len(pl_entries)} segments/others={len(seg_entries)}')

        # Enqueue first playlists for further inspection
        for pu in pl_entries[:5]:
            if pu not in visited:
                queue.append((pu, depth + 1))

        # Directly sample first segments/others to see if blocked
        for su in seg_entries[:5]:
            print(f'\n== [{depth}] sample ENTRY {su} ==')
            st, fu, stype, l, sb = fetch(s, su, headers=headers, timeout=timeout)
            log_entry('  ', 'ENTRY', st, fu, stype, l, sb)

    print('\nDone.')


if __name__ == '__main__':
    main()

