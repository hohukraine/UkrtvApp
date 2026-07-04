import requests
from bs4 import BeautifulSoup
import re
import json
import time

class ParsingStrategy:
    def extract_movies(self, html, base_url):
        pass
    def extract_details(self, html, url):
        pass

class DleStrategy(ParsingStrategy):
    def __init__(self, selectors):
        self.selectors = selectors

    def extract_movies(self, html, base_url):
        soup = BeautifulSoup(html, 'html.parser')
        items = soup.select(self.selectors['item'])
        results = []
        for item in items:
            link = item.select_one('a[href]')
            if not link: continue
            url = link['href']
            if not url.startswith('http'): url = base_url.rstrip('/') + '/' + url.lstrip('/')

            title_el = item.select_one(self.selectors['title'])
            title = title_el.text.strip() if title_el else ""

            results.append({'title': title, 'url': url})
        return results

    def extract_details(self, html, url):
        soup = BeautifulSoup(html, 'html.parser')
        title = soup.select_one('h1').text.strip() if soup.select_one('h1') else "Unknown"

        # Шукаємо ознаки серіалу
        is_series = any(x in html.lower() for x in ['серія', 'сезон', 'episode', 'playlist'])

        # Спроба знайти список серій в HTML (Tab-based)
        episodes = []
        items = soup.select('li[data-file], li[data-url], .video-tabs li, .player-tabs li, .series-tabs li')
        for idx, item in enumerate(items):
            file = item.get('data-file') or item.get('data-url') or ""
            ep_title = item.text.strip() or f"Episode {idx+1}"
            if file:
                episodes.append({'title': ep_title, 'file': file})

        return {
            'title': title,
            'url': url,
            'is_series': is_series,
            'episodes_count': len(episodes),
            'episodes': episodes
        }

class ResolutionChain:
    def __init__(self, strategies):
        self.strategies = strategies

    def resolve(self, url, html):
        for strategy in self.strategies:
            result = strategy(url, html)
            if result: return result
        return None

def iframe_strategy(url, html):
    iframes = re.findall(r'<iframe[^>]+src=["\']([^"\']+)["\']', html)
    # Також шукаємо data-src
    iframes += re.findall(r'<iframe[^>]+data-src=["\']([^"\']+)["\']', html)
    return [f for f in iframes if 'google' not in f and 'facebook' not in f]

def script_strategy(url, html):
    # Шукаємо посилання на m3u8 або плейлисти в скриптах
    links = re.findall(r'["\'](https?://[^"\']+\.(?:m3u8|mp4|json))["\']', html)
    return links

# Конфігурації провайдерів
PROVIDERS = {
    'Uakino': {
        'base_url': 'https://uakino.best/',
        'selectors': {
            'item': '.movie-item, .short-item',
            'title': '.movie-title, .short-title'
        },
        'test_urls': [
            'https://uakino.best/seriesss/fantastic_series/33511-zzovni-4-sezon.html',
            'https://uakino.best/filmy/adventure/32953-viana-2.html'
        ]
    },
    'Eneyida': {
        'base_url': 'https://eneyida.tv/',
        'selectors': {
            'item': 'article.short',
            'title': 'a.short_title'
        },
        'test_urls': [
            'https://eneyida.tv/7026-zzovni.html',
            'https://eneyida.tv/9885-duzhe-dyvni-dyva-5-sezon.html'
        ]
    }
}

def run_tests():
    headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}

    for name, config in PROVIDERS.items():
        print(f"\n=== Testing Provider: {name} ===")
        strategy = DleStrategy(config['selectors'])

        for url in config['test_urls']:
            print(f"Testing URL: {url}")
            try:
                resp = requests.get(url, headers=headers, timeout=10)
                if resp.status_code != 200:
                    print(f"  [ERROR] Status code: {resp.status_code}")
                    continue

                details = strategy.extract_details(resp.text, url)
                print(f"  Title: {details['title']}")
                print(f"  Is Series: {details['is_series']}")
                print(f"  Episodes Found: {details['episodes_count']}")

                ifframes = iframe_strategy(url, resp.text)
                print(f"  Iframes (Potential Players): {len(ifframes)}")
                for f in ifframes:
                    print(f"    - {f[:80]}...")

                scripts = script_strategy(url, resp.text)
                if scripts:
                    print(f"  Direct Links in Scripts: {len(scripts)}")

            except Exception as e:
                print(f"  [CRITICAL ERROR] {str(e)}")

            time.sleep(1)

if __name__ == "__main__":
    run_tests()
