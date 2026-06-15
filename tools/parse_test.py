import requests
from bs4 import BeautifulSoup
import json

def test_provider(name, url, card_selector, title_selector, img_selector):
    print(f"--- Testing {name} at {url} ---")
    headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'}
    try:
        response = requests.get(url, headers=headers, timeout=10)
        soup = BeautifulSoup(response.text, 'html.parser')

        cards = soup.select(card_selector)
        print(f"Found {len(cards)} cards")

        for i, card in enumerate(cards[:3]):
            title_el = card.select_one(title_selector)
            img_el = card.select_one(img_selector)

            title = title_el.get_text(strip=True) if title_el else "NOT FOUND"
            page_url = title_el['href'] if title_el and title_el.has_attr('href') else "NOT FOUND"

            poster = ""
            if img_el:
                poster = img_el.get('data-src') or img_el.get('src') or ""

            print(f"Card {i+1}:")
            print(f"  Title: {title}")
            print(f"  URL: {page_url}")
            print(f"  Poster: {poster}")

    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    # Test Uakino
    test_provider(
        "Uakino",
        "https://uakino.best/ua/",
        ".movie-item",
        "a.movie-title",
        ".movie-img img"
    )

    # Test Eneyida
    test_provider(
        "Eneyida",
        "https://eneyida.tv/",
        ".short-item",
        ".short-title a",
        ".short-img img"
    )
