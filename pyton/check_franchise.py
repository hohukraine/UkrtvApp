import requests
from bs4 import BeautifulSoup

url = "https://uakino.best/franchise/27558-teoriia-velykogo-vybukhu.html"
headers = {"User-Agent": "Mozilla/5.0"}
r = requests.get(url, headers=headers)
soup = BeautifulSoup(r.text, 'html.parser')

items = soup.select(".movie-item, .short-item, .shortstory, .th-item")
print(f"Found {len(items)} items")
for item in items[:5]:
    title = item.select_one("a.movie-title, .short-title a")
    if title:
        print(f"  Item: {title.text} -> {title.get('href')}")
