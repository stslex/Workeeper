#!/usr/bin/env python3
"""Android-device XML identity oracle for the KMP MVI smoke cases.

The result directory is the AGP-KMP path measured from a real connected run on the
Phase 7.3 implementation tree. Structural validation is shared with Native and host.
"""

from junit_identity import run_gate

MVI_PACKAGE = "io.github.stslex.workeeper.core.ui.mvi"

EXPECTED = [
    {
        "module": "core:ui:mvi (androidDeviceTest)",
        "results_dir": "core/ui/mvi/build/outputs/androidTest-results/connected/androidMain",
        "identities": [
            {
                "classname": f"{MVI_PACKAGE}.AppFeatureScopeTest",
                "name": "appFeatureProcessorResolvesAtActivityScope",
            },
            {
                "classname": f"{MVI_PACKAGE}.AppFeatureScopeTest",
                "name": "storeJobsAreDescendantsOfTheGenerationInjectedIntoTheStore",
            },
        ],
    },
]


def main() -> None:
    run_gate("MVI Android-device gate", EXPECTED)


if __name__ == "__main__":
    main()
