import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "packaging"))
from nightly_release import assemble, nightly_version


class NightlyReleaseTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.source = self.root / "artifacts"
        self.destination = self.root / "release"
        self.version = "1.0.0-nightly.123.2"
        self.commit = "a" * 40
        self.packages = []
        for arch in ("x64", "arm64"):
            for platform, extension in (("Windows", "zip"), ("Linux", "AppImage"), ("Linux", "deb")):
                package = self.source / f"{platform}-{arch}" / f"OpenNOW-Qt-{self.version}-{platform}-{arch}.{extension}"
                package.parent.mkdir(parents=True, exist_ok=True)
                package.write_bytes(f"test fixture {platform} {arch}".encode())
                self.packages.append(package)

    def collect(self):
        assemble(self.source, self.destination, self.version, self.commit)

    def test_complete_inventory_and_checksums(self):
        self.collect()
        metadata = json.loads((self.destination / "RELEASE-INFO.json").read_text())
        self.assertEqual(metadata["sourceCommit"], self.commit)
        self.assertEqual(metadata["version"], self.version)
        self.assertEqual(metadata["updates"], "manual-download")
        self.assertEqual(len(metadata["assets"]), 6)
        sums = (self.destination / "SHA256SUMS").read_text().splitlines()
        self.assertEqual(len(sums), 7)
        for line in sums:
            digest, name = line.split("  ")
            self.assertEqual(digest, hashlib.sha256((self.destination / name).read_bytes()).hexdigest())

    def test_missing_architecture_is_rejected(self):
        self.packages[0].unlink()
        with self.assertRaisesRegex(ValueError, "Missing release artifacts"):
            self.collect()
        self.assertFalse(self.destination.exists())

    def test_duplicate_names_are_rejected_before_flattening(self):
        (self.source / self.packages[0].name).write_bytes(b"duplicate")
        with self.assertRaisesRegex(ValueError, "duplicate"):
            self.collect()

    def test_wrong_version_and_empty_artifacts_are_rejected(self):
        self.packages[0].write_bytes(b"")
        with self.assertRaisesRegex(ValueError, "empty artifact"):
            self.collect()
        self.packages[0].rename(self.packages[0].with_name("wrong-version.zip"))
        with self.assertRaisesRegex(ValueError, "Unexpected"):
            self.collect()

    def test_mutable_commit_and_invalid_version_are_rejected(self):
        self.commit = "dev"
        with self.assertRaisesRegex(ValueError, "immutable"):
            self.collect()
        self.commit = "a" * 40
        self.version = "1.0.0-nightly.01.1"
        with self.assertRaisesRegex(ValueError, "Invalid nightly"):
            self.collect()

    def test_version_comes_from_project_and_run_identity(self):
        project = self.root / "CMakeLists.txt"
        project.write_text("project(OpenNOWQt VERSION 1.0.0 LANGUAGES CXX)")
        self.assertEqual(nightly_version(project, 123, 2), self.version)
        with self.assertRaises(ValueError):
            nightly_version(project, 0, 1)


if __name__ == "__main__":
    unittest.main()
