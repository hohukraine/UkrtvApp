import sys
sys.path.insert(0, 'C:\\UkrtvApp')
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

from stream_manager import UakinoBestProvider, UakinoCxProvider

best = UakinoBestProvider()
cx = UakinoCxProvider()

# Test search on both
print("=== UakinoBest search: офісний роман ===")
results_best = best.search("офісний роман", limit=3)
for r in results_best:
    print(f"  {r['title']} -> {r['url']}")

print("\n=== UakinoCx search: офісний роман ===")
results_cx = cx.search("офісний роман", limit=3)
for r in results_cx:
    print(f"  {r['title']} -> {r['url']}")

# Test stream for first result from each
if results_best:
    print(f"\n=== Stream from UakinoBest: {results_best[0]['url']} ===")
    stream = best.get_stream(results_best[0]['url'])
    if stream:
        print(f"✓ {stream['url']}")
    else:
        print("✗ not found")

if results_cx:
    print(f"\n=== Stream from UakinoCx: {results_cx[0]['url']} ===")
    stream = cx.get_stream(results_cx[0]['url'])
    if stream:
        print(f"✓ {stream['url']}")
    else:
        print("✗ not found")
