import requests
from bs4 import BeautifulSoup
import re
import json
import time
import sys
import os
from urllib.parse import urljoin, quote

# Force UTF-8 encoding
if sys.platform == 'win32':
    os.system('chcp 65001 >nul 2>&1')
    sys.stdout.reconfigure(encoding='utf-8')

BASE_URL = "https://uakino.best"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
    "Accept-Language": "uk-UA,uk;q=0.9,en;q=0.8",
    "Referer": BASE_URL,
    "Connection": "keep-alive",
}

AJAX_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "application/json, text/javascript, */*; q=0.01",
    "Accept-Language": "uk-UA,uk;q=0.9,en;q=0.8",
    "Referer": BASE_URL,
    "X-Requested-With": "XMLHttpRequest",
    "Connection": "keep-alive",
}

session = requests.Session()
session.headers.update(HEADERS)
ajax_session = requests.Session()
ajax_session.headers.update(AJAX_HEADERS)


def search(query, limit=10):
    url = f"{BASE_URL}/index.php?do=search&subaction=search&story={quote(query)}"
    try:
        r = session.get(url, timeout=15)
        r.raise_for_status()
    except Exception as e:
        print(f"Помилка запиту: {e}")
        return []

    soup = BeautifulSoup(r.text, "html.parser")
    results = []
    for item in soup.select(".movie-item"):
        title_el = item.select_one("a.movie-title")
        if not title_el:
            continue
        href = title_el.get("href", "")
        title = title_el.get_text(strip=True)
        if href and title:
            img_el = item.select_one("img")
            img = img_el.get("src", "") if img_el else ""
            if img and not img.startswith("http"):
                img = urljoin(BASE_URL, img)
            results.append({"title": title, "url": href, "image": img})
            if len(results) >= limit:
                break
    return results


def get_page(url):
    try:
        r = session.get(url, timeout=15)
        r.raise_for_status()
        return r.text
    except Exception as e:
        print(f"Помилка: {e}")
        return None


def get_playlist_html(news_id, xfname, edittime):
    url = f"{BASE_URL}/engine/ajax/playlists.php"
    params = {"news_id": news_id, "xfield": xfname, "time": edittime}
    try:
        r = ajax_session.get(url, params=params, timeout=15)
        if r.status_code == 200:
            try:
                data = r.json()
                if data.get("success") and data.get("response"):
                    resp = data["response"]
                    resp = resp.replace('\\"', '"').replace('\\n', '\n').replace('\\t', '\t').replace('\\/', '/')
                    return resp
            except json.JSONDecodeError as e:
                print(f"JSON error: {e}, response: {r.text[:100]}")
    except Exception as e:
        print(f"Помилка плейлиста: {e}")
    return None


def extract_player_urls(html):
    soup = BeautifulSoup(html, "html.parser")
    urls = []
    for li in soup.select("li[data-file]"):
        file_url = li.get("data-file", "")
        if file_url and (file_url.startswith("http") or file_url.startswith("//")):
            if file_url.startswith("//"):
                file_url = "https:" + file_url
            urls.append(file_url)
    return urls


def extract_m3u8(html):
    return re.findall(r'https?://[^\s"\'\\]+\.m3u8[^\s"\'\\]*', html)


def get_stream(page_url):
    print(f"\nЗавантажуємо: {page_url}")
    html = get_page(page_url)
    if not html:
        return None

    edittime = re.search(r"dle_edittime\s*=\s*'(\d+)'", html)
    edittime = edittime.group(1) if edittime else str(int(time.time()))

    playlist_div = BeautifulSoup(html, "html.parser").select_one(".playlists-ajax")
    if not playlist_div:
        print("Плейлист не знайдено")
        return None

    news_id = playlist_div.get("data-news_id", "")
    xfname = playlist_div.get("data-xfname", "")

    if not news_id or not xfname:
        print("Немає news_id/xfname")
        return None

    print(f"ID: {news_id}, XField: {xfname}")

    playlist_html = get_playlist_html(news_id, xfname, edittime)
    if not playlist_html:
        print("Не вдалося завантажити плейлист")
        return None

    player_urls = extract_player_urls(playlist_html)
    if not player_urls:
        print("Плеєри не знайдено")
        return None

    print(f"Плеєрів: {len(player_urls)}")

    for url in player_urls:
        print(f"  -> {url}")
        try:
            r = session.get(url, headers={"Referer": page_url}, timeout=15)
            if r.status_code == 200:
                m3u8s = extract_m3u8(r.text)
                if m3u8s:
                    return {"type": "hls", "url": m3u8s[0], "source": "ashdi"}
        except Exception as e:
            print(f"  Помилка: {e}")

    return None


def browse_category(path, page=1):
    url = f"{BASE_URL}{path}page/{page}/" if page > 1 else f"{BASE_URL}{path}"
    html = get_page(url)
    if not html:
        return []
    soup = BeautifulSoup(html, "html.parser")
    return [{"title": el.get_text(strip=True), "url": el.get("href", "")}
            for el in soup.select(".movie-item a.movie-title") if el.get("href")]


def get_homepage(limit=20):
    html = get_page(f"{BASE_URL}/ua/")
    if not html:
        return []
    return [{"title": el.get_text(strip=True), "url": el.get("href", "")}
            for el in BeautifulSoup(html, "html.parser").select(".movie-item a.movie-title") if el.get("href")][:limit]


if __name__ == "__main__":
    print("UAKino Scraper")
    print("=" * 50)
    print("1. Пошук фільму")
    print("2. Головна сторінка")
    print("3. Категорія")
    print("=" * 50)

    choice = input("Виберіть опцію (1-3): ").strip()

    if choice == "1":
        query = input("Назва фільму: ").strip()
        if not query:
            sys.exit(0)
        results = search(query)
        if not results:
            print("Нічого не знайдено.")
            sys.exit(0)
        print(f"\nЗнайдено: {len(results)} результатів\n")
        for i, r in enumerate(results[:10], 1):
            print(f"{i}. {r['title']}\n   {r['url']}\n")

        idx = input("Номер для стріму (Enter = вихід): ").strip()
        if idx.isdigit():
            idx = int(idx) - 1
            if 0 <= idx < len(results):
                stream = get_stream(results[idx]["url"])
                if stream:
                    print(f"\n✓ Стрім: {stream['url']}")
                    print(f"  Тип: {stream['type']}, Джерело: {stream['source']}")
                else:
                    print("\n✗ Стрім не знайдено")

    elif choice == "2":
        movies = get_homepage()
        print("\nГоловна сторінка:\n")
        for i, m in enumerate(movies, 1):
            print(f"{i}. {m['title']}")

    elif choice == "3":
        cats = {"Фільми": "/filmy/", "Серіали": "/seriesss/", "Мультфільми": "/cartoon/", "Аніме": "/animeukr/", "Підбірки": "/colections/"}
        print("\nКатегорії:")
        for i, name in enumerate(cats, 1):
            print(f"{i}. {name}")
        idx = input("Виберіть: ").strip()
        if idx.isdigit():
            idx = int(idx) - 1
            names = list(cats.keys())
            if 0 <= idx < len(names):
                items = browse_category(cats[names[idx]])
                print(f"\n{names[idx]}:\n")
                for i, item in enumerate(items, 1):
                    print(f"{i}. {item['title']}")
