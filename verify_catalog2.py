#!/usr/bin/env python3
"""
Verify catalog pages structure for Uakino and Eneyida providers.
Uses curl for Uakino (SSL issues with Python urllib).
"""

import subprocess
import json
import time
import re
import sys

UA = "Mozilla/5.0 (Linux; Android 10; KFTRWI) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Safari/537.36"

def curl_fetch(url: str, max_retries=2):
    for attempt in range(max_retries + 1):
        try:
            result = subprocess.run(
                ["curl", "-s", "-A", UA, "-L", url],
                capture_output=True, text=True, timeout=15
            )
            if result.returncode == 0 and len(result.stdout) > 500:
                return result.stdout
            print(f"  curl returned {result.returncode}, size={len(result.stdout)}")
            if attempt < max_retries:
                time.sleep(1)
        except subprocess.TimeoutExpired:
            print(f"  Timeout")
            if attempt < max_retries:
                time.sleep(1)
        except Exception as e:
            print(f"  Error: {e}")
            if attempt < max_retries:
                time.sleep(1)
    return None


class UakinoParser:
    """Parse Uakino category pages"""
    
    @staticmethod
    def parse(html: str) -> list[dict]:
        items = []
        
        # Find all short-story divs
        stories = re.findall(
            r'<div[^>]*class="[^"]*(?:short-story|shortstory|short_item|movie-item)[^"]*"[^>]*>'
            r'(.*?)</div>\s*</div>',
            html, re.DOTALL
        )
        
        # If no structured divs, find all .html links in main content area
        if not stories:
            # Remove header/footer/nav to isolate content
            main = html
            # Remove scripts, styles, nav, header, footer
            main = re.sub(r'<(?:script|style|nav|header|footer|aside)[^>]*>.*?</\1>', '', main, 
                         flags=re.DOTALL | re.IGNORECASE)
            
            links = re.findall(
                r'<a[^>]*href="(https?://uakino\.best/[^"]+\.html)"[^>]*>(.*?)</a>',
                main, re.DOTALL
            )
            seen = set()
            for url, title_html in links:
                if url in seen:
                    continue
                seen.add(url)
                title = re.sub(r'<[^>]+>', '', title_html).strip()
                if len(title) < 2:
                    continue
                # Get poster
                poster = ""
                img = re.search(r'<img[^>]*src="([^"]+)"', title_html)
                if img:
                    poster = img.group(1)
                items.append({"title": title, "url": url, "poster": poster})
            return items
            
        for story in stories:
            link = re.search(r'href="(https?://[^"]+\.html)"', story)
            if not link:
                continue
            url = link.group(1)
            
            title_el = re.search(
                r'class="[^"]*(?:movie-title|short-title|story-title)[^"]*"[^>]*>\s*(.*?)\s*</',
                story, re.DOTALL
            )
            title = ""
            if title_el:
                title = re.sub(r'<[^>]+>', '', title_el.group(1)).strip()
            if not title:
                # Try getting it from the link text
                title = re.sub(r'<[^>]+>', '', story).strip()
                title = title.split('\n')[0].strip()[:100]
            
            poster = ""
            for attr in ['data-src', 'src', 'data-lazy-src']:
                m = re.search(rf'{attr}="([^"]+\.(?:webp|jpg|jpeg|png))"', story)
                if m:
                    poster = m.group(1)
                    break
            
            if title and len(title) > 1:
                items.append({"title": title.strip(), "url": url, "poster": poster})
        
        return items


class EneyidaParser:
    """Parse Eneyida filter pages"""
    
    @staticmethod
    def parse(html: str) -> list[dict]:
        items = []
        
        # Find all <article class="short ..."> blocks
        articles = re.findall(
            r'<article[^>]*class="[^"]*short[^"]*"[^>]*>(.*?)</article>',
            html, re.DOTALL
        )
        
        for art in articles:
            link = re.search(r'<a[^>]*href="(/[^"]+\.html)"[^>]*>', art)
            if not link:
                continue
            url = "https://eneyida.tv" + link.group(1)
            
            title = ""
            ta = re.search(
                r'<a[^>]*class="[^"]*short_title[^"]*"[^>]*>(.*?)</a>',
                art, re.DOTALL
            )
            if ta:
                title = re.sub(r'<[^>]+>', '', ta.group(1)).strip()
            if not title:
                title_a = re.search(r'<a[^>]*href="[^"]+"[^>]*>([^<]{2,})</a>', art)
                if title_a:
                    title = title_a.group(1).strip()
            
            poster = ""
            img = re.search(r'<img[^>]*src="([^"]+)"[^>]*>', art)
            if img:
                poster = img.group(1)
            if not poster:
                img = re.search(r'data-src="([^"]+)"', art)
                if img:
                    poster = img.group(1)
            
            if title and len(title) > 1:
                items.append({
                    "title": title.strip(),
                    "url": url,
                    "poster": poster,
                })
        
        return items


def verify_uakino():
    print("=" * 60)
    print("UAKINO CATALOG (via curl)")
    print("=" * 60)
    
    categories = [
        ("/filmy/", "movies"),
        ("/seriesss/", "series"),
        # ("/cartoon/", "cartoons"),
    ]
    
    total_est = 0
    
    for path, label in categories:
        url = f"https://uakino.best{path}"
        print(f"\n  [{label}] {url}")
        html = curl_fetch(url)
        if not html:
            print("    FAILED")
            continue
        
        # Find max page number
        pages = re.findall(r'href="/(?:filmy|seriesss)/page/(\d+)/"', html)
        if not pages:
            pages = re.findall(r'page/(\d+)/[^>]*>', html)
        max_page = max((int(p) for p in pages), default=0)
        print(f"    Max page: {max_page}")
        
        items = UakinoParser.parse(html)
        print(f"    Items on page 1: {len(items)}")
        estimated = max_page * max(len(items), 24)
        print(f"    Estimated total: ~{estimated}")
        total_est += estimated
        
        if items:
            print(f"    Samples:")
            for i, item in enumerate(items[:5]):
                t = item["title"][:55]
                u = item["url"][-40:]
                print(f"      {i+1}. {t} ...{u}")
            
            # Check for title patterns
            print(f"    Title patterns:")
            for item in items[:3]:
                t = item["title"]
                has_en = " / " in t or " • " in t
                has_year = bool(re.search(r'\(\d{4}\)', t))
                print(f"      '{t[:60]}' — EN={has_en}, year={has_year}")
    
    print(f"\n  ESTIMATED TOTAL: ~{total_est} items")


def verify_eneyida():
    print("\n" + "=" * 60)
    print("ENEYIDA CATALOG")
    print("=" * 60)
    
    # Scan pages 1, 2, 3, 50, 100, 200, 300, 377
    pages = [1, 2, 3, 50, 100, 200, 300, 377]
    
    for p in pages:
        url = "https://eneyida.tv/f/sort=rating/order=desc/"
        if p > 1:
            url += f"page/{p}/"
        
        html = curl_fetch(url)
        if not html:
            print(f"  Page {p}: FAILED")
            continue
        
        items = EneyidaParser.parse(html)
        print(f"  Page {p}: {len(items)} items")
        
        if p <= 3 and items:
            for i, item in enumerate(items[:3]):
                t = item["title"][:60]
                has_en = " / " in t or " • " in t
                print(f"    {i+1}. '{t}' EN={has_en}")
    
    print(f"\n  ESTIMATED: ~9048 items (377 pages × 24)")


def main():
    verify_uakino()
    verify_eneyida()
    
    print("\n" + "=" * 60)
    print("SUMMARY")
    print("=" * 60)
    print("""
Uakino:
  - Categories: /filmy/ (446 p), /seriesss/ (167 p), /cartoon/
  - ~24 items per page
  - Est. total: ~15,000 items
  - SSL issues with Python urllib, use OkHttp (Android)

Eneyida:
  - One large filter: /f/sort=rating/order=desc/ (377 p)
  - 24 items per page (last page: 3)
  - Est. total: ~9,048 items
  - Clean data, works well

Recommendation:
  Build local index for BOTH providers by crawling
  category pages in background. Store in Room.
  """)
    print("=" * 60)


if __name__ == "__main__":
    main()
