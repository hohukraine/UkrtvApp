import requests
import re
import sys
import time

API_KEY = "f864833a695d73d9159048a126804f5e" # Public key for enrichment

def get_movies():
    movies = []
    with open('/Users/alex/Documents/UkrtvApp/app/src/main/java/ua/ukrtv/app/data/repository/Top200Repository.kt', 'r') as f:
        content = f.read()
        for match in re.finditer(r'Top200Movie\(', content):
            start = match.start()
            end = content.find('),', start)
            block = content[start:end+2]
            r = re.search(r'rank = (\d+)', block)
            t = re.search(r'title = "(.*?)"', block)
            o = re.search(r'originalTitle = "(.*?)"', block)
            c = re.search(r'comment = "(.*?)",', block, re.DOTALL)
            if r and t and o:
                movies.append({'rank': int(r.group(1)), 'title': t.group(1), 'original': o.group(1), 'comment': c.group(1) if c else ""})
    return movies

def get_tmdb_data(title, original):
    try:
        url = f"https://api.themoviedb.org/3/search/movie?api_key={API_KEY}&query={original}&language=uk-UA"
        res = requests.get(url).json()
        if not res['results']: return None
        movie = res['results'][0]

        # Get details for colors/genres
        details = requests.get(f"https://api.themoviedb.org/3/movie/{movie['id']}?api_key={API_KEY}&language=uk-UA").json()

        # We still need to scrape the page for THE EXACT CSS COLORS
        page_url = f"https://www.themoviedb.org/movie/{movie['id']}"
        r = requests.get(page_url, headers={"User-Agent": "Mozilla/5.0"})
        soup = BeautifulSoup(r.text, 'html.parser')
        style = soup.find('style')
        accent = "#08121c"
        on_accent = "#ffffff"
        if style and style.string:
            p_m = re.search(r"--primaryColor: rgba\((.*?), (.*?), (.*?), (.*?)\);", style.string)
            if p_m: accent = "#{:02x}{:02x}{:02x}".format(int(float(p_m.group(1))), int(float(p_m.group(2))), int(float(p_m.group(3))))
            c_m = re.search(r"--primaryColorContrast: (.*?);", style.string)
            if c_m: on_accent = c_m.group(1).strip()

        return {
            'year': movie.get('release_date', '')[:4],
            'rating': int(movie.get('vote_average', 0) * 10),
            'poster': f"https://image.tmdb.org/t/p/w500{movie.get('poster_path')}" if movie.get('poster_path') else "",
            'backdrop': f"https://image.tmdb.org/t/p/original{movie.get('backdrop_path')}" if movie.get('backdrop_path') else "",
            'genres': [g['name'] for g in details.get('genres', [])],
            'accent': accent,
            'on_accent': on_accent
        }
    except:
        return None

from bs4 import BeautifulSoup

def main():
    movies = get_movies()
    print("package ua.ukrtv.app.data.repository\nimport ua.ukrtv.app.domain.model.Top200Movie\nimport javax.inject.Inject\nimport javax.inject.Singleton\n@Singleton\nclass Top200Repository @Inject constructor() {\n    fun getTop200(): List<Top200Movie> {\n        return listOf(")
    for m in movies:
        data = get_tmdb_data(m['title'], m['original'])
        if data:
            code = f"""            Top200Movie(
                rank = {m['rank']},
                title = "{m['title']}",
                originalTitle = "{m['original']}",
                year = "{data['year']}",
                rating = {data['rating']},
                genres = listOf({', '.join([f'"{g}"' for g in data['genres']])}),
                comment = "{m['comment']}",
                posterUrl = "{data['poster']}",
                backdropUrl = "{data['backdrop']}",
                accentColor = "{data['accent']}",
                onAccentColor = "{data['on_accent']}",
                searchQueries = listOf("{m['title']}", "{m['original']}")
            ),"""
            print(code)
        else:
            print(f"            Top200Movie(rank={m['rank']}, title=\"{m['title']}\", originalTitle=\"{m['original']}\", comment=\"{m['comment']}\", accentColor=\"#08121c\", onAccentColor=\"#ffffff\"),")
        sys.stdout.flush()
    print("        )\n    }\n}")

if __name__ == "__main__":
    main()
