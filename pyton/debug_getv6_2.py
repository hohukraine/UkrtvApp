import requests
import re
import base64
from bs4 import BeautifulSoup

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://uakino.best/"
}
url = "https://uakino.best/filmy/genre_drama/62-syayvo-yevropeyske-vidannya.html"
r = requests.get(url, headers=headers, timeout=15)
html = r.text

# Search for calls to mbn2r056csd3 - it might be called with a variable
print("=== Searching for mbn2r056csd3 usage ===")
occurrences = [m.start() for m in re.finditer(r'mbn2r056csd3', html)]
print(f"Found {len(occurrences)} occurrences at positions: {occurrences}")

for pos in occurrences:
    context = html[max(0, pos-100):pos+200]
    print(f"\nContext at {pos}:")
    print(context)

# Also search for "getv6"
print("\n=== Searching for getv6 ===")
getv6_positions = [m.start() for m in re.finditer(r'getv6', html)]
print(f"Found {len(getv6_positions)} occurrences")
for pos in getv6_positions:
    context = html[max(0, pos-100):pos+200]
    print(f"\nContext at {pos}:")
    print(context)

# Decode the base64
decoded = base64.b64decode("aHR0cHM6Ly9nZXQ2LmZ1bi9wb2ludC8/bWV0aG9kPXZpZGVvX2xpbmsyJnhsPQ==").decode('utf-8')
print(f"\nDecoded API base URL: {decoded}")
