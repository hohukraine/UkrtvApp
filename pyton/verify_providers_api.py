import requests
import re
import time
import json

USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

def test_uakino():
    print("=== Testing Uakino API ===")
    base_url = "https://uakino.best/"

    # 1. Search test
    search_url = f"{base_url}engine/ajax/search.php"
    headers = {
        "User-Agent": USER_AGENT,
        "X-Requested-With": "XMLHttpRequest",
        "Referer": base_url,
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"
    }
    query = "Легенда"
    print(f"Testing Search for '{query}'...")
    try:
        resp = requests.post(search_url, data={"q": query}, headers=headers, timeout=10)
        print(f"Search Status: {resp.status_code}")
        if resp.status_code == 200:
            html = resp.text
            # Simple check for titles/links
            found = re.findall(r'href="(https?://uakino\.best/[^"]+\.html)"', html)
            print(f"Found {len(found)} results in search.")
            if found:
                test_page = found[0]
                print(f"Using page for further tests: {test_page}")

                # 2. Extract newsId
                news_id_match = re.search(r'/(\d+)-', test_page)
                if news_id_match:
                    news_id = news_id_match.group(1)
                    print(f"Extracted newsId: {news_id}")

                    # 3. Test Playlist API
                    playlist_url = f"{base_url}engine/ajax/playlists.php"
                    edittime = str(int(time.time()))
                    # Try both 'playlist' and 'seria'
                    for xf in ['playlist', 'seria']:
                        print(f"Testing Playlist API for xfield={xf}...")
                        payload = {
                            "news_id": news_id,
                            "xfield": xf,
                            "time": edittime
                        }
                        p_resp = requests.post(playlist_url, data=payload, headers=headers, timeout=10)
                        print(f"Playlist Status ({xf}): {p_resp.status_code}")
                        if p_resp.status_code == 200:
                            try:
                                data = p_resp.json()
                                print(f"Response success: {data.get('success')}")
                                if data.get('success'):
                                    content = data.get('response', '').replace('\\/', '/')
                                    file_match = re.search(r'data-file="([^"]+)"', content)
                                    if file_match:
                                        print(f"FOUND STREAM URL: {file_match.group(1)}")
                                    else:
                                        print("Could not find data-file in response HTML.")
                                        # Also check ashdi iframe if it's there
                                        if "ashdi.vip" in content:
                                             print("Found ashdi.vip in response.")
                            except:
                                print(f"Response is not JSON: {p_resp.text[:200]}")
                else:
                    print("Could not extract newsId from URL.")
    except Exception as e:
        print(f"Uakino Error: {e}")

def test_eneyida():
    print("\n=== Testing Eneyida API ===")
    base_url = "https://eneyida.tv/"
    headers = {
        "User-Agent": USER_AGENT,
        "X-Requested-With": "XMLHttpRequest",
        "Referer": base_url,
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"
    }

    # 1. Search test
    search_url = f"{base_url}engine/ajax/search.php"
    query = "Гладіатор"
    print(f"Testing Search for '{query}'...")
    try:
        resp = requests.post(search_url, data={"q": query}, headers=headers, timeout=10)
        print(f"Search Status: {resp.status_code}")
        if resp.status_code == 200:
            found = re.findall(r'href="(https?://eneyida\.tv/[^"]+\.html)"', resp.text)
            print(f"Found {len(found)} results.")
            if found:
                test_page = found[0]
                print(f"Using page: {test_page}")
                news_id_match = re.search(r'/(\d+)-', test_page)
                if news_id_match:
                    news_id = news_id_match.group(1)
                    print(f"Extracted newsId: {news_id}")

                    # Eneyida often uses index.php?do=ajax&mod=re_playlist or get_playlist
                    for mod in ['re_playlist', 'get_playlist']:
                        p_url = f"{base_url}index.php?do=ajax&mod={mod}"
                        print(f"Testing Playlist API mod={mod}...")
                        payload = {
                            "news_id": news_id,
                            "xfield": "playlist",
                            "time": str(int(time.time()))
                        }
                        p_resp = requests.post(p_url, data=payload, headers=headers, timeout=10)
                        print(f"Playlist Status ({mod}): {p_resp.status_code}")
                        if p_resp.status_code == 200:
                            try:
                                data = p_resp.json()
                                if data.get('success'):
                                    content = data.get('response', '').replace('\\/', '/')
                                    file_match = re.search(r'data-file="([^"]+)"', content)
                                    if file_match:
                                        print(f"FOUND STREAM URL: {file_match.group(1)}")
                            except:
                                pass
    except Exception as e:
        print(f"Eneyida Error: {e}")

def test_ashdi(video_id):
    print(f"\n=== Testing Ashdi.vip Resolution (videoID={video_id}) ===")
    url = f"https://ashdi.vip/vod/{video_id}"
    headers = {"User-Agent": USER_AGENT, "Referer": "https://uakino.best/"}
    try:
        resp = requests.get(url, headers=headers, timeout=10)
        print(f"Status: {resp.status_code}")
        if "cf-challenge" in resp.text or "Checking your browser" in resp.text:
            print("BLOCKED BY CLOUDFLARE")
            return

        file_match = re.search(r'["\']?file["\']?\s*:\s*["\']([^"\']+)["\']', resp.text)
        if file_match:
            token = file_match.group(1)
            print(f"Found Token: {token}")
            if token.startswith("http"):
                 print(f"Direct URL: {token}")
            else:
                 final = f"https://ashdi.vip/video15/2/new/video_{video_id}/hls/{token}/index.m3u8"
                 print(f"Constructed URL: {final}")
        else:
            print("Token not found in HTML.")
            if len(resp.text) < 1000:
                print(f"HTML Content: {resp.text}")
    except Exception as e:
        print(f"Ashdi Error: {e}")

if __name__ == "__main__":
    test_uakino()
    test_eneyida()
    # Test a known ashdi ID if possible, or use extracted from previous steps
    # test_ashdi("34058") # Example ID
