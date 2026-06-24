import json
import os
from datetime import datetime
from typing import Any


REPORT_TEMPLATE = """<!DOCTYPE html>
<html lang="uk">
<head>
<meta charset="utf-8">
<title>UkrtvApp Audit Report</title>
<style>
body {{ font-family: -apple-system, BlinkMacSystemFont, sans-serif; max-width: 1200px; margin: 0 auto; padding: 20px; background: #1a1a2e; color: #eee; }}
h1, h2, h3 {{ color: #e94560; }}
h2 {{ border-bottom: 1px solid #333; padding-bottom: 8px; margin-top: 40px; }}
table {{ border-collapse: collapse; width: 100%; margin: 16px 0; }}
th, td {{ padding: 10px 14px; text-align: left; border-bottom: 1px solid #333; }}
th {{ background: #16213e; color: #e94560; font-weight: 600; }}
.pass {{ color: #4ecca3; font-weight: bold; }}
.fail {{ color: #e94560; font-weight: bold; }}
.warn {{ color: #f5a623; font-weight: bold; }}
.summary {{ display: flex; gap: 20px; margin: 20px 0; }}
.summary-item {{ background: #16213e; padding: 20px; border-radius: 8px; flex: 1; text-align: center; }}
.summary-item .num {{ font-size: 2.5em; font-weight: bold; }}
.summary-item .label {{ font-size: 0.9em; color: #aaa; margin-top: 4px; }}
pre {{ background: #0f3460; padding: 12px; border-radius: 4px; overflow-x: auto; font-size: 13px; }}
code {{ background: #0f3460; padding: 2px 6px; border-radius: 3px; }}
.strategy-table td.strategy-name {{ font-weight: 600; }}
.detail-toggle {{ cursor: pointer; color: #4ecca3; }}
.detail-content {{ display: none; }}
.progress {{ height: 20px; background: #333; border-radius: 10px; margin: 8px 0; overflow: hidden; }}
.progress-bar {{ height: 100%; border-radius: 10px; transition: width 0.3s; }}
.footer {{ margin-top: 40px; padding-top: 20px; border-top: 1px solid #333; color: #666; font-size: 0.85em; }}
</style>
</head>
<body>
<h1>📊 UkrtvApp Audit Report</h1>
<p>Generated: {timestamp}</p>

<div class="summary">
  <div class="summary-item">
    <div class="num">{passed}</div>
    <div class="label">✅ Passed</div>
  </div>
  <div class="summary-item">
    <div class="num">{failed}</div>
    <div class="label">❌ Failed</div>
  </div>
  <div class="summary-item">
    <div class="num">{total}</div>
    <div class="label">📋 Total Tests</div>
  </div>
  <div class="summary-item">
    <div class="num">{duration:.1f}s</div>
    <div class="label">⏱ Duration</div>
  </div>
</div>

{content}

<div class="footer">
  UkrtvApp Audit Framework | {timestamp}
</div>
</body>
</html>"""


SECTION_TEMPLATE = """
<h2>{name}</h2>
{description}
<div class="progress">
  <div class="progress-bar" style="width:{pct}%;background:{color}"></div>
</div>
<table>
<tr><th>Check</th><th>Status</th><th>Detail</th></tr>
{rows}
</table>
"""


class Reporter:
    def __init__(self):
        self.results: dict[str, list[dict[str, Any]]] = {}
        self.start_time = datetime.now()

    def section(self, name: str) -> "SectionBuilder":
        return SectionBuilder(self, name)

    def generate(self, output_path: str = "") -> str:
        end_time = datetime.now()
        duration = (end_time - self.start_time).total_seconds()

        all_checks = []
        content_parts = []

        for sname, checks in self.results.items():
            passed = sum(1 for c in checks if c.get("status") == "PASS")
            total = len(checks)
            pct = (passed / total * 100) if total else 0
            color = "#4ecca3" if pct >= 80 else ("#f5a623" if pct >= 50 else "#e94560")

            desc = ""
            rows_html = ""
            for c in checks:
                status_class = c.get("status", "FAIL").lower()
                rows_html += f"<tr><td>{c.get('check', '')}</td>"
                rows_html += f'<td class="{status_class}">{c.get("status", "FAIL")}</td>'
                detail = c.get("detail", "")
                if len(detail) > 120:
                    detail_id = f"d{hash(sname + c['check'])}".replace("-", "_")
                    rows_html += f'<td><span class="detail-toggle" onclick="document.getElementById(\'{detail_id}\').style.display=\'block\';this.style.display=\'none\'">▶ show</span>'
                    rows_html += f'<div id="{detail_id}" class="detail-content"><pre>{detail}</pre></div></td>'
                else:
                    rows_html += f"<td>{detail}</td>"
                rows_html += "</tr>"

            content_parts.append(SECTION_TEMPLATE.format(
                name=sname, description=desc, pct=pct, color=color, rows=rows_html
            ))

        all_checks = [c for checks in self.results.values() for c in checks]
        passed = sum(1 for c in all_checks if c.get("status") == "PASS")
        failed = sum(1 for c in all_checks if c.get("status") == "FAIL")
        total = len(all_checks)

        html = REPORT_TEMPLATE.format(
            timestamp=self.start_time.strftime("%Y-%m-%d %H:%M:%S"),
            passed=passed, failed=failed, total=total, duration=duration,
            content="\n".join(content_parts)
        )

        if output_path:
            os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
            with open(output_path, "w", encoding="utf-8") as f:
                f.write(html)

        return html

    def to_json(self) -> str:
        return json.dumps(self.results, ensure_ascii=False, indent=2, default=str)


class SectionBuilder:
    def __init__(self, reporter: Reporter, name: str):
        self.reporter = reporter
        self.name = name
        self.checks: list[dict[str, Any]] = []

    def check(self, name: str, status: str, detail: str = ""):
        self.checks.append({
            "check": name,
            "status": status,
            "detail": detail
        })
        return self

    def __enter__(self):
        return self

    def __exit__(self, *args):
        if self.name in self.reporter.results:
            self.reporter.results[self.name].extend(self.checks)
        else:
            self.reporter.results[self.name] = self.checks
