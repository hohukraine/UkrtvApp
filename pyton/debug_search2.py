import requests
from bs4 import BeautifulSoup
from urllib.parse import quote

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://uakino.best/",
    "Content-Type": "application/x-www-form-urlencoded",
}

query = "друзі"

# Try POST with story parameter (from the form)
print("=== POST to /ua/ with story ===")
r = requests.post("https://uakino.best/ua/", data={
    "do": "search",
    "subaction": "search",
    "story": query,
}, headers=headers, timeout=15, allow_redirects=True)
print(f"Status: {r.status_code}, Final URL: {r.url}")
print(f"Title: {BeautifulSoup(r.text, 'html.parser').title.string if BeautifulSoup(r.text, 'html.parser').title else 'None'}")
items = BeautifulSoup(r.text, "html.parser").select(".movie-item")
print(f"movie-item count: {len(items)}")
for i, item in enumerate(items[:5]):
    t = item.select_one("a.movie-title")
    if t:
        print(f"  {i+1}. {t.get_text(strip=True)}")

# Also try GET with story parameter
print("\n=== GET with story parameter ===")
r2 = requests.get(f"https://uakino.best/index.php?do=search&subaction=search&story={quote(query)}", headers=headers, timeout=15)
print(f"Status: {r2.status_code}")
print(f"Title: {BeautifulSoup(r2.text, 'html.parser').title.string if BeautifulSoup(r2.text, 'html.parser').title else 'None'}")
items2 = BeautifulSoup(r2.text, "html.parser").select(".movie-item")
print(f"movie-item count: {len(items2)}")
for i, item in enumerate(items2[:5]):
    t = item.select_one("a.movie-title")
    if t:
        print(f"  {i+1}. {t.get_text(strip=True)}")

# Try the original URL but with different param names
print("\n=== GET with q param (original) ===")
r3 = requests.get(f"https://uakino.best/index.php?do=search&subaction=search&q={quote(query)}", headers=headers, timeout=15)
print(f"Status: {r3.status_code}")
print(f"Title: {BeautifulSoup(r3.text, 'html.parser').title.string if BeautifulSoup(r3.text, 'html.parser').title else 'None'}")
items3 = BeautifulSoup(r3.text, "html.parser").select(".movie-item")
print(f"movie-item count: {len(items3)}")
for i, item in enumerate(items3[:5]):
    t = item.select_one("a.movie-title")
    if t:
        print(f"  {i+1}. {t.get_text(strip=True)}")
