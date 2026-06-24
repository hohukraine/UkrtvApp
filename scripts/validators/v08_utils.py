from ..core.parser import clean_title, normalize_for_match, is_title_match
from ..core.playlist_parser import find_media_urls_in_text, extract_season_num, is_media_url, parse_playlist
from ..core.models import MediaSource


def run(client, reporter_section, verbose: bool = False) -> None:
    # === cleanTitle tests ===
    title_tests = [
        ("Аватар (2009) HD 1080p дивитися онлайн", "Аватар"),
        ("Гра Престолів 1 сезон серія 5 IMDB 8.5 (2011)", "Гра Престолів"),
        ("10 1 сезон серія", ""),  # After stripping prefix, may be empty
        ("Назва фільму 2023 BDRip", "Назва фільму"),
        ("Title &amp; &quot;Quote&quot;", "Title Quote"),
        ("Слово / Правильна назва", "Правильна назва"),
        ("   Зайві пробіли   ", "Зайві пробіли"),
        ("Дубль Дубль назва", "Дубль назва"),
        ("Дуже довга назва фільму яка має більше ніж вісім слів і буде обрізана", "Дуже довга назва фільму яка має"),
    ]

    for title, expected_prefix in title_tests:
        result = clean_title(title)
        is_ok = result.startswith(expected_prefix) if expected_prefix else not result
        status = "PASS" if is_ok else "WARN"
        detail = f"\"{title}\" → \"{result}\" (expected starts with \"{expected_prefix}\")"
        reporter_section.check(f"cleanTitle: {title[:40]}", status, detail)

    # === normalizeForMatch tests ===
    norm_tests = [
        ("Аватар", "avatar"),
        ("Гра престолів", "hraprestoliv"),
        ("Майкл", "майкл"),  # English→Ukrainian map
        ("Terminator", "terminator"),
    ]

    for text, expected_prefix in norm_tests:
        result = normalize_for_match(text)
        is_ok = expected_prefix in result.lower() or result.startswith(expected_prefix[:3])
        status = "PASS" if is_ok else "WARN"
        detail = f"\"{text}\" → \"{result[:30]}...\""
        reporter_section.check(f"normalizeForMatch: {text[:30]}", status, detail)

    # === isTitleMatch tests ===
    match_tests = [
        ("Аватар", "avatar", True),
        ("Гра Престолів", "Game of Thrones", True),
        ("Аватар", "Месники", False),
    ]

    for t1, t2, expected in match_tests:
        result = is_title_match(t1, t2)
        status = "PASS" if result == expected else "FAIL"
        reporter_section.check(f"isTitleMatch: \"{t1}\" vs \"{t2}\"", status, f"got {result}, expected {expected}")

    # === findMediaUrlsInText tests ===
    media_tests = [
        ('<source src="https://example.com/video.m3u8" type="application/x-mpegURL">',
         ["https://example.com/video.m3u8"]),
        ('file: "https://cdn.example.com/playlist.m3u8?token=abc"',
         ["https://cdn.example.com/playlist.m3u8?token=abc"]),
        ('https://example.com/movie.mp4', ["https://example.com/movie.mp4"]),
        ('https://example.com/master.m3u8', ["https://example.com/master.m3u8"]),
        ('dleid://12345', ["dleid://12345"]),
        ('no urls here', []),
    ]

    for text, expected in media_tests:
        result = find_media_urls_in_text(text)
        status = "PASS" if result == expected else "WARN"
        detail = f"found {result[:2]}, expected {expected[:2]}"
        reporter_section.check(f"findMediaUrls: {text[:40]}", status, detail)

    # === extractSeasonNum tests ===
    season_tests = [
        ("1 сезон", 1),
        ("Сезон 2", 2),
        ("s03e04", 3),
        ("S4E5", 4),
        ("no season here", None),
    ]

    for text, expected in season_tests:
        result = extract_season_num(text)
        status = "PASS" if result == expected else "FAIL"
        reporter_section.check(f"extractSeasonNum: \"{text}\"", status, f"got {result}, expected {expected}")

    # === isMediaUrl tests ===
    media_url_tests = [
        ("https://example.com/video.m3u8", True),
        ("https://example.com/movie.mp4", True),
        ("https://example.com/page.html", False),
        ("dleid://12345", True),
        ("random text", False),
    ]

    for url, expected in media_url_tests:
        result = is_media_url(url)
        status = "PASS" if result == expected else "FAIL"
        reporter_section.check(f"isMediaUrl: {url[:40]}", status, f"got {result}, expected {expected}")

    # === parsePlaylist tests ===
    # JSON with playlist
    json_playlist = '{"playlist": [{"title": "Серія 1", "file": "https://example.com/ep1.m3u8"}, {"title": "Серія 2", "file": "https://example.com/ep2.m3u8"}]}'
    result = parse_playlist(json_playlist, "https://example.com/", "Test")
    status = "PASS" if result and result.is_series and len(result.seasons[0].episodes) == 2 else "FAIL"
    detail = f"got {len(result.seasons[0].episodes) if result and result.seasons else 0} episodes" if result else "None"
    reporter_section.check("parsePlaylist JSON flat", status, detail)

    # JSON with folder (seasons)
    json_seasons = '{"playlist": [{"title": "Сезон 1", "folder": [{"title": "Серія 1", "file": "https://example.com/s1e1.m3u8"}]}]}'
    result = parse_playlist(json_seasons, "https://example.com/", "Test")
    status = "PASS" if result and result.is_series and len(result.seasons) == 1 else "FAIL"
    detail = f"{len(result.seasons) if result and result.seasons else 0} seasons" if result else "None"
    reporter_section.check("parsePlaylist JSON seasons", status, detail)

    # HTML li[data-file]
    html_list = '<ul><li data-file="https://example.com/e1.m3u8">Серія 1</li><li data-file="https://example.com/e2.m3u8">Серія 2</li></ul>'
    result = parse_playlist(html_list, "https://example.com/", "Test")
    status = "PASS" if result and result.is_series and len(result.seasons[0].episodes) == 2 else "FAIL"
    reporter_section.check("parsePlaylist HTML li", status, f"{len(result.seasons[0].episodes) if result and result.seasons else 0} episodes")

    # Direct URL
    result = parse_playlist("https://example.com/video.m3u8", "https://example.com/", "Test")
    status = "PASS" if result and not result.is_series and "video.m3u8" in result.url else "FAIL"
    reporter_section.check("parsePlaylist direct URL", status, f"url={result.url if result else 'None'}")

    # JSON with response field
    json_response = '{"success": true, "response": "<li data-file=\\"https://example.com/e1.m3u8\\">Серія 1</li>"}'
    result = parse_playlist(json_response, "https://example.com/", "Test")
    status = "PASS" if result and result.is_series else "FAIL"
    reporter_section.check("parsePlaylist JSON response", status, f"{len(result.seasons[0].episodes) if result and result.seasons else 0} episodes")
