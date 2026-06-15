import requests
from bs4 import BeautifulSoup
import collections

url = "https://uakino.best/ua/"
headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
}

try:
    r = requests.get(url, headers=headers, timeout=15)
    r.raise_for_status()
    soup = BeautifulSoup(r.text, 'html.parser')

    classes = []
    for el in soup.find_all(class_=True):
        classes.extend(el['class'])

    counter = collections.Counter(classes)
    print("Top 50 classes found on Uakino:")
    for cls, count in counter.most_common(50):
        print(f"{cls}: {count}")

    # Check for specific Uakino selectors
    # .movie-item, .short-item, .shortstory, .th-item
    target_selectors = ['.movie-item', '.short-item', '.shortstory', '.th-item']
    for sel in target_selectors:
        found = soup.select(sel)
        print(f"Selector {sel}: found {len(found)}")

except Exception as e:
    print(f"Error: {e}")
