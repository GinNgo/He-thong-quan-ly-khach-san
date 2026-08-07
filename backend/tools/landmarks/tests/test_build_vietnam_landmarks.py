import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parents[1] / "build_vietnam_landmarks.py"
SPEC = importlib.util.spec_from_file_location("build_vietnam_landmarks", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class LandmarkGeneratorTest(unittest.TestCase):

    def test_normalize_handles_vietnamese_and_administrative_prefixes(self):
        self.assertEqual("ba ria vung tau", MODULE.normalize("Tỉnh Bà Rịa - Vũng Tàu"))
        self.assertEqual("ho chi minh", MODULE.normalize("TP. Hồ Chí Minh"))

    def test_category_mapping_is_deterministic(self):
        row = {"nameVi": "Vườn quốc gia", "keywords": "rừng, tham quan", "descriptionVi": "Thiên nhiên"}
        self.assertEqual("NATURE", MODULE.category_for(row))

    def test_write_json_is_utf8_and_byte_stable(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.json"
            value = [{"nameVi": "Hồ Hoàn Kiếm", "provinceCode": "VN34-01"}]
            MODULE.write_json(path, value)
            first = path.read_bytes()
            MODULE.write_json(path, value)
            self.assertEqual(first, path.read_bytes())
            self.assertEqual(value, json.loads(first.decode("utf-8")))

    def test_validate_rejects_duplicate_source_keys(self):
        landmark = {
            "code": "LM-1",
            "sourceProvider": "SOURCE",
            "sourceObjectType": "ROW",
            "sourceObjectId": "1",
            "provinceCode": "VN34-01",
            "status": "ACTIVE",
            "latitude": 10.0,
            "longitude": 106.0,
        }
        coverage = {"provinces": [{"code": "VN34-01", "landmarkCount": 3}]}
        errors = MODULE.validate([landmark, {**landmark, "code": "LM-2"}], coverage, 3)
        self.assertIn("Duplicate source natural key detected", errors)

    def test_validate_rejects_invalid_active_coordinate(self):
        landmark = {
            "code": "LM-1",
            "sourceProvider": "SOURCE",
            "sourceObjectType": "ROW",
            "sourceObjectId": "1",
            "provinceCode": "VN34-01",
            "status": "ACTIVE",
            "latitude": 0.0,
            "longitude": 0.0,
        }
        coverage = {"provinces": [{"code": "VN34-01", "landmarkCount": 3}]}
        errors = MODULE.validate([landmark], coverage, 3)
        self.assertIn("Invalid active coordinate: LM-1", errors)

    def test_packaged_sources_cover_legacy_administrative_baseline(self):
        source_rows = MODULE.load_json(MODULE.SOURCE_FILE)
        provinces = MODULE.load_json(MODULE.LOCATION_FILE)
        current_provinces = MODULE.load_json(MODULE.CURRENT_PROVINCE_FILE)
        self.assertEqual(315, len(source_rows))
        self.assertEqual(63, len(provinces))
        self.assertEqual(34, len(current_provinces))
        self.assertEqual(315, len({row["sourceObjectId"] for row in source_rows}))

        _, by_legacy_code = MODULE.current_province_lookup()
        self.assertEqual({str(province["code"]) for province in provinces}, set(by_legacy_code))

    def test_current_codes_do_not_repurpose_legacy_numeric_codes(self):
        by_current_code, by_legacy_code = MODULE.current_province_lookup()
        self.assertEqual("Tỉnh Gia Lai", by_current_code["VN34-52"]["name"])
        self.assertEqual("VN34-52", by_legacy_code["52"]["sourceCode"])
        self.assertEqual("VN34-52", by_legacy_code["64"]["sourceCode"])

    def test_curated_coordinate_overrides_are_unique_and_replace_cached_matches(self):
        source_rows = MODULE.load_source_rows()
        overrides = MODULE.load_json(MODULE.COORDINATE_OVERRIDE_FILE)
        override_ids = [item["sourceObjectId"] for item in overrides]
        self.assertEqual(len(override_ids), len(set(override_ids)))

        matches = MODULE.coordinate_matches_with_overrides(source_rows)
        for override in overrides:
            self.assertEqual(override, matches[override["sourceObjectId"]])

    def test_source_row_corrections_are_applied_without_rewriting_raw_source(self):
        raw_rows = {row["sourceObjectId"]: row for row in MODULE.load_json(MODULE.SOURCE_FILE)}
        corrected_rows = {row["sourceObjectId"]: row for row in MODULE.load_source_rows()}
        self.assertEqual("Vường quốc gia Bến En", raw_rows["VNTRAVEL-124"]["nameVi"])
        self.assertEqual("Vườn quốc gia Bến En", corrected_rows["VNTRAVEL-124"]["nameVi"])

    def test_generated_reports_reconcile_without_duplicate_keys(self):
        landmarks = MODULE.load_json(MODULE.LANDMARK_FILE)
        coverage = MODULE.load_json(MODULE.COVERAGE_FILE)
        quarantine = MODULE.load_json(MODULE.QUARANTINE_FILE)
        source_rows = MODULE.load_json(MODULE.SOURCE_FILE)
        manual_landmarks = MODULE.load_json(MODULE.MANUAL_FILE)
        self.assertEqual("CURRENT_34", coverage["administrativeBaseline"])
        self.assertEqual(34, coverage["supportedProvinceCount"])
        self.assertEqual(coverage["generatedLandmarks"], len(landmarks))
        self.assertEqual(coverage["quarantined"], len(quarantine))
        self.assertEqual(len(source_rows) + len(manual_landmarks), len(landmarks) + len(quarantine))
        self.assertTrue(all(item["provinceCode"].startswith("VN34-") for item in landmarks))
        self.assertTrue(all(item["meetsEditorialTarget"] for item in coverage["provinces"]))
        self.assertEqual([], MODULE.validate(landmarks, coverage, 3))


if __name__ == "__main__":
    unittest.main()
