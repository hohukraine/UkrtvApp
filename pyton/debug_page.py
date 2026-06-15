import requests
from bs4 import BeautifulSoup
import re

url = "https://uakino.best/filmy/genre_drama/62-syayvo-yevropeyske-vidannya.html"
headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://uakino.best/"
}
r = requests.get(url, headers=headers, timeout=15)
print(f"Status: {r.status_code}")
print(f"Length: {len(r.text)}")

soup = BeautifulSoup(r.text, "html.parser")

# All iframes
print("\n=== Iframes ===")
for i, iframe in enumerate(soup.select("iframe")):
    print(f"{i}: {iframe.get('src', 'no src')}")

# go-watch
print("\n=== go-watch ===")
for i, el in enumerate(soup.select("i.go-watch")):
    print(f"{i}: data-link={el.get('data-link', 'none')}")

# m3u8 in page
print("\n=== m3u8 direct ===")
m3u8s = re.findall(r'https?://[^\s"\'\\]+\.m3u8[^\s"\'\\]*', r.text)
for m in m3u8s:
    print(m)

# Scripts with player/m3u8
print("\n=== Scripts with player/iframe/m3u8 ===")
for i, script in enumerate(soup.select("script")):
    txt = script.string or ""
    if "m3u8" in txt.lower() or "iframe" in txt.lower() or "player" in txt.lower():
        print(f"Script {i} (len={len(txt)}):")
        print(txt[:500])
        print("---")

# Any div with class containing player
print("\n=== Divs with player ===")
for div in soup.select("[class*='player']"):
    print(f"class={div.get('class')}, id={div.get('id')}")

# Look for any data attributes
print("\n=== data-* attributes ===")
for el in soup.find_all(attrs={"data-link": True}):
    print(f"{el.name}: data-link={el.get('data-link')}")
