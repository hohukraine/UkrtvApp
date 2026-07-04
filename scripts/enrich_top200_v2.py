import requests
from bs4 import BeautifulSoup
import time
import re
import sys

def get_current_movies():
    movies = []
    try:
        with open('/Users/alex/Documents/UkrtvApp/app/src/main/java/ua/ukrtv/app/data/repository/Top200Repository.kt', 'r') as f:
            content = f.read()
            # Пошук за допомогою finditer для надійності
            for match in re.finditer(r'Top200Movie\(', content):
                start = match.start()
                # Знаходимо закриваючу дужку блоку
                end = content.find('),', start)
                block = content[start:end]

                rank_m = re.search(r'rank = (\d+)', block)
                title_m = re.search(r'title = "(.*?)"', block)
                orig_m = re.search(r'originalTitle = "(.*?)"', block)

                if rank_m and title_m and orig_m:
                    movies.append({
                        'rank': int(rank_m.group(1)),
                        'title': title_m.group(1),
                        'original': orig_m.group(1)
                    })
    except Exception as e:
        sys.stderr.write(f"!! Error reading repository: {e}\n")
    return movies

def get_comment_for_rank(rank):
    try:
        with open('/Users/alex/Documents/UkrtvApp/app/src/main/java/ua/ukrtv/app/data/repository/Top200Repository.kt', 'r') as f:
            content = f.read()
            match = re.search(rf'rank = {rank}.*?comment = "(.*?)",', content, re.DOTALL)
            return match.group(1) if match else ""
    except:
        return ""

def enrich():
    movies = get_current_movies()
    if not movies:
        sys.stderr.write("!! No movies found. Check parser.\n")
        return

    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
    session = requests.Session()
    session.headers.update(headers)

    print("package ua.ukrtv.app.data.repository\n", flush=True)
    print("import ua.ukrtv.app.domain.model.Top200Movie", flush=True)
    print("import javax.inject.Inject", flush=True)
    print("import javax.inject.Singleton\n", flush=True)
    print("@Singleton\nclass Top200Repository @Inject constructor() {\n", flush=True)
    print("    fun getTop200(): List<Top200Movie> {\n        return listOf(", flush=True)

    for m in movies:
        # sys.stderr.write(f"Processing Rank {m['rank']}: {m['title']}\n")
        tmdb_url = ""
        try:
            search_url = f"https://www.themoviedb.org/search/movie?query={m['original'].replace(' ', '+')}"
            r = session.get(search_url, timeout=10)
            soup = BeautifulSoup(r.text, 'html.parser')

            results = soup.find_all('a', class_='result')
            if results: tmdb_url = "https://www.themoviedb.org" + results[0]['href']
            else:
                item = soup.find('a', {'data-id': True})
                if item: tmdb_url = "https://www.themoviedb.org" + item['href']

            if tmdb_url:
                if '?' not in tmdb_url: tmdb_url += "?language=uk-UA"
                else: tmdb_url += "&language=uk-UA"

                r = session.get(tmdb_url, timeout=10)
                soup = BeautifulSoup(r.text, 'html.parser')

                poster = ""
                img = soup.find('img', class_='poster')
                if img:
                    poster = img.get('src', '')
                    if 'w300' in poster: poster = poster.replace('w300', 'w500')
                    if not poster.startswith('http'): poster = "https://image.tmdb.org" + poster

                backdrop = ""
                style_tag = soup.find('style')
                accent = "#08121c"
                on_accent = "#ffffff"
                if style_tag:
                    style_str = style_tag.string if style_tag.string else ""
                    bg_match = re.search(r"background-image: url\('(.*?)'\)", style_str)
                    if bg_match: backdrop = bg_match.group(1).replace('w1920_and_h800_multi_faces', 'original')

                    p_m = re.search(r"--primaryColor: rgba\((.*?), (.*?), (.*?), (.*?)\);", style_str)
                    if p_m:
                        red, g, b = float(p_m.group(1)), float(p_m.group(2)), float(p_m.group(3))
                        accent = "#{:02x}{:02x}{:02x}".format(int(red), int(g), int(b))

                    c_m = re.search(r"--primaryColorContrast: (.*?);", style_str)
                    if c_m: on_accent = c_m.group(1).strip()

                rating = 0
                chart = soup.find('div', class_='user_score_chart')
                if chart: rating = int(chart.get('data-percent', 0))

                year = ""
                y_s = soup.find('span', class_='tag release_date')
                if y_s: year = y_s.text.strip('()')

                director = ""
                profiles = soup.find_all('li', class_='profile')
                for p in profiles:
                    if "Director" in p.text:
                        director = p.find('a').text if p.find('a') else p.find('p').text.replace('Director', '').strip()
                        break

                genres = []
                g_s = soup.find('span', class_='genres')
                if g_s: genres = [a.text for a in g_s.find_all('a')]

                comment = get_comment_for_rank(m['rank'])

                print(f"""            Top200Movie(
                rank = {m['rank']},
                title = "{m['title']}",
                originalTitle = "{m['original']}",
                year = "{year}",
                rating = {rating},
                genres = listOf({', '.join([f'"{g}"' for g in genres])}),
                director = "{director}",
                comment = "{comment}",
                posterUrl = "{poster}",
                backdropUrl = "{backdrop}",
                accentColor = "{accent}",
                onAccentColor = "{on_accent}",
                searchQueries = listOf("{m['title']}", "{m['original']}")
            ),""", flush=True)

            time.sleep(0.1)
        except Exception:
            pass

    print("        )\n    }\n\n    fun getRandom5(): List<Top200Movie> {\n        return getTop200().shuffled().take(5)\n    }\n}", flush=True)

enrich()
