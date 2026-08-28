#!/usr/bin/env python3
"""Android-host XML identity oracle for `core:ui:mvi`, inside `Build and Unit Tests`.

The root `testDebugUnitTest` invocation stays; it is a broad run whose result
can be restored from the build cache. This gate is the liveness oracle for the
Store runtime specifically: a forced `:core:ui:mvi:testAndroidHostTest` must
have really executed, and these exact cases must be among what it executed.

Structural rules come from `junit_identity.py`, shared with the Native and
device gates. The JVM runner emits the bare fqcn as `classname` and suffixes
every method name with `()`; both are measured from real output, not guessed.

Run from the repository root, after the forced Gradle invocation:

    python3 .github/scripts/assert_mvi_host_identities.py
"""

from junit_identity import run_gate

MVI_PACKAGE = "io.github.stslex.workeeper.core.ui.mvi"


def host(classname: str, name: str) -> dict:
    return {"classname": classname, "name": f"{name}()"}


EXPECTED = [
    {
        "module": "core:ui:mvi (androidHostTest)",
        "results_dir": "core/ui/mvi/build/test-results/testAndroidHostTest",
        "identities": [
            # Generation join.
            host(
                f"{MVI_PACKAGE}.StoreGenerationJoinTest",
                "aStoreJobStartedViaLaunchDefaultIsJoinedByTheGenerationLifetime",
            ),
            # Post-dispose action rejection — the consume guard.
            host(
                f"{MVI_PACKAGE}.StoreGenerationJoinTest",
                "actionsAfterDisposalAreRejected",
            ),
            # Buffer-pressure exact delivery.
            host(
                f"{MVI_PACKAGE}.StoreEventPressureTest",
                "everyEventSubmittedUnderBufferPressureIsObservedExactlyOnce",
            ),
            # JVM ABI: no DefaultImpls holders, and the $default helpers stay on the
            # interfaces. Both halves are pinned, because they fail independently.
            host(f"{MVI_PACKAGE}.MviJvmAbiTest", "noMviInterfaceCarriesADefaultImplsHolder"),
            host(
                f"{MVI_PACKAGE}.MviJvmAbiTest",
                "defaultArgumentHelpersStayStaticMembersOfTheInterface",
            ),
            host(
                f"{MVI_PACKAGE}.MviJvmAbiTest",
                "publicMviAbiMatchesTheMeasuredManifest",
            ),
            # Android keeps real Firebase: the platform provider, and the delegation.
            host(
                f"{MVI_PACKAGE}.performance.AndroidPerformanceProviderTest",
                "theAndroidPlatformBackendIsTheFirebaseOneAndNotANoOp",
            ),
            host(
                f"{MVI_PACKAGE}.performance.AndroidPerformanceProviderTest",
                "theCommonFacadeRoutesEveryActionThroughTheAndroidFirebaseRouter",
            ),
            host(
                f"{MVI_PACKAGE}.performance.AndroidPerformanceProviderTest",
                "theComposableAndroidScreenProviderKeepsTheFirebaseAdapter",
            ),
        ],
    },
]


def main() -> None:
    run_gate("MVI Android-host gate", EXPECTED)


if __name__ == "__main__":
    main()
