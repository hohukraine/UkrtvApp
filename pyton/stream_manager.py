"""
Unified Stream Manager — Strategy Pattern
==========================================
4 провайдери (uakino.best, uakino.cx, kinoukr.tv, eneyida.tv)
в єдиному інтерфейсі з спільною сесією, куками та пріоритетом.

Архітектура:
- StreamProvider (абстракція/інтерфейс)
- UakinoBestProvider, UakinoCxProvider, KinoukrProvider, EneyidaProvider (стратегії)
- StreamManager (контекст) — керує провайдерами, пріоритетом, пошуком

Кінцева мета: отримати .m3u8 лінк або None.
"""

import requests
from bs4 import BeautifulSoup
import re
import time
import sys
from urllib.parse import urljoin, quote
from abc import ABC, abstractmethod

# Force UTF-8 for Windows console
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

# =============================================================================
# Глобальна сесія (спільна для всіх провайдерів)
# =============================================================================
BASE_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
    "Accept-Language": "uk-UA,uk;q=0.9,en;q=0.8",
    "Connection": "keep-alive",
}

AJAX_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "application/json, text/javascript, */*; q=0.01",
    "Accept-Language": "uk-UA,uk;q=0.9,en;q=0.8",
    "X-Requested-With": "XMLHttpRequest",
    "Connection": "keep-alive",
}

# Одна спільна сесія для всіх провайдерів
shared_session = requests.Session()
shared_session.headers.update(BASE_HEADERS)

# Окрема сесія для AJAX-запитів (тільки для uakino.best)
ajax_shared_session = requests.Session()
ajax_shared_session.headers.update(AJAX_HEADERS)


# =============================================================================
# Допоміжні функції
# =============================================================================
def extract_m3u8(html: str) -> list[str]:
    """Витягує всі .m3u8 URL з HTML."""
    return re.findall(r'https?://[^\s"\'\\]+\.m3u8[^\s"\'\\]*', html)


def normalize_url(url: str) -> str:
    """Нормалізує protocol-relative URL (//example.com -> https://example.com)."""
    if url.startswith("//"):
        return "https:" + url
    return url


# =============================================================================
# Абстракція (Strategy Interface)
# =============================================================================
class StreamProvider(ABC):
    """
    Інтерфейс для всіх провайдерів потоків.
    Кожен провайдер реалізує:
      - has_public_search: чи доступний публічний пошук
      - initialize_session(): "розігрів" cookies через GET на головну
      - search(): пошук за запитом
      - get_stream_url(): отримання .m3u8 зі сторінки фільму
      - get_homepage(): головна сторінка
      - get_categories(): категорії
    """

    def __init__(self, name: str, base_url: str, has_public_search: bool = False):
        self.name = name
        self.base_url = base_url
        self.has_public_search = has_public_search

    def supports_url(self, url: str) -> bool:
        """Чи належить URL цьому провайдеру."""
        return url.startswith(self.base_url)

    def _get_page(self, url: str) -> str | None:
        """Завантажує сторінку з спільною сесією, 10с таймаут."""
        try:
            print(f"  [{self.name}] GET {url}")
            r = shared_session.get(url, timeout=10)
            r.raise_for_status()
            return r.text
        except Exception as e:
            print(f"  [{self.name}] ПОМИЛКА завантаження: {e}")
            return None

    def _get_iframe(self, iframe_src: str, referer: str) -> str | None:
        """Завантажує iframe з правильним Referer."""
        try:
            resolved = normalize_url(iframe_src)
            print(f"  [{self.name}] iframe -> {resolved}")
            r = shared_session.get(resolved, headers={"Referer": referer}, timeout=10)
            r.raise_for_status()
            return r.text
        except Exception as e:
            print(f"  [{self.name}] ПОМИЛКА iframe: {e}")
            return None

    # -------------------------------------------------------------------------
    # Методи, які має реалізувати кожен провайдер
    # -------------------------------------------------------------------------
    @abstractmethod
    def initialize_session(self) -> bool:
        """Розігріває cookies через GET на головну сторінку. Повертає True/False."""
        pass

    @abstractmethod
    def search(self, query: str, limit: int = 10) -> list[dict]:
        """Пошук фільмів. Повертає список {'title', 'url', 'image'}."""
        pass

    @abstractmethod
    def get_stream_url(self, page_url: str) -> dict | None:
        """Отримує .m3u8 лінк. Повертає {'type', 'url', 'source', 'provider'} або None."""
        pass

    @abstractmethod
    def get_homepage(self, limit: int = 20) -> list[dict]:
        """Головна сторінка. Повертає список {'title', 'url', 'image'}."""
        pass

    @abstractmethod
    def get_categories(self) -> dict[str, str]:
        """Словник категорій: назва -> шлях."""
        pass


# =============================================================================
# Провайдер 1: UakinoBest (uakino.best)
# =============================================================================
class UakinoBestProvider(StreamProvider):
    """
    uakino.best — підтримує публічний пошук.
    Два типи сторінок:
      - newer: прямий iframe -> ashdi.vip (без AJAX)
      - older: .playlists-ajax -> /engine/ajax/playlists.php -> плеєри -> ashdi.vip
    """

    def __init__(self):
        super().__init__("UakinoBest", "https://uakino.best", has_public_search=True)

    def initialize_session(self) -> bool:
        """Розігріває cookies через головну сторінку."""
        try:
            print(f"[{self.name}] Ініціалізація сесії (головна сторінка)...")
            r = shared_session.get(f"{self.base_url}/ua/", timeout=10)
            r.raise_for_status()
            print(f"  [{self.name}] Сесія готова (status {r.status_code})")
            return True
        except Exception as e:
            print(f"  [{self.name}] ПОМИЛКА ініціалізації: {e}")
            return False

    def search(self, query: str, limit: int = 10) -> list[dict]:
        url = f"{self.base_url}/index.php?do=search&subaction=search&story={quote(query)}"
        html = self._get_page(url)
        if not html:
            return []

        soup = BeautifulSoup(html, "html.parser")
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
                    img = urljoin(self.base_url, img)
                results.append({
                    "title": title,
                    "url": href,
                    "image": img,
                    "provider": self.name,
                })
                if len(results) >= limit:
                    break
        return results

    def get_stream_url(self, page_url: str) -> dict | None:
        """
        Отримує .m3u8 зі сторінки uakino.best.
        Спочатку пробує прямий iframe (нові сторінки),
        потім AJAX плейлист (старі сторінки).
        """
        print(f"\n[{self.name}] Отримання стріму: {page_url}")
        html = self._get_page(page_url)
        if not html:
            return None

        soup = BeautifulSoup(html, "html.parser")

        # =====================================================================
        # Спосіб 1: прямий iframe -> ashdi.vip (нові сторінки)
        # =====================================================================
        iframe = soup.select_one("iframe[src*='ashdi.vip']")
        if iframe:
            iframe_src = iframe.get("src", "")
            if iframe_src:
                iframe_html = self._get_iframe(iframe_src, page_url)
                if iframe_html:
                    m3u8s = extract_m3u8(iframe_html)
                    if m3u8s:
                        print(f"  [{self.name}] ✓ m3u8 знайдено (прямий iframe)")
                        return {
                            "type": "hls",
                            "url": m3u8s[0],
                            "source": "ashdi",
                            "provider": self.name,
                        }

        # =====================================================================
        # Спосіб 2: AJAX плейлист (старі сторінки)
        # =====================================================================
        edittime_match = re.search(r"dle_edittime\s*=\s*'(\d+)'", html)
        edittime = edittime_match.group(1) if edittime_match else str(int(time.time()))

        playlist_div = soup.select_one(".playlists-ajax")
        if not playlist_div:
            print(f"  [{self.name}] Плейлист не знайдено (ні iframe, ні .playlists-ajax)")
            return None

        news_id = playlist_div.get("data-news_id", "")
        xfname = playlist_div.get("data-xfname", "")
        if not news_id or not xfname:
            print(f"  [{self.name}] Немає news_id/xfname")
            return None

        print(f"  [{self.name}] AJAX плейлист: news_id={news_id}, xfield={xfname}")

        playlist_url = f"{self.base_url}/engine/ajax/playlists.php"
        params = {"news_id": news_id, "xfield": xfname, "time": edittime}
        try:
            r = ajax_shared_session.get(playlist_url, params=params, timeout=10)
            if r.status_code != 200:
                print(f"  [{self.name}] AJAX статус: {r.status_code}")
                return None
            data = r.json()
            if not data.get("success") or not data.get("response"):
                print(f"  [{self.name}] AJAX: успіх=false або порожня відповідь")
                return None
            resp = data["response"]
            resp = resp.replace('\\"', '"').replace('\\n', '\n').replace('\\t', '\t').replace('\\/', '/')
        except Exception as e:
            print(f"  [{self.name}] ПОМИЛКА AJAX: {e}")
            return None

        playlist_soup = BeautifulSoup(resp, "html.parser")
        player_urls = []
        for li in playlist_soup.select("li[data-file]"):
            file_url = li.get("data-file", "")
            if file_url and (file_url.startswith("http") or file_url.startswith("//")):
                player_urls.append(normalize_url(file_url))

        if not player_urls:
            print(f"  [{self.name}] Плеєри не знайдено в плейлисті")
            return None

        print(f"  [{self.name}] Плеєрів у плейлисті: {len(player_urls)}")

        for url in player_urls:
            iframe_html = self._get_iframe(url, page_url)
            if iframe_html:
                m3u8s = extract_m3u8(iframe_html)
                if m3u8s:
                    print(f"  [{self.name}] ✓ m3u8 знайдено (AJAX плеєр)")
                    return {
                        "type": "hls",
                        "url": m3u8s[0],
                        "source": "ashdi",
                        "provider": self.name,
                    }

        print(f"  [{self.name}] m3u8 не знайдено в жодному плеєрі")
        return None

    def get_homepage(self, limit: int = 20) -> list[dict]:
        html = self._get_page(f"{self.base_url}/ua/")
        if not html:
            return []
        items = []
        for el in BeautifulSoup(html, "html.parser").select(".movie-item a.movie-title"):
            href = el.get("href", "")
            title = el.get_text(strip=True)
            if href and title:
                img_el = el.find_parent().select_one("img") if el.find_parent() else None
                img = img_el.get("src", "") if img_el else ""
                if img and not img.startswith("http"):
                    img = urljoin(self.base_url, img)
                items.append({"title": title, "url": href, "image": img})
                if len(items) >= limit:
                    break
        return items

    def get_categories(self) -> dict[str, str]:
        return {
            "Фільми": "/filmy/",
            "Серіали": "/seriesss/",
            "Мультфільми": "/cartoon/",
            "Аніме": "/animeukr/",
            "Підбірки": "/colections/",
        }


# =============================================================================
# Провайдер 2: UakinoCx (uakino.cx)
# =============================================================================
class UakinoCxProvider(StreamProvider):
    """
    uakino.cx — підтримує публічний пошук.
    Сторінка фільму: iframe[data-src] -> ortified.ws -> m3u8.
    Високий пріоритет для отримання стріму (якщо URL належить цьому домену).
    """

    def __init__(self):
        super().__init__("UakinoCx", "https://uakino.cx", has_public_search=True)

    def initialize_session(self) -> bool:
        try:
            print(f"[{self.name}] Ініціалізація сесії (головна сторінка)...")
            r = shared_session.get(self.base_url, timeout=10)
            r.raise_for_status()
            print(f"  [{self.name}] Сесія готова (status {r.status_code})")
            return True
        except Exception as e:
            print(f"  [{self.name}] ПОМИЛКА ініціалізації: {e}")
            return False

    def search(self, query: str, limit: int = 10) -> list[dict]:
        url = f"{self.base_url}/index.php?do=search&subaction=search&story={quote(query)}"
        html = self._get_page(url)
        if not html:
            return []

        soup = BeautifulSoup(html, "html.parser")
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
                    img = urljoin(self.base_url, img)
                results.append({
                    "title": title,
                    "url": href,
                    "image": img,
                    "provider": self.name,
                })
                if len(results) >= limit:
                    break
        return results

    def get_stream_url(self, page_url: str) -> dict | None:
        print(f"\n[{self.name}] Отримання стріму: {page_url}")
        html = self._get_page(page_url)
        if not html:
            return None

        soup = BeautifulSoup(html, "html.parser")
        iframe = soup.select_one("iframe[data-src]")
        if not iframe:
            iframe = soup.select_one(".video-box iframe")
            if not iframe:
                print(f"  [{self.name}] iframe не знайдено")
                return None

        iframe_src = iframe.get("data-src") or iframe.get("src", "")
        if not iframe_src:
            print(f"  [{self.name}] iframe src порожній")
            return None

        iframe_html = self._get_iframe(iframe_src, page_url)
        if not iframe_html:
            return None

        m3u8s = extract_m3u8(iframe_html)
        if m3u8s:
            print(f"  [{self.name}] ✓ m3u8 знайдено (ortified)")
            return {
                "type": "hls",
                "url": m3u8s[0],
                "source": "ortified",
                "provider": self.name,
            }

        print(f"  [{self.name}] m3u8 не знайдено в iframe")
        return None

    def get_homepage(self, limit: int = 20) -> list[dict]:
        html = self._get_page(self.base_url)
        if not html:
            return []
        items = []
        for el in BeautifulSoup(html, "html.parser").select(
            ".grid-items__item .item__title, .grid-items__item .expand-link__trg"
        ):
            href = el.get("href", "")
            title = el.get_text(strip=True)
            if href and title:
                img_el = el.find_parent().select_one("img") if el.find_parent() else None
                img = img_el.get("src", "") if img_el else ""
                if img and not img.startswith("http"):
                    img = urljoin(self.base_url, img)
                items.append({"title": title, "url": href, "image": img})
                if len(items) >= limit:
                    break
        return items

    def get_categories(self) -> dict[str, str]:
        return {
            "Фільми": "/cinema/",
            "Серіали": "/series/",
            "Мультфільми": "/caartons/",
            "Аніме": "/anime/",
        }


# =============================================================================
# Провайдер 3: Kinoukr (kinoukr.tv)
# =============================================================================
class KinoukrProvider(StreamProvider):
    """
    kinoukr.tv — пошук недоступний (потрібна реєстрація).
    Сторінка фільму: iframe[src*='ashdi.vip'] -> m3u8.
    """

    def __init__(self):
        super().__init__("KinoUkr", "https://kinoukr.tv", has_public_search=False)

    def initialize_session(self) -> bool:
        try:
            print(f"[{self.name}] Ініціалізація сесії (головна сторінка)...")
            r = shared_session.get(f"{self.base_url}/home/", timeout=10)
            r.raise_for_status()
            print(f"  [{self.name}] Сесія готова (status {r.status_code})")
            return True
        except Exception as e:
            print(f"  [{self.name}] ПОМИЛКА ініціалізації: {e}")
            return False

    def search(self, query: str, limit: int = 10) -> list[dict]:
        """Пошук недоступний без реєстрації."""
        return []

    def get_stream_url(self, page_url: str) -> dict | None:
        print(f"\n[{self.name}] Отримання стріму: {page_url}")
        html = self._get_page(page_url)
        if not html:
            return None

        soup = BeautifulSoup(html, "html.parser")
        iframe = soup.select_one("iframe[src*='ashdi.vip']")
        if not iframe:
            iframe = soup.select_one(".video-box iframe")
            if not iframe:
                print(f"  [{self.name}] iframe не знайдено")
                return None

        iframe_src = iframe.get("src", "")
        if not iframe_src:
            return None

        iframe_html = self._get_iframe(iframe_src, page_url)
        if not iframe_html:
            return None

        m3u8s = extract_m3u8(iframe_html)
        if m3u8s:
            print(f"  [{self.name}] ✓ m3u8 знайдено (ashdi)")
            return {
                "type": "hls",
                "url": m3u8s[0],
                "source": "ashdi",
                "provider": self.name,
            }

        print(f"  [{self.name}] m3u8 не знайдено в iframe")
        return None

    def get_homepage(self, limit: int = 20) -> list[dict]:
        html = self._get_page(f"{self.base_url}/home/")
        if not html:
            return []
        items = []
        soup = BeautifulSoup(html, "html.parser")
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
                    img = urljoin(self.base_url, img)
                items.append({"title": title, "url": href, "image": img})
                if len(items) >= limit:
                    break
        return items

    def get_categories(self) -> dict[str, str]:
        return {
            "Фільми": "/filmss/",
            "Серіали": "/series/",
            "Мультфільми": "/caartons/",
            "Аніме": "/anime/",
        }


# =============================================================================
# Провайдер 4: Eneyida (eneyida.tv)
# =============================================================================
class EneyidaProvider(StreamProvider):
    """
    eneyida.tv — пошук недоступний (потрібна реєстрація).
    Сторінка фільму: iframe[src*='hdvbua.pro'] -> m3u8.
    """

    def __init__(self):
        super().__init__("Eneyida", "https://eneyida.tv", has_public_search=False)

    def initialize_session(self) -> bool:
        try:
            print(f"[{self.name}] Ініціалізація сесії (головна сторінка)...")
            r = shared_session.get(self.base_url, timeout=10)
            r.raise_for_status()
            print(f"  [{self.name}] Сесія готова (status {r.status_code})")
            return True
        except Exception as e:
            print(f"  [{self.name}] ПОМИЛКА ініціалізації: {e}")
            return False

    def search(self, query: str, limit: int = 10) -> list[dict]:
        """Пошук недоступний без реєстрації."""
        return []

    def get_stream_url(self, page_url: str) -> dict | None:
        print(f"\n[{self.name}] Отримання стріму: {page_url}")
        html = self._get_page(page_url)
        if not html:
            return None

        soup = BeautifulSoup(html, "html.parser")
        iframe = soup.select_one("iframe[src*='hdvbua.pro']")
        if not iframe:
            iframe = soup.select_one(".video_box iframe")
            if not iframe:
                print(f"  [{self.name}] iframe не знайдено")
                return None

        iframe_src = iframe.get("src", "")
        if not iframe_src:
            return None

        iframe_html = self._get_iframe(iframe_src, page_url)
        if not iframe_html:
            return None

        m3u8s = extract_m3u8(iframe_html)
        if m3u8s:
            print(f"  [{self.name}] ✓ m3u8 знайдено (hdvbua)")
            return {
                "type": "hls",
                "url": m3u8s[0],
                "source": "hdvbua",
                "provider": self.name,
            }

        print(f"  [{self.name}] m3u8 не знайдено в iframe")
        return None

    def get_homepage(self, limit: int = 20) -> list[dict]:
        html = self._get_page(self.base_url)
        if not html:
            return []
        items = []
        soup = BeautifulSoup(html, "html.parser")
        for item in soup.select(".short"):
            title_el = item.select_one(".short_title")
            if not title_el:
                continue
            href = title_el.get("href", "")
            title = title_el.get_text(strip=True)
            if href and title:
                img_el = item.select_one("img")
                img = img_el.get("src", "") if img_el else ""
                if img and not img.startswith("http"):
                    img = urljoin(self.base_url, img)
                items.append({"title": title, "url": href, "image": img})
                if len(items) >= limit:
                    break
        return items

    def get_categories(self) -> dict[str, str]:
        return {
            "Фільми": "/films/",
            "Серіали": "/series/",
            "Мультфільми": "/cartoon/",
            "Мультсеріали": "/cartoon-series/",
            "Аніме": "/anime/",
        }


# =============================================================================
# Менеджер (Context) — керує провайдерами
# =============================================================================
class StreamManager:
    """
    Контекст патерну Strategy.
    Приймає список провайдерів, керує їх пріоритетом та виконує пошук/стрім.
    """

    def __init__(self):
        self.providers: list[StreamProvider] = []
        self.stream_priority: list[StreamProvider] = []

    def register(self, provider: StreamProvider) -> None:
        """Додає провайдера до списку та встановляє пріоритет за замовчуванням."""
        self.providers.append(provider)
        if provider not in self.stream_priority:
            self.stream_priority.append(provider)
        print(f"[Manager] Зареєстровано провайдера: {provider.name}")

    def set_stream_priority(self, provider_class: type) -> None:
        """
        Встановлює пріоритет отримання стріму.
        Провайдер заданого класу стає першим, інші зберігають порядок.
        """
        self.stream_priority.sort(key=lambda p: 0 if isinstance(p, provider_class) else 1)
        priority_names = [p.name for p in self.stream_priority]
        print(f"[Manager] Пріоритет стріму: {' -> '.join(priority_names)}")

    def initialize_all(self) -> None:
        """Ініціалізує сесію для всіх провайдерів (розігріває cookies)."""
        print("\n[Manager] Ініціалізація всіх сесій...")
        for provider in self.providers:
            provider.initialize_session()
        print("[Manager] Всі сесії ініціалізовано\n")

    def find_best_stream(self, query: str) -> dict | None:
        """
        Головний метод: знаходить .m3u8 за назвою фільму.
        1. Шукає URL через провайдерів з has_public_search=True.
        2. Отримує стрім через провайдерів у порядку пріоритету.

        Повертає dict з результатом або None.
        """
        print(f"\n{'='*60}")
        print(f"[Manager] Пошук: '{query}'")
        print(f"{'='*60}")

        # ---------------------------------------------------------------------
        # Крок 1: Пошук URL фільму
        # ---------------------------------------------------------------------
        search_results: list[dict] = []
        search_providers = [p for p in self.providers if p.has_public_search]

        if not search_providers:
            print("[Manager] ПОМИЛКА: жоден провайдер не має публічного пошуку")
            return None

        print(f"\n[Manager] Крок 1: Пошук URL через {len(search_providers)} провайдерів...")
        for provider in search_providers:
            try:
                print(f"\n  → Шукаємо через {provider.name}...")
                results = provider.search(query, limit=10)
                if results:
                    print(f"    ✓ Знайдено {len(results)} результатів")
                    search_results.extend(results)
                else:
                    print(f"    ✗ Нічого не знайдено")
            except Exception as e:
                print(f"    ✗ ПОМИЛКА пошуку ({provider.name}): {e}")

        if not search_results:
            print(f"\n[Manager] Фільм не знайдено ні на одному сайті")
            return None

        # Беремо перший (найрелевантніший) результат
        best_match = search_results[0]
        print(f"\n  Найкращий результат: [{best_match['provider']}] {best_match['title']}")
        print(f"  URL: {best_match['url']}")

        # ---------------------------------------------------------------------
        # Крок 2: Отримання стріму (за пріоритетом)
        # ---------------------------------------------------------------------
        print(f"\n[Manager] Крок 2: Отримання стріму (пріоритет: {' -> '.join(p.name for p in self.stream_priority)})...")

        for provider in self.stream_priority:
            if not provider.supports_url(best_match["url"]):
                print(f"\n  → {provider.name}: URL не належить цьому домену, пропускаємо")
                continue

            try:
                print(f"\n  → Пробуємо {provider.name}...")
                stream = provider.get_stream_url(best_match["url"])
                if stream:
                    print(f"\n{'='*60}")
                    print(f"[Manager] ✓ СТРІМ ЗНАЙДЕНО!")
                    print(f"  Провайдер: {stream['provider']}")
                    print(f"  Джерело: {stream['source']}")
                    print(f"  Тип: {stream['type']}")
                    print(f"  URL: {stream['url']}")
                    print(f"{'='*60}")
                    return stream
                else:
                    print(f"    ✗ {provider.name}: стрім не знайдено")
            except Exception as e:
                print(f"    ✗ ПОМИЛКА ({provider.name}): {e}")

        print(f"\n[Manager] Стрім не знайдено ні через один провайдер")
        return None

    def search(self, query: str, limit: int = 10) -> list[dict]:
        """Пошук через всі провайдери з has_public_search=True."""
        results = []
        for provider in self.providers:
            if provider.has_public_search:
                try:
                    r = provider.search(query, limit)
                    results.extend(r)
                except Exception as e:
                    print(f"[Manager] ПОМИЛКА пошуку ({provider.name}): {e}")
        return results

    def get_stream(self, page_url: str) -> dict | None:
        """Отримує стрім через провайдерів у порядку пріоритету."""
        for provider in self.stream_priority:
            if provider.supports_url(page_url):
                try:
                    stream = provider.get_stream_url(page_url)
                    if stream:
                        return stream
                except Exception as e:
                    print(f"[Manager] ПОМИЛКА ({provider.name}): {e}")
        return None


# =============================================================================
# Точка входу (CLI)
# =============================================================================
if __name__ == "__main__":
    # Створення менеджера та реєстрація провайдерів
    manager = StreamManager()
    manager.register(UakinoBestProvider())    # Пошук: UakinoBest (основним)
    manager.register(UakinoCxProvider())      # Пошук: UakinoCx
    manager.register(KinoukrProvider())       # Без пошуку
    manager.register(EneyidaProvider())       # Без пошуку

    # Пріоритет отримання стріму: UakinoCx -> інші
    manager.set_stream_priority(UakinoCxProvider)

    # Ініціалізація сесій (кукі)
    manager.initialize_all()

    # CLI меню
    print("Stream Manager — українські фільми")
    print("=" * 50)
    print("1. Швидкий пошук (find_best_stream)")
    print("2. Перегляд головної (UakinoBest)")
    print("3. Категорія")
    print("=" * 50)

    choice = input("Виберіть опцію (1-3): ").strip()

    if choice == "1":
        query = input("Назва фільму: ").strip()
        if not query:
            sys.exit(0)
        result = manager.find_best_stream(query)
        if result:
            print(f"\n✓ Фінальний .m3u8: {result['url']}")
        else:
            print("\n✗ Нічого не знайдено")

    elif choice == "2":
        provider = manager.providers[0]  # UakinoBest
        movies = provider.get_homepage()
        print(f"\nГоловна ({provider.name}, знайдено {len(movies)}):\n")
        for i, m in enumerate(movies[:20], 1):
            print(f"{i}. {m['title']}")
        idx = input("\nНомер для стріму (Enter = вихід): ").strip()
        if idx.isdigit():
            idx = int(idx) - 1
            if 0 <= idx < len(movies):
                stream = manager.get_stream(movies[idx]["url"])
                if stream:
                    print(f"\n✓ Стрім: {stream['url']}")
                else:
                    print("\n✗ Стрім не знайдено")

    elif choice == "3":
        cats = {"Фільми": "/films/", "Серіали": "/series/", "Мультфільми": "/cartoon/", "Аніме": "/anime/"}
        print("\nКатегорії:")
        for i, name in enumerate(cats, 1):
            print(f"{i}. {name}")
        idx = input("Виберіть: ").strip()
        if idx.isdigit():
            idx = int(idx) - 1
            names = list(cats.keys())
            if 0 <= idx < len(names):
                provider = manager.providers[0]
                items = provider.get_homepage()
                print(f"\n{names[idx]}:\n")
                for i, item in enumerate(items[:20], 1):
                    print(f"{i}. {item['title']}")
