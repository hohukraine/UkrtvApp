import sys
sys.path.insert(0, 'C:\\UkrtvApp')
from test import search, get_stream

results = search("Дюна")
print(f"Знайдено: {len(results)} результатів")
for i, r in enumerate(results[:3], 1):
    print(f"{i}. {r['title']}: {r['url']}")

if results:
    print(f"\nОтримуємо стрім для: {results[0]['title']}")
    stream = get_stream(results[0]['url'])
    if stream:
        print(f"\nСтрім знайдено: {stream['url']}")
        print(f"Тип: {stream['type']}, Джерело: {stream['source']}")
    else:
        print("\nСтрім не знайдено.")
