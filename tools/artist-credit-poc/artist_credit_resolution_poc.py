from __future__ import annotations

import argparse
import hashlib
import html
import json
import os
import re
import sqlite3
import sys
import unicodedata
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Iterable, Mapping, Sequence


class ResolutionStatus(str, Enum):
    RESOLVED = "resolved"
    UNRESOLVED = "unresolved"


@dataclass(frozen=True)
class CreditContext:
    original_credit: str
    album_title: str | None = None
    track_title: str | None = None
    structured_artists: tuple[str, ...] = ()
    source: str = "sample"
    source_id: str | None = None


@dataclass(frozen=True)
class Resolution:
    status: ResolutionStatus
    display_names: tuple[str, ...]
    reason: str
    confidence: float
    cacheable: bool = True
    evidence: tuple[str, ...] = ()


@dataclass
class CacheEntry:
    names: tuple[str, ...]
    reason: str


class ArtistCreditCache:
    def __init__(self, entries: Mapping[str, CacheEntry] | None = None) -> None:
        self._entries: dict[str, CacheEntry] = dict(entries or {})

    @classmethod
    def load(cls, path: Path) -> "ArtistCreditCache":
        if not path.exists():
            return cls()
        payload = json.loads(path.read_text(encoding="utf-8"))
        entries = {
            key: CacheEntry(tuple(value["names"]), value["reason"])
            for key, value in payload.get("entries", {}).items()
        }
        return cls(entries)

    def save(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "version": 1,
            "entries": {
                key: {"names": list(entry.names), "reason": entry.reason}
                for key, entry in sorted(self._entries.items())
            },
        }
        path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    def get(self, context: CreditContext) -> CacheEntry | None:
        return self._entries.get(credit_hash(context))

    def store(self, context: CreditContext, names: Sequence[str], reason: str) -> None:
        deduped = dedupe_names(names)
        if deduped:
            self._entries[credit_hash(context)] = CacheEntry(deduped, reason)

    def as_json(self) -> dict[str, object]:
        return {
            key: {"names": list(entry.names), "reason": entry.reason}
            for key, entry in sorted(self._entries.items())
        }


class InMemoryArtistIndex:
    def __init__(
        self,
        artists: Iterable[str] = (),
        albums: Mapping[str, Sequence[str]] | None = None,
    ) -> None:
        self._artists: dict[str, str] = {}
        for artist in artists:
            cleaned = clean_display_name(artist)
            if cleaned:
                self._artists[identity_key(cleaned)] = cleaned
        self._albums: dict[str, tuple[str, ...]] = {
            identity_key(title): dedupe_names(names)
            for title, names in (albums or {}).items()
        }

    def exact_artist(self, name: str) -> str | None:
        return self._artists.get(identity_key(name))

    def album_artists(self, title: str | None) -> tuple[str, ...]:
        if not title:
            return ()
        return self._albums.get(identity_key(title), ())

    def merge(self, other: "InMemoryArtistIndex") -> "InMemoryArtistIndex":
        artists = set(self._artists.values()) | set(other._artists.values())
        albums: dict[str, tuple[str, ...]] = dict(self._albums)
        albums.update(other._albums)
        return InMemoryArtistIndex(artists=artists, albums=albums)


class CompositeArtistIndex:
    def __init__(self, indexes: Sequence[InMemoryArtistIndex]) -> None:
        self._indexes = tuple(indexes)

    def exact_artist(self, name: str) -> str | None:
        for index in self._indexes:
            artist = index.exact_artist(name)
            if artist:
                return artist
        return None

    def album_artists(self, title: str | None) -> tuple[str, ...]:
        for index in self._indexes:
            artists = index.album_artists(title)
            if artists:
                return artists
        return ()


class AurralArtistIndex(InMemoryArtistIndex):
    def __init__(self, base_url: str, headers: Mapping[str, str]) -> None:
        super().__init__()
        self._base_url = base_url.rstrip("/")
        self._headers = dict(headers)
        self._memo: dict[str, str | None] = {}

    def exact_artist(self, name: str) -> str | None:
        key = identity_key(name)
        if key not in self._memo:
            self._memo[key] = self._search_exact_artist(name)
        return self._memo[key]

    def _search_exact_artist(self, name: str) -> str | None:
        payload = self._get_json(
            "/api/search/artists",
            {"query": name, "limit": "10", "offset": "0"},
        )
        for item in payload if isinstance(payload, list) else payload.get("items", []):
            candidate = item.get("name") if isinstance(item, dict) else None
            if candidate and identity_key(candidate) == identity_key(name):
                return clean_display_name(candidate)
        return None

    def _get_json(self, path: str, query: Mapping[str, str]) -> object:
        encoded = urllib.parse.urlencode(query)
        request = urllib.request.Request(
            f"{self._base_url}{path}?{encoded}",
            headers={"Accept": "application/json", **self._headers},
        )
        with urllib.request.urlopen(request) as response:
            return json.loads(response.read().decode("utf-8"))


def clean_display_name(name: str) -> str:
    return re.sub(r"\s+", " ", name).strip()


def identity_key(value: str) -> str:
    normalized = unicodedata.normalize("NFKC", value)
    normalized = normalized.replace("’", "'").replace("`", "'")
    normalized = re.sub(r"\s+", " ", normalized).strip()
    return normalized.casefold()


def dedupe_names(names: Iterable[str]) -> tuple[str, ...]:
    seen: set[str] = set()
    result: list[str] = []
    for name in names:
        cleaned = clean_display_name(name)
        key = identity_key(cleaned)
        if cleaned and key not in seen:
            seen.add(key)
            result.append(cleaned)
    return tuple(result)


def credit_hash(context: CreditContext) -> str:
    payload = {
        "credit": identity_key(context.original_credit),
        "album": identity_key(context.album_title or ""),
        "track": identity_key(context.track_title or ""),
    }
    encoded = json.dumps(payload, sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(encoded.encode("utf-8")).hexdigest()[:20]


def immediate_render(context: CreditContext, cache: ArtistCreditCache) -> tuple[str, ...]:
    entry = cache.get(context)
    if entry:
        return entry.names
    return (clean_display_name(context.original_credit),)


_SPLIT_PATTERNS = (
    re.compile(r"\s+(?:feat\.?|featuring|ft\.?|with)\s+", re.IGNORECASE),
    re.compile(r"\s+[xX]\s+"),
    re.compile(r"\s*&\s*"),
    re.compile(r"\s*,\s*"),
    re.compile(r"\s*•\s*"),
    re.compile(r"\s*;\s*"),
    re.compile(r"\s+/\s+"),
)


def split_artist_credit(credit: str) -> tuple[str, ...]:
    working = clean_display_name(credit)
    for pattern in _SPLIT_PATTERNS:
        working = pattern.sub("\u001f", working)
    return dedupe_names(segment for segment in working.split("\u001f"))


def resolve_credit(context: CreditContext, index: InMemoryArtistIndex) -> Resolution:
    structured = dedupe_names(context.structured_artists)
    if structured and structured_artists_are_useful(context.original_credit, structured, index):
        return Resolution(
            ResolutionStatus.RESOLVED,
            structured,
            "structured-artists",
            1.0,
            evidence=("server returned structured artists[]",),
        )

    full_credit = clean_display_name(context.original_credit)
    exact_full = index.exact_artist(full_credit)
    if exact_full:
        return Resolution(
            ResolutionStatus.RESOLVED,
            (exact_full,),
            "exact-full-credit",
            0.98,
            evidence=("full credit exists as a validated artist",),
        )

    candidates = split_artist_credit(full_credit)
    if len(candidates) <= 1:
        return Resolution(
            ResolutionStatus.UNRESOLVED,
            (full_credit,),
            "no-safe-split",
            0.0,
            cacheable=False,
            evidence=("no delimiter split produced multiple candidates",),
        )

    album_artists = index.album_artists(context.album_title)
    if album_artists and same_artist_set(candidates, album_artists):
        return Resolution(
            ResolutionStatus.RESOLVED,
            album_artists,
            "album-context",
            0.97,
            evidence=("album context confirms the candidate artist set",),
        )

    resolved_candidates = []
    missing_candidates = []
    for candidate in candidates:
        resolved = index.exact_artist(candidate)
        if resolved:
            resolved_candidates.append(resolved)
        else:
            missing_candidates.append(candidate)

    if missing_candidates:
        return Resolution(
            ResolutionStatus.UNRESOLVED,
            (full_credit,),
            "candidate-not-found",
            0.0,
            cacheable=False,
            evidence=tuple(f"missing: {candidate}" for candidate in missing_candidates),
        )

    return Resolution(
        ResolutionStatus.RESOLVED,
        dedupe_names(resolved_candidates),
        "validated-split",
        0.92,
        evidence=("all split candidates exist as validated artists",),
    )


def structured_artists_are_useful(
    original_credit: str,
    structured: Sequence[str],
    index: InMemoryArtistIndex,
) -> bool:
    if len(structured) > 1:
        return True
    if not structured:
        return False
    only = structured[0]
    return identity_key(only) != identity_key(original_credit) and index.exact_artist(only) is not None


def same_artist_set(left: Sequence[str], right: Sequence[str]) -> bool:
    return {identity_key(item) for item in left} == {identity_key(item) for item in right}


def decode_storage_value(value: object) -> str:
    if isinstance(value, bytes):
        try:
            text = value.decode("utf-8")
        except UnicodeDecodeError:
            return ""
    else:
        text = str(value)
    text = text.strip()
    if len(text) >= 2 and text[0] == '"' and text[-1] == '"':
        parsed = json.loads(text)
        return str(parsed)
    return text


@dataclass
class PocRunResult:
    contexts: list[dict[str, object]] = field(default_factory=list)
    cache_entries: dict[str, object] = field(default_factory=dict)


def run_resolution_pass(
    contexts: Sequence[CreditContext],
    index: InMemoryArtistIndex,
    cache: ArtistCreditCache,
) -> PocRunResult:
    result = PocRunResult()
    for context in contexts:
        before = immediate_render(context, cache)
        resolution = resolve_credit(context, index)
        if resolution.status == ResolutionStatus.RESOLVED and resolution.cacheable:
            cache.store(context, resolution.display_names, resolution.reason)
        after = immediate_render(context, cache)
        result.contexts.append(
            {
                "source": context.source,
                "sourceId": context.source_id,
                "trackTitle": context.track_title,
                "albumTitle": context.album_title,
                "originalCredit": context.original_credit,
                "immediateRender": list(before),
                "finalRender": list(after),
                "status": resolution.status.value,
                "reason": resolution.reason,
                "confidence": resolution.confidence,
                "evidence": list(resolution.evidence),
                "cacheKey": credit_hash(context),
            }
        )
    result.cache_entries = cache.as_json()
    return result


def sample_contexts() -> list[CreditContext]:
    return [
        CreditContext(
            original_credit="Anyma & LISA",
            track_title="Bad Angel",
            album_title="Genesys II",
            source="navidrome-song",
            source_id="sample-anyma-lisa",
        ),
        CreditContext(
            original_credit="Afrojack, Sia & David Guetta",
            track_title="Titanium",
            album_title="Titanium Single",
            source="navidrome-artist-row",
            source_id="sample-afrojack-sia-guetta",
        ),
        CreditContext(
            original_credit=(
                "Eric Buchholz & Braxton Burks, "
                "Eric Buchholz • Eric Buchholz & Braxton Burks"
            ),
            album_title="Pokemon Reorchestrated: Double Team!",
            structured_artists=("Eric Buchholz", "Braxton Burks"),
            source="navidrome-album",
            source_id="sample-pokemon-double-team",
        ),
        CreditContext(
            original_credit="Earth, Wind & Fire",
            source="known-group-guard",
            source_id="sample-earth-wind-fire",
        ),
        CreditContext(
            original_credit="Chase & Status",
            source="unsafe-split-guard",
            source_id="sample-chase-status",
        ),
    ]


def sample_artist_index() -> InMemoryArtistIndex:
    return InMemoryArtistIndex(
        artists={
            "Afrojack",
            "Anyma",
            "Braxton Burks",
            "David Guetta",
            "Earth, Wind & Fire",
            "Eric Buchholz",
            "LISA",
            "Sia",
        },
        albums={
            "Titanium Single": ("Afrojack", "Sia", "David Guetta"),
            "Pokemon Reorchestrated: Double Team!": ("Eric Buchholz", "Braxton Burks"),
        },
    )


def aurral_index_from_env() -> AurralArtistIndex | None:
    base_url = os.environ.get("AURRAL_BASE_URL", "").strip()
    if not base_url:
        return None

    headers: dict[str, str] = {}
    bearer = os.environ.get("AURRAL_BEARER", "").strip()
    if bearer:
        headers["Authorization"] = f"Bearer {bearer}"

    username = os.environ.get("AURRAL_USERNAME", "").strip()
    password = os.environ.get("AURRAL_PASSWORD", "").strip()
    if username and password and "Authorization" not in headers:
        import base64

        token = base64.b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
        headers["Authorization"] = f"Basic {token}"

    return AurralArtistIndex(base_url, headers)


def navidrome_contexts_from_firefox(profile_path: Path) -> list[CreditContext]:
    storage = profile_path / "storage" / "default" / "https+++music.remaxku.eu" / "ls" / "data.sqlite"
    if not storage.exists():
        return []

    rows: dict[str, str] = {}
    with sqlite3.connect(storage) as connection:
        for key, value in connection.execute("select key, value from data"):
            decoded_key = decode_storage_value(key)
            decoded_value = decode_storage_value(value)
            if decoded_key and decoded_value:
                rows[decoded_key] = decoded_value

    base_url = rows.get("server", "https://music.remaxku.eu").rstrip("/")
    username = rows.get("username", "")
    token = rows.get("subsonic-token", "")
    salt = rows.get("subsonic-salt", "")
    if not username or not token or not salt:
        return []

    contexts: list[CreditContext] = []
    for query in ("Anyma & LISA", "Afrojack, Sia & David Guetta"):
        payload = subsonic_get_json(
            base_url,
            username,
            token,
            salt,
            "search3",
            {"query": query, "artistCount": "5", "albumCount": "2", "songCount": "5"},
        )
        contexts.extend(contexts_from_subsonic_search(query, payload))
    return contexts


def subsonic_get_json(
    base_url: str,
    username: str,
    token: str,
    salt: str,
    endpoint: str,
    params: Mapping[str, str],
) -> object:
    query = {
        "u": username,
        "t": token,
        "s": salt,
        "v": "1.16.1",
        "c": "NavicArtistCreditPoc",
        "f": "json",
        **params,
    }
    request = urllib.request.Request(
        f"{base_url}/rest/{endpoint}.view?{urllib.parse.urlencode(query)}",
        headers={"Accept": "application/json"},
    )
    with urllib.request.urlopen(request) as response:
        return json.loads(response.read().decode("utf-8"))


def contexts_from_subsonic_search(query: str, payload: object) -> list[CreditContext]:
    if not isinstance(payload, dict):
        return []
    response = payload.get("subsonic-response", {})
    search = response.get("searchResult3", {}) if isinstance(response, dict) else {}
    contexts: list[CreditContext] = []
    for song in search.get("song", []) if isinstance(search, dict) else []:
        if not isinstance(song, dict):
            continue
        original_credit = song.get("artist") or query
        structured_artists = tuple(
            item.get("name", "")
            for item in song.get("artists", [])
            if isinstance(item, dict) and item.get("name")
        )
        contexts.append(
            CreditContext(
                original_credit=str(original_credit),
                album_title=song.get("album"),
                track_title=song.get("title"),
                structured_artists=structured_artists,
                source="live-navidrome-search",
                source_id=song.get("id"),
            )
        )
    return contexts


def write_report(path: Path, result: PocRunResult) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.suffix.casefold() == ".html":
        path.write_text(render_html_report(result), encoding="utf-8")
    else:
        path.write_text(
            json.dumps(
                {"contexts": result.contexts, "cacheEntries": result.cache_entries},
                ensure_ascii=False,
                indent=2,
            ),
            encoding="utf-8",
        )


def render_html_report(result: PocRunResult) -> str:
    rows = []
    for item in result.contexts:
        rows.append(
            "<tr>"
            f"<td>{html.escape(str(item['source']))}</td>"
            f"<td>{html.escape(str(item['originalCredit']))}</td>"
            f"<td>{html.escape(' • '.join(item['immediateRender']))}</td>"
            f"<td>{html.escape(' • '.join(item['finalRender']))}</td>"
            f"<td>{html.escape(str(item['status']))}</td>"
            f"<td>{html.escape(str(item['reason']))}</td>"
            "</tr>"
        )
    return """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>Artist Credit Resolution POC</title>
  <style>
    body { background: #111; color: #eee; font: 14px system-ui, sans-serif; margin: 32px; }
    table { border-collapse: collapse; width: 100%; }
    th, td { border: 1px solid #333; padding: 8px 10px; text-align: left; vertical-align: top; }
    th { background: #222; }
    td:nth-child(4) { color: #74d99f; }
  </style>
</head>
<body>
  <h1>Artist Credit Resolution POC</h1>
  <table>
    <thead>
      <tr>
        <th>Source</th>
        <th>Original credit</th>
        <th>Immediate render</th>
        <th>Final render</th>
        <th>Status</th>
        <th>Reason</th>
      </tr>
    </thead>
    <tbody>
""" + "\n".join(rows) + """
    </tbody>
  </table>
</body>
</html>
"""


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Emulate Navic artist-credit resolution.")
    parser.add_argument(
        "--cache",
        default="tools/artist-credit-poc/work/artist-credit-cache.json",
        help="Cache JSON path used for immediate render and persisted translations.",
    )
    parser.add_argument(
        "--report",
        default="tools/artist-credit-poc/work/artist-credit-report.html",
        help="Report path. Use .json for JSON or .html for a readable table.",
    )
    parser.add_argument(
        "--live-navidrome",
        action="store_true",
        help="Add sample live contexts from the local Firefox Navidrome session.",
    )
    parser.add_argument(
        "--firefox-profile",
        default=str(Path.home() / "AppData" / "Roaming" / "Mozilla" / "Firefox" / "Profiles" / "8559swam.default-release"),
        help="Firefox profile path used only with --live-navidrome.",
    )
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    cache = ArtistCreditCache.load(Path(args.cache))
    index: InMemoryArtistIndex | CompositeArtistIndex = sample_artist_index()
    aurral_index = aurral_index_from_env()
    if aurral_index:
        index = CompositeArtistIndex((index, aurral_index))

    contexts = sample_contexts()
    if args.live_navidrome:
        contexts.extend(navidrome_contexts_from_firefox(Path(args.firefox_profile)))

    result = run_resolution_pass(contexts, index, cache)
    cache.save(Path(args.cache))
    write_report(Path(args.report), result)

    resolved_count = sum(1 for item in result.contexts if item["status"] == "resolved")
    print(f"contexts={len(result.contexts)} resolved={resolved_count} report={args.report}")
    print(f"cache={args.cache}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
