import requests
from bs4 import BeautifulSoup

url = "https://uakino.best/seriesss/comedy_series/5642-teorya-velikogo-vibuhu-1-sezon.html"
headers = {"User-Agent": "Mozilla/5.0"}
r = requests.get(url, headers=headers)
soup = BeautifulSoup(r.text, 'html.parser')

print("--- ALL IMAGES ---")
for img in soup.find_all('img'):
    print(f"src: {img.get('src')}, data-src: {img.get('data-src')}, alt: {img.get('alt')}")

print("\n--- OG:IMAGE ---")
og = soup.find('meta', property='og:image')
if og:
    print(og.get('content'))
