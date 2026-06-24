#!/usr/bin/env python3
"""
UkrtvApp Audit Framework
Запуск: python audit.py [--run-all] [--only v01,v05] [--providers uakino,eneyida] [--verbose] [--save-html output/report.html]
"""

import sys
import os
import time
import argparse
from typing import Optional

# Add project root to path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from scripts.core.http_client import HttpClient
from scripts.core.reporter import Reporter


def main():
    parser = argparse.ArgumentParser(description="UkrtvApp Audit Framework")
    parser.add_argument("--run-all", action="store_true", help="Run all validators")
    parser.add_argument("--only", type=str, help="Comma-separated list of validators to run (e.g. v01,v05)")
    parser.add_argument("--providers", type=str, default="uakino,eneyida", help="Providers to test (comma-separated)")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    parser.add_argument("--save-html", type=str, default="", help="Save HTML report to file")
    parser.add_argument("--save-json", type=str, default="", help="Save JSON report to file")
    args = parser.parse_args()

    if not args.run_all and not args.only:
        parser.print_help()
        print("\nSpecify --run-all or --only <validators>")
        sys.exit(1)

    # Determine which validators to run
    all_validators = [
        ("v01", "Connectivity", "validators.v01_connectivity"),
        ("v02", "Session Hash", "validators.v02_session"),
        ("v03", "Parsing (Home + Categories + Detail)", "validators.v03_parsing"),
        ("v04", "Search", "validators.v04_search"),
        ("v05", "Stream Resolution (ALL 4 strategies)", "validators.v05_streams"),
        ("v06", "HLS Validation", "validators.v06_hls"),
        ("v07", "Season Detection", "validators.v07_seasons"),
        ("v08", "ContentUtils + Playlist Formats", "validators.v08_utils"),
    ]

    if args.run_all:
        selected = all_validators
    else:
        only_set = set(args.only.split(","))
        selected = [v for v in all_validators if v[0] in only_set]

    # Initialize
    client = HttpClient(verbose=args.verbose)
    reporter = Reporter()
    total_start = time.time()

    print(f"UkrtvApp Audit Framework")
    print(f"{'='*60}")
    print(f"Providers: {args.providers}")
    print(f"Validators: {', '.join(v[0] for v in selected)}")
    print(f"{'='*60}\n")

    # Run each validator
    for vid, vname, vmod_path in selected:
        print(f"[{vid}] {vname}... ", end="", flush=True)
        try:
            # Dynamic import
            import importlib
            mod_path = f"scripts.{vmod_path}"
            vmod = importlib.import_module(mod_path)

            section = reporter.section(f"{vid}: {vname}")
            with section:
                vmod.run(client, section, verbose=args.verbose)

            print("✓")
        except Exception as e:
            print(f"✗ ERROR: {e}")
            import traceback
            if args.verbose:
                traceback.print_exc()

    total_duration = time.time() - total_start
    client.close()

    # Summary
    all_checks = [c for checks in reporter.results.values() for c in checks]
    passed = sum(1 for c in all_checks if c.get("status") == "PASS")
    failed = sum(1 for c in all_checks if c.get("status") == "FAIL")
    total = len(all_checks)

    print(f"\n{'='*60}")
    print(f"Results: {passed} PASS, {failed} FAIL, {total} TOTAL")
    print(f"Duration: {total_duration:.1f}s")
    print(f"{'='*60}")

    # Extract strategy summary from v05 results
    if "v05: Stream Resolution (ALL 4 strategies)" in reporter.results:
        print("\n--- Strategy Matrix ---")
        strategy_checks = reporter.results["v05: Stream Resolution (ALL 4 strategies)"]
        by_strategy = {}
        for c in strategy_checks:
            for sname in ["HtmlPlaylist", "Script", "Iframe", "Ajax"]:
                if f"→ {sname}" in c.get("check", ""):
                    by_strategy.setdefault(sname, [])
                    by_strategy[sname].append(c)

        for sname, checks in sorted(by_strategy.items()):
            sp = sum(1 for c in checks if c.get("status") == "PASS")
            st = len(checks)
            print(f"  {sname:20s}: {sp}/{st} passed ({sp/st*100:.0f}%)" if st else f"  {sname:20s}: --")

    # Save reports
    if args.save_html:
        reporter.generate(args.save_html)
        print(f"HTML report saved: {args.save_html}")

    if args.save_json:
        with open(args.save_json, "w", encoding="utf-8") as f:
            f.write(reporter.to_json())
        print(f"JSON report saved: {args.save_json}")

    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
