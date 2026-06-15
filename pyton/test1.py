import requests
from bs4 import BeautifulSoup
import re
import json
import time
import sys
import os
from urllib.parse import urljoin, quote

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

BASE_URL = "https://uakino.cx"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
    "Accept-Language": "uk-UA,uk;q=0.9,en;q=0.8",
    "Referer": BASE_URL,
    "Connection": "keep-alive",
}

session = requests.Session()
session.headers.update(HEADERS)


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
    for item in soup.select(".grid-items__item"):
        title_el = item.select_one(".item__title, .expand-link__trg")
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


def extract_m3u8(html):
    return re.findall(r'https?://[^\s"\'\\]+\.m3u8[^\s"\'\\]*', html)


def get_stream(page_url):
    print(f"\nЗавантажуємо: {page_url}")
    html = get_page(page_url)
    if not html:
        return None

    soup = BeautifulSoup(html, "html.parser")

    # Find iframe with data-src
    iframe = soup.select_one("iframe[data-src]")
    if not iframe:
        print("iframe не знайдено")
        return None

    iframe_src = iframe.get("data-src", "")
    if not iframe_src:
        print("data-src порожній")
        return None

    if iframe_src.startswith("//"):
        iframe_src = "https:" + iframe_src

    print(f"iframe: {iframe_src}")

    try:
        r = session.get(iframe_src, headers={"Referer": page_url}, timeout=15)
        if r.status_code == 200:
            m3u8s = extract_m3u8(r.text)
            if m3u8s:
                return {"type": "hls", "url": m3u8s[0], "source": "ortified"}
            else:
                print("m3u8 не знайдено в iframe")
                # Save for debugging
                with open("C:\\UkrtvApp\\iframe_debug.html", "w", encoding="utf-8") as f:
                    f.write(r.text)
                print("Збережено iframe_debug.html")
    except Exception as e:
        print(f"Помилка iframe: {e}")

    return None


def browse_category(path, page=1):
    url = f"{BASE_URL}{path}page/{page}/" if page > 1 else f"{BASE_URL}{path}"
    html = get_page(url)
    if not html:
        return []
    soup = BeautifulSoup(html, "html.parser")
    return [{"title": el.get_text(strip=True), "url": el.get("href", "")}
            for el in soup.select(".grid-items__item .item__title, .grid-items__item .expand-link__trg") if el.get("href")]


def get_homepage(limit=20):
    html = get_page(f"{BASE_URL}/")
    if not html:
        return []
    return [{"title": el.get_text(strip=True), "url": el.get("href", "")}
            for el in BeautifulSoup(html, "html.parser").select(".grid-items__item .item__title, .grid-items__item .expand-link__trg") if el.get("href")][:limit]


if __name__ == "__main__":
    print("UAKino.cx Scraper")
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
        cats = {
            "Фільми": "/cinema/",
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
                print(f"\n{names[idx]}:\n")
                for i, item in enumerate(items, 1):
                    print(f"{i}. {item['title']}")
