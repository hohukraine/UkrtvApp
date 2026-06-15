import requests
from bs4 import BeautifulSoup
import re

def clean_title(title):
    clean = title.replace("+", " ").replace("_", " ")
    clean = re.sub(r"\+\d+", "", clean)
    clean = re.sub(r"\b(FHD|HD|SD|720p|1080p|2160p|4K|HDR)\b", "", clean, flags=re.I)
    return clean.strip()

def test_parser(name, url):
    print(f"\n--- Testing {name} Parser on {url} ---")
    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"}
    try:
        resp = requests.get(url, headers=headers, timeout=15)
        soup = BeautifulSoup(resp.text, 'html.parser')

        is_full_page = bool(soup.select("nav, .header, .footer"))
        print(f"Detected as Full Page: {is_full_page}")

        container = soup.select_one("#dle-content, .items, .gridder, main") or soup
        items = container.select(".movie-item, .short-item, .th-item, .story, .short, .shortstory, a[href*='.html']")

        results = []
        for element in items:
            # Navigation exclusion
            is_nav = False
            curr = element
            while curr and curr.name != '[document]':
                if curr.name == 'nav' or any(c in str(curr.get('class','')).lower() for c in ['menu', 'sidebar', 'header', 'footer']):
                    is_nav = True
                    break
                curr = curr.parent
            if is_nav: continue

            title_el = element if element.name == 'a' else element.select_one(".movie-title, .th-title, h2, a[href*='.html']")
            if not title_el: continue

            raw_title = title_el.get_text(strip=True)
            if len(raw_title) < 3: continue

            # Category filter
            if any(k in raw_title.upper() for k in ["СВЯТКОВІ", "НЕТФЛІКС", "NETFLIX", "ПІДБІРКИ"]):
                continue

            page_url = title_el.get('href') or (title_el.find('a')['href'] if title_el.find('a') else None)
            if not page_url or ".html" not in page_url: continue

            poster_el = element.select_one("img[src*='uploads'], img")
            poster = poster_el.get('src') or poster_el.get('data-src') if poster_el else None

            if is_full_page and not poster:
                continue

            results.append({"title": clean_title(raw_title), "link": page_url})

        print(f"Parsed {len(results)} valid movies")
        # Deduplicate
        seen = set()
        unique_results = []
        for r in results:
            if r['link'] not in seen:
                unique_results.append(r)
                seen.add(r['link'])

        for r in unique_results[:10]:
            print(f"  - {r['title']} ({r['link']})")

    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    test_parser("Uakino", "https://uakino.best/ua/")
    test_parser("Eneyida", "https://eneyida.tv/")
