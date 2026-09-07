import json
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPT_DIR))

from validate_kemi_sbom import SbomValidationError, validate_sbom  # noqa: E402


class ValidateKemiSbomTest(unittest.TestCase):
    def setUp(self):
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.sbom_path = Path(self.temporary_directory.name) / "kemi-release.cdx.json"

    def write_sbom(self, overrides=None):
        document = {
            "bomFormat": "CycloneDX",
            "specVersion": "1.7",
            "version": 1,
            "metadata": {
                "component": {
                    "type": "application",
                    "bom-ref": "pkg:generic/kemi-mail@20.1",
                    "name": "KEMI Mail",
                    "version": "20.1",
                },
            },
            "components": [
                {
                    "type": "library",
                    "bom-ref": "pkg:maven/com.example/mail-core@1.0",
                    "name": "mail-core",
                    "version": "1.0",
                },
            ],
            "dependencies": [
                {
                    "ref": "pkg:generic/kemi-mail@20.1",
                    "dependsOn": ["pkg:maven/com.example/mail-core@1.0"],
                },
                {
                    "ref": "pkg:maven/com.example/mail-core@1.0",
                    "dependsOn": [],
                },
            ],
        }
        if overrides:
            overrides(document)
        self.sbom_path.write_text(json.dumps(document), encoding="utf-8")

    def test_accepts_release_runtime_sbom(self):
        self.write_sbom()

        summary = validate_sbom(self.sbom_path, version="20.1")

        self.assertEqual(summary.component_count, 1)
        self.assertEqual(summary.dependency_edge_count, 1)

    def test_rejects_absolute_local_path(self):
        self.write_sbom(lambda document: document["metadata"].update({"workspace": "E:\\email\\android-client"}))

        with self.assertRaisesRegex(SbomValidationError, "本机绝对路径"):
            validate_sbom(self.sbom_path, version="20.1")

    def test_rejects_sensitive_key(self):
        self.write_sbom(lambda document: document["metadata"].update({"api_token": "not-allowed"}))

        with self.assertRaisesRegex(SbomValidationError, "敏感字段"):
            validate_sbom(self.sbom_path, version="20.1")

    def test_rejects_broken_dependency_reference(self):
        def add_broken_reference(document):
            document["dependencies"][0]["dependsOn"].append("pkg:maven/com.example/missing@1.0")

        self.write_sbom(add_broken_reference)

        with self.assertRaisesRegex(SbomValidationError, "未知组件"):
            validate_sbom(self.sbom_path, version="20.1")

    def test_rejects_wrong_product_metadata(self):
        self.write_sbom(lambda document: document["metadata"]["component"].update({"name": "Other Mail"}))

        with self.assertRaisesRegex(SbomValidationError, "根组件名称"):
            validate_sbom(self.sbom_path, version="20.1")

    def test_rejects_serial_number_to_keep_output_stable(self):
        self.write_sbom(lambda document: document.update({"serialNumber": "urn:uuid:random"}))

        with self.assertRaisesRegex(SbomValidationError, "serialNumber"):
            validate_sbom(self.sbom_path, version="20.1")


if __name__ == "__main__":
    unittest.main()
