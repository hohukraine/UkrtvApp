import requests
from bs4 import BeautifulSoup
import urllib.parse

base_url = "https://uakino.best/"
query = "Дюна"
encoded_query = urllib.parse.quote(query)
url = f"{base_url}/index.php?do=search&subaction=search&story={encoded_query}"

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Referer": base_url
}

r = requests.get(url, headers=headers, timeout=15)
soup = BeautifulSoup(r.text, 'html.parser')

containers = soup.select(".movie-item, .short-item, .shortstory, .th-item")
for i, el in enumerate(containers[:3]):
    title_el = el.select_one("a.movie-title, .short-title a, .shortstorytitle a, .th-title a")
    if title_el:
        print(f"Title: {title_el.get_text(strip=True)} | URL: {title_el['href']}")
