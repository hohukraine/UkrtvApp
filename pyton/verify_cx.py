import sys
sys.path.insert(0, 'C:\\UkrtvApp')
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

from test1 import search, get_stream

# Test search
results = search("Люблю, скучаю")
print(f"Запит: Люблю, скучаю")
print(f"Знайдено: {len(results)} результатів")
for i, r in enumerate(results[:3], 1):
    print(f"{i}. {r['title']}")
    print(f"   {r['url']}")

if results:
    print(f"\nОтримуємо стрім для: {results[0]['title']}")
    stream = get_stream(results[0]["url"])
    if stream:
        print(f"\n✓ Стрім: {stream['url']}")
        print(f"  Тип: {stream['type']}, Джерело: {stream['source']}")
    else:
        print("\n✗ Стрім не знайдено")
