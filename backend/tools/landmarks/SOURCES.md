# Nationwide Landmark Data Sources

The generated fixture is a derived catalog. Do not edit generated coordinates or provenance fields directly; use the source cache, versioned correction/override files and rebuild it.

## Editorial Source

- Repository: `https://github.com/HongTin2104/VietNam-Travel-Recommendation-System`
- File: `data/DataSet.xlsx`
- License: MIT (`LICENSE` in the upstream repository)
- Snapshot used: commit `21c1d09eaec50ce4dfe7d684ba7a88b6f7d215cf` (2025-05-02), inspected 2026-07-29
- Coverage: 315 rows, five tourist attractions for each of the 63 legacy province names after correcting `Quang Ninh` and `Nam Đinh` spelling variants
- Limitations: no coordinates; descriptions, ratings, image URLs and keywords require editorial review before prominent publication

The normalized copy is `sources/vietnam_travel_63.json` (SHA-256 `bb9fac65bfeaceeb4fad4d38bc0bc51dcb4f9ea034671370afe7739d05e7be81`). Upstream image URLs are retained for audit only and are not imported as application media.

## Coordinate Sources

- Wikidata API: `https://www.wikidata.org/w/api.php`, cached by QID and match score in `sources/coordinate_matches.json`; Wikidata structured data is CC0.
- GeoNames Vietnam dump: `https://download.geonames.org/export/dump/VN.zip`, CC BY 4.0. Used only as a fallback for stable ids/coordinates.
- OpenStreetMap verification: selected missing source rows were checked through Overpass/Photon on 2026-07-29 and pinned by OSM object type/id in `config/coordinate_overrides.json`. These are reviewed overrides, not an unversioned runtime geocoder dependency. OpenStreetMap data is ODbL and requires OpenStreetMap contributor attribution.
- Curated corrections: `config/coordinate_overrides.json` contains 31 reviewed source-row coordinate overrides; `config/source_row_corrections.json` fixes six obvious display-name/casing errors without modifying the audited raw source. Their SHA-256 values are `77c2105d27889f80041db3370c92d6a33a38ab11d9aebc83f1f8c89ba32525a6` and `45f4683655402a6c5265e5d4acdd51beac2b22e392d5e88cb6ae89c4a0fa8673`.
- Preferred future refresh: Geofabrik Vietnam OpenStreetMap PBF, `https://download.geofabrik.de/asia/vietnam-latest.osm.pbf`, ODbL. The attempted 2026-07-28 snapshot was 325,750,725 bytes with SHA-256 `0eabb81d3f3417552bbd5dd46418510155e59f9dcfab346465499ac9335467a9`; full local parsing remains a future optimization. Public Nominatim is not used for bulk catalog generation.

## Administrative Compatibility Source

- Legacy hierarchy: `https://provinces.open-api.vn/api/?depth=3`, downloaded 2026-07-29. The packaged 63-province file SHA-256 is `87c64c32262f3d0ade57954668cdf1771c7718ca2c38cdcb3250c246b3f65c9b`.
- Current province list: `https://provinces.open-api.vn/api/v2/p/`, downloaded 2026-07-29. It returns 34 current provinces/cities; upstream response SHA-256 `ad03b2f3a40bd652d7f2dd63564cc49817f943380560ede8164239ec3c32c0f0`.
- Current ward reference: `https://provinces.open-api.vn/api/v2/?depth=2`, inspected 2026-07-29. It returns 34 provinces and 3,321 wards; upstream response SHA-256 `e997eb9dcbbbafbbae00496965572f8ccd6e192cbb1024e8fe6b5513c162dc9b`.
- Legal baseline: Resolution `202/2025/QH15` and Decision `19/2025/QD-TTg`. The packaged `data/provinces-current-34.json` assigns independent `VN34-*` application identities and lists every legacy member.
- Legacy province bboxes: `daohoangson/dvhcvn` commit `4b1b6b59c5880c620722663bcb48c5c105364be5`; packaged bbox SHA-256 `4adf4457bd2e0ed5f36ecca1ce7d2a8595188dcb1d3c56745ba83609b86e2283`.
- The application persists legacy numeric province/ward source codes for existing hotels. Legacy rows remain compatibility data and are not returned as the public province catalog.
- Numeric codes that changed meaning after consolidation are never renamed in place. Current rows use `VN34-*`; search expands a current province to all mapped legacy database ids.

## Rebuild

```powershell
Set-Location backend
python tools/landmarks/build_vietnam_landmarks.py --dry-run
python tools/landmarks/build_vietnam_landmarks.py --write --min-per-province 3
python tools/landmarks/build_vietnam_landmarks.py --validate-only --min-per-province 3
```

Coordinate refresh is explicit and networked; normal builds consume the checked-in cache:

```powershell
python tools/landmarks/build_vietnam_landmarks.py --refresh-wikidata --write
python tools/landmarks/build_vietnam_landmarks.py --geonames-file C:\path\to\VN.txt --write
```

Current release artifact (2026-07-29): 122 landmarks, 207 quarantined candidates, 34/34 current provinces and a minimum of three active coordinate-valid landmarks per province. The generated landmark, coverage and quarantine SHA-256 values are `54092b3fcb7f4d4f0583165baafa41d31c63e8e6289da243640ccf537da2b078`, `656330578dd5c7298f2a3c32d079f10676316950705769b8cc14b0b9f7814a6c` and `bfeb78d8300ebf09493010cce47d35d2349ceaca5166820ad524dab33498118f`.
