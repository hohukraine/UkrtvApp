import requests
from bs4 import BeautifulSoup
import json

url = "https://uakino.best/seriesss/comedy_series/5642-teorya-velikogo-vibuhu-1-sezon.html"
headers = {"User-Agent": "Mozilla/5.0"}
r = requests.get(url, headers=headers)
soup = BeautifulSoup(r.text, 'html.parser')

scripts = soup.find_all('script', type='application/ld+json')
for s in scripts:
    print("--- LD+JSON ---")
    print(s.string)

ajax = soup.find(class_='playlists-ajax')
if ajax:
    print("--- AJAX ---")
    print(ajax.attrs)
