import requests
from bs4 import BeautifulSoup
import urllib.parse

base_url = "https://eneyida.tv/"
query = "Бетмен"
encoded_query = urllib.parse.quote(query)
url = f"{base_url}/index.php?do=search&subaction=search&story={encoded_query}"

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Referer": base_url
}

print(f"Searching for '{query}' at {url}")
r = requests.get(url, headers=headers, timeout=15)
soup = BeautifulSoup(r.text, 'html.parser')

container_sel = ".short, .short-item, .movie-item, .th-item, .shortstory, .movie-short, .short-t, .short-i"
containers = soup.select(container_sel)
print(f"Results found: {len(containers)}")

for i, el in enumerate(containers[:5]):
    title_el = el.select_one(".short_title, .short-title, a.short-title, .th-title, .shortstorytitle a, .movie-title a, .short-t a, h2 a")
    title = title_el.get_text(strip=True) if title_el else "NOT FOUND"
    print(f" {i+1}. {title}")
