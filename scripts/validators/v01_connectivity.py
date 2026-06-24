from ..core.config import ALL_PROVIDERS
from ..core.http_client import HttpClient


def run(client: HttpClient, reporter_section, verbose: bool = False) -> None:
    for p in ALL_PROVIDERS:
        url = p.base_url + p.home_path
        r = client.get(url, timeout=20)

        status = "PASS" if r.ok else "FAIL"
        detail = f"HTTP {r.status}, {len(r.body)} bytes, CT: {r.content_type}"
        if r.has_cf_ray:
            detail += " [Cloudflare proxied]"
        if not r.ok:
            detail += f" BODY: {r.body[:200]}"

        reporter_section.check(
            f"{p.name} доступність ({url})",
            status,
            detail
        )
