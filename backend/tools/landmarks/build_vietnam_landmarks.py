#!/usr/bin/env python3
"""Build the deterministic nationwide landmark fixture and quality reports."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import re
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from difflib import SequenceMatcher
from pathlib import Path
from typing import Any


BACKEND_ROOT = Path(__file__).resolve().parents[2]
TOOL_ROOT = Path(__file__).resolve().parent
SOURCE_FILE = TOOL_ROOT / "sources" / "vietnam_travel_63.json"
MATCH_FILE = TOOL_ROOT / "sources" / "coordinate_matches.json"
MANUAL_FILE = TOOL_ROOT / "config" / "manual_landmarks.json"
COORDINATE_OVERRIDE_FILE = TOOL_ROOT / "config" / "coordinate_overrides.json"
SOURCE_CORRECTION_FILE = TOOL_ROOT / "config" / "source_row_corrections.json"
ALIAS_FILE = TOOL_ROOT / "config" / "province_aliases.json"
BBOX_FILE = TOOL_ROOT / "config" / "province_bboxes_legacy_63.json"
LANDMARK_FILE = BACKEND_ROOT / "src" / "main" / "resources" / "data" / "landmarks.json"
COVERAGE_FILE = TOOL_ROOT / "reports" / "landmark_coverage.json"
QUARANTINE_FILE = TOOL_ROOT / "reports" / "landmark_quarantine.json"
LOCATION_FILE = BACKEND_ROOT / "src" / "main" / "resources" / "data" / "locations.json"
CURRENT_PROVINCE_FILE = BACKEND_ROOT / "src" / "main" / "resources" / "data" / "provinces-current-34.json"

WIKIDATA_API = "https://www.wikidata.org/w/api.php"
USER_AGENT = "LuxeStay-landmark-catalog/1.0 (local project data build)"
VIETNAM_BOUNDS = (8.0, 23.5, 102.0, 116.0)


def load_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8-sig") as source:
        return json.load(source)


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(value, ensure_ascii=False, indent=2, sort_keys=False) + "\n"
    path.write_text(payload, encoding="utf-8", newline="\n")


def load_source_rows() -> list[dict[str, Any]]:
    rows = [dict(row) for row in load_json(SOURCE_FILE)]
    rows_by_id = {row["sourceObjectId"]: row for row in rows}
    correction_ids: set[str] = set()
    corrections = load_json(SOURCE_CORRECTION_FILE) if SOURCE_CORRECTION_FILE.exists() else []
    allowed_fields = {"nameVi", "descriptionVi", "rating", "keywords"}
    for correction in corrections:
        source_id = str(correction["sourceObjectId"])
        if source_id in correction_ids:
            raise ValueError(f"Duplicate source-row correction: {source_id}")
        row = rows_by_id.get(source_id)
        if row is None:
            raise ValueError(f"Source-row correction points to unknown row: {source_id}")
        unsupported = set(correction) - allowed_fields - {"sourceObjectId"}
        if unsupported:
            raise ValueError(f"Unsupported source-row correction fields for {source_id}: {sorted(unsupported)}")
        for field in allowed_fields:
            if field in correction:
                row[field] = correction[field]
        correction_ids.add(source_id)
    return rows


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def normalize(value: str | None) -> str:
    text = unicodedata.normalize("NFC", (value or "").strip().lower())
    text = text.replace("đ", "d")
    text = "".join(character for character in unicodedata.normalize("NFD", text)
                   if unicodedata.category(character) != "Mn")
    text = re.sub(r"\b(tinh|thanh pho|tp|province|city)\b", " ", text)
    return re.sub(r"[^a-z0-9]+", " ", text).strip()


def similarity(left: str, right: str) -> float:
    left_normalized = normalize(left)
    right_normalized = normalize(right)
    if not left_normalized or not right_normalized:
        return 0.0
    if left_normalized == right_normalized:
        return 1.0
    left_tokens = set(left_normalized.split())
    right_tokens = set(right_normalized.split())
    token_score = len(left_tokens & right_tokens) / max(len(left_tokens | right_tokens), 1)
    sequence_score = SequenceMatcher(None, left_normalized, right_normalized).ratio()
    return max(sequence_score, token_score)


def province_lookup() -> tuple[dict[str, dict[str, str]], dict[str, dict[str, str]]]:
    aliases = load_json(ALIAS_FILE)
    provinces = load_json(LOCATION_FILE)
    by_name: dict[str, dict[str, str]] = {}
    by_code: dict[str, dict[str, str]] = {}
    for province in provinces:
        record = {
            "code": str(province["code"]),
            "name": province["name"],
            "codename": province["codename"],
        }
        by_code[record["code"]] = record
        by_name[normalize(record["name"])] = record
        by_name[normalize(record["codename"])] = record
    for alias, code in aliases.items():
        if str(code) not in by_code:
            raise ValueError(f"Province alias {alias!r} points to missing code {code}")
        by_name[normalize(alias)] = by_code[str(code)]
    return by_name, by_code


def current_province_lookup() -> tuple[dict[str, dict[str, Any]], dict[str, dict[str, Any]]]:
    by_code: dict[str, dict[str, Any]] = {}
    by_legacy_code: dict[str, dict[str, Any]] = {}
    for province in load_json(CURRENT_PROVINCE_FILE):
        source_code = str(province["sourceCode"])
        if not source_code.startswith("VN34-"):
            raise ValueError(f"Current province source code must use VN34- prefix: {source_code}")
        if source_code in by_code:
            raise ValueError(f"Duplicate current province source code: {source_code}")
        by_code[source_code] = province
        for legacy_code in province.get("legacyProvinceCodes", []):
            legacy_code = str(legacy_code)
            if legacy_code in by_legacy_code:
                raise ValueError(f"Legacy province code mapped more than once: {legacy_code}")
            by_legacy_code[legacy_code] = province
    legacy_codes = {str(province["code"]) for province in load_json(LOCATION_FILE)}
    if set(by_legacy_code) != legacy_codes:
        missing = sorted(legacy_codes - set(by_legacy_code), key=int)
        extra = sorted(set(by_legacy_code) - legacy_codes, key=int)
        raise ValueError(f"Current province aliases do not cover legacy baseline; missing={missing}, extra={extra}")
    return by_code, by_legacy_code


def canonicalize_manual_landmarks(landmarks: list[dict[str, Any]],
                                  current_by_code: dict[str, dict[str, Any]],
                                  current_by_legacy_code: dict[str, dict[str, Any]]) -> list[dict[str, Any]]:
    canonicalized = []
    for landmark in landmarks:
        item = dict(landmark)
        province_code = str(item["provinceCode"])
        if province_code in current_by_legacy_code:
            item["provinceCode"] = current_by_legacy_code[province_code]["sourceCode"]
        elif province_code not in current_by_code:
            raise ValueError(f"Manual landmark uses unknown province code: {province_code}")
        canonicalized.append(item)
    return canonicalized


def coordinate_matches_with_overrides(rows: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    matches = {item["sourceObjectId"]: item for item in load_json(MATCH_FILE)} if MATCH_FILE.exists() else {}
    source_ids = {row["sourceObjectId"] for row in rows}
    override_ids: set[str] = set()
    overrides = load_json(COORDINATE_OVERRIDE_FILE) if COORDINATE_OVERRIDE_FILE.exists() else []
    for override in overrides:
        source_id = str(override["sourceObjectId"])
        if source_id in override_ids:
            raise ValueError(f"Duplicate coordinate override: {source_id}")
        if source_id not in source_ids:
            raise ValueError(f"Coordinate override points to unknown source row: {source_id}")
        latitude = float(override["latitude"])
        longitude = float(override["longitude"])
        if not valid_coordinate(latitude, longitude):
            raise ValueError(f"Coordinate override is outside Vietnam: {source_id}")
        override_ids.add(source_id)
        matches[source_id] = override
    return matches


def bootstrap_source(raw_path: Path) -> None:
    raw_rows = load_json(raw_path)
    corrections = {
        "Quang Ninh": "Quảng Ninh",
        "Nam Đinh": "Nam Định",
        "TP. Hồ Chí Minh": "Hồ Chí Minh",
    }
    normalized_rows = []
    for index, row in enumerate(raw_rows, start=1):
        source_row = int(row.get("STT") or index)
        province_name = str(row.get("Vị trí") or "").strip()
        province_name = corrections.get(province_name, province_name)
        name = str(row.get("Tên địa điểm") or "").strip()
        if not name or not province_name:
            continue
        normalized_rows.append({
            "sourceObjectId": f"VNTRAVEL-{source_row:03d}",
            "sourceRow": source_row,
            "nameVi": name,
            "provinceName": province_name,
            "descriptionVi": str(row.get("Mô tả") or "").strip(),
            "rating": str(row.get("Đánh giá") or "").strip(),
            "keywords": str(row.get("Từ Khóa") or "").strip(),
            "imageUrl": str(row.get("Ảnh") or "").strip(),
        })
    normalized_rows.sort(key=lambda item: item["sourceRow"])
    write_json(SOURCE_FILE, normalized_rows)
    print(json.dumps({"sourceRows": len(normalized_rows), "path": str(SOURCE_FILE)}, ensure_ascii=False))


def request_json(params: dict[str, str], attempts: int = 4) -> Any:
    url = WIKIDATA_API + "?" + urllib.parse.urlencode(params)
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(attempts):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.load(response)
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError):
            if attempt + 1 == attempts:
                raise
            time.sleep(2 ** attempt)
    raise RuntimeError("Unreachable retry state")


def download_range_file(url: str, output: Path, workers: int = 4, chunk_size: int = 10_000_000) -> None:
    probe = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Range": "bytes=0-0"})
    with urllib.request.urlopen(probe, timeout=120) as response:
        content_range = response.headers.get("Content-Range", "")
    total_match = re.search(r"/(\d+)$", content_range)
    if not total_match:
        raise RuntimeError(f"Source does not expose a ranged content length: {content_range!r}")
    total_size = int(total_match.group(1))
    parts_dir = output.with_suffix(output.suffix + ".parts")
    parts_dir.mkdir(parents=True, exist_ok=True)

    ranges = []
    for index, start in enumerate(range(0, total_size, chunk_size)):
        end = min(start + chunk_size - 1, total_size - 1)
        ranges.append((index, start, end, parts_dir / f"part-{index:04d}.bin"))

    def fetch(part: tuple[int, int, int, Path]) -> tuple[int, Path]:
        index, start, end, path = part
        expected = end - start + 1
        if path.exists() and path.stat().st_size == expected:
            return index, path
        request = urllib.request.Request(url, headers={
            "User-Agent": USER_AGENT,
            "Range": f"bytes={start}-{end}",
        })
        with urllib.request.urlopen(request, timeout=240) as response:
            payload = response.read()
        if len(payload) != expected:
            raise RuntimeError(f"Range {start}-{end} returned {len(payload)} bytes, expected {expected}")
        path.write_bytes(payload)
        return index, path

    with ThreadPoolExecutor(max_workers=max(1, workers)) as executor:
        futures = [executor.submit(fetch, part) for part in ranges]
        for completed, future in enumerate(as_completed(futures), start=1):
            index, _ = future.result()
            print(f"download range {completed}/{len(ranges)} part={index:04d}")

    with output.open("wb") as destination:
        for _, _, _, part_path in ranges:
            with part_path.open("rb") as source:
                for chunk in iter(lambda: source.read(1024 * 1024), b""):
                    destination.write(chunk)
    if output.stat().st_size != total_size:
        raise RuntimeError(f"Joined file has {output.stat().st_size} bytes, expected {total_size}")
    print(json.dumps({"downloaded": str(output), "bytes": total_size, "sha256": sha256(output)}))


def entity_coordinate(entity: dict[str, Any]) -> tuple[float, float] | None:
    claims = entity.get("claims", {}).get("P625", [])
    for claim in claims:
        value = claim.get("mainsnak", {}).get("datavalue", {}).get("value")
        if isinstance(value, dict) and "latitude" in value and "longitude" in value:
            return float(value["latitude"]), float(value["longitude"])
    return None


def entity_names(entity: dict[str, Any]) -> list[str]:
    names = []
    for language in ("vi", "en"):
        label = entity.get("labels", {}).get(language, {}).get("value")
        if label:
            names.append(label)
        for alias in entity.get("aliases", {}).get(language, []):
            if alias.get("value"):
                names.append(alias["value"])
    return names


def wikidata_match(row: dict[str, Any]) -> dict[str, Any] | None:
    search_terms = [
        f"{row['nameVi']} {row['provinceName']} Việt Nam",
        f"{row['nameVi']} Việt Nam",
        row["nameVi"],
    ]
    candidate_ids: list[str] = []
    for term in search_terms:
        result = request_json({
            "action": "wbsearchentities",
            "search": term,
            "language": "vi",
            "uselang": "vi",
            "type": "item",
            "limit": "10",
            "format": "json",
        })
        for candidate in result.get("search", []):
            candidate_id = candidate.get("id")
            if candidate_id and candidate_id not in candidate_ids:
                candidate_ids.append(candidate_id)
        if len(candidate_ids) >= 10:
            break
    if not candidate_ids:
        return None

    entities = request_json({
        "action": "wbgetentities",
        "ids": "|".join(candidate_ids[:20]),
        "props": "claims|labels|aliases|descriptions|info",
        "languages": "vi|en",
        "format": "json",
    }).get("entities", {})

    best: dict[str, Any] | None = None
    for rank, candidate_id in enumerate(candidate_ids[:20]):
        entity = entities.get(candidate_id, {})
        coordinate = entity_coordinate(entity)
        if not coordinate or not valid_coordinate(*coordinate):
            continue
        names = entity_names(entity)
        name_score = max((similarity(row["nameVi"], name) for name in names), default=0.0)
        descriptions = " ".join(
            entity.get("descriptions", {}).get(language, {}).get("value", "")
            for language in ("vi", "en")
        )
        context_score = 0.0
        if normalize(row["provinceName"]) in normalize(descriptions):
            context_score += 0.10
        if "viet nam" in normalize(descriptions) or "vietnam" in normalize(descriptions):
            context_score += 0.04
        score = min(1.0, name_score * 0.86 + context_score + max(0.0, 0.03 - rank * 0.003))
        candidate = {
            "sourceObjectId": row["sourceObjectId"],
            "coordinateSourceProvider": "WIKIDATA",
            "coordinateSourceObjectType": "ITEM",
            "coordinateSourceObjectId": candidate_id,
            "latitude": round(coordinate[0], 7),
            "longitude": round(coordinate[1], 7),
            "matchedName": names[0] if names else row["nameVi"],
            "matchScore": round(score, 4),
        }
        if best is None or candidate["matchScore"] > best["matchScore"]:
            best = candidate
    return best


def refresh_wikidata(force: bool, delay_seconds: float) -> None:
    source_rows = load_source_rows()
    matches = {item["sourceObjectId"]: item for item in load_json(MATCH_FILE)} if MATCH_FILE.exists() else {}
    for index, row in enumerate(source_rows, start=1):
        source_id = row["sourceObjectId"]
        if not force and matches.get(source_id, {}).get("matchScore", 0) >= 0.84:
            continue
        try:
            candidate = wikidata_match(row)
        except Exception as error:  # Network refresh preserves already cached results.
            print(f"WARN wikidata {source_id}: {error}")
            candidate = None
        if candidate and candidate["matchScore"] >= 0.68:
            matches[source_id] = candidate
        if index % 10 == 0:
            write_json(MATCH_FILE, sorted(matches.values(), key=lambda item: item["sourceObjectId"]))
            print(f"wikidata {index}/{len(source_rows)} cached={len(matches)}")
        time.sleep(max(0.0, delay_seconds))
    write_json(MATCH_FILE, sorted(matches.values(), key=lambda item: item["sourceObjectId"]))


def valid_coordinate(latitude: float, longitude: float) -> bool:
    min_latitude, max_latitude, min_longitude, max_longitude = VIETNAM_BOUNDS
    return min_latitude <= latitude <= max_latitude and min_longitude <= longitude <= max_longitude


def load_geonames(path: Path) -> list[dict[str, Any]]:
    headers = [
        "geonameid", "name", "asciiname", "alternatenames", "latitude", "longitude",
        "feature_class", "feature_code", "country_code", "cc2", "admin1_code", "admin2_code",
        "admin3_code", "admin4_code", "population", "elevation", "dem", "timezone", "modification_date",
    ]
    records = []
    with path.open("r", encoding="utf-8") as source:
        for row in csv.DictReader(source, fieldnames=headers, delimiter="\t"):
            if row["country_code"] != "VN":
                continue
            try:
                latitude = float(row["latitude"])
                longitude = float(row["longitude"])
            except ValueError:
                continue
            names = [row["name"], row["asciiname"]]
            names.extend((row["alternatenames"] or "").split(","))
            records.append({
                "id": row["geonameid"],
                "names": [name for name in names if name],
                "latitude": latitude,
                "longitude": longitude,
                "featureCode": row["feature_code"],
            })
    return records


def refresh_geonames(path: Path, force: bool) -> None:
    source_rows = load_source_rows()
    matches = {item["sourceObjectId"]: item for item in load_json(MATCH_FILE)} if MATCH_FILE.exists() else {}
    bboxes = load_json(BBOX_FILE)
    by_name, _ = province_lookup()
    records = load_geonames(path)
    records_by_province: dict[str, list[dict[str, Any]]] = {}
    for code, bbox in bboxes.items():
        min_lon, min_lat, max_lon, max_lat = bbox
        records_by_province[str(int(code))] = [
            record for record in records
            if min_lat <= record["latitude"] <= max_lat and min_lon <= record["longitude"] <= max_lon
        ]
    for index, row in enumerate(source_rows, start=1):
        source_id = row["sourceObjectId"]
        if not force and matches.get(source_id, {}).get("matchScore", 0) >= 0.84:
            continue
        province = by_name.get(normalize(row["provinceName"]))
        if not province:
            continue
        province_records = records_by_province.get(province["code"])
        if not province_records:
            continue
        best = None
        for record in province_records:
            score = max((similarity(row["nameVi"], name) for name in record["names"]), default=0.0)
            if score < 0.70:
                continue
            candidate = {
                "sourceObjectId": source_id,
                "coordinateSourceProvider": "GEONAMES",
                "coordinateSourceObjectType": record["featureCode"] or "FEATURE",
                "coordinateSourceObjectId": record["id"],
                "latitude": round(record["latitude"], 7),
                "longitude": round(record["longitude"], 7),
                "matchedName": record["names"][0],
                "matchScore": round(score * 0.93, 4),
            }
            if best is None or candidate["matchScore"] > best["matchScore"]:
                best = candidate
        if best and (force or best["matchScore"] > matches.get(source_id, {}).get("matchScore", 0)):
            matches[source_id] = best
        if index % 25 == 0:
            print(f"geonames {index}/{len(source_rows)} cached={len(matches)}")
    write_json(MATCH_FILE, sorted(matches.values(), key=lambda item: item["sourceObjectId"]))


def refresh_osm(path: Path, force: bool, nodes_only: bool) -> None:
    try:
        import osmium  # type: ignore
    except ImportError as error:
        raise RuntimeError("pyosmium is required for --osm-pbf; install the optional osmium package") from error

    rows = load_source_rows()
    matches = {item["sourceObjectId"]: item for item in load_json(MATCH_FILE)} if MATCH_FILE.exists() else {}
    by_name, _ = province_lookup()
    bboxes = load_json(BBOX_FILE)
    rows_by_id = {row["sourceObjectId"]: row for row in rows}
    province_by_id: dict[str, str] = {}
    exact_index: dict[str, set[str]] = {}
    token_index: dict[str, set[str]] = {}
    stop_tokens = {"khu", "du", "lich", "diem", "dia", "bien", "nui", "ho", "dao", "cho", "cong", "vien"}
    for row in rows:
        province = by_name.get(normalize(row["provinceName"]))
        if not province:
            continue
        source_id = row["sourceObjectId"]
        province_by_id[source_id] = province["code"]
        normalized_name = normalize(row["nameVi"])
        exact_index.setdefault(normalized_name, set()).add(source_id)
        for token in normalized_name.split():
            if len(token) >= 3 and token not in stop_tokens:
                token_index.setdefault(token, set()).add(source_id)

    relevant_keys = {"tourism", "historic", "natural", "leisure", "amenity", "man_made", "place", "boundary"}

    def object_names(tags: Any) -> list[str]:
        values = []
        for key in ("name", "name:vi", "official_name", "alt_name", "short_name"):
            value = tags.get(key)
            if value:
                values.extend(part.strip() for part in value.split(";") if part.strip())
        return values

    def consider(object_type: str, object_id: int, latitude: float, longitude: float, tags: Any) -> None:
        if not valid_coordinate(latitude, longitude) or not any(tags.get(key) for key in relevant_keys):
            return
        names = object_names(tags)
        if not names:
            return
        candidate_ids: set[str] = set()
        normalized_names = [normalize(name) for name in names]
        for normalized_name in normalized_names:
            candidate_ids.update(exact_index.get(normalized_name, set()))
            for token in normalized_name.split():
                candidate_ids.update(token_index.get(token, set()))
        for source_id in candidate_ids:
            province_code = province_by_id.get(source_id)
            bbox = bboxes.get(str(province_code).zfill(2)) if province_code else None
            if not bbox:
                continue
            min_lon, min_lat, max_lon, max_lat = bbox
            if not (min_lat <= latitude <= max_lat and min_lon <= longitude <= max_lon):
                continue
            row = rows_by_id[source_id]
            name_score = max((similarity(row["nameVi"], name) for name in names), default=0.0)
            if name_score < 0.64:
                continue
            relevance_bonus = 0.04 if tags.get("tourism") or tags.get("historic") else 0.02
            reference_bonus = 0.03 if tags.get("wikidata") or tags.get("wikipedia") else 0.0
            score = min(1.0, name_score * 0.93 + relevance_bonus + reference_bonus)
            candidate = {
                "sourceObjectId": source_id,
                "coordinateSourceProvider": "OPENSTREETMAP",
                "coordinateSourceObjectType": object_type,
                "coordinateSourceObjectId": str(object_id),
                "latitude": round(latitude, 7),
                "longitude": round(longitude, 7),
                "matchedName": names[0],
                "matchScore": round(score, 4),
            }
            current_score = float(matches.get(source_id, {}).get("matchScore", 0))
            if force or candidate["matchScore"] > current_score:
                matches[source_id] = candidate

    class LandmarkHandler(osmium.SimpleHandler):
        def node(self, node: Any) -> None:
            if (node.location.valid()
                    and any(node.tags.get(key) for key in relevant_keys)
                    and object_names(node.tags)):
                consider("NODE", node.id, node.location.lat, node.location.lon, node.tags)

        def way(self, way: Any) -> None:
            if nodes_only:
                return
            if not any(way.tags.get(key) for key in relevant_keys) or not object_names(way.tags):
                return
            locations = [node.location for node in way.nodes if node.location.valid()]
            if not locations:
                return
            latitude = sum(location.lat for location in locations) / len(locations)
            longitude = sum(location.lon for location in locations) / len(locations)
            consider("WAY", way.id, latitude, longitude, way.tags)

    handler = LandmarkHandler()
    handler.apply_file(str(path), locations=not nodes_only)
    write_json(MATCH_FILE, sorted(matches.values(), key=lambda item: item["sourceObjectId"]))
    print(json.dumps({"osmMatches": len(matches), "path": str(MATCH_FILE)}))


def category_for(row: dict[str, Any]) -> str:
    text = normalize(" ".join([row["nameVi"], row.get("keywords", ""), row.get("descriptionVi", "")]))
    rules = [
        ("BEACH", ("bien", "bai tam", "dao", "vinh", "hon ")),
        ("RELIGIOUS", ("chua", "nha tho", "den ", "thanh duong", "thien vien")),
        ("HISTORIC", ("di tich", "lich su", "thanh co", "bao tang", "lang ", "nha tu")),
        ("ENTERTAINMENT", ("cong vien", "khu vui choi", "vinwonders", "pho di bo", "cho dem")),
        ("NATURE", ("vuon quoc gia", "thac", "nui", "hang", "dong", "ho ", "suoi", "rung", "cao nguyen")),
        ("CULTURE", ("lang nghe", "van hoa", "cho ", "cau ", "thap", "dinh ")),
    ]
    for category, keywords in rules:
        if any(keyword in f"{text} " for keyword in keywords):
            return category
    return "OTHER"


def rating_score(value: str) -> int:
    match = re.search(r"([0-5](?:[.,]\d+)?)", value or "")
    rating = float(match.group(1).replace(",", ".")) if match else 4.0
    return max(0, min(100, round(rating * 20)))


def haversine_km(left: dict[str, Any], right: dict[str, Any]) -> float:
    radius = 6371.0088
    lat1, lon1 = math.radians(left["latitude"]), math.radians(left["longitude"])
    lat2, lon2 = math.radians(right["latitude"]), math.radians(right["longitude"])
    delta_latitude = lat2 - lat1
    delta_longitude = lon2 - lon1
    value = math.sin(delta_latitude / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(delta_longitude / 2) ** 2
    return radius * 2 * math.atan2(math.sqrt(value), math.sqrt(1 - value))


def build(min_match_score: float, min_per_province: int) -> tuple[list[dict[str, Any]], dict[str, Any], list[dict[str, Any]]]:
    rows = load_source_rows()
    matches = coordinate_matches_with_overrides(rows)
    by_name, _ = province_lookup()
    current_by_code, current_by_legacy_code = current_province_lookup()
    bboxes = load_json(BBOX_FILE)
    manual_landmarks = load_json(MANUAL_FILE) if MANUAL_FILE.exists() else []
    landmarks = canonicalize_manual_landmarks(manual_landmarks, current_by_code, current_by_legacy_code)
    quarantine: list[dict[str, Any]] = []

    for row in rows:
        source_id = row["sourceObjectId"]
        province = by_name.get(normalize(row["provinceName"]))
        match = matches.get(source_id)
        if not province:
            quarantine.append({"sourceObjectId": source_id, "reason": "UNRESOLVED_PROVINCE", "row": row})
            continue
        provider = match.get("coordinateSourceProvider") if match else None
        provider_threshold = max(min_match_score, 0.92) if provider == "GEONAMES" else max(min_match_score, 0.84)
        if not match or float(match.get("matchScore", 0)) < provider_threshold:
            quarantine.append({
                "sourceObjectId": source_id,
                "reason": "MISSING_OR_LOW_CONFIDENCE_COORDINATE",
                "match": match,
                "row": row,
            })
            continue
        latitude = float(match["latitude"])
        longitude = float(match["longitude"])
        if not valid_coordinate(latitude, longitude):
            quarantine.append({"sourceObjectId": source_id, "reason": "INVALID_COORDINATE", "match": match, "row": row})
            continue
        bbox = bboxes.get(str(province["code"]).zfill(2))
        if not bbox:
            quarantine.append({"sourceObjectId": source_id, "reason": "MISSING_PROVINCE_BBOX", "match": match, "row": row})
            continue
        min_lon, min_lat, max_lon, max_lat = bbox
        if not (min_lat <= latitude <= max_lat and min_lon <= longitude <= max_lon):
            quarantine.append({
                "sourceObjectId": source_id,
                "reason": "COORDINATE_OUTSIDE_PROVINCE",
                "match": match,
                "row": row,
            })
            continue

        score = rating_score(row.get("rating", ""))
        category = category_for(row)
        current_province = current_by_legacy_code.get(province["code"])
        if current_province is None:
            quarantine.append({"sourceObjectId": source_id, "reason": "UNRESOLVED_CURRENT_PROVINCE", "row": row})
            continue
        landmark = {
            "code": f"LM-VN-{row['sourceRow']:03d}",
            "nameVi": row["nameVi"],
            "nameEn": None,
            "provinceCode": current_province["sourceCode"],
            "latitude": latitude,
            "longitude": longitude,
            "category": category,
            "defaultRadiusKm": 12 if category in {"NATURE", "BEACH"} else 7,
            "popularityScore": score,
            "descriptionVi": row.get("descriptionVi") or None,
            "descriptionEn": None,
            "status": "ACTIVE",
            "sourceProvider": "CURATED_VN_TRAVEL",
            "sourceObjectType": "DATASET_ROW",
            "sourceObjectId": source_id,
            "sourceUpdatedAt": "2026-07-29T00:00:00",
            "dataQualityStatus": "VERIFIED" if match["matchScore"] >= 0.90 else "MATCHED",
            "manualOverride": False,
            "coordinateSourceProvider": match["coordinateSourceProvider"],
            "coordinateSourceObjectType": match["coordinateSourceObjectType"],
            "coordinateSourceObjectId": match["coordinateSourceObjectId"],
            "coordinateMatchScore": match["matchScore"],
        }
        duplicate = next((existing for existing in landmarks
                          if similarity(existing["nameVi"], landmark["nameVi"]) >= 0.94
                          and haversine_km(existing, landmark) <= 0.75), None)
        if duplicate:
            quarantine.append({
                "sourceObjectId": source_id,
                "reason": "NEAR_DUPLICATE",
                "duplicateOf": duplicate["sourceObjectId"],
                "row": row,
                "match": match,
            })
            continue
        landmarks.append(landmark)

    landmarks.sort(key=lambda item: (
        int(current_by_code[item["provinceCode"]]["officialCode"]),
        -item["popularityScore"], normalize(item["nameVi"]), item["code"],
    ))
    quarantine.sort(key=lambda item: item["sourceObjectId"])
    coverage_counts = Counter(item["provinceCode"] for item in landmarks if item["status"] == "ACTIVE")
    coverage = {
        "sourceRows": len(rows),
        "generatedLandmarks": len(landmarks),
        "quarantined": len(quarantine),
        "administrativeBaseline": "CURRENT_34",
        "currentProvinceSourceSha256": sha256(CURRENT_PROVINCE_FILE),
        "supportedProvinceCount": len(current_by_code),
        "coveredProvinceCount": sum(1 for code in current_by_code if coverage_counts[code] > 0),
        "minimumPerProvince": min_per_province,
        "editorialTargetPerProvince": 3,
        "provinces": [
            {
                "code": code,
                "officialCode": current_by_code[code]["officialCode"],
                "name": current_by_code[code]["name"],
                "legacyProvinceCodes": current_by_code[code]["legacyProvinceCodes"],
                "landmarkCount": coverage_counts[code],
                "meetsMinimum": coverage_counts[code] >= min_per_province,
                "meetsEditorialTarget": coverage_counts[code] >= 3,
            }
            for code in sorted(current_by_code, key=lambda item: int(current_by_code[item]["officialCode"]))
        ],
        "qualityStatusCounts": dict(sorted(Counter(item["dataQualityStatus"] for item in landmarks).items())),
        "coordinateProviderCounts": dict(sorted(Counter(
            item.get("coordinateSourceProvider", item["sourceProvider"]) for item in landmarks
        ).items())),
    }
    return landmarks, coverage, quarantine


def validate(landmarks: list[dict[str, Any]], coverage: dict[str, Any], min_per_province: int) -> list[str]:
    errors = []
    codes = [item["code"] for item in landmarks]
    source_keys = [(item["sourceProvider"], item["sourceObjectType"], item["sourceObjectId"]) for item in landmarks]
    if len(codes) != len(set(codes)):
        errors.append("Duplicate landmark code detected")
    if len(source_keys) != len(set(source_keys)):
        errors.append("Duplicate source natural key detected")
    for item in landmarks:
        if item["status"] == "ACTIVE" and not valid_coordinate(float(item["latitude"]), float(item["longitude"])):
            errors.append(f"Invalid active coordinate: {item['code']}")
    supported_codes = {item["code"] for item in coverage["provinces"]}
    unknown_codes = sorted({item["provinceCode"] for item in landmarks} - supported_codes)
    if unknown_codes:
        errors.append("Unknown current province codes: " + ", ".join(unknown_codes))
    deficient = [item for item in coverage["provinces"] if item["landmarkCount"] < min_per_province]
    if deficient:
        errors.append("Province coverage below minimum: " + ", ".join(f"{item['code']}={item['landmarkCount']}" for item in deficient))
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bootstrap-source", type=Path, help="Normalize the audited spreadsheet JSON export")
    parser.add_argument("--refresh-wikidata", action="store_true", help="Refresh cached Wikidata coordinate matches")
    parser.add_argument("--wikidata-force", action="store_true", help="Replace existing high-confidence Wikidata matches")
    parser.add_argument("--wikidata-delay", type=float, default=0.15)
    parser.add_argument("--geonames-file", type=Path, help="Merge coordinate matches from extracted GeoNames VN.txt")
    parser.add_argument("--geonames-force", action="store_true")
    parser.add_argument("--download-osm-pbf", type=str, help="Download a large PBF with resumable HTTP ranges")
    parser.add_argument("--osm-output", type=Path)
    parser.add_argument("--osm-workers", type=int, default=4)
    parser.add_argument("--osm-pbf", type=Path, help="Merge coordinate matches from an offline OSM PBF")
    parser.add_argument("--osm-force", action="store_true")
    parser.add_argument("--osm-nodes-only", action="store_true", help="Skip ways to avoid building the node-location index")
    parser.add_argument("--write", action="store_true", help="Write fixture and reports")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--validate-only", action="store_true")
    parser.add_argument("--min-match-score", type=float, default=0.78)
    parser.add_argument("--min-per-province", type=int, default=1,
                        help="Release breadth gate; use 3 to enforce the editorial depth target")
    args = parser.parse_args()

    if args.bootstrap_source:
        bootstrap_source(args.bootstrap_source)
    if args.refresh_wikidata:
        refresh_wikidata(args.wikidata_force, args.wikidata_delay)
    if args.geonames_file:
        refresh_geonames(args.geonames_file, args.geonames_force)
    if args.download_osm_pbf:
        if not args.osm_output:
            parser.error("--osm-output is required with --download-osm-pbf")
        download_range_file(args.download_osm_pbf, args.osm_output, args.osm_workers)
    if args.osm_pbf:
        refresh_osm(args.osm_pbf, args.osm_force, args.osm_nodes_only)

    if args.validate_only:
        landmarks = load_json(LANDMARK_FILE)
        coverage = load_json(COVERAGE_FILE)
        errors = validate(landmarks, coverage, args.min_per_province)
    else:
        landmarks, coverage, quarantine = build(args.min_match_score, args.min_per_province)
        errors = validate(landmarks, coverage, args.min_per_province)
        if args.write:
            write_json(LANDMARK_FILE, landmarks)
            write_json(COVERAGE_FILE, coverage)
            write_json(QUARANTINE_FILE, quarantine)
        print(json.dumps({
            "generated": len(landmarks),
            "quarantined": len(quarantine),
            "coveredProvinces": coverage["coveredProvinceCount"],
            "supportedProvinces": coverage["supportedProvinceCount"],
            "write": args.write,
        }, ensure_ascii=False))

    if errors:
        for error in errors:
            print(f"ERROR {error}")
        return 1
    if args.write:
        print(json.dumps({
            "landmarkSha256": sha256(LANDMARK_FILE),
            "coverageSha256": sha256(COVERAGE_FILE),
            "quarantineSha256": sha256(QUARANTINE_FILE),
        }))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
