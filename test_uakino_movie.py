import requests
from bs4 import BeautifulSoup
import re

def test_uakino_movie():
    # Example movie
    url = "https://uakino.best/filmy/documentaries/34467-materynskyi-instynkt-sprava-teilor-parker.html"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer": "https://uakino.best/"
    }

    print(f"Fetching movie page: {url}")
    try:
        response = requests.get(url, headers=headers, timeout=15)
        response.raise_for_status()
    except Exception as e:
        print(f"FAILED: Connection error: {e}")
        return

    soup = BeautifulSoup(response.text, 'html.parser')

    # Check for playlists-ajax (some movies use it for different voiceovers or quality)
    playlist_div = soup.select_one(".playlists-ajax")
    if playlist_div:
        print("Movie uses AJAX playlist system.")
        return

    # Check for iframes
    iframes = soup.select("iframe")
    print(f"Found {len(iframes)} iframes.")
    for i, iframe in enumerate(iframes):
        src = iframe.get('data-src') or iframe.get('src')
        print(f"Iframe {i+1} src: {src}")

        if src and ("ashdi" in src or "hdvb" in src or "voidboost" in src):
            print(f"  -> Potential video source found!")

    # Check for Playerjs in scripts
    scripts = soup.find_all("script")
    for s in scripts:
        if s.string and ("Playerjs" in s.string or "file:" in s.string):
            print("Found potential Playerjs script in movie page.")
            file_match = re.search(r"file\s*:\s*['\"](.*?)['\"]", s.string)
            if file_match:
                print(f"  File: {file_match.group(1)}")

if __name__ == "__main__":
    test_uakino_movie.test_uakino_movie() if hasattr(test_uakino_movie, 'test_uakino_movie') else test_uakino_movie()
