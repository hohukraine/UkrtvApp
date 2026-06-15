import argparse
import json
import sys
import time
from dataclasses import dataclass
from typing import Any, Dict, Optional

try:
    from playwright.sync_api import sync_playwright
except ModuleNotFoundError:
    print("Playwright is not installed. Run:\n  py -m pip install playwright\n  py -m playwright install chromium\n")
    raise



TARGET_DEFAULT = "https://uakino.best/filmy/online/"


@dataclass
class Capture:
    url: str
    method: str
    headers: Dict[str, str]
    json_body: Any


def _safe_json(data: bytes) -> Any:
    if not data:
        return None
    try:
        return json.loads(data.decode("utf-8", errors="replace"))
    except Exception:
        return None


def looks_like_api_json_response(json_obj: Any) -> bool:
    # Heuristic: uakino AJAX often returns {success: bool, response: ...}
    if isinstance(json_obj, dict):
        if "success" in json_obj or "response" in json_obj or "message" in json_obj:
            return True
    return False


def main():
    parser = argparse.ArgumentParser(
        description="uakino.best media client (Playwright) — captures XHR/Fetch JSON response when clicking 'Відтворити'"
    )
    parser.add_argument("--page", default=TARGET_DEFAULT, help="Page URL with 'online' content")
    parser.add_argument(
        "--click-first",
        action="store_true",
        help="Click the first available 'Відтворити' button on the page",
    )
    parser.add_argument(
        "--title",
        default=None,
        help="Click 'Відтворити' for a specific title (best-effort by text match)",
    )
    parser.add_argument(
        "--max-captures",
        type=int,
        default=3,
        help="How many interesting JSON captures to collect before exiting",
    )

    args = parser.parse_args()

    if not args.click_first and not args.title:
        # default to click-first for practicality
        args.click_first = True

    captures: list[Capture] = []

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=False)
        context = browser.new_context(
            user_agent=(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/120.0.0.0 Safari/537.36"
            ),
        )

        page = context.new_page()

        # Track and print frames as soon as an ashdi.vip iframe appears

        ashdi_frame = {"obj": None}

        def on_frame_added(frame):
            try:
                url = frame.url or ""
                if "ashdi.vip" in url.lower():
                    ashdi_frame["obj"] = frame
                    print(f"Detected iframe: {url}")
            except Exception:
                pass

        page.on("frameattached", on_frame_added)


        # --- Interception strategy ---
        # 1) listen to all responses
        # 2) capture:
        #    - JSON responses (for uakino/ajax and player init payloads)
        #    - any response whose URL ends with/contains .m3u8 (even if it's not JSON)
        def on_response(resp):

            nonlocal captures
            try:
                url = resp.url
                if not url:
                    return

                ct = (resp.headers.get("content-type") or "").lower()
                lower = url.lower()

                # Capture only likely API-ish JSON responses, but keep heuristics relaxed.
                # Also capture the actual HLS manifest responses (may be text/plain, not JSON)
                if ".m3u8" not in lower:
                    json_candidate = (
                        ("application/json" in ct)
                        or ("json" in ct)
                        or ("engine/ajax" in lower)
                        or ("playlists" in lower)
                        or ("controller" in lower)
                        or ("ajax" in lower)
                        or ("fetch" in lower)
                    )
                    if not json_candidate and ".json" not in lower:
                        return

                    body_bytes = resp.body()
                    json_obj = _safe_json(body_bytes)
                    if json_obj is None:
                        return

                    # keep original heuristic but do not require it strictly
                    if not looks_like_api_json_response(json_obj):
                        pass
                else:
                    # m3u8 response: print full body text (could include tokens)
                    req = resp.request
                    method = req.method
                    req_headers = dict(req.headers)
                    body = resp.body() or b""
                    body_text = body.decode("utf-8", errors="replace")

                    print("\n" + "=" * 80)
                    print("CAPTURED M3U8 RESPONSE")
                    print(f"Request: {method} {url}")
                    print("Headers (request):")
                    for k, v in req_headers.items():
                        if k.lower() in ("cookie", "authorization") or "token" in k.lower():
                            print(f"  {k}: {v}")
                        else:
                            print(f"  {k}: {v}")

                    print("\nResponse body (first 12000 chars):")
                    print(body_text[:12000])
                    print("=" * 80 + "\n")

                    captures.append(
                        Capture(url=url, method=method, headers=req_headers, json_body=None)
                    )
                    return



                req = resp.request
                method = req.method
                req_headers = dict(req.headers)

                # Print + store capture
                print("\n" + "=" * 80)
                print("CAPTURED JSON XHR/FETCH RESPONSE")
                print(f"Request: {method} {url}")
                print("Headers (request):")
                # Keep output readable: one header per line
                for k, v in req_headers.items():
                    if k.lower() in ("cookie", "authorization") or "token" in k.lower():
                        # print tokens fully (you requested that)
                        print(f"  {k}: {v}")
                    else:
                        print(f"  {k}: {v}")

                print("\nResponse JSON (full body):")
                print(json.dumps(json_obj, ensure_ascii=False, indent=2))
                print("=" * 80 + "\n")

                captures.append(
                    Capture(url=url, method=method, headers=req_headers, json_body=json_obj)
                )

            except Exception:
                return

        page.on("response", on_response)

        print(f"Opening page: {args.page}")
        page.goto(args.page, wait_until="domcontentloaded", timeout=60000)

        # Wait for content to load
        page.wait_for_timeout(1500)

        clicked = False

        if args.title:
            # Best-effort: find text node and click the nearest 'Відтворити' button
            # (Site markup may change; this is heuristic.)
            locator = page.locator("text=" + args.title).first
            locator.wait_for(timeout=3000)

            # Try to find a 'play' button in the same card/container
            container = locator.locator("xpath=ancestor::*[contains(@class,'movie') or contains(@class,'card')][1]")
            play_btn = container.get_by_role("button", name=lambda n: n and ("Відтворити" in n or "Play" in n))
            if play_btn.count() > 0:
                play_btn.first.click()
                clicked = True
            else:
                # fallback: click any element containing 'Відтворити' near container
                alt = container.locator("text=Відтворити").first
                if alt.count() > 0:
                    alt.click()
                    clicked = True

        if not clicked and args.click_first:
            # Click the first visible button with text 'Відтворити' (Ukrainian)
            # If site uses icons/spans, we broaden selector.
            candidates = page.locator("button:has-text('Відтворити'), a:has-text('Відтворити')").filter(
                has_text=re.compile(r"\bВідтворити\b", re.IGNORECASE)
            )
            try:
                candidates.first.wait_for(state="visible", timeout=5000)
                candidates.first.click()
                clicked = True
            except Exception:
                pass

            if not clicked:
                # Fallback: any element containing 'Відтворити'
                try:
                    page.locator("text=Відтворити").first.click(timeout=5000)
                    clicked = True
                except Exception:
                    pass

        if not clicked:
            # Instead of failing hard, still keep going and just wait for network.
            # This helps when the text 'Відтворити' is rendered as an icon or different markup.
            print("WARNING: Не вдалося знайти/клікнути 'Відтворити'. Продовжую збір мережевих JSON-відповідей без кліку...")


        # Give time for XHR/fetches to occur.
        # Ads often load first; keep listening longer for the final film manifest.
        print("Clicked 'Відтворити'. Waiting for network captures (JSON + m3u8)...")
        start = time.time()
        while len(captures) < args.max_captures and (time.time() - start) < 90:
            page.wait_for_timeout(500)


        print(f"\nDone. Captured {len(captures)} interesting JSON responses.")

        # Keep browser open briefly for manual inspection
        time.sleep(1)

        browser.close()


if __name__ == "__main__":
    try:
        # Small fix: re is needed for fallback filter in candidates
        import re

        main()
    except KeyboardInterrupt:
        sys.exit(130)

