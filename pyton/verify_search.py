import sys
sys.path.insert(0, 'C:\\UkrtvApp')
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

from test import search, get_stream

# Test search for "друзі"
results = search("друзі")
print(f"Запит: друзі")
print(f"Знайдено: {len(results)} результатів")
for i, r in enumerate(results[:5], 1):
    print(f"{i}. {r['title']}")
    print(f"   {r['url']}")
