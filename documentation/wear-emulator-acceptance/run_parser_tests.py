#!/usr/bin/env python3
"""Run the offline observer tests with named JUnit evidence for the mutation harness.

The suite is an exact identity inventory, not a count: EXPECTED_TESTS names every test id that
`unittest` discovery must find, and the run must leave a passing testcase for each of them. A
missing, renamed or extra test fails before anything runs, so a broken discovery pattern or a
half-deleted class cannot pass as "at least N tests" (same rule as .github/scripts/junit_identity.py).
Adding a test means adding its id here, deliberately.

Gradle runs this as `:app:wear:verifyEmulatorAcceptanceRunner`, an Exec task inside the root
`testDebugUnitTest`; it needs `python3` on PATH (documentation/ci-cd.md, documentation/testing.md).
"""
import argparse
from pathlib import Path
import time
import unittest
import xml.etree.ElementTree as ET

EXPECTED_TESTS = (
    "test_acceptance.ArtifactEvidenceTest.test_activity_observer_accepts_actual_equals_and_colon_but_not_empty_dump",
    "test_acceptance.ArtifactEvidenceTest.test_archive_never_extracts_parent_paths_links_or_duplicate_files",
    "test_acceptance.ArtifactEvidenceTest.test_duplicate_json_keys_rejected",
    "test_acceptance.ArtifactEvidenceTest.test_missing_reordered_or_repeated_timed_sections_rejected",
    "test_acceptance.ArtifactEvidenceTest.test_nested_receipts_are_hashed_and_changed_bytes_detectable",
    "test_acceptance.DeathPreparationTest.test_background_dump_requires_home_and_target_stopped_nonvisible",
    "test_acceptance.DeathPreparationTest.test_background_transition_cannot_change_process_notification_or_deadline",
    "test_acceptance.DeathPreparationTest.test_death_trial_backgrounds_before_signalling_the_verified_process",
    "test_acceptance.DeathSignalTest.test_birth_reuse_prevents_either_signal_backend",
    "test_acceptance.DeathSignalTest.test_caught_ignored_or_blocked_term_never_reaches_signal",
    "test_acceptance.DeathSignalTest.test_cli_defaults_to_kill_and_forwards_explicit_term",
    "test_acceptance.DeathSignalTest.test_default_disposition_is_preserved_as_explicit_backend_evidence",
    "test_acceptance.DeathSignalTest.test_explicit_term_trial_keeps_background_and_removal_oracles",
    "test_acceptance.DeathSignalTest.test_sigkill_remains_default_without_term_disposition_reads",
    "test_acceptance.DeathSignalTest.test_signal_error_has_no_second_signal_or_fallback",
    "test_acceptance.DeathSignalTest.test_term_is_rejected_for_wrong_api_or_retention_before_case_creation",
    "test_acceptance.DeathSignalTest.test_wrong_identity_or_corrupt_status_is_inconclusive",
    "test_acceptance.IndependentInvocationTest.test_failed_first_invocation_does_not_skip_remaining_fixtures_or_journeys",
    "test_acceptance.IndependentInvocationTest.test_partial_or_inconclusive_cell_cannot_be_overwritten_with_pass",
    "test_acceptance.InstrumentationEvidenceTest.test_actual_named_assertion_failure_is_fail",
    "test_acceptance.InstrumentationEvidenceTest.test_execution_errors_are_inconclusive_even_with_assertion_stack",
    "test_acceptance.InstrumentationEvidenceTest.test_failure_code_requires_top_level_assertion_stack",
    "test_acceptance.InstrumentationEvidenceTest.test_requires_exact_started_and_completed_method_and_final_code",
    "test_acceptance.InstrumentationEvidenceTest.test_skips_missing_methods_duplicate_completions_are_not_pass",
    "test_acceptance.LocaleSetupTest.test_effective_locale_requires_one_exact_matching_config",
    "test_acceptance.LocaleSetupTest.test_final_setting_drift_is_not_reported_as_configured",
    "test_acceptance.LocaleSetupTest.test_framework_restart_waits_for_committed_settings_locale",
    "test_acceptance.LocaleSetupTest.test_matching_user_property_and_effective_locale_do_not_restart",
    "test_acceptance.LocaleSetupTest.test_nonzero_user_is_rejected_before_locale_mutations",
    "test_acceptance.LocaleSetupTest.test_persisted_locale_accepts_observed_fragment_and_optional_namespace",
    "test_acceptance.LocaleSetupTest.test_persisted_locale_rejects_ambiguous_or_nonplain_values",
    "test_acceptance.LocaleSetupTest.test_uncommitted_locale_never_stops_the_framework",
    "test_acceptance.LocaleSetupTest.test_user_locale_is_corrected_even_when_property_matches",
    "test_acceptance.LocaleSetupTest.test_wrong_effective_locale_after_restart_cannot_be_configured",
    "test_acceptance.ManualRegistrationTest.test_changed_attachment_after_preflight_cannot_publish_a_receipt",
    "test_acceptance.ManualRegistrationTest.test_invalid_later_attachment_leaves_no_partial_case",
    "test_acceptance.ManualRegistrationTest.test_late_manual_io_failures_preserve_attempt_and_allow_retry",
    "test_acceptance.ManualRegistrationTest.test_manual_publication_refuses_a_new_empty_case",
    "test_acceptance.ManualRegistrationTest.test_report_keeps_unpublished_manual_attempts_visible",
    "test_acceptance.ManualRegistrationTest.test_valid_manual_artifacts_preserve_bytes_and_existing_case",
    "test_acceptance.MatrixEvidenceTest.test_all_required_lifecycle_and_manual_rows_remain_required",
    "test_acceptance.MatrixEvidenceTest.test_config_uses_actual_resources_and_rejects_matrix_drift",
    "test_acceptance.MatrixEvidenceTest.test_matrix_has_exact_sixteen_unique_cells",
    "test_acceptance.MatrixEvidenceTest.test_passive_trials_require_measured_integer_fifteen_second_timeout",
    "test_acceptance.MatrixEvidenceTest.test_ui_receipt_rejects_stale_token_wrong_method_or_preview_claim",
    "test_acceptance.MatrixEvidenceTest.test_unknown_na_or_skipped_rows_cannot_turn_into_green",
    "test_acceptance.MonotonicEvidenceTest.test_calibration_rejects_missing_stale_or_incompatible_clock",
    "test_acceptance.MonotonicEvidenceTest.test_elapsed_clock_requires_raw_centisecond_pair",
    "test_acceptance.MonotonicEvidenceTest.test_pid_starttime_parser_handles_spaces_and_parentheses",
    "test_acceptance.MonotonicEvidenceTest.test_poll_gaps_and_slow_commands_cannot_hide_removal_latency",
    "test_acceptance.MonotonicEvidenceTest.test_reboot_stale_time_and_partial_process_identity_are_inconclusive",
    "test_acceptance.MonotonicEvidenceTest.test_removal_before_deadline_is_failure_with_centisecond_precision",
    "test_acceptance.MonotonicEvidenceTest.test_removal_has_a_real_bracket_and_no_live_app",
    "test_acceptance.MonotonicEvidenceTest.test_removal_watchdog_requires_quantized_upper_bound",
    "test_acceptance.MonotonicEvidenceTest.test_retention_cannot_catch_up_after_stall_or_late_refresh",
    "test_acceptance.MonotonicEvidenceTest.test_retention_observer_rejects_more_than_500ms_but_keeps_refresh_budget",
    "test_acceptance.MonotonicEvidenceTest.test_watchdog_distinguishes_present_failure_from_unresolved_late_absence",
    "test_acceptance.MutationEvidenceTest.test_only_top_level_assertion_or_comparison_failures_credit_named_red",
    "test_acceptance.NotificationEvidenceTest.test_api30_user_all_list_and_colon_record_preserve_exact_target_identity",
    "test_acceptance.NotificationEvidenceTest.test_empty_success_differs_from_malformed_or_failed_list",
    "test_acceptance.NotificationEvidenceTest.test_reads_actual_long_dump_format_and_rejects_missing_duplicate_or_wrong_key",
    "test_acceptance.NotificationEvidenceTest.test_selects_exact_user_package_id_and_null_tag",
    "test_acceptance.OutputContainmentTest.test_case_parent_link_refuses_manual_and_automated_writes",
    "test_acceptance.OutputContainmentTest.test_existing_json_temporary_link_cannot_overwrite_external_file",
    "test_acceptance.OutputContainmentTest.test_inventory_rejects_file_and_directory_links_before_hashing",
)


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


def test_ids(suite):
    for item in suite:
        if isinstance(item, unittest.TestSuite):
            yield from test_ids(item)
        else:
            yield item.id()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    suite = unittest.defaultTestLoader.discover(str(Path(__file__).resolve().parent), pattern="test_*.py")
    discovered = sorted(test_ids(suite))
    expected = sorted(EXPECTED_TESTS)
    missing = sorted(set(expected) - set(discovered))
    extra = sorted(set(discovered) - set(expected))
    if missing or extra or len(discovered) != len(expected):
        raise SystemExit(
            "Parser suite identity mismatch: "
            f"missing={missing} extra={extra} discovered={len(discovered)} expected={len(expected)}. "
            "Update EXPECTED_TESTS deliberately."
        )
    XmlResult.xml = ET.Element("testsuite", name="WearAcceptanceParser")
    result = unittest.TextTestRunner(verbosity=2, resultclass=XmlResult).run(suite)
    for key, value in (("tests", result.testsRun), ("failures", len(result.failures)),
                       ("errors", len(result.errors)), ("skipped", len(result.skipped))):
        result.xml.set(key, str(value))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(result.xml).write(args.output, encoding="utf-8", xml_declaration=True)
    passed = {
        f"{case.get('classname')}.{case.get('name')}"
        for case in result.xml.findall("testcase") if len(case) == 0
    }
    unproven = sorted(set(expected) - passed)
    if unproven:
        print(f"Parser suite left {len(unproven)} expected test(s) without a passing testcase: {unproven}")
    raise SystemExit(0 if result.wasSuccessful() and not result.skipped and not unproven else 1)


if __name__ == "__main__":
    main()
