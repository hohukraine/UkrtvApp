# TODO

## Goal
Make `scripts/app_emulator.py` correctly extract **up to 8 seasons** and **video stream URLs (m3u8/mp4/other media)**.

## Steps
- [ ] Inspect current `scripts/app_emulator.py` logic (already done)
- [ ] Update `scripts/app_emulator.py`:
  - [ ] Collect seasons dynamically but cap at 8
  - [ ] Fetch episode/playlist items per season via AJAX (probe multiple actions/fields)
  - [ ] Resolve playable stream URLs per episode (parse HTML/script for m3u8/mp4, and fallback to AJAX endpoints)
  - [ ] Output structured result (console + optional JSON)
- [ ] Add minimal CLI options (target provider, output json path)
- [ ] Run a quick smoke test (execute the script)

