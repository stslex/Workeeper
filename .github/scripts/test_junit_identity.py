import os
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from junit_identity import ModuleError, check_module, run_gate


class JunitIdentityTest(unittest.TestCase):
    def setUp(self):
        temporary = TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        self.results = root / "results"
        self.results.mkdir()
        self.marker = root / "start"
        self.marker.touch()
        os.utime(self.marker, ns=(2_000_000_000, 2_000_000_000))
        self.expected = {
            "module": "test module",
            "results_dir": self.results,
            "identities": [{"classname": "example.Test", "name": "required"}],
            "classname_prefix": "nativeTask.",
            "freshness_marker": self.marker,
        }

    def write_suite(self, cases, *, count, skipped=0, failures=0, filename="TEST-example.xml"):
        path = self.results / filename
        path.write_text(
            f'<testsuite tests="{count}" skipped="{skipped}" failures="{failures}" errors="0">'
            f'{cases}</testsuite>',
            encoding="utf-8",
        )
        os.utime(path, ns=(3_000_000_000, 3_000_000_000))
        return path

    def required_case(self, status=""):
        return f'<testcase classname="nativeTask.example.Test" name="required">{status}</testcase>'

    def test_complete_suite_allows_an_additional_passing_identity(self):
        self.write_suite(
            self.required_case() + '<testcase classname="example.Test" name="extra"/>', count=2,
        )
        self.assertIn("2 executed / 0 skipped", check_module(self.expected))

    def test_missing_result_directory_and_xml(self):
        with self.assertRaisesRegex(ModuleError, "no TEST-"):
            check_module(self.expected)
        self.results.rmdir()
        with self.assertRaisesRegex(ModuleError, "does not exist"):
            check_module(self.expected)

    def test_empty_suite(self):
        self.write_suite("", count=0)
        with self.assertRaisesRegex(ModuleError, "no <testcase>"):
            check_module(self.expected)

    def test_wrong_class_and_missing_identity(self):
        self.write_suite('<testcase classname="foreign.example.Test" name="required"/>', count=1)
        with self.assertRaisesRegex(ModuleError, "found 0"):
            check_module(self.expected)

    def test_duplicate_identity_across_suites(self):
        self.write_suite(self.required_case(), count=1)
        self.write_suite(self.required_case(), count=1, filename="TEST-duplicate.xml")
        with self.assertRaisesRegex(ModuleError, "found 2"):
            check_module(self.expected)

    def test_duplicate_required_inventory(self):
        self.expected["identities"] *= 2
        with self.assertRaisesRegex(ModuleError, "duplicate identities in the required inventory"):
            check_module(self.expected)

    def test_missing_marker_and_stale_xml(self):
        result = self.write_suite(self.required_case(), count=1)
        os.utime(result, ns=(1_000_000_000, 1_000_000_000))
        with self.assertRaisesRegex(ModuleError, "stale XML"):
            check_module(self.expected)
        self.marker.unlink()
        with self.assertRaisesRegex(ModuleError, "execution-start marker .* is missing"):
            check_module(self.expected)

    def test_failed_and_skipped_cases_cannot_hide_behind_counts(self):
        for status in ("failure", "error", "skipped"):
            with self.subTest(status=status):
                self.write_suite(self.required_case(f"<{status}/>"), count=1)
                with self.assertRaisesRegex(ModuleError, f"carries a <{status}>"):
                    check_module(self.expected)

    def test_nonzero_suite_failure_and_skip_counts(self):
        for attribute in ("failures", "skipped"):
            with self.subTest(attribute=attribute):
                self.write_suite(self.required_case(), count=1, **{attribute: 1})
                with self.assertRaisesRegex(ModuleError, f"reports {attribute}=1"):
                    check_module(self.expected)

    def test_malformed_suite_counts(self):
        for count, diagnostic in (("no", "non-numeric"), (-1, "negative"), (2, "inconsistent XML")):
            with self.subTest(count=count):
                self.write_suite(self.required_case(), count=count)
                with self.assertRaisesRegex(ModuleError, diagnostic):
                    check_module(self.expected)

    def test_empty_or_duplicated_module_inventory(self):
        for expected in ([], [self.expected, self.expected]):
            with self.subTest(modules=len(expected)):
                with self.assertRaisesRegex(SystemExit, "nonempty, unique module inventory"):
                    run_gate("test gate", expected)


if __name__ == "__main__":
    unittest.main()
