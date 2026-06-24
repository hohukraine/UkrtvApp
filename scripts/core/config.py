from dataclasses import dataclass, field
from typing import Optional


@dataclass
class Selectors:
    item: str
    title: str
    poster: str
    description: str
    year: Optional[str] = None
    search_link: str = "a"
    search_title: Optional[str] = "span"


@dataclass
class ProviderProfile:
    name: str
    base_url: str
    brand_color: str
    logo_url: str
    selectors: Selectors
    series_markers: list[str]
    category_paths: dict[str, str]
    home_path: str = ""
    playlist_endpoints: list[str] = field(default_factory=lambda: [
        "engine/ajax/playlists.php",
        "engine/ajax/video.php",
        "engine/ajax/get_playlist.php"
    ])
    search_endpoint_ajax: str = "engine/ajax/search.php"
    search_endpoint_non_ajax: str = "index.php?do=search&subaction=search"
    id_regex_pattern: str = r"^(\d+)"
    home_path: str = ""


UAKINO = ProviderProfile(
    name="Uakino",
    base_url="https://uakino.best/",
    brand_color="#E50914",
    logo_url="https://uakino.best/templates/uakino/images/logo.png",
    home_path="/ua/",
    selectors=Selectors(
        item=".movie-item, .short-item",
        title=".movie-title, .short-title",
        poster="img[data-src], img[src]",
        description=".full-text, .full-description",
        year=".movie-desk-item:contains(Рік:) .deck-value, .fi-year"
    ),
    series_markers=["/seriesss/", "/seriali/", "/animeukr/", "/cartoon/", "/cartoons/", "/tv-shows/"],
    category_paths={
        "MOVIES": "filmy/online/",
        "SERIES": "seriesss/online/",
        "ANIME": "animeukr/online/",
        "CARTOONS": "cartoon/online/",
        "CARTOON_SERIES": "cartoon/cartoonseries/"
    }
)

ENEYIDA = ProviderProfile(
    name="Eneyida",
    base_url="https://eneyida.tv/",
    brand_color="#FF9800",
    logo_url="https://eneyida.tv/templates/Eneyida/images/logo.png",
    home_path="",
    selectors=Selectors(
        item="article.short",
        title="a.short_title",
        poster="a.short_img img",
        description=".full-text",
        year=".short_subtitle, .full-right li:contains(Рік), .full-info li:contains(Рік)"
    ),
    series_markers=["/series/", "/cartoon-series/", "/anime-series/", "/serialy/", "/anime/", "/filmi-seriali/"],
    category_paths={
        "MOVIES": "films/",
        "SERIES": "series/",
        "ANIME": "anime/",
        "CARTOONS": "cartoon/",
        "CARTOON_SERIES": "cartoon-series/"
    }
)

ALL_PROVIDERS = [UAKINO, ENEYIDA]
PROVIDER_MAP = {p.name.lower(): p for p in ALL_PROVIDERS}

USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
CONNECT_TIMEOUT = 15
READ_TIMEOUT = 20
MAX_RETRIES = 3
RETRY_DELAY = 2.0
