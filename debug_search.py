#!/usr/bin/env python3
"""
Debug search for Top200 movies on Uakino and Eneyida.

Tests various search strategies (POST, AJAX, GET) with proper session cookies
to diagnose why 23 movies aren't found by the audit test.
"""

import re
import time
import sys
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

_last_raw_responses: dict = {}

# Use curl subprocess for reliable TLS (macOS system SSL is too old for some providers)
import subprocess
import shlex
import tempfile
import os


def _curl(method, url, data=None, headers=None, timeout=15):
    """Execute HTTP request via curl subprocess."""
    cmd = ["curl", "-s", "-L",
           "--connect-timeout", str(timeout),
           "--max-time", str(timeout + 5)]
    if method == "POST":
        cmd += ["-X", "POST"]
        if data:
            cmd += ["-d", data]
    if headers:
        for k, v in headers.items():
            cmd += ["-H", f"{k}: {v}"]
    cmd += [url]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout + 10)
        if result.returncode == 0:
            return result.stdout
        else:
            return None
    except Exception:
        return None


UA = (
    "Mozilla/5.0 (Linux; Android 11; BRAVIA 4K VH2) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.159 Safari/537.36"
)

HEADERS = {
    "User-Agent": UA,
    "Accept-Language": "uk-UA,uk;q=0.9,en-US;q=0.8,en;q=0.7",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
    "Upgrade-Insecure-Requests": "1",
    "Sec-Fetch-Dest": "document",
    "Sec-Fetch-Mode": "navigate",
    "Sec-Fetch-Site": "none",
    "Sec-Fetch-User": "?1",
}

AJAX_HEADERS = {
    "User-Agent": UA,
    "Accept-Language": "uk-UA,uk;q=0.9,en-US;q=0.8,en;q=0.7",
    "X-Requested-With": "XMLHttpRequest",
    "Accept": "application/json, text/javascript, */*; q=0.01",
    "Sec-Fetch-Dest": "empty",
    "Sec-Fetch-Mode": "cors",
    "Sec-Fetch-Site": "same-origin",
    "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
}

# The 23 movies not found on either provider
PROBLEM_MOVIES = [
    (14, "Три білборди за межами Еббінга, Міссурі", "Three Billboards Outside Ebbing, Missouri", "2017"),
    (25, "Безмежний розум: Області пітьми", "Limitless", "2011"),
    (36, "Зачаровані Місяцем", "Moonstruck", "1987"),
    (44, "Падіння «Чорного яструба»", "Black Hawk Down", "2001"),
    (67, "Список останніх бажань", "The Bucket List", "2007"),
    (71, "Людина на ім'я Уве", "En man som heter Ove", "2015"),
    (73, "1+1", "Intouchables", "2011"),
    (82, "Злочинець", "Felon", "2008"),
    (93, "Чотири брати", "Four Brothers", "2005"),
    (97, "127 годин", "127 Hours", "2010"),
    (104, "Мiстер i мiсiс Смiт", "Mr. & Mrs. Smith", "2005"),
    (120, "Я — легенда", "I Am Legend", "2007"),
    (126, "Леон", "Léon", "1994"),
    (136, "F1: Фільм", "F1", "2025"),
    (140, "Хижа в лісі", "The Cabin in the Woods", "2012"),
    (157, "Тринадцять життів", "Thirteen Lives", "2022"),
    (162, "Воно", "It", "2017"),
    (174, "Дежавю", "Deja Vu", "2006"),
    (182, "Сім", "Se7en", "1995"),
    (185, "Тридцять хвилин по опівночі", "Zero Dark Thirty", "2012"),
    (187, "Одного разу в… Голлівуді", "Once Upon a Time... in Hollywood", "2019"),
    (192, "Антарктида (Птушкін)", "Antarctica", ""),
    (195, "Знайомтеся — Джо Блек", "Meet Joe Black", "1998"),
]


def extract_dle_login_hash(html: str) -> str:
    m = re.search(r"""dle_login_hash\s*=\s*['"]([^'"]+)['"]""", html)
    return m.group(1) if m else ""


def extract_movies_from_html(html: str, base_url: str) -> list[dict]:
    """Parse search results like DleParser.parseSearch does."""
    results = []
    soup = BeautifulSoup(html, "html.parser")

    # Try Jsoup-style selectors
    for card_sel in [".movie-item", ".short-item", ".shortstory", "article.short"]:
        cards = soup.select(card_sel)
        if cards:
            for el in cards:
                link_el = el.select_one("a[href]")
                if not link_el:
                    continue
                url = link_el.get("href", "")
                if url and not url.startswith("http"):
                    url = urljoin(base_url, url)
                title_el = el.select_one(".movie-title, .short-title, .shortstory-title, a.short_title")
                title = ""
                if title_el:
                    title = title_el.get_text(strip=True)
                if not title:
                    title = link_el.get("title", link_el.get_text(strip=True))
                if not title:
                    continue
                poster_el = el.select_one("img[data-src], img[src]")
                poster = ""
                if poster_el:
                    poster = poster_el.get("data-src") or poster_el.get("src") or ""
                    if poster and not poster.startswith("http"):
                        poster = urljoin(base_url, poster)
                results.append({"title": title, "url": url, "poster": poster})
            if results:
                break

    # If no structured results, try regex fallback
    if not results:
        for m in re.finditer(r'href=["\']([^"\']+\.html)["\']', html):
            raw_url = m.group(1)
            url = raw_url if raw_url.startswith("http") else urljoin(base_url, raw_url)
            block_start = max(0, m.start() - 300)
            block_end = min(len(html), m.end() + 200)
            block = html[block_start:block_end]
            title_m = re.search(r'title=["\']([^"\']{2,})["\']', block)
            title = title_m.group(1) if title_m else ""
            if not title:
                title_m = re.search(r'>([^<]{2,}?)</a>', block)
                title = title_m.group(1) if title_m else ""
            if title:
                results.append({"title": title.strip(), "url": url, "poster": ""})

    return results


def try_search(session, base_url, query, hash_val, name):
    """Try multiple search strategies, return list of found movies."""
    all_results = []
    seen_urls = set()
    global _last_raw_responses
    _last_raw_responses.clear()

    def add_unique(results):
        for r in results:
            if r["url"] not in seen_urls:
                seen_urls.add(r["url"])
                all_results.append(r)

    # Strategy 1: POST to index.php?do=search
    try:
        url = urljoin(base_url, "index.php?do=search")
        data = {"do": "search", "subaction": "search", "story": query}
        if hash_val:
            data["user_hash"] = hash_val
        r = session.post(url, data=data, headers=HEADERS, timeout=15)
        r.raise_for_status()
        _last_raw_responses["post_search"] = r.text
        movies = extract_movies_from_html(r.text, base_url)
        if movies:
            add_unique(movies)
            return all_results
    except Exception as e:
        print(f"    [{name}] POST search failed: {e}")

    # Strategy 2: AJAX search
    try:
        url = urljoin(base_url, "engine/ajax/search.php")
        data = {"query": query}
        if hash_val:
            data["user_hash"] = hash_val
        r = session.post(url, data=data, headers=AJAX_HEADERS, timeout=15)
        r.raise_for_status()
        _last_raw_responses["ajax_search"] = r.text
        movies = extract_movies_from_html(r.text, base_url)
        if movies:
            add_unique(movies)
            return all_results
    except Exception as e:
        print(f"    [{name}] AJAX search failed: {e}")

    # Strategy 3: GET search (some DLE versions support it)
    try:
        url = urljoin(base_url, f"search/{query}.html")
        r = session.get(url, headers=HEADERS, timeout=15)
        r.raise_for_status()
        _last_raw_responses["get_search"] = r.text
        movies = extract_movies_from_html(r.text, base_url)
        if movies:
            add_unique(movies)
            return all_results
    except Exception as e:
        print(f"    [{name}] GET search failed: {e}")

    # Strategy 4: Direct site search via GET parameter
    try:
        url = urljoin(base_url, f"index.php?do=search&subaction=search&story={query}")
        r = session.get(url, headers=HEADERS, timeout=15)
        r.raise_for_status()
        _last_raw_responses["get_param_search"] = r.text
        movies = extract_movies_from_html(r.text, base_url)
        if movies:
            add_unique(movies)
            return all_results
    except Exception as e:
        print(f"    [{name}] GET param search failed: {e}")

    return all_results


def debug_movie(session: requests.Session, base_url: str, hash_val: str, name: str):
    """Interactive mode: search for a specific movie."""
    q = input(f"\n[{name}] Enter search query (or 'q' to quit): ").strip()
    if q.lower() == "q":
        return False
    results = try_search(session, base_url, q, hash_val, name)
    print(f"\n  Results ({len(results)} found):")
    for i, r in enumerate(results[:20]):
        print(f"    [{i + 1}] {r['title']}")
        print(f"         URL: {r['url']}")
    return True


def main():
    base_urls = {
        "Uakino": "https://uakino.best/",
        "Eneyida": "https://eneyida.tv/",
    }

    mode = "batch"
    if len(sys.argv) > 1:
        if sys.argv[1] in ("-i", "--interactive"):
            mode = "interactive"
        elif sys.argv[1] in ("-m", "--movie"):
            mode = "single_movie"
            single_rank = int(sys.argv[2]) if len(sys.argv) > 2 else 0
        elif sys.argv[1] in ("-a", "--all"):
            mode = "full_batch"

    for prov_name, base_url in base_urls.items():
        print(f"\n{'=' * 70}")
        print(f"  {prov_name} ({base_url})")
        print(f"{'=' * 70}")

        session = requests.Session()
        session.verify = False
        import urllib3
        urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
        # Set shorter timeouts for problematic providers
        session.timeout = (10, 15)

        # Initialize session
        print("  Initializing session...")
        try:
            r = session.get(base_url, headers=HEADERS, timeout=15)
            r.raise_for_status()
            hash_val = extract_dle_login_hash(r.text)
            if not hash_val:
                r2 = session.get(urljoin(base_url, "index.php?do=search"), headers=HEADERS, timeout=15)
                r2.raise_for_status()
                hash_val = extract_dle_login_hash(r2.text)
            print(f"  dle_login_hash: {hash_val[:8] if hash_val else 'NOT FOUND'}...")
        except Exception as e:
            print(f"  Session init FAILED: {e}")
            session.close()
            continue

        if mode == "interactive":
            while debug_movie(session, base_url, hash_val, prov_name):
                pass
            session.close()
            continue

        # Batch mode: test the 23 problem movies
        movies_to_test = PROBLEM_MOVIES
        if mode == "single_movie":
            movies_to_test = [m for m in PROBLEM_MOVIES if m[0] == single_rank]
            if not movies_to_test:
                print(f"  Rank {single_rank} not in problem list")
                session.close()
                continue

        for rank, title, orig_title, year in movies_to_test:
            print(f"\n  --- #{rank} \"{title}\" ({year}) [{orig_title}] ---")

            # Build query variants
            queries = list(dict.fromkeys([
                orig_title,
                title,
                f"{orig_title} {year}" if year else orig_title,
                f"{title} {year}" if year else title,
            ]))

            # Extra query variants: transliterations, short, without accents
            extra = []
            for q in queries:
                if len(q) < 3:
                    continue
                extra.append(q)
                # Add transliteration (cyrillic → latin)
                trans = q.translate(str.maketrans(
                    "абвгдеёжзийклмнопрстуфхцчшщъыьэюяАБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ",
                    "abvgdeejzijklmnoprstufhccssyy'euaABVGDEEJZIJKLMNOPRSTUFHCCSSYY'EUA"
                ))
                if trans != q and len(trans) >= 3:
                    extra.append(trans)
                # Without accents
                import unicodedata
                no_acc = unicodedata.normalize('NFKD', q).encode('ASCII', 'ignore').decode()
                if no_acc != q and len(no_acc) >= 3:
                    extra.append(no_acc)

            queries = list(dict.fromkeys(extra))

    all_found = []
    found_contexts = []
    for q in queries:
        if len(q) < 3:
            continue
        results = try_search(session, base_url, q, hash_val, prov_name)
        if results:
            print(f"    Query \"{q}\" → {len(results)} results")
            for r in results[:5]:
                print(f"       • {r['title']}")
                print(f"         {r['url']}")
            all_found.extend(results)
        else:
            print(f"    Query \"{q}\" → no results")
            # Dump raw search response for debugging
            for strategy_url, strategy_response in _last_raw_responses.items():
                if len(strategy_response) > 50:
                    # Check if movie title appears anywhere in response
                    found_in_raw = any(
                        kw.lower() in strategy_response.lower()
                        for kw in [title, orig_title] + [title.split()[0] if title else ""]
                    )
                    if found_in_raw:
                        print(f"    ⚠ Raw response CONTAINS title关键词 but parser missed it!")
                        print(f"      URL: {strategy_url}")
                        # Show relevant snippet
                        for kw in [title[:20], orig_title[:20]]:
                            idx = strategy_response.lower().find(kw.lower())
                            if idx > 0:
                                snippet = strategy_response[max(0,idx-100):idx+len(kw)+100]
                                print(f"      Snippet around '{kw}': ...{snippet}...")
                                break

            if not all_found:
                # Last resort: try each variant with year appended
                for q in queries:
                    if not year:
                        continue
                    qy = f"{q} {year}"
                    if len(qy) < 3:
                        continue
                    results = try_search(session, base_url, qy, hash_val, prov_name)
                    if results:
                        print(f"    Query \"{qy}\" → {len(results)} results")
                        for r in results[:5]:
                            print(f"       • {r['title']}")
                            print(f"         {r['url']}")
                        break
                    else:
                        print(f"    Query \"{qy}\" → no results")

                # Try short query workaround for 1-2 char titles
                if len(title) < 3 and len(orig_title) >= 3:
                    results = try_search(session, base_url, orig_title, hash_val, prov_name)
                    if results:
                        print(f"    (short fallback) Query \"{orig_title}\" → {len(results)} results")
                        for r in results[:5]:
                            print(f"       • {r['title']}")
                            print(f"         {r['url']}")

        session.close()

    print("\nDone.")


if __name__ == "__main__":
    main()
