import requests
from bs4 import BeautifulSoup
import re
import sys
import os
from urllib.parse import urljoin, quote

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

BASE_URL = "https://kinoukr.tv"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
    "Accept-Language": "uk-UA,uk;q=0.9,en;q=0.8",
    "Referer": BASE_URL,
    "Connection": "keep-alive",
}

session = requests.Session()
session.headers.update(HEADERS)


def get_page(url):
    try:
        r = session.get(url, timeout=15)
        r.raise_for_status()
        return r.text
    except Exception as e:
        print(f"Помилка завантаження: {e}")
        return None


def extract_m3u8(html):
    return re.findall(r'https?://[^\s"\'\\]+\.m3u8[^\s"\'\\]*', html)


def get_stream(page_url):
    print(f"\nЗавантажуємо: {page_url}")
    html = get_page(page_url)
    if not html:
        return None

    soup = BeautifulSoup(html, "html.parser")

    # Find iframe with src (ashdi.vip)
    iframe = soup.select_one("iframe[src*='ashdi.vip']")
    if not iframe:
        # Try any iframe in video-box
        iframe = soup.select_one(".video-box iframe")
        if not iframe:
            print("iframe не знайдено")
            return None

    iframe_src = iframe.get("src", "")
    if not iframe_src:
        print("iframe src порожній")
        return None

    if iframe_src.startswith("//"):
        iframe_src = "https:" + iframe_src

    print(f"iframe: {iframe_src}")

    try:
        r = session.get(iframe_src, headers={"Referer": page_url}, timeout=15)
        if r.status_code == 200:
            m3u8s = extract_m3u8(r.text)
            if m3u8s:
                return {"type": "hls", "url": m3u8s[0], "source": "ashdi"}
            else:
                print("m3u8 не знайдено в iframe")
                return None
        else:
            print(f"iframe статус: {r.status_code}")
            return None
    except Exception as e:
        print(f"Помилка iframe: {e}")
        return None


def browse_category(path, page=1):
    url = f"{BASE_URL}{path}page/{page}/" if page > 1 else f"{BASE_URL}{path}"
    html = get_page(url)
    if not html:
        return []
    soup = BeautifulSoup(html, "html.parser")
    items = []
    for item in soup.select(".short"):
        title_el = item.select_one(".short-title")
        if not title_el:
            continue
        href = title_el.get("href", "")
        title = title_el.get_text(strip=True)
        if href and title:
            img_el = item.select_one("img")
            img = img_el.get("src", "") if img_el else ""
            if img and not img.startswith("http"):
                img = urljoin(BASE_URL, img)
            items.append({"title": title, "url": href, "image": img})
    return items


def get_homepage(limit=20):
    html = get_page(f"{BASE_URL}/home/")
    if not html:
        return []
    soup = BeautifulSoup(html, "html.parser")
    items = []
    for item in soup.select(".short"):
        title_el = item.select_one(".short-title")
        if not title_el:
            continue
        href = title_el.get("href", "")
        title = title_el.get_text(strip=True)
        if href and title:
            img_el = item.select_one("img")
            img = img_el.get("src", "") if img_el else ""
            if img and not img.startswith("http"):
                img = urljoin(BASE_URL, img)
            items.append({"title": title, "url": href, "image": img})
        if len(items) >= limit:
            break
    return items


if __name__ == "__main__":
    print("KinoUkr.tv Scraper")
    print("=" * 50)
    print("1. Головна сторінка")
    print("2. Категорія")
    print("=" * 50)
    print("Пошук недоступний (потрібна реєстрація)")

    choice = input("Виберіть опцію (1-2): ").strip()

    if choice == "1":
        movies = get_homepage()
        print(f"\nГоловна сторінка (знайдено: {len(movies)}):\n")
        for i, m in enumerate(movies, 1):
            print(f"{i}. {m['title']}")

        idx = input("\nНомер для стріму (Enter = вихід): ").strip()
        if idx.isdigit():
            idx = int(idx) - 1
            if 0 <= idx < len(movies):
                stream = get_stream(movies[idx]["url"])
                if stream:
                    print(f"\n✓ Стрім: {stream['url']}")
                    print(f"  Тип: {stream['type']}, Джерело: {stream['source']}")
                else:
                    print("\n✗ Стрім не знайдено")

    elif choice == "2":
        cats = {
            "Фільми": "/filmss/",
            "Серіали": "/series/",
            "Мультфільми": "/caartons/",
            "Аніме": "/anime/",
        }
        print("\nКатегорії:")
        for i, name in enumerate(cats, 1):
            print(f"{i}. {name}")
        idx = input("Виберіть: ").strip()
        if idx.isdigit():
            idx = int(idx) - 1
            names = list(cats.keys())
            if 0 <= idx < len(names):
                items = browse_category(cats[names[idx]])
                print(f"\n{names[idx]} (знайдено: {len(items)}):\n")
                for i, item in enumerate(items, 1):
                    print(f"{i}. {item['title']}")

                idx2 = input("\nНомер для стріму (Enter = вихід): ").strip()
                if idx2.isdigit():
                    idx2 = int(idx2) - 1
                    if 0 <= idx2 < len(items):
                        stream = get_stream(items[idx2]["url"])
                        if stream:
                            print(f"\n✓ Стрім: {stream['url']}")
                            print(f"  Тип: {stream['type']}, Джерело: {stream['source']}")
                        else:
                            print("\n✗ Стрім не знайдено")
