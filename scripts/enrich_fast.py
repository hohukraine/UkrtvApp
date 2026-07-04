import requests
from bs4 import BeautifulSoup
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
            r = re.search(r'rank = (\d+)', block)
            t = re.search(r'title = "(.*?)"', block)
            o = re.search(r'originalTitle = "(.*?)"', block)
            c = re.search(r'comment = "(.*?)",', block, re.DOTALL)
            if r and t and o:
                movies.append({'rank': int(r.group(1)), 'title': t.group(1), 'original': o.group(1), 'comment': c.group(1) if c else ""})
    return movies

def enrich_movie(m, session):
    try:
        # Прямий пошук по TMDB
        url = f"https://www.themoviedb.org/search?query={m['original'].replace(' ', '+')}"
        r = session.get(url, timeout=5)
        soup = BeautifulSoup(r.text, 'html.parser')
        link = soup.find('a', class_='result') or soup.find('a', {'data-id': True})
        if not link: return None

        m_url = "https://www.themoviedb.org" + link['href'] + "?language=uk-UA"
        r = session.get(m_url, timeout=5)
        soup = BeautifulSoup(r.text, 'html.parser')

        style = soup.find('style')
        s_text = style.string if style else ""
        accent = "#08121c"
        on_accent = "#ffffff"

        p_m = re.search(r"--primaryColor: rgba\((.*?), (.*?), (.*?), (.*?)\);", s_text)
        if p_m: accent = "#{:02x}{:02x}{:02x}".format(int(float(p_m.group(1))), int(float(p_m.group(2))), int(float(p_m.group(3))))

        c_m = re.search(r"--primaryColorContrast: (.*?);", s_text)
        if c_m: on_accent = c_m.group(1).strip()

        # Poster / Backdrop
        img = soup.find('img', class_='poster')
        p_url = ""
        if img:
            p_src = img.get('src', '')
            p_url = "https://image.tmdb.org/t/p/w500" + p_src[p_src.find('/t/p/')+len('/t/p/w300_and_h450_face/'):]

        bg_match = re.search(r"background-image: url\('(.*?)'\)", s_text)
        b_url = ""
        if bg_match:
            bg_raw = bg_match.group(1)
            b_url = "https://image.tmdb.org/t/p/original" + bg_raw[bg_raw.find('/t/p/')+len('/t/p/w1920_and_h800_multi_faces/'):]

        # Meta
        chart = soup.find('div', class_='user_score_chart')
        rating = int(chart.get('data-percent', 0)) if chart else 0
        tag = soup.find('span', class_='tag release_date')
        year = tag.text.strip('()') if tag else ""

        return {
            'rank': m['rank'], 'title': m['title'], 'original': m['original'],
            'year': year, 'rating': rating, 'accent': accent, 'on_accent': on_accent,
            'poster': p_url, 'backdrop': b_url, 'comment': m['comment']
        }
    except:
        return None

def main():
    movies = get_movies()
    session = requests.Session()
    session.headers.update({"User-Agent": "Mozilla/5.0"})

    print("listOf(", flush=True)
    for m in movies:
        data = enrich_movie(m, session)
        if data:
            code = f"""            Top200Movie(
                rank = {data['rank']},
                title = "{data['title']}",
                originalTitle = "{data['original']}",
                year = "{data['year']}",
                rating = {data['rating']},
                comment = "{data['comment']}",
                posterUrl = "{data['poster']}",
                backdropUrl = "{data['backdrop']}",
                accentColor = "{data['accent']}",
                onAccentColor = "{data['on_accent']}",
                searchQueries = listOf("{data['title']}", "{data['original']}")
            ),"""
            print(code, flush=True)
        else:
            # Fallback
            print(f"            Top200Movie(rank={m['rank']}, title=\"{m['title']}\", originalTitle=\"{m['original']}\", comment=\"{m['comment']}\", accentColor=\"#08121c\", onAccentColor=\"#ffffff\"),")
    print(")", flush=True)

if __name__ == "__main__":
    main()
