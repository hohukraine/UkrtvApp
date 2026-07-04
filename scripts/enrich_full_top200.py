import requests
from bs4 import BeautifulSoup
import time
import re
import sys

def get_movies():
    movies = []
    with open('/Users/alex/Documents/UkrtvApp/app/src/main/java/ua/ukrtv/app/data/repository/Top200Repository.kt', 'r') as f:
        content = f.read()
        for match in re.finditer(r'Top200Movie\(', content):
            start = match.start()
            end = content.find('),', start)
            block = content[start:end+2]

            rank = re.search(r'rank = (\d+)', block)
            title = re.search(r'title = "(.*?)"', block)
            orig = re.search(r'originalTitle = "(.*?)"', block)
            comm = re.search(r'comment = "(.*?)",', block, re.DOTALL)

            if rank and title and orig:
                movies.append({
                    'rank': int(rank.group(1)),
                    'title': title.group(1).strip(),
                    'original': orig.group(1).strip(),
                    'comment': comm.group(1) if comm else ""
                })
    return movies

def enrich():
    movies = get_movies()
    session = requests.Session()
    session.headers.update({"User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"})

    output_path = '/Users/alex/Documents/UkrtvApp/app/src/main/java/ua/ukrtv/app/data/repository/Top200Repository.kt.new'

    with open(output_path, 'w') as out:
        out.write("package ua.ukrtv.app.data.repository\n\n")
        out.write("import ua.ukrtv.app.domain.model.Top200Movie\n")
        out.write("import javax.inject.Inject\n")
        out.write("import javax.inject.Singleton\n\n")
        out.write("@Singleton\nclass Top200Repository @Inject constructor() {\n\n")
        out.write("    fun getTop200(): List<Top200Movie> {\n        return listOf(\n")

        for m in movies:
            sys.stderr.write(f"[{m['rank']}/200] {m['title']}... ")
            sys.stderr.flush()

            accent = "#08121c"
            on_accent = "#ffffff"
            poster = ""
            backdrop = ""
            rating = 0
            year = ""
            director = ""
            genres = []

            try:
                # Search TMDB
                s_url = f"https://www.themoviedb.org/search?query={m['original'].replace(' ', '+')}"
                r = session.get(s_url, timeout=10)
                soup = BeautifulSoup(r.text, 'html.parser')

                link = soup.find('a', class_='result')
                if not link: link = soup.find('a', {'data-id': True})

                if link:
                    movie_url = "https://www.themoviedb.org" + link['href'] + "?language=uk-UA"
                    mr = session.get(movie_url, timeout=10)
                    msoup = BeautifulSoup(mr.text, 'html.parser')

                    # 1. Colors
                    style = msoup.find('style')
                    if style:
                        s_text = style.string
                        p_m = re.search(r"--primaryColor: rgba\((.*?), (.*?), (.*?), (.*?)\);", s_text)
                        if p_m:
                            accent = "#{:02x}{:02x}{:02x}".format(int(float(p_m.group(1))), int(float(p_m.group(2))), int(float(p_m.group(3))))
                        c_m = re.search(r"--primaryColorContrast: (.*?);", s_text)
                        if c_m: on_accent = c_m.group(1).strip()

                    # 2. Images
                    img = msoup.find('img', class_='poster')
                    if img:
                        p_src = img.get('src', '')
                        poster = "https://image.tmdb.org/t/p/w500" + p_src[p_src.find('/t/p/')+len('/t/p/w300_and_h450_face/'):]

                    bg_style = str(style.string) if style else ""
                    bg_match = re.search(r"background-image: url\('(.*?)'\)", bg_style)
                    if bg_match:
                        b_url = bg_match.group(1)
                        backdrop = "https://image.tmdb.org/t/p/original" + b_url[b_url.find('/t/p/')+len('/t/p/w1920_and_h800_multi_faces/'):]

                    # 3. Meta
                    chart = msoup.find('div', class_='user_score_chart')
                    if chart: rating = int(chart.get('data-percent', 0))

                    tag = msoup.find('span', class_='tag release_date')
                    if tag: year = tag.text.strip('()')

                    for p in msoup.find_all('li', class_='profile'):
                        if "Director" in p.text:
                            director = p.find('a').text if p.find('a') else p.find('p').text.replace('Director','').strip()
                            break

                    g_span = msoup.find('span', class_='genres')
                    if g_span: genres = [a.text for a in g_span.find_all('a')]

                out.write(f"""            Top200Movie(
                rank = {m['rank']},
                title = "{m['title']}",
                originalTitle = "{m['original']}",
                year = "{year}",
                rating = {rating},
                genres = listOf({', '.join([f'"{g}"' for g in genres])}),
                director = "{director}",
                comment = "{m['comment']}",
                posterUrl = "{poster}",
                backdropUrl = "{backdrop}",
                accentColor = "{accent}",
                onAccentColor = "{on_accent}",
                searchQueries = listOf("{m['title']}", "{m['original']}")
            ),\n""")
                sys.stderr.write("Done\n")
            except Exception as e:
                sys.stderr.write(f"Error: {e}\n")
                # Fallback on error
                out.write(f"            Top200Movie(rank={m['rank']}, title=\"{m['title']}\", originalTitle=\"{m['original']}\", comment=\"{m['comment']}\"),\n")

            out.flush()
            time.sleep(0.5)

        out.write("        )\n    }\n\n    fun getRandom5(): List<Top200Movie> {\n        return getTop200().shuffled().take(5)\n    }\n}\n")

if __name__ == "__main__":
    enrich()
