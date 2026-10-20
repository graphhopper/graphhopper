"""Standard-library acceptance/rejection checks for the Maven evidence freezer."""
from datetime import datetime, timezone
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("freezer", Path(__file__).with_name("snapshot-matrix-verification.py"))
FREEZER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(FREEZER)


class VerificationEvidenceGuards(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        (self.root / "pom.xml").write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><artifactId>parent</artifactId><modules><module>core</module></modules></project>')
        (self.root / "core").mkdir()
        (self.root / "core/pom.xml").write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><artifactId>core</artifactId></project>')
        self.report = self.root / "core/target/surefire-reports/TEST-demo.xml"
        self.report.parent.mkdir(parents=True)
        self.xml = '<testsuite name="demo" tests="2" failures="0" errors="0" skipped="1"><testcase name="passed"/><testcase name="conditional"><skipped type="condition" message="opt-in disabled"/></testcase></testsuite>'
        self.report.write_text(self.xml)
        self.finished = datetime.now(timezone.utc)
        self.log = ('[INFO] --- failsafe:2.22.2:integration-test (default) @ parent ---\n'
                    '[INFO] --- surefire:2.22.2:test (default-test) @ core ---\n'
                    '[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 1, Time elapsed: 0.1 s - in demo\n'
                    '[INFO] parent ........ SUCCESS [ 0.1 s]\n[INFO] core ........ SUCCESS [ 0.1 s]\n'
                    '[INFO] BUILD SUCCESS\n[INFO] Total time: 5.000 s\n'
                    '[INFO] Finished at: ' + self.finished.isoformat() + '\n').encode()
        self.modules = FREEZER.module_inventory(self.root)

    def tearDown(self):
        self.temporary.cleanup()

    def collect(self, log=None):
        run, logged = FREEZER.logged_run(self.log if log is None else log, self.modules)
        return FREEZER.collect_reports(self.root, self.modules, run, logged)

    def test_matching_reports_count_actual_conditional_skip(self):
        _, suites, totals = self.collect()
        self.assertEqual(dict(tests=2, failures=0, errors=0, skipped=1, passed=1), totals)
        self.assertEqual(["PASS", "SKIPPED"], [case["status"] for case in suites[0]["testcases"]])

    def test_warning_level_summary_with_skips_is_accepted(self):
        _, _, totals = self.collect(self.log.replace(b"[INFO] Tests run:", b"[WARNING] Tests run:"))
        self.assertEqual(1, totals["skipped"])

    def test_full_width_reactor_name_without_padding_is_accepted(self):
        self.collect(self.log.replace(b"[INFO] core ........ SUCCESS", b"[INFO] A full-width human project name. SUCCESS"))

    def test_old_xml_is_rejected(self):
        old = self.finished.timestamp() - 60
        os.utime(self.report, (old, old))
        with self.assertRaisesRegex(ValueError, "Stale or later-overwritten"):
            self.collect()

    def test_later_focused_xml_is_rejected(self):
        future = self.finished.timestamp() + 60
        os.utime(self.report, (future, future))
        with self.assertRaisesRegex(ValueError, "Stale or later-overwritten"):
            self.collect()

    def test_suite_count_mismatch_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "counts disagree"):
            self.collect(self.log.replace(b"Tests run: 2,", b"Tests run: 3,"))

    def test_actual_testcase_outcome_mismatch_is_rejected(self):
        self.report.write_text(self.xml.replace('skipped="1"', 'skipped="0"'))
        with self.assertRaisesRegex(ValueError, "outcomes disagree"):
            self.collect(self.log.replace(b"Skipped: 1,", b"Skipped: 0,"))

    def test_incomplete_build_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "completed successful"):
            self.collect(self.log.replace(b"[INFO] BUILD SUCCESS\n", b""))

    def test_partial_reactor_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "every declared module"):
            self.collect(self.log.replace(b"[INFO] core ........ SUCCESS [ 0.1 s]\n", b""))

    def test_stale_extra_suite_is_rejected(self):
        self.report.with_name("TEST-old.xml").write_text(self.xml.replace('name="demo"', 'name="old"'))
        with self.assertRaisesRegex(ValueError, "absent from this exact Maven log"):
            self.collect()

    def test_missing_current_xml_is_rejected(self):
        self.report.unlink()
        with self.assertRaisesRegex(ValueError, "without corresponding current XML"):
            self.collect()


if __name__ == "__main__":
    unittest.main(verbosity=2)
