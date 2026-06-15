import sys
sys.path.insert(0, 'C:\\UkrtvApp')
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

from test3 import get_homepage, get_stream, browse_category

# Test homepage
movies = get_homepage(5)
print(f"Головна сторінка: {len(movies)} фільмів")
for i, m in enumerate(movies[:3], 1):
    print(f"{i}. {m['title']} -> {m['url']}")

# Test stream for first movie
if movies:
    print(f"\nОтримуємо стрім для: {movies[0]['title']}")
    stream = get_stream(movies[0]['url'])
    if stream:
        print(f"\n✓ Стрім: {stream['url']}")
        print(f"  Тип: {stream['type']}, Джерело: {stream['source']}")
    else:
        print("\n✗ Стрім не знайдено")
