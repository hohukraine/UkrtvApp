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


def get_page(url: str) -> str:
    r = session.get(url, timeout=25, allow_redirects=True)
    r.raise_for_status()
    return r.text


def main():
    html = get_page(BASE_URL)
    soup = BeautifulSoup(html, 'html.parser')

    # Find likely title links
    links = soup.find_all('a', href=True)
    title_links = []
    for a in links:
        href = a['href']
        if any(k in href for k in ['/seriali/', '/anime/', '/multfilm-serials/', '/films/', '.html']):
            text = a.get_text(' ', strip=True)
            title_links.append((href, text))

    # Find candidate poster images
    imgs = soup.find_all('img')
    imgs_candidates = []
    for img in imgs:
        data_src = img.get('data-src')
        src = img.get('src')
        poster = data_src or src
        if poster and poster.strip():
            imgs_candidates.append(poster.strip())

    print("homepage status ok. html len:", len(html))
    print("title-like links found:", len(title_links))
    print("image posters candidates:", len(imgs_candidates))

    # Print a few href samples
    print("\nSample title-like hrefs:")
    shown = 0
    for href, text in title_links:
        if not href:
            continue
        print(" - href:", href[:120], "| text:", (text[:80] if text else ''))
        shown += 1
        if shown >= 10:
            break

    # Try to find common item containers by inspecting ancestors of first a/img
    first_a = None
    for href, text in title_links:
        # locate actual tag by matching href
        # (best-effort)
        first_a = soup.find('a', href=href)
        if first_a:
            break

    first_img = None
    if imgs_candidates:
        # locate first img tag with data-src or src
        for img in imgs:
            poster = img.get('data-src') or img.get('src')
            if poster and poster.strip():
                first_img = img
                break

    def summarize_container(tag):
        if not tag:
            return
        # walk up a bit and print classes/ids
        cur = tag
        for i in range(6):
            cls = cur.get('class')
            idv = cur.get('id')
            print(f"  level {i}: tag={cur.name} id={idv} class={cls}")
            cur = cur.parent if cur.parent else None
            if not cur:
                break

    print("\nContainer ancestry for first title link (if found):")
    summarize_container(first_a)

    print("\nContainer ancestry for first poster img (if found):")
    summarize_container(first_img)

    # Also print which CSS classes are most frequent (top 30)
    class_counts = {}
    for t in soup.find_all(True):
        cls = t.get('class')
        if not cls:
            continue
        for c in cls:
            class_counts[c] = class_counts.get(c, 0) + 1
    top = sorted(class_counts.items(), key=lambda x: -x[1])[:30]
    print("\nTop frequent classes:")
    for c, n in top:
        print(f"  {c}: {n}")


if __name__ == '__main__':
    main()

