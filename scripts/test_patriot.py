import requests
from bs4 import BeautifulSoup
import re

headers = {"User-Agent": "Mozilla/5.0"}
tmdb_url = "https://www.themoviedb.org/movie/2024-the-patriot"

r = requests.get(tmdb_url, headers=headers)
r.encoding = 'utf-8'
soup = BeautifulSoup(r.text, 'html.parser')

style_tag = soup.find('style')
accent = "#08121c"
on_accent = "#ffffff"

if style_tag:
    style_str = style_tag.string if style_tag.string else ""
    primary_match = re.search(r"--primaryColor: rgba\((.*?), (.*?), (.*?), (.*?)\);", style_str)
    if primary_match:
        red, g, b = float(primary_match.group(1)), float(primary_match.group(2)), float(primary_match.group(3))
        accent = "#{:02x}{:02x}{:02x}".format(int(red), int(g), int(b))

    contrast_match = re.search(r"--primaryColorContrast: (.*?);", style_str)
    if contrast_match:
        on_accent = contrast_match.group(1)

print(f"Accent: {accent}")
print(f"On Accent: {on_accent}")
