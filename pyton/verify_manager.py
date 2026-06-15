import sys
sys.path.insert(0, 'C:\\UkrtvApp')
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

from stream_manager import StreamManager, UakinoBestProvider, UakinoCxProvider, KinoukrProvider, EneyidaProvider, smart_search

manager = StreamManager()
manager.register(UakinoBestProvider())
manager.register(UakinoCxProvider())
manager.register(KinoukrProvider())
manager.register(EneyidaProvider())
manager.set_stream_priority(UakinoCxProvider)

# Test smart_search
print("=== Smart search: офісний роман ===")
result = smart_search(manager, "офісний роман", limit=5)
print(f"Знайдено: {len(result['results'])} результатів")
for i, r in enumerate(result['results'][:3], 1):
    print(f"{i}. [{r['provider']}] {r['title']}")
if result['stream']:
    print(f"\n✓ Стрім: {result['stream']['url']}")
    print(f"  Джерело: {result['stream']['source']}, Провайдер: {result['stream']['provider']}")
else:
    print("\n✗ Стрім не знайдено")
