#!/usr/bin/env python3
"""
Final catalog verification for Uakino and Eneyida.
Extracts and counts items from listing pages.
"""

import subprocess
import json
import time
import re

UA = "Mozilla/5.0 (Linux; Android 10; KFTRWI) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Safari/537.36"

def curl_fetch(url, max_retries=2):
    for attempt in range(max_retries + 1):
        try:
            result = subprocess.run(
                ["curl", "-s", "-A", UA, "-L", url],
                capture_output=True, text=True, timeout=20
            )
            if result.returncode == 0 and len(result.stdout) > 1000:
                return result.stdout
            if attempt < max_retries:
                time.sleep(1)
        except:
            if attempt < max_retries:
                time.sleep(1)
    return None


def parse_uakino_page(html):
    """Parse Uakino category page"""
    items = []
    blocks = re.findall(
        r'<div class="movie-item short-item">(.*?)</div>\s*</div>',
        html, re.DOTALL
    )
    for block in blocks:
        title = ""
        m = re.search(r'<a class="movie-title"[^>]*>\s*(.*?)\s*</a>', block, re.DOTALL)
        if m:
            title = m.group(1).strip()
        
        url = ""
        m = re.search(r'href="(https?://[^"]+\.html)"', block)
        if m:
            url = m.group(1)
        
        poster = ""
        m = re.search(r'<img[^>]*src="([^"]+)"', block)
        if m:
            poster = m.group(1)
        
        quality = ""
        m = re.search(r'<div class="full-quality">([^<]+)</div>', block)
        if m:
            quality = m.group(1).strip()
        
        rating = ""
        m = re.search(r'IMDB:</span></div><div class="deck-value"[^>]*>([^<]+)', block)
        if m:
            rating = m.group(1).strip()
        
        if title and url:
            items.append({
                "title": title,
                "url": url,
                "poster": poster,
                "quality": quality,
                "rating": rating,
            })
    return items


def parse_eneyida_page(html):
    """Parse Eneyida filter page"""
    items = []
    articles = re.findall(
        r'<article[^>]*class="[^"]*short[^"]*"[^>]*>(.*?)</article>',
        html, re.DOTALL
    )
    for art in articles:
        title = ""
        m = re.search(r'<a class="short_title"[^>]*>\s*(.*?)\s*</a>', art, re.DOTALL)
        if m:
            title = m.group(1).strip()
        
        url = ""
        m = re.search(r'<a class="short_title"[^>]*href="([^"]+)"', art)
        if m:
            url = m.group(1)
        
        poster = ""
        m = re.search(r'<img[^>]*data-src="([^"]+)"', art)
        if m:
            poster = m.group(1)
        if not poster:
            m = re.search(r'<img[^>]*src="([^"]+)"', art)
            if m:
                poster = m.group(1)
        
        en_title = ""
        subtitle = re.search(r'<div class="short_subtitle">(.*?)</div>', art, re.DOTALL)
        if subtitle:
            # After the year link and •
            sub_html = subtitle.group(1)
            m = re.search(r'&bull;\s*(.*?)$', sub_html)
            if m:
                en_title = re.sub(r'<[^>]+>', '', m.group(1)).strip()
        
        year = ""
        m = re.search(r'<a[^>]*>(\d{4})</a>', art)
        if m:
            year = m.group(1)
        
        rating = ""
        m = re.search(r'ratingplus"[^>]*>\s*\+?(\d+)', art)
        if m:
            rating = m.group(1)
            m2 = re.search(r'ratingplus"[^>]*>.*?</span>\s*(\d+)', art, re.DOTALL)
            if m2:
                rating += m2.group(1)
        
        quality = ""
        m = re.search(r'label_quel-hd[^>]*>([^<]+)', art)
        if m:
            quality = m.group(1).strip()
        
        if title and url:
            items.append({
                "title": title,
                "en_title": en_title,
                "url": url,
                "poster": poster,
                "year": year,
                "rating": rating,
                "quality": quality,
            })
    return items


def scan_uakino():
    print("=" * 60)
    print("UAKINO CATALOG SCAN")
    print("=" * 60)
    
    categories = [
        ("/filmy/", "movies", 446),
        ("/seriesss/", "series", 167),
    ]
    
    total_est = 0
    for path, label, max_pages in categories:
        print(f"\n--- {label.upper()} ({path}) ---")
        
        # Scan pages 1, 2, last
        for page in [1, 2, max_pages]:
            url = f"https://uakino.best{path}"
            if page > 1:
                url += f"page/{page}/"
            
            html = curl_fetch(url)
            if not html:
                print(f"  Page {page}: FAILED")
                continue
            
            items = parse_uakino_page(html)
            print(f"  Page {page}: {len(items)} items")
            
            if page <= 2 and items:
                for i, item in enumerate(items[:4]):
                    t = item["title"][:50]
                    r = item["rating"][:6] if item["rating"] else "-"
                    q = item["quality"][:10] if item["quality"] else "-"
                    print(f"    {i+1}. [{r}][{q}] {t}")
        
        page1_items = len(parse_uakino_page(curl_fetch(f"https://uakino.best{path}")))
        est = max_pages * max(page1_items, 24)
        print(f"  Est. total: ~{est} items ({max_pages} pages)")
        total_est += est
    
    print(f"\n  UAKINO TOTAL: ~{total_est} items")


def scan_eneyida():
    print("\n" + "=" * 60)
    print("ENEYIDA CATALOG SCAN")
    print("=" * 60)
    
    pages_to_check = [1, 2, 3, 50, 100, 200, 300, 377]
    last_page = 377
    
    results = {}
    for p in pages_to_check:
        url = "https://eneyida.tv/f/sort=rating/order=desc/"
        if p > 1:
            url += f"page/{p}/"
        
        html = curl_fetch(url)
        if not html:
            print(f"  Page {p}: FAILED")
            continue
        
        items = parse_eneyida_page(html)
        results[p] = items
        print(f"  Page {p}: {len(items)} items")
        
        if p <= 3 and items:
            for i, item in enumerate(items[:4]):
                en = item["en_title"][:30] if item["en_title"] else "-"
                y = item["year"] if item["year"] else "-"
                q = item["quality"][:10] if item["quality"] else "-"
                r = item["rating"][:6] if item["rating"] else "-"
                t = item["title"][:40]
                print(f"    {i+1}. [{r}][{q}] {t} ({y}) EN={en}")
    
    # Check: is last page really last?
    last_html = curl_fetch(f"https://eneyida.tv/f/sort=rating/order=desc/page/{last_page}/")
    if last_html:
        last_items = parse_eneyida_page(last_html)
        print(f"\n  Last page ({last_page}): {len(last_items)} items")
        # Try page 378 to confirm
        check_html = curl_fetch("https://eneyida.tv/f/sort=rating/order=desc/page/378/")
        if check_html:
            check_items = parse_eneyida_page(check_html)
            print(f"  Page 378: {len(check_items)} items (should be 0 if last is {last_page})")
    
    avg_items = sum(len(v) for v in results.values()) / len(results)
    print(f"\n  Avg items/page: {avg_items:.0f}")
    print(f"  EST. TOTAL: ~{int(last_page * avg_items)} items ({last_page} pages)")


def main():
    import sys
    
    if "--quick" in sys.argv:
        # Quick test: just verify parsing works
        print("Quick verification...")
        
        print("\n--- Uakino (page 1) ---")
        html = curl_fetch("https://uakino.best/filmy/")
        if html:
            items = parse_uakino_page(html)
            print(f"  {len(items)} items")
            for i, item in enumerate(items[:3]):
                print(f"  {i+1}. {item['title'][:50]}")
        
        print("\n--- Eneyida (page 1) ---")
        html = curl_fetch("https://eneyida.tv/f/sort=rating/order=desc/")
        if html:
            items = parse_eneyida_page(html)
            print(f"  {len(items)} items")
            for i, item in enumerate(items[:3]):
                print(f"  {i+1}. {item['title'][:40]} ({item['year']}) EN={item['en_title'][:30]}")
        
        print("\n✅ Quick verification done")
        return
    
    scan_uakino()
    scan_eneyida()
    
    print("\n" + "=" * 60)
    print("PLAN SUMMARY")
    print("=" * 60)
    print("""
DATA PER ITEM:
  Uakino: title, url, poster, quality, IMDB rating
  Eneyida: title, en_title, url, poster, year, rating, quality

STORAGE:
  ~3 MB total in Room (both providers combined)

BUILD TIME:
  ~3-5 min at first launch (parallel requests)

SEARCH TIME:
  ~0 ms (local SQLite query)
  
COVERAGE:
  Uakino: ~15,000 items (446 film + 167 series pages)
  Eneyida: ~9,000 items (377 pages)
  
REFRESH:
  Periodic (or on manual trigger)
    """)
    print("=" * 60)


if __name__ == "__main__":
    main()
