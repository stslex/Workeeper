#!/usr/bin/env python3
"""Run offline observer tests with named JUnit evidence for the mutation harness."""
import argparse
from pathlib import Path
import time
import unittest
import xml.etree.ElementTree as ET


class XmlResult(unittest.TextTestResult):
    def startTest(self, test):
        super().startTest(test)
        self.began = time.monotonic()

    def stopTest(self, test):
        name = test.id().rsplit(".", 1)
        case = ET.SubElement(self.xml, "testcase", classname=name[0], name=name[1],
                             time=str(time.monotonic() - self.began))
        for kind, outcomes in (("failure", self.failures), ("error", self.errors), ("skipped", self.skipped)):
            for failed, detail in outcomes:
                if failed == test or getattr(failed, "test_case", None) == test:
                    ET.SubElement(case, kind, type="AssertionError" if kind == "failure" else kind).text = (
                        f"{failed}\n{detail}"
                    )
        super().stopTest(test)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    suite = unittest.defaultTestLoader.discover(str(Path(__file__).resolve().parent), pattern="test_*.py")
    if suite.countTestCases() < 20:
        raise SystemExit("Incomplete parser suite discovery: expected at least 20 named tests")
    XmlResult.xml = ET.Element("testsuite", name="WearAcceptanceParser")
    result = unittest.TextTestRunner(verbosity=2, resultclass=XmlResult).run(suite)
    for key, value in (("tests", result.testsRun), ("failures", len(result.failures)),
                       ("errors", len(result.errors)), ("skipped", len(result.skipped))):
        result.xml.set(key, str(value))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(result.xml).write(args.output, encoding="utf-8", xml_declaration=True)
    raise SystemExit(0 if result.wasSuccessful() and not result.skipped else 1)


if __name__ == "__main__":
    main()
