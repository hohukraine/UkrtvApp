import requests
from bs4 import BeautifulSoup

url = "https://eneyida.tv/"
headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
}

r = requests.get(url, headers=headers, timeout=15)
soup = BeautifulSoup(r.text, 'html.parser')

container_sel = ".short, .short-item, .movie-item, .th-item, .shortstory, .movie-short, .short-t, .short-i"
containers = soup.select(container_sel)
print(f"Containers found with '{container_sel}': {len(containers)}")

title_sel = ".short_title, .short-title, a.short-title, .th-title, .shortstorytitle a, .movie-title a, .short-t a, h2 a"

for i, el in enumerate(containers[:5]):
    title_el = el.select_one(title_sel)
    title = title_el.get_text(strip=True) if title_el else "NOT FOUND"

    href = ""
    if title_el and title_el.name == 'a' and title_el.has_attr('href'):
        href = title_el['href']
    else:
        a_el = el.select_one('a')
        href = a_el['href'] if a_el else "NOT FOUND"

    img_el = el.select_one('img')
    img = img_el.get('data-src') or img_el.get('src') if img_el else "NOT FOUND"

    print(f"Item {i+1}:")
    print(f"  Title: {title}")
    print(f"  Link: {href}")
    print(f"  Image: {img}")
