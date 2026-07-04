import requests
from bs4 import BeautifulSoup
import re
from urllib.parse import urljoin

class SearchDiagnosticTool:
    def __init__(self, name, base_url):
        self.name = name
        self.base_url = base_url
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
        })

    def get_user_hash(self):
        """Намагається знайти dle_hash для пошуку"""
        try:
            resp = self.session.get(self.base_url, timeout=10)
            m = re.search(r'dle_login_hash\s*=\s*[\'"]([^\'"]+)[\'"]', resp.text)
            return m.group(1) if m else ""
        except: return ""

    def test_standard_search(self, query):
        print(f"\n  [Standard Search] Query: '{query}'")
        url = urljoin(self.base_url, "index.php?do=search")
        data = {
            'do': 'search',
            'subaction': 'search',
            'search_start': '1',
            'full_search': '0',
            'result_from': '1',
            'story': query,
            'user_hash': self.get_user_hash()
        }
        try:
            resp = self.session.post(url, data=data, timeout=10)
            soup = BeautifulSoup(resp.text, 'html.parser')
            # Шукаємо результати (зазвичай посилання)
            results = soup.select('a[href*=".html"]')
            unique_results = []
            seen_urls = set()

            for a in results:
                href = a['href']
                title = a.text.strip()
                if '.html' in href and len(title) > 2 and href not in seen_urls:
                    unique_results.append({'title': title, 'url': href})
                    seen_urls.add(href)

            print(f"    Found {len(unique_results)} items.")
            for r in unique_results[:3]:
                print(f"    - {r['title']} -> {r['url'][:60]}...")
            return len(unique_results)
        except Exception as e:
            print(f"    ❌ FAILED: {e}")
            return 0

    def test_ajax_search(self, query):
        print(f"\n  [AJAX Search] Query: '{query}'")
        url = urljoin(self.base_url, "engine/ajax/search.php")
        data = {'query': query, 'user_hash': self.get_user_hash()}
        try:
            # AJAX зазвичай вимагає правильний Referer та заголовок
            headers = {'X-Requested-With': 'XMLHttpRequest', 'Referer': self.base_url}
            resp = self.session.post(url, data=data, headers=headers, timeout=5)

            # AJAX повертає або HTML (список li), або порожнечу
            if len(resp.text) < 10:
                print("    ⏳ Returned empty result (Too short).")
                return 0

            soup = BeautifulSoup(resp.text, 'html.parser')
            results = soup.select('a')
            print(f"    Found {len(results)} items in quick search.")
            for r in results[:3]:
                print(f"    - {r.text.strip()} -> {r['href'][:60]}...")
            return len(results)
        except Exception as e:
            print(f"    ❌ FAILED: {e}")
            return 0

def run_diagnostic():
    queries = ["Ззовні", "Віяна 2", "Хлопаки"]
    providers = [
        ('Uakino', 'https://uakino.best/'),
        ('Eneyida', 'https://eneyida.tv/')
    ]

    for name, base_url in providers:
        print(f"\n{'='*20} TESTING SEARCH: {name} {'='*20}")
        tool = SearchDiagnosticTool(name, base_url)
        for q in queries:
            tool.test_standard_search(q)
            tool.test_ajax_search(q)

if __name__ == "__main__":
    run_diagnostic()
