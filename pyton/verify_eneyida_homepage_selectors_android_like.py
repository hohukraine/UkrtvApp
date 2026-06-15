import sys
sys.path.insert(0, 'C:\\UkrtvApp')
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

import re
import requests
from bs4 import BeautifulSoup

BASE_URL = "https://eneyida.tv/"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
    "Accept-Language": "uk-UA,uk;q=0.9,en;q=0.8",
    "Referer": BASE_URL,
    "Connection": "keep-alive",
}

session = requests.Session()
session.headers.update(HEADERS)


def get_page(url: str):
    r = session.get(url, timeout=25, allow_redirects=True)
    print("GET", url, "->", r.status_code, "len", len(r.text), "ct", r.headers.get('content-type'))
    return r.text


def main():
    html = get_page(BASE_URL)
    soup = BeautifulSoup(html, 'html.parser')

    selectors = [
        '.short-item, .movie-item, .th-item, .shortstory, .movie-short, .short-t, .short-i'
    ]
    # BeautifulSoup doesn't support combined selectors with commas directly
    # but select_one/select can; easiest is select with CSS selector string.
    container_sel = '.short-item, .movie-item, .th-item, .shortstory, .movie-short, .short-t, .short-i'
    containers = soup.select(container_sel)
    print("containers matched:", len(containers))

    title_selectors = '.short-title, a.short-title, .th-title, .shortstorytitle a, .movie-title a, .short-t a, h2 a'
    img_sel = 'img'

    title_count = 0
    href_count = 0
    poster_count = 0

    examples = []

    for el in containers[:30]:
        title_el = el.select_one(title_selectors)
        title = title_el.get_text(strip=True) if title_el else ''
        href = ''
        if title_el and title_el.has_attr('href'):
            href = title_el.get('href')
        if not href:
            a = el.select_one('a')
            href = a.get('href') if a else ''

        img = el.select_one('img')
        poster_src = ''
        if img:
            poster_src = img.get('data-src') or img.get('src') or ''

        if title:
            title_count += 1
        if href:
            href_count += 1
        if poster_src:
            poster_count += 1

        if title or href or poster_src:
            examples.append({
                'title': title[:80],
                'href': (href or '')[:120],
                'poster': (poster_src or '')[:120],
                'classes': el.get('class'),
            })

    print("among first containers (<=30):")
    print("  title found:", title_count)
    print("  href found:", href_count)
    print("  poster found:", poster_count)

    print("\nExamples (up to 5):")
    for e in examples[:5]:
        print(e)

    # Also check if page contains any of the title selectors globally
    global_titles = soup.select(title_selectors)
    print("global title-elements matched:", len(global_titles))

    # Print a short snippet of body to understand if cloudflare/empty page
    text_lower = soup.get_text(' ', strip=True).lower()
    if 'checking your browser' in text_lower or 'cf-challenge' in text_lower:
        print("WARNING: cloudflare-like text found in page text")


if __name__ == '__main__':
    main()

