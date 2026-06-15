import requests
from bs4 import BeautifulSoup

def test_hdvb_resolution(url):
    print(f"\nResolving HDVB: {url}")
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer": "https://eneyida.tv/"
    }
    resp = requests.get(url, headers=headers)
    print(f"Status: {resp.status_code}")

    if ".m3u8" in resp.text:
        print("  [+] Found .m3u8 in JS/HTML!")
        # Basic extraction
        import re
        m3u8s = re.findall(r'https?://[^\s"\']+\.m3u8[^\s"\']*', resp.text)
        for m in m3u8s:
            print(f"    - {m}")
    else:
        print("  [-] No direct .m3u8 found.")
        if "iframe" in resp.text:
            print("  [!] Found nested iframes.")

if __name__ == "__main__":
    test_hdvb_resolution("https://hdvbua.pro/embed/12590/b0c42c552")
