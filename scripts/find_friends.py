import requests
from bs4 import BeautifulSoup
import re
from urllib.parse import urljoin

class FriendsFinder:
    def __init__(self, name, base_url):
        self.name = name
        self.base_url = base_url
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
        })

    def get_hash(self):
        try:
            resp = self.session.get(self.base_url, timeout=5)
            m = re.search(r'dle_login_hash\s*=\s*[\'"]([^\'"]+)[\'"]', resp.text)
            return m.group(1) if m else ""
        except: return ""

    def search_friends(self):
        print(f"\n[{self.name}] Searching for 'Друзі'...")
        # Try AJAX search as it is usually more precise for titles on DLE
        ajax_url = urljoin(self.base_url, "engine/ajax/search.php")
        try:
            resp = self.session.post(ajax_url, data={'query': 'Друзі', 'user_hash': self.get_hash()},
                                    headers={'X-Requested-With': 'XMLHttpRequest'}, timeout=5)
            soup = BeautifulSoup(resp.text, 'html.parser')
            links = soup.select('a')

            # Find the main "Friends" link (exclude movies with 'друзі' in title if possible)
            candidates = []
            for a in links:
                title = a.text.strip().lower()
                href = urljoin(self.base_url, a['href'])
                # Look for exact match or typical series URL
                if title == "друзі" or "друзі (1-10 сезон)" in title or "113-druzi" in href or "70-druzi" in href:
                    candidates.append({'title': a.text.strip(), 'url': href})

            if not candidates and links:
                candidates = [{'title': links[0].text.strip(), 'url': urljoin(self.base_url, links[0]['href'])}]

            return candidates
        except: return []

    def count_seasons(self, url):
        print(f"  Visiting: {url}")
        try:
            resp = self.session.get(url, timeout=10)
            soup = BeautifulSoup(resp.text, 'html.parser')

            # Look for seasons in franchise/tabs
            seasons = set()
            # 1. Search in common DLE containers
            containers = soup.select(".franchise-list, .serial-series, .video-tabs, .player-tabs, .series-tabs, .tabs-sel")
            target = containers if containers else [soup]

            for container in target:
                for a in container.select("a[href]"):
                    t = a.text.lower()
                    if "сезон" in t or "season" in t or " s" in t:
                        m = re.search(r'(\d+)', t)
                        if m: seasons.add(int(m.group(1)))

            # 2. Search for playlist items (AJAX)
            news_id_match = re.search(r'news_id\s*[:=]\s*["\']?(\d+)', resp.text)
            if news_id_match:
                news_id = news_id_match.group(1)
                endpoint = urljoin(self.base_url, "engine/ajax/playlists.php")
                r = self.session.post(endpoint, data={'news_id': news_id, 'action': 'playlists', 'xfield': 'playlist'}, timeout=5)
                # If JSON, count folders
                if r.text.startswith('{') or r.text.startswith('['):
                    try:
                        data = r.json()
                        resp_content = data.get('response', '')
                        if 'folder' in resp_content or '"title"' in resp_content:
                            # It's a structured playlist, might contain seasons
                            s_matches = re.findall(r'сезон\s*(\d+)', resp_content, re.I)
                            for s in s_matches: seasons.add(int(s))
                    except: pass

            if not seasons:
                # Check title for range like (1-10 сезон)
                range_match = re.search(r'1-(\d+)\s*сезон', soup.text)
                if range_match: return int(range_match.group(1))
                return 1 # Fallback to 1 if it's a series page

            return max(seasons) if seasons else 1
        except: return 0

if __name__ == "__main__":
    providers = [
        ('Uakino', 'https://uakino.best/'),
        ('Eneyida', 'https://eneyida.tv/')
    ]

    for name, base in providers:
        finder = FriendsFinder(name, base)
        results = finder.search_friends()
        if results:
            best_url = results[0]['url']
            total = finder.count_seasons(best_url)
            print(f"  [RESULT] '{results[0]['title']}' on {name} has {total} seasons.")
        else:
            print(f"  [RESULT] Series 'Friends' not found on {name}.")
