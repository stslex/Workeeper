#!/usr/bin/env python3
"""Explicit source-topology oracle for shared KMP UI leaf modules.

The manifest is intentionally path-based rather than count-based: moving a reviewed file into a
different source set while adding a same-count replacement must fail. Add future shared UI leaves
as new manifest entries instead of weakening the checks for the first migrated leaf.

Run from the repository root:

    python3 .github/scripts/assert_kmp_ui_source_topology.py
"""

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


MODULES = {
    "core:ui:start-mode": {
        "root": Path("core/ui/start-mode"),
        "files": {
            "src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/start_mode/StartCardModeName.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/start_mode/StartCardModeSheet.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/start_mode/model/StartCardModeUi.kt",
            "src/commonMain/composeResources/values/strings.xml",
            "src/commonMain/composeResources/values-ru/strings.xml",
            "src/commonTest/kotlin/io/github/stslex/workeeper/core/ui/start_mode/model/StartCardModeCatalogTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/core/ui/start_mode/golden/StartCardModeSheetGoldenTest.kt",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.start_mode.golden_StartCardModeSheetGoldenTest_modeSheet_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.start_mode.golden_StartCardModeSheetGoldenTest_modeSheet_light.png",
            "src/iosTest/kotlin/io/github/stslex/workeeper/core/ui/start_mode/StartModeSceneIosTest.kt",
        },
        "kotlin_source_sets": {
            "commonMain",
            "commonTest",
            "androidHostTest",
            "iosTest",
        },
        "resource_dirs": {
            "src/commonMain/composeResources/values",
            "src/commonMain/composeResources/values-ru",
        },
    },
    "core:ui:plan-editor": {
        "root": Path("core/ui/plan-editor"),
        "files": {
            "src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/plan_editor/ExercisePickerBottomSheet.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/plan_editor/PlanEditorBody.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/plan_editor/PlanSetCard.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/plan_editor/TypeToggle.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/plan_editor/domain/PlanDraftReducer.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/plan_editor/model/ExercisePickerAction.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/plan_editor/model/ExercisePickerUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/plan_editor/model/ExerciseTypeUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/plan_editor/model/PlanDraftResult.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/plan_editor/model/PlanEditorBodyAction.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/plan_editor/model/PlanEditorUIMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/plan_editor/model/PlanSetUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/plan_editor/model/SetTypeUiModel.kt",
            "src/commonMain/composeResources/values/strings.xml",
            "src/commonMain/composeResources/values-ru/strings.xml",
            "src/commonTest/kotlin/io/github/stslex/workeeper/core/ui/plan_editor/domain/PlanDraftReducerTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/core/ui/plan_editor/golden/PlanEditorBodyGoldenTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/core/ui/plan_editor/golden/PlanSetCardReadOnlyGoldenTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/core/ui/plan_editor/golden/TypeToggleGoldenTest.kt",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.plan_editor.golden_PlanEditorBodyGoldenTest_emptyDraft_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.plan_editor.golden_PlanEditorBodyGoldenTest_emptyDraft_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.plan_editor.golden_PlanEditorBodyGoldenTest_weightedDraft_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.plan_editor.golden_PlanEditorBodyGoldenTest_weightedDraft_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.plan_editor.golden_PlanEditorBodyGoldenTest_weightlessDraft_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.plan_editor.golden_PlanEditorBodyGoldenTest_weightlessDraft_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.plan_editor.golden_PlanSetCardReadOnlyGoldenTest_readOnlyEmpty_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.plan_editor.golden_PlanSetCardReadOnlyGoldenTest_readOnlyEmpty_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.plan_editor.golden_PlanSetCardReadOnlyGoldenTest_readOnlyFiveGlyphWeight_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.plan_editor.golden_PlanSetCardReadOnlyGoldenTest_readOnlyFiveGlyphWeight_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.plan_editor.golden_PlanSetCardReadOnlyGoldenTest_readOnlyWeighted_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.plan_editor.golden_PlanSetCardReadOnlyGoldenTest_readOnlyWeighted_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.plan_editor.golden_PlanSetCardReadOnlyGoldenTest_readOnlyWeightless_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.plan_editor.golden_PlanSetCardReadOnlyGoldenTest_readOnlyWeightless_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.plan_editor.golden_TypeToggleGoldenTest_typeWeighted_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.plan_editor.golden_TypeToggleGoldenTest_typeWeighted_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.plan_editor.golden_TypeToggleGoldenTest_typeWeightless_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.plan_editor.golden_TypeToggleGoldenTest_typeWeightless_light.png",
            "src/iosTest/kotlin/io/github/stslex/workeeper/core/ui/plan_editor/PlanEditorSceneIosTest.kt",
        },
        "kotlin_source_sets": {
            "commonMain",
            "commonTest",
            "androidHostTest",
            "iosTest",
        },
        "resource_dirs": {
            "src/commonMain/composeResources/values",
            "src/commonMain/composeResources/values-ru",
        },
    },
    "feature:image-viewer": {
        "root": Path("feature/image-viewer"),
        "files": {
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/di/ImageViewerFeature.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/di/ImageViewerGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/di/ImageViewerHandlerStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/di/ImageViewerHandlerStoreImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/di/ImageViewerScope.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/mvi/handler/ClickHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/mvi/handler/CommonHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/mvi/handler/NavigationHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/mvi/store/ImageViewerStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/mvi/store/ImageViewerStoreImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/ui/ImageViewerGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/ui/ImageViewerScreen.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/image_viewer/ui/components/ZoomableImage.kt",
            "src/commonMain/composeResources/values/strings.xml",
            "src/commonMain/composeResources/values-ru/strings.xml",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/image_viewer/mvi/handler/ClickHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/image_viewer/mvi/handler/CommonHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/image_viewer/mvi/handler/NavigationHandlerTest.kt",
            "src/iosTest/kotlin/io/github/stslex/workeeper/feature/image_viewer/ImageViewerSceneIosTest.kt",
        },
        "kotlin_source_sets": {
            "commonMain",
            "commonTest",
            "iosTest",
        },
        "resource_dirs": {
            "src/commonMain/composeResources/values",
            "src/commonMain/composeResources/values-ru",
        },
    },
}

LEGACY_SOURCE_SETS = ("main", "test", "androidTest")

FORBIDDEN_IMPORT = re.compile(
    r"^\s*import\s+(android\.|androidx\.annotation(?:\.|\s|$)|"
    r"androidx\.compose\.ui\.res(?:\.|\s|$)|java\.|javax\.)",
    re.MULTILINE,
)

ANDROID_R_ACCESS = re.compile(r"(?<![A-Za-z0-9_])R\.")

PLAN_EDITOR_R_IMPORT = re.compile(
    r"^\s*import\s+io\.github\.stslex\.workeeper\.core\.ui\.plan_editor\.R(?:\s+as\s+\w+)?\s*$",
    re.MULTILINE,
)

CORE_PLAN_EDITOR_CATALOGS = {
    Path("core/ui/plan-editor/src/commonMain/composeResources/values/strings.xml"): {
        "core_ui_plan_editor_read_plan_empty": "This exercise has no default plan.",
    },
    Path("core/ui/plan-editor/src/commonMain/composeResources/values-ru/strings.xml"): {
        "core_ui_plan_editor_read_plan_empty": "У упражнения нет плана по умолчанию.",
    },
}

IMAGE_VIEWER_CATALOGS = {
    Path("feature/image-viewer/src/commonMain/composeResources/values/strings.xml"): {
        "feature_image_viewer_back": "Back",
        "feature_image_viewer_content_description": "Exercise image, full size",
        "feature_image_viewer_unavailable": "Image unavailable",
        "feature_image_viewer_menu": "Image actions",
        "feature_image_viewer_action_replace": "Replace photo",
        "feature_image_viewer_action_remove": "Remove photo",
    },
    Path("feature/image-viewer/src/commonMain/composeResources/values-ru/strings.xml"): {
        "feature_image_viewer_back": "Назад",
        "feature_image_viewer_content_description": "Полноразмерное фото упражнения",
        "feature_image_viewer_unavailable": "Фото недоступно",
        "feature_image_viewer_menu": "Действия с фото",
        "feature_image_viewer_action_replace": "Заменить фото",
        "feature_image_viewer_action_remove": "Удалить фото",
    },
}

EXPECTED_APP_DEPS_READERS = {
    Path("feature/all-exercises/src/main/kotlin/io/github/stslex/workeeper/feature/all_exercises/di/AllExercisesFeature.kt"): "AllExercisesGraph.Factory",
    Path("feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/di/AllTrainingsFeature.kt"): "AllTrainingsGraph.Factory",
    Path("feature/app-dialogs/impl/src/main/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/di/AppDialogFeature.kt"): "AppDialogGraph.Factory",
    Path("feature/archive/src/main/kotlin/io/github/stslex/workeeper/feature/archive/di/ArchiveFeature.kt"): "ArchiveGraph.Factory",
    Path("feature/exercise-chart/src/main/kotlin/io/github/stslex/workeeper/feature/exercise_chart/di/ExerciseChartFeature.kt"): "ExerciseChartGraph.Factory",
    Path("feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/di/ExerciseFeature.kt"): "ExerciseGraph.Factory",
    Path("feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/di/HomeFeature.kt"): "HomeGraph.Factory",
    Path("feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/di/LiveWorkoutFeature.kt"): "LiveWorkoutGraph.Factory",
    Path("feature/past-session/src/main/kotlin/io/github/stslex/workeeper/feature/past_session/di/PastSessionFeature.kt"): "PastSessionGraph.Factory",
    Path("feature/plan-editor/src/main/kotlin/io/github/stslex/workeeper/feature/plan_editor/di/PlanEditorFeature.kt"): "PlanEditorGraph.Factory",
    Path("feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/di/SettingsFeature.kt"): "SettingsGraph.Factory",
    Path("feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/di/SingleTrainingFeature.kt"): "SingleTrainingGraph.Factory",
}

FEATURE_PLAN_EDITOR_RESOURCES = {
    "feature_plan_editor_set_type_tooltip": (
        "Tap to cycle: warmup → work → failure → drop",
        "Нажмите, чтобы переключить: разминка → рабочий → отказ → дроп",
    ),
    "feature_plan_editor_type_change_weightless_title": (
        "Switch to weightless?",
        "Переключить на без веса?",
    ),
    "feature_plan_editor_type_change_weightless_body": (
        "Weight values from this exercise’s plans will be cleared. This cannot be undone.",
        "Значения веса из планов этого упражнения будут очищены. Это нельзя отменить.",
    ),
    "feature_plan_editor_type_change_weightless_impact": (
        "All plan weights cleared",
        "Все веса в планах очищены",
    ),
    "feature_plan_editor_type_change_weightless_confirm": ("Switch", "Переключить"),
}

FEATURE_EXERCISE_RESOURCES = {
    "feature_exercise_edit_plan_set_type_tooltip": (
        "Tap to cycle: warmup → work → failure → drop",
        "Нажмите, чтобы переключить: разминка → рабочий → отказ → дроп",
    ),
    "feature_exercise_edit_plan_type_change_weightless_title": (
        "Switch to weightless?",
        "Переключить на без веса?",
    ),
    "feature_exercise_edit_plan_type_change_weightless_body": (
        "Weight values from this exercise’s plans will be cleared. This cannot be undone.",
        "Значения веса из планов этого упражнения будут очищены. Это нельзя отменить.",
    ),
    "feature_exercise_edit_plan_type_change_weightless_impact": (
        "All plan weights cleared",
        "Все веса в планах очищены",
    ),
    "feature_exercise_edit_plan_type_change_weightless_confirm": ("Switch", "Переключить"),
    "feature_exercise_edit_plan_set_removed": ("Set removed", "Подход удалён"),
}

FEATURE_SINGLE_TRAINING_RESOURCES = {
    "feature_training_edit_plan_set_removed": ("Set removed", "Подход удалён"),
}

FEATURE_RESOURCE_OWNERS = {
    Path("feature/plan-editor/src/main/res"): FEATURE_PLAN_EDITOR_RESOURCES,
    Path("feature/exercise/src/main/res"): FEATURE_EXERCISE_RESOURCES,
    Path("feature/single-training/src/main/res"): FEATURE_SINGLE_TRAINING_RESOURCES,
}

FORMER_CROSS_MODULE_KEYS = {
    "core_ui_plan_editor_set_type_tooltip",
    "core_ui_plan_editor_type_change_weightless_title",
    "core_ui_plan_editor_type_change_weightless_body",
    "core_ui_plan_editor_type_change_weightless_impact",
    "core_ui_plan_editor_type_change_weightless_confirm",
    "core_ui_plan_editor_toast_set_removed",
}

FEATURE_PLAN_EDITOR_LEGACY_KEYS = {
    "core_ui_plan_editor_screen_title_format",
    "core_ui_plan_editor_screen_title_default",
    "core_ui_plan_editor_screen_back",
    "core_ui_plan_editor_screen_save",
    "core_ui_plan_editor_screen_cancel",
    "core_ui_plan_editor_error_load",
    "core_ui_plan_editor_error_save",
}


def files_below(root: Path) -> list[Path]:
    return sorted(path for path in root.rglob("*") if path.is_file()) if root.is_dir() else []


def kotlin_files(source_root: Path, source_set: str) -> list[Path]:
    root = source_root / source_set
    return sorted(root.rglob("*.kt")) if root.is_dir() else []


def source_files(suffix: str) -> list[Path]:
    return sorted(
        path
        for path in Path(".").rglob(f"*{suffix}")
        if "src" in path.parts
        and "build" not in path.parts
        and ".gradle" not in path.parts
        and ".git" not in path.parts
        and not any(part.startswith(".") for part in path.parts if part != ".")
    )


def read_strings(path: Path) -> dict[str, str]:
    root = ET.parse(path).getroot()
    return {
        element.attrib["name"]: element.text or ""
        for element in root.findall("string")
    }


def check_plan_editor_resources() -> list[str]:
    failures: list[str] = []
    catalogs = source_files("strings.xml")
    entries: dict[str, list[tuple[Path, str]]] = {}
    for catalog in catalogs:
        for key, value in read_strings(catalog).items():
            entries.setdefault(key, []).append((catalog, value))

    for catalog, expected in CORE_PLAN_EDITOR_CATALOGS.items():
        actual = read_strings(catalog) if catalog.is_file() else {}
        if actual != expected:
            failures.append(
                f"core:ui:plan-editor: exact CMP catalog mismatch in {catalog}; "
                f"expected={expected!r}, actual={actual!r}"
            )

    for owner_root, expected_resources in FEATURE_RESOURCE_OWNERS.items():
        owner_catalogs = [
            owner_root / "values" / "strings.xml",
            owner_root / "values-ru" / "strings.xml",
        ]
        for key, values in expected_resources.items():
            expected_entries = dict(zip(owner_catalogs, values))
            actual_entries = entries.get(key, [])
            if (
                len(actual_entries) != len(expected_entries)
                or dict(actual_entries) != expected_entries
            ):
                failures.append(
                    f"resource ownership mismatch for {key}; "
                    f"expected={expected_entries!r}, actual={actual_entries!r}"
                )

    for key in sorted(FORMER_CROSS_MODULE_KEYS):
        if key in entries:
            failures.append(
                f"former cross-module resource {key} still exists in {entries[key]!r}"
            )

    plan_editor_legacy_catalogs = {
        Path("feature/plan-editor/src/main/res/values/strings.xml"),
        Path("feature/plan-editor/src/main/res/values-ru/strings.xml"),
    }
    for key in sorted(FEATURE_PLAN_EDITOR_LEGACY_KEYS):
        actual_catalogs = {path for path, _ in entries.get(key, [])}
        if actual_catalogs != plan_editor_legacy_catalogs:
            failures.append(
                f"legacy feature plan-editor resource {key} has wrong owners; "
                f"expected={sorted(plan_editor_legacy_catalogs)!r}, "
                f"actual={sorted(actual_catalogs)!r}"
            )

    read_only_owners = {
        path for path, _ in entries.get("core_ui_plan_editor_read_plan_empty", [])
    }
    if read_only_owners != set(CORE_PLAN_EDITOR_CATALOGS):
        failures.append(
            "core_ui_plan_editor_read_plan_empty has wrong owners; "
            f"expected={sorted(CORE_PLAN_EDITOR_CATALOGS)!r}, "
            f"actual={sorted(read_only_owners)!r}"
        )

    for path in source_files(".kt"):
        source = path.read_text(encoding="utf-8")
        if PLAN_EDITOR_R_IMPORT.search(source):
            failures.append(f"{path}: imports the removed plan-editor Android R class")
        if "CoreEditorR" in source:
            failures.append(f"{path}: retains the removed CoreEditorR alias")

    build_source = Path("core/ui/plan-editor/build.gradle.kts").read_text(encoding="utf-8")
    expected_package = (
        'packageOfResClass = "io.github.stslex.workeeper.core.ui.plan_editor.resources"'
    )
    if expected_package not in build_source:
        failures.append("core:ui:plan-editor: generated resource package is not exact")
    if "publicResClass = true" in build_source:
        failures.append("core:ui:plan-editor: generated resource class must remain private")

    return failures


def compact(source: str) -> str:
    return re.sub(r"\s+", "", source)


def check_image_viewer_contract() -> list[str]:
    failures: list[str] = []

    for catalog, expected in IMAGE_VIEWER_CATALOGS.items():
        actual = read_strings(catalog) if catalog.is_file() else {}
        if actual != expected:
            failures.append(
                f"feature:image-viewer: exact private CMP catalog mismatch in {catalog}; "
                f"expected={expected!r}, actual={actual!r}"
            )

    build_path = Path("feature/image-viewer/build.gradle.kts")
    build_source = build_path.read_text(encoding="utf-8")
    required_build_fragments = [
        "alias(libs.plugins.convention.kmpComposeLibrary)",
        "alias(libs.plugins.metro)",
        'packageOfResClass = "io.github.stslex.workeeper.feature.image_viewer.resources"',
        "includeJavax()",
        'implementation(project(":core:core"))',
        'implementation(project(":core:ui:kit"))',
        'api(project(":core:ui:mvi"))',
        'api(project(":core:ui:navigation"))',
        "api(libs.cmp.ui)",
        "implementation(libs.coil.compose)",
        "implementation(libs.cmp.animation)",
        "implementation(libs.cmp.material.icons.extended)",
        "implementation(libs.cmp.ui.test)",
    ]
    for fragment in required_build_fragments:
        if build_source.count(fragment) != 1:
            failures.append(
                f"feature:image-viewer: build contract must contain {fragment!r} exactly once"
            )
    if build_source.count('implementation(kotlin("test"))') != 2:
        failures.append(
            "feature:image-viewer: kotlin(test) must exist exactly once in commonTest and iosTest"
        )
    for forbidden in (
        "convention.composeLibrary",
        "publicResClass",
        "androidTestImplementation",
        "debugImplementation",
        "coil-network-ktor3",
        "libs.bundles.android.test",
        "libs.androidx.compose.ui.test.manifest",
        "mockk",
        "robolectric",
        "paparazzi",
    ):
        if forbidden in build_source:
            failures.append(
                f"feature:image-viewer: forbidden build dependency/configuration remains: {forbidden}"
            )

    zoom_path = Path(
        "feature/image-viewer/src/commonMain/kotlin/io/github/stslex/workeeper/"
        "feature/image_viewer/ui/components/ZoomableImage.kt"
    )
    zoom_source = zoom_path.read_text(encoding="utf-8")
    exact_request = compact(
        """
        ImageRequest.Builder(LocalPlatformContext.current)
            .data(model)
            .crossfade(true)
            .build()
        """
    )
    if compact(zoom_source).count(exact_request) != 1:
        failures.append(
            "feature:image-viewer: Coil request must use exactly one "
            "LocalPlatformContext/data(model)/crossfade(true)/build() chain"
        )
    if zoom_source.count("ImageRequest.Builder(") != 1:
        failures.append("feature:image-viewer: expected exactly one ImageRequest.Builder call")
    if "import coil3.compose.LocalPlatformContext" not in zoom_source:
        failures.append("feature:image-viewer: LocalPlatformContext import is missing")

    required_root_fragments = {
        Path("app/common/src/main/kotlin/io/github/stslex/workeeper/app/common/di/AppRootDeps.kt"): [
            "val imageViewerGraphFactory: ImageViewerGraph.Factory",
        ],
        Path("app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt"): [
            "override val imageViewerGraphFactory: ImageViewerGraph.Factory",
        ],
        Path("app/common/src/main/kotlin/io/github/stslex/workeeper/App.kt"): [
            "if (admission.granted) {",
            "val deps = remember(currentPhase.id)",
            "(context.applicationContext as AppRootDepsHolder).appRootDeps()",
            "AppGenerationContent(deps)",
            "private fun AppGenerationContent(deps: AppRootDeps)",
            "commonDataStore = deps.commonDataStore",
            "navigatorEventBus = deps.navigatorEventBus",
            "imageViewerGraphFactory = deps.imageViewerGraphFactory",
        ],
        Path(
            "app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt"
        ): [
            "imageViewerGraphFactory: ImageViewerGraph.Factory",
            "imageViewerGraph(factory = imageViewerGraphFactory",
        ],
        Path(
            "feature/image-viewer/src/commonMain/kotlin/io/github/stslex/workeeper/"
            "feature/image_viewer/ui/ImageViewerGraph.kt"
        ): [
            "factory: ImageViewerGraph.Factory",
            "navComponentScreen(ImageViewerFeature(factory))",
        ],
        Path(
            "feature/image-viewer/src/commonMain/kotlin/io/github/stslex/workeeper/"
            "feature/image_viewer/di/ImageViewerFeature.kt"
        ): [
            "private val factory: ImageViewerGraph.Factory",
        ],
        Path(
            "app/app/src/test/kotlin/io/github/stslex/workeeper/di/"
            "ImageViewerExtensionIdentityTest.kt"
        ): [
            ".imageViewerGraphFactory",
        ],
    }
    for path, fragments in required_root_fragments.items():
        source = path.read_text(encoding="utf-8") if path.is_file() else ""
        compact_source = compact(source)
        for fragment in fragments:
            if compact(fragment) not in compact_source:
                failures.append(
                    f"feature:image-viewer: required root-factory flow is missing in {path}: "
                    f"{fragment!r}"
                )

    app_source = Path(
        "app/common/src/main/kotlin/io/github/stslex/workeeper/App.kt"
    ).read_text(encoding="utf-8")
    if app_source.count(".appRootDeps()") != 1:
        failures.append(
            "feature:image-viewer: the admitted composed region must resolve appRootDeps exactly once"
        )

    feature_source = Path(
        "feature/image-viewer/src/commonMain/kotlin/io/github/stslex/workeeper/"
        "feature/image_viewer/di/ImageViewerFeature.kt"
    ).read_text(encoding="utf-8")
    retained_factory = re.compile(
        r"rememberMetroStoreProcessor<ImageViewerStoreImpl>\s*\{\s*"
        r"factory\s*\.createImageViewerGraph\(screen\)\s*\.imageViewerStore\s*\}",
        re.DOTALL,
    )
    if len(retained_factory.findall(feature_source)) != 1:
        failures.append(
            "feature:image-viewer: factory invocation must occur exactly inside retained Store creation"
        )
    for forbidden in ("LocalContext", "appDeps<", "CompositionLocal"):
        if forbidden in feature_source:
            failures.append(
                f"feature:image-viewer: explicit feature factory flow forbids {forbidden}"
            )

    identity_source = Path(
        "app/app/src/test/kotlin/io/github/stslex/workeeper/di/"
        "ImageViewerExtensionIdentityTest.kt"
    ).read_text(encoding="utf-8")
    if identity_source.count(".imageViewerGraphFactory") != 3:
        failures.append(
            "feature:image-viewer: all three extension identities must use the root accessor"
        )
    if "asContribution" in identity_source:
        failures.append(
            "feature:image-viewer: extension identities must not bypass AppRootDeps via asContribution"
        )

    app_common_build = Path("app/common/build.gradle.kts").read_text(encoding="utf-8")
    if app_common_build.count('api(project(":feature:image-viewer"))') != 1:
        failures.append("app:common: image-viewer edge must be exactly one api dependency")
    if 'implementation(project(":feature:image-viewer"))' in app_common_build:
        failures.append("app:common: image-viewer edge must not remain implementation")

    app_build = Path("app/app/build.gradle.kts").read_text(encoding="utf-8")
    if app_build.count('implementation(project(":feature:image-viewer"))') != 1:
        failures.append("app:app: direct image-viewer aggregation edge must remain implementation")

    reader_pattern = re.compile(r"\bappDeps<\s*([A-Za-z0-9_.]+\.Factory)\s*>\s*\(")
    actual_readers: dict[Path, list[str]] = {}
    for path in source_files(".kt"):
        matches = reader_pattern.findall(path.read_text(encoding="utf-8"))
        if matches:
            actual_readers[path] = matches
    expected_readers = {
        path: [factory]
        for path, factory in EXPECTED_APP_DEPS_READERS.items()
    }
    if actual_readers != expected_readers:
        failures.append(
            "remaining Context.appDeps readers are not the exact 12 unported entries; "
            f"expected={expected_readers!r}, actual={actual_readers!r}"
        )

    return failures


def check_module(name: str, manifest: dict) -> list[str]:
    failures: list[str] = []
    root = manifest["root"]
    source_root = root / "src"

    if not source_root.is_dir():
        return [f"{name}: {source_root} does not exist; run from the repository root"]

    actual_files = {
        path.relative_to(root).as_posix()
        for path in files_below(source_root)
    }
    missing = sorted(manifest["files"] - actual_files)
    extra = sorted(actual_files - manifest["files"])
    if missing or extra:
        failures.append(f"{name}: source manifest mismatch; missing={missing}, extra={extra}")

    for legacy in LEGACY_SOURCE_SETS:
        stragglers = files_below(source_root / legacy)
        if stragglers:
            failures.append(
                f"{name}: legacy source set src/{legacy} still contains "
                f"{[path.relative_to(root).as_posix() for path in stragglers]}"
            )

    kotlin_by_source_set = {
        path.name: kotlin_files(source_root, path.name)
        for path in sorted(source_root.iterdir())
        if path.is_dir() and kotlin_files(source_root, path.name)
    }
    unexpected_source_sets = sorted(
        set(kotlin_by_source_set) - manifest["kotlin_source_sets"]
    )
    for source_set in unexpected_source_sets:
        failures.append(
            f"{name}: unexpected Kotlin-bearing source set src/{source_set}: "
            f"{[path.relative_to(root).as_posix() for path in kotlin_by_source_set[source_set]]}"
        )

    for relative in sorted(manifest["resource_dirs"]):
        resource_dir = root / relative
        if not resource_dir.is_dir():
            failures.append(f"{name}: required CMP resource directory is missing: {relative}")

    android_resources = files_below(source_root / "main" / "res")
    if android_resources:
        failures.append(
            f"{name}: Android resources remain under src/main/res: "
            f"{[path.relative_to(root).as_posix() for path in android_resources]}"
        )

    for source_set in ("commonMain", "iosMain"):
        for path in kotlin_files(source_root, source_set):
            source = path.read_text(encoding="utf-8")
            for match in FORBIDDEN_IMPORT.finditer(source):
                failures.append(
                    f"{name}: {path.relative_to(root)} imports a platform API in "
                    f"{source_set}: {match.group(0).strip()!r}"
                )
            if ANDROID_R_ACCESS.search(source):
                failures.append(
                    f"{name}: {path.relative_to(root)} uses Android R from {source_set}; "
                    "use the generated Compose Res class"
                )

    return failures


def main() -> None:
    failures: list[str] = []
    for name, manifest in MODULES.items():
        failures.extend(check_module(name, manifest))
    failures.extend(check_plan_editor_resources())
    failures.extend(check_image_viewer_contract())

    if failures:
        raise SystemExit(
            "shared KMP UI topology gate FAILED:\n"
            + "\n".join(f"  {failure}" for failure in failures)
        )

    print("shared KMP UI topology gate live:")
    for name, manifest in MODULES.items():
        print(f"  {name}: {len(manifest['files'])} exact source/resource/test files")
        for path in sorted(manifest["files"]):
            print(f"    {path}")
    print(
        "  legacy source sets empty; common/native production has no "
        "Android/Java/Javax/AndroidX-annotation API"
    )
    print("  plan-editor resource ownership and exact EN/RU values are canonical")
    print("  image-viewer resources, Coil request, root factory flow, and 12 readers are exact")


if __name__ == "__main__":
    sys.exit(main())
