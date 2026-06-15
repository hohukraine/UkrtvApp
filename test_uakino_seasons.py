import requests
from bs4 import BeautifulSoup
import re
import json

def test_uakino_seasons():
    url = "https://uakino.best/seriesss/comedy_series/5642-teorya-velikogo-vibuhu-1-sezon.html"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer": url,
        "X-Requested-With": "XMLHttpRequest"
    }

    response = requests.get(url, headers=headers, timeout=15)
    soup = BeautifulSoup(response.text, 'html.parser')

    playlist_div = soup.select_one(".playlists-ajax")
    news_id = playlist_div.get('data-news_id')
    xfname = playlist_div.get('data-xfname', 'seria')
    edittime_match = re.search(r"dle_edittime\s*=\s*'(\d+)'", response.text)
    edittime = edittime_match.group(1) if edittime_match else "1700000000"

    ajax_url = "https://uakino.best/engine/ajax/playlists.php"
    data = {"news_id": news_id, "xfield": xfname, "time": edittime}
    ajax_resp = requests.post(ajax_url, data=data, headers=headers, timeout=15)
    ajax_data = ajax_resp.json()
    html_content = ajax_data.get('response', '').replace('\\/', '/')
    ajax_soup = BeautifulSoup(html_content, 'html.parser')

    seasons_tabs = ajax_soup.select(".playlists-lists li")
    for tab in seasons_tabs:
        s_id = tab.get('data-id')
        s_name = tab.get_text(strip=True)
        if not s_id: continue

        print(f"\nTab: {s_name}")
        eps = ajax_soup.select(f".playlists-videos li[data-id='{s_id}']")
        for ep in eps[:2]:
            print(f"  {ep.get_text(strip=True)}: {ep.get('data-file')}")

if __name__ == "__main__":
    test_uakino_seasons()
