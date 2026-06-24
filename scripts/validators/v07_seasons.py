from ..core.config import ALL_PROVIDERS
from ..core.parser import DleParser
from ..core.playlist_parser import extract_season_num, find_season_links


def run(client, reporter_section, verbose: bool = False) -> None:
    # 1. Test season regex patterns against known strings
    test_strings = [
        ("1 сезон", 1),
        ("Сезон 2", 2),
        ("сезон 3", 3),
        ("Season 4", 4),
        ("sezon 5", 5),
        ("S01", 1),
        ("s02e03", 2),
        ("S3E4", 3),
        ("/4-сезон", 4),
        ("5 сезон 2024", 5),
        ("Серіал назва 6 сезон", 6),
    ]

    for text, expected in test_strings:
        result = extract_season_num(text)
        status = "PASS" if result == expected else "FAIL"
        detail = f"\"{text}\" → got {result}, expected {expected}"
        reporter_section.check(
            f"Season regex: \"{text}\"",
            status, detail
        )

    # 2. Test season link extraction from real pages
    for p in ALL_PROVIDERS:
        parser = DleParser(p)
        home_url = p.base_url + p.home_path
        r = client.get(home_url, timeout=20)
        if not r.ok:
            reporter_section.check(f"{p.name} season links — home fetch failed", "FAIL", f"HTTP {r.status}")
            continue

        movies = parser.parse_list(r.body)
        series = [m for m in movies if m.type == "SERIES" and m.page_url and m.page_url != home_url]

        if not series:
            reporter_section.check(f"{p.name} season links — no series found on home", "WARN", "trying all items")
            series = [m for m in movies if m.page_url and m.page_url != home_url]

        tested = 0
        for s in series[:3]:
            rd = client.get(s.page_url, timeout=20)
            if not rd.ok:
                continue

            season_links = find_season_links(rd.body, s.page_url)
            # Also check default season
            default_season = extract_season_num(s.page_url) or extract_season_num(s.title)

            parts = []
            if default_season:
                parts.append(f"default=S{default_season}")
            if season_links:
                links_str = ", ".join(f"S{n}" for n, _ in season_links[:6])
                parts.append(f"links={season_links} -> {links_str}")

            status = "PASS" if season_links or default_season else "WARN"
            reporter_section.check(
                f"{p.name} seasons: \"{s.title[:30]}\"",
                status,
                "; ".join(parts) if parts else "no season info found"
            )
            tested += 1
            if tested >= 2:
                break
