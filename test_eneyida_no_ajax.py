import requests
from bs4 import BeautifulSoup

def test_eneyida_no_ajax(url):
    print(f"\nTesting {url} for embedded playlist...")
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
    resp = requests.get(url, headers=headers)
    soup = BeautifulSoup(resp.text, 'html.parser')

    # 1. Search for data-file in any element
    items = soup.select("[data-file], [data-src], [data-url]")
    print(f"Found {len(items)} elements with data attributes.")
    for i in items[:10]:
        file = i.get('data-file') or i.get('data-src') or i.get('data-url')
        if file and ('.m3u8' in file or '/vod/' in file or 'ashdi' in file):
            print(f"  [!] MATCH: {i.get_text(strip=True)} -> {file}")

    # 2. Search for iframes
    iframes = soup.select("iframe")
    print(f"Found {len(iframes)} iframes.")
    for f in iframes:
        src = f.get('src') or f.get('data-src')
        print(f"  Iframe: {src}")

if __name__ == "__main__":
    test_eneyida_no_ajax("https://eneyida.tv/10053-influensery.html")
    test_eneyida_no_ajax("https://eneyida.tv/7026-zzovni.html")
