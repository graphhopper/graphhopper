"""Small standard-library tests for the actual GraphHopper gitinfo resource format."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


SMOKE = load("smoke_release", "smoke-ch-matrix-release.py")
RECORD = load("record_release", "record-ch-matrix-release.py")
COMMIT = "a" * 40


class GraphHopperReleaseIdentityTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.jar = Path(self.temporary.name) / "small.jar"

    def tearDown(self):
        self.temporary.cleanup()

    def write_jar(self, dirty="false", message="Source commit", newline="\r\n", version="11.0-SNAPSHOT", extra=""):
        gitinfo = newline.join(["# filtered git identity", COMMIT, "2026-10-19T23:00:00+0100", message, "feature/local-matrix", dirty]) + extra
        with zipfile.ZipFile(self.jar, "w") as archive:
            archive.writestr("com/graphhopper/gitinfo", gitinfo.encode("utf-8"))
            archive.writestr("com/graphhopper/version", version.encode("utf-8"))
            archive.writestr("com/graphhopper/builddate", b"2026-10-19T22:00:00Z")

    def test_actual_gitinfo_crlf_format_is_authoritative_without_git_properties(self):
        self.write_jar()
        identity = SMOKE.artifact_identity(self.jar, COMMIT)
        self.assertEqual(COMMIT, identity["graphhopper_gitinfo"]["commit"])
        self.assertEqual("false", identity["graphhopper_gitinfo"]["dirty"])
        self.assertEqual("11.0-SNAPSHOT", identity["version"])
        self.assertEqual(3, len(identity["resource_sha256"]))

    def test_lf_crlf_cr_and_final_terminator_match_java_reader(self):
        for newline in ("\n", "\r\n", "\r"):
            with self.subTest(newline=repr(newline)):
                raw = newline.join(["comment", COMMIT, "time", "", "branch", "false"]).encode()
                for reader in (SMOKE.java_lines, RECORD.java_lines):
                    self.assertEqual(["comment", COMMIT, "time", "", "branch", "false"], reader(raw))
                    self.assertEqual(reader(raw), reader(raw + newline.encode()))

    def test_empty_commit_message_remains_a_valid_positional_field(self):
        self.write_jar(message="")
        self.assertEqual("", SMOKE.artifact_identity(self.jar, COMMIT)["graphhopper_gitinfo"]["commit_message"])

    def test_dirty_build_is_rejected(self):
        self.write_jar(dirty="true")
        with self.assertRaisesRegex(ValueError, "clean committed"):
            SMOKE.artifact_identity(self.jar, COMMIT)

    def test_different_commit_is_rejected(self):
        self.write_jar()
        with self.assertRaisesRegex(ValueError, "complete source commit"):
            SMOKE.artifact_identity(self.jar, "b" * 40)

    def test_unfiltered_version_is_rejected(self):
        self.write_jar(version="${project.version}")
        with self.assertRaisesRegex(ValueError, "Unfiltered"):
            SMOKE.artifact_identity(self.jar, COMMIT)

    def test_extra_interior_lines_are_rejected_as_constants_would(self):
        self.write_jar(extra="\r\n\r\n")
        with self.assertRaisesRegex(ValueError, "five positional fields"):
            SMOKE.artifact_identity(self.jar, COMMIT)


if __name__ == "__main__":
    unittest.main(verbosity=2)
