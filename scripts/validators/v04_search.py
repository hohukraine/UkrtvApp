from ..core.config import ALL_PROVIDERS
from ..core.parser import DleParser


SEARCH_QUERIES = [
    ("фільм", "аватар"),
    ("серіал", "гра престолів"),
]


def run(client, reporter_section, verbose: bool = False) -> None:
    for p in ALL_PROVIDERS:
        parser = DleParser(p)

        # 1. Non-AJAX search (POST to index.php)
        for q_title, q_val in SEARCH_QUERIES:
            search_url = p.base_url + "/" + p.search_endpoint_non_ajax
            post_data = f"do=search&subaction=search&story={q_val}"

            # Try to get hash first
            home_r = client.get(p.base_url + p.home_path, timeout=20)
            hash_match = __import__("re").search(r"""dle_login_hash\s*=\s*['"]([^'"]+)['"]""", home_r.body)

            r = client.post(search_url, data=post_data, referer=p.base_url + p.home_path, timeout=20)
            if r.ok:
                results = parser.parse_search(r.body)
                reporter_section.check(
                    f"{p.name} POST search ({q_val})",
                    "PASS" if results else "WARN",
                    f"{len(results)} results"
                )
            else:
                reporter_section.check(
                    f"{p.name} POST search ({q_val})",
                    "FAIL" if r.status != 0 else "FAIL",
                    f"HTTP {r.status}: {r.body[:100]}"
                )

            # 2. AJAX search fallback
            ajax_url = f"{p.base_url}/{p.search_endpoint_ajax}"
            ajax_data = f"query={q_val}"
            r2 = client.post(ajax_url, data=ajax_data, referer=p.base_url + p.home_path, timeout=20)
            if r2.ok and r2.body.strip():
                results = parser.parse_search(r2.body)
                reporter_section.check(
                    f"{p.name} AJAX search ({q_val})",
                    "PASS" if results else "WARN",
                    f"{len(results)} results"
                )
            else:
                reporter_section.check(
                    f"{p.name} AJAX search ({q_val})",
                    "WARN" if not r2.ok else "WARN",
                    f"HTTP {r2.status}, body={len(r2.body)} bytes"
                )
