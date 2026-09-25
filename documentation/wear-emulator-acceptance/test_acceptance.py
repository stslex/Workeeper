"""Offline parser/ledger controls. These tests never call ADB, Gradle, or an emulator."""

import io
import json
import tarfile
import tempfile
import unittest
import xml.etree.ElementTree as ET
from unittest.mock import Mock, patch
from types import SimpleNamespace
from dataclasses import replace
from pathlib import Path

from acceptance_model import (
    DEADLINE_EXTRA, InvalidEvidence, Observation, ObservedFailure, boot_id, cell_by_id,
    expected_inventory, instrumentation_results, inventory_result, matrix,
    notification_deadline, notification_key, process_birth, removal_result, strict_json,
    uptime_ms, validate_config, validate_observation, validate_retention_progress, validate_ui_receipt,
    validate_elapsed_calibration, resumed_activities, background_activity_state, validate_background_continuity,
    validate_passive_timeout,
)
from adb_acceptance import artifact_inventory, extract_artifacts, parse_sections, run_cell, observation_verdict, UI_INVOCATIONS, death_trial
from run_mutations import is_assertion_test_failure
import adb_acceptance as acceptance_runner
import configure_cell


BOOT = "12c90163-e223-412e-bd25-a40e776ad999"
PACKAGE = "io.github.stslex.workeeper.dev"
KEY = "0|io.github.stslex.workeeper.dev|35501|null|10083"
IDENTITY = ("io.github.stslex.workeeper.wear.acceptance.WearActivityAcceptanceTest", "fixtureRendersThroughRealActivity")


def instrument_output(code=0, final=-1, stack=None):
    common = f"INSTRUMENTATION_STATUS: class={IDENTITY[0]}\nINSTRUMENTATION_STATUS: test={IDENTITY[1]}\n"
    failure = "" if stack is None else f"INSTRUMENTATION_STATUS: stack={stack}\n"
    return common + "INSTRUMENTATION_STATUS_CODE: 1\n" + common + failure + f"INSTRUMENTATION_STATUS_CODE: {code}\nINSTRUMENTATION_CODE: {final}\n"


def sample(when, *, key=KEY, pid=None, birth=None):
    return Observation(BOOT, when, when + 20, key, pid, birth)


class MatrixEvidenceTest(unittest.TestCase):
    def test_matrix_has_exact_sixteen_unique_cells(self):
        cells = matrix()
        self.assertEqual(16, len(cells))
        self.assertEqual(16, len({cell.id for cell in cells}))
        self.assertEqual({30, 36}, {cell.api for cell in cells})
        self.assertEqual({192, 240}, {cell.dp for cell in cells})
        self.assertEqual({"en", "ru"}, {cell.locale for cell in cells})
        self.assertEqual({"1.00", "1.24"}, {cell.font for cell in cells})

    def test_all_required_lifecycle_and_manual_rows_remain_required(self):
        inventory = expected_inventory()
        self.assertEqual(16, sum(row["kind"] == "ui" for row in inventory.values()))
        self.assertEqual(18, sum(row["kind"] == "lifecycle" for row in inventory.values()))
        self.assertEqual(4, sum(row["kind"] == "ambient_expiry" for row in inventory.values()))
        self.assertEqual(34, sum(row["kind"] == "manual" for row in inventory.values()))
        for api in (30, 36):
            self.assertIn(f"manual/api{api}/ambient-capabilities", inventory)
        rows = {key: {"status": "PASS"} for key in inventory}
        self.assertEqual("PASS", inventory_result(rows)["status"])
        for missing in inventory:
            partial = rows.copy()
            del partial[missing]
            result = inventory_result(partial)
            self.assertEqual("BLOCKED", result["status"], missing)
            self.assertIn(missing, result["missing"])

    def test_unknown_na_or_skipped_rows_cannot_turn_into_green(self):
        rows = {key: {"status": "PASS"} for key in expected_inventory()}
        for status in ("N/A", "SKIP", "UNKNOWN"):
            with self.subTest(status=status), self.assertRaises(InvalidEvidence):
                inventory_result({**rows, next(iter(rows)): {"status": status}})
        with self.assertRaises(InvalidEvidence):
            inventory_result({**rows, "ui/extra": {"status": "PASS"}})
        self.assertEqual("FAIL", inventory_result({**rows, next(iter(rows)): {"status": "FAIL"}})["status"])

    def test_passive_trials_require_measured_integer_fifteen_second_timeout(self):
        validate_passive_timeout({"screenOffTimeoutMs": 15000})
        for invalid in ({}, {"screenOffTimeoutMs": 120000}, {"screenOffTimeoutMs": "15000"},
                        {"screenOffTimeoutMs": 15000.0}, {"screenOffTimeoutMs": True}, {"screenOffTimeoutMs": None}):
            with self.assertRaises(InvalidEvidence):
                validate_passive_timeout(invalid)

    def test_config_uses_actual_resources_and_rejects_matrix_drift(self):
        cell = cell_by_id("api36-192-ru-1.24")
        good = {"api": 36, "screenWidthDp": 192, "screenHeightDp": 192, "locale": "ru",
                "fontScale": 1.24, "isScreenRound": True}
        validate_config(good, cell)
        for key, wrong in (("api", 30), ("screenWidthDp", 240), ("screenHeightDp", 240),
                           ("locale", "en"), ("fontScale", 1), ("isScreenRound", False)):
            with self.subTest(key=key), self.assertRaises(InvalidEvidence):
                validate_config({**good, key: wrong}, cell)
        with self.assertRaises(InvalidEvidence):
            validate_config({key: value for key, value in good.items() if key != "fontScale"}, cell)

    def test_ui_receipt_rejects_stale_token_wrong_method_or_preview_claim(self):
        cell = cell_by_id("api36-192-en-1.00")
        config = {"api": 36, "screenWidthDp": 192, "screenHeightDp": 192, "locale": "en",
                  "fontScale": 1, "isScreenRound": True}
        good = {"schema": 1, "acceptanceCellId": "fresh-id", "fixture": "active_boundary", "status": "PASS",
                "method": "actualMethod", "hostType": "real-MainActivity", "configuration": config,
                "sourceClassification": "SYNTHETIC_RUNTIME", "startedAtEpochMs": 123, "finishedAtEpochMs": 456,
                "artifacts": ["config.json", "first-view.png"]}
        validate_ui_receipt(good, config, cell, "fresh-id", "active_boundary", "actualMethod")
        for key, wrong in (("acceptanceCellId", "stale-id"), ("method", "wrongMethod"),
                           ("sourceClassification", "STATIC_PREVIEW"), ("status", "BLOCKED"),
                           ("artifacts", []), ("artifacts", ["../file"]), ("finishedAtEpochMs", 122)):
            with self.subTest(key=key, wrong=wrong), self.assertRaises(InvalidEvidence):
                validate_ui_receipt({**good, key: wrong}, config, cell, "fresh-id", "active_boundary", "actualMethod")


class NotificationEvidenceTest(unittest.TestCase):
    def test_api30_user_all_list_and_colon_record_preserve_exact_target_identity(self):
        key = "0|io.github.stslex.workeeper.dev|35501|null|10064"
        mixed_list = (
            "0|com.google.android.apps.wearable.settings|4137|null|1000\n"
            + key + "\n-1|android|19|null|1000\n"
        )
        record = (
            "NotificationRecord(0x02a20104: pkg=io.github.stslex.workeeper.dev user=UserHandle{0} "
            "id=35501 tag=null importance=2 key=" + key
            + ": Notification(channel=ongoing_workout shortcut=null contentView=null))\n"
            + f"      {DEADLINE_EXTRA}=Long (1009510)\n"
        )
        try:
            selected = notification_key(mixed_list, PACKAGE, 0)
            observed_deadline = notification_deadline(record, key)
        except InvalidEvidence as error:
            self.fail(f"Valid recorded API30 notification shape was rejected: {error}")
        self.assertEqual(key, selected)
        self.assertEqual(1009510, observed_deadline)
        self.assertIsNone(notification_key("-1|io.github.stslex.workeeper.dev|35501|null|10064", PACKAGE, 0))
        with self.assertRaises(InvalidEvidence):
            notification_key(mixed_list.replace("-1|android", "-2|android"), PACKAGE, 0)
        for invalid_record in (record.replace(key, key + "1"), record.replace(": Notification(", ": unrelated(")):
            with self.assertRaises(InvalidEvidence):
                notification_deadline(invalid_record, key)

    def test_selects_exact_user_package_id_and_null_tag(self):
        unrelated = ["10|io.github.stslex.workeeper.dev|35501|null|1010083",
                     "0|io.github.stslex.workeeper|35501|null|10082",
                     "0|io.github.stslex.workeeper.dev|35502|null|10083",
                     "0|io.github.stslex.workeeper.dev|35501|other|10083"]
        self.assertIsNone(notification_key("\n".join(unrelated), PACKAGE, 0))
        self.assertEqual(KEY, notification_key("\n".join(unrelated + [KEY]), PACKAGE, 0))
        with self.assertRaises(InvalidEvidence):
            notification_key(KEY + "\n" + KEY, PACKAGE, 0)

    def test_empty_success_differs_from_malformed_or_failed_list(self):
        self.assertIsNone(notification_key("", PACKAGE, 0))
        for case, output in enumerate(("error: service unavailable", "Unknown command: list", "0|broken",
                                      "Permission Denial: blocked", "Error occurred. Check logcat for details. stale")):
            with self.subTest(case=case), self.assertRaises(InvalidEvidence):
                notification_key(output, PACKAGE, 0)

    def test_reads_actual_long_dump_format_and_rejects_missing_duplicate_or_wrong_key(self):
        record = f"NotificationRecord(0x123: pkg={PACKAGE} id=35501 key={KEY})\n  {DEADLINE_EXTRA}=Long (671005)\n"
        self.assertEqual(671005, notification_deadline(record, KEY))
        for bad in (record.replace(KEY, "wrong"), record.replace("671005", "NaN"),
                    record.replace("671005", "0"), record + f"{DEADLINE_EXTRA}=123\n", ""):
            with self.subTest(bad=bad), self.assertRaises(InvalidEvidence):
                notification_deadline(bad, KEY)


class MonotonicEvidenceTest(unittest.TestCase):
    def test_calibration_rejects_missing_stale_or_incompatible_clock(self):
        validate_elapsed_calibration({"elapsedRealtimeMs": 1029}, 1000, 1020)
        for config, first, last in (({}, 1000, 1020), ({"elapsedRealtimeMs": 999}, 1000, 1020),
                                    ({"elapsedRealtimeMs": 1030}, 1000, 1020),
                                    ({"elapsedRealtimeMs": 1010}, 1020, 1000)):
            with self.subTest(config=config), self.assertRaises(InvalidEvidence):
                validate_elapsed_calibration(config, first, last)

    def test_retention_cannot_catch_up_after_stall_or_late_refresh(self):
        validate_retention_progress(sample(10100), sample(9900), 11000)
        with self.assertRaises(InvalidEvidence):
            validate_retention_progress(sample(13000), sample(9900), 15000)
        with self.assertRaises(InvalidEvidence):
            validate_retention_progress(sample(14000), sample(13800), 11000)

    def test_retention_observer_rejects_more_than_500ms_but_keeps_refresh_budget(self):
        validate_retention_progress(sample(10400), sample(9900), 10200)
        with self.assertRaises(InvalidEvidence):
            validate_retention_progress(sample(10401), sample(9900), 12000)
        with self.assertRaises(InvalidEvidence):
            validate_retention_progress(replace(sample(10100), after_ms=10601), sample(9900), 12000)
        validate_retention_progress(sample(10100), sample(9900), 8200)
        with self.assertRaises(InvalidEvidence):
            validate_retention_progress(sample(10100), sample(9900), 8100)

    def test_elapsed_clock_requires_raw_centisecond_pair(self):
        self.assertEqual(123450, uptime_ms("123.45 999.00\n"))
        for value in ("123", "123.45", "-1.00 0.00", "123.45 0.00\nstale", "NaN 0"):
            with self.subTest(value=value), self.assertRaises(InvalidEvidence):
                uptime_ms(value)

    def test_pid_starttime_parser_handles_spaces_and_parentheses(self):
        fields = ["S"] + ["0"] * 18 + ["12345", "42", "99"]
        text = "321 (a name (with parentheses)) " + " ".join(fields)
        self.assertEqual(12345, process_birth(text, 321))
        with self.assertRaises(InvalidEvidence):
            process_birth(text, 123)
        with self.assertRaises(InvalidEvidence):
            process_birth("321 (app) S 0", 321)

    def test_reboot_stale_time_and_partial_process_identity_are_inconclusive(self):
        first = sample(1000)
        validate_observation(sample(1030), first)
        invalid = [replace(sample(1030), boot="00000000-0000-0000-0000-000000000000"),
                   sample(1010), replace(sample(1030), after_ms=1029), sample(1030, pid=12)]
        for value in invalid:
            with self.subTest(value=value), self.assertRaises(InvalidEvidence):
                validate_observation(value, first)
        with self.assertRaises(InvalidEvidence):
            boot_id("missing")

    def test_removal_before_deadline_is_failure_with_centisecond_precision(self):
        for deadline in (10000, 9930):
            with self.assertRaises(ObservedFailure):
                removal_result([sample(9700), sample(9900, key=None)], deadline)
        try:
            uncertain = removal_result([sample(9700), sample(9900, key=None)], 9925)
        except InvalidEvidence as error:
            self.fail(f"A boundary within the recorded clock precision was rejected: {error}")
        self.assertEqual([9700, 9930], uncertain["removal_interval_ms"])
        self.assertEqual(10, uncertain.get("clock_precision_ms"))

    def test_removal_watchdog_requires_quantized_upper_bound(self):
        with self.assertRaises(InvalidEvidence):
            removal_result([sample(14780), sample(14980, key=None)], 10000)
        try:
            result = removal_result([sample(14770), sample(14970, key=None)], 10000)
        except InvalidEvidence as error:
            self.fail(f"A fully bounded watchdog observation was rejected: {error}")
        self.assertEqual([14770, 15000], result["removal_interval_ms"])
        self.assertEqual(10, result.get("clock_precision_ms"))

    def test_removal_has_a_real_bracket_and_no_live_app(self):
        rows = [sample(9900), sample(10100), sample(10300, key=None)]
        result = removal_result(rows, 10000)
        self.assertEqual([10100, 10330], result["removal_interval_ms"])
        for invalid in ([sample(9900, pid=1, birth=99), sample(10100, key=None)],
                        [sample(9900), sample(10100, key="wrong")],
                        [sample(9900, key=None), sample(10100, key=None)], [sample(9900)]):
            with self.subTest(invalid=invalid), self.assertRaises(InvalidEvidence):
                removal_result(invalid, 10000)

    def test_poll_gaps_and_slow_commands_cannot_hide_removal_latency(self):
        with self.assertRaises(InvalidEvidence):
            removal_result([sample(9900), sample(10500, key=None)], 10000)
        with self.assertRaises(InvalidEvidence):
            removal_result([sample(9900), replace(sample(10100, key=None), after_ms=11000)], 10000)

    def test_watchdog_distinguishes_present_failure_from_unresolved_late_absence(self):
        with self.assertRaises(ObservedFailure):
            removal_result([sample(14900), sample(15100)], 10000)
        with self.assertRaises(InvalidEvidence):
            removal_result([sample(14900), sample(15100, key=None)], 10000)
        with self.assertRaises(InvalidEvidence):
            removal_result([sample(9900), sample(10100)], 10000)


class InstrumentationEvidenceTest(unittest.TestCase):
    def test_requires_exact_started_and_completed_method_and_final_code(self):
        rows = instrumentation_results(instrument_output(), {IDENTITY})
        self.assertEqual(IDENTITY[1], rows[0]["method"])
        for output in (instrument_output().replace("INSTRUMENTATION_STATUS_CODE: 0", ""),
                       instrument_output().replace("INSTRUMENTATION_STATUS_CODE: 1", ""),
                       instrument_output(final=0), instrument_output().replace(IDENTITY[1], "differentMethod"),
                       "OK (1 test)\nINSTRUMENTATION_CODE: -1\n"):
            with self.subTest(output=output), self.assertRaises(InvalidEvidence):
                instrumentation_results(output, {IDENTITY})

    def test_skips_missing_methods_duplicate_completions_are_not_pass(self):
        for code in (-3, -4):
            with self.subTest(code=code), self.assertRaises(InvalidEvidence):
                instrumentation_results(instrument_output(code), {IDENTITY})
        with self.assertRaises(InvalidEvidence):
            instrumentation_results(instrument_output(), {IDENTITY, (IDENTITY[0], "other")})
        with self.assertRaises(InvalidEvidence):
            instrumentation_results(instrument_output() + instrument_output(), {IDENTITY})

    def test_actual_named_assertion_failure_is_fail(self):
        for assertion in ("AssertionError", "java.lang.AssertionError", "junit.framework.AssertionFailedError",
                          "junit.framework.ComparisonFailure", "org.junit.ComparisonFailure", "org.opentest4j.AssertionFailedError",
                          "org.junit.internal.ArrayComparisonFailure"):
            with self.assertRaises(ObservedFailure):
                instrumentation_results(instrument_output(-2, stack=assertion + ": expected a different value"), {IDENTITY})

    def test_execution_errors_are_inconclusive_even_with_assertion_stack(self):
        for code, stack in ((-1, "java.lang.IllegalStateException: setup failed"),
                            (-1, "java.lang.AssertionError: runner failed"),
                            (2, "java.lang.AssertionError: unknown status")):
            try:
                instrumentation_results(instrument_output(code, stack=stack), {IDENTITY})
            except InvalidEvidence:
                pass
            except ObservedFailure as error:
                self.fail(f"Execution status was misclassified as an application assertion: {error}")
            else:
                self.fail("Execution error incorrectly returned completed test evidence")

    def test_failure_code_requires_top_level_assertion_stack(self):
        stacks = (None, "", "java.lang.RuntimeException: setup failed",
                  "java.lang.IllegalStateException: wrapper\nCaused by: java.lang.AssertionError: nested",
                  "at org.junit.Assert.fail(Assert.java:89)", "java.lang.AssertionErrorExtra: unknown type")
        for stack in stacks:
            try:
                instrumentation_results(instrument_output(-2, stack=stack), {IDENTITY})
            except InvalidEvidence:
                pass
            except ObservedFailure as error:
                self.fail(f"Non-assertion stack was misclassified as an application assertion: {error}")
            else:
                self.fail("Non-assertion stack incorrectly returned completed test evidence")


class ArtifactEvidenceTest(unittest.TestCase):
    def test_activity_observer_accepts_actual_equals_and_colon_but_not_empty_dump(self):
        header = "ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)\n"
        for separator in ("=", ": "):
            self.assertEqual(["abc u0 pkg/.Activity t10"], resumed_activities(
                header + f"  topResumedActivity{separator}ActivityRecord{{abc u0 pkg/.Activity t10}}\n"))
        with self.assertRaises(InvalidEvidence):
            resumed_activities("")

    def test_duplicate_json_keys_rejected(self):
        with self.assertRaises(InvalidEvidence):
            strict_json('{"status":"FAIL","status":"PASS"}')
        with self.assertRaises(InvalidEvidence):
            strict_json("[]")

    def test_missing_reordered_or_repeated_timed_sections_rejected(self):
        good = "@@WEAR:before\n10.00 9.00\n@@WEAR:after\n10.01 9.00\n"
        self.assertEqual("10.00 9.00", parse_sections(good, ("before", "after"))["before"])
        for text in (good.replace("@@WEAR:after", "@@WEAR:before"), "stale\n" + good, "", good.split("@@WEAR:after")[0]):
            with self.subTest(text=text), self.assertRaises(InvalidEvidence):
                parse_sections(text, ("before", "after"))

    def test_nested_receipts_are_hashed_and_changed_bytes_detectable(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "receipt.json").write_text("outer")
            (root / "nested").mkdir()
            (root / "nested/receipt.json").write_text("initial")
            before = artifact_inventory(root)
            self.assertNotIn("receipt.json", before)
            self.assertIn("nested/receipt.json", before)
            (root / "nested/receipt.json").write_text("changed")
            self.assertNotEqual(before, artifact_inventory(root))

    def test_archive_never_extracts_parent_paths_links_or_duplicate_files(self):
        for name, linked in (("../escape", False), ("/absolute", False), ("link", True), ("duplicate", False)):
            data = io.BytesIO()
            with tarfile.open(fileobj=data, mode="w") as archive:
                entry = tarfile.TarInfo(name)
                if linked:
                    entry.type = tarfile.SYMTYPE
                    entry.linkname = "/tmp/escape"
                archive.addfile(entry)
                if name == "duplicate":
                    archive.addfile(entry)
            with tempfile.TemporaryDirectory() as directory, self.subTest(name=name), self.assertRaises(InvalidEvidence):
                extract_artifacts(data.getvalue(), Path(directory))


class IndependentInvocationTest(unittest.TestCase):
    def test_failed_first_invocation_does_not_skip_remaining_fixtures_or_journeys(self):
        results = [ObservedFailure("first assertion"), InvalidEvidence("second observation"), *({} for _ in range(20))]
        with tempfile.TemporaryDirectory() as temporary:
            adb = SimpleNamespace(directory=Path(temporary))
            with patch("adb_acceptance.preflight"), patch("adb_acceptance.verify_installed"), patch(
                "adb_acceptance.run_instrumentation", side_effect=results
            ) as execute:
                detail = run_cell(adb, {"package": PACKAGE}, cell_by_id("api36-192-ru-1.24"))
            self.assertEqual(execute.call_count, 22)
            self.assertEqual([row["selection"] for row in detail["invocations"]], list(UI_INVOCATIONS))
            self.assertEqual([row["status"] for row in detail["invocations"]], ["FAIL", "BLOCKED", *(["PASS"] * 20)])
            self.assertEqual(strict_json((Path(temporary) / "invocations.json").read_text())["invocations"], detail["invocations"])
            self.assertEqual(observation_verdict(detail)[0], "FAIL")
            self.assertIn("first assertion", observation_verdict(detail)[1])

    def test_partial_or_inconclusive_cell_cannot_be_overwritten_with_pass(self):
        self.assertEqual(observation_verdict({"complete": False})[0], "BLOCKED")
        detail = {"complete": True, "invocations": [{"status": "BLOCKED", "selection": "ambient:none", "reason": "unsupported"}]}
        self.assertEqual(observation_verdict(detail)[0], "BLOCKED")
        self.assertIn("unsupported", observation_verdict(detail)[1])
        self.assertEqual(observation_verdict({"complete": True, "invocations": [{"status": "PASS"}]})[0], "PASS")


class MutationEvidenceTest(unittest.TestCase):
    def test_only_top_level_assertion_or_comparison_failures_credit_named_red(self):
        accepted_types = (
            "AssertionError", "java.lang.AssertionError", "junit.framework.AssertionFailedError",
            "org.opentest4j.AssertionFailedError", "junit.framework.ComparisonFailure",
            "org.junit.ComparisonFailure", "org.junit.internal.ArrayComparisonFailure",
        )
        for index, exception in enumerate(accepted_types):
            for declared in (True, False):
                test = ET.Element("testcase")
                failure = ET.SubElement(test, "failure", {"type": exception} if declared else {})
                failure.text = exception + ": expected value differs\nat org.junit.Assert.fail(Assert.java:89)"
                self.assertTrue(is_assertion_test_failure(test), (index, declared))
        rejected_bodies = (
            "", "java.lang.IllegalStateException: setup failed\nCaused by: java.lang.AssertionError: nested",
            "java.io.IOException: transport unavailable\nat org.junit.Assert.fail(Assert.java:89)",
            "at java.lang.AssertionError.fake(Stack.java:1)",
            "setup log mentions java.lang.AssertionError\njava.lang.AssertionError: later",
            "java.lang.AssertionErrorExtra: wrong type",
            "Caused by: java.lang.AssertionError: nested only",
        )
        for index, body in enumerate(rejected_bodies):
            test = ET.Element("testcase")
            ET.SubElement(test, "failure").text = body
            self.assertFalse(is_assertion_test_failure(test), index)
        for index, shape in enumerate(("wrong_type", "error", "skipped", "missing")):
            test = ET.Element("testcase")
            if shape != "missing":
                kind = "java.lang.RuntimeException" if shape == "wrong_type" else "java.lang.AssertionError"
                ET.SubElement(test, "failure", type=kind).text = "java.lang.AssertionError: body cannot override explicit type"
            if shape in ("error", "skipped"):
                ET.SubElement(test, shape)
            self.assertFalse(is_assertion_test_failure(test), index)
        grouped = ET.Element("testcase")
        ET.SubElement(grouped, "failure", type="AssertionError").text = "first failed subtest"
        ET.SubElement(grouped, "failure").text = "org.junit.ComparisonFailure: second failed subtest"
        self.assertTrue(is_assertion_test_failure(grouped))
        for runtime_first in (False, True):
            mixed = ET.Element("testcase")
            bodies = ["java.lang.AssertionError: assertion", "java.lang.IllegalStateException: setup"]
            for body in reversed(bodies) if runtime_first else bodies:
                ET.SubElement(mixed, "failure").text = body
            self.assertFalse(is_assertion_test_failure(mixed), runtime_first)


class DeathPreparationTest(unittest.TestCase):
    def test_background_dump_requires_home_and_target_stopped_nonvisible(self):
        home = "com.google.android.wearable.app/com.google.android.clockwork.home.HomeActivity"
        target = PACKAGE + "/io.github.stslex.workeeper.wear.MainActivity"
        raw = (
            "ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)\n"
            "  * Task{abc type=home}\n"
            f"    * Hist #0: ActivityRecord{{aaa u0 {home} t1}}\n"
            "      state=RESUMED delayedResume=false finishing=false\n"
            "      mVisibleRequested=true mVisible=true mClientVisible=true\n"
            "  * Task{def type=standard}\n"
            f"    * Hist #0: ActivityRecord{{bbb u0 {target} t2}}\n"
            "      state=STOPPED stopped=true delayedResume=false finishing=false\n"
            "      mVisibleRequested=false mVisible=false mClientVisible=false\n"
            f"  topResumedActivity=ActivityRecord{{aaa u0 {home} t1}}\n"
        )
        self.assertEqual(background_activity_state(raw, PACKAGE, 0, home)["stopped_nonvisible_targets"], [target])
        resolved = "priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\n" + home
        self.assertEqual(background_activity_state(raw, PACKAGE, 0, resolved)["stopped_nonvisible_targets"], [target])
        for invalid_home in (home + "\n" + home, "unexpected metadata\n" + home):
            with self.assertRaises(InvalidEvidence):
                background_activity_state(raw, PACKAGE, 0, invalid_home)
        malformed = (
            raw.replace(f"topResumedActivity=ActivityRecord{{aaa u0 {home} t1}}", f"topResumedActivity=ActivityRecord{{bbb u0 {target} t2}}"),
            raw.replace("state=STOPPED", "state=PAUSED"),
            raw.replace("mVisible=false", "mVisible=true"),
            raw.replace("mVisibleRequested=false", "mVisibleRequested=true"),
            raw.replace(f"Hist #0: ActivityRecord{{bbb u0 {target} t2}}", "Hist #0: ActivityRecord{bbb u0 unrelated/.Other t2}"),
            raw.replace("      state=STOPPED", "    state=STOPPED"),
            raw.replace("u0", "u10"), "",
        )
        for index, value in enumerate(malformed):
            with self.assertRaises(InvalidEvidence, msg=str(index)):
                background_activity_state(value, PACKAGE, 0, home)
        with self.assertRaises(InvalidEvidence):
            background_activity_state(raw, PACKAGE, 0, "No activity found")

    def test_background_transition_cannot_change_process_notification_or_deadline(self):
        previous = sample(1000, pid=77, birth=8)
        current = sample(1200, pid=77, birth=8)
        validate_background_continuity(previous, current, 2000, 2000)
        for index, bad in enumerate((replace(current, pid=78), replace(current, birth=9), replace(current, key=None),
                                     replace(current, key=KEY + "x"), replace(current, pid=None, birth=None))):
            with self.assertRaises(InvalidEvidence, msg=str(index)):
                validate_background_continuity(previous, bad, 2000, 2000)
        for changed in (1999, 2001, 1200):
            with self.assertRaises(InvalidEvidence):
                validate_background_continuity(previous, current, 2000, changed)

    def test_death_trial_backgrounds_before_signalling_the_verified_process(self):
        events = []
        def background(adb, plan):
            events.append("home")
            return {"verified": True}
        def shell(*args, **kwargs):
            if args[2] == "cat":
                return "77 (wear process) " + " ".join(["S"] + ["0"] * 18 + ["8"])
            self.assertEqual(args[2:], ("kill", "-9", "77"))
            events.append("kill")
            return ""
        observations = [sample(1100, pid=77, birth=8), sample(1200, pid=77, birth=8), sample(1300), sample(1400, key=None)]
        with tempfile.TemporaryDirectory() as directory:
            adb = SimpleNamespace(directory=Path(directory), shell=shell, command=Mock(return_value=b"cache"), screenshot=Mock())
            with patch("adb_acceptance.start_trial", return_value=sample(1000, pid=77, birth=8)), patch(
                "adb_acceptance.deadline", return_value=1400
            ), patch("adb_acceptance.background_for_death", side_effect=background), patch(
                "adb_acceptance.observe", side_effect=observations
            ), patch("adb_acceptance.time.sleep"):
                result = death_trial(adb, {"package": PACKAGE, "user": 0}, 36, False)
            self.assertEqual(events, ["home", "kill"])
            self.assertEqual(result["background"], {"verified": True})
            self.assertEqual(result["before_kill"]["before_ms"], 1200)


class OutputContainmentTest(unittest.TestCase):
    def test_case_parent_link_refuses_manual_and_automated_writes(self):
        for mode in ("manual", "automated"):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary).resolve()
                cohort, outside = root / "cohort", root / "outside"
                (cohort / "cases").mkdir(parents=True)
                outside.mkdir()
                evidence = root / "observation.txt"
                evidence.write_text("Recorded isolated observation")
                case_id = "manual/api36/system-tile"
                plan = {"cohort": "test", "source": {"head": "test"}, "package": PACKAGE,
                        "expected": {case_id: {"kind": "manual"}}}
                operation_called = []

                def operation(adb):
                    operation_called.append(True)
                    return {}

                def invoke():
                    if mode == "manual":
                        arguments = ["adb_acceptance.py", "record-manual", "--cohort", str(cohort),
                                     "--case", case_id, "--status", "PASS", "--reason", "Observed",
                                     "--artifact", str(evidence)]
                        with patch("sys.argv", arguments):
                            acceptance_runner.main()
                    else:
                        args = SimpleNamespace(cohort=cohort, serial="emulator-5554", adb="adb")
                        acceptance_runner.run_case(args, plan, case_id, operation)

                with patch("adb_acceptance.validate_plan", return_value=plan), patch(
                    "adb_acceptance.source_identity", return_value=plan["source"]
                ), patch("adb_acceptance.verify_installed"), patch("builtins.print"):
                    invoke()
                    receipt = strict_json((cohort / "cases" / case_id / "receipt.json").read_text())
                    self.assertEqual("PASS", receipt["status"], "An ordinary in-cohort output remains valid")
                    cohort = root / "linked-cohort"
                    (cohort / "cases").mkdir(parents=True)
                    (cohort / "cases/manual").symlink_to(outside, target_is_directory=True)
                    operation_called.clear()
                    with self.assertRaises(InvalidEvidence):
                        invoke()
                self.assertEqual([], operation_called)
                self.assertEqual([], list(outside.iterdir()), "Escaped directory must remain untouched")
                self.assertEqual("Recorded isolated observation", evidence.read_text())

    def test_inventory_rejects_file_and_directory_links_before_hashing(self):
        for kind in ("file", "directory"):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                artifacts, outside = root / "artifacts", root / "outside"
                artifacts.mkdir()
                outside.mkdir()
                witness = outside / "evidence.txt"
                witness.write_text("External bytes must not be read as cohort evidence")
                target = witness if kind == "file" else outside
                (artifacts / "linked").symlink_to(target, target_is_directory=kind == "directory")
                with patch("adb_acceptance.sha256", side_effect=AssertionError("External artifact was read")):
                    with self.assertRaises(InvalidEvidence):
                        artifact_inventory(artifacts)
                self.assertEqual("External bytes must not be read as cohort evidence", witness.read_text())

    def test_existing_json_temporary_link_cannot_overwrite_external_file(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            report = root / "report.json"
            acceptance_runner.write_json(report, {"status": "BLOCKED"})
            previous = report.read_bytes()
            outside = root / "outside.txt"
            outside.write_text("Preserve this external file")
            report.with_suffix(".json.tmp").symlink_to(outside)
            with self.assertRaises(InvalidEvidence):
                acceptance_runner.write_json(report, {"status": "PASS"})
            self.assertEqual("Preserve this external file", outside.read_text())
            self.assertEqual(previous, report.read_bytes())


class LocaleSetupTest(unittest.TestCase):
    def run_setup(self, *, setting="en-US", prop="en-US", effective="en-rUS", persisted=None,
                  user="0", after_effective=None, final_setting=None, clock=None):
        self.commands = []
        self.locale_state = {"setting": setting, "prop": prop, "effective": effective}
        persisted = list(persisted or ["ru-RU"])

        def run(argv, **kwargs):
            parts = argv[3:]
            self.commands.append(parts)
            state = self.locale_state
            out = ""
            if parts == ["shell", "getprop", "ro.build.version.sdk"]:
                out = "30"
            elif parts == ["shell", "am", "get-current-user"]:
                out = user
            elif parts == ["shell", "getprop", "persist.sys.locale"]:
                out = state["prop"]
            elif parts == ["shell", "settings", "--user", "0", "get", "system", "system_locales"]:
                out = final_setting if final_setting is not None and ["shell", "start"] in self.commands else state["setting"]
            elif parts == ["shell", "am", "get-config"]:
                out = "config: " + state["effective"] + "-ldltr-sw192dp-w192dp-h192dp-small-round-v30\n"
            elif parts[:3] == ["shell", "setprop", "persist.sys.locale"]:
                state["prop"] = parts[3]
            elif parts[:6] == ["shell", "settings", "--user", "0", "put", "system"]:
                state["setting"] = parts[7]
            elif parts == ["exec-out", "abx2xml", "/data/system/users/0/settings_system.xml", "-"]:
                value = persisted.pop(0) if len(persisted) > 1 else persisted[0]
                out = ("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>"
                       '<settings version="213"><setting id="12" name="system_locales" value="'
                       + value + '" /></settings><namespaceHashes />')
            elif parts == ["shell", "start"]:
                state["effective"] = after_effective or {"en-US": "en-rUS", "ru-RU": "ru-rRU"}[state["setting"]]
            elif parts == ["shell", "pm", "path", "android"]:
                out = "package:/system/framework/framework-res.apk"
            elif parts == ["shell", "dumpsys", "battery"]:
                out = "AC powered: false\nUSB powered: false\nWireless powered: false\n"
            elif parts == ["shell", "settings", "get", "system", "screen_off_timeout"]:
                out = "120000"
            return SimpleNamespace(returncode=0, stdout=out, stderr="", check_returncode=lambda: None)

        args = ["configure_cell.py", "--adb", "/fake/adb", "--serial", "emulator-5554",
                "--locale", "ru", "--scale", "1.24", "--output", str(self.output)]
        with patch("sys.argv", args), patch("configure_cell.subprocess.run", side_effect=run), patch(
            "configure_cell.time.sleep"
        ), patch("builtins.print"):
            if clock is None:
                configure_cell.main()
            else:
                with patch("configure_cell.time.monotonic", side_effect=clock):
                    configure_cell.main()

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.output = Path(self.temporary.name) / "setup.json"

    def test_user_locale_is_corrected_even_when_property_matches(self):
        self.run_setup(prop="ru-RU")
        self.assertEqual("ru-RU", self.locale_state["setting"])
        self.assertEqual("ru-rRU", self.locale_state["effective"])
        self.assertIn(["shell", "stop"], self.commands)
        self.assertEqual("CONFIGURED_NOT_VERIFIED", json.loads(self.output.read_text())["status"])

    def test_framework_restart_waits_for_committed_settings_locale(self):
        self.run_setup(persisted=["en-US", "ru-RU"])
        stop = self.commands.index(["shell", "stop"])
        reads = [i for i, command in enumerate(self.commands) if command[:2] == ["exec-out", "abx2xml"]]
        self.assertEqual(2, len(reads))
        self.assertLess(reads[-1], stop)
        self.assertLess(self.commands.index(["shell", "settings", "--user", "0", "put", "system",
                                            "system_locales", "ru-RU"]), reads[0])

    def test_uncommitted_locale_never_stops_the_framework(self):
        with self.assertRaises(RuntimeError):
            self.run_setup(persisted=["en-US"], clock=[0, 91])
        self.assertNotIn(["shell", "stop"], self.commands)
        self.assertEqual("BLOCKED", json.loads(self.output.read_text())["status"])

    def test_wrong_effective_locale_after_restart_cannot_be_configured(self):
        with self.assertRaises(RuntimeError):
            self.run_setup(after_effective="en-rUS", clock=[0, 0, 91])
        self.assertEqual("BLOCKED", json.loads(self.output.read_text())["status"])

    def test_nonzero_user_is_rejected_before_locale_mutations(self):
        with self.assertRaises(RuntimeError):
            self.run_setup(user="10")
        self.assertFalse(any(command[:2] == ["shell", "setprop"] or command == ["shell", "stop"]
                             for command in self.commands))
        self.assertEqual("BLOCKED", json.loads(self.output.read_text())["status"])

    def test_matching_user_property_and_effective_locale_do_not_restart(self):
        self.run_setup(setting="ru-RU", prop="ru-RU", effective="ru-rRU")
        self.assertNotIn(["shell", "stop"], self.commands)
        self.assertNotIn(["root"], self.commands)
        self.assertEqual("CONFIGURED_NOT_VERIFIED", json.loads(self.output.read_text())["status"])


    def test_persisted_locale_accepts_observed_fragment_and_optional_namespace(self):
        declaration = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>"
        for value in ("en-US", "ru-RU"):
            for suffix in ("", '<namespaceHashes><namespaceHash namespace="runtime" hash="123" /></namespaceHashes>'):
                raw = (declaration + '\n<settings version="213"><setting id="42" name="system_locales" '
                       'value="' + value + '" package="android" /></settings>' + suffix)
                try:
                    actual = configure_cell.persisted_locale(raw)
                except RuntimeError as error:
                    self.fail(str(error))
                self.assertEqual(value, actual)

    def test_persisted_locale_rejects_ambiguous_or_nonplain_values(self):
        declaration = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>"
        entry = '<setting name="system_locales" value="ru-RU" />'
        root = '<settings version="213">' + entry + '</settings>'
        bodies = [root + root, root + '<namespaceHashes /><namespaceHashes />',
                  '<namespaceHashes />' + root, root + 'unparsed tail',
                  root.replace(entry, entry + entry), root.replace(entry, ''),
                  root.replace(entry, '<nested>' + entry + '</nested>'),
                  root.replace('value="ru-RU"', 'valueBase64="cnUtUlU="'),
                  root.replace('value="ru-RU"', 'value="ru-RU" valueBase64="cnUtUlU="'),
                  root.replace('value="ru-RU"', 'value="de-DE"'),
                  root.replace('version="213"', 'version="invalid"'),
                  root.replace('</settings>', ''), '<!DOCTYPE settings>' + root]
        for raw in (root, *[declaration + body for body in bodies]):
            with self.subTest(raw=raw), self.assertRaises(RuntimeError):
                configure_cell.persisted_locale(raw)

    def test_effective_locale_requires_one_exact_matching_config(self):
        ru = 'config: mcc250-mnc01-ru-rRU-ldltr-sw192dp-round-v30\n'
        en = 'config: en-rUS-ldltr-sw192dp-round-v30\n'
        self.assertTrue(configure_cell.locale_configuration_matches(ru, "ru-RU"))
        self.assertTrue(configure_cell.locale_configuration_matches(en, "en-US"))
        for raw in (en, ru + ru, ru + en, 'metadata ru-rRU\n', ru.replace('config:', 'configuration:')):
            with self.subTest(raw=raw):
                self.assertFalse(configure_cell.locale_configuration_matches(raw, "ru-RU"))
        self.assertTrue(configure_cell.locale_needs_reload("en-US", "ru-RU", ru, "ru-RU"))
        self.assertTrue(configure_cell.locale_needs_reload("ru-RU", "en-US", ru, "ru-RU"))
        self.assertTrue(configure_cell.locale_needs_reload("ru-RU", "ru-RU", en, "ru-RU"))
        self.assertFalse(configure_cell.locale_needs_reload("ru-RU", "ru-RU", ru, "ru-RU"))

    def test_final_setting_drift_is_not_reported_as_configured(self):
        with self.assertRaises(RuntimeError):
            self.run_setup(final_setting="en-US")
        self.assertEqual("BLOCKED", json.loads(self.output.read_text())["status"])


class ManualRegistrationTest(unittest.TestCase):
    def invoke(self, cohort, artifacts):
        case_id = "manual/api36/system-tile"
        plan = {"cohort": "manual-test", "source": {"head": "test"},
                "expected": {case_id: {"kind": "manual"}}}
        args = ["adb_acceptance.py", "record-manual", "--cohort", str(cohort), "--case", case_id,
                "--status", "BLOCKED", "--reason", "A reviewed limitation"]
        for artifact in artifacts:
            args += ["--artifact", str(artifact)]
        with patch("sys.argv", args), patch("adb_acceptance.validate_plan", return_value=plan):
            acceptance_runner.main()

    def test_invalid_later_attachment_leaves_no_partial_case(self):
        for kind in ("empty", "missing", "unreadable", "became_empty"):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                cohort = root / "cohort"
                cohort.mkdir()
                first, last = root / "first.txt", root / "last.txt"
                first.write_text("Keep the first source artifact")
                if kind != "missing":
                    last.write_bytes(b"" if kind == "empty" else b"unreadable")
                real_open = Path.open

                def open_path(path, *args, **kwargs):
                    if path == last and kind == "unreadable":
                        raise PermissionError("Cannot read this attachment")
                    if path == last and kind == "became_empty":
                        return io.BytesIO()
                    return real_open(path, *args, **kwargs)

                with patch.object(Path, "open", open_path):
                    with self.assertRaises((InvalidEvidence, OSError)):
                        self.invoke(cohort, [first, last])
                self.assertFalse((cohort / "cases").exists(), "Preflight must not publish a partial case")
                self.assertEqual("Keep the first source artifact", first.read_text())

    def test_valid_manual_artifacts_preserve_bytes_and_existing_case(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            cohort = root / "cohort"
            cohort.mkdir()
            first, second = root / "first.txt", root / "second.bin"
            first.write_bytes(b"Observed text\n")
            second.write_bytes(bytes(range(256)))
            self.invoke(cohort, [first, second])
            case = cohort / "cases/manual/api36/system-tile"
            receipt = json.loads((case / "receipt.json").read_text())
            self.assertEqual("BLOCKED", receipt["status"])
            self.assertEqual(first.read_bytes(), (case / "operator-00-first.txt").read_bytes())
            self.assertEqual(second.read_bytes(), (case / "operator-01-second.bin").read_bytes())
            before = {str(path.relative_to(case)): path.read_bytes() for path in case.rglob("*") if path.is_file()}
            with self.assertRaises(FileExistsError):
                self.invoke(cohort, [first, second])
            self.assertEqual(before, {str(path.relative_to(case)): path.read_bytes()
                                      for path in case.rglob("*") if path.is_file()})

    def test_changed_attachment_after_preflight_cannot_publish_a_receipt(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            cohort = root / "cohort"
            cohort.mkdir()
            evidence = root / "evidence.txt"
            evidence.write_bytes(b"expected")
            real_read = Path.read_bytes

            def read_path(path):
                return b"replaced" if path == evidence else real_read(path)

            with patch.object(Path, "read_bytes", read_path):
                with self.assertRaises(InvalidEvidence):
                    self.invoke(cohort, [evidence])
            case = cohort / "cases/manual/api36/system-tile"
            self.assertFalse((case / "receipt.json").exists())
            self.assertFalse((case / "operator-00-evidence.txt").exists())
            self.assertEqual(b"expected", evidence.read_bytes())


class DeathSignalTest(unittest.TestCase):
    STATUS = ("Name:\twear\nTgid:\t77\nPid:\t77\n"
              "SigBlk:\t0000000080001204\nSigIgn:\t0000000000000001\n"
              "SigCgt:\t0000006e400084f8\n")
    STAT = "77 (wear process) " + " ".join(["S"] + ["0"] * 18 + ["8"])

    def fake_adb(self, directory, status=None, stat=None, fail_signal=False):
        calls = []
        def shell(*args, **kwargs):
            calls.append(args)
            if args[2] == "cat" and args[3].endswith("/status"):
                return self.STATUS if status is None else status
            if args[2] == "cat" and args[3].endswith("/stat"):
                return self.STAT if stat is None else stat
            if args[2] == "kill":
                if fail_signal and args[3] == "-15":
                    raise InvalidEvidence("Recorded signal command was denied")
                return ""
            raise AssertionError("Unexpected fake command")
        return SimpleNamespace(directory=Path(directory), shell=shell, calls=calls,
                               command=Mock(return_value=b"cache"), screenshot=Mock())

    def invoke_cli(self, api, scenario="death-fresh", selected=None):
        args = ["adb_acceptance.py", "run-lifecycle", "--cohort", "/private/tmp/term-unit-cohort",
                "--api", str(api), "--scenario", scenario, "--repetition", "1", "--serial", "emulator-test"]
        if selected is not None:
            args += ["--death-signal", selected]
        with patch("sys.argv", args), patch("adb_acceptance.validate_plan", return_value={}), patch(
            "adb_acceptance.death_trial", return_value={}
        ) as death, patch("adb_acceptance.run_case", side_effect=lambda args, plan, key, operation: operation("fake")):
            acceptance_runner.main()
            return death.call_args

    def test_cli_defaults_to_kill_and_forwards_explicit_term(self):
        for api, selected, expected in ((30, None, "kill"), (36, None, "kill"), (30, "term-default", "term-default")):
            try:
                actual = self.invoke_cli(api, selected=selected)
            except Exception as error:
                self.fail(f"Valid CLI selection rejected: {type(error).__name__}: {error}")
            self.assertEqual(actual.args, ("fake", {}, api, False, expected))

    def test_term_is_rejected_for_wrong_api_or_retention_before_case_creation(self):
        for api, scenario in ((36, "death-fresh"), (30, "retention")):
            with patch("adb_acceptance.retention_trial", return_value={}):
                with self.assertRaises(InvalidEvidence):
                    self.invoke_cli(api, scenario, "term-default")
        with self.assertRaises(InvalidEvidence):
            acceptance_runner.validate_death_signal(30, "death-fresh", "unexpected")
        with patch("adb_acceptance.start_trial") as setup:
            with self.assertRaises(InvalidEvidence):
                death_trial(None, {}, 36, False, "term-default")
            setup.assert_not_called()

    def test_default_disposition_is_preserved_as_explicit_backend_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            adb = self.fake_adb(directory)
            try:
                record = acceptance_runner.signal_process_death(adb, {"package": PACKAGE}, sample(1000, pid=77, birth=8), 30, "term-default")
            except Exception as error:
                self.fail(f"Default TERM disposition rejected: {type(error).__name__}: {error}")
            self.assertEqual(adb.calls, [("run-as", PACKAGE, "cat", "/proc/77/status"),
                                         ("run-as", PACKAGE, "cat", "/proc/77/stat"),
                                         ("run-as", PACKAGE, "kill", "-15", "77")])
            self.assertEqual(record["signal"], 15)
            self.assertEqual(record["backend"], "RUN_AS_SIGTERM_DEFAULT_DISPOSITION")
            self.assertTrue(record["disposition"]["default_disposition_observed"])
            self.assertEqual(record["status"], "SIGNAL_COMMAND_RETURNED_SUCCESS_NOT_DEATH_PROOF")
            self.assertEqual(json.loads((Path(directory) / "signal-backend.json").read_text()), record)

    def test_caught_ignored_or_blocked_term_never_reaches_signal(self):
        for original, replacement in (("0000006e400084f8", "0000006e4000c4f8"),
                                      ("SigIgn:\t0000000000000001", "SigIgn:\t0000000000004001"),
                                      ("SigBlk:\t0000000080001204", "SigBlk:\t0000000080005204")):
            with tempfile.TemporaryDirectory() as directory:
                adb = self.fake_adb(directory, status=self.STATUS.replace(original, replacement))
                with self.assertRaises(InvalidEvidence):
                    acceptance_runner.signal_process_death(adb, {"package": PACKAGE}, sample(1000, pid=77, birth=8), 30, "term-default")
                self.assertEqual(adb.calls, [("run-as", PACKAGE, "cat", "/proc/77/status")])

    def test_wrong_identity_or_corrupt_status_is_inconclusive(self):
        invalid = (self.STATUS + "Pid:\t77\n", self.STATUS.replace("Pid:\t77", "Pid:\t78"),
                   self.STATUS.replace("Tgid:\t77", "Tgid:\t78"), self.STATUS.replace("0000006e400084f8", "84f8"),
                   self.STATUS.replace("0000006e400084f8", "0000006e400084fg"), self.STATUS.rstrip("\n"),
                   self.STATUS.replace("SigIgn:\t0000000000000001\n", ""))
        for raw in invalid:
            try:
                acceptance_runner.default_term_disposition(raw, 77)
            except InvalidEvidence:
                continue
            except Exception as error:
                self.fail(f"Wrong error classification: {type(error).__name__}: {error}")
            self.fail("Malformed or mismatched signal status was accepted")

    def test_birth_reuse_prevents_either_signal_backend(self):
        for backend in ("kill", "term-default"):
            with tempfile.TemporaryDirectory() as directory:
                adb = self.fake_adb(directory, stat=self.STAT[:-1] + "9")
                with self.assertRaises(InvalidEvidence):
                    acceptance_runner.signal_process_death(adb, {"package": PACKAGE}, sample(1000, pid=77, birth=8), 30, backend)
                self.assertFalse(any(call[2] == "kill" for call in adb.calls))

    def test_signal_error_has_no_second_signal_or_fallback(self):
        with tempfile.TemporaryDirectory() as directory:
            adb = self.fake_adb(directory, fail_signal=True)
            with self.assertRaises(InvalidEvidence):
                acceptance_runner.signal_process_death(adb, {"package": PACKAGE}, sample(1000, pid=77, birth=8), 30, "term-default")
            self.assertEqual([call[3] for call in adb.calls if call[2] == "kill"], ["-15"])
            self.assertEqual(json.loads((Path(directory) / "signal-backend.json").read_text())["status"],
                             "PRECONDITIONS_VALIDATED_SIGNAL_NOT_YET_SENT")

    def test_sigkill_remains_default_without_term_disposition_reads(self):
        for api in (30, 36):
            with tempfile.TemporaryDirectory() as directory:
                adb = self.fake_adb(directory)
                record = acceptance_runner.signal_process_death(adb, {"package": PACKAGE}, sample(1000, pid=77, birth=8), api, "kill")
                self.assertEqual(adb.calls, [("run-as", PACKAGE, "cat", "/proc/77/stat"),
                                             ("run-as", PACKAGE, "kill", "-9", "77")])
                self.assertEqual((record["backend"], record["signal"]), ("RUN_AS_SIGKILL", 9))
                self.assertNotIn("disposition", record)

    def test_explicit_term_trial_keeps_background_and_removal_oracles(self):
        with tempfile.TemporaryDirectory() as directory:
            adb = self.fake_adb(directory)
            observations = [sample(1100, pid=77, birth=8), sample(1200, pid=77, birth=8),
                            sample(1300), sample(1400, key=None)]
            with patch("adb_acceptance.start_trial", return_value=sample(1000, pid=77, birth=8)), patch(
                "adb_acceptance.deadline", return_value=1400
            ), patch("adb_acceptance.background_for_death", return_value={"verified": True}), patch(
                "adb_acceptance.observe", side_effect=observations
            ), patch("adb_acceptance.time.sleep"):
                try:
                    result = death_trial(adb, {"package": PACKAGE, "user": 0}, 30, False, "term-default")
                except InvalidEvidence as error:
                    self.fail(f"Valid default-disposition trial was rejected: {error}")
            self.assertEqual(result["signal_backend"]["backend"], "RUN_AS_SIGTERM_DEFAULT_DISPOSITION")
            self.assertEqual(adb.calls[-1], ("run-as", PACKAGE, "kill", "-15", "77"))
            self.assertEqual(result["background"], {"verified": True})
            self.assertEqual(result["removal"]["first_absent"]["before_ms"], 1400)
            self.assertEqual(result["removal"]["clock_precision_ms"], 10)


if __name__ == "__main__":
    unittest.main()
