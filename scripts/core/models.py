from dataclasses import dataclass, field
from typing import Optional


@dataclass
class Movie:
    id: str
    title: str
    poster: str = ""
    year: Optional[str] = None
    page_url: str = ""
    type: str = "MOVIE"
    rating: Optional[str] = None


@dataclass
class MovieDetail:
    id: str
    title: str
    poster: str = ""
    description: str = ""
    year: Optional[str] = None
    genres: list[str] = field(default_factory=list)
    page_url: str = ""
    provider_name: str = ""
    rating: Optional[str] = None
    country: list[str] = field(default_factory=list)
    actors: list[str] = field(default_factory=list)
    director: list[str] = field(default_factory=list)
    content_type: str = "MOVIE"


@dataclass
class Episode:
    number: int
    title: str
    url: str
    voiceover: Optional[str] = None
    subtitles: Optional[str] = None


@dataclass
class Season:
    number: int
    episodes: list[Episode] = field(default_factory=list)


@dataclass
class MediaSource:
    url: str
    fallback_urls: list[str] = field(default_factory=list)
    referer: str = ""
    provider_name: str = ""
    seasons: list[Season] = field(default_factory=list)
    method: str = ""

    @property
    def is_series(self) -> bool:
        return len(self.seasons) > 0

    @property
    def primary_url(self) -> Optional[str]:
        return self.url or (self.seasons[0].episodes[0].url if self.seasons and self.seasons[0].episodes else None)


@dataclass
class StrategyResult:
    strategy_name: str
    success: bool
    media_source: Optional[MediaSource] = None
    error: Optional[str] = None
    duration_ms: float = 0.0
    detail: str = ""
