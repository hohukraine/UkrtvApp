import re
import json
from typing import Optional
from bs4 import BeautifulSoup

from .models import MediaSource, Season, Episode


SEASON_REGEXES = [
    re.compile(r"(?:сезон|season|sezon)[\s\-_]*([0-9]{1,2})", re.IGNORECASE),
    re.compile(r"([0-9]{1,2})[\s\-_]*(?:сезон|season|sezon)", re.IGNORECASE),
    re.compile(r"\bs(\d+)(?:e\d+)?\b", re.IGNORECASE),
    re.compile(r"s(\d+)e\d+", re.IGNORECASE),
    re.compile(r"/(\d+)[\s\-_]*сезон", re.IGNORECASE),
]


def find_media_urls_in_text(text: str) -> list[str]:
    if not text:
        return []
    candidates = set()

    pattern1 = re.compile(r"https?://[^\s\"'>]+(?:\.m3u8|\.mp4|\.webm)(?:\?[^\s\"'>]*)?", re.IGNORECASE)
    for m in pattern1.finditer(text):
        candidates.add(m.group(0))

    pattern2 = re.compile(r"https?://[^\s\"'>]+/(?:master\.m3u8|index\.m3u8|playlist\.m3u8)", re.IGNORECASE)
    for m in pattern2.finditer(text):
        candidates.add(m.group(0))

    pattern3 = re.compile(r"dleid://(\d+)")
    for m in pattern3.finditer(text):
        candidates.add(m.group(0))

    return sorted(candidates)


def extract_season_num(text: str) -> Optional[int]:
    clean = re.sub(r"\b(19|20)\d{2}\b", "", text.lower())
    for regex in SEASON_REGEXES:
        m = regex.search(clean)
        if m:
            try:
                return int(m.group(1))
            except ValueError:
                pass
    return None


def find_season_links(html: str, base_url: str) -> list[tuple[int, str]]:
    soup = BeautifulSoup(html, "html.parser")
    containers = soup.select(".seasons, .franchise-list, .serial-series")
    if not containers:
        containers = soup.select(".video-tabs, .player-tabs, .tabs-sel")

    if containers:
        links = []
        for c in containers:
            for a in c.select("a[href]"):
                links.append(a)
    else:
        links = soup.select('a[href*="-sezon"]')

    results = []
    seen_urls = set()
    for a in links:
        href = a.get("href", "")
        if not href:
            continue
        s_num = extract_season_num(a.get_text(strip=True))
        if s_num is None:
            continue
        if s_num > 50:
            continue
        abs_href = ensure_absolute_url(href, base_url)
        if abs_href in seen_urls:
            continue
        seen_urls.add(abs_href)
        results.append((s_num, abs_href))

    results.sort(key=lambda x: x[0])
    return results


def ensure_absolute_url(url: str, base_url: str) -> str:
    if url.startswith("http"):
        return url
    if url.startswith("//"):
        return "https:" + url
    if url.isdigit():
        return f"dleid://{url}"
    if url.startswith("dleid://"):
        return url
    if url.startswith("/"):
        from urllib.parse import urlparse
        parsed = urlparse(base_url)
        return f"{parsed.scheme}://{parsed.hostname}{url}"
    return base_url.rstrip("/") + "/" + url.lstrip("/")


def is_media_url(url: str) -> bool:
    if len(url) > 500 or "<" in url or ">" in url or "html" in url.lower():
        return False
    lurl = url.lower()
    if lurl.startswith(("dleid://", "[", "{")):
        return True
    markers = [".m3u8", ".mp4", ".mpd", "/hls/", "/video/", "/vod/",
               "/embed/", "token=", "hdvb", "ashdi", "vidmoly", "mcloud"]
    return any(m in lurl for m in markers)


def parse_dle_links(input_str: str) -> str:
    trimmed = input_str.strip()
    if not trimmed or trimmed.lower().startswith("<html"):
        return ""
    if "]" in trimmed:
        return trimmed.split("]")[-1].strip()
    return trimmed


def parse_playlist(input_str: str, referer: str, provider_name: str,
                   default_season: Optional[int] = None) -> Optional[MediaSource]:
    trimmed = input_str.strip()
    if not trimmed or trimmed in ("0", "null") or trimmed.startswith("<!"):
        return None

    # 1. JSON
    if trimmed.startswith("{") or trimmed.startswith("["):
        try:
            data = json.loads(trimmed)
        except json.JSONDecodeError:
            pass
        else:
            if isinstance(data, dict):
                inner_html = data.get("response")
                if isinstance(inner_html, str) and ("<li" in inner_html or "data-file" in inner_html):
                    return parse_playlist(inner_html, referer, provider_name, default_season)

                playlist_data = data.get("playlist") or data.get("folder") or data.get("video")
            else:
                playlist_data = data

            if isinstance(playlist_data, list) and playlist_data:
                episodes: list[Episode] = []
                seasons_list: list[Season] = []

                for item in playlist_data:
                    if not isinstance(item, dict):
                        continue
                    folder = item.get("folder")
                    if isinstance(folder, list):
                        s_title = item.get("title", "Сезон")
                        s_num = extract_season_num(s_title) or (len(seasons_list) + 1)
                        s_episodes = []
                        for e_idx, e_item in enumerate(folder):
                            if not isinstance(e_item, dict):
                                continue
                            e_title = e_item.get("title", f"Серія {e_idx + 1}")
                            e_file = parse_dle_links(str(e_item.get("file", "") or e_item.get("url", "") or ""))
                            if not e_file:
                                continue
                            e_num_match = re.search(r"(\d+)", e_title)
                            e_num = int(e_num_match.group(1)) if e_num_match else (e_idx + 1)
                            e_voice = e_item.get("voice", "") or e_item.get("voiceover", "") or ""
                            e_subs = e_item.get("subtitle", "") or e_item.get("subtitles", "") or e_item.get("vtt", "") or ""
                            s_episodes.append(Episode(
                                number=e_num,
                                title=e_title,
                                url=ensure_absolute_url(e_file, referer),
                                voiceover=e_voice or None,
                                subtitles=e_subs or None
                            ))
                        if s_episodes:
                            seasons_list.append(Season(number=s_num, episodes=s_episodes))
                    else:
                        title = item.get("title", "Епізод")
                        file_val = parse_dle_links(str(item.get("file", "") or item.get("url", "") or ""))
                        if file_val:
                            e_num_match = re.search(r"(\d+)", title)
                            e_num = int(e_num_match.group(1)) if e_num_match else (len(episodes) + 1)
                            voice = item.get("voice", "") or item.get("voiceover", "") or ""
                            subs = item.get("subtitle", "") or item.get("subtitles", "") or item.get("vtt", "") or ""
                            episodes.append(Episode(
                                number=e_num, title=title,
                                url=ensure_absolute_url(file_val, referer),
                                voiceover=voice or None, subtitles=subs or None
                            ))

                if seasons_list:
                    seasons_list.sort(key=lambda s: s.number)
                    return MediaSource(url="", referer=referer, provider_name=provider_name,
                                       seasons=seasons_list, method="json")
                if episodes:
                    s_num = default_season or extract_season_num(referer) or 1
                    return MediaSource(url="", referer=referer, provider_name=provider_name,
                                       seasons=[Season(number=s_num, episodes=episodes)], method="json")

    # 2. HTML list
    if "<li" in trimmed and ("data-file" in trimmed or "data-url" in trimmed or "data-id" in trimmed):
        soup = BeautifulSoup(trimmed, "html.parser")
        items = soup.select("li[data-file], li[data-url], li[data-id]")
        if items:
            episodes = []
            for idx, li in enumerate(items):
                raw_file = li.get("data-file", "") or li.get("data-url", "") or li.get("data-id", "") or ""
                url_val = parse_dle_links(raw_file)
                if not url_val or url_val in ("0", "") or len(url_val) < 5 or "<" in url_val:
                    continue
                title = li.get_text(strip=True) or f"Серія {idx + 1}"
                e_num_match = re.search(r"(\d+)", title)
                e_num = int(e_num_match.group(1)) if e_num_match else (idx + 1)
                voice = li.get("data-voice", "") or li.get("data-voiceover", "") or ""
                subs = li.get("data-subtitle", "") or li.get("data-subtitles", "") or li.get("data-vtt", "") or ""
                episodes.append(Episode(
                    number=e_num, title=title,
                    url=ensure_absolute_url(url_val, referer),
                    voiceover=voice or None, subtitles=subs or None
                ))
            if episodes:
                s_num = default_season or extract_season_num(referer) or 1
                season_map = {}
                for ep in episodes:
                    es = extract_season_num(ep.title) or extract_season_num(ep.url) or s_num
                    season_map.setdefault(es, []).append(ep)
                seasons = [Season(number=num, episodes=sorted(eps, key=lambda x: x.number))
                           for num, eps in season_map.items()]
                seasons.sort(key=lambda s: s.number)
                return MediaSource(url="", referer=referer, provider_name=provider_name,
                                   seasons=seasons, method="html")

    # 3. Direct link
    if is_media_url(trimmed):
        final_url = parse_dle_links(trimmed)
        if final_url:
            return MediaSource(
                url=ensure_absolute_url(final_url, referer),
                referer=referer,
                provider_name=provider_name,
                method="direct"
            )

    return None
