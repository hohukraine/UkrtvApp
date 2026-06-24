import re
from typing import Optional
from urllib.parse import urlparse
from bs4 import BeautifulSoup, Tag

from .config import ProviderProfile
from .models import Movie, MovieDetail


class DleParser:
    def __init__(self, profile: ProviderProfile):
        self.profile = profile

    def parse_list(self, html: str) -> list[Movie]:
        soup = BeautifulSoup(html, "html.parser")
        items = soup.select(self.profile.selectors.item)
        results = []
        for el in items:
            link = el.select_one("a[href]")
            if not link:
                continue
            url = self._abs_url(link.get("href", ""))

            title_el = el.select_one(self.profile.selectors.title)
            title = self._extract_title(title_el, link, el)

            if not title or not title.strip():
                continue

            rating_el = el.select_one(
                ".rating-num, .imdb-rating, .kp-rating, .rate_res, "
                ".meta-item:contains(IMDB), .meta-item:contains(КП)"
            )
            rating = ""
            if rating_el:
                rating = rating_el.get_text(strip=True)
                rating = rating.replace("IMDB:", "").replace("КП:", "").strip()[:4]

            poster = self._extract_poster(el)

            year_match = re.search(r"\b(19|20)\d{2}\b", el.get_text())
            year = year_match.group(0) if year_match else None

            content_type = self._detect_type(url, el)

            results.append(Movie(
                id=str(hash(url)),
                title=self._clean_title(title),
                poster=poster,
                year=year,
                page_url=url,
                type=content_type,
                rating=rating
            ))
        return results

    def _extract_title(self, title_el: Optional[Tag], link: Tag, container: Tag) -> str:
        if title_el:
            b_or_h2 = title_el.select_one("b, h2")
            if b_or_h2:
                return b_or_h2.get_text(strip=True)
            return title_el.get_text(strip=True)
        title_attr = link.get("title", "")
        if title_attr:
            return title_attr
        img = container.select_one("img")
        if img and img.get("alt"):
            return img["alt"]
        return link.get_text(strip=True)

    def _extract_poster(self, el: Tag) -> str:
        img = el.select_one(self.profile.selectors.poster)
        if not img:
            img = el.select_one("img")
        if not img:
            return ""
        for attr in ("data-src", "src", "data-original"):
            val = img.get(attr, "")
            if val:
                if val.startswith("//"):
                    return "https:" + val
                if val.startswith("http"):
                    return val
                if val.startswith("/"):
                    parts = urlparse(self.profile.base_url)
                    return f"{parts.scheme}://{parts.hostname}{val}"
        return ""

    def _detect_type(self, url: str, el: Tag) -> str:
        text = el.get_text()
        if "-sezon" in url:
            return "SERIES"
        if "серія" in text.lower():
            return "SERIES"
        for marker in self.profile.series_markers:
            if marker in url and "/filmy/" not in url:
                return "SERIES"
        return "MOVIE"

    def parse_detail(self, html: str, url: str) -> MovieDetail:
        soup = BeautifulSoup(html, "html.parser")
        title_el = soup.select_one("h1")
        title = title_el.get_text(strip=True) if title_el else soup.title.string.split("|")[0].strip() if soup.title else ""

        def extract_info(*terms: str) -> list[str]:
            for term in terms:
                selectors = [
                    f'li:contains("{term}")',
                    f'.movie-desk-item:contains("{term}")',
                    f'.meta-item:contains("{term}")',
                    f'.finfo li:contains("{term}")'
                ]
                element = soup.select_one(",".join(selectors))
                if not element:
                    selectors2 = [f'li:contains({term})', f'.movie-desk-item:contains({term})',
                                  f'.meta-item:contains({term})', f'.finfo li:contains({term})']
                    for s in selectors2:
                        element = soup.select_one(s)
                        if element:
                            break

                if not element:
                    continue

                values = element.select(".deck-value, span, a")
                values = [v for v in values if v.get_text(strip=True)]
                if values:
                    return [v.get_text(strip=True).rstrip(",") for v in values]

                text = element.get_text()
                after = text.split(term, 1)[1].strip().lstrip(":").strip() if term in text else ""
                if after:
                    return [x.strip() for x in after.split(",") if x.strip()]
            return []

        genres = extract_info("Жанр", "Категорія")
        country = extract_info("Країна")
        actors = extract_info("Актори", "В ролях")
        director = extract_info("Режисер")

        year_selectors = [
            'li:contains("Рік")',
            '.movie-desk-item:contains("Рік")',
            '.meta-item:contains("Рік")'
        ]
        year = None
        for s in year_selectors:
            el = soup.select_one(s)
            if el:
                m = re.search(r"\b(19|20)\d{2}\b", el.get_text())
                if m:
                    year = m.group(0)
                    break
        if not year:
            m = re.search(r"\b(19|20)\d{2}\b", soup.get_text())
            if m:
                year = m.group(0)

        rating_selectors = [
            ".rating-num", ".imdb-rating", ".kp-rating", ".rate_res",
            ".imdb b", '.meta-item:contains("IMDB")',
            '.meta-item:contains(IMDB)'
        ]
        rating = None
        for s in rating_selectors:
            el = soup.select_one(s)
            if el:
                rating = el.get_text(strip=True).replace("IMDB:", "").strip()[:4]
                if rating:
                    break
        if not rating:
            imdb_match = re.search(r"IMDB:\s*(\d+(\.\d+)?)", soup.get_text())
            if imdb_match:
                rating = imdb_match.group(1)

        description_selectors = [
            "#full-text", ".full-text", ".full-info .full-text",
            "article div", ".movie-description", ".full-description"
        ]
        description = ""
        best_text = ""
        for s in description_selectors:
            for el in soup.select(s):
                text = el.get_text(strip=True)
                if len(text) <= 80:
                    continue
                parents = el.parents
                is_comment = False
                is_sidebar = False
                for p in parents:
                    combined = ((p.get("class", []) and " ".join(p.get("class", []))) or "") + " " + (p.get("id", "") or "")
                    combined = combined.lower()
                    for kw in ("comment", "comm-", "comm_", "feedback", "reply"):
                        if kw in combined:
                            is_comment = True
                            break
                    for kw in ("side", "footer", "related", "header", "menu", "popular"):
                        if kw in combined:
                            is_sidebar = True
                            break
                if is_comment or is_sidebar:
                    continue
                links_count = len(el.select("a"))
                if links_count >= 5:
                    continue
                if len(text) > len(best_text):
                    best_text = text

        description = best_text

        og_image = soup.select_one('meta[property="og:image"]')
        poster = og_image.get("content", "") if og_image else ""
        if poster and poster.startswith("//"):
            poster = "https:" + poster

        if not poster:
            poster = self._extract_poster(soup)

        content_type = self._detect_type(url, soup)

        return MovieDetail(
            id=str(hash(url)),
            title=self._clean_title(title),
            poster=poster,
            description=description,
            year=year,
            genres=genres,
            page_url=url,
            provider_name=self.profile.name,
            rating=rating,
            country=country,
            actors=actors,
            director=director,
            content_type=content_type
        )

    def parse_search(self, html: str) -> list[Movie]:
        blacklist = ["топ", "добірка", "добырка", "кращі", "краші",
                     "фільми 202", "серіали 202", "netflix", "imdb"]

        results = self.parse_list(html)
        results = [m for m in results if not any(b in m.title.lower() for b in blacklist)]

        if not results:
            soup = BeautifulSoup(html, "html.parser")
            for a in soup.select('a[href*=".html"]'):
                url = self._abs_url(a.get("href", ""))
                title = a.get("title", "") or a.get_text(strip=True)
                if len(title) > 3 and "/?do=" not in url:
                    results.append(Movie(
                        id=str(hash(url)),
                        title=self._clean_title(title),
                        poster="",
                        year=None,
                        page_url=url,
                        type="MOVIE"
                    ))

        seen = set()
        unique = []
        for m in results:
            if m.page_url not in seen:
                seen.add(m.page_url)
                unique.append(m)
        return unique

    def _abs_url(self, url: str) -> str:
        if url.startswith("http"):
            return url
        if url.startswith("//"):
            return "https:" + url
        return self.profile.base_url.rstrip("/") + "/" + url.lstrip("/")

    def _clean_title(self, title: str) -> str:
        from .models import Movie  # noqa
        return clean_title(title)


# ========== ContentUtils port ==========

YEAR_REGEX = re.compile(r"\(\d{4}\)")
TECH_REGEX = re.compile(r"\b(FHD|HD|SD|720p|1080p|2160p|4K|HDR|BD-Rip|BDRip|DVDRip|WEB-DL|WEBRip|Rip|CAMRip|TS|H264|HEVC)\b", re.IGNORECASE)
TECHNICAL_SUFFIX_REGEX = re.compile(r"(?:\s+\d+[-\s]*\d*)?\s*(?:сезон|серія|серії|серій|season|episode|sezon|seria|seriya|IMDB|голосів|рейтинг|rating|votes|переглядів|дивитися|онлайн).*$", re.IGNORECASE)
START_SERIES_PREFIX_REGEX = re.compile(r"^\d*[-\s]*\d*\s*(?:сезон|серія|серії|серій|season|episode|sezon|seria|seriya)\s*", re.IGNORECASE)
START_NUMERIC_PREFIX_REGEX = re.compile(r"^\d{1,8}\s+")
TRAILING_JUNK_REGEX = re.compile(r"\s+[воуіа]\b\s*$", re.IGNORECASE)
HTML_TAGS_REGEX = re.compile(r"<[^>]*>")
NON_ALPHANUM_REGEX = re.compile(r"[^\w\s']", re.UNICODE)
WHITESPACE_REGEX = re.compile(r"\s+")
ROMAN_REGEX = re.compile(r"\b([IVX]|II|III|IV|V|VI|VII|VIII|IX|X)\b", re.IGNORECASE)

PARASITES = [
    re.compile(r"\bдивитися онлайн\b", re.IGNORECASE),
    re.compile(r"\bдивитися\b", re.IGNORECASE),
    re.compile(r"\bдивись\b", re.IGNORECASE),
    re.compile(r"\bонлайн\b", re.IGNORECASE),
    re.compile(r"\bнаживо в\b", re.IGNORECASE),
    re.compile(r"\bдивись наживо\b", re.IGNORECASE),
    re.compile(r"\bонлайн в HD\b", re.IGNORECASE),
    re.compile(r"\bонлайн в\b", re.IGNORECASE),
    re.compile(r"\bукраїнською\b", re.IGNORECASE),
]

ROMAN_MAP = {"i": "1", "ii": "2", "iii": "3", "iv": "4", "v": "5",
             "vi": "6", "vii": "7", "viii": "8", "ix": "9", "x": "10"}

CYRILLIC_TO_LATIN = {
    "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "е": "e",
    "є": "ie", "ж": "zh", "з": "z", "и": "y", "і": "i", "ї": "i",
    "й": "y", "к": "k", "л": "l", "м": "m", "н": "n", "о": "o",
    "п": "p", "р": "r", "с": "s", "т": "t", "у": "u", "ф": "f",
    "х": "kh", "ц": "ts", "ч": "ch", "ш": "sh", "щ": "shch",
    "ь": "", "ю": "iu", "я": "ia"
}


def clean_title(title: str) -> str:
    if not title or not title.strip():
        return ""

    clean = title.split(" / ")[-1] if " / " in title else title

    stop_markers = ["Жанр:", "Актори:", "Рік виходу:", "Короткий опис:", "0 IMDB:", " IMDB:"]
    for marker in stop_markers:
        idx = clean.lower().find(marker.lower())
        if idx != -1:
            clean = clean[:idx]

    clean = TECHNICAL_SUFFIX_REGEX.sub("", clean)
    clean = START_SERIES_PREFIX_REGEX.sub("", clean)
    clean = START_NUMERIC_PREFIX_REGEX.sub("", clean)

    html_replacements = {
        "&amp;": "&", "&#039;": "'", "&rsquo;": "'",
        "&quot;": '"', "&lt;": "<", "&gt;": ">", "&nbsp;": " "
    }
    for old, new in html_replacements.items():
        clean = clean.replace(old, new)
    clean = HTML_TAGS_REGEX.sub("", clean)
    clean = clean.replace("+", " ").replace("_", " ")

    for p in PARASITES:
        clean = p.sub("", clean)

    clean = TECH_REGEX.sub("", clean)
    clean = YEAR_REGEX.sub("", clean)

    clean = NON_ALPHANUM_REGEX.sub(" ", clean)
    clean = WHITESPACE_REGEX.sub(" ", clean)
    clean = TRAILING_JUNK_REGEX.sub("", clean)
    clean = clean.strip()

    words = [w for w in clean.split(" ") if w]
    deduped = []
    for w in words:
        if not deduped or deduped[-1].lower() != w.lower():
            deduped.append(w)

    if len(deduped) > 8:
        return " ".join(deduped[:6])
    return " ".join(deduped)


def normalize_for_match(text: str) -> str:
    if not text:
        return ""
    norm = text.lower().replace("'", "")
    en_to_ukr = {"michael": "майкл", "joker": "джокер", "avatar": "аватар"}
    if norm in en_to_ukr:
        return en_to_ukr[norm]

    result = []
    for ch in norm:
        result.append(CYRILLIC_TO_LATIN.get(ch, ch))
    norm = "".join(result)

    def replace_roman(m: re.Match) -> str:
        return ROMAN_MAP.get(m.group(0).lower(), m.group(0))

    norm = ROMAN_REGEX.sub(replace_roman, norm)
    norm = re.sub(r"[^\w]", "", norm)
    return norm.strip()


def is_title_match(provider_title: str, other_title: str, strict: bool = False) -> bool:
    p_norm = normalize_for_match(clean_title(provider_title))
    t_norm = normalize_for_match(clean_title(other_title))
    if strict:
        return p_norm == t_norm
    return p_norm == t_norm or (p_norm in t_norm and len(t_norm) >= 3)


def infer_content_type(url: str, title: str = "") -> str:
    combined = (url + title).lower()
    markers = ["сезон", "серіал", "серія", "series", "/seriesss/", "/seriali/"]
    return "SERIES" if any(m in combined for m in markers) else "MOVIE"


def is_playable_stream_url(url: str) -> bool:
    lurl = url.lower()
    if lurl.endswith((".html", ".php", ".htm")):
        return False
    if "text/html" in lurl or "<html" in lurl:
        return False
    return any(m in lurl for m in (".m3u8", ".mpd", ".mp4", "/hls/"))


def is_direct(url: str) -> bool:
    lurl = url.lower()
    if ".m3u8" in lurl or ".mpd" in lurl:
        return True
    return ".mp4" in lurl and "/vod/" not in lurl
