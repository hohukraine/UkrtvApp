import re
from urllib.parse import urljoin

from ..core.http_client import HttpClient


def extract_m3u8_urls(text: str, base_url: str) -> list[str]:
    urls = set()
    for pat in [
        r'https?://[^\s"\'<>]+?\.m3u8[^\s"\'<>]*',
        r'https?://[^\s"\'<>]+?\.mpd[^\s"\'<>]*',
    ]:
        for m in re.finditer(pat, text, re.IGNORECASE):
            urls.add(m.group(0))
    for line in text.splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            if re.search(r"\.(m4s|ts|m3u8|mp4)($|[?#])", line, re.IGNORECASE):
                urls.add(urljoin(base_url, line))
    return sorted(urls)


def check_m3u8(client: HttpClient, url: str, referer: str) -> dict:
    result = {"url": url, "status": "UNKNOWN", "detail": "", "segments_ok": 0, "keys_ok": 0}

    r = client.get(url, referer=referer, timeout=20)
    if not r.ok:
        result["status"] = "FAIL"
        result["detail"] = f"HTTP {r.status}"
        return result

    body = r.body
    if "#EXTM3U" not in body[:5000]:
        result["status"] = "FAIL"
        result["detail"] = "Not an m3u8 playlist (no #EXTM3U)"
        return result

    # Extract key URLs
    key_urls = []
    for line in body.splitlines():
        if line.startswith("#EXT-X-KEY"):
            m = re.search(r'URI="([^"]+)"', line)
            if m:
                key_urls.append(urljoin(r.url, m.group(1)))

    # Test keys
    keys_ok = 0
    for ku in key_urls[:3]:
        kr = client.get(ku, referer=referer, timeout=15)
        if kr.ok:
            keys_ok += 1

    # Extract segment/playlist URLs
    entries = []
    for line in body.splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            if re.search(r"\.(m4s|ts|m3u8|mp4)($|[?#])", line, re.IGNORECASE):
                entries.append(urljoin(r.url, line))

    # Test first 3 segments
    segments_ok = 0
    for eu in entries[:3]:
        er = client.get(eu, referer=referer, timeout=15)
        if er.ok:
            segments_ok += 1

    # Check if variant (has #EXT-X-STREAM-INF)
    is_variant = "#EXT-X-STREAM-INF" in body

    result["status"] = "PASS"
    issues = []
    if key_urls and keys_ok < len(key_urls[:3]):
        issues.append(f"keys: {keys_ok}/{min(3, len(key_urls))} accessible")
    if entries and segments_ok < len(entries[:3]):
        issues.append(f"segments: {segments_ok}/{min(3, len(entries))} accessible")
    if is_variant:
        issues.append("variant playlist (contains sub-playlists)")
    if not issues:
        issues.append(f"ok: {len(entries)} entries, {len(key_urls)} keys")

    result["detail"] = "; ".join(issues)
    result["segments_ok"] = segments_ok
    result["keys_ok"] = keys_ok
    return result


def run(client: HttpClient, reporter_section, verbose: bool = False) -> None:
    # We need m3u8 URLs to test — collect from known sources
    # First, try a known test playlist if available
    test_m3u8_urls = []

    # Search through any previously fetched pages for m3u8/mpd URLs
    # This validator depends on v05 having found streams
    reporter_section.check(
        "HLS validation requires m3u8 URLs from previous tests",
        "INFO",
        "Run v05_streams first to discover m3u8 URLs, then test them here"
    )

    # Try a few known working URLs as baseline (from the existing scripts)
    # We'll mark as info since we need dynamic resolution first
    reporter_section.check(
        "To test: fetch sample m3u8, verify segments and keys are accessible",
        "INFO",
        "Use: python verify_hls_headers.py <m3u8_url> for targeted check"
    )
