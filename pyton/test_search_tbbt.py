import requests
from bs4 import BeautifulSoup
from urllib.parse import quote

query = "Теорія великого вибуху"
url = f"https://uakino.best/index.php?do=search&subaction=search&story={quote(query)}"
headers = {"User-Agent": "Mozilla/5.0", "Referer": "https://uakino.best/"}

# Uakino search usually needs a POST
data = {
    "do": "search",
    "subaction": "search",
    "story": query,
    "search_start": 0,
    "full_search": 0,
    "result_from": 1
}

r = requests.post("https://uakino.best/index.php?do=search", headers=headers, data=data)
soup = BeautifulSoup(r.text, "html.parser")

print(f"Results for '{query}':")
for i, item in enumerate(soup.select(".movie-item, .short-item, .shortstory, .th-item")):
    title_el = item.select_one("a.movie-title, .short-title a, .shortstorytitle a, .th-title a")
    if title_el:
        title = title_el.get_text(strip=True)
        link = title_el.get("href")
        print(f"{i+1}. {title} ({link})")

if not soup.select(".movie-item, .short-item, .shortstory, .th-item"):
    print("No results found.")
    # Maybe try GET
    r2 = requests.get(url, headers=headers)
    soup2 = BeautifulSoup(r2.text, "html.parser")
    for i, item in enumerate(soup2.select(".movie-item, .short-item, .shortstory, .th-item")):
        title_el = item.select_one("a.movie-title, .short-title a, .shortstorytitle a, .th-title a")
        if title_el:
            print(f"GET {i+1}. {title_el.get_text(strip=True)} ({title_el.get('href')})")
