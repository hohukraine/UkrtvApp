import requests
import re
import sys

API_KEY = "f864833a695d73d9159048a126804f5e"

def get_accent_for_movie(original):
    try:
        # Search
        url = f"https://api.themoviedb.org/3/search/movie?api_key={API_KEY}&query={original}"
        res = requests.get(url).json()
        if not res['results']: return "#08121c"
        mid = res['results'][0]['id']

        # Scrape page for the REAL accent (API doesn't provide it)
        r = requests.get(f"https://www.themoviedb.org/movie/{mid}", headers={"User-Agent": "Mozilla/5.0"})
        m = re.search(r"--primaryColor: rgba\((.*?), (.*?), (.*?), (.*?)\);", r.text)
        if m:
            return "#{:02x}{:02x}{:02x}".format(int(float(m.group(1))), int(float(m.group(2))), int(float(m.group(3))))
        return "#08121c"
    except:
        return "#08121c"

def main():
    path = '/Users/alex/Documents/UkrtvApp/app/src/main/java/ua/ukrtv/app/data/repository/Top200Repository.kt'
    with open(path, 'r') as f:
        content = f.read()

    # Find all movies and update accentColor
    updated = content
    movies = re.findall(r'rank = (\d+),.*?title = "(.*?)",.*?originalTitle = "(.*?)",', content, re.DOTALL)

    for rank, title, original in movies:
        sys.stderr.write(f"Updating {rank}: {title}\n")
        accent = get_accent_for_movie(original)
        # Regex to replace accentColor for THIS specific rank
        pattern = rf'(rank = {rank},.*?accentColor = ")(#.*?)(")'
        updated = re.sub(pattern, rf'\1{accent}\3', updated, flags=re.DOTALL)

    with open(path, 'w') as f:
        f.write(updated)

if __name__ == "__main__":
    main()
