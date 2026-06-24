import subprocess
import tempfile
import os
import re
import time
from typing import Optional
from urllib.parse import urlparse, urljoin

from .config import USER_AGENT, CONNECT_TIMEOUT, MAX_RETRIES, RETRY_DELAY


class HttpResult:
    def __init__(self, status: int, url: str, body: str, headers: dict[str, str]):
        self.status = status
        self.url = url
        self.body = body
        self.headers = headers
        self.content_type = headers.get("content-type", "")

    @property
    def is_html(self) -> bool:
        return "text/html" in self.content_type.lower() or self.body.strip().startswith("<")

    @property
    def is_json(self) -> bool:
        ct = self.content_type.lower()
        return "application/json" in ct or self.body.strip().startswith("{") or self.body.strip().startswith("[")

    @property
    def ok(self) -> bool:
        return 200 <= self.status < 400

    @property
    def is_cloudflare(self) -> bool:
        return self.status in (403, 503, 429) and ("cf-ray" in self.headers or "cloudflare" in self.body[:500].lower())

    @property
    def has_cf_ray(self) -> bool:
        return "cf-ray" in self.headers


class HttpClient:
    def __init__(self, verbose: bool = False):
        self.verbose = verbose
        self._cookie_file = tempfile.NamedTemporaryFile(prefix="cookies_", suffix=".txt", delete=False)
        self._cookie_path = self._cookie_file.name
        self._cookie_file.close()

    def _run_curl(self, url: str, method: str = "GET", data: str = "",
                  referer: str = "", origin: str = "",
                  headers: Optional[dict[str, str]] = None,
                  timeout: int = CONNECT_TIMEOUT) -> HttpResult:
        cmd = [
            "curl", "-s", "-L", "-i",
            "--max-time", str(timeout),
            "--cookie", self._cookie_path,
            "--cookie-jar", self._cookie_path,
            "-A", USER_AGENT,
        ]
        if referer:
            cmd += ["-e", referer]
        if origin:
            cmd += ["-H", f"Origin: {origin}"]
        if headers:
            for k, v in headers.items():
                cmd += ["-H", f"{k}: {v}"]

        if method == "POST":
            cmd += ["-X", "POST"]
            if data:
                cmd += ["--data", data]

        cmd += [url]

        if self.verbose:
            print(f"  [curl] {method} {url}")

        try:
            result = subprocess.run(cmd, capture_output=True, timeout=timeout + 5)
        except subprocess.TimeoutExpired:
            return HttpResult(0, url, "<timeout>", {"content-type": "text/plain"})

        raw = result.stdout
        if not raw:
            raw = result.stderr

        header_bytes, _, body_bytes = raw.partition(b"\r\n\r\n")
        if not body_bytes:
            header_bytes, _, body_bytes = raw.partition(b"\n\n")

        header_text = header_bytes.decode("utf-8", errors="replace")
        body = body_bytes.decode("utf-8", errors="replace")

        status_match = re.search(r"HTTP/[\d.]+ (\d+)", header_text)
        status = int(status_match.group(1)) if status_match else 0

        final_url = url
        for line in header_text.splitlines():
            if line.lower().startswith("location:"):
                final_url = line.split(":", 1)[1].strip()
                if not final_url.startswith("http"):
                    parsed = urlparse(url)
                    base = f"{parsed.scheme}://{parsed.netloc}"
                    final_url = urljoin(base, final_url)

        parsed_headers = {}
        for line in header_text.splitlines():
            if ":" in line and not line.startswith("HTTP/"):
                k, v = line.split(":", 1)
                parsed_headers[k.strip().lower()] = v.strip()

        # Cloudflare detection in redirect chain
        if status in (403, 503, 429):
            pass

        if self.verbose and status != 200:
            print(f"  [curl] -> {status} {final_url} ({len(body)} bytes)")

        return HttpResult(status, final_url, body, parsed_headers)

    def get(self, url: str, referer: str = "", origin: str = "",
            headers: Optional[dict[str, str]] = None, timeout: int = CONNECT_TIMEOUT) -> HttpResult:
        result = self._run_curl(url, "GET", referer=referer, origin=origin, headers=headers, timeout=timeout)

        for attempt in range(1, MAX_RETRIES):
            if result.status != 0 and not result.is_cloudflare:
                break
            delay = RETRY_DELAY * (2 ** attempt)
            if self.verbose:
                print(f"  [retry {attempt}] waiting {delay}s...")
            time.sleep(delay)
            result = self._run_curl(url, "GET", referer=referer, origin=origin, headers=headers, timeout=timeout)

        return result

    def post(self, url: str, data: str, referer: str = "", origin: str = "",
             headers: Optional[dict[str, str]] = None, timeout: int = CONNECT_TIMEOUT) -> HttpResult:
        result = self._run_curl(url, "POST", data=data, referer=referer, origin=origin, headers=headers, timeout=timeout)
        for attempt in range(1, MAX_RETRIES):
            if result.status != 0 and not result.is_cloudflare:
                break
            delay = RETRY_DELAY * (2 ** attempt)
            if self.verbose:
                print(f"  [retry {attempt}] waiting {delay}s...")
            time.sleep(delay)
            result = self._run_curl(url, "POST", data=data, referer=referer, origin=origin, headers=headers, timeout=timeout)
        return result

    def resolve_url(self, url: str, base_url: str) -> str:
        if url.startswith("http"):
            return url
        if url.startswith("//"):
            return "https:" + url
        if url.startswith("dleid://"):
            return url
        parsed = urlparse(base_url)
        if url.startswith("/"):
            return f"{parsed.scheme}://{parsed.hostname}{url}"
        return base_url.rstrip("/") + "/" + url.lstrip("/")

    def close(self):
        try:
            os.unlink(self._cookie_path)
        except OSError:
            pass
