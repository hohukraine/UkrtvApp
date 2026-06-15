import requests
from bs4 import BeautifulSoup

url = "https://uakino.best/franchise/27558-teoriia-velykogo-vybukhu.html"
headers = {"User-Agent": "Mozilla/5.0"}
r = requests.get(url, headers=headers)
soup = BeautifulSoup(r.text, 'html.parser')

print(f"Title: {soup.title.text if soup.title else 'No title'}")
for a in soup.find_all('a', href=True):
    href = a.get('href')
    if '/seriesss/' in href or '/filmy/' in href:
        print(f"Link: {a.text.strip()} -> {href}")
