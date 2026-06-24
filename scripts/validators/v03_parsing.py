from ..core.config import ALL_PROVIDERS
from ..core.parser import DleParser


def run(client, reporter_section, verbose: bool = False) -> None:
    for p in ALL_PROVIDERS:
        parser = DleParser(p)

        # Home page
        home_url = p.base_url + p.home_path
        r = client.get(home_url, timeout=20)
        if r.ok:
            movies = parser.parse_list(r.body)
            movies_movie = [m for m in movies if m.type == "MOVIE"]
            movies_series = [m for m in movies if m.type == "SERIES"]
            detail = f"{len(movies)} items ({len(movies_movie)} movies, {len(movies_series)} series)"
            if movies:
                detail += f" | First: \"{movies[0].title}\""
                if movies[0].rating:
                    detail += f" rating={movies[0].rating}"
                if not movies[0].poster:
                    detail += " [no poster]"
            reporter_section.check(
                f"{p.name} Home page parsing",
                "PASS" if len(movies) >= 3 else "WARN",
                detail
            )
        else:
            reporter_section.check(f"{p.name} Home page parsing", "FAIL", f"HTTP {r.status}")

        # Categories
        for cat_name, cat_path in p.category_paths.items():
            cat_url = f"{p.base_url}/{cat_path}"
            r = client.get(cat_url, timeout=20)
            if r.ok:
                movies = parser.parse_list(r.body)
                reporter_section.check(
                    f"{p.name} Category {cat_name} ({cat_path})",
                    "PASS" if len(movies) >= 2 else "WARN",
                    f"{len(movies)} items"
                )
            else:
                reporter_section.check(
                    f"{p.name} Category {cat_name} ({cat_path})",
                    "FAIL",
                    f"HTTP {r.status}"
                )

        # Detail: pick first movie from home page
        r = client.get(home_url, timeout=20)
        if r.ok:
            movies = parser.parse_list(r.body)
            tested = 0
            for m in movies[:5]:
                if not m.page_url or m.page_url == home_url:
                    continue
                rd = client.get(m.page_url, timeout=20)
                if not rd.ok:
                    reporter_section.check(f"{p.name} Detail {m.title[:40]}", "FAIL", f"HTTP {rd.status}")
                    continue
                detail = parser.parse_detail(rd.body, m.page_url)
                issues = []
                if detail.rating:
                    issues.append(f"rating={detail.rating}")
                if detail.genres:
                    issues.append(f"genres={detail.genres[:3]}")
                if detail.country:
                    issues.append(f"country={detail.country}")
                if detail.actors:
                    issues.append(f"actors={len(detail.actors)}")
                if detail.director:
                    issues.append(f"director={detail.director}")
                if detail.description:
                    desc_len = len(detail.description)
                    issues.append(f"desc={desc_len}ch")
                if not issues:
                    issues.append("basic metadata only")

                reporter_section.check(
                    f"{p.name} Detail \"{detail.title[:40]}\"",
                    "PASS" if any([detail.rating, detail.genres, detail.country, detail.description]) else "WARN",
                    "; ".join(issues)
                )
                tested += 1
                if tested >= 3:
                    break
