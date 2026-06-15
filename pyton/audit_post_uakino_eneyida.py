import argparse
import re
import sys
import json
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup


BASE_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
    "Accept-Language": "uk-UA,uk;q=0.9,en;q=0.8",
    "Connection": "keep-alive",
}

AJAX_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "application/json, text/javascript, */*; q=0.01",
    "Accept-Language": "uk-UA,uk;q=0.9,en;q=0.8",
    "X-Requested-With": "XMLHttpRequest",
    "Connection": "keep-alive",
}


def norm_url(url: str) -> str:
    if not url:
        return url
    if url.startswith("//"):
        return "https:" + url
    return url


def extract_first_group(pattern: str, text: str) -> str | None:
    m = re.search(pattern, text, flags=re.IGNORECASE | re.MULTILINE)
    if not m:
        return None
    return m.group(1)


def extract_news_id_kotlin_like(page_url: str) -> str:
    # Kotlin fallback: substringAfterLast("/"), then substringBefore("-") and digits-only check.
    last = page_url.rstrip("/").split("/")[-1]
    news_id = last.split("-")[0]
    if news_id.isdigit():
        return news_id
    return ""


def extract_news_id_and_edittime_from_html(page_url: str, html: str) -> tuple[str, str, str]:
    # Kotlin regex: data-news_id=['"](\d+)['"]
    news_id = extract_first_group(r"data-news_id\s*=\s*['\"](\d+)['\"]", html) or ""
    if not news_id:
        news_id = extract_news_id_kotlin_like(page_url)

    # Kotlin regex: dle_edittime\s*=\s*['"](\d+)['"]
    edittime = extract_first_group(r"dle_edittime\s*=\s*['\"](\d+)['\"]", html) or ""
    if not edittime:
        import time as _time
        edittime = str(int(_time.time()))


    # xfname Kotlin-like (path-based heuristic)
    if "uakino.best" in page_url or "uakino" in page_url:
        # UakinoProvider heuristic
        is_series = any(
            s in page_url for s in ["/seriesss/", "/seriali/", "/anime/", "/animeukr/", "/cartoons/", "/cartoon/"]
        )
        xfname = "seria" if is_series else "playlist"
    elif "eneyida" in page_url:
        is_series = any(
            s in page_url for s in ["/series/", "/serialy/", "/seriali/", "/anime/", "/cartoon-series/", "/multfilm-serials/"]
        )
        xfname = "seria" if is_series else "playlist"
    else:
        xfname = "playlist"

    return news_id, edittime, xfname


def extract_ajax_files_from_html_snippets(ajax_html: str) -> tuple[list[str], bool]:
    soup = BeautifulSoup(ajax_html, "html.parser")
    file_urls = []
    for li in soup.select("li[data-file], li[data-src]"):
        u = li.get("data-file") or li.get("data-src")
        if u:
            file_urls.append(norm_url(u))
    m3u8_found = bool(re.search(r"https?://[^\s\"']+\.m3u8", ajax_html, flags=re.IGNORECASE))
    return file_urls, m3u8_found


def try_parse_response_as_json(text: str):
    text_stripped = text.lstrip()
    if not text_stripped:
        return None
    if not (text_stripped.startswith("{") or text_stripped.startswith("[")):
        return None
    try:
        return json.loads(text)
    except Exception:
        return None


def audit_provider(page_url: str, provider_name: str):
    if provider_name == "uakino":
        base_url = "https://uakino.best/"
        endpoints = [
            urljoin(base_url, "engine/ajax/playlists.php"),
            urljoin(base_url, "index.php?do=ajax&mod=playlists"),
        ]
    elif provider_name == "eneyida":
        base_url = "https://eneyida.tv/"
        endpoints = [
            urljoin(base_url, "engine/ajax/playlists.php"),
            urljoin(base_url, "index.php?do=ajax&mod=playlists"),
        ]
    else:
        raise ValueError("Unknown provider")

    print(f"\n{'='*80}\n[{provider_name.upper()}] URL: {page_url}\n{'='*80}")

    with requests.Session() as s:
        s.headers.update(BASE_HEADERS)
        resp = s.get(page_url, timeout=20)
        print(f"GET page status={resp.status_code} len={len(resp.text)}")
        html = resp.text

    news_id, edittime, xfname = extract_news_id_and_edittime_from_html(page_url, html)
    print(f"Kotlin-like extraction: newsId={news_id!r}, edittime={edittime!r}, xfname={xfname!r}")

    if not news_id:
        print("WARNING: newsId is empty => POST will likely not work")

    ajax_session = requests.Session()
    ajax_session.headers.update(AJAX_HEADERS)

    body = {
        "news_id": news_id,
        "xfield": xfname,
        "time": edittime,
    }

    for ajax_url in endpoints:
        print(f"\nPOST attempt: {ajax_url}")
        try:
            r = ajax_session.post(ajax_url, data=body, headers={"Referer": page_url}, timeout=20)
            text = r.text or ""
            print(f"POST status={r.status_code} len={len(text)} preview={text[:180].replace('\n',' ')}")

            data = try_parse_response_as_json(text)
            if data is None:
                print("Response is not JSON (or JSON parse failed). Trying as HTML directly...")
                urls, m3u8_found = extract_ajax_files_from_html_snippets(text)
                print(f"AJAX HTML parse: files_found={len(urls)} m3u8_found={m3u8_found}")
                if urls:
                    print("First files:", urls[:5])
                continue

            # Kotlin expects: { success: boolean, response: string }
            if isinstance(data, dict):
                success = data.get("success", None)
                response_html = data.get("response", "")
                print(f"JSON keys={list(data.keys())} success={success!r} response_len={len(response_html) if isinstance(response_html,str) else 'n/a'}")

                if isinstance(response_html, str):
                    urls, m3u8_found = extract_ajax_files_from_html_snippets(response_html)
                    print(f"Parsed response: files_found={len(urls)} m3u8_found={m3u8_found}")
                    if urls:
                        print("First files:", urls[:5])
                else:
                    print("response is not a string; cannot parse")
            else:
                print("Unexpected JSON type (not dict).")

        except Exception as e:
            print(f"POST error: {e}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--uakino-url", default=None, help="Full page URL from uakino.best")
    ap.add_argument("--eneyida-url", default=None, help="Full page URL from eneyida.tv")
    args = ap.parse_args()

    if not args.uakino_url and not args.eneyida_url:
        print("Provide at least --uakino-url or --eneyida-url")
        sys.exit(2)

    if args.uakino_url:
        audit_provider(args.uakino_url, "uakino")
    if args.eneyida_url:
        audit_provider(args.eneyida_url, "eneyida")


if __name__ == "__main__":
    main()

