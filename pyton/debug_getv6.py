import requests
import base64

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://uakino.best/"
}

# Decode the base64 URL
encoded = "aHR0cHM6Ly9nZXQ2LmZ1bi9wb2ludC8/bWV0aG9kPXZpZGVvX2xpbmsyJnhsPQ=="
decoded = base64.b64decode(encoded).decode('utf-8')
print(f"Decoded URL base: {decoded}")

# The full function builds: base + btoa(btoa(a))
# So the API endpoint is: https://getv6.fun/point/?method=video_link2&xl=<double_base64_param>

# Now search for calls to mbn2r056csd3 in the HTML
url = "https://uakino.best/filmy/genre_drama/62-syayvo-yevropeyske-vidannya.html"
r = requests.get(url, headers=headers, timeout=15)
html = r.text

# Find where mbn2r056csd3 is called
import re
calls = re.findall(r'mbn2r056csd3\(([^)]+)\)', html)
print(f"\nCalls to mbn2r056csd3: {calls}")

# Also search for getv6
getv6_refs = re.findall(r'getv6[^\s"\'\\<]*', html)
print(f"\ngetv6 references: {getv6_refs}")

# Search for any hash/ID patterns that might be the parameter
hash_patterns = re.findall(r'["\']([a-f0-9]{32})["\']', html)
print(f"\n32-char hex strings: {hash_patterns[:20]}")

# Look for the playlist data structure in the page
# The playlists-ajax div might have data that needs to be sent to an endpoint
print("\n=== Looking for playlist init data ===")
soup = BeautifulSoup(html, "html.parser")
pre_div = soup.select_one("#pre")
if pre_div:
    print(f"pre div: {pre_div.attrs}")
    # Check parent for more context
    parent = pre_div.parent
    print(f"parent: {parent.name}, class: {parent.get('class')}")
    print(f"parent HTML (first 500): {str(parent)[:500]}")

# Try to find the xfield name from the page
print("\n=== Searching for xfield ===")
xf_matches = re.findall(r'xfname[=:\s]*["\']?(\w+)', html, re.I)
print(f"xfname values: {xf_matches}")

# The playlists-ajax div has data-xfname="playlist" and data-news_id="62"
# Maybe we need to look up the actual xfield ID
print("\n=== Looking for custom fields ===")
# Search for any data related to xfields
xfield_data = re.findall(r'data-xfield[^>]*>', html)
print(f"xfield data: {xfield_data}")

# Maybe there's a separate endpoint that returns playlist data
# Let's try the endpoint with the actual xfield name from the database
# The xfield name "playlist" might need to be resolved to an ID
print("\n=== Trying xfield ID 1 ===")
try:
    r2 = requests.get(
        "https://uakino.best/engine/ajax/controller.php?mod=playlists&xfname=playlist&news_id=62&skin=uakino&xfield_id=1",
        headers=headers,
        timeout=10
    )
    print(f"Status: {r2.status_code}, len: {len(r2.text)}")
    if r2.status_code == 200 and r2.text.strip() != '{"success":false,"message":"ERR_XFIELD_NAME"}':
        print(f"Content: {r2.text[:500]}")
except Exception as e:
    print(f"Error: {e}")
