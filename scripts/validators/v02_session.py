import re

from ..core.config import ALL_PROVIDERS


DLE_HASH_REGEX = re.compile(r"""dle_login_hash\s*=\s*['"]([^'"]+)['"]""")


def run(client, reporter_section, verbose: bool = False) -> None:
    for p in ALL_PROVIDERS:
        urls = [p.base_url + p.home_path, p.base_url + p.search_endpoint_non_ajax]
        hashes = []

        for url in urls:
            r = client.get(url, timeout=20)
            if not r.ok:
                reporter_section.check(
                    f"{p.name} hash from {url}",
                    "FAIL",
                    f"HTTP {r.status} — cannot fetch"
                )
                continue

            match = DLE_HASH_REGEX.search(r.body)
            if match:
                hashes.append(match.group(1))
                reporter_section.check(
                    f"{p.name} dle_login_hash ({url.split('/')[-1]})",
                    "PASS",
                    f"hash={match.group(1)[:20]}..."
                )
            else:
                reporter_section.check(
                    f"{p.name} dle_login_hash ({url.split('/')[-1]})",
                    "FAIL",
                    "hash not found in page"
                )

        if len(hashes) >= 2:
            if hashes[0] == hashes[1]:
                reporter_section.check(
                    f"{p.name} hash consistency",
                    "PASS",
                    "same hash on both pages"
                )
            else:
                reporter_section.check(
                    f"{p.name} hash consistency",
                    "WARN",
                    f"different hashes: {hashes[0][:16]} vs {hashes[1][:16]}"
                )
