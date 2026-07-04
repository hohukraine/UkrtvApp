import requests
from bs4 import BeautifulSoup

url = "https://uakino.best/seriesss/fantastic_series/33511-zzovni-4-sezon.html"
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
}
resp = requests.get(url, headers=headers)
soup = BeautifulSoup(resp.text, 'html.parser')

print("--- Scripts ---")
for s in soup.select('script'):
    if s.get('src'):
        print(f"SRC: {s['src']}")
    elif "ajax" in s.text or "playlist" in s.text or "news_id" in s.text:
        print(f"INLINE SCRIPT: {s.text[:500]}...")

print("\n--- Playlists ---")
for el in soup.select('[data-file], [data-url], [data-news_id], [data-id]'):
    print(f"Tag: {el.name}, Class: {el.get('class')}, Data: {el.attrs}")
