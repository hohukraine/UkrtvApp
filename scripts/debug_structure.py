import requests
from bs4 import BeautifulSoup
import re

url = "https://uakino.best/seriesss/fantastic_series/33511-zzovni-4-sezon.html"
resp = requests.get(url, headers={'User-Agent': 'Mozilla/5.0'})
soup = BeautifulSoup(resp.text, 'html.parser')

print("--- SEASONS BLOCK DETECTION ---")
for a in soup.select("a"):
    if "сезон" in a.text.lower():
        parents = [p.get('class', '') for p in a.parents]
        print(f"Text: '{a.text.strip()}', Href: {a['href']}, Parents: {parents}")

print("\n--- AJAX PARAMS ---")
news_id = soup.find('input', {'name': 'news_id'})
if news_id: print(f"news_id: {news_id['value']}")

script_vars = re.findall(r'var\s+([^=]+)\s*=\s*[\'"]([^\'"]+)[\'"]', resp.text)
for var, val in script_vars:
    if "hash" in var or "id" in var:
        print(f"Script Var: {var} = {val}")
