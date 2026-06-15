import requests
from bs4 import BeautifulSoup
import sys

def test_uakino():
    url = "https://uakino.best/ua/"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept-Language": "uk-UA,uk;q=0.9,en-US;q=0.8,en;q=0.7"
    }

    print(f"Fetching {url}...")
    try:
        response = requests.get(url, headers=headers, timeout=15)
        response.raise_for_status()
    except Exception as e:
        print(f"FAILED: Connection error: {e}")
        return

    soup = BeautifulSoup(response.text, 'html.parser')

    # Try finding items
    items = soup.select(".movie-item, .short-item, .th-item")
    if not items:
        print("FAILED: No items found with selectors .movie-item, .short-item, .th-item")
        # Print a snippet of HTML to see what's there
        print("HTML Snippet:", response.text[:500])
        return

    print(f"Found {len(items)} items. Testing the first one...")
    item = items[0]

    # Extract data
    title_el = item.select_one(".movie-title, .th-title a")
    title = title_el.get_text(strip=True) if title_el else None

    link_el = title_el if title_el and title_el.name == 'a' else item.select_one("a")
    link = link_el['href'] if link_el and link_el.has_attr('href') else None

    poster_el = item.select_one(".movie-img img, .th-img img, img")
    poster = poster_el['src'] if poster_el and poster_el.has_attr('src') else None

    # Validation
    errors = []
    if not title: errors.append("Title not found")
    if not link: errors.append("Link not found")
    if not poster: errors.append("Poster not found")

    if errors:
        print(f"FAILED: {', '.join(errors)}")
        print(f"Item HTML: {item.prettify()[:500]}")
    else:
        print("PASSED")
        print(f"Title: {title}")
        print(f"Poster: {poster}")
        print(f"Link: {link}")

if __name__ == "__main__":
    test_uakino()
