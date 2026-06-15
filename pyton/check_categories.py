import requests
from bs4 import BeautifulSoup

def check_site(url, name):
    print(f"\nCategories for {name}:")
    headers = {"User-Agent": "Mozilla/5.0"}
    r = requests.get(url, headers=headers)
    soup = BeautifulSoup(r.text, 'html.parser')
    for a in soup.find_all('a', href=True):
        href = a['href']
        text = a.get_text(strip=True)
        if 'serial' in href.lower() or 'series' in href.lower() or 'anime' in href.lower():
            print(f" {text}: {href}")

check_site("https://eneyida.tv/", "Eneyida")
check_site("https://uakino.best/ua/", "Uakino")
