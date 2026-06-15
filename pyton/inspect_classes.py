import requests
from bs4 import BeautifulSoup
import collections

url = "https://eneyida.tv/"
headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
}

r = requests.get(url, headers=headers, timeout=15)
soup = BeautifulSoup(r.text, 'html.parser')

classes = []
for el in soup.find_all(class_=True):
    classes.extend(el['class'])

counter = collections.Counter(classes)
print("Top 50 classes found:")
for cls, count in counter.most_common(50):
    print(f"{cls}: {count}")

# Print sample of titles/links
print("\nSample links with text:")
for a in soup.find_all('a', href=True)[:20]:
    print(f"Text: {a.get_text(strip=True)[:50]} | Href: {a['href']}")
