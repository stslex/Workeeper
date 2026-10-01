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
    "feature:plan-editor": {
        "root": Path("feature/plan-editor"),
        "files": {
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/di/PlanEditorFeature.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/di/PlanEditorGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/di/PlanEditorHandlerStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/di/PlanEditorHandlerStoreImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/di/PlanEditorScope.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/domain/PlanEditorInteractor.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/domain/PlanEditorInteractorImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/domain/mapper/PlanEditorDomainMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/domain/model/ExerciseTypeDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/domain/model/PlanEditorLoadResult.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/domain/model/PlanSetDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/domain/model/SetTypeDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/PlanEditorGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/PlanEditorScreen.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/mapper/PlanEditorMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/mvi/handler/ClickHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/mvi/handler/CommonHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/mvi/handler/EditorHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/mvi/handler/InputHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/mvi/handler/NavigationHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/mvi/store/DialogState.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/mvi/store/PlanEditorStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/mvi/store/PlanEditorStoreImpl.kt",
            "src/commonMain/composeResources/values/strings.xml",
            "src/commonMain/composeResources/values-ru/strings.xml",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/plan_editor/mappers/PlanEditorMapperTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/plan_editor/model/SetTypeUiModelTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/plan_editor/mvi/handler/ClickHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/plan_editor/mvi/handler/CommonHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/plan_editor/mvi/handler/NavigationHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/plan_editor/ui/mvi/store/PlanEditorStateRouteArgTest.kt",
            "src/iosTest/kotlin/io/github/stslex/workeeper/feature/plan_editor/PlanEditorFeatureSceneIosTest.kt",
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
    "feature:archive": {
        "root": Path("feature/archive"),
        "files": {
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/di/ArchiveFeature.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/di/ArchiveGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/di/ArchiveHandlerStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/di/ArchiveHandlerStoreImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/di/ArchiveScope.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/domain/ArchiveInteractor.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/domain/ArchiveInteractorImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/domain/mapper/ArchivedItemDomainMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/domain/model/ArchivedItem.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/domain/model/ExerciseTypeDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/mvi/handler/ArchiveClickHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/mvi/handler/ArchiveNavigationHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/mvi/handler/ArchivePagingHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/mvi/mapper/ArchiveUiMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/mvi/model/ArchivedItemUi.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/mvi/store/ArchiveStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/mvi/store/ArchiveStoreImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/ui/ArchiveGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/ui/ArchiveScreen.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/ui/components/ArchiveBody.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/ui/components/ArchiveListSurface.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/ui/components/ArchivedItemRow.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/ui/components/PagingTailKind.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/ui/components/PagingTails.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/ui/components/PermanentDeleteDialog.kt",
            "src/commonMain/composeResources/values-ru/strings.xml",
            "src/commonMain/composeResources/values/strings.xml",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/archive/mvi/handler/ArchiveClickHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/archive/mvi/handler/ArchivePagingHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/archive/mvi/mapper/ArchiveMetaLineTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/archive/ui/components/ArchiveListSurfaceTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/archive/ui/components/PagingTailKindTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/archive/golden/ArchiveGoldenTest.kt",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_pagingError_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_pagingError_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_pagingLoading_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_pagingLoading_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_rowClamped_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_rowClamped_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_rowExercise_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_rowExercise_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_rowTraining_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_rowTraining_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_screenExercisesNoRows_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_screenExercisesNoRows_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_screenTrainingsNoRows_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.archive.golden_ArchiveGoldenTest_screenTrainingsNoRows_light.png",
            "src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/archive/ArchiveScreenTest.kt",
            "src/iosTest/kotlin/io/github/stslex/workeeper/feature/archive/ArchiveFeatureSceneIosTest.kt",
        },
        "kotlin_source_sets": {
            "commonMain",
            "commonTest",
            "androidHostTest",
            "androidDeviceTest",
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
    Path("feature/exercise-chart/src/main/kotlin/io/github/stslex/workeeper/feature/exercise_chart/di/ExerciseChartFeature.kt"): "ExerciseChartGraph.Factory",
    Path("feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/di/ExerciseFeature.kt"): "ExerciseGraph.Factory",
    Path("feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/di/HomeFeature.kt"): "HomeGraph.Factory",
    Path("feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/di/LiveWorkoutFeature.kt"): "LiveWorkoutGraph.Factory",
    Path("feature/past-session/src/main/kotlin/io/github/stslex/workeeper/feature/past_session/di/PastSessionFeature.kt"): "PastSessionGraph.Factory",
    Path("feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/di/SettingsFeature.kt"): "SettingsGraph.Factory",
    Path("feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/di/SingleTrainingFeature.kt"): "SingleTrainingGraph.Factory",
}

FEATURE_PLAN_EDITOR_RESOURCES = {
    "core_ui_plan_editor_screen_title_format": ("Edit plan: %1$s", "План: %1$s"),
    "core_ui_plan_editor_screen_title_default": ("Edit plan", "План"),
    "core_ui_plan_editor_screen_back": ("Back", "Назад"),
    "core_ui_plan_editor_screen_save": ("Save", "Сохранить"),
    "core_ui_plan_editor_screen_cancel": ("Cancel", "Отмена"),
    "core_ui_plan_editor_error_load": (
        "Failed to load the plan.",
        "Не удалось загрузить план.",
    ),
    "core_ui_plan_editor_error_save": (
        "Failed to save the plan.",
        "Не удалось сохранить план.",
    ),
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
    Path("feature/plan-editor/src/commonMain/composeResources"): FEATURE_PLAN_EDITOR_RESOURCES,
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

    feature_catalogs = (
        Path("feature/plan-editor/src/commonMain/composeResources/values/strings.xml"),
        Path("feature/plan-editor/src/commonMain/composeResources/values-ru/strings.xml"),
    )
    for locale_index, catalog in enumerate(feature_catalogs):
        expected = {
            key: values[locale_index]
            for key, values in FEATURE_PLAN_EDITOR_RESOURCES.items()
        }
        actual = read_strings(catalog) if catalog.is_file() else {}
        if actual != expected:
            failures.append(
                f"feature:plan-editor: exact private CMP catalog mismatch in {catalog}; "
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
        Path("feature/plan-editor/src/commonMain/composeResources/values/strings.xml"),
        Path("feature/plan-editor/src/commonMain/composeResources/values-ru/strings.xml"),
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


def braced_call_bodies(source: str, call: str) -> list[str]:
    """Return balanced lambda bodies for a literal ``call {`` sequence."""
    bodies: list[str] = []
    cursor = 0
    marker = re.compile(rf"\b{re.escape(call)}\s*\{{")
    while match := marker.search(source, cursor):
        start = source.index("{", match.start())
        depth = 0
        for index in range(start, len(source)):
            if source[index] == "{":
                depth += 1
            elif source[index] == "}":
                depth -= 1
                if depth == 0:
                    bodies.append(source[start + 1:index])
                    cursor = index + 1
                    break
        else:
            break
    return bodies


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
            "remaining Context.appDeps readers are not the exact 10 unported entries; "
            f"expected={expected_readers!r}, actual={actual_readers!r}"
        )

    return failures


def check_plan_editor_feature_contract() -> list[str]:
    failures: list[str] = []
    root = Path("feature/plan-editor")

    build_path = root / "build.gradle.kts"
    build_source = build_path.read_text(encoding="utf-8")
    required_build_fragments = [
        "alias(libs.plugins.convention.kmpComposeLibrary)",
        "alias(libs.plugins.metro)",
        'packageOfResClass = "io.github.stslex.workeeper.feature.plan_editor.resources"',
        "includeJavax()",
        'implementation(project(":core:core"))',
        'implementation(project(":core:ui:kit"))',
        'api(project(":core:ui:mvi"))',
        'api(project(":core:ui:navigation"))',
        'api(project(":core:ui:plan-editor"))',
        'implementation(project(":core:data:database"))',
        'implementation(project(":core:data:exercise"))',
        "api(libs.cmp.ui)",
        "api(libs.kotlinx.collections.immutable)",
        "implementation(libs.coroutine.test)",
        "implementation(libs.cmp.ui.test)",
    ]
    for fragment in required_build_fragments:
        if build_source.count(fragment) != 1:
            failures.append(
                f"feature:plan-editor: build contract must contain {fragment!r} exactly once"
            )
    if build_source.count('implementation(kotlin("test"))') != 2:
        failures.append(
            "feature:plan-editor: kotlin(test) must exist exactly once in commonTest and iosTest"
        )
    for forbidden in (
        "convention.composeLibrary",
        "publicResClass",
        "androidTestImplementation",
        "debugImplementation",
        "libs.cmp.uiBackhandler",
        "libs.androidx.activity.compose",
        "libs.bundles.android.test",
        "libs.androidx.compose.ui.test.manifest",
        "serialization.json",
        "junit",
        "mockk",
        "robolectric",
        "paparazzi",
    ):
        if forbidden in build_source:
            failures.append(
                f"feature:plan-editor: forbidden build dependency/configuration remains: {forbidden}"
            )

    common_main = root / "src/commonMain/kotlin"
    common_sources = {
        path: path.read_text(encoding="utf-8")
        for path in sorted(common_main.rglob("*.kt"))
    }
    forbidden_common_tokens = (
        "androidx.activity.compose.BackHandler",
        "androidx.compose.ui.platform.LocalContext",
        "androidx.compose.ui.res.",
        "ResourceWrapper",
        "appDeps<",
        "CompositionLocal",
        "ServiceLocator",
        "FactoryRegistry",
    )
    for path, source in common_sources.items():
        for token in forbidden_common_tokens:
            if token in source:
                failures.append(
                    f"feature:plan-editor: {path.relative_to(root)} contains forbidden "
                    f"platform/lookup API {token!r}"
                )
        if re.search(r"^\s*(?:expect|actual)\s+", source, re.MULTILINE):
            failures.append(
                f"feature:plan-editor: {path.relative_to(root)} contains a forbidden expect/actual shim"
            )

    common_test = root / "src/commonTest/kotlin"
    for path in sorted(common_test.rglob("*.kt")):
        source = path.read_text(encoding="utf-8")
        for forbidden in ("org.junit", "io.mockk", "@Disabled", "@Ignore"):
            if forbidden in source:
                failures.append(
                    f"feature:plan-editor: {path.relative_to(root)} contains forbidden test API "
                    f"{forbidden!r}"
                )

    graph_path = common_main / "io/github/stslex/workeeper/feature/plan_editor/ui/PlanEditorGraph.kt"
    graph_source = common_sources[graph_path]
    screen_path = common_main / "io/github/stslex/workeeper/feature/plan_editor/ui/PlanEditorScreen.kt"
    screen_source = common_sources[screen_path]
    click_path = (
        common_main
        / "io/github/stslex/workeeper/feature/plan_editor/ui/mvi/handler/ClickHandler.kt"
    )
    click_source = common_sources[click_path]
    state_path = (
        common_main
        / "io/github/stslex/workeeper/feature/plan_editor/ui/mvi/store/PlanEditorStore.kt"
    )
    state_source = common_sources[state_path]
    dialog_path = (
        common_main
        / "io/github/stslex/workeeper/feature/plan_editor/ui/mvi/store/DialogState.kt"
    )
    dialog_source = common_sources[dialog_path]

    screen_resource_keys = set(FEATURE_PLAN_EDITOR_RESOURCES) - {
        "core_ui_plan_editor_error_load",
        "core_ui_plan_editor_error_save",
    }
    for key in sorted(screen_resource_keys):
        if screen_source.count(key) != 2:
            failures.append(
                f"feature:plan-editor: composable must import and resolve {key} exactly once"
            )
    for key in ("core_ui_plan_editor_error_load", "core_ui_plan_editor_error_save"):
        if graph_source.count(key) != 2:
            failures.append(
                f"feature:plan-editor: graph composable must import and resolve {key} exactly once"
            )
    for path, source in common_sources.items():
        if "/handler/" in path.as_posix() or path in {state_path, dialog_path}:
            for key in FEATURE_PLAN_EDITOR_RESOURCES:
                if key in source:
                    failures.append(
                        f"feature:plan-editor: feature copy {key} must be resolved in a composable, "
                        f"not {path.relative_to(root)}"
                    )

    error_block = re.search(
        r"enum class ErrorType(?P<header>\s*\([^)]*\))?\s*\{(?P<body>[^}]*)\}",
        state_source,
    )
    if error_block is None:
        failures.append("feature:plan-editor: ErrorType enum is missing")
    else:
        variants = re.findall(r"^\s*(\w+)(?:\([^)]*\))?,?\s*$", error_block.group("body"), re.MULTILINE)
        if (
            variants != ["LoadFailed", "SaveFailed"]
            or error_block.group("header") is not None
            or "(" in error_block.group("body")
        ):
            failures.append(
                "feature:plan-editor: ErrorType must remain payload-free LoadFailed, SaveFailed"
            )
    dialog_variants = re.findall(r"data object (\w+)\s*:\s*DialogState", dialog_source)
    if dialog_variants != ["Hidden", "DiscardConfirm", "TypeChangeConfirm"]:
        failures.append(
            "feature:plan-editor: DialogState must remain three ordered payload-free objects"
        )
    if re.search(r"data class\s+\w+\s*\([^)]*\)\s*:\s*DialogState", dialog_source):
        failures.append("feature:plan-editor: DialogState variants must not carry payloads")

    handler_paths = sorted((common_main / "io/github/stslex/workeeper/feature/plan_editor/ui/mvi/handler").glob("*.kt"))
    update_count = 0
    for path in handler_paths:
        source = common_sources[path]
        bodies = braced_call_bodies(source, "updateState")
        update_count += len(bodies)
        for body in bodies:
            if ".copy(" not in compact(body):
                failures.append(
                    f"feature:plan-editor: updateState in {path.relative_to(root)} must return a State copy"
                )
            for side_effect in ("consume(", "consumeOnMain(", "sendEvent(", "interactor.", "stringResource("):
                if side_effect in body:
                    failures.append(
                        f"feature:plan-editor: updateState in {path.relative_to(root)} contains "
                        f"side effect {side_effect!r}"
                    )
    if update_count == 0:
        failures.append("feature:plan-editor: no updateState lambdas were inspected")

    if graph_source.count("import androidx.compose.ui.backhandler.BackHandler") != 1:
        failures.append("feature:plan-editor: portable BackHandler import must exist exactly once")
    back_handler = compact(
        """
        BackHandler(enabled = state.interceptBack) {
            processor.consume(Action.Click.OnBackClick)
        }
        """
    )
    if compact(graph_source).count(back_handler) != 1:
        failures.append(
            "feature:plan-editor: portable BackHandler must dispatch exact OnBackClick action"
        )

    if screen_source.count("@Preview(") != 2:
        failures.append("feature:plan-editor: exactly two portable previews are required")
    preview_fragments = (
        '@Preview(name = "Light")',
        "PlanEditorScreenLightPreview()",
        "PlanEditorScreenPreview(themeMode = ThemeMode.LIGHT)",
        '@Preview(name = "Dark")',
        "PlanEditorScreenDarkPreview()",
        "PlanEditorScreenPreview(themeMode = ThemeMode.DARK)",
    )
    for fragment in preview_fragments:
        if screen_source.count(fragment) != 1:
            failures.append(
                f"feature:plan-editor: two-preview contract requires {fragment!r} exactly once"
            )

    required_root_fragments = {
        Path("app/common/src/main/kotlin/io/github/stslex/workeeper/app/common/di/AppRootDeps.kt"): [
            "val planEditorGraphFactory: PlanEditorGraph.Factory",
        ],
        Path("app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt"): [
            "override val planEditorGraphFactory: PlanEditorGraph.Factory",
        ],
        Path("app/common/src/main/kotlin/io/github/stslex/workeeper/App.kt"): [
            "if (admission.granted) {",
            "val deps = remember(currentPhase.id)",
            "(context.applicationContext as AppRootDepsHolder).appRootDeps()",
            "AppGenerationContent(deps)",
            "private fun AppGenerationContent(deps: AppRootDeps)",
            "planEditorGraphFactory = deps.planEditorGraphFactory",
        ],
        Path("app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt"): [
            "planEditorGraphFactory: PlanEditorGraph.Factory",
            "planEditorGraph(",
            "factory = planEditorGraphFactory",
        ],
        graph_path: [
            "factory: PlanEditorGraph.Factory",
            "navScreen<Screen.PlanEditor.Existing> { screen ->",
            "PlanEditorFeature(factory).processor(screen)",
        ],
        Path(
            "feature/plan-editor/src/commonMain/kotlin/io/github/stslex/workeeper/"
            "feature/plan_editor/di/PlanEditorFeature.kt"
        ): [
            "private val factory: PlanEditorGraph.Factory",
            "factory.createPlanEditorGraph(screen)",
        ],
        Path(
            "feature/plan-editor/src/commonMain/kotlin/io/github/stslex/workeeper/"
            "feature/plan_editor/ui/mvi/store/PlanEditorStoreImpl.kt"
        ): [
            "initialState = screen.toInitialState()",
        ],
        Path(
            "feature/plan-editor/src/commonMain/kotlin/io/github/stslex/workeeper/"
            "feature/plan_editor/ui/mvi/handler/NavigationHandler.kt"
        ): [
            "destination = Screen.PlanEditor::class",
            "result = true",
        ],
        Path("app/app/src/test/kotlin/io/github/stslex/workeeper/di/PlanEditorExtensionIdentityTest.kt"): [
            "planEditorGraphFactory.createPlanEditorGraph(screen)",
        ],
    }
    for path, fragments in required_root_fragments.items():
        source = path.read_text(encoding="utf-8") if path.is_file() else ""
        compact_source = compact(source)
        for fragment in fragments:
            if compact_source.count(compact(fragment)) != 1:
                failures.append(
                    f"feature:plan-editor: exact root-factory flow requires {fragment!r} "
                    f"once in {path}"
                )

    feature_path = (
        root
        / "src/commonMain/kotlin/io/github/stslex/workeeper/feature/plan_editor/di/PlanEditorFeature.kt"
    )
    feature_source = common_sources[feature_path]
    retained_factory = compact(
        """
        rememberMetroStoreProcessor<PlanEditorStoreImpl> {
            factory.createPlanEditorGraph(screen)
                .planEditorStore
        }
        """
    )
    if compact(feature_source).count(retained_factory) != 1:
        failures.append(
            "feature:plan-editor: factory invocation must occur exactly inside retained Store creation"
        )
    if feature_source.count("createPlanEditorGraph(") != 1:
        failures.append(
            "feature:plan-editor: createPlanEditorGraph(screen) must occur once in production feature"
        )
    for source in (graph_source, feature_source):
        if re.search(r"(?:factory|planEditorGraphFactory)\s*:\s*PlanEditorGraph\.Factory\s*[?=]", source):
            failures.append("feature:plan-editor: factory parameters must be required and non-null")

    app_source = Path("app/common/src/main/kotlin/io/github/stslex/workeeper/App.kt").read_text(
        encoding="utf-8"
    )
    if app_source.count(".appRootDeps()") != 1:
        failures.append(
            "feature:plan-editor: admitted composed region must resolve appRootDeps exactly once"
        )

    identity_path = Path(
        "app/app/src/test/kotlin/io/github/stslex/workeeper/di/PlanEditorExtensionIdentityTest.kt"
    )
    identity_source = identity_path.read_text(encoding="utf-8")
    if identity_source.count(".planEditorGraphFactory") != 0:
        failures.append(
            "feature:plan-editor: extension identity accessor must use receiver-local root property"
        )
    if identity_source.count("planEditorGraphFactory.createPlanEditorGraph(screen)") != 1:
        failures.append(
            "feature:plan-editor: extension identities must share one explicit root accessor helper"
        )
    if "asContribution" in identity_source:
        failures.append(
            "feature:plan-editor: extension identities must not bypass AppRootDeps via asContribution"
        )

    app_common_build = Path("app/common/build.gradle.kts").read_text(encoding="utf-8")
    if app_common_build.count('api(project(":feature:plan-editor"))') != 1:
        failures.append("app:common: plan-editor edge must be exactly one api dependency")
    if 'implementation(project(":feature:plan-editor"))' in app_common_build:
        failures.append("app:common: plan-editor edge must not remain implementation")

    app_build = Path("app/app/build.gradle.kts").read_text(encoding="utf-8")
    if app_build.count('implementation(project(":feature:plan-editor"))') != 1:
        failures.append("app:app: direct plan-editor aggregation edge must remain implementation")

    return failures


ARCHIVE_ROOT = Path("feature/archive")

ARCHIVE_COMMON_MAIN = ARCHIVE_ROOT / "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive"

ARCHIVE_CATALOG_PATHS = (
    ARCHIVE_ROOT / "src/commonMain/composeResources/values/strings.xml",
    ARCHIVE_ROOT / "src/commonMain/composeResources/values-ru/strings.xml",
)

# Exact private EN/RU catalog, in file order: (tag, name, EN value, RU value). Plurals carry
# ordered (quantity, value) pairs. Identifier set, order, placeholders and plural categories are
# all contractual (kmp-phase-7-8-archive-feature.md §3.4).
ARCHIVE_RESOURCES = (
    ("string", "feature_archive_title", "Archive", "Архив"),
    ("string", "feature_archive_action_more", "More", "Ещё"),
    ("string", "feature_archive_segment_exercises", "Exercises (%1$d)", "Упражнения (%1$d)"),
    ("string", "feature_archive_segment_trainings", "Trainings (%1$d)", "Тренировки (%1$d)"),
    ("string", "feature_archive_action_restore", "Restore", "Восстановить"),
    ("string", "feature_archive_action_permanent_delete", "Delete permanently", "Удалить навсегда"),
    ("string", "feature_archive_kind_exercise", "exercise", "упражнение"),
    ("string", "feature_archive_kind_training", "training", "тренировка"),
    ("string", "feature_archive_label_archived", "archived", "в архиве"),
    (
        "string",
        "feature_archive_label_archived_since_format",
        "archived since %1$s",
        "в архиве с %1$s",
    ),
    ("string", "feature_archive_meta_separator", "·", "·"),
    ("string", "feature_archive_empty_headline", "Nothing archived", "Архив пуст"),
    (
        "string",
        "feature_archive_empty_supporting_exercises",
        "Archived exercises appear here for restore or permanent delete.",
        "Здесь будут архивированные упражнения — для восстановления или удаления навсегда.",
    ),
    (
        "string",
        "feature_archive_empty_supporting_trainings",
        "Archived trainings appear here for restore or permanent delete.",
        "Здесь будут архивированные тренировки — для восстановления или удаления навсегда.",
    ),
    (
        "string",
        "feature_archive_dialog_permanent_delete_title",
        "Delete ‘%1$s’ permanently?",
        "Удалить «%1$s» навсегда?",
    ),
    (
        "string",
        "feature_archive_dialog_permanent_delete_body_no_history",
        "This action cannot be undone.",
        "Это действие нельзя отменить.",
    ),
    (
        "string",
        "feature_archive_dialog_impact_summary_empty",
        "No session history affected",
        "Сессии истории не затронуты",
    ),
    ("string", "feature_archive_dialog_confirm_delete", "Delete", "Удалить"),
    ("string", "feature_archive_snackbar_restored_format", "%1$s restored", "«%1$s» восстановлено"),
    (
        "string",
        "feature_archive_snackbar_deleted_format",
        "%1$s permanently deleted",
        "«%1$s» удалено навсегда",
    ),
    ("string", "feature_archive_snackbar_undo", "Undo", "Отменить"),
    (
        "plurals",
        "feature_archive_session_count",
        (("one", "%d session"), ("other", "%d sessions")),
        (
            ("one", "%d сессия"),
            ("few", "%d сессии"),
            ("many", "%d сессий"),
            ("other", "%d сессии"),
        ),
    ),
    (
        "plurals",
        "feature_archive_dialog_permanent_delete_body_with_history",
        (
            ("one", "%1$d session of history will also be deleted. This action cannot be undone."),
            ("other", "%1$d sessions of history will also be deleted. This action cannot be undone."),
        ),
        (
            ("one", "Также будет удалена %1$d сессия истории. Это действие нельзя отменить."),
            ("few", "Также будут удалены %1$d сессии истории. Это действие нельзя отменить."),
            ("many", "Также будет удалено %1$d сессий истории. Это действие нельзя отменить."),
            ("other", "Также будет удалено %1$d сессии истории. Это действие нельзя отменить."),
        ),
    ),
    ("string", "feature_archive_paging_loading", "Loading", "Загружаю"),
    ("string", "feature_archive_paging_error", "Couldn’t load more", "Не удалось загрузить дальше"),
    ("string", "feature_archive_paging_retry", "Retry", "Повторить"),
    ("string", "feature_archive_refresh_error", "Couldn’t load the archive", "Не удалось загрузить архив"),
)

# Store State stays semantic: plain strings and counts, never a generated handle or an Int id.
ARCHIVE_STATE_FIELDS = (
    ("selectedSegment", "Segment"),
    ("exerciseCount", "Int"),
    ("trainingCount", "Int"),
    ("exerciseSegmentLabel", "String"),
    ("trainingSegmentLabel", "String"),
    ("archivedExercisesPaging", "PagingUiState<PagingData<ArchivedItemUi.Exercise>>"),
    ("archivedTrainingsPaging", "PagingUiState<PagingData<ArchivedItemUi.Training>>"),
    ("pendingDeleteImpact", "Int?"),
    ("pendingDeleteTarget", "ArchivedItem?"),
    ("deleteImpactLoading", "Boolean"),
)

# The only suppressions the target may carry: two inherited production annotations and the three
# Native test-name annotations §4.4 authorizes. Anything else is a new suppression.
ARCHIVE_SUPPRESSIONS = {
    "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/di/ArchiveFeature.kt": [
        '"UNCHECKED_CAST"',
    ],
    "src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/domain/ArchiveInteractor.kt": [
        '"TooManyFunctions"',
    ],
    "src/commonTest/kotlin/io/github/stslex/workeeper/feature/archive/mvi/mapper/ArchiveMetaLineTest.kt": [
        '"INVALID_CHARACTERS_NATIVE_ERROR"',
    ],
    "src/commonTest/kotlin/io/github/stslex/workeeper/feature/archive/ui/components/ArchiveListSurfaceTest.kt": [
        '"INVALID_CHARACTERS_NATIVE_ERROR"',
    ],
    "src/commonTest/kotlin/io/github/stslex/workeeper/feature/archive/ui/components/PagingTailKindTest.kt": [
        '"INVALID_CHARACTERS_NATIVE_ERROR"',
    ],
}

# The exact portable test-name inventory: 25 inherited identities plus the one Native scene.
ARCHIVE_TEST_NAMES = {
    "src/commonTest/kotlin/io/github/stslex/workeeper/feature/archive/mvi/handler/ArchiveClickHandlerTest.kt": [
        "OnSegmentChange updates selectedSegment and emits SegmentTick haptic",
        "OnSegmentChange to current segment is no-op",
        "OnRestoreClick emits ContextClick haptic",
        "OnUndoRestore emits ContextClick haptic",
        "OnDeleteDismiss does not emit haptic",
        "OnDeleteDismiss clears pending delete state",
        "OnPermanentDeleteClick emits LongPress haptic and stores target",
        "OnDeleteConfirm emits LongPress haptic and clears target",
        "OnDeleteConfirm without target does nothing",
    ],
    "src/commonTest/kotlin/io/github/stslex/workeeper/feature/archive/mvi/handler/ArchivePagingHandlerTest.kt": [
        "placeholder",
    ],
    "src/commonTest/kotlin/io/github/stslex/workeeper/feature/archive/mvi/mapper/ArchiveMetaLineTest.kt": [
        "an exercise leads with its kind word",
        "a training leads with the other kind word",
        "the kind is first, ahead of the date",
        "tags come last, after the date",
        "no tags leaves no dangling separator",
        "the date is day-and-month, not a relative span",
        "a missing timestamp degrades to the bare word rather than a wrong date",
    ],
    "src/commonTest/kotlin/io/github/stslex/workeeper/feature/archive/ui/components/ArchiveListSurfaceTest.kt": [
        "rows win over everything",
        "an unsettled refresh with no rows is loading, not empty",
        "a failed first page is its own verdict",
        "settled with no rows is the empty state",
    ],
    "src/commonTest/kotlin/io/github/stslex/workeeper/feature/archive/ui/components/PagingTailKindTest.kt": [
        "appending draws the loading footer",
        "a failed page draws the error footer, not silence",
        "exhausted draws no footer at all",
        "idle mid-list draws no footer either",
    ],
    "src/iosTest/kotlin/io/github/stslex/workeeper/feature/archive/ArchiveFeatureSceneIosTest.kt": [
        "resourcesPagingBranchesAndActionsRenderAndDispatch",
    ],
}

ARCHIVE_GOLDEN_METHODS = [
    "rowExercise",
    "rowTraining",
    "rowClamped",
    "pagingLoading",
    "pagingError",
    "screenExercisesNoRows",
    "screenTrainingsNoRows",
]

ARCHIVE_IDENTITY_TEST_NAMES = [
    "extension resolves the store through the parent graph",
    "store's app-scoped deps are the SAME instances the parent holds",
    "the two handler-store keys resolve to ONE instance",
    "the emitter the Store bound itself into is the one the handlers delegate through",
]

KOTLIN_TEST_NAME = re.compile(r"@Test\s+fun\s+(?:`([^`]+)`|(\w+))\s*\(")


def strip_kotlin_comments(source: str) -> str:
    return re.sub(r"//[^\n]*", "", re.sub(r"/\*.*?\*/", "", source, flags=re.DOTALL))


def read_catalog(path: Path) -> list[tuple]:
    """Ordered (tag, name, value) entries; plurals carry ordered (quantity, value) pairs."""
    entries: list[tuple] = []
    for element in ET.parse(path).getroot():
        if element.tag == "string":
            entries.append(("string", element.attrib["name"], element.text or ""))
        elif element.tag == "plurals":
            items = tuple(
                (item.attrib["quantity"], item.text or "") for item in element.findall("item")
            )
            entries.append(("plurals", element.attrib["name"], items))
        else:
            entries.append((element.tag, element.attrib.get("name", ""), None))
    return entries


def check_archive_feature_contract() -> list[str]:
    failures: list[str] = []
    root = ARCHIVE_ROOT

    # Exact 50-path topology: MODULES pins the 49 files under src; the build file is the 50th.
    top_level_files = sorted(path.name for path in root.iterdir() if path.is_file())
    if top_level_files != ["build.gradle.kts"]:
        failures.append(
            f"feature:archive: module root must hold exactly build.gradle.kts; found {top_level_files}"
        )

    build_source = (root / "build.gradle.kts").read_text(encoding="utf-8")
    required_build_fragments = [
        "alias(libs.plugins.convention.kmpComposeLibrary)",
        "alias(libs.plugins.metro)",
        "alias(libs.plugins.paparazzi)",
        'packageOfResClass = "io.github.stslex.workeeper.feature.archive.resources"',
        "includeJavax()",
        'implementation(project(":core:core"))',
        'api(project(":core:ui:kit"))',
        'api(project(":core:ui:mvi"))',
        'api(project(":core:ui:navigation"))',
        'implementation(project(":core:data:exercise"))',
        "api(libs.cmp.ui)",
        "api(libs.androidx.paging.common)",
        "api(libs.coroutines.core)",
        "implementation(libs.androidx.compose.paging)",
        "implementation(libs.cmp.material.icons.extended)",
        "implementation(libs.kotlinx.collections.immutable)",
        "implementation(libs.cmp.ui.test)",
        '"androidHostTestImplementation"(project(":core:ui:golden-harness"))',
        '"androidDeviceTestImplementation"(libs.bundles.android.test)',
        '"androidDeviceTestImplementation"(libs.androidx.compose.ui.test.junit4)',
        '"androidDeviceTestImplementation"(platform(libs.androidx.compose.bom))',
        '"androidDeviceTestImplementation"(libs.androidx.compose.ui.test.manifest)',
        '"androidDeviceTestImplementation"(project(":core:ui:test-utils"))',
        'apply(from = "$rootDir/gradle/golden-gate.gradle.kts")',
    ]
    for fragment in required_build_fragments:
        if build_source.count(fragment) != 1:
            failures.append(
                f"feature:archive: build contract must contain {fragment!r} exactly once"
            )
    if build_source.count('implementation(kotlin("test"))') != 2:
        failures.append(
            "feature:archive: kotlin(test) must exist exactly once in commonTest and iosTest"
        )
    for forbidden in (
        "convention.composeLibrary",
        "publicResClass",
        "androidTestImplementation",
        "debugImplementation",
        "testImplementation(",
        "paging.testing",
        "androidMain",
        "iosMain",
        "mockk",
        "robolectric",
    ):
        if forbidden in build_source:
            failures.append(
                f"feature:archive: forbidden build dependency/configuration remains: {forbidden}"
            )

    # Exact private catalogs, in order, with the Android res owner gone.
    for locale_index, catalog in enumerate(ARCHIVE_CATALOG_PATHS):
        expected = [
            (tag, name, values[locale_index])
            for tag, name, *values in ARCHIVE_RESOURCES
        ]
        actual = read_catalog(catalog) if catalog.is_file() else []
        if actual != expected:
            actual_by_name = {entry[1]: entry for entry in actual}
            mismatches = [
                f"{name}: expected={value!r}, actual={actual_by_name.get(name)!r}"
                for tag, name, value in expected
                if actual_by_name.get(name) != (tag, name, value)
            ]
            extra = sorted(set(actual_by_name) - {name for _, name, _ in expected})
            failures.append(
                f"feature:archive: exact private CMP catalog mismatch in {catalog}; "
                f"mismatched={mismatches}, unexpected={extra}, "
                f"order={'exact' if [e[1] for e in actual] == [e[1] for e in expected] else 'drifted'}"
            )
    owners: dict[str, list[str]] = {}
    for catalog in source_files("strings.xml"):
        for entry in read_catalog(catalog):
            if entry[1].startswith("feature_archive_"):
                owners.setdefault(entry[1], []).append(catalog.as_posix())
    expected_owners = sorted(path.as_posix() for path in ARCHIVE_CATALOG_PATHS)
    for name, paths in sorted(owners.items()):
        if sorted(paths) != expected_owners:
            failures.append(
                f"feature:archive: resource ownership drift for {name}; "
                f"expected={expected_owners}, actual={sorted(paths)}"
            )

    common_sources = {
        path: path.read_text(encoding="utf-8")
        for path in sorted((root / "src/commonMain/kotlin").rglob("*.kt"))
    }
    forbidden_common_tokens = (
        "androidx.compose.ui.platform.LocalContext",
        "LocalContext",
        "appDeps<",
        "android.content",
        "uiMode",
        "VisibleForTesting",
        "CompositionLocal",
        "ServiceLocator",
        "FactoryRegistry",
        ".format(",
    )
    for path, source in common_sources.items():
        relative = path.relative_to(root)
        for token in forbidden_common_tokens:
            if token in source:
                failures.append(
                    f"feature:archive: {relative} contains forbidden platform/lookup API {token!r}"
                )
        if re.search(r"^\s*(?:expect|actual)\s+", source, re.MULTILINE):
            failures.append(f"feature:archive: {relative} contains a forbidden expect/actual shim")
        if "@Preview(" in source and "ThemeMode" not in source:
            failures.append(f"feature:archive: {relative} previews must use portable ThemeMode")

    # No generated resource handle or Int id in Store, model or domain payloads.
    payload_paths = [
        path
        for path in common_sources
        if "/domain/" in path.as_posix()
        or path.name in {"ArchiveStore.kt", "ArchivedItemUi.kt", "ArchiveUiMapper.kt"}
    ]
    for path in payload_paths:
        source = common_sources[path]
        for token in (
            "StringResource",
            "PluralStringResource",
            ".resources.",
            "Res.string",
            "Res.plurals",
            "StringRes",
            "PluralsRes",
            "ResourceWrapper",
        ):
            if token in source:
                failures.append(
                    f"feature:archive: {path.relative_to(root)} carries a resource handle/lookup "
                    f"{token!r} in a Store/domain payload"
                )
    store_source = strip_kotlin_comments(common_sources[ARCHIVE_COMMON_MAIN / "mvi/store/ArchiveStore.kt"])
    state_block = re.search(r"data class State\((?P<params>.*?)\)\s*:\s*Store\.State", store_source, re.DOTALL)
    state_fields = (
        tuple(
            (name, type_.strip())
            for name, type_ in re.findall(r"val\s+(\w+)\s*:\s*([^,]+),", state_block.group("params"))
        )
        if state_block
        else ()
    )
    if state_fields != ARCHIVE_STATE_FIELDS:
        failures.append(
            "feature:archive: Store State must stay semantic with exactly "
            f"{ARCHIVE_STATE_FIELDS!r}; actual={state_fields!r}"
        )
    item_ui_source = strip_kotlin_comments(common_sources[ARCHIVE_COMMON_MAIN / "mvi/model/ArchivedItemUi.kt"])
    item_ui_fields = sorted(set(re.findall(r"val\s+(\w+)\s*:\s*([\w.]+)", item_ui_source)))
    expected_item_ui_fields = sorted(
        {
            ("item", "ArchivedItem"),
            ("item", "ArchivedItem.Exercise"),
            ("item", "ArchivedItem.Training"),
            ("metaLine", "String"),
        }
    )
    if item_ui_fields != expected_item_ui_fields:
        failures.append(
            "feature:archive: ArchivedItemUi must carry only item and the plain metaLine; "
            f"actual={item_ui_fields!r}"
        )

    # ResourceWrapper stays for formatDayMonth only, in the paging handler only.
    paging_path = ARCHIVE_COMMON_MAIN / "mvi/handler/ArchivePagingHandler.kt"
    for path, source in common_sources.items():
        if path != paging_path and "ResourceWrapper" in source:
            failures.append(
                f"feature:archive: {path.relative_to(root)} must not read ResourceWrapper"
            )
        for match in re.finditer(
            r"resourceWrapper\s*\.\s*(getString|getQuantityString|getAbbreviatedRelativeTime|"
            r"formatMediumDate)\s*\(",
            source,
        ):
            failures.append(
                f"feature:archive: {path.relative_to(root)} resolves archive copy through "
                f"ResourceWrapper.{match.group(1)}; use the private Compose resources"
            )
    if common_sources[paging_path].count("resourceWrapper.formatDayMonth(") != 1:
        failures.append(
            "feature:archive: ResourceWrapper must serve exactly one formatDayMonth date"
        )

    # State lambdas only copy State: resources resolve before updateState, never inside it.
    update_count = 0
    for path, source in common_sources.items():
        if "/mvi/handler/" not in path.as_posix():
            continue
        for call in ("updateState", "updateStateImmediate"):
            for body in braced_call_bodies(source, call):
                update_count += 1
                if ".copy(" not in compact(body):
                    failures.append(
                        f"feature:archive: {call} in {path.relative_to(root)} must return a State copy"
                    )
                for side_effect in (
                    "getString(",
                    "getPluralString(",
                    "stringResource(",
                    "pluralStringResource(",
                    "resourceWrapper.",
                    "Res.",
                ):
                    if side_effect in body:
                        failures.append(
                            f"feature:archive: {call} in {path.relative_to(root)} resolves a "
                            f"resource inside the State lambda: {side_effect!r}"
                        )
    if update_count == 0:
        failures.append("feature:archive: no updateState lambdas were inspected")

    # Exactly four portable previews: ArchiveScreen and ArchivedItemRow, each Light and Dark.
    preview_contract = {
        ARCHIVE_COMMON_MAIN / "ui/ArchiveScreen.kt": "ArchiveScreen",
        ARCHIVE_COMMON_MAIN / "ui/components/ArchivedItemRow.kt": "ArchivedItemRow",
    }
    total_previews = sum(source.count("@Preview(") for source in common_sources.values())
    if total_previews != 4:
        failures.append(
            f"feature:archive: exactly four portable previews are required; found {total_previews}"
        )
    for path, subject in preview_contract.items():
        source = common_sources[path]
        for fragment in (
            '@Preview(name = "Light", showBackground = true)',
            f"{subject}LightPreview()",
            f"{subject}Preview(themeMode = ThemeMode.LIGHT)",
            '@Preview(name = "Dark", showBackground = true)',
            f"{subject}DarkPreview()",
            f"{subject}Preview(themeMode = ThemeMode.DARK)",
            "AppTheme(themeMode = themeMode)",
        ):
            if source.count(fragment) != 1:
                failures.append(
                    f"feature:archive: {path.name} preview contract requires {fragment!r} exactly once"
                )

    # Exactly the two inherited production and three Native test-name suppressions.
    actual_suppressions: dict[str, list[str]] = {}
    for path in sorted((root / "src").rglob("*.kt")):
        found = re.findall(r"@(?:file:)?Suppress\(([^)]*)\)", path.read_text(encoding="utf-8"))
        if found:
            actual_suppressions[path.relative_to(root).as_posix()] = [args.strip() for args in found]
    if actual_suppressions != ARCHIVE_SUPPRESSIONS:
        failures.append(
            "feature:archive: suppressions must be exactly the two inherited production and three "
            f"Native test-name annotations; expected={ARCHIVE_SUPPRESSIONS!r}, "
            f"actual={actual_suppressions!r}"
        )
    for relative in ARCHIVE_SUPPRESSIONS:
        if "/commonTest/" in relative:
            source = (root / relative).read_text(encoding="utf-8")
            if '@file:Suppress("INVALID_CHARACTERS_NATIVE_ERROR")' not in source:
                failures.append(
                    f"feature:archive: {relative} Native test-name suppression must be file-scoped"
                )

    # The exact 26 portable/Native test names, and no Android test API in commonTest.
    for relative, expected_names in ARCHIVE_TEST_NAMES.items():
        path = root / relative
        source = path.read_text(encoding="utf-8") if path.is_file() else ""
        actual_names = [quoted or plain for quoted, plain in KOTLIN_TEST_NAME.findall(source)]
        if sorted(actual_names) != sorted(expected_names):
            failures.append(
                f"feature:archive: exact test identities drifted in {relative}; "
                f"missing={sorted(set(expected_names) - set(actual_names))}, "
                f"unexpected={sorted(set(actual_names) - set(expected_names))}"
            )
    for path in sorted((root / "src/commonTest/kotlin").rglob("*.kt")):
        source = path.read_text(encoding="utf-8")
        for forbidden in ("org.junit", "io.mockk", "@Disabled", "@Ignore"):
            if forbidden in source:
                failures.append(
                    f"feature:archive: {path.relative_to(root)} contains forbidden test API "
                    f"{forbidden!r}"
                )
    golden_source = (
        root
        / "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/archive/golden/ArchiveGoldenTest.kt"
    ).read_text(encoding="utf-8")
    golden_methods = re.findall(r"@EnumSource\(GoldenTheme::class\)\s+fun\s+(\w+)\(", golden_source)
    if golden_methods != ARCHIVE_GOLDEN_METHODS:
        failures.append(
            f"feature:archive: golden methods must be exactly {ARCHIVE_GOLDEN_METHODS}; "
            f"actual={golden_methods}"
        )
    device_source = (
        root
        / "src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/archive/ArchiveScreenTest.kt"
    ).read_text(encoding="utf-8")
    for fragment in (
        "@Smoke",
        '@Ignore("Awaiting feature rewrite — see GH issue #93 for coverage scope.")',
        "fun pendingFeatureRewrite()",
    ):
        if device_source.count(fragment) != 1:
            failures.append(
                f"feature:archive: device placeholder must keep {fragment!r} exactly once"
            )

    # The explicit generation-owned factory flow, and nothing that bypasses it.
    feature_path = ARCHIVE_COMMON_MAIN / "di/ArchiveFeature.kt"
    feature_source = common_sources[feature_path]
    graph_path = ARCHIVE_COMMON_MAIN / "ui/ArchiveGraph.kt"
    graph_source = common_sources[graph_path]
    required_root_fragments = {
        Path("app/common/src/main/kotlin/io/github/stslex/workeeper/app/common/di/AppRootDeps.kt"): [
            "val archiveGraphFactory: ArchiveGraph.Factory",
        ],
        Path("app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt"): [
            "override val archiveGraphFactory: ArchiveGraph.Factory",
        ],
        Path("app/common/src/main/kotlin/io/github/stslex/workeeper/App.kt"): [
            "if (admission.granted) {",
            "val deps = remember(currentPhase.id)",
            "(context.applicationContext as AppRootDepsHolder).appRootDeps()",
            "AppGenerationContent(deps)",
            "private fun AppGenerationContent(deps: AppRootDeps)",
            "archiveGraphFactory = deps.archiveGraphFactory",
        ],
        Path("app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt"): [
            "archiveGraphFactory: ArchiveGraph.Factory,",
            "archiveGraph(factory = archiveGraphFactory,",
        ],
        graph_path: [
            "factory: ArchiveGraph.Factory,",
            "navComponentScreen(ArchiveFeature(factory))",
        ],
        feature_path: [
            "internal class ArchiveFeature(",
            "private val factory: ArchiveGraph.Factory",
        ],
        Path("app/app/src/test/kotlin/io/github/stslex/workeeper/di/ArchiveExtensionIdentityTest.kt"): [
            "private fun AppGraph.archive(): ArchiveGraph = archiveGraphFactory.createArchiveGraph()",
        ],
    }
    for path, fragments in required_root_fragments.items():
        source = path.read_text(encoding="utf-8") if path.is_file() else ""
        compact_source = compact(source)
        for fragment in fragments:
            if compact_source.count(compact(fragment)) != 1:
                failures.append(
                    f"feature:archive: exact root-factory flow requires {fragment!r} once in {path}"
                )
    retained_factory = compact(
        """
        rememberMetroStoreProcessor<ArchiveStoreImpl> {
            factory
                .createArchiveGraph()
                .archiveStore
        }
        """
    )
    if compact(feature_source).count(retained_factory) != 1:
        failures.append(
            "feature:archive: factory invocation must occur exactly inside retained Store creation"
        )
    graph_calls = sum(source.count("createArchiveGraph(") for source in common_sources.values())
    # One declaration on ArchiveGraph.Factory plus the one retained invocation.
    if graph_calls != 2 or feature_source.count("createArchiveGraph(") != 1:
        failures.append(
            "feature:archive: createArchiveGraph() must be invoked once, inside the Store lambda"
        )
    for source in (graph_source, feature_source):
        if re.search(r"(?:factory|archiveGraphFactory)\s*:\s*ArchiveGraph\.Factory\s*[?=]", source):
            failures.append("feature:archive: factory parameters must be required and non-null")
    if "object ArchiveFeature" in feature_source:
        failures.append("feature:archive: ArchiveFeature must take its factory, not be an object")

    identity_source = Path(
        "app/app/src/test/kotlin/io/github/stslex/workeeper/di/ArchiveExtensionIdentityTest.kt"
    ).read_text(encoding="utf-8")
    if "asContribution" in identity_source:
        failures.append(
            "feature:archive: extension identities must not bypass AppRootDeps via asContribution"
        )
    identity_names = [quoted or plain for quoted, plain in KOTLIN_TEST_NAME.findall(identity_source)]
    if identity_names != ARCHIVE_IDENTITY_TEST_NAMES:
        failures.append(
            f"feature:archive: the four extension identities must stay exact; actual={identity_names}"
        )
    if len(re.findall(r"(?<!AppGraph)\.archive\(\)", identity_source)) != 4:
        failures.append(
            "feature:archive: all four extension identities must reach the graph through the accessor"
        )

    app_common_build = Path("app/common/build.gradle.kts").read_text(encoding="utf-8")
    if app_common_build.count('api(project(":feature:archive"))') != 1:
        failures.append("app:common: archive edge must be exactly one api dependency")
    if 'implementation(project(":feature:archive"))' in app_common_build:
        failures.append("app:common: archive edge must not remain implementation")
    app_build = Path("app/app/build.gradle.kts").read_text(encoding="utf-8")
    if app_build.count('implementation(project(":feature:archive"))') != 1:
        failures.append("app:app: direct archive aggregation edge must remain implementation")

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
    failures.extend(check_plan_editor_feature_contract())
    failures.extend(check_archive_feature_contract())

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
    print("  image-viewer resources and Coil request are exact")
    print(
        "  plan-editor resources, semantic State, portable BackHandler, previews, "
        "and explicit factory flow are exact"
    )
    print(
        "  archive catalogs, semantic State, suppressions, previews, test identities, "
        "and explicit factory flow are exact"
    )
    print("  app:common API edges and 10 remaining Context.appDeps readers are exact")


if __name__ == "__main__":
    sys.exit(main())
