import requests
from bs4 import BeautifulSoup
import re
import json

def test_details(url):
    print(f"\nTesting details for: {url}")
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer": "https://uakino.best/"
    }

    r = requests.get(url, headers=headers, timeout=15)
    html = r.text
    soup = BeautifulSoup(html, 'html.parser')

    # AJAX info
    ajax_div = soup.select_one(".playlists-ajax")
    if ajax_div:
        news_id = ajax_div.get('data-news_id')
        xfname = ajax_div.get('data-xfname') or ('seria' if '/seriali/' in url or '/seriesss/' in url else 'playlist')
        print(f"Found AJAX playlist div: news_id={news_id}, xfname={xfname}")

        # Try to simulate AJAX request
        edittime_match = re.search(r"dle_edittime\s*=\\s*'(\d+)'", html)
        edittime = edittime_match.group(1) if edittime_match else "1710000000"

        ajax_url = f"https://uakino.best/engine/ajax/playlists.php?news_id={news_id}&xfield={xfname}&time={edittime}"
        print(f"Requesting AJAX: {ajax_url}")
        aj_headers = headers.copy()
        aj_headers["X-Requested-With"] = "XMLHttpRequest"
        aj_r = requests.get(ajax_url, headers=aj_headers, timeout=10)
        try:
            aj_json = aj_r.json()
            if aj_json.get('success'):
                resp_html = aj_json.get('response')
                aj_soup = BeautifulSoup(resp_html, 'html.parser')
                tabs = aj_soup.select(".playlists-lists li")
                print(f"AJAX Response: SUCCESS. Season tabs: {len(tabs)}")
                for tab in tabs:
                    print(f"  Tab: {tab.get_text(strip=True)} (id: {tab.get('data-id')})")

                # Check for playlist items
                items = aj_soup.select("li[data-file]")
                print(f"  Playlist items found: {len(items)}")
                if items:
                    print(f"  First item file: {items[0].get('data-file')[:100]}")
            else:
                print(f"AJAX Response: FAILED. Message: {aj_json.get('message')}")
        except Exception as e:
            print(f"AJAX Error: {e}")
            print(f"Response snippet: {aj_r.text[:200]}")
    else:
        print("AJAX playlist div NOT found")

test_details("https://uakino.best/seriesss/10458-dyuna-1-sezon.html")
test_details("https://uakino.best/filmy/genre-action/12567-dyuna.html")
