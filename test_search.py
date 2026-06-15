import requests
from bs4 import BeautifulSoup
import re
import sys
import json

# Force UTF-8 for console output
if sys.platform == 'win32':
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

def extract_m3u8(html):
    return re.findall(r'https?://[^\s"\'\\]+\.m3u8[^\s"\'\\]*', html)

def test_provider(name, base, search_path, data, headers):
    print(f"\n--- Testing {name} ---")
    try:
        search_url = f"{base}{search_path}"
        print(f"POST to: {search_url}")
        r = requests.post(search_url, data=data, headers=headers, timeout=15)
        print(f"Search status: {r.status_code}")

        soup = BeautifulSoup(r.text, 'html.parser')

        # Generic link finding for search results
        links = []
        for a in soup.select('a'):
            href = a.get('href', '')
            text = a.get_text().strip()
            if 'друзі' in text.lower() and '.html' in href:
                links.append((text, href))

        if not links:
            print(f"No results for 'Друзі' on {name}")
            return

        # Take first result
        title, url = links[0]
        if not url.startswith('http'):
            url = base + url if url.startswith('/') else f"{base}/{url}"

        print(f"Found series: {title}")
        print(f"URL: {url}")

        # Load series page
        r_page = requests.get(url, headers=headers, timeout=15)
        page_html = r_page.text
        page_soup = BeautifulSoup(page_html, 'html.parser')

        # 1. AJAX Playlist
        pl_ajax = page_soup.select_one('.playlists-ajax')
        if pl_ajax:
            news_id = pl_ajax.get('data-news_id')
            xfname = pl_ajax.get('data-xfname') or 'seria'
            print(f"AJAX Playlist Found: news_id={news_id}, xfield={xfname}")

            ajax_url = f"{base}/engine/ajax/playlists.php?news_id={news_id}&xfield={xfname}"
            r_ajax = requests.get(ajax_url, headers={'X-Requested-With': 'XMLHttpRequest', 'Referer': url}, timeout=15)
            if r_ajax.status_code == 200:
                print("✓ AJAX request successful")
                # Look for files in AJAX response
                files = re.findall(r'data-file="([^"]+)"', r_ajax.text)
                if not files:
                    files = re.findall(r'data-file=\\"([^\\"]+)\\"', r_ajax.text)

                if files:
                    print(f"✓ Found {len(files)} episodes/sources in playlist")
                    print(f"First source: {files[0][:100]}...")

                    # If it is a player link, try to resolve it
                    if 'ashdi' in files[0] or 'hdvbua' in files[0]:
                        player_url = files[0]
                        if player_url.startswith('//'): player_url = 'https:' + player_url
                        print(f"Attempting to resolve player: {player_url}")
                        r_player = requests.get(player_url, headers={'Referer': url}, timeout=15)
                        m3u8s = extract_m3u8(r_player.text)
                        if m3u8s:
                            print(f"✓✓ FINAL M3U8: {m3u8s[0]}")
                else:
                    print("✗ No files found in AJAX response")

        # 2. Iframe search
        iframes = page_soup.select('iframe')
        for iframe in iframes:
            src = iframe.get('src') or iframe.get('data-src')
            if src and not 'google' in src:
                if src.startswith('//'): src = 'https:' + src
                print(f"Found Iframe: {src}")
                try:
                    r_if = requests.get(src, headers={'Referer': url}, timeout=10)
                    m3u8s = extract_m3u8(r_if.text)
                    if m3u8s:
                        print(f"✓✓ FINAL M3U8 (from iframe): {m3u8s[0]}")
                        break
                except: pass

    except Exception as e:
        print(f"Error testing {name}: {e}")

# Configuration
ua_headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
}

# 1. UakinoBest
test_provider(
    "UakinoBest",
    "https://uakino.best",
    "/index.php?do=search",
    {'do': 'search', 'subaction': 'search', 'story': 'Друзі'},
    ua_headers
)

# 2. Uaserials
test_provider(
    "Uaserials",
    "https://uaserials.my",
    "/index.php?do=search",
    {'do': 'search', 'subaction': 'search', 'story': 'Друзі'},
    ua_headers
)

# 3. Eneyida
test_provider(
    "Eneyida",
    "https://eneyida.tv",
    "/index.php?do=search",
    {'do': 'search', 'subaction': 'search', 'story': 'Друзі'},
    ua_headers
)
