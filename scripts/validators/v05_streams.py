import re
import time
import json
from urllib.parse import urlparse

from ..core.config import ALL_PROVIDERS, USER_AGENT
from ..core.models import MediaSource, Season, Episode, StrategyResult
from ..core.parser import DleParser
from ..core.playlist_parser import (
    find_media_urls_in_text, parse_playlist, find_season_links,
    extract_season_num, ensure_absolute_url
)
from ..core.http_client import HttpClient


def resolve_html_playlist(html: str, url: str, provider_name: str, default_season: int = None) -> MediaSource:
    return parse_playlist(html, url, provider_name, default_season)


def resolve_script(html: str, url: str, provider_name: str, default_season: int = None) -> MediaSource:
    pattern = re.compile(
        r"""["']?(?:file|playlist|url)["']?\s*[:=]\s*(\[[^\]]+\]|\{[^\}]+\}|["']https?://[^"']+\.m3u8[^"']*)""",
        re.IGNORECASE
    )
    for match in pattern.finditer(html):
        raw = match.group(1).strip("\"'")
        result = parse_playlist(raw, url, provider_name, default_season)
        if result:
            return result
    return None


def resolve_iframe(client: HttpClient, html: str, page_url: str, provider_name: str,
                   content_type: str = "MOVIE", default_season: int = None) -> MediaSource:
    from bs4 import BeautifulSoup
    soup = BeautifulSoup(html, "html.parser")
    iframes = []
    for ifr in soup.select("iframe"):
        src = ifr.get("data-src", "") or ifr.get("src", "")
        if src and "youtube" not in src and "facebook" not in src:
            if src.startswith("//"):
                src = "https:" + src
            elif src.startswith("/"):
                parsed = urlparse(page_url)
                src = f"{parsed.scheme}://{parsed.hostname}{src}"
            elif not src.startswith("http"):
                continue
            iframes.append(src)

    # Eneyida: filter out trailer iframes
    if provider_name == "Eneyida":
        filtered = [s for s in iframes if not ("vid/" in s and "tr=1" in s)]
        iframes = filtered or iframes

    limit = 999 if (content_type == "MOVIE" or provider_name == "Eneyida") else 2

    for src in iframes[:limit]:
        r = client.get(src, referer=page_url, timeout=20)
        if not r.ok:
            continue

        # Direct media URLs in iframe content
        media = find_media_urls_in_text(r.body)
        if media:
            # Eneyida series: try to extract episode structure
            if provider_name == "Eneyida" and content_type == "SERIES":
                temp_eps = []
                for m_url in media:
                    # Pattern: s01e02
                    hdvb = re.search(r"s(\d+)e(\d+)", m_url, re.IGNORECASE)
                    if hdvb:
                        temp_eps.append((int(hdvb.group(1)), int(hdvb.group(2)), m_url))
                        continue
                    # Pattern: /SEASON/EPISODE/index.m3u8
                    parts = m_url.split("/")
                    try:
                        idx = len(parts) - 1 - parts[::-1].index([p for p in parts if "index.m3u8" in p][0])
                    except (ValueError, IndexError):
                        idx = -1
                    if idx >= 2:
                        e_num = _to_int(parts[idx - 1])
                        s_num = _to_int(parts[idx - 2])
                        if e_num and s_num:
                            if s_num > 100 and idx >= 3:
                                s2 = _to_int(parts[idx - 3])
                                e2 = _to_int(parts[idx - 2])
                                if s2 and e2 and s2 < 100:
                                    temp_eps.append((s2, e2, m_url))
                                    continue
                            if s_num < 100:
                                temp_eps.append((s_num, e_num, m_url))

                if temp_eps:
                    from collections import defaultdict
                    groups = defaultdict(list)
                    for s, e, u in temp_eps:
                        groups[s].append(Episode(e, f"Серія {e}", u))
                    seasons = [Season(s, sorted(eps, key=lambda x: x.number))
                               for s, eps in groups.items()]
                    seasons.sort(key=lambda x: x.number)
                    return MediaSource("", referer=page_url, provider_name=provider_name,
                                       seasons=seasons, method="iframe")

                # Multiple media = episodes of current season
                if len(media) > 1:
                    eps = [Episode(i + 1, f"Серія {i + 1}", u) for i, u in enumerate(media)]
                    s_num = default_season or 1
                    return MediaSource("", referer=page_url, provider_name=provider_name,
                                       seasons=[Season(s_num, eps)], method="iframe")

            return MediaSource(media[0], [], page_url, provider_name, method="iframe")

        # Known player domains fallback
        known_domains = ["hdvb", "ashdi", "vidmoly", "mcloud", "vidsrc"]
        if any(d in src.lower() for d in known_domains):
            return MediaSource(src, [], page_url, provider_name, method="iframe_fallback")

    return None


def _to_int(val):
    try:
        return int(val)
    except (ValueError, TypeError):
        return None


def resolve_ajax(client: HttpClient, html: str, page_url: str, provider_name: str,
                 user_hash: str = "", default_season: int = None,
                 endpoints: list[str] = None) -> MediaSource:
    # Extract news_id
    news_id = None
    from bs4 import BeautifulSoup
    soup = BeautifulSoup(html, "html.parser")
    for sel in ["input[name=news_id]", "#news_id", "input[name=post_id]"]:
        el = soup.select_one(sel)
        if el and el.get("value"):
            news_id = el["value"]
            break
    if not news_id:
        for pat in [r"""news_id\s*[:=]\s*["']?(\d+)""", r"""dle_news_id\s*=\s*['"]?(\d+)""", r"""/(\d+)-"""]:
            m = re.search(pat, html)
            if m:
                news_id = m.group(1)
                break
    if not news_id:
        return None

    if not endpoints:
        endpoints = ["engine/ajax/playlists.php", "engine/ajax/video.php", "engine/ajax/get_playlist.php"]

    base_url = page_url
    parsed = urlparse(page_url)
    base = f"{parsed.scheme}://{parsed.hostname}"

    for ep in endpoints:
        endpoint = ep if ep.startswith("http") else base + "/" + ep.lstrip("/")
        for field in ["playlist", "seria", "video", "playlist_ua"]:
            data = f"news_id={news_id}&action=playlists&xfield={field}&area=news"
            if user_hash:
                data += f"&user_hash={user_hash}"

            r = client.post(endpoint, data=data, referer=page_url, timeout=8)
            if not r.ok or not r.body.strip():
                continue

            # Process JSON response
            response_text = r.body
            if response_text.strip().startswith("{"):
                try:
                    resp = json.loads(response_text.strip())
                    if isinstance(resp, dict):
                        success = resp.get("success", False)
                        if isinstance(success, str):
                            success = success.lower() == "true"
                        if not success:
                            continue
                        inner = resp.get("response")
                        if inner:
                            response_text = inner
                except json.JSONDecodeError:
                    pass

            result = parse_playlist(response_text, page_url, provider_name, default_season)
            if result:
                result.method = f"ajax({ep.split('/')[-1]}?xfield={field})"
                return result

    return None


def run(client: HttpClient, reporter_section, verbose: bool = False) -> None:
    # Test URLs: pick real movies/series from both providers
    test_cases = [
        # Uakino movie, series
        ("Uakino", "/ua/", "MOVIE"),
        ("Uakino", "/ua/", "SERIES"),
        # Eneyida movie, series
        ("Eneyida", "", "MOVIE"),
        ("Eneyida", "", "SERIES"),
    ]

    strategy_names = ["HtmlPlaylist", "Script", "Iframe", "Ajax"]

    for provider_name, home_path, content_type in test_cases:
        p = [x for x in ALL_PROVIDERS if x.name == provider_name][0]
        parser = DleParser(p)
        home_url = p.base_url + home_path

        r = client.get(home_url, timeout=20)
        if not r.ok:
            reporter_section.check(
                f"{p.name} {content_type} stream — cannot load home",
                "FAIL", f"HTTP {r.status}"
            )
            continue

        movies = parser.parse_list(r.body)
        target_type = content_type
        candidates = [m for m in movies if m.type == target_type and m.page_url and m.page_url != home_url]

        if not candidates:
            reporter_section.check(
                f"{p.name} {content_type} stream — no {content_type} items found",
                "WARN", "trying any type"
            )
            candidates = [m for m in movies if m.page_url and m.page_url != home_url]

        tested = 0
        for movie in candidates[:4]:
            if tested >= 2:
                break

            # Fetch detail page
            rd = client.get(movie.page_url, timeout=25)
            if not rd.ok:
                continue

            detail = parser.parse_detail(rd.body, movie.page_url)
            default_season = extract_season_num(movie.page_url)

            # Extract user_hash for AJAX
            user_hash = ""
            hash_match = re.search(r"""dle_login_hash\s*=\s*['"]([^'"]+)['"]""", rd.body)
            if hash_match:
                user_hash = hash_match.group(1)

            results = {}

            # Try each strategy
            for sname in strategy_names:
                t0 = time.time()
                try:
                    if sname == "HtmlPlaylist":
                        result = resolve_html_playlist(rd.body, movie.page_url, p.name, default_season)
                    elif sname == "Script":
                        result = resolve_script(rd.body, movie.page_url, p.name, default_season)
                    elif sname == "Iframe":
                        result = resolve_iframe(client, rd.body, movie.page_url, p.name,
                                                detail.content_type, default_season)
                    elif sname == "Ajax":
                        result = resolve_ajax(client, rd.body, movie.page_url, p.name,
                                              user_hash, default_season, p.playlist_endpoints)
                    else:
                        result = None
                except Exception as e:
                    result = None
                    if verbose:
                        print(f"  [{sname}] error: {e}")

                duration_ms = (time.time() - t0) * 1000
                success = result is not None
                if success:
                    url_preview = result.primary_url or ""
                    if result.is_series:
                        ep_count = sum(len(s.episodes) for s in result.seasons)
                        detail_text = f"Series: {len(result.seasons)} seasons, {ep_count} episodes ({duration_ms:.0f}ms)"
                    else:
                        detail_text = f"Movie: {url_preview[:60]}... ({duration_ms:.0f}ms)"
                else:
                    detail_text = f"No result ({duration_ms:.0f}ms)"

                results[sname] = StrategyResult(sname, success, result, None, duration_ms, detail_text)
                reporter_section.check(
                    f"{p.name} \"{movie.title[:30]}\" [{content_type}] → {sname}",
                    "PASS" if success else "FAIL",
                    detail_text
                )

            tested += 1

        if tested == 0:
            reporter_section.check(
                f"{p.name} {content_type} — no testable items",
                "FAIL",
                "Could not find any page to test"
            )

    # Summary: which strategies work per provider
    for sname in strategy_names:
        successes = []
        for provider_name, _, content_type in test_cases:
            p = [x for x in ALL_PROVIDERS if x.name == provider_name][0]
            key = f"{p.name} {content_type} → {sname}"
            successes.append(key)

        # We'll compute actual pass rate later in orchestrator
        reporter_section.check(
            f"Strategy: {sname} — overall",
            "INFO",
            "See per-provider results above"
        )
