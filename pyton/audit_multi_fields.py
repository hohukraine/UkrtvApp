import requests
import re
import time
import json
from urllib.parse import urljoin

BASE_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
}

AJAX_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "X-Requested-With": "XMLHttpRequest",
}

def audit(page_url):
    print(f"Auditing: {page_url}")

    is_uakino = "uakino" in page_url
    is_eneyida = "eneyida" in page_url

    with requests.Session() as s:
        s.headers.update(BASE_HEADERS)
        resp = s.get(page_url, timeout=20)
        html = resp.text

    news_id = re.search(r"data-news_id\s*=\s*['\"](\d+)['\"]", html)
    if news_id:
        news_id = news_id.group(1)
    else:
        # Fallback to URL extraction
        filename = page_url.split("/")[-1]
        news_id = re.search(r"(\d+)", filename).group(1) if re.search(r"(\d+)", filename) else ""

    edittime = re.search(r"dle_edittime\s*=\s*['\"](\d+)['\"]", html)
    edittime = edittime.group(1) if edittime else str(int(time.time()))

    user_hash = re.search(r"dle_login_hash\s*=\s*['\"]([^'"]+)['\"]", html)
    user_hash = user_hash.group(1) if user_hash else ""

    print(f"Extracted: news_id={news_id}, edittime={edittime}, user_hash={user_hash}")

    if is_uakino:
        endpoints = ["https://uakino.best/engine/ajax/playlists.php"]
        xfnames = ["seria", "playlist", "video", "serial", "season", "playlist_serial", "movie", "film"]
    elif is_eneyida:
        endpoints = [
            "https://eneyida.tv/index.php?do=ajax&mod=get_playlist",
            "https://eneyida.tv/engine/ajax/playlists.php"
        ]
        xfnames = ["seria", "playlist", "video", "serial", "season", "playlist_serial", "movie", "film"]
    else:
        print("Unknown provider")
        return

    ajax_session = requests.Session()
    ajax_session.headers.update(AJAX_HEADERS)

    for url in endpoints:
        for xf in xfnames:
            body = {
                "news_id": news_id,
                "xfield": xf,
                "time": edittime,
            }
            if user_hash:
                body["user_hash"] = user_hash

            print(f"TRYING: {url} xfield={xf} ... ", end="", flush=True)
            try:
                r = ajax_session.post(url, data=body, headers={"Referer": page_url}, timeout=10)
                text = r.text
                if "ERR_XFIELD_ACCESS" in text or "ERR_NOT_DATA" in text:
                    print("FAILED")
                else:
                    print(f"SUCCESS! (len={len(text)})")
                    print(f"PREVIEW: {text[:200]}")
                    try:
                        data = r.json()
                        if data.get("success"):
                            print("JSON SUCCESS!")
                            # print(data.get("response")[:200])
                    except:
                        pass
            except Exception as e:
                print(f"ERROR: {e}")

if __name__ == "__main__":
    import sys
    url = sys.argv[1] if len(sys.argv) > 1 else "https://uakino.best/seriesss/comedy_series/5642-teorya-velikogo-vibuhu-1-sezon.html"
    audit(url)
