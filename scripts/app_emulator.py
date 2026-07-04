import requests
from bs4 import BeautifulSoup
import re
import json
import time
from urllib.parse import urljoin

class AppEmulator:
    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
        })
        self.providers = {
            'Uakino': 'https://uakino.best/',
            'Eneyida': 'https://eneyida.tv/'
        }

    def clean_title(self, text):
        if not text: return ""
        text = text.split("Жанр:")[0].split("Актори:")[0]
        text = re.sub(r'^[+-]?\d{1,8}(?:\s*/\s*\d*)?[- \s]*', '', text.strip())
        text = re.sub(r'\b(FHD|HD|SD|1080p|720p|4K|HDR|CAMRip|TS)\b', '', text, flags=re.I)
        return re.sub(r'\s+', ' ', text).strip()

    def fetch_home(self):
        all_items = []
        for name, base in self.providers.items():
            print(f"  -> Завантаження {name}...")
            try:
                resp = self.session.get(base, timeout=10)
                soup = BeautifulSoup(resp.text, 'html.parser')
                selector = '.movie-item, .short-item' if name == 'Uakino' else 'article.short'
                items = soup.select(selector)[:15]
                for el in items:
                    link = el.select_one('a[href]')
                    if not link: continue
                    title = link.get('title') or el.text.strip()
                    all_items.append({
                        'provider': name,
                        'title': self.clean_title(title),
                        'url': urljoin(base, link['href'])
                    })
            except: print(f"  ❌ Помилка завантаження {name}")
        return all_items

    def get_stream_from_player(self, embed_url, referer):
        """Емуляція UnifiedStreamProvider"""
        try:
            resp = self.session.get(embed_url, headers={'Referer': referer}, timeout=5)
            html = resp.text.replace('\\/', '/')
            # Шукаємо m3u8
            m3u8 = re.search(r'["\'](https?://[^"\']+\.m3u8[^"\']*)["\']', html, re.I)
            if m3u8: return m3u8.group(1)
            # Шукаємо file:
            file_match = re.search(r'file\s*[:=]\s*["\']([^"\'#]+)["\']', html)
            if file_match: return file_match.group(1)
        except: pass
        return None

    def fetch_details(self, item):
        print(f"\n{'-'*50}\n🔍 ДЕТАЛІ: {item['title']} ({item['provider']})")
        resp = self.session.get(item['url'], timeout=10)
        html = resp.text
        soup = BeautifulSoup(html, 'html.parser')

        # 1. Метадані
        desc = soup.select_one('article .full-text, .full-right .full-text, .full-info .full-text')
        print(f"📝 Опис: {desc.text.strip()[:150]}..." if desc else "📝 Опис відсутній")

        # 2. Пошук серій (AJAX - Uakino style)
        news_id = ""
        ni_m = re.search(r'(?:news_id|post_id)\s*[:=]\s*["\']?(\d+)', html)
        if ni_m: news_id = ni_m.group(1)

        user_hash = ""
        h_m = re.search(r'dle_login_hash\s*=\s*[\'"]([^\'"]+)[\'"]', html)
        if h_m: user_hash = h_m.group(1)

        episodes = []
        if news_id:
            endpoint = urljoin(self.providers[item['provider']], "engine/ajax/playlists.php")
            for field in ["playlist", "seria", "video"]:
                r = self.session.post(endpoint, data={
                    'news_id': news_id, 'action': 'playlists', 'xfield': field, 'user_hash': user_hash, 'area': 'news'
                }, headers={'Referer': item['url']}, timeout=5)
                if "li" in r.text:
                    ajax_soup = BeautifulSoup(r.text, 'html.parser')
                    for li in ajax_soup.select("li[data-file]"):
                        episodes.append({'title': li.text.strip(), 'url': li['data-file']})
                    if episodes: break

        # 3. Пошук плеєрів (Iframe - Eneyida style)
        iframes = []
        for ifr in soup.select('iframe'):
            src = ifr.get('src') or ifr.get('data-src') or ""
            if any(p in src for p in ["hdvb", "ashdi", "vidmoly", "mcloud"]):
                iframes.append(urljoin(item['url'], src))

        # 4. Вивід результатів
        if episodes:
            print(f"✅ ЗНАЙДЕНО СЕРІЙ: {len(episodes)}")
            for ep in episodes[:5]: # перші 5
                raw_url = ep['url'].split(']')[-1] if ']' in ep['url'] else ep['url']
                print(f"  - {ep['title']}: {raw_url[:60]}...")

        if iframes:
            print(f"📺 ЗНАЙДЕНО ПЛЕЄРІВ: {len(iframes)}")
            for ifr in iframes:
                stream = self.get_stream_from_player(ifr, item['url'])
                print(f"  - Плеєр: {ifr[:50]}...")
                if stream: print(f"    🚀 ПРЯМЕ ПОСИЛАННЯ: {stream[:70]}...")

        # 5. Агрегація сезонів
        other = soup.select(".franchise-list a, .serial-series a, .video-tabs a")
        if other:
            print(f"🔗 ІНШІ СЕЗОНИ: {len(other)} посилань знайдено")

    def run(self):
        print("\n" + "="*20 + " UKRTV EMULATOR " + "="*20)
        items = self.fetch_home()

        print("\nГОЛОВНА СТОРІНКА (Останні оновлення):")
        for i, item in enumerate(items):
            print(f"{i+1}. [{item['provider']}] {item['title']}")

        while True:
            choice = input("\nВведіть номер фільму (або 'q' для виходу): ")
            if choice.lower() == 'q': break
            try:
                idx = int(choice) - 1
                if 0 <= idx < len(items):
                    self.fetch_details(items[idx])
                else: print("❌ Невірний номер")
            except: print("❌ Введіть число")

if __name__ == "__main__":
    emulator = AppEmulator()
    emulator.run()
