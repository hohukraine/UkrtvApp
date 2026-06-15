import requests
from bs4 import BeautifulSoup
import re

url = "https://uakino.best/seriesss/comedy_series/5642-teorya-velikogo-vibuhu-1-sezon.html"
headers = {"User-Agent": "Mozilla/5.0"}
r = requests.get(url, headers=headers)
soup = BeautifulSoup(r.text, 'html.parser')

print("--- SEASONS LINKS ---")
# Look for blocks that might contain season links
for a in soup.find_all('a', href=True):
    txt = a.text.strip()
    if re.search(r'\d+\s*сезон', txt, re.I):
        print(f"{txt} -> {a.get('href')}")

print("\n--- POSSIBLE BLOCKS ---")
for div in soup.find_all('div', class_=re.compile(r'season|playlist|parts', re.I)):
    print(f"Div class={div.get('class')}")
    for a in div.find_all('a', href=True):
         print(f"  {a.text.strip()} -> {a.get('href')}")
