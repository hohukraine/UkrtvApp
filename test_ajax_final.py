import requests
from bs4 import BeautifulSoup
import time
import json

def test_provider(name, base_url, search_query, test_movie_url, news_id):
    print(f"\n{'='*20} TESTING {name} ({base_url}) {'='*20}")

    # 1. Test Search (AJAX)
    print(f"\n[1] Testing AJAX Search for '{search_query}'...")
    search_url = f"{base_url}engine/ajax/search.php"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "X-Requested-With": "XMLHttpRequest",
        "Referer": base_url
    }
    try:
        resp = requests.post(search_url, headers=headers, data={"q": search_query}, timeout=10)
        print(f"Status: {resp.status_code}")
        if resp.status_code == 200:
            soup = BeautifulSoup(resp.text, 'html.parser')
            links = soup.select("a[href*='.html']")
            print(f"SUCCESS: Found {len(links)} search results")
            for a in links[:2]:
                print(f"  - {a.get_text(strip=True)} ({a['href']})")
        else:
            print(f"FAILED Search: {resp.status_code}")
    except Exception as e:
        print(f"ERROR Search: {e}")

    # 2. Test Playlist (POST)
    print(f"\n[2] Testing Playlist POST discovery...")
    endpoints = [
        f"{base_url}engine/ajax/playlists.php",
        f"{base_url}index.php?do=ajax&mod=playlists"
    ]
    xfields = ["playlist", "seria"]

    found_playlist = False
    for url in endpoints:
        if found_playlist: break
        for xf in xfields:
            data = {
                "news_id": news_id,
                "xfield": xf,
                "time": str(int(time.time()))
            }
            try:
                print(f"Trying POST {url} (xfield={xf})...")
                resp = requests.post(url, headers=headers, data=data, timeout=10)
                if resp.status_code == 200:
                    text = resp.text
                    if "success" in text and '"success":true' in text:
                        js = resp.json()
                        res_soup = BeautifulSoup(js.get("response", ""), 'html.parser')
                        items = res_soup.select("li[data-file], li[data-src]")
                        if items:
                            print(f"  SUCCESS! Found {len(items)} items in JSON playlist")
                            found_playlist = True
                            break
                    elif "playlists-lists" in text or "data-file" in text:
                        print(f"  SUCCESS! Found HTML playlist directly")
                        found_playlist = True
                        break
                    else:
                        print(f"  No playlist data (Status 200)")
                else:
                    print(f"  Failed (Status {resp.status_code})")
            except Exception as e:
                print(f"  Error: {e}")

    if not found_playlist:
        print("FAILED: Could not find AJAX playlist via POST")

if __name__ == "__main__":
    # Uakino: Rick and Morty (Serial)
    test_provider("Uakino", "https://uakino.best/", "Рік та Морті",
                  "https://uakino.best/cartoon/cartoonseries/34181-rik-ta-morti-9-sezon.html", "34181")

    # Eneyida: House of the Dragon (Serial)
    test_provider("Eneyida", "https://eneyida.tv/", "Дім дракона",
                  "https://eneyida.tv/9330-dim-drakona-2-sezon.html", "9330")
