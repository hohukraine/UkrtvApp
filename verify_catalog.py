#!/usr/bin/env python3
"""
Verify catalog pages structure for Uakino and Eneyida providers.
Extracts titles, URLs, years, types from listing pages.
"""

import urllib.request
import urllib.error
import json
import time
import re
import sys
from typing import Optional
from html.parser import HTMLParser

UA = "Mozilla/5.0 (Linux; Android 10; KFTRWI) AppleWebKit/537.36"

def fetch(url: str, max_retries=2) -> Optional[str]:
    for attempt in range(max_retries + 1):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=15) as resp:
                return resp.read().decode("utf-8", errors="replace")
        except Exception as e:
            if attempt < max_retries:
                time.sleep(1)
            else:
                print(f"  ERROR: {e}")
                return None


class UakinoPageParser:
    """Parse Uakino category page (/filmy/, /seriesss/)"""

    @staticmethod
    def parse(html: str, source: str) -> list[dict]:
        items = []
        
        # Find movie cards - patterns from DleParser
        # Each card is typically a <div> with class containing movie-item, short-item, etc.
        card_patterns = [
            r'<div[^>]*class="[^"]*(?:movie-item|short-item|shortstory)[^"]*"[^>]*>(.*?)</div>\s*</div>',
            r'<div[^>]*class="[^"]*(?:movie-item|short-item|shortstory)[^"]*"[^>]*>(.*?)</article>',
        ]
        
        # Find all .html links first to get individual items
        # Pattern: look for structured cards
        cards = re.findall(
            r'<div[^>]*class="[^"]*(?:movie-item|short-item)[^"]*"[^>]*>'
            r'(.*?)'
            r'</div>\s*</div>',
            html, re.DOTALL
        )
        
        if not cards:
            # Try alternative pattern - find items by link + image structure
            # Each item has: <a href="...title.html"> with poster image inside
            blocks = re.findall(
                r'(?:<div[^>]*class="[^"]*short[^"]*"[^>]*>|)'
                r'(.*?<a[^>]*href="([^"]+\.html)"[^>]*>.*?</a>.*?)'
                r'(?:</div>|</article>)',
                html, re.DOTALL
            )
            # Simpler: just find all .html links in the content area
            links = re.findall(r'<a[^>]*href="(https?://[^"]+\.html)"[^>]*>(.*?)</a>', html)
            seen = set()
            for url, title_text in links:
                if url in seen:
                    continue
                seen.add(url)
                title = re.sub(r'<[^>]+>', '', title_text).strip()
                if len(title) < 2 or '/page/' in url:
                    continue
                poster = ""
                img_match = re.search(r'<img[^>]*src="([^"]*)"[^>]*>', title_text)
                if img_match:
                    poster = img_match.group(1)
                items.append({
                    "title": title,
                    "url": url,
                    "poster": poster,
                    "source": source,
                })
            return items

        for card in cards:
            link = re.search(r'href="([^"]+\.html)"', card)
            if not link:
                continue
            url = link.group(1)
            
            title_el = re.search(r'class="[^"]*(?:movie-title|short-title)[^"]*"[^>]*>\s*(.*?)\s*</', card, re.DOTALL)
            title = title_el.group(1) if title_el else ""
            title = re.sub(r'<[^>]+>', '', title).strip()
            
            poster = ""
            poster_match = re.search(r'<img[^>]*src="([^"]+)"[^>]*>', card)
            if not poster_match:
                poster_match = re.search(r'data-src="([^"]+)"', card)
            if poster_match:
                poster = poster_match.group(1)
            
            if title and len(title) > 1:
                items.append({
                    "title": title,
                    "url": url,
                    "poster": poster,
                    "source": source,
                })
        
        return items


class EneyidaPageParser:
    """Parse Eneyida filter page (/f/sort=rating/order=desc/)"""

    @staticmethod
    def parse(html: str, source: str) -> list[dict]:
        items = []
        
        # Eneyida uses article.short structure
        articles = re.findall(
            r'<article[^>]*class="[^"]*short[^"]*"[^>]*>(.*?)</article>',
            html, re.DOTALL
        )
        
        if articles:
            for art in articles:
                link = re.search(r'<a[^>]*href="([^"]+)"[^>]*>', art)
                if not link:
                    continue
                url = link.group(1)
                if not url.startswith("http"):
                    url = "https://eneyida.tv" + url
                
                title = ""
                title_a = re.search(r'<a[^>]*class="[^"]*short_title[^"]*"[^>]*>(.*?)</a>', art, re.DOTALL)
                if title_a:
                    title = re.sub(r'<[^>]+>', '', title_a.group(1)).strip()
                
                poster = ""
                poster_match = re.search(r'<img[^>]*src="([^"]+)"[^>]*>', art)
                if poster_match:
                    poster = poster_match.group(1)
                if not poster_match:
                    poster_match = re.search(r'data-src="([^"]+)"', art)
                    if poster_match:
                        poster = poster_match.group(1)
                
                if title and len(title) > 1:
                    items.append({
                        "title": title,
                        "url": url,
                        "poster": poster,
                        "source": source,
                    })
            return items
        
        # Fallback: look for items in the content area
        # Eneyida filter page has structure:
        # <div class="..."> with items containing img + a
        blocks = re.findall(
            r'<div[^>]*class="[^"]*(?:short|item|poster)[^"]*"[^>]*>'
            r'(.*?<a[^>]*href="([^"]+)"[^>]*>.*?</a>.*?)'
            r'</div>',
            html, re.DOTALL
        )
        
        links = re.findall(r'href="(/[^"]+\.html)"[^>]*>\s*([^<]{2,}?)\s*</a>', html)
        seen = set()
        for url, title in links:
            if url in seen:
                continue
            seen.add(url)
            title = title.strip()
            if len(title) < 2:
                continue
            items.append({
                "title": title,
                "url": "https://eneyida.tv" + url,
                "poster": "",
                "source": source,
            })
        
        return items


def verify_uakino():
    print("=" * 60)
    print("UAKINO CATALOG VERIFICATION")
    print("=" * 60)
    
    categories = {
        "/filmy/": "movies",
        "/seriesss/": "series",
        "/cartoon/": "cartoons",
    }
    
    total_items = 0
    total_pages = 0
    
    for path, label in categories.items():
        url = f"https://uakino.best{path}"
        print(f"\n--- Category: {label} ({url}) ---")
        html = fetch(url)
        if not html:
            print(f"  FAILED to fetch {url}")
            continue
        
        # Find max page number
        pages = re.findall(r'href="[^"]*/page/(\d+)/"', html)
        max_page = max(int(p) for p in pages) if pages else 0
        print(f"  Total pages: {max_page}")
        
        # Parse page 1
        items = UakinoPageParser.parse(html, label)
        print(f"  Items on page 1: {len(items)}")
        estimated_total = max_page * max(len(items), 20)
        print(f"  Estimated total: ~{estimated_total}")
        
        if items:
            print(f"  Sample items (first 5):")
            for i, item in enumerate(items[:5]):
                title_clean = item["title"][:60]
                url_short = item["url"][:50]
                print(f"    {i+1}. {title_clean} | {url_short}")
        
        total_items += estimated_total
        total_pages += max_page
    
    print(f"\n  TOTAL: ~{total_items} items across ~{total_pages} pages")


def verify_eneyida():
    print("\n" + "=" * 60)
    print("ENEYIDA CATALOG VERIFICATION")
    print("=" * 60)
    
    # Check first 3 pages
    pages_to_check = [1, 2, 3, 100, 377]
    
    total_items = 0
    for page_num in pages_to_check:
        if page_num == 1:
            url = "https://eneyida.tv/f/sort=rating/order=desc/"
        else:
            url = f"https://eneyida.tv/f/sort=rating/order=desc/page/{page_num}/"
        
        print(f"\n--- Page {page_num} ({url}) ---")
        html = fetch(url)
        if not html:
            print(f"  FAILED")
            continue
        
        items = EneyidaPageParser.parse(html, "eneyida")
        print(f"  Items found: {len(items)}")
        total_items += len(items)
        
        if items:
            print(f"  Sample items (first 5):")
            for i, item in enumerate(items[:5]):
                title_clean = item["title"][:60]
                url_short = item["url"][:50]
                print(f"    {i+1}. {title_clean} | {url_short}")
    
    print(f"\n  Total items across {len(pages_to_check)} pages: {total_items}")
    print(f"  Estimated total (377 pages × 24 items): ~9048")


def verify_uakino_xml_api():
    """Try the XML API on various domains"""
    print("\n" + "=" * 60)
    print("UAKINO XML API TEST")
    print("=" * 60)
    
    endpoints = [
        "https://uakino.best/index.php?category=filmi&box_mac=11223344",
        "https://uakino.club/index.php?category=filmi&box_mac=11223344",
        "https://uakino.info/index.php?category=filmi&box_mac=11223344",
    ]
    
    for url in endpoints:
        print(f"\n  Testing: {url}")
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=10) as resp:
                data = resp.read()
                content_type = resp.headers.get("Content-Type", "")
                is_xml = b"<?xml" in data[:100] or b"<channel" in data[:1000] or "xml" in content_type
                print(f"    Status: {resp.status}")
                print(f"    Content-Type: {content_type}")
                print(f"    Size: {len(data)} bytes")
                print(f"    Is XML: {is_xml}")
                if not is_xml:
                    preview = data[:200].decode("utf-8", errors="replace")
                    print(f"    Preview: {preview[:100]}")
        except urllib.error.HTTPError as e:
            print(f"    HTTP {e.code}: {e.reason}")
        except Exception as e:
            print(f"    Error: {e}")


def check_pagination_patterns():
    """Verify pagination patterns for page 2, 100, etc."""
    print("\n" + "=" * 60)
    print("PAGINATION PATTERN VERIFICATION")
    print("=" * 60)
    
    # Uakino - check page 2
    for path, label in [("/filmy/page/2/", "Uakino filmy p2"), 
                         ("/seriesss/page/2/", "Uakino series p2")]:
        url = f"https://uakino.best{path}"
        print(f"\n  {label}: {url}")
        html = fetch(url)
        if html:
            items = UakinoPageParser.parse(html, label)
            print(f"    Items: {len(items)}")
        else:
            print(f"    FAILED")
    
    # Eneyida - check page 2 and 100
    for page in [2, 100]:
        url = f"https://eneyida.tv/f/sort=rating/order=desc/page/{page}/"
        print(f"\n  Eneyida page {page}: {url}")
        html = fetch(url)
        if html:
            items = EneyidaPageParser.parse(html, label)
            print(f"    Items: {len(items)}")
        else:
            print(f"    FAILED")


if __name__ == "__main__":
    verify_uakino()
    verify_eneyida()
    verify_uakino_xml_api()
    check_pagination_patterns()
    
    print("\n" + "=" * 60)
    print("DONE")
    print("=" * 60)
