import requests
from bs4 import BeautifulSoup
import re
from urllib.parse import quote

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://uakino.best/"
}

# Test search URL
query = "друзі"
url = f"https://uakino.best/index.php?do=search&subaction=search&q={quote(query)}"
print(f"URL: {url}")

r = requests.get(url, headers=headers, timeout=15)
print(f"Status: {r.status_code}")
print(f"Final URL: {r.url}")
print(f"Length: {len(r.text)}")

soup = BeautifulSoup(r.text, "html.parser")

# Check what's on the page
print("\n=== Page title ===")
print(soup.title.string if soup.title else "No title")

print("\n=== .movie-item count ===")
print(f"Found: {len(soup.select('.movie-item'))}")

print("\n=== First few movie titles ===")
for i, item in enumerate(soup.select(".movie-item")[:5]):
    title = item.select_one("a.movie-title")
    if title:
        print(f"{i+1}. {title.get_text(strip=True)}")

# Check if there's a search results section
print("\n=== Searching for search-specific elements ===")
for selector in [".search-results", "#search-results", ".search-result", ".result-item"]:
    count = len(soup.select(selector))
    if count > 0:
        print(f"{selector}: {count}")

# Look for any h1/h2 that might indicate search results
print("\n=== Headings ===")
for h in soup.find_all(["h1", "h2"])[:10]:
    print(h.get_text(strip=True))

# Check the URL - maybe it redirects or uses different params
print(f"\n=== Request URL vs Final URL ===")
print(f"Requested: {url}")
print(f"Final: {r.url}")

# Try alternative search URLs
print("\n=== Trying alternative search URLs ===")
alt_urls = [
    f"https://uakino.best/index.php?do=search&q={quote(query)}",
    f"https://uakino.best/search?q={quote(query)}",
    f"https://uakino.best/ua/index.php?do=search&subaction=search&q={quote(query)}",
]
for alt in alt_urls:
    try:
        r2 = requests.get(alt, headers=headers, timeout=10, allow_redirects=False)
        print(f"{alt}")
        print(f"  Status: {r2.status_code}, Length: {len(r2.text)}")
        if r2.status_code in [301, 302]:
            print(f"  Redirect: {r2.headers.get('Location')}")
    except Exception as e:
        print(f"  Error: {e}")
