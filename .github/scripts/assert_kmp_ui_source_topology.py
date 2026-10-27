#!/usr/bin/env python3
"""Explicit source-topology oracle for shared KMP UI leaf modules.

The manifest is intentionally path-based rather than count-based: moving a reviewed file into a
different source set while adding a same-count replacement must fail. Add future shared UI leaves
as new manifest entries instead of weakening the checks for the first migrated leaf.
Future shared feature entries are FeatureEntry data in FEATURE_ENTRIES, not new checkers.

Run from the repository root:

    python3 .github/scripts/assert_kmp_ui_source_topology.py
"""

import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
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
    "feature:all-trainings": {
        "root": Path("feature/all-trainings"),
        "files": {
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/di/AllTrainingsFeature.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/di/AllTrainingsGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/di/AllTrainingsHandlerStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/di/AllTrainingsHandlerStoreImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/di/AllTrainingsScope.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/domain/AllTrainingsInteractor.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/domain/AllTrainingsInteractorImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/domain/mapper/AllTrainingsDomainMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/domain/model/BulkArchiveResult.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/domain/model/TagDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/domain/model/TrainingListItemDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/handler/ClickHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/handler/NavigationHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/handler/PagingHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/mapper/TagUiMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/mapper/TrainingListItemMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/model/TagUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/model/TrainingListItemUi.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/store/AllTrainingsStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/store/AllTrainingsStoreImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/AllTrainingsGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/AllTrainingsScreen.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/components/ArrivedEmptyStates.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/components/ListSurface.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/components/PagingTailKind.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/components/PagingTails.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/components/TagFilterRow.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/components/TopBarMode.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/components/TrailingSlotKind.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/components/TrainingRow.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/components/TrainingsEmptyState.kt",
            "src/commonMain/composeResources/values-ru/strings.xml",
            "src/commonMain/composeResources/values/strings.xml",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/domain/AllTrainingsInteractorImplTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/handler/ClickHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/handler/NavigationHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/store/StartBlankGateTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/AllTrainingsClearanceTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/components/ListSurfaceTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/components/PagingTailKindTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/components/TopBarModeTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/components/TrailingSlotKindTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/golden/AllTrainingsGoldenTest.kt",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_coldOpenError_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_coldOpenError_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_coldOpenLoading_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_coldOpenLoading_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_confirmDialogContent_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_confirmDialogContent_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_emptyState_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_emptyState_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_filteredEmpty_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_filteredEmpty_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_pagingError_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_pagingError_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_pagingLoading_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_pagingLoading_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_rowActiveSelected_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_rowActiveSelected_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_rowActive_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_rowActive_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_rowClamped_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_rowClamped_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_rowLongName_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_rowLongName_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_rowPlain_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_rowPlain_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_rowSelected_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_rowSelected_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_rowUnselectedInSelection_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_rowUnselectedInSelection_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_screenColdOpen_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_screenColdOpen_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_screenFilteredEmpty_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_screenFilteredEmpty_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_screenFirstRunEmpty_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_screenFirstRunEmpty_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_screenList_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_screenList_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_screenRefreshError_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_screenRefreshError_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_screenSelectionEmptyUnfiltered_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_screenSelectionEmptyUnfiltered_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_screenSelectionEmpty_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_screenSelectionEmpty_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_screenSelection_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_screenSelection_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_selectionEmptyFiltered_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_selectionEmptyFiltered_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_selectionEmptyUnfiltered_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_selectionEmptyUnfiltered_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_tagFilterBand_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_trainings.golden_AllTrainingsGoldenTest_tagFilterBand_light.png",
            "src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/AllTrainingsScreenTest.kt",
            "src/iosTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/AllTrainingsFeatureSceneIosTest.kt",
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
    "feature:all-exercises": {
        "root": Path("feature/all-exercises"),
        "files": {
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/di/AllExercisesFeature.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/di/AllExercisesGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/di/AllExercisesHandlerStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/di/AllExercisesHandlerStoreImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/di/AllExercisesScope.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/domain/AllExercisesInteractor.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/domain/AllExercisesInteractorImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/domain/mapper/AllExercisesDomainMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/domain/model/ArchiveResult.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/domain/model/BulkArchiveResult.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/domain/model/ExerciseDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/domain/model/ExerciseListItemDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/domain/model/ExerciseTypeDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/domain/model/TagDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/handler/ClickHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/handler/NavigationHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/handler/PagingHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/mapper/AllExercisesUiMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/model/ExerciseTypeUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/model/ExerciseUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/model/TagUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/store/AllExercisesStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/store/AllExercisesStoreImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/AllExercisesGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/AllExercisesScreen.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/components/ArrivedEmptyStates.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/components/ExerciseRow.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/components/ExercisesEmptyState.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/components/ListSurface.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/components/PagingTailKind.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/components/PagingTails.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/components/TagFilterRow.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/components/TopBarMode.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/components/TrailingSlotKind.kt",
            "src/commonMain/composeResources/values-ru/strings.xml",
            "src/commonMain/composeResources/values/strings.xml",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/domain/AllExercisesInteractorImplTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/handler/ClickHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/handler/NavigationHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/handler/PagingHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/mapper/AllExercisesUiMapperTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/AllExercisesClearanceTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/components/ListSurfaceTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/components/PagingTailKindTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/components/TopBarModeTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/components/TrailingSlotKindTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/golden/AllExercisesGoldenTest.kt",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_blockedArchiveDialogContent_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_blockedArchiveDialogContent_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_coldOpenError_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_coldOpenError_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_coldOpenLoading_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_coldOpenLoading_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_confirmDialogContent_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_confirmDialogContent_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_emptyState_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_emptyState_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_filteredEmpty_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_filteredEmpty_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_pagingError_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_pagingError_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_pagingLoading_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_pagingLoading_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_rowClamped_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_rowClamped_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_rowLongName_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_rowLongName_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_rowSelected_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_rowSelected_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_rowTypeIsolated_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_rowTypeIsolated_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_rowUnselectedInSelection_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_rowUnselectedInSelection_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_rowWeighted_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_rowWeighted_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_rowWeightless_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_rowWeightless_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_screenColdOpen_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_screenColdOpen_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_screenFilteredEmpty_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_screenFilteredEmpty_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_screenFirstRunEmpty_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_screenFirstRunEmpty_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_screenList_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_screenList_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_screenRefreshError_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_screenRefreshError_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_screenSelectionEmptyUnfiltered_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_screenSelectionEmptyUnfiltered_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_screenSelectionEmpty_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_screenSelectionEmpty_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_screenSelection_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_screenSelection_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_selectionEmptyFiltered_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_selectionEmptyFiltered_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_selectionEmptyUnfiltered_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_selectionEmptyUnfiltered_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_tagFilterBand_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.all_exercises.golden_AllExercisesGoldenTest_tagFilterBand_light.png",
            "src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/AllExercisesScreenTest.kt",
            "src/iosTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/AllExercisesFeatureSceneIosTest.kt",
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
    "feature:past-session": {
        "root": Path("feature/past-session"),
        "files": {
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/past_session/gate/PastSetRowOverflowGateTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/past_session/golden/PastSessionGoldenTest.kt",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_cardCollapsed_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_cardCollapsed_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_cardDoubleDigitIndex_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_cardDoubleDigitIndex_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_cardSkippedEmpty_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_cardSkippedEmpty_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_cardSkippedWithSets_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_cardSkippedWithSets_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_cardWithSets_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_cardWithSets_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_headerWithoutTonnage_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_headerWithoutTonnage_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_header_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_header_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_screenError_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_screenError_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_screenLoaded_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_screenLoaded_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_setRowInvalidReps_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_setRowInvalidReps_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_setRowPersonalRecord_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_setRowPersonalRecord_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_setRowPlain_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_setRowPlain_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_setRowWeightless_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_setRowWeightless_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_sheetSessionMenu_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_sheetSessionMenu_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_topbar_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.past_session.golden_PastSessionGoldenTest_topbar_light.png",
            "src/commonMain/composeResources/values-ru/strings.xml",
            "src/commonMain/composeResources/values/strings.xml",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/di/PastSessionFeature.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/di/PastSessionGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/di/PastSessionHandlerStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/di/PastSessionHandlerStoreImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/di/PastSessionScope.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/domain/PastSessionInteractor.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/domain/PastSessionInteractorImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/domain/mapper/PastSessionDomainMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/domain/model/DetailWithPrs.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/domain/model/ExerciseTypeDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/domain/model/SessionDetailDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/domain/model/SetDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/domain/model/SetTypeDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/handler/ClickHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/handler/CommonHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/handler/InputHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/handler/NavigationHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/mapper/PastSessionUiMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/model/PastSessionUiModels.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/store/DialogState.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/store/PastSessionStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/store/PastSessionStoreImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/ui/PastSessionGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/ui/PastSessionScreen.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/ui/components/DeleteConfirmDialog.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/ui/components/PastExerciseCard.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/ui/components/PastSessionHeader.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/ui/components/PastSessionSheets.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/ui/components/PastSetEditRow.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/past_session/domain/PastSessionInteractorImplTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/handler/ClickHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/handler/CommonHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/handler/InputHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/handler/NavigationHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/mapper/PastSessionUiMapperTest.kt",
            "src/iosTest/kotlin/io/github/stslex/workeeper/feature/past_session/PastSessionFeatureSceneIosTest.kt",
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
    "feature:home": {
        "root": Path("feature/home"),
        "files": {
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/home/golden/HomeGoldenTest.kt",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_activeSessionBanner_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_activeSessionBanner_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_coldOpenError_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_coldOpenError_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_pagingErrorTail_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_pagingErrorTail_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_pagingLoadingTail_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_pagingLoadingTail_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_recentRowRussian_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_recentRowRussian_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_recentRow_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_recentRow_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_screenActiveSessionLoading_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_screenActiveSessionLoading_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_screenColdOpenError_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_screenColdOpenError_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_screenColdOpenLoading_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_screenColdOpenLoading_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_screenEmptyRegion_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_screenEmptyRegion_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_screenList_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_screenList_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_screenWithActiveSession_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_screenWithActiveSession_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_startCardDaysSince_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_startCardDaysSince_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_startCardEmptyDaysSince_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_startCardEmptyDaysSince_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_startCardEmptyTags_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_startCardEmptyTags_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_startCardEmptyTemplates_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_startCardEmptyTemplates_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_startCardEmptyWeek_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_startCardEmptyWeek_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_startCardForgottenNeverRun_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_startCardForgottenNeverRun_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_startCardForgotten_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_startCardForgotten_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_startCardTagIdle_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_startCardTagIdle_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_startCard_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.home.golden_HomeGoldenTest_startCard_light.png",
            "src/commonMain/composeResources/values-ru/strings.xml",
            "src/commonMain/composeResources/values/strings.xml",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/di/HomeFeature.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/di/HomeGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/di/HomeHandlerStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/di/HomeHandlerStoreImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/di/HomeScope.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/domain/HomeInteractor.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/domain/HomeInteractorImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/domain/mapper/HomeDomainMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/domain/model/ActiveSessionDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/domain/model/ActiveSessionWithStatsDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/domain/model/RecentSessionDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/domain/model/StartCardModeDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/domain/model/StartCardReadoutDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/domain/model/StartSessionConflict.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/domain/model/TrainingListItemDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/domain/model/WeekReadoutDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/domain/usecase/ObserveStartCardReadoutUseCase.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/domain/usecase/ObserveStartCardReadoutUseCaseImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/domain/usecase/ObserveWeekReadoutUseCase.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/domain/usecase/ObserveWeekReadoutUseCaseImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/mvi/handler/ClickHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/mvi/handler/CommonHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/mvi/handler/NavigationHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/mvi/handler/PagingHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/mvi/mapper/HomeUiMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/mvi/mapper/StartCardModeMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/mvi/model/PickerTrainingItem.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/mvi/model/RecentSessionItem.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/mvi/model/StartCardBodyUi.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/mvi/store/BottomSheetState.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/mvi/store/HomeStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/mvi/store/HomeStoreImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/ui/HomeGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/ui/HomeScreen.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/ui/components/ActiveSessionBanner.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/ui/components/HomeListSurface.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/ui/components/HomeStartCard.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/ui/components/PagingTailKind.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/ui/components/PagingTails.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/ui/components/RecentSessionRow.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/ui/components/TrainingPickerSheet.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/domain/HomeInteractorImplTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/domain/usecase/ObserveStartCardReadoutUseCaseTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/domain/usecase/ObserveWeekReadoutUseCaseTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/mvi/handler/ClickHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/mvi/handler/CommonHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/mvi/handler/NavigationHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/mvi/mapper/HomeUiMapperTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/mvi/mapper/StartCardModeMapperTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/mvi/store/HomeStartCardSeedTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/mvi/store/HomeTestState.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/ui/components/HomeListSurfaceTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/ui/components/HomePagingTailKindTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/ui/components/RecentMetaLineTest.kt",
            "src/iosTest/kotlin/io/github/stslex/workeeper/feature/home/HomeFeatureSceneIosTest.kt",
            "src/iosTest/kotlin/io/github/stslex/workeeper/feature/home/ui/components/HomeStartCardModeLabelTest.kt",
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
    "feature:exercise-chart": {
        "root": Path("feature/exercise-chart"),
        "files": {
            "src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ExerciseChartScreenTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/ChartFolderPrRuleParityTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/golden/ExerciseChartGoldenTest.kt",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_canvasMidScrub_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_canvasMidScrub_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_canvasRecordActive_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_canvasRecordActive_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_footerStats_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_footerStats_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_pickerSheetContent_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_pickerSheetContent_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_readoutPlain_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_readoutPlain_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_readoutRecord_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_readoutRecord_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_screenEmptyNoData_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_screenEmptyNoData_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_screenEmptyNoSessions_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_screenEmptyNoSessions_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_screenEmptyNotFound_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_screenEmptyNotFound_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_screenLoadFailed_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_screenLoadFailed_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_screenPopulated_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_screenPopulated_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_screenRecordActive_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_screenRecordActive_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_screenWeightless_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_screenWeightless_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_tabsIndicatorFirst_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_tabsIndicatorFirst_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_tabsIndicatorSecond_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise_chart.golden_ExerciseChartGoldenTest_tabsIndicatorSecond_light.png",
            "src/commonMain/composeResources/values-ru/strings.xml",
            "src/commonMain/composeResources/values/strings.xml",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/di/ExerciseChartFeature.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/di/ExerciseChartGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/di/ExerciseChartHandlerStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/di/ExerciseChartHandlerStoreImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/di/ExerciseChartScope.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/ChartFolder.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/ExerciseChartInteractor.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/ExerciseChartInteractorImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/mapper/ExerciseChartDomainMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/model/ChartFoldDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/model/ChartFooterStatsDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/model/ChartMetricDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/model/ChartPointDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/model/ChartPresetDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/model/ExerciseTypeDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/model/HistoryEntryDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/model/RecentExerciseDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/handler/ChartLoader.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/handler/ClickHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/handler/CommonHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/handler/NavigationHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/mapper/ChartReadoutMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/mapper/ExerciseChartUiMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/model/ChartFooterStatsUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/model/ChartMetricUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/model/ChartPointUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/model/ChartPresetUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/model/ChartReadoutCopyUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/model/ChartReadoutUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/model/ExercisePickerItemUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/store/ExerciseChartStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/store/ExerciseChartStoreImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ui/ExerciseChartGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ui/ExerciseChartScreen.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ui/components/ChartCanvas.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ui/components/ChartEmptyState.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ui/components/ChartFooterStats.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ui/components/ChartPointsAnimation.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ui/components/ChartReadout.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ui/components/ExerciseHeader.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ui/components/ExercisePickerSheet.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ui/components/MetricTabs.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ui/components/PresetChipsRow.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/ChartFolderTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/handler/ClickHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/handler/CommonHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/handler/NavigationHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/mapper/ExerciseChartUiMapperTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/store/ChartContentResolutionTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ui/components/ChartPointsAnimationTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ui/components/MetricTabsMotionTest.kt",
            "src/iosTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ExerciseChartFeatureSceneIosTest.kt",
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
    "feature:single-training": {
        "root": Path("feature/single-training"),
        "files": {
            "src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/single_training/SingleTrainingScreenTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/single_training/golden/TrainingDetailGoldenTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/single_training/golden/TrainingEditGoldenTest.kt",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.single_training.golden_TrainingDetailGoldenTest_detailEmptyHistory_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.single_training.golden_TrainingDetailGoldenTest_detailEmptyHistory_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.single_training.golden_TrainingDetailGoldenTest_detailNoPlanExercise_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.single_training.golden_TrainingDetailGoldenTest_detailNoPlanExercise_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.single_training.golden_TrainingDetailGoldenTest_detailWithPlans_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.single_training.golden_TrainingDetailGoldenTest_detailWithPlans_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.single_training.golden_TrainingEditGoldenTest_editCollapsed_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.single_training.golden_TrainingEditGoldenTest_editCollapsed_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.single_training.golden_TrainingEditGoldenTest_editEmpty_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.single_training.golden_TrainingEditGoldenTest_editEmpty_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.single_training.golden_TrainingEditGoldenTest_editExpanded_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.single_training.golden_TrainingEditGoldenTest_editExpanded_light.png",
            "src/commonMain/composeResources/values-ru/strings.xml",
            "src/commonMain/composeResources/values/strings.xml",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/di/SingleTrainingFeature.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/di/SingleTrainingGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/di/SingleTrainingHandlerStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/di/SingleTrainingHandlerStoreImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/di/SingleTrainingScope.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/domain/SingleTrainingInteractor.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/domain/SingleTrainingInteractorImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/domain/mapper/SingleTrainingDomainMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/domain/model/ActiveSessionDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/domain/model/ArchiveResult.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/domain/model/ExerciseDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/domain/model/ExercisePlanDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/domain/model/ExerciseTypeDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/domain/model/PickerExercise.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/domain/model/PlanSetDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/domain/model/SessionDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/domain/model/SessionStateDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/domain/model/SetTypeDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/domain/model/StartSessionConflict.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/domain/model/TagDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/domain/model/TrainingChangeDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/domain/model/TrainingDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/domain/model/TrainingExerciseDetail.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/handler/ClickHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/handler/CommonHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/handler/InputHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/handler/NavigationHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/mapper/TagUiMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/model/SingleTrainingUiModels.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/store/DialogState.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/store/SingleTrainingStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/store/SingleTrainingStoreImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/ui/SingleTrainingGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/ui/TrainingDetailScreen.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/ui/TrainingEditScreen.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/ui/components/ExercisePickerSheet.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/ui/components/TrainingDetailMenuSheetContent.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/ui/components/TrainingExerciseCard.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/ui/components/TrainingHistoryRow.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/handler/ClickHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/handler/CommonHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/handler/NavigationHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/store/SingleTrainingStateRouteArgTest.kt",
            "src/iosTest/kotlin/io/github/stslex/workeeper/feature/single_training/SingleTrainingFeatureSceneIosTest.kt",
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
    "feature:exercise": {
        "root": Path("feature/exercise"),
        "files": {
            "src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/exercise/ExerciseFormBasicsTest.kt",
            "src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/exercise/ExerciseScreenTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/exercise/golden/ExerciseDescriptionBlockGoldenTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/exercise/golden/ExerciseDetailGoldenTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/exercise/golden/ExerciseEditGoldenTest.kt",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDescriptionBlockGoldenTest_editable_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDescriptionBlockGoldenTest_editable_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDescriptionBlockGoldenTest_readWithImage_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDescriptionBlockGoldenTest_readWithImage_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDescriptionBlockGoldenTest_readWithPlaceholder_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDescriptionBlockGoldenTest_readWithPlaceholder_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_historyRowRecord_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_historyRowRecord_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_historyRow_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_historyRow_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_recordHeroMetaClamped_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_recordHeroMetaClamped_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_recordHeroWeightless_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_recordHeroWeightless_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_recordHero_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_recordHero_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_screenEmptyHistory_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_screenEmptyHistory_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_screenEmptyPlan_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_screenEmptyPlan_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_screenEmpty_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_screenEmpty_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_screenImageNoDescription_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_screenImageNoDescription_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_screenLoaded_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_screenLoaded_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_screenNoRecord_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_screenNoRecord_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_screenWeightless_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_screenWeightless_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_sheetDetailMenuDeletable_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_sheetDetailMenuDeletable_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_sheetDetailMenu_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_sheetDetailMenu_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_topbar_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseDetailGoldenTest_topbar_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseEditGoldenTest_createEmpty_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseEditGoldenTest_createEmpty_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseEditGoldenTest_editBlankNameError_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseEditGoldenTest_editBlankNameError_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseEditGoldenTest_editDuplicateNameError_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseEditGoldenTest_editDuplicateNameError_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseEditGoldenTest_editWeighted_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseEditGoldenTest_editWeighted_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseEditGoldenTest_editWeightless_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseEditGoldenTest_editWeightless_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseEditGoldenTest_sheetPlanInfo_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.exercise.golden_ExerciseEditGoldenTest_sheetPlanInfo_light.png",
            "src/commonMain/composeResources/values-ru/strings.xml",
            "src/commonMain/composeResources/values/strings.xml",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/di/ExerciseFeature.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/di/ExerciseGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/di/ExerciseHandlerStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/di/ExerciseHandlerStoreImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/di/ExerciseScope.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/ExerciseInteractor.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/ExerciseInteractorImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/mapper/ExerciseDomainMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/model/ActiveSessionDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/model/ArchiveResult.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/model/ExerciseChangeDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/model/ExerciseDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/model/ExerciseTypeDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/model/HistoryEntryDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/model/PersonalRecordDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/model/PlanSetDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/model/SaveResult.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/model/SetTypeDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/model/TagDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/model/TrackNowConflict.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/usecase/ArchiveExerciseUseCase.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/usecase/ArchiveExerciseUseCaseImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/usecase/DeleteSessionUseCase.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/usecase/DeleteSessionUseCaseImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/usecase/ResolveTrackNowConflictUseCase.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/usecase/ResolveTrackNowConflictUseCaseImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/usecase/StartTrackNowSessionUseCase.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/usecase/StartTrackNowSessionUseCaseImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/ExerciseDetailScreen.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/ExerciseEditScreen.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/ExerciseGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/components/ExerciseDescriptionBlock.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/components/ExerciseDetailSheets.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/components/ExerciseHistoryRow.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/components/ImageSourceSheetContent.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/components/PlanInfoSheetContent.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/handler/ClickHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/handler/CommonHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/handler/InputHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/handler/NavigationHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/mapper/ExerciseUiMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/model/ExerciseClickCopy.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/model/HistoryUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/model/ImageDisplay.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/model/ImageErrorType.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/model/ImageSourceUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/model/PendingImage.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/model/PersonalRecordUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/store/DialogState.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/store/ExerciseStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/store/ExerciseStoreImpl.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise/domain/ExerciseInteractorImplTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/handler/ClickHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/handler/CommonHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/handler/ExerciseDeferredDeleteDbTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/handler/InputHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/handler/NavigationHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/handler/PermanentDeleteUndoLabelRuTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/mapper/ExerciseUiMapperTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/store/ExerciseDirtyStateTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/store/ExerciseStateRouteArgTest.kt",
            "src/iosTest/kotlin/io/github/stslex/workeeper/feature/exercise/ExerciseFeatureSceneIosTest.kt",
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
    "feature:live-workout": {
        "root": Path("feature/live-workout"),
        "files": {
            "src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/live_workout/LiveWorkoutScreenTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/live_workout/gate/LiveSetRowOverflowGateTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/live_workout/gate/SetColumnAlignmentGateTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/live_workout/golden/SessionHeaderGoldenTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/live_workout/golden/SessionScreenGoldenTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/live_workout/golden/SessionSheetsGoldenTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/live_workout/golden/SessionStateGoldenTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/ExerciseDoneRuleTest.kt",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionHeaderGoldenTest_sheadDefault_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionHeaderGoldenTest_sheadDefault_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionHeaderGoldenTest_sheadEditing_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionHeaderGoldenTest_sheadEditing_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionHeaderGoldenTest_sheadSkippedMeta_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionHeaderGoldenTest_sheadSkippedMeta_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionHeaderGoldenTest_topbar_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionHeaderGoldenTest_topbar_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionScreenGoldenTest_sessionScreen_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionScreenGoldenTest_sessionScreen_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionSheetsGoldenTest_deleteExerciseSheetAdhoc_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionSheetsGoldenTest_deleteExerciseSheetAdhoc_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionSheetsGoldenTest_deleteExerciseSheetPlanned_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionSheetsGoldenTest_deleteExerciseSheetPlanned_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionSheetsGoldenTest_descriptionSheet_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionSheetsGoldenTest_descriptionSheet_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionSheetsGoldenTest_exerciseMenuSheetSkipped_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionSheetsGoldenTest_exerciseMenuSheetSkipped_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionSheetsGoldenTest_exerciseMenuSheet_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionSheetsGoldenTest_exerciseMenuSheet_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionSheetsGoldenTest_sessionMenuSheet_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionSheetsGoldenTest_sessionMenuSheet_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionSheetsGoldenTest_undoToast_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionSheetsGoldenTest_undoToast_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_exerciseActive_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_exerciseActive_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_exerciseBodyweightRu_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_exerciseBodyweightRu_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_exerciseFinishedReopened_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_exerciseFinishedReopened_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_exerciseFinished_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_exerciseFinished_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_exerciseOneOffFinished_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_exerciseOneOffFinished_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_exerciseOneOff_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_exerciseOneOff_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_exercisePending_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_exercisePending_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_exerciseSingleRow_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_exerciseSingleRow_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_exerciseSkipped_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_exerciseSkipped_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_exerciseTenSets_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_exerciseTenSets_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_setBodyweight_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_setBodyweight_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_setDone_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_setDone_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_setFlashPeak_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_setFlashPeak_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_setPersonalRecordDone_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_setPersonalRecordDone_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_setPersonalRecord_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_setPersonalRecord_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_setPlain_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_setPlain_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_setTwoDigitReps_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_setTwoDigitReps_light.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_setUnfilled_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.feature.live_workout.golden_SessionStateGoldenTest_setUnfilled_light.png",
            "src/commonMain/composeResources/values-ru/strings.xml",
            "src/commonMain/composeResources/values/strings.xml",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/di/LiveWorkoutFeature.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/di/LiveWorkoutGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/di/LiveWorkoutHandlerStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/di/LiveWorkoutHandlerStoreImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/di/LiveWorkoutScope.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/LiveWorkoutInteractor.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/LiveWorkoutInteractorImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/mapper/LiveWorkoutDomainMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/model/AddExerciseResult.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/model/AdhocSessionResult.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/model/ExercisePickerEntry.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/model/ExerciseTypeDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/model/ExternalWriteDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/model/FinishResult.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/model/InlineAdhocResult.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/model/LiveExerciseDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/model/PerformedExerciseDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/model/PersonalRecordDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/model/PlanSetDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/model/SessionDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/model/SessionSnapshotDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/model/SessionStateDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/model/SetDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/model/SetTypeDomain.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/ClickHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/CommonHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/DialogClickHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/ExercisePicker.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/ExercisePickerHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/ExternalWriteCoverage.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/InputHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/LiveWorkoutDraftExt.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/NavigationHandler.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/PendingUndoOps.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/DeleteExerciseCopyMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/ErrorTypeMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/ExerciseDoneRule.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/LiveSetMutator.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/LiveSetRowsResolver.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/LiveWorkoutMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/RailMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/StateStatusMapper.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/model/DeleteExerciseCopyUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/model/ErrorType.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/model/ExerciseStatusUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/model/ExternalWriteUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/model/LiveExerciseUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/model/LiveSessionStateUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/model/LiveSetUiModel.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/model/LiveWorkoutCopy.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/store/BottomSheetState.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/store/DialogState.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/store/LiveWorkoutStore.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/store/LiveWorkoutStoreImpl.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/store/PendingUndo.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/ui/LiveWorkoutGraph.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/ui/LiveWorkoutScreen.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/ui/components/FinishConfirmDialog.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/ui/components/LiveExerciseCard.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/ui/components/LiveSetRow.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/ui/components/LiveWorkoutHeader.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/feature/live_workout/ui/components/SessionSheets.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/LiveWorkoutInteractorImplTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/ClickHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/CommonHandlerExternalWritesTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/CommonHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/DialogClickHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/ExercisePickerHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/ExternalWriteCoverageTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/FakeExercisePicker.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/InputHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/LiveSetDraftBehaviorTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/NavigationHandlerTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/DeleteExerciseCopyMapperTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/LiveSetMutatorTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/LiveSetVisibleRowsResolverTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/LiveWorkoutMapperReloadTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/LiveWorkoutMapperTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/StateStatusMapperTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/model/LiveWorkoutCopyFixtures.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/model/UnfilledSetTest.kt",
            "src/commonTest/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/store/LiveWorkoutStateRouteArgTest.kt",
            "src/iosTest/kotlin/io/github/stslex/workeeper/feature/live_workout/LiveWorkoutFeatureSceneIosTest.kt",
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
    Path("feature/app-dialogs/impl/src/main/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/di/AppDialogFeature.kt"): "AppDialogGraph.Factory",
    Path("feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/di/SettingsFeature.kt"): "SettingsGraph.Factory",
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
    Path("feature/exercise/src/commonMain/composeResources"): FEATURE_EXERCISE_RESOURCES,
    Path("feature/single-training/src/commonMain/composeResources"): FEATURE_SINGLE_TRAINING_RESOURCES,
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
            f"remaining Context.appDeps readers are not the exact {len(EXPECTED_APP_DEPS_READERS)} "
            f"unported entries; expected={expected_readers!r}, actual={actual_readers!r}"
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


@dataclass(frozen=True)
class FeatureEntry:
    """One shared feature entry, checked field by field by check_feature_entry.

    `root` and `identity_test` are repo-relative, as are extra_root_fragments' keys;
    build/test/suppression paths are relative to `root`; store_file, feature_file, graph_file,
    resource_wrapper_readers, preview_subjects' keys, preview_inventory's keys and
    resource_wrapper_dates' keys are relative to root/src/commonMain/kotlin/<package_dir>. An
    empty device_test means the module has no device test: its file is not read, and
    device_fragments must then be empty. Containers keep the type their failure text embeds:
    lists stay lists and tuples stay tuples, because a repr is part of the message. Never hash an
    entry; it holds lists and dicts.
    """

    module: str
    short_name: str
    root: Path
    package_dir: str
    build_fragments: tuple
    kotlin_test_count: int
    catalog: tuple
    resource_prefix: str
    payload_files: frozenset
    store_file: str
    state_fields: tuple
    resource_wrapper_readers: frozenset
    preview_subjects: dict
    suppressions: dict
    test_names: dict
    golden_test: str
    golden_methods: list
    device_test: str
    device_fragments: tuple
    feature_class: str
    graph_interface: str
    graph_function: str
    factory_accessor: str
    create_method: str
    store_impl: str
    store_accessor: str
    feature_file: str
    graph_file: str
    identity_test: Path
    identity_fragments: tuple
    identity_names: list
    identity_accessor: str
    extra_root_fragments: dict = field(default_factory=dict)
    extra_checks: tuple = ()
    # Additive shape fields (kmp-phase-7-11-past-session-feature.md Section 12.0). The defaults
    # keep every earlier entry's checks and failure text byte-identical.
    create_args: str = ""
    preview_inventory: dict = field(default_factory=dict)
    resource_wrapper_dates: dict = field(default_factory=dict)
    # D5 (kmp-phase-7-13-exercise-chart-feature.md Section 12.0): the exact bare `@Preview`
    # count per commonMain file, keyed like preview_inventory. Empty skips the check, so every
    # earlier entry stays byte-identical; home keeps home_preview_contract.
    bare_previews: dict = field(default_factory=dict)
    # D4 (kmp-phase-7-14-single-training-feature.md Section 12.0): every golden class beside
    # golden_test, path relative to root -> its exact methods. Empty by default, so every earlier
    # entry stays byte-identical.
    extra_golden_tests: dict = field(default_factory=dict)
    # G1 (kmp-phase-7-15-exercise-feature.md Section B12.0): the navComponentScreen overload the
    # entry's graph registers through. The default keeps every earlier entry byte-identical.
    screen_registration: str = "navComponentScreen"
    # G2 (same section): keys that share resource_prefix but belong to another owner. The
    # ownership walk skips them; the default skips nothing, so every earlier entry is unchanged.
    foreign_resource_prefixes: tuple = ()
    # D7 (kmp-phase-7-16-live-workout-feature.md Section C10.0): the pure same-module classes a
    # State lambda may return through in place of a `.copy(`. Empty by default, so every earlier
    # entry stays byte-identical.
    state_mutators: tuple = ()

    @property
    def common_main(self) -> Path:
        return self.root / "src/commonMain/kotlin" / self.package_dir

    @property
    def catalog_paths(self) -> tuple:
        return (
            self.root / "src/commonMain/composeResources/values/strings.xml",
            self.root / "src/commonMain/composeResources/values-ru/strings.xml",
        )


APP_ROOT_DEPS_PATH = Path(
    "app/common/src/main/kotlin/io/github/stslex/workeeper/app/common/di/AppRootDeps.kt"
)
APP_GRAPH_PATH = Path("app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt")
APP_PATH = Path("app/common/src/main/kotlin/io/github/stslex/workeeper/App.kt")
APP_NAVIGATION_HOST_PATH = Path(
    "app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt"
)

# The admitted composed region every feature factory is read from, once per generation.
APP_ADMISSION_FRAGMENTS = (
    "if (admission.granted) {",
    "val deps = remember(currentPhase.id)",
    "(context.applicationContext as AppRootDepsHolder).appRootDeps()",
    "AppGenerationContent(deps)",
    "private fun AppGenerationContent(deps: AppRootDeps)",
)

FEATURE_FORBIDDEN_BUILD_TOKENS = (
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
)

FEATURE_FORBIDDEN_COMMON_TOKENS = (
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

FEATURE_PAYLOAD_TOKENS = (
    "StringResource",
    "PluralStringResource",
    ".resources.",
    "Res.string",
    "Res.plurals",
    "StringRes",
    "PluralsRes",
    "ResourceWrapper",
)

FEATURE_STATE_LAMBDA_SIDE_EFFECTS = (
    "getString(",
    "getPluralString(",
    "stringResource(",
    "pluralStringResource(",
    "resourceWrapper.",
    "Res.",
)

# D3 (kmp-phase-7-12-home-feature.md Section 12.0): a resolver or wrapper handed to a call inside a
# State lambda resolves copy there just the same, where no token above sees it. A direct member call
# (`name.`) stays the token list's to report, so every failure that list reports stays
# byte-identical; any other reference is this pattern's.
FEATURE_STATE_LAMBDA_RESOLVERS = re.compile(r"\b(stringResolver|resourceWrapper)\b(?!\.)")

# D5 (kmp-phase-7-14-single-training-feature.md Section 12.0): what a State lambda may not hold,
# it may not reach through a function of its own file either. A function resolves copy when its
# text holds a side-effect token above or names the wrapper or the resolver at all. `invoke` is
# the handler's entry point, and `.invoke(` on a function value would collide with it.
FEATURE_COPY_HELPER_REFERENCES = re.compile(r"\b(?:stringResolver|resourceWrapper)\b")
SAME_FILE_FUNCTION = re.compile(
    r"^(?P<indent>[ ]*)(?:(?:private|internal|protected|public|override|open|suspend|inline|"
    r"tailrec|operator|infix)\s+)*fun\s+(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?(?P<name>\w+)\s*\(",
    re.MULTILINE,
)

# D4: golden_test's pattern, for the extra classes and the completeness walk.
GOLDEN_METHODS = re.compile(r"@EnumSource\(GoldenTheme::class\)\s+fun\s+(\w+)\(")

FEATURE_FORBIDDEN_TEST_APIS = ("org.junit", "io.mockk", "@Disabled", "@Ignore")

NATIVE_TEST_NAME_SUPPRESSION = '@file:Suppress("INVALID_CHARACTERS_NATIVE_ERROR")'

NUMBER_WORDS = (
    "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
    "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
    "nineteen", "twenty",
)


def number_word(count: int) -> str:
    return NUMBER_WORDS[count] if 0 <= count < len(NUMBER_WORDS) else str(count)


def every(count: int, singular: str, plural: str) -> str:
    """'the one X', 'both Xs' or 'all four Xs'."""
    if count == 1:
        return f"the one {singular}"
    if count == 2:
        return f"both {plural}"
    return f"all {number_word(count)} {plural}"


def kotlin_test_rule(count: int) -> str:
    # Two is the shared convention: one kotlin("test") in commonTest and one in iosTest.
    if count == 2:
        return "exactly once in commonTest and iosTest"
    return "exactly once" if count == 1 else f"exactly {number_word(count)} times"


def suppression_census(suppressions: dict) -> str:
    production = sum(1 for relative in suppressions if relative.startswith("src/commonMain/"))
    native = sum(1 for relative in suppressions if relative.startswith("src/commonTest/"))
    return (
        f"{number_word(production)} inherited production and "
        f"{number_word(native)} Native test-name annotations"
    )


KOTLIN_TEST_NAME = re.compile(r"@Test\s+fun\s+(?:`([^`]+)`|(\w+))\s*\(")

# D7 (kmp-phase-7-16-live-workout-feature.md Section C10.0): a `val`/`var` and its declared type.
STATE_MUTATOR_PROPERTY = re.compile(r"\b(?:val|var)\s+(\w+)\s*:\s*(\w+)\b")
# A top-level class or object; a nested one belongs to its file's mutator.
STATE_MUTATOR_DECLARATION = re.compile(
    r"^(?:(?:internal|private|public|data|open)\s+)*(?:class|object)\s+(\w+)", re.MULTILINE
)


def state_mutator_contract(entry: FeatureEntry, common_sources: dict[Path, str]) -> list[str]:
    """D7: each listed mutator is declared once in commonMain, its file is pure (no State lambda
    side effect, no resolver or wrapper), and every mapper class it names is listed too."""
    failures: list[str] = []
    if not entry.state_mutators:
        return failures
    declared: dict[str, list[Path]] = {}
    for path, raw in common_sources.items():
        for name in STATE_MUTATOR_DECLARATION.findall(strip_kotlin_comments(raw)):
            declared.setdefault(name, []).append(path)
    mappers = {
        name
        for name, paths in declared.items()
        if any("/mapper/" in path.as_posix() for path in paths)
    }
    for mutator in entry.state_mutators:
        paths = declared.get(mutator, [])
        if len(paths) != 1:
            failures.append(
                f"{entry.module}: state mutator {mutator} must be declared once in commonMain; "
                f"found {len(paths)}"
            )
            continue
        relative = paths[0].relative_to(entry.root)
        source = strip_kotlin_comments(common_sources[paths[0]])
        for token in FEATURE_STATE_LAMBDA_SIDE_EFFECTS:
            if token in source:
                failures.append(
                    f"{entry.module}: state mutator {mutator} in {relative} holds {token!r}; "
                    "a State lambda returns only through pure code"
                )
        for name in sorted(set(FEATURE_COPY_HELPER_REFERENCES.findall(source))):
            failures.append(f"{entry.module}: state mutator {mutator} in {relative} names {name!r}")
        for type_name in ("ResourceWrapper", "StringResolver"):
            if type_name in source:
                failures.append(
                    f"{entry.module}: state mutator {mutator} in {relative} takes {type_name}"
                )
        for name in sorted(mappers - set(entry.state_mutators)):
            if re.search(rf"\b{re.escape(name)}\b", source):
                failures.append(
                    f"{entry.module}: state mutator {mutator} in {relative} reaches {name}, "
                    "which state_mutators does not list"
                )
    return failures


def strip_kotlin_comments(source: str) -> str:
    return re.sub(r"//[^\n]*", "", re.sub(r"/\*.*?\*/", "", source, flags=re.DOTALL))


def calls_function(source: str, name: str) -> bool:
    return re.search(rf"(?<!\w){re.escape(name)}\s*\(", source) is not None


def copy_resolving_functions(source: str) -> set:
    """D5: the functions of a comment-free file that resolve copy, directly or through another
    of them. A function runs from its line to the next line indented at most as deep that is not
    its own closing bracket."""
    lines = source.splitlines(keepends=True)
    texts: dict[str, list[str]] = {}
    for match in SAME_FILE_FUNCTION.finditer(source):
        if match.group("name") == "invoke":
            continue
        indent = len(match.group("indent"))
        first = source.count("\n", 0, match.start())
        last = first + 1
        while last < len(lines):
            text = lines[last].strip()
            depth = len(lines[last]) - len(lines[last].lstrip(" "))
            if text and (depth < indent or (depth == indent and text[0] not in ")]}")):
                break
            last += 1
        texts.setdefault(match.group("name"), []).append("".join(lines[first:last]))
    resolving = {
        name
        for name, bodies in texts.items()
        for body in bodies
        if FEATURE_COPY_HELPER_REFERENCES.search(body)
        or any(token in body for token in FEATURE_STATE_LAMBDA_SIDE_EFFECTS)
    }
    grown = True
    while grown:
        grown = False
        for name, bodies in texts.items():
            if name not in resolving and any(
                calls_function(body, helper) for body in bodies for helper in resolving
            ):
                resolving.add(name)
                grown = True
    return resolving


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


def check_feature_entry(entry: FeatureEntry) -> list[str]:
    failures: list[str] = []
    module = entry.module
    root = entry.root
    common_main = entry.common_main

    # Exact topology: MODULES pins every file under src; the build file is the only one beside it.
    top_level_files = sorted(path.name for path in root.iterdir() if path.is_file())
    if top_level_files != ["build.gradle.kts"]:
        failures.append(
            f"{module}: module root must hold exactly build.gradle.kts; found {top_level_files}"
        )

    build_source = (root / "build.gradle.kts").read_text(encoding="utf-8")
    for fragment in entry.build_fragments:
        if build_source.count(fragment) != 1:
            failures.append(
                f"{module}: build contract must contain {fragment!r} exactly once"
            )
    if build_source.count('implementation(kotlin("test"))') != entry.kotlin_test_count:
        failures.append(
            f"{module}: kotlin(test) must exist {kotlin_test_rule(entry.kotlin_test_count)}"
        )
    for forbidden in FEATURE_FORBIDDEN_BUILD_TOKENS:
        if forbidden in build_source:
            failures.append(
                f"{module}: forbidden build dependency/configuration remains: {forbidden}"
            )

    # Exact private catalogs, in order, with the Android res owner gone.
    for locale_index, catalog in enumerate(entry.catalog_paths):
        expected = [
            (tag, name, values[locale_index])
            for tag, name, *values in entry.catalog
        ]
        actual = read_catalog(catalog) if catalog.is_file() else []
        if actual != expected:
            actual_by_name = {row[1]: row for row in actual}
            mismatches = [
                f"{name}: expected={value!r}, actual={actual_by_name.get(name)!r}"
                for tag, name, value in expected
                if actual_by_name.get(name) != (tag, name, value)
            ]
            extra = sorted(set(actual_by_name) - {name for _, name, _ in expected})
            failures.append(
                f"{module}: exact private CMP catalog mismatch in {catalog}; "
                f"mismatched={mismatches}, unexpected={extra}, "
                f"order={'exact' if [e[1] for e in actual] == [e[1] for e in expected] else 'drifted'}"
            )
    owners: dict[str, list[str]] = {}
    for catalog in source_files("strings.xml"):
        for row in read_catalog(catalog):
            if row[1].startswith(entry.resource_prefix) and not row[1].startswith(
                entry.foreign_resource_prefixes
            ):
                owners.setdefault(row[1], []).append(catalog.as_posix())
    expected_owners = sorted(path.as_posix() for path in entry.catalog_paths)
    for name, paths in sorted(owners.items()):
        if sorted(paths) != expected_owners:
            failures.append(
                f"{module}: resource ownership drift for {name}; "
                f"expected={expected_owners}, actual={sorted(paths)}"
            )

    common_sources = {
        path: path.read_text(encoding="utf-8")
        for path in sorted((root / "src/commonMain/kotlin").rglob("*.kt"))
    }
    for path, source in common_sources.items():
        relative = path.relative_to(root)
        for token in FEATURE_FORBIDDEN_COMMON_TOKENS:
            if token in source:
                failures.append(
                    f"{module}: {relative} contains forbidden platform/lookup API {token!r}"
                )
        if re.search(r"^\s*(?:expect|actual)\s+", source, re.MULTILINE):
            failures.append(f"{module}: {relative} contains a forbidden expect/actual shim")
        if "@Preview(" in source and "ThemeMode" not in source:
            failures.append(f"{module}: {relative} previews must use portable ThemeMode")

    # No generated resource handle or Int id in Store, model or domain payloads.
    payload_paths = [
        path
        for path in common_sources
        if "/domain/" in path.as_posix()
        or path.name in entry.payload_files
    ]
    for path in payload_paths:
        source = common_sources[path]
        for token in FEATURE_PAYLOAD_TOKENS:
            if token in source:
                failures.append(
                    f"{module}: {path.relative_to(root)} carries a resource handle/lookup "
                    f"{token!r} in a Store/domain payload"
                )
    store_source = strip_kotlin_comments(common_sources[common_main / entry.store_file])
    state_block = re.search(r"data class State\((?P<params>.*?)\)\s*:\s*Store\.State", store_source, re.DOTALL)
    state_fields = (
        tuple(
            (name, type_.strip())
            for name, type_ in re.findall(r"val\s+(\w+)\s*:\s*([^,]+),", state_block.group("params"))
        )
        if state_block
        else ()
    )
    if state_fields != entry.state_fields:
        failures.append(
            f"{module}: Store State must stay semantic with exactly "
            f"{entry.state_fields!r}; actual={state_fields!r}"
        )

    # ResourceWrapper stays with its declared readers, and copy never resolves through it.
    readers = {common_main / relative for relative in entry.resource_wrapper_readers}
    dates = {
        common_main / relative: methods
        for relative, methods in entry.resource_wrapper_dates.items()
    }
    for path, source in common_sources.items():
        if path not in readers and "ResourceWrapper" in source:
            failures.append(
                f"{module}: {path.relative_to(root)} must not read ResourceWrapper"
            )
        for match in re.finditer(
            r"resourceWrapper\s*\.\s*(getString|getQuantityString|getAbbreviatedRelativeTime|"
            r"formatMediumDate|formatDayMonth)\s*\(",
            source,
        ):
            if match.group(1) in dates.get(path, {}):
                continue
            failures.append(
                f"{module}: {path.relative_to(root)} resolves {entry.short_name} copy through "
                f"ResourceWrapper.{match.group(1)}; use the private Compose resources"
            )
    for path, methods in dates.items():
        relative = path.relative_to(root)
        if path not in readers:
            failures.append(
                f"{module}: {relative} declares a ResourceWrapper date but is not a declared reader"
            )
        source = common_sources.get(path)
        if source is None:
            failures.append(f"{module}: {relative} declares a ResourceWrapper date but is missing")
            continue
        for method, expected in methods.items():
            found = len(re.findall(rf"resourceWrapper\s*\.\s*{re.escape(method)}\s*\(", source))
            if found != expected:
                failures.append(
                    f"{module}: {relative} must read the ResourceWrapper.{method} date exactly "
                    f"{expected} time(s); found {found}"
                )

    # State lambdas only copy State: resources resolve before updateState, never inside it.
    update_count = 0
    for path, source in common_sources.items():
        if "/mvi/handler/" not in path.as_posix():
            continue
        helpers = copy_resolving_functions(strip_kotlin_comments(source))
        # D7: a property of this file whose declared type is a listed mutator, reached with `.`
        # or `::`, stands in for the `.copy(`.
        mutators = {
            name
            for name, declared in STATE_MUTATOR_PROPERTY.findall(strip_kotlin_comments(source))
            if declared in entry.state_mutators
        }
        for call in ("updateState", "updateStateImmediate"):
            for body in braced_call_bodies(source, call):
                update_count += 1
                delegates = any(
                    re.search(rf"(?<![\w.]){re.escape(name)}(?:\.|::)", compact(body))
                    for name in mutators
                )
                if ".copy(" not in compact(body) and not delegates:
                    failures.append(
                        f"{module}: {call} in {path.relative_to(root)} must return a State copy"
                    )
                for side_effect in FEATURE_STATE_LAMBDA_SIDE_EFFECTS:
                    if side_effect in body:
                        failures.append(
                            f"{module}: {call} in {path.relative_to(root)} resolves a "
                            f"resource inside the State lambda: {side_effect!r}"
                        )
                referenced = FEATURE_STATE_LAMBDA_RESOLVERS.findall(strip_kotlin_comments(body))
                for name in sorted(set(referenced)):
                    failures.append(
                        f"{module}: {call} in {path.relative_to(root)} references {name!r} "
                        "inside the State lambda; resolve copy before it"
                    )
                for helper in sorted(helpers):
                    if calls_function(strip_kotlin_comments(body), helper):
                        failures.append(
                            f"{module}: {call} in {path.relative_to(root)} calls {helper!r}, "
                            "a same-file function that resolves copy; resolve it before the lambda"
                        )
    if update_count == 0:
        failures.append(f"{module}: no updateState lambdas were inspected")
    failures.extend(state_mutator_contract(entry, common_sources))

    # Exactly the portable previews: every subject, each Light and Dark.
    preview_count = 2 * len(entry.preview_subjects) + sum(
        len(arguments) for arguments in entry.preview_inventory.values()
    )
    total_previews = sum(source.count("@Preview(") for source in common_sources.values())
    if total_previews != preview_count:
        failures.append(
            f"{module}: exactly {number_word(preview_count)} portable previews are required; "
            f"found {total_previews}"
        )
    for relative, subject in entry.preview_subjects.items():
        path = common_main / relative
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
                    f"{module}: {path.name} preview contract requires {fragment!r} exactly once"
                )
    for relative, expected_arguments in entry.preview_inventory.items():
        path = common_main / relative
        actual_arguments = re.findall(r"@Preview\(([^)]*)\)", common_sources.get(path, ""))
        if actual_arguments != expected_arguments:
            failures.append(
                f"{module}: {path.name} preview inventory must be exactly {expected_arguments!r}; "
                f"actual={actual_arguments!r}"
            )
    # D5: the count above sees only `@Preview(`. An entry that declares bare_previews pins every
    # bare `@Preview` per file, and every other commonMain file holds none.
    if entry.bare_previews:
        bare_preview = re.compile(r"@Preview\b(?!\s*\()")
        expected_bare = {
            common_main / relative: count for relative, count in entry.bare_previews.items()
        }
        for path in sorted(set(expected_bare) - set(common_sources)):
            failures.append(
                f"{module}: {path.relative_to(root)} must hold bare @Preview annotations "
                "but is missing"
            )
        for path, raw in common_sources.items():
            expected = expected_bare.get(path, 0)
            found = len(bare_preview.findall(strip_kotlin_comments(raw)))
            if found != expected:
                failures.append(
                    f"{module}: {path.relative_to(root)} must hold exactly {expected} bare "
                    f"@Preview annotation(s); found {found}"
                )

    # Exactly the declared production and Native test-name suppressions.
    actual_suppressions: dict[str, list[str]] = {}
    for path in sorted((root / "src").rglob("*.kt")):
        found = re.findall(r"@(?:file:)?Suppress\(([^)]*)\)", path.read_text(encoding="utf-8"))
        if found:
            actual_suppressions[path.relative_to(root).as_posix()] = [args.strip() for args in found]
    if actual_suppressions != entry.suppressions:
        failures.append(
            f"{module}: suppressions must be exactly the {suppression_census(entry.suppressions)}; "
            f"expected={entry.suppressions!r}, "
            f"actual={actual_suppressions!r}"
        )
    for relative in entry.suppressions:
        if "/commonTest/" in relative:
            source = (root / relative).read_text(encoding="utf-8")
            if NATIVE_TEST_NAME_SUPPRESSION not in source:
                failures.append(
                    f"{module}: {relative} Native test-name suppression must be file-scoped"
                )

    # The exact portable/Native test names, and no Android test API in commonTest.
    for relative, expected_names in entry.test_names.items():
        path = root / relative
        source = path.read_text(encoding="utf-8") if path.is_file() else ""
        actual_names = [quoted or plain for quoted, plain in KOTLIN_TEST_NAME.findall(source)]
        if sorted(actual_names) != sorted(expected_names):
            failures.append(
                f"{module}: exact test identities drifted in {relative}; "
                f"missing={sorted(set(expected_names) - set(actual_names))}, "
                f"unexpected={sorted(set(actual_names) - set(expected_names))}"
            )
    for path in sorted((root / "src/commonTest/kotlin").rglob("*.kt")):
        source = path.read_text(encoding="utf-8")
        for forbidden in FEATURE_FORBIDDEN_TEST_APIS:
            if forbidden in source:
                failures.append(
                    f"{module}: {path.relative_to(root)} contains forbidden test API "
                    f"{forbidden!r}"
                )
    golden_source = (root / entry.golden_test).read_text(encoding="utf-8")
    golden_methods = re.findall(r"@EnumSource\(GoldenTheme::class\)\s+fun\s+(\w+)\(", golden_source)
    if golden_methods != entry.golden_methods:
        failures.append(
            f"{module}: golden methods must be exactly {entry.golden_methods}; "
            f"actual={golden_methods}"
        )
    # D4: every extra golden class is pinned like golden_test, and no other androidHostTest file
    # renders goldens.
    for relative, expected_methods in entry.extra_golden_tests.items():
        path = root / relative
        methods = GOLDEN_METHODS.findall(path.read_text(encoding="utf-8") if path.is_file() else "")
        if methods != expected_methods:
            failures.append(
                f"{module}: golden methods in {relative} must be exactly {expected_methods}; "
                f"actual={methods}"
            )
    declared_goldens = {entry.golden_test, *entry.extra_golden_tests}
    for path in sorted((root / "src/androidHostTest").rglob("*.kt")):
        relative = path.relative_to(root).as_posix()
        if relative not in declared_goldens and GOLDEN_METHODS.search(
            path.read_text(encoding="utf-8")
        ):
            failures.append(
                f"{module}: {relative} renders goldens but is neither golden_test nor "
                "in extra_golden_tests"
            )
    if entry.device_test:
        device_source = (root / entry.device_test).read_text(encoding="utf-8")
        for fragment in entry.device_fragments:
            if device_source.count(fragment) != 1:
                failures.append(
                    f"{module}: device placeholder must keep {fragment!r} exactly once"
                )
    elif entry.device_fragments:
        failures.append(
            f"{module}: device fragments {list(entry.device_fragments)!r} need a device test"
        )

    # The explicit generation-owned factory flow, and nothing that bypasses it.
    graph_factory = f"{entry.graph_interface}.Factory"
    feature_path = common_main / entry.feature_file
    feature_source = common_sources[feature_path]
    graph_path = common_main / entry.graph_file
    graph_source = common_sources[graph_path]
    required_root_fragments = [
        (APP_ROOT_DEPS_PATH, [f"val {entry.factory_accessor}: {graph_factory}"]),
        (APP_GRAPH_PATH, [f"override val {entry.factory_accessor}: {graph_factory}"]),
        (
            APP_PATH,
            [*APP_ADMISSION_FRAGMENTS, f"{entry.factory_accessor} = deps.{entry.factory_accessor}"],
        ),
        (
            APP_NAVIGATION_HOST_PATH,
            [
                f"{entry.factory_accessor}: {graph_factory},",
                f"{entry.graph_function}(factory = {entry.factory_accessor},",
            ],
        ),
        (
            graph_path,
            [
                f"factory: {graph_factory},",
                f"{entry.screen_registration}({entry.feature_class}(factory))",
            ],
        ),
        (
            feature_path,
            [f"internal class {entry.feature_class}(", f"private val factory: {graph_factory}"],
        ),
        (entry.identity_test, list(entry.identity_fragments)),
        *((Path(path), list(fragments)) for path, fragments in entry.extra_root_fragments.items()),
    ]
    for path, fragments in required_root_fragments:
        source = path.read_text(encoding="utf-8") if path.is_file() else ""
        compact_source = compact(source)
        for fragment in fragments:
            if compact_source.count(compact(fragment)) != 1:
                failures.append(
                    f"{module}: exact root-factory flow requires {fragment!r} once in {path}"
                )
    retained_factory = compact(
        f"""
        rememberMetroStoreProcessor<{entry.store_impl}> {{
            factory
                .{entry.create_method}({entry.create_args})
                .{entry.store_accessor}
        }}
        """
    )
    if compact(feature_source).count(retained_factory) != 1:
        failures.append(
            f"{module}: factory invocation must occur exactly inside retained Store creation"
        )
    create_call = f"{entry.create_method}("
    graph_calls = sum(source.count(create_call) for source in common_sources.values())
    # One declaration on the graph's Factory plus the one retained invocation.
    if graph_calls != 2 or feature_source.count(create_call) != 1:
        failures.append(
            f"{module}: {entry.create_method}({entry.create_args}) must be invoked once, inside "
            "the Store lambda"
        )
    optional_factory = (
        rf"(?:factory|{re.escape(entry.factory_accessor)})\s*:\s*"
        rf"{re.escape(entry.graph_interface)}\.Factory\s*[?=]"
    )
    for source in (graph_source, feature_source):
        if re.search(optional_factory, source):
            failures.append(f"{module}: factory parameters must be required and non-null")
    if f"object {entry.feature_class}" in feature_source:
        failures.append(
            f"{module}: {entry.feature_class} must take its factory, not be an object"
        )

    identity_source = entry.identity_test.read_text(encoding="utf-8")
    if "asContribution" in identity_source:
        failures.append(
            f"{module}: extension identities must not bypass AppRootDeps via asContribution"
        )
    identity_count = len(entry.identity_names)
    identity_noun = "extension identity" if identity_count == 1 else "extension identities"
    identity_names = [quoted or plain for quoted, plain in KOTLIN_TEST_NAME.findall(identity_source)]
    if identity_names != entry.identity_names:
        failures.append(
            f"{module}: the {number_word(identity_count)} {identity_noun} must stay exact; "
            f"actual={identity_names}"
        )
    if len(re.findall(entry.identity_accessor, identity_source)) != identity_count:
        failures.append(
            f"{module}: {every(identity_count, 'extension identity', 'extension identities')} "
            "must reach the graph through the accessor"
        )

    app_common_build = Path("app/common/build.gradle.kts").read_text(encoding="utf-8")
    if app_common_build.count(f'api(project(":{module}"))') != 1:
        failures.append(f"app:common: {entry.short_name} edge must be exactly one api dependency")
    if f'implementation(project(":{module}"))' in app_common_build:
        failures.append(f"app:common: {entry.short_name} edge must not remain implementation")
    app_build = Path("app/app/build.gradle.kts").read_text(encoding="utf-8")
    if app_build.count(f'implementation(project(":{module}"))') != 1:
        failures.append(
            f"app:app: direct {entry.short_name} aggregation edge must remain implementation"
        )

    for extra_check in entry.extra_checks:
        failures.extend(extra_check(entry, common_sources))

    return failures


def archive_item_ui_contract(entry: FeatureEntry, common_sources: dict[Path, str]) -> list[str]:
    failures: list[str] = []
    item_ui_source = strip_kotlin_comments(common_sources[entry.common_main / "mvi/model/ArchivedItemUi.kt"])
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
    return failures


def archive_day_month_contract(entry: FeatureEntry, common_sources: dict[Path, str]) -> list[str]:
    failures: list[str] = []
    # ResourceWrapper stays for formatDayMonth only, in the paging handler only.
    paging_path = entry.common_main / "mvi/handler/ArchivePagingHandler.kt"
    if common_sources[paging_path].count("resourceWrapper.formatDayMonth(") != 1:
        failures.append(
            "feature:archive: ResourceWrapper must serve exactly one formatDayMonth date"
        )
    return failures


ARCHIVE_ENTRY = FeatureEntry(
    module="feature:archive",
    short_name="archive",
    root=Path("feature/archive"),
    package_dir="io/github/stslex/workeeper/feature/archive",
    build_fragments=(
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
    ),
    kotlin_test_count=2,
    # Exact private EN/RU catalog, in file order: (tag, name, EN value, RU value). Plurals carry
    # ordered (quantity, value) pairs. Identifier set, order, placeholders and plural categories are
    # all contractual (kmp-phase-7-8-archive-feature.md §3.4, with the session count's six items
    # made positional by decision P1, §19.8).
    catalog=(
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
            (("one", "%1$d session"), ("other", "%1$d sessions")),
            (
                ("one", "%1$d сессия"),
                ("few", "%1$d сессии"),
                ("many", "%1$d сессий"),
                ("other", "%1$d сессии"),
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
    ),
    resource_prefix="feature_archive_",
    payload_files=frozenset({"ArchiveStore.kt", "ArchivedItemUi.kt", "ArchiveUiMapper.kt"}),
    store_file="mvi/store/ArchiveStore.kt",
    # Store State stays semantic: plain strings and counts, never a generated handle or an Int id.
    state_fields=(
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
    ),
    resource_wrapper_readers=frozenset({"mvi/handler/ArchivePagingHandler.kt"}),
    preview_subjects={
        "ui/ArchiveScreen.kt": "ArchiveScreen",
        "ui/components/ArchivedItemRow.kt": "ArchivedItemRow",
    },
    # The only suppressions the target may carry: two inherited production annotations and the three
    # Native test-name annotations §4.4 authorizes. Anything else is a new suppression.
    suppressions={
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
    },
    # The exact portable test-name inventory: 25 inherited identities plus the one Native scene.
    test_names={
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
    },
    golden_test=(
        "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/archive/golden/ArchiveGoldenTest.kt"
    ),
    golden_methods=[
        "rowExercise",
        "rowTraining",
        "rowClamped",
        "pagingLoading",
        "pagingError",
        "screenExercisesNoRows",
        "screenTrainingsNoRows",
    ],
    device_test=(
        "src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/archive/ArchiveScreenTest.kt"
    ),
    device_fragments=(
        "@Smoke",
        '@Ignore("Awaiting feature rewrite — see GH issue #93 for coverage scope.")',
        "fun pendingFeatureRewrite()",
    ),
    feature_class="ArchiveFeature",
    graph_interface="ArchiveGraph",
    graph_function="archiveGraph",
    factory_accessor="archiveGraphFactory",
    create_method="createArchiveGraph",
    store_impl="ArchiveStoreImpl",
    store_accessor="archiveStore",
    feature_file="di/ArchiveFeature.kt",
    graph_file="ui/ArchiveGraph.kt",
    identity_test=Path(
        "app/app/src/test/kotlin/io/github/stslex/workeeper/di/ArchiveExtensionIdentityTest.kt"
    ),
    identity_fragments=(
        "private fun AppGraph.archive(): ArchiveGraph = archiveGraphFactory.createArchiveGraph()",
    ),
    identity_names=[
        "extension resolves the store through the parent graph",
        "store's app-scoped deps are the SAME instances the parent holds",
        "the two handler-store keys resolve to ONE instance",
        "the emitter the Store bound itself into is the one the handlers delegate through",
    ],
    identity_accessor=r"(?<!AppGraph)\.archive\(\)",
    extra_checks=(archive_item_ui_contract, archive_day_month_contract),
    # D5: the generic regex now sees formatDayMonth, so archive declares its one date read.
    resource_wrapper_dates={"mvi/handler/ArchivePagingHandler.kt": {"formatDayMonth": 1}},
)


def data_class_parameters(source: str, name: str) -> list:
    """`name: Type` (any default kept) for each parameter of `data class <name>(`; None if absent."""
    match = re.search(rf"\bdata\s+class\s+{re.escape(name)}\s*\(", source)
    if not match:
        return None
    depth = 0
    for index in range(match.end() - 1, len(source)):
        if source[index] == "(":
            depth += 1
        elif source[index] == ")":
            depth -= 1
            if depth == 0:
                body = source[match.end():index]
                break
    else:
        return None
    parameters: list[str] = []
    chunk = ""
    nesting = 0
    for char in body + ",":
        if char in "(<[{":
            nesting += 1
        elif char in ")>]}":
            nesting -= 1
        if char == "," and nesting == 0:
            text = " ".join(chunk.split())
            if text:
                declared = re.fullmatch(r"(?:val|var)\s+(\w+)\s*:\s*(.+)", text)
                parameters.append(f"{declared.group(1)}: {declared.group(2)}" if declared else text)
            chunk = ""
        else:
            chunk += char
    return parameters


def all_trainings_copy_contract(entry: FeatureEntry, common_sources: dict[Path, str]) -> list[str]:
    # D1 and D2 (kmp-phase-7-9-all-trainings-feature.md §5.2, §5.3, §12.1): the event carries
    # semantics, copy resolves in the mapper and the graph, and no handler resolves a resource.
    failures: list[str] = []
    module = entry.module

    def source_of(relative: str):
        # None only for an absent file: an existing empty file is still checked, and fails.
        path = entry.common_main / relative
        if path not in common_sources:
            failures.append(f"{module}: copy contract cannot read {path.relative_to(entry.root)}")
            return None
        return common_sources[path]

    store_relative = "mvi/store/AllTrainingsStore.kt"
    store_source = source_of(store_relative)
    if store_source is not None:
        expected_parameters = ["archivedCount: Int", "blockedNames: ImmutableList<String>"]
        parameters = data_class_parameters(
            strip_kotlin_comments(store_source), "ShowBulkDeleteSuccess"
        )
        if parameters != expected_parameters:
            failures.append(
                f"{module}: {(entry.common_main / store_relative).relative_to(entry.root)} "
                f"ShowBulkDeleteSuccess parameters must be exactly {expected_parameters}; "
                f"actual={parameters}"
            )
    for relative, fragments in (
        (
            "ui/AllTrainingsGraph.kt",
            (
                "internal suspend fun bulkArchiveMessage(",
                "bulkArchiveMessage(event.archivedCount, event.blockedNames)",
                "// TODO(tech-debt): UI mapping boundary — see documentation/tech-debt.md",
            ),
        ),
        (
            "mvi/mapper/TrainingListItemMapper.kt",
            (
                "internal suspend fun TrainingListItemDomain.toUi(",
                "Clock.System.now().toEpochMilliseconds()",
            ),
        ),
    ):
        source = source_of(relative)
        if source is None:
            continue
        for fragment in fragments:
            count = source.count(fragment)
            if count != 1:
                failures.append(
                    f"{module}: {(entry.common_main / relative).relative_to(entry.root)} copy "
                    f"contract requires {fragment!r} exactly once; found {count}"
                )
    handler_prefix = (entry.common_main / "mvi/handler").as_posix() + "/"
    handlers = [path for path in common_sources if path.as_posix().startswith(handler_prefix)]
    if not handlers:
        failures.append(f"{module}: copy contract found no file under mvi/handler/")
    for path in handlers:
        for token in ("getString(", "getPluralString(", "stringResource(", "pluralStringResource(", "Res."):
            if token in common_sources[path]:
                failures.append(
                    f"{module}: {path.relative_to(entry.root)} resolves copy in a handler: {token!r}"
                )
    return failures


def all_trainings_back_contract(entry: FeatureEntry, common_sources: dict[Path, str]) -> list[str]:
    # The portable BackHandler intercepts back only while selecting, and exits selection (§5.6).
    failures: list[str] = []
    graph_path = entry.common_main / "ui/AllTrainingsGraph.kt"
    source = common_sources.get(graph_path, "")
    for fragment in (
        "import androidx.compose.ui.backhandler.BackHandler",
        "ExperimentalComposeUiApi::class",
        "BackHandler(enabled = processor.state.value.interceptBack)",
        "processor.consume(Action.Click.OnSelectionExit)",
    ):
        count = source.count(fragment)
        if count != 1:
            failures.append(
                f"{entry.module}: {graph_path.relative_to(entry.root)} back contract requires "
                f"{fragment!r} exactly once; found {count}"
            )
    return failures


ALL_TRAININGS_ENTRY = FeatureEntry(
    module="feature:all-trainings",
    short_name="all-trainings",
    root=Path("feature/all-trainings"),
    package_dir="io/github/stslex/workeeper/feature/all_trainings",
    # Every plugin, resource-package, interop, dependency and apply line of §10, each exactly once;
    # kotlin("test") is counted by kotlin_test_count.
    build_fragments=(
        "alias(libs.plugins.convention.kmpComposeLibrary)",
        "alias(libs.plugins.metro)",
        "alias(libs.plugins.paparazzi)",
        'packageOfResClass = "io.github.stslex.workeeper.feature.all_trainings.resources"',
        "includeJavax()",
        'implementation(project(":core:core"))',
        'api(project(":core:ui:kit"))',
        'api(project(":core:ui:mvi"))',
        'api(project(":core:ui:navigation"))',
        'implementation(project(":core:data:exercise"))',
        "api(libs.cmp.ui)",
        "api(libs.androidx.paging.common)",
        "api(libs.coroutines.core)",
        "api(libs.kotlinx.collections.immutable)",
        "implementation(libs.androidx.compose.paging)",
        "implementation(libs.cmp.animation)",
        "implementation(libs.cmp.material.icons.core)",
        "implementation(libs.coroutine.test)",
        "implementation(libs.cmp.ui.test)",
        '"androidHostTestImplementation"(project(":core:ui:golden-harness"))',
        '"androidDeviceTestImplementation"(libs.bundles.android.test)',
        '"androidDeviceTestImplementation"(libs.androidx.compose.ui.test.junit4)',
        '"androidDeviceTestImplementation"(platform(libs.androidx.compose.bom))',
        '"androidDeviceTestImplementation"(libs.androidx.compose.ui.test.manifest)',
        '"androidDeviceTestImplementation"(project(":core:ui:test-utils"))',
        'apply(from = "$rootDir/gradle/golden-gate.gradle.kts")',
    ),
    kotlin_test_count=2,
    # Exact private EN/RU catalog, in file order: (tag, name, EN value, RU value). Plurals carry
    # ordered (quantity, value) pairs. Identifier set, order, placeholders and plural categories are
    # all contractual (kmp-phase-7-9-all-trainings-feature.md §3.4, whose table already carries the
    # 24 plural items made positional by decision P1, §5.1).
    catalog=(
        ("string", "feature_all_trainings_title", "Trainings", "Тренировки"),
        (
            "string",
            "feature_all_trainings_empty_headline",
            "Your trainings will appear here",
            "Здесь появятся тренировки",
        ),
        (
            "string",
            "feature_all_trainings_empty_supporting",
            "Build a template in advance, or start an empty one and add exercises as you go.",
            "Собери шаблон заранее или начни пустую и добавляй упражнения по ходу.",
        ),
        ("string", "feature_all_trainings_fab_create", "Create training", "Создать тренировку"),
        (
            "string",
            "feature_all_trainings_status_in_progress_format",
            "in progress · started %1$s ago",
            "в процессе · началась %1$s назад",
        ),
        ("string", "feature_all_trainings_status_last_format", "last: %1$s", "последняя: %1$s"),
        ("string", "feature_all_trainings_status_never", "never trained", "ещё не было"),
        ("string", "feature_all_trainings_relative_just_now", "just now", "только что"),
        ("string", "feature_all_trainings_relative_minutes_format", "%1$dm", "%1$d мин"),
        ("string", "feature_all_trainings_relative_hours_format", "%1$dh", "%1$d ч"),
        ("string", "feature_all_trainings_relative_days_format", "%1$dd", "%1$d дн"),
        (
            "plurals",
            "feature_all_trainings_exercise_count",
            (("one", "%1$d exercise"), ("other", "%1$d exercises")),
            (
                ("one", "%1$d упражнение"),
                ("few", "%1$d упражнения"),
                ("many", "%1$d упражнений"),
                ("other", "%1$d упражнения"),
            ),
        ),
        ("string", "feature_all_trainings_selection_close", "Close selection", "Закрыть выбор"),
        (
            "plurals",
            "feature_all_trainings_selected_count",
            (("one", "%1$d selected"), ("other", "%1$d selected")),
            (
                ("one", "выбрана %1$d"),
                ("few", "выбрано %1$d"),
                ("many", "выбрано %1$d"),
                ("other", "выбрано %1$d"),
            ),
        ),
        ("string", "feature_all_trainings_bulk_archive", "Archive", "В архив"),
        (
            "plurals",
            "feature_all_trainings_bulk_archive_success",
            (("one", "%1$d training archived"), ("other", "%1$d trainings archived")),
            (
                ("one", "%1$d тренировка в архиве"),
                ("few", "%1$d тренировки в архиве"),
                ("many", "%1$d тренировок в архиве"),
                ("other", "%1$d тренировки в архиве"),
            ),
        ),
        (
            "string",
            "feature_all_trainings_bulk_archive_partial_format",
            "Archived %1$d, blocked: %2$s",
            "В архиве: %1$d, не получилось: %2$s",
        ),
        (
            "string",
            "feature_all_trainings_bulk_archive_confirm_title",
            "Archive selected?",
            "Архивировать выбранные?",
        ),
        (
            "plurals",
            "feature_all_trainings_bulk_archive_confirm_body",
            (
                ("one", "%1$d training will move to archive. Restore from Settings → Archive."),
                ("other", "%1$d trainings will move to archive. Restore from Settings → Archive."),
            ),
            (
                ("one", "%1$d тренировка будет перенесена в архив. Восстановить можно в Настройках → Архив."),
                ("few", "%1$d тренировки будут перенесены в архив. Восстановить можно в Настройках → Архив."),
                ("many", "%1$d тренировок будут перенесены в архив. Восстановить можно в Настройках → Архив."),
                ("other", "%1$d тренировки будут перенесены в архив. Восстановить можно в Настройках → Архив."),
            ),
        ),
        (
            "string",
            "feature_all_trainings_bulk_archive_impact",
            "Reversible · history preserved",
            "Обратимо · история сохранится",
        ),
        ("string", "feature_all_trainings_paging_loading", "Loading", "Загружаю"),
        (
            "string",
            "feature_all_trainings_paging_error",
            "Couldn’t load more",
            "Не удалось загрузить дальше",
        ),
        ("string", "feature_all_trainings_paging_retry", "Retry", "Повторить"),
        ("string", "feature_all_trainings_empty_create", "Create a training", "Создать тренировку"),
        (
            "string",
            "feature_all_trainings_empty_start_blank",
            "Start an empty training",
            "Начать пустую тренировку",
        ),
        (
            "string",
            "feature_all_trainings_filtered_empty_headline",
            "Nothing matches these tags",
            "По этим тегам ничего нет",
        ),
        ("string", "feature_all_trainings_filtered_empty_clear", "Clear filter", "Сбросить фильтр"),
        (
            "string",
            "feature_all_trainings_selection_empty_headline",
            "Nothing here, but your selection is kept",
            "Здесь пусто, но выбор цел",
        ),
        (
            "string",
            "feature_all_trainings_selection_empty_supporting",
            "Your selection stays until you leave selection mode.",
            "Отметки останутся, пока не выйдешь из режима.",
        ),
        (
            "string",
            "feature_all_trainings_refresh_error",
            "Couldn’t load the list",
            "Не удалось загрузить список",
        ),
    ),
    resource_prefix="feature_all_trainings_",
    # TrainingListItemMapper.kt is deliberately absent: under D2 it is where copy resolves (§12.1).
    payload_files=frozenset({"AllTrainingsStore.kt", "TrainingListItemUi.kt", "TagUiModel.kt"}),
    store_file="mvi/store/AllTrainingsStore.kt",
    # Store State stays semantic: paging, tags, filter, selection and plain flags (§12.1).
    state_fields=(
        ("pagingUiState", "PagingUiState<PagingData<TrainingListItemUi>>"),
        ("availableTags", "ImmutableList<TagUiModel>"),
        ("activeTagFilter", "ImmutableSet<String>"),
        ("selectionMode", "SelectionMode"),
        ("pendingBulkDelete", "PendingBulkDelete?"),
        ("hasActiveSession", "Boolean"),
    ),
    # ResourceWrapper leaves the feature entirely (§5.2, §5.3).
    resource_wrapper_readers=frozenset(),
    preview_subjects={
        "ui/components/TagFilterRow.kt": "TagFilterRow",
        "ui/components/TrainingRow.kt": "TrainingRow",
        "ui/components/ArrivedEmptyStates.kt": "ArrivedEmptyStates",
        "ui/components/TrainingsEmptyState.kt": "TrainingsEmptyState",
    },
    # The only suppressions the target may carry: the four inherited production annotations of §3.1.
    # Nothing under commonTest: PR-N left no Native-illegal test name (§4.4).
    suppressions={
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/di/AllTrainingsFeature.kt": [
            '"UNCHECKED_CAST"',
        ],
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/handler/ClickHandler.kt": [
            '"TooManyFunctions"',
        ],
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/AllTrainingsGraph.kt": [
            '"LongMethod"',
        ],
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/AllTrainingsScreen.kt": [
            '"UNCHECKED_CAST"',
        ],
    },
    # The exact portable test-name inventory: the 49 identities of §3.2 plus the one Native scene
    # of §11.4.
    test_names={
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/domain/AllTrainingsInteractorImplTest.kt": [
            "archiveTrainings delegates to repository bulkArchive",
            "deleteTrainings returns target count and delegates",
            "canPermanentlyDelete delegates to repository",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/handler/ClickHandlerTest.kt": [
            "OnTrainingClick emits haptic and navigates to OpenDetail",
            "OnFabClick emits haptic and navigates to OpenCreate",
            "OnFabClick with selection fires no haptic and sets pendingBulkDelete",
            "OnTagFilterToggle adds tag when not selected",
            "OnTagFilterToggle removes tag when already selected",
            "OnSelectionExit clears selection mode",
            "OnBulkDeleteConfirm calls archiveTrainings and clears selection on success",
            "OnBulkDeleteDismiss clears pending delete",
            "entering selection by long press fires LongPress",
            "toggling an item inside selection fires ContextClick not LongPress",
            "untoggling an item inside selection fires ContextClick",
            "long press inside selection fires ContextClick not a second LongPress",
            "toggling a tag filter fires no haptic",
            "confirmed bulk archive fires Confirm",
            "OnClearTagFilter empties the whole filter in one act",
            "OnClearTagFilter fires no haptic",
            "OnClearTagFilter on an already-empty filter changes nothing",
            "OnEmptyCreate opens create and fires no haptic",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/handler/NavigationHandlerTest.kt": [
            "OpenDetail navigates to Screen Training with uuid",
            "OpenCreate navigates to Screen Training with null uuid",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/store/StartBlankGateTest.kt": [
            "no workout running — the drawn pair is whole",
            "a workout is running — the blank-start CTA withdraws",
            "before the first emission the CTA is withheld not offered",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/AllTrainingsClearanceTest.kt": [
            "list bottom clearance is the drawn 88 not the 72 it shipped with",
            "each drawn part is the value the mockup gives it",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/components/ListSurfaceTest.kt": [
            "rows win over everything",
            "an unsettled refresh with no rows is loading not empty",
            "loading outranks selection and the filter",
            "a failed first page is its own verdict",
            "no rows nothing done is the first-run empty",
            "a filter that matches nothing is its own state not the first-run empty",
            "selection outranks the filter because the selection block carries the filter recovery",
            "the crossfade covers the drawn blocks and neither non-block verdict",
            "selection empty and filtered empty are both in the crossfade so the pair transits",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/components/PagingTailKindTest.kt": [
            "appending draws the loading footer",
            "a failed page draws the error footer not silence",
            "exhausted draws no footer at all",
            "idle mid-list draws no footer either",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/components/TopBarModeTest.kt": [
            "off is the resting bar",
            "on is the selection bar",
            "different selections are one mode so the count cannot drive the crossfade",
            "an empty selection is still the selection bar",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/components/TrailingSlotKindTest.kt": [
            "at rest the slot promises a destination",
            "an unselected row in selection mode draws nothing and keeps its slot",
            "a selected row draws the check",
            "selected outranks selecting so the mark never blanks",
        ],
        "src/iosTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/AllTrainingsFeatureSceneIosTest.kt": [
            "resourcesPagingBranchesSelectionAndActionsRenderAndDispatch",
        ],
    },
    golden_test=(
        "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/golden/AllTrainingsGoldenTest.kt"
    ),
    # The 25 methods of §3.3, in source order; each renders light and dark (50 PNGs).
    golden_methods=[
        "rowPlain",
        "rowLongName",
        "rowClamped",
        "rowActive",
        "rowSelected",
        "rowActiveSelected",
        "tagFilterBand",
        "emptyState",
        "rowUnselectedInSelection",
        "pagingLoading",
        "pagingError",
        "confirmDialogContent",
        "screenList",
        "screenSelection",
        "screenFirstRunEmpty",
        "filteredEmpty",
        "selectionEmptyFiltered",
        "selectionEmptyUnfiltered",
        "coldOpenLoading",
        "coldOpenError",
        "screenColdOpen",
        "screenRefreshError",
        "screenFilteredEmpty",
        "screenSelectionEmpty",
        "screenSelectionEmptyUnfiltered",
    ],
    device_test=(
        "src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/AllTrainingsScreenTest.kt"
    ),
    device_fragments=(
        "@Smoke",
        '@Ignore("Awaiting feature rewrite — see GH issue #93 for coverage scope.")',
        "fun pendingFeatureRewrite()",
    ),
    feature_class="AllTrainingsFeature",
    graph_interface="AllTrainingsGraph",
    graph_function="allTrainingsGraph",
    factory_accessor="allTrainingsGraphFactory",
    create_method="createAllTrainingsGraph",
    store_impl="AllTrainingsStoreImpl",
    store_accessor="allTrainingsStore",
    feature_file="di/AllTrainingsFeature.kt",
    graph_file="ui/AllTrainingsGraph.kt",
    identity_test=Path(
        "app/app/src/test/kotlin/io/github/stslex/workeeper/di/AllTrainingsExtensionIdentityTest.kt"
    ),
    identity_fragments=(
        "private fun AppGraph.allTrainings(): AllTrainingsGraph = allTrainingsGraphFactory.createAllTrainingsGraph()",
    ),
    identity_names=[
        "extension resolves the store through the parent graph",
        "store's app-scoped deps are the SAME instances the parent holds",
    ],
    identity_accessor=r"(?<!AppGraph)\.allTrainings\(\)",
    # The one app device test that composes the graph directly passes the factory (§6).
    extra_root_fragments={
        "app/app/src/androidTest/kotlin/io/github/stslex/workeeper/app/AllTrainingsExtensionDbVisibilityTest.kt": (
            "MetroTestGraphHolder.graph.allTrainingsGraphFactory",
            "allTrainingsGraph(factory = factory)",
        ),
    },
    extra_checks=(all_trainings_copy_contract, all_trainings_back_contract),
)

def all_exercises_copy_contract(entry: FeatureEntry, common_sources: dict[Path, str]) -> list[str]:
    # D1 (kmp-phase-7-10-all-exercises-feature.md §5.2, §5.3, §12.1): copy outside composition
    # resolves through the injected StringResolver, never CMP's top-level functions, and the events
    # and the blocked dialog keep their shape. Every count is over comment-stripped source.
    failures: list[str] = []
    module = entry.module

    def source_of(relative: str):
        # None only for an absent file: an existing empty file is still checked, and fails.
        path = entry.common_main / relative
        if path not in common_sources:
            failures.append(f"{module}: copy contract cannot read {path.relative_to(entry.root)}")
            return None
        return strip_kotlin_comments(common_sources[path])

    def shown(relative: str) -> Path:
        return (entry.common_main / relative).relative_to(entry.root)

    def require(relative: str, source: str, fragment: str, expected: int) -> None:
        count = source.count(fragment)
        if count != expected:
            times = "once" if expected == 1 else f"{number_word(expected)} times"
            failures.append(
                f"{module}: {shown(relative)} copy contract requires {fragment!r} "
                f"exactly {times}; found {count}"
            )

    store_relative = "mvi/store/AllExercisesStore.kt"
    store_source = source_of(store_relative)
    if store_source is not None:
        for name, expected_parameters in (
            ("ShowPermanentDeleteSuccess", ["message: String"]),
            ("ShowBulkDeleteSuccess", ["message: String"]),
            (
                "BlockedArchiveDialog",
                ["archivedSummary: String?", "items: ImmutableList<BlockedArchiveItem>"],
            ),
        ):
            parameters = data_class_parameters(store_source, name)
            if parameters != expected_parameters:
                failures.append(
                    f"{module}: {shown(store_relative)} {name} parameters must be exactly "
                    f"{expected_parameters}; actual={parameters}"
                )

    mapper_relative = "mvi/mapper/AllExercisesUiMapper.kt"
    mapper_source = source_of(mapper_relative)
    if mapper_source is not None:
        for fragment, expected in (
            ("suspend fun ExerciseListItemDomain.toUi(", 1),
            ("suspend fun composeFooterLabel(", 1),
            ("suspend fun BulkArchiveResult.toBlockedArchiveDialog(", 1),
            ("Clock.System.now().toEpochMilliseconds()", 2),
            ("stringResolver.getString(", 3),
            ("stringResolver.getPluralString(", 7),
        ):
            require(mapper_relative, mapper_source, fragment, expected)

    click_relative = "mvi/handler/ClickHandler.kt"
    click_source = source_of(click_relative)
    if click_source is not None:
        for fragment in (
            "private val stringResolver: StringResolver,",
            "stringResolver.getString(",
            "stringResolver.getPluralString(",
        ):
            require(click_relative, click_source, fragment, 1)

    paging_relative = "mvi/handler/PagingHandler.kt"
    paging_source = source_of(paging_relative)
    if paging_source is not None:
        require(paging_relative, paging_source, "private val stringResolver: StringResolver,", 1)
        for fragment in ("getString(", "getPluralString("):
            require(paging_relative, paging_source, fragment, 0)

    top_level_import = re.compile(
        r"^import org\.jetbrains\.compose\.resources\.(getString|getPluralString)\b", re.MULTILINE
    )
    mvi_prefix = (entry.common_main / "mvi").as_posix() + "/"
    ui_prefix = (entry.common_main / "ui").as_posix() + "/"
    mvi_files = ui_files = 0
    for path, raw in common_sources.items():
        source = strip_kotlin_comments(raw)
        relative = path.relative_to(entry.root)
        for match in top_level_import.finditer(source):
            failures.append(
                f"{module}: {relative} imports CMP's top-level {match.group(1)}; read copy "
                "outside composition through the injected StringResolver"
            )
        posix = path.as_posix()
        if posix.startswith(mvi_prefix):
            mvi_files += 1
            for call in ("getString(", "getPluralString("):
                total = source.count(call)
                through_resolver = source.count(f"stringResolver.{call}")
                if total != through_resolver:
                    failures.append(
                        f"{module}: {relative} reads copy outside the StringResolver: {call!r} "
                        f"{total} time(s), {through_resolver} through stringResolver"
                    )
            for token in ("stringResource(", "pluralStringResource("):
                if token in source:
                    failures.append(
                        f"{module}: {relative} resolves composable copy under mvi/: {token!r}"
                    )
        if posix.startswith(ui_prefix):
            ui_files += 1
            for call in ("getString(", "getPluralString("):
                if call in source:
                    failures.append(
                        f"{module}: {relative} reads copy outside composition under ui/: {call!r}"
                    )
    if mvi_files == 0 or ui_files == 0:
        failures.append(
            f"{module}: copy contract walked {mvi_files} mvi/ and {ui_files} ui/ file(s); "
            "both must be non-empty"
        )
    return failures


def all_exercises_back_contract(entry: FeatureEntry, common_sources: dict[Path, str]) -> list[str]:
    # The portable BackHandler intercepts back only while selecting, and exits selection (§5.5).
    failures: list[str] = []
    graph_path = entry.common_main / entry.graph_file
    source = common_sources.get(graph_path, "")
    for fragment in (
        "import androidx.compose.ui.backhandler.BackHandler",
        "ExperimentalComposeUiApi::class",
        "BackHandler(enabled = processor.state.value.interceptBack)",
        "processor.consume(Action.Click.OnSelectionExit)",
    ):
        count = source.count(fragment)
        if count != 1:
            failures.append(
                f"{entry.module}: {graph_path.relative_to(entry.root)} back contract requires "
                f"{fragment!r} exactly once; found {count}"
            )
    return failures


ALL_EXERCISES_ENTRY = FeatureEntry(
    module="feature:all-exercises",
    short_name="all-exercises",
    root=Path("feature/all-exercises"),
    package_dir="io/github/stslex/workeeper/feature/all_exercises",
    # Every plugin, resource-package, interop, dependency and apply line of §10, each exactly once;
    # kotlin("test") is counted by kotlin_test_count. Decision D5 (§19) adds commonTest's
    # core:data:database edge for the domain suite's in-file ExerciseRepository fake.
    build_fragments=(
        "alias(libs.plugins.convention.kmpComposeLibrary)",
        "alias(libs.plugins.metro)",
        "alias(libs.plugins.paparazzi)",
        'packageOfResClass = "io.github.stslex.workeeper.feature.all_exercises.resources"',
        "includeJavax()",
        'implementation(project(":core:core"))',
        'api(project(":core:ui:kit"))',
        'api(project(":core:ui:mvi"))',
        'api(project(":core:ui:navigation"))',
        'implementation(project(":core:data:exercise"))',
        "api(libs.cmp.ui)",
        "api(libs.androidx.paging.common)",
        "api(libs.coroutines.core)",
        "api(libs.kotlinx.collections.immutable)",
        "implementation(libs.androidx.compose.paging)",
        "implementation(libs.cmp.animation)",
        "implementation(libs.cmp.material.icons.core)",
        "implementation(libs.coroutine.test)",
        'implementation(project(":core:data:database"))',
        "implementation(libs.cmp.ui.test)",
        '"androidHostTestImplementation"(project(":core:ui:golden-harness"))',
        '"androidDeviceTestImplementation"(libs.bundles.android.test)',
        '"androidDeviceTestImplementation"(libs.androidx.compose.ui.test.junit4)',
        '"androidDeviceTestImplementation"(platform(libs.androidx.compose.bom))',
        '"androidDeviceTestImplementation"(libs.androidx.compose.ui.test.manifest)',
        '"androidDeviceTestImplementation"(project(":core:ui:test-utils"))',
        'apply(from = "$rootDir/gradle/golden-gate.gradle.kts")',
    ),
    kotlin_test_count=2,
    # Exact private EN/RU catalog, in file order: (tag, name, EN value, RU value). Plurals carry
    # ordered (quantity, value) pairs. Identifier set, order, placeholders and plural categories are
    # all contractual (kmp-phase-7-10-all-exercises-feature.md §3.4, whose table already carries the
    # 48 plural items made positional by decision P1, §5.1, and decodes the character references
    # of permanent_delete_title, permanent_delete_body and blocked_archive_title).
    catalog=(
        ("string", "feature_all_exercises_title", "Exercises", "Упражнения"),
        (
            "string",
            "feature_all_exercises_empty_headline",
            "Your exercises will appear here",
            "Здесь появятся твои упражнения",
        ),
        (
            "string",
            "feature_all_exercises_empty_supporting",
            "Add the first one — after that you can put it into any training.",
            "Добавь первое — дальше его можно будет класть в любую тренировку.",
        ),
        ("string", "feature_all_exercises_empty_create", "Add exercise", "Добавить упражнение"),
        ("string", "feature_all_exercises_fab_create", "Create exercise", "Создать упражнение"),
        ("string", "feature_all_exercises_type_weighted", "with weight", "с весом"),
        ("string", "feature_all_exercises_type_weightless", "bodyweight", "без веса"),
        ("string", "feature_all_exercises_paging_loading", "Loading", "Загружаю"),
        (
            "string",
            "feature_all_exercises_paging_error",
            "Couldn’t load more",
            "Не удалось загрузить дальше",
        ),
        ("string", "feature_all_exercises_paging_retry", "Retry", "Повторить"),
        (
            "string",
            "feature_all_exercises_permanent_delete_title",
            "Delete ‘%1$s’ permanently?",
            "Удалить «%1$s» навсегда?",
        ),
        (
            "string",
            "feature_all_exercises_permanent_delete_body",
            "This exercise has no history and isn’t used. This cannot be undone.",
            "Упражнение не использовалось и не имеет истории. Это действие необратимо.",
        ),
        (
            "string",
            "feature_all_exercises_permanent_delete_impact",
            "No history affected",
            "История не пострадает",
        ),
        ("string", "feature_all_exercises_permanent_delete_confirm", "Delete", "Удалить"),
        (
            "string",
            "feature_all_exercises_permanent_delete_success",
            "Exercise deleted",
            "Упражнение удалено",
        ),
        (
            "plurals",
            "feature_all_exercises_session_count",
            (("one", "%1$d session"), ("other", "%1$d sessions")),
            (
                ("one", "%1$d сессия"),
                ("few", "%1$d сессии"),
                ("many", "%1$d сессий"),
                ("other", "%1$d сессии"),
            ),
        ),
        (
            "plurals",
            "feature_all_exercises_linked_trainings_count",
            (("one", "in %1$d training"), ("other", "in %1$d trainings")),
            (
                ("one", "в %1$d тренировке"),
                ("few", "в %1$d тренировках"),
                ("many", "в %1$d тренировках"),
                ("other", "в %1$d тренировках"),
            ),
        ),
        ("string", "feature_all_exercises_last_trained_just_now", "last just now", "только что"),
        (
            "plurals",
            "feature_all_exercises_last_trained_minutes",
            (("one", "last %1$dm ago"), ("other", "last %1$dm ago")),
            (
                ("one", "%1$d мин назад"),
                ("few", "%1$d мин назад"),
                ("many", "%1$d мин назад"),
                ("other", "%1$d мин назад"),
            ),
        ),
        (
            "plurals",
            "feature_all_exercises_last_trained_hours",
            (("one", "last %1$dh ago"), ("other", "last %1$dh ago")),
            (
                ("one", "%1$d ч назад"),
                ("few", "%1$d ч назад"),
                ("many", "%1$d ч назад"),
                ("other", "%1$d ч назад"),
            ),
        ),
        (
            "plurals",
            "feature_all_exercises_last_trained_days",
            (("one", "last %1$dd ago"), ("other", "last %1$dd ago")),
            (
                ("one", "%1$d д назад"),
                ("few", "%1$d д назад"),
                ("many", "%1$d д назад"),
                ("other", "%1$d д назад"),
            ),
        ),
        ("string", "feature_all_exercises_footer_separator", "·", "·"),
        ("string", "feature_all_exercises_selection_close", "Close selection", "Закрыть выбор"),
        (
            "plurals",
            "feature_all_exercises_selected_count",
            (("one", "%1$d selected"), ("other", "%1$d selected")),
            (
                ("one", "выбрано %1$d"),
                ("few", "выбрано %1$d"),
                ("many", "выбрано %1$d"),
                ("other", "выбрано %1$d"),
            ),
        ),
        ("string", "feature_all_exercises_bulk_archive", "Archive", "В архив"),
        (
            "plurals",
            "feature_all_exercises_bulk_archive_success",
            (("one", "%1$d exercise archived"), ("other", "%1$d exercises archived")),
            (
                ("one", "%1$d упражнение в архиве"),
                ("few", "%1$d упражнения в архиве"),
                ("many", "%1$d упражнений в архиве"),
                ("other", "%1$d упражнения в архиве"),
            ),
        ),
        (
            "string",
            "feature_all_exercises_bulk_archive_confirm_title",
            "Archive selected?",
            "Архивировать выбранные?",
        ),
        (
            "plurals",
            "feature_all_exercises_bulk_archive_confirm_body",
            (("one", "%1$d exercise will move to archive. Restore from Settings → Archive."), ("other", "%1$d exercises will move to archive. Restore from Settings → Archive.")),
            (
                ("one", "%1$d упражнение будет перенесено в архив. Восстановить можно в Настройках → Архив."),
                ("few", "%1$d упражнения будут перенесены в архив. Восстановить можно в Настройках → Архив."),
                ("many", "%1$d упражнений будут перенесены в архив. Восстановить можно в Настройках → Архив."),
                ("other", "%1$d упражнения будут перенесены в архив. Восстановить можно в Настройках → Архив."),
            ),
        ),
        (
            "string",
            "feature_all_exercises_bulk_archive_impact",
            "Reversible · history preserved",
            "Обратимо · история сохранится",
        ),
        (
            "string",
            "feature_all_exercises_blocked_archive_title",
            "Some couldn’t be archived",
            "Не удалось архивировать",
        ),
        (
            "string",
            "feature_all_exercises_blocked_archive_used_in_format",
            "used in %1$s",
            "в тренировках: %1$s",
        ),
        (
            "plurals",
            "feature_all_exercises_blocked_archive_more",
            (("one", "+%1$d more"), ("other", "+%1$d more")),
            (
                ("one", "и ещё %1$d"),
                ("few", "и ещё %1$d"),
                ("many", "и ещё %1$d"),
                ("other", "и ещё %1$d"),
            ),
        ),
        (
            "string",
            "feature_all_exercises_blocked_archive_next_step",
            "These are still in active trainings. Remove them from those trainings first, then archive.",
            "Они используются в активных тренировках. Сначала уберите их оттуда, затем архивируйте.",
        ),
        ("string", "feature_all_exercises_blocked_archive_confirm", "Got it", "Понятно"),
        (
            "string",
            "feature_all_exercises_filtered_empty_headline",
            "Nothing matches these tags",
            "По этим тегам ничего нет",
        ),
        ("string", "feature_all_exercises_filtered_empty_clear", "Clear filter", "Сбросить фильтр"),
        (
            "string",
            "feature_all_exercises_selection_empty_headline",
            "Nothing here, but your selection is kept",
            "Здесь пусто, но выбор цел",
        ),
        (
            "string",
            "feature_all_exercises_selection_empty_supporting",
            "Your selection stays until you leave selection mode.",
            "Отметки останутся, пока не выйдешь из режима.",
        ),
        (
            "string",
            "feature_all_exercises_refresh_error",
            "Couldn’t load the list",
            "Не удалось загрузить список",
        ),
    ),
    resource_prefix="feature_all_exercises_",
    # AllExercisesUiMapper.kt, ClickHandler.kt and PagingHandler.kt are deliberately absent: under D1
    # they resolve copy through the injected StringResolver (§12.1).
    payload_files=frozenset(
        {"AllExercisesStore.kt", "ExerciseUiModel.kt", "ExerciseTypeUiModel.kt", "TagUiModel.kt"}
    ),
    store_file="mvi/store/AllExercisesStore.kt",
    # Store State stays semantic: paging, tags, filter, the pending dialogs, selection, and the
    # blocked dialog's pre-formatted text (§12.1).
    state_fields=(
        ("pagingUiState", "PagingUiState<PagingData<ExerciseUiModel>>"),
        ("availableTags", "ImmutableList<TagUiModel>"),
        ("activeTagFilter", "ImmutableSet<String>"),
        ("pendingPermanentDelete", "PendingDelete?"),
        ("selectionMode", "SelectionMode"),
        ("pendingBulkDelete", "PendingBulkDelete?"),
        ("blockedArchiveDialog", "BlockedArchiveDialog?"),
    ),
    # ResourceWrapper leaves the feature entirely (§5.2).
    resource_wrapper_readers=frozenset(),
    preview_subjects={
        "ui/components/TagFilterRow.kt": "TagFilterRow",
        "ui/components/ExerciseRow.kt": "ExerciseRow",
        "ui/components/ArrivedEmptyStates.kt": "ArrivedEmptyStates",
        "ui/components/ExercisesEmptyState.kt": "ExercisesEmptyState",
    },
    # The only suppressions the target may carry: the two inherited production annotations of §3.1.
    # Nothing under commonTest: the inert MagicNumber suppression left the mapper suite (§0.2).
    suppressions={
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/di/AllExercisesFeature.kt": [
            '"UNCHECKED_CAST"',
        ],
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/handler/ClickHandler.kt": [
            '"TooManyFunctions"',
        ],
    },
    # The exact portable test-name inventory: the 66 identities of §3.2 plus the one Native scene
    # of §11.4.
    test_names={
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/domain/AllExercisesInteractorImplTest.kt": [
            "archiveExercise returns Success when no active trainings",
            "archiveExercise returns Blocked when active trainings exist",
            "restoreExercise delegates to repository",
            "canPermanentlyDelete delegates to repository",
            "permanentlyDelete delegates to repository",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/handler/ClickHandlerTest.kt": [
            "OnExerciseClick emits haptic and navigates to OpenDetail",
            "OnFabClick emits haptic and navigates to OpenCreate",
            "OnTagFilterToggle adds tag when not selected",
            "OnTagFilterToggle removes tag when already selected",
            "OnCancelPermanentDelete clears pending delete",
            "OnConfirmPermanentDelete with no pending is no-op",
            "OnConfirmPermanentDelete with pending clears state and emits Confirm haptic",
            "OnBulkDelete fires no haptic — the morph is silent",
            "OnTagFilterToggle fires no haptic",
            "OnBulkDeleteConfirm emits Confirm haptic",
            "OnBulkDelete with selection opens confirm dialog",
            "OnBulkDeleteConfirm calls bulkArchive and clears selection on success",
            "OnBulkDeleteConfirm opens blocked dialog and no snackbar when all blocked",
            "OnBulkDeleteConfirm opens blocked dialog with archived summary on partial block",
            "OnBlockedArchiveDismiss clears the blocked dialog",
            "OnBulkDeleteDismiss clears pending",
            "OnClearTagFilter empties the whole filter in one act",
            "OnClearTagFilter fires no haptic",
            "OnClearTagFilter on an already-empty filter changes nothing",
            "entering selection by long press fires LongPress",
            "long press inside selection fires ContextClick not a second LongPress",
            "selection toggle fires ContextClick",
            "selection exit fires ContextClick",
            "OnEmptyCreate opens create and fires no haptic",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/handler/NavigationHandlerTest.kt": [
            "OpenDetail navigates to Screen Exercise with uuid",
            "OpenCreate navigates to Screen Exercise with null uuid",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/handler/PagingHandlerTest.kt": [
            "Init invokes observeAvailableTags via scope launch",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/mapper/AllExercisesUiMapperTest.kt": [
            "composeFooterLabel hides every segment when all values are zero or null",
            "composeFooterLabel renders all three segments when present",
            "composeFooterLabel hides session segment when count is zero",
            "composeFooterLabel hides linked-trainings segment when count is zero",
            "composeFooterLabel hides last-trained segment when value is null",
            "composeFooterLabel just-now segment when delta below one minute",
            "composeFooterLabel minute granularity for sub-hour deltas",
            "ExerciseListItemDomain toUi propagates non-zero stats into the model",
            "ExerciseListItemDomain toUi yields empty footer when stats are absent",
            "toBlockedArchiveDialog truncates trainings and sets archived summary on partial block",
            "toBlockedArchiveDialog omits archived summary and truncation when nothing archived",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/AllExercisesClearanceTest.kt": [
            "list bottom clearance is the drawn 88 not the 72 it shipped with",
            "each drawn part is the value the mockup gives it",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/components/ListSurfaceTest.kt": [
            "rows win over everything",
            "an unsettled refresh with no rows is loading not empty",
            "loading outranks selection and the filter",
            "a failed first page is its own verdict",
            "no rows nothing done is the first-run empty",
            "a filter that matches nothing is its own state not the first-run empty",
            "selection outranks the filter because the selection block carries the filter recovery",
            "the crossfade covers the drawn blocks and neither non-block verdict",
            "selection empty and filtered empty are both in the crossfade so the pair transits",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/components/PagingTailKindTest.kt": [
            "appending draws the loading footer",
            "a failed page draws the error footer not silence",
            "exhausted draws no footer at all",
            "idle mid-list draws no footer either",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/components/TopBarModeTest.kt": [
            "off is the resting bar",
            "on is the selection bar",
            "different selections are one mode so the count cannot drive the crossfade",
            "an empty selection is still the selection bar",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/components/TrailingSlotKindTest.kt": [
            "at rest the slot promises a destination",
            "an unselected row in selection mode draws nothing and keeps its slot",
            "a selected row draws the check",
            "selected outranks selecting so the mark never blanks",
        ],
        "src/iosTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/AllExercisesFeatureSceneIosTest.kt": [
            "resourcesPagingBranchesSelectionAndActionsRenderAndDispatch",
        ],
    },
    golden_test=(
        "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/golden/AllExercisesGoldenTest.kt"
    ),
    # The 26 methods of §3.3, in source order; each renders light and dark (52 PNGs).
    golden_methods=[
        "rowWeighted",
        "rowWeightless",
        "rowTypeIsolated",
        "rowLongName",
        "rowClamped",
        "rowSelected",
        "rowUnselectedInSelection",
        "tagFilterBand",
        "emptyState",
        "pagingLoading",
        "pagingError",
        "confirmDialogContent",
        "blockedArchiveDialogContent",
        "screenList",
        "screenSelection",
        "screenFirstRunEmpty",
        "filteredEmpty",
        "selectionEmptyFiltered",
        "selectionEmptyUnfiltered",
        "coldOpenLoading",
        "coldOpenError",
        "screenColdOpen",
        "screenRefreshError",
        "screenFilteredEmpty",
        "screenSelectionEmpty",
        "screenSelectionEmptyUnfiltered",
    ],
    device_test=(
        "src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/AllExercisesScreenTest.kt"
    ),
    # Archive's three, plus the @Regression identity and its resolver read of the label (D3, §11.3).
    device_fragments=(
        "@Smoke",
        '@Ignore("Awaiting feature rewrite — see GH issue #93 for coverage scope.")',
        "fun pendingFeatureRewrite()",
        "@Regression",
        "fun blockedArchiveDialogListsBlockingTrainings()",
        "ComposeStringResolver().getString(Res.string.feature_all_exercises_blocked_archive_confirm)",
    ),
    feature_class="AllExercisesFeature",
    graph_interface="AllExercisesGraph",
    graph_function="allExercisesGraph",
    factory_accessor="allExercisesGraphFactory",
    create_method="createAllExercisesGraph",
    store_impl="AllExercisesStoreImpl",
    store_accessor="allExercisesStore",
    feature_file="di/AllExercisesFeature.kt",
    graph_file="ui/AllExercisesGraph.kt",
    identity_test=Path(
        "app/app/src/test/kotlin/io/github/stslex/workeeper/di/AllExercisesExtensionIdentityTest.kt"
    ),
    identity_fragments=(
        "private fun AppGraph.allExercises(): AllExercisesGraph = allExercisesGraphFactory.createAllExercisesGraph()",
    ),
    identity_names=[
        "extension resolves the store through the parent graph",
        "store's app-scoped deps are the SAME instances the parent holds",
    ],
    identity_accessor=r"(?<!AppGraph)\.allExercises\(\)",
    extra_checks=(all_exercises_copy_contract, all_exercises_back_contract),
)

def past_session_copy_contract(entry: FeatureEntry, common_sources: dict[Path, str]) -> list[str]:
    # D1 and S2 (kmp-phase-7-11-past-session-feature.md §5.3, §5.4, §12.1): copy outside composition
    # resolves through the injected StringResolver, the mapper groups the tonnage itself with the
    # catalog separator, and the events, the State and PastSessionUiModel keep their shape. Every
    # count is over comment-stripped source.
    failures: list[str] = []
    module = entry.module

    def source_of(relative: str):
        # None only for an absent file: an existing empty file is still checked, and fails.
        path = entry.common_main / relative
        if path not in common_sources:
            failures.append(f"{module}: copy contract cannot read {path.relative_to(entry.root)}")
            return None
        return strip_kotlin_comments(common_sources[path])

    def shown(relative: str) -> Path:
        return (entry.common_main / relative).relative_to(entry.root)

    def require(relative: str, source: str, fragment: str, expected: int) -> None:
        count = source.count(fragment)
        if count != expected:
            times = "once" if expected == 1 else f"{number_word(expected)} times"
            failures.append(
                f"{module}: {shown(relative)} copy contract requires {fragment!r} "
                f"exactly {times}; found {count}"
            )

    def require_parameters(relative: str, source: str, name: str, expected: list) -> None:
        parameters = data_class_parameters(source, name)
        if parameters != expected:
            failures.append(
                f"{module}: {shown(relative)} {name} parameters must be exactly {expected}; "
                f"actual={parameters}"
            )

    store_relative = "mvi/store/PastSessionStore.kt"
    store_source = source_of(store_relative)
    if store_source is not None:
        require_parameters(store_relative, store_source, "HapticClick", ["type: HapticFeedbackType"])
        require_parameters(store_relative, store_source, "ShowError", ["errorType: ErrorType"])
        for fragment in (
            "data object DeletedSnackbar : Event",
            "data object SaveFailedSnackbar : Event",
        ):
            require(store_relative, store_source, fragment, 1)

    models_relative = "mvi/model/PastSessionUiModels.kt"
    models_source = source_of(models_relative)
    if models_source is not None:
        require_parameters(
            models_relative,
            models_source,
            "PastSessionUiModel",
            [
                "trainingName: String",
                "isAdhoc: Boolean",
                "finishedAtAbsoluteLabel: String",
                "durationLabel: String",
                "totalsLabel: String",
                "exercises: ImmutableList<PastExerciseUiModel>",
            ],
        )

    mapper_relative = "mvi/mapper/PastSessionUiMapper.kt"
    mapper_source = source_of(mapper_relative)
    if mapper_source is not None:
        for fragment, expected in (
            ("suspend fun SessionDetailDomain.toUi(", 1),
            ("private suspend fun buildTotalsLabel(", 1),
            ("Res.string.feature_past_session_tonnage_group_separator", 1),
            ("private fun Long.groupThousands(separator: String)", 1),
            ("roundedTonnage.groupThousands(separator)", 1),
            ("THOUSANDS_GROUP_SIZE = 3", 1),
            ("stringResolver.getString(", 5),
            ("stringResolver.getPluralString(", 2),
        ):
            require(mapper_relative, mapper_source, fragment, expected)

    common_relative = "mvi/handler/CommonHandler.kt"
    common_source = source_of(common_relative)
    if common_source is not None:
        for fragment in (
            "private val resourceWrapper: ResourceWrapper,",
            "private val stringResolver: StringResolver,",
            "toUi(",
        ):
            require(common_relative, common_source, fragment, 1)
        for fragment in ("getString(", "getPluralString("):
            require(common_relative, common_source, fragment, 0)

    top_level_import = re.compile(
        r"^import org\.jetbrains\.compose\.resources\.(getString|getPluralString)\b", re.MULTILINE
    )
    mvi_prefix = (entry.common_main / "mvi").as_posix() + "/"
    ui_prefix = (entry.common_main / "ui").as_posix() + "/"
    mvi_files = ui_files = 0
    for path, raw in common_sources.items():
        source = strip_kotlin_comments(raw)
        relative = path.relative_to(entry.root)
        for match in top_level_import.finditer(source):
            failures.append(
                f"{module}: {relative} imports CMP's top-level {match.group(1)}; read copy "
                "outside composition through the injected StringResolver"
            )
        posix = path.as_posix()
        if posix.startswith(mvi_prefix):
            mvi_files += 1
            for call in ("getString(", "getPluralString("):
                total = source.count(call)
                through_resolver = source.count(f"stringResolver.{call}")
                if total != through_resolver:
                    failures.append(
                        f"{module}: {relative} reads copy outside the StringResolver: {call!r} "
                        f"{total} time(s), {through_resolver} through stringResolver"
                    )
            for token in ("stringResource(", "pluralStringResource("):
                if token in source:
                    failures.append(
                        f"{module}: {relative} resolves composable copy under mvi/: {token!r}"
                    )
        if posix.startswith(ui_prefix):
            ui_files += 1
            for call in ("getString(", "getPluralString("):
                if call in source:
                    failures.append(
                        f"{module}: {relative} reads copy outside composition under ui/: {call!r}"
                    )
    if mvi_files == 0 or ui_files == 0:
        failures.append(
            f"{module}: copy contract walked {mvi_files} mvi/ and {ui_files} ui/ file(s); "
            "both must be non-empty"
        )
    return failures


def past_session_dependency_contract(
    entry: FeatureEntry,
    common_sources: dict[Path, str],
) -> list[str]:
    # D3 (§3.5, §12.1): core:data:database is no edge of the module — no main edge, no test edge.
    build_source = (entry.root / "build.gradle.kts").read_text(encoding="utf-8")
    if ":core:data:database" in build_source:
        return [
            f"{entry.module}: build must not depend on :core:data:database in any source set (D3)"
        ]
    return []


PAST_SESSION_ENTRY = FeatureEntry(
    module="feature:past-session",
    short_name="past-session",
    root=Path("feature/past-session"),
    package_dir="io/github/stslex/workeeper/feature/past_session",
    # Every plugin, resource-package, interop, dependency and apply line of §10, each exactly once;
    # kotlin("test") is counted by kotlin_test_count. D3: no core:data:database edge (see
    # past_session_dependency_contract).
    build_fragments=(
        "alias(libs.plugins.convention.kmpComposeLibrary)",
        "alias(libs.plugins.metro)",
        "alias(libs.plugins.paparazzi)",
        'packageOfResClass = "io.github.stslex.workeeper.feature.past_session.resources"',
        "includeJavax()",
        'implementation(project(":core:core"))',
        'implementation(project(":core:ui:kit"))',
        'api(project(":core:ui:mvi"))',
        'api(project(":core:ui:navigation"))',
        'api(project(":core:ui:plan-editor"))',
        'implementation(project(":core:data:exercise"))',
        "api(libs.cmp.ui)",
        "api(libs.coroutines.core)",
        "api(libs.kotlinx.collections.immutable)",
        "implementation(libs.cmp.animation)",
        "implementation(libs.cmp.material.icons.extended)",
        "implementation(libs.coroutine.test)",
        "implementation(libs.cmp.ui.test)",
        '"androidHostTestImplementation"(project(":core:ui:golden-harness"))',
        'apply(from = "$rootDir/gradle/golden-gate.gradle.kts")',
    ),
    kotlin_test_count=2,
    # Exact private EN/RU catalog, in file order: (tag, name, EN value, RU value), as §3.4 prints it
    # at exit — P1's twelve positional plural items and D1's tonnage_format and separator.
    catalog=(
        ("string", "feature_past_session_loading_title", "Past session", "Прошедшая сессия"),
        ("string", "feature_past_session_adhoc_label", "Ad-hoc workout", "Свободная тренировка"),
        (
            "string",
            "feature_past_session_error_not_found",
            "Session not found",
            "Сессия не найдена",
        ),
        (
            "string",
            "feature_past_session_error_load_failed",
            "Could not load session",
            "Не удалось загрузить сессию",
        ),
        ("string", "feature_past_session_action_retry", "Retry", "Повторить"),
        ("string", "feature_past_session_action_delete", "Delete", "Удалить"),
        (
            "string",
            "feature_past_session_delete_dialog_title",
            "Delete session?",
            "Удалить сессию?",
        ),
        (
            "string",
            "feature_past_session_delete_dialog_body",
            "This permanently removes the session and all its sets. The training template is unchanged.",
            "Сессия и все её подходы будут безвозвратно удалены. Шаблон тренировки не изменится.",
        ),
        ("string", "feature_past_session_deleted_snackbar", "Session deleted", "Сессия удалена"),
        (
            "string",
            "feature_past_session_save_failed_snackbar",
            "Could not save change — try again",
            "Не удалось сохранить — попробуйте ещё раз",
        ),
        ("string", "feature_past_session_skipped_line", "skipped", "пропущено"),
        ("string", "feature_past_session_no_sets", "No sets logged", "Подходы не записаны"),
        ("string", "feature_past_session_section_logged", "Logged", "Записано"),
        ("string", "feature_past_session_section_editable", "editable", "можно править"),
        ("string", "feature_past_session_finished_at_label", "Finished · %1$s", "Завершена · %1$s"),
        ("string", "feature_past_session_action_more", "More", "Ещё"),
        ("string", "feature_past_session_menu_delete_session", "Delete session", "Удалить сессию"),
        ("string", "feature_past_session_totals_format", "%1$s · %2$s", "%1$s · %2$s"),
        (
            "string",
            "feature_past_session_totals_format_with_tonnage",
            "%1$s · %2$s · %3$s",
            "%1$s · %2$s · %3$s",
        ),
        ("string", "feature_past_session_tonnage_format", "%1$s kg", "%1$s кг"),
        ("string", "feature_past_session_tonnage_group_separator", ",", "\u00a0"),
        (
            "plurals",
            "feature_past_session_exercises_count",
            (("one", "%1$d exercise"), ("other", "%1$d exercises")),
            (
                ("one", "%1$d упражнение"),
                ("few", "%1$d упражнения"),
                ("many", "%1$d упражнений"),
                ("other", "%1$d упражнений"),
            ),
        ),
        (
            "plurals",
            "feature_past_session_sets_count",
            (("one", "%1$d set"), ("other", "%1$d sets")),
            (
                ("one", "%1$d подход"),
                ("few", "%1$d подхода"),
                ("many", "%1$d подходов"),
                ("other", "%1$d подходов"),
            ),
        ),
    ),
    resource_prefix="feature_past_session_",
    # PastSessionUiMapper.kt and CommonHandler.kt are deliberately absent: they resolve copy (§12.1).
    payload_files=frozenset({"DialogState.kt", "PastSessionStore.kt", "PastSessionUiModels.kt"}),
    store_file="mvi/store/PastSessionStore.kt",
    state_fields=(
        ("sessionUuid", "String"),
        ("phase", "Phase"),
        ("hasResolved", "Boolean"),
        ("expandedExerciseUuids", "ImmutableSet<String>"),
        ("dialogState", "DialogState"),
        ("bottomSheetState", "BottomSheetState"),
    ),
    # S2: the mapper keeps the one session-date read; CommonHandler only passes ResourceWrapper on.
    resource_wrapper_readers=frozenset({"mvi/handler/CommonHandler.kt", "mvi/mapper/PastSessionUiMapper.kt"}),
    preview_subjects={},
    # The only suppression the target may carry: the inherited processor cast of §3.1.
    suppressions={
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/di/PastSessionFeature.kt": [
            '"UNCHECKED_CAST"',
        ],
    },
    # The 40 portable identities of §3.2, the host overflow gate, and the Native scene of §11.4.
    test_names={
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/past_session/domain/PastSessionInteractorImplTest.kt": [
            "observeDetailWithPrs emits null when session is missing",
            "observeDetailWithPrs combines fresh detail with PR set uuids",
            "updateSet maps domain SetDomain to data SetsDataModel and forwards",
            "deleteSession forwards to SessionRepository deleteSession",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/handler/ClickHandlerTest.kt": [
            "OnDeleteClick opens the confirmation and closes the sheet it came from",
            "OnSessionMenuClick opens the overflow sheet with a haptic",
            "OnSheetDismiss hides the sheet",
            "OnPrTagClick opens the explainer and OnPrExplainerDismiss hides it",
            "OnDeleteClick while the explainer is shown replaces the dialog",
            "OnDeleteConfirm deletes the session closes dialog and navigates back with snackbar",
            "OnRetryLoad consumes Init",
            "OnSetTypeChange updates the row and persists the changed type",
            "OnExerciseHeaderClick expands exactly the tapped card and touches nothing else",
            "OnExerciseHeaderClick collapses the tapped card and only that card",
            "OnExerciseHeaderClick with an unknown uuid changes nothing",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/handler/CommonHandlerTest.kt": [
            "Init success loads mapped detail",
            "Init with missing session switches to SessionNotFound phase",
            "Init failure switches to LoadFailed phase",
            "Init flags PR set with isPersonalRecord",
            "Init seeds the first card open on the first Loaded emission",
            "a re-emission carries the user's open set instead of reseeding",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/handler/InputHandlerTest.kt": [
            "OnSetWeightChange marks invalid weight and skips persist",
            "OnSetRepsChange clears reps error when value becomes valid",
            "rapid valid inputs debounce to a single persist with the latest value",
            "invalid follow-up input cancels in-flight persist",
            "persist failure reverts to the last saved set and emits SaveFailedSnackbar",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/handler/NavigationHandlerTest.kt": [
            "Back triggers popBack",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/mapper/PastSessionUiMapperTest.kt": [
            "mapper covers adhoc header skipped rows weighted volume and no-set exercise",
            "mapper marks the PR-bearing set with isPersonalRecord",
            "mapper leaves all sets unflagged when prSetUuids is empty",
            "mapper sums tonnage over weighted sets",
            "mapper excludes a weightless exercise carrying a residual weight from tonnage",
            "mapper omits the tonnage term when a weighted session logged no weights",
            "mapper builds the collapsed summary per exercise type",
            "mapper excludes unfilled sets from the collapsed summary",
            "mapper marks a weighted set with no logged weight instead of printing bare reps",
            "first Loaded state seeds exactly the first card open",
            "first Loaded state with no exercises seeds nothing",
            "Loaded to Loaded carries the previous open set pruned to live exercises",
            "a non-Loaded next phase is returned untouched",
        ],
        "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/past_session/gate/PastSetRowOverflowGateTest.kt": [
            "weightedRowValuesFitTheirSlots",
        ],
        "src/iosTest/kotlin/io/github/stslex/workeeper/feature/past_session/PastSessionFeatureSceneIosTest.kt": [
            "resourcesTotalsPhasesSheetDialogsAndActionsRenderAndDispatch",
        ],
    },
    golden_test=(
        "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/past_session/golden/PastSessionGoldenTest.kt"
    ),
    # The 15 methods of §3.3, in source order; each renders light and dark (30 PNGs).
    golden_methods=[
        "screenLoaded",
        "screenError",
        "topbar",
        "sheetSessionMenu",
        "header",
        "headerWithoutTonnage",
        "cardWithSets",
        "cardCollapsed",
        "cardDoubleDigitIndex",
        "cardSkippedEmpty",
        "cardSkippedWithSets",
        "setRowPlain",
        "setRowPersonalRecord",
        "setRowWeightless",
        "setRowInvalidReps",
    ],
    # No device test (§11.3): an empty device_test reads no file (§12.0).
    device_test="",
    device_fragments=(),
    feature_class="PastSessionFeature",
    graph_interface="PastSessionGraph",
    graph_function="pastSessionGraph",
    factory_accessor="pastSessionGraphFactory",
    create_method="createPastSessionGraph",
    store_impl="PastSessionStoreImpl",
    store_accessor="pastSessionStore",
    feature_file="di/PastSessionFeature.kt",
    graph_file="ui/PastSessionGraph.kt",
    identity_test=Path(
        "app/app/src/test/kotlin/io/github/stslex/workeeper/di/PastSessionExtensionIdentityTest.kt"
    ),
    identity_fragments=(
        "private fun AppGraph.pastSession(sessionUuid: String): PastSessionGraph = pastSessionGraphFactory.createPastSessionGraph(Screen.PastSession(sessionUuid = sessionUuid))",
    ),
    identity_names=[
        "extension resolves the store through the parent graph",
        "store's app-scoped deps are the SAME instances the parent holds",
        "the extension inherits the IO dispatcher key and not the Default one",
        "each extension carries its own route arg into the store state",
    ],
    # Each identity reaches the graph once through the accessor; the fourth's session-2 call is not
    # counted.
    identity_accessor=r'(?<!AppGraph)\.pastSession\("session-1"\)',
    extra_checks=(past_session_copy_contract, past_session_dependency_contract),
    # Shape B: the retained factory takes the route argument.
    create_args="screen",
    # S1: the 15 previews stay verbatim, pinned by file and exact arguments (§5.4).
    preview_inventory={
        "ui/components/PastSetEditRow.kt": [
            'name = "Weighted Light"',
            'name = "Record Dark"',
            'name = "Weightless Dark"',
            'name = "Error"',
        ],
        "ui/components/DeleteConfirmDialog.kt": [
            'name = "Light"',
            'name = "Dark"',
        ],
        "ui/components/PastSessionHeader.kt": [
            'name = "Light"',
            'name = "Dark"',
        ],
        "ui/components/PastExerciseCard.kt": [
            'name = "Expanded — Light"',
            'name = "Collapsed — Dark"',
            'name = "Skipped — Collapsed"',
        ],
        "ui/PastSessionScreen.kt": [
            'name = "Loaded — Light"',
            'name = "Loaded — Dark"',
            'name = "Loading"',
            'name = "Error"',
        ],
    },
    # S2: the one ResourceWrapper read, the session date in the mapper.
    resource_wrapper_dates={"mvi/mapper/PastSessionUiMapper.kt": {"formatMediumDate": 1}},
)

def home_copy_contract(entry: FeatureEntry, common_sources: dict[Path, str]) -> list[str]:
    # §5.3 and §12.1 (kmp-phase-7-12-home-feature.md): copy outside composition resolves through the
    # injected StringResolver, ResourceWrapper keeps only the two relative times (S1), and the
    # event, the State and the row/picker payloads keep their shape. Every count is over
    # comment-stripped source.
    failures: list[str] = []
    module = entry.module

    def source_of(relative: str):
        # None only for an absent file: an existing empty file is still checked, and fails.
        path = entry.common_main / relative
        if path not in common_sources:
            failures.append(f"{module}: copy contract cannot read {path.relative_to(entry.root)}")
            return None
        return strip_kotlin_comments(common_sources[path])

    def shown(relative: str) -> Path:
        return (entry.common_main / relative).relative_to(entry.root)

    def require(relative: str, source: str, fragment: str, expected: int) -> None:
        count = source.count(fragment)
        if count != expected:
            times = "once" if expected == 1 else f"{number_word(expected)} times"
            failures.append(
                f"{module}: {shown(relative)} copy contract requires {fragment!r} "
                f"exactly {times}; found {count}"
            )

    def require_parameters(relative: str, source: str, name: str, expected: list) -> None:
        parameters = data_class_parameters(source, name)
        if parameters != expected:
            failures.append(
                f"{module}: {shown(relative)} {name} parameters must be exactly {expected}; "
                f"actual={parameters}"
            )

    # 1. The event, the conflict and the row/picker payloads keep their shape.
    store_relative = "mvi/store/HomeStore.kt"
    store_source = source_of(store_relative)
    if store_source is not None:
        require_parameters(store_relative, store_source, "HapticClick", ["type: HapticFeedbackType"])
        require_parameters(
            store_relative,
            store_source,
            "ConflictInfo",
            [
                "activeSessionUuid: String",
                "requestedTrainingUuid: String",
                "activeSessionName: String",
                "progressLabel: String",
            ],
        )
    recent_relative = "mvi/model/RecentSessionItem.kt"
    recent_source = source_of(recent_relative)
    if recent_source is not None:
        require_parameters(
            recent_relative,
            recent_source,
            "RecentSessionItem",
            [
                "sessionUuid: String",
                "trainingName: String",
                "isAdhoc: Boolean",
                "finishedAtRelativeLabel: String",
                "durationLabel: String",
                "statsLabel: String",
            ],
        )
    picker_relative = "mvi/model/PickerTrainingItem.kt"
    picker_source = source_of(picker_relative)
    if picker_source is not None:
        require_parameters(
            picker_relative,
            picker_source,
            "PickerTrainingItem",
            [
                "trainingUuid: String",
                "name: String",
                "exerciseCountLabel: String",
                "lastSessionRelativeLabel: String?",
            ],
        )

    # 2. The mapper: five suspend functions over the resolver, twelve string and seven plural reads.
    mapper_relative = "mvi/mapper/HomeUiMapper.kt"
    mapper_source = source_of(mapper_relative)
    if mapper_source is not None:
        for fragment, expected in (
            ("suspend fun StartCardReadoutDomain.toUi(stringResolver: StringResolver)", 1),
            ("suspend fun WeekReadoutDomain.toUi(stringResolver: StringResolver)", 1),
            ("suspend fun ActiveSessionWithStatsDomain.toUi(", 1),
            ("suspend fun RecentSessionDomain.toRecentItem(", 1),
            ("suspend fun List<TrainingListItemDomain>.toPickerItems(", 1),
            ("stringResolver.getString(", 12),
            ("stringResolver.getPluralString(", 7),
        ):
            require(mapper_relative, mapper_source, fragment, expected)

    # 3–5. The three handlers that inject the resolver.
    for relative, fragments, string_reads, absent in (
        (
            "mvi/handler/ClickHandler.kt",
            (
                "private val resourceWrapper: ResourceWrapper,",
                "private val stringResolver: StringResolver,",
                "toPickerItems(now, resourceWrapper, stringResolver)",
            ),
            2,
            ("getPluralString(",),
        ),
        (
            "mvi/handler/CommonHandler.kt",
            (
                "private val stringResolver: StringResolver,",
                "toUi(now, stringResolver)",
                "readout.toUi(stringResolver)",
            ),
            None,
            ("getString(", "getPluralString("),
        ),
        (
            "mvi/handler/PagingHandler.kt",
            (
                "private val resourceWrapper: ResourceWrapper,",
                "private val stringResolver: StringResolver,",
                "toRecentItem(now, resourceWrapper, stringResolver)",
            ),
            None,
            ("getString(", "getPluralString("),
        ),
    ):
        source = source_of(relative)
        if source is None:
            continue
        for fragment in fragments:
            require(relative, source, fragment, 1)
        if string_reads is not None:
            require(relative, source, "stringResolver.getString(", string_reads)
        for fragment in absent:
            require(relative, source, fragment, 0)

    # 6. 7.11's rules 4–7: no top-level CMP reads, mvi/ reads only through the resolver, no
    # composable copy under mvi/, no non-composable copy under ui/, and both walks non-empty.
    top_level_import = re.compile(
        r"^import org\.jetbrains\.compose\.resources\.(getString|getPluralString)\b", re.MULTILINE
    )
    mvi_prefix = (entry.common_main / "mvi").as_posix() + "/"
    ui_prefix = (entry.common_main / "ui").as_posix() + "/"
    mvi_files = ui_files = 0
    for path, raw in common_sources.items():
        source = strip_kotlin_comments(raw)
        relative = path.relative_to(entry.root)
        for match in top_level_import.finditer(source):
            failures.append(
                f"{module}: {relative} imports CMP's top-level {match.group(1)}; read copy "
                "outside composition through the injected StringResolver"
            )
        posix = path.as_posix()
        if posix.startswith(mvi_prefix):
            mvi_files += 1
            for call in ("getString(", "getPluralString("):
                total = source.count(call)
                through_resolver = source.count(f"stringResolver.{call}")
                if total != through_resolver:
                    failures.append(
                        f"{module}: {relative} reads copy outside the StringResolver: {call!r} "
                        f"{total} time(s), {through_resolver} through stringResolver"
                    )
            for token in ("stringResource(", "pluralStringResource("):
                if token in source:
                    failures.append(
                        f"{module}: {relative} resolves composable copy under mvi/: {token!r}"
                    )
        if posix.startswith(ui_prefix):
            ui_files += 1
            for call in ("getString(", "getPluralString("):
                if call in source:
                    failures.append(
                        f"{module}: {relative} reads copy outside composition under ui/: {call!r}"
                    )
    if mvi_files == 0 or ui_files == 0:
        failures.append(
            f"{module}: copy contract walked {mvi_files} mvi/ and {ui_files} ui/ file(s); "
            "both must be non-empty"
        )
    return failures


def home_preview_contract(entry: FeatureEntry, common_sources: dict[Path, str]) -> list[str]:
    # S3 (§5.4, §12.1): the shared preview check counts only `@Preview(`, so the six bare
    # `@Preview` annotations are pinned here per file, and every other commonMain file holds none.
    failures: list[str] = []
    bare_previews = {"ui/HomeScreen.kt": 4, "ui/components/ActiveSessionBanner.kt": 2}
    bare_preview = re.compile(r"@Preview\b(?!\s*\()")
    expected_files = {entry.common_main / relative for relative in bare_previews}
    for path in expected_files - set(common_sources):
        failures.append(
            f"{entry.module}: {path.relative_to(entry.root)} must hold bare @Preview annotations "
            "but is missing"
        )
    for path, raw in common_sources.items():
        expected = bare_previews.get(path.relative_to(entry.common_main).as_posix(), 0)
        found = len(bare_preview.findall(strip_kotlin_comments(raw)))
        if found != expected:
            failures.append(
                f"{entry.module}: {path.relative_to(entry.root)} must hold exactly {expected} bare "
                f"@Preview annotation(s); found {found}"
            )
    return failures


def home_dependency_contract(entry: FeatureEntry, common_sources: dict[Path, str]) -> list[str]:
    # §3.5 and §12.1: core:data:database is no edge of the module — no main edge, no test edge.
    build_source = (entry.root / "build.gradle.kts").read_text(encoding="utf-8")
    if ":core:data:database" in build_source:
        return [f"{entry.module}: build must not depend on :core:data:database in any source set"]
    return []


HOME_ENTRY = FeatureEntry(
    module="feature:home",
    short_name="home",
    root=Path("feature/home"),
    package_dir="io/github/stslex/workeeper/feature/home",
    # Every plugin, resource-package, interop, dependency and apply line of §10, each exactly once;
    # kotlin("test") is counted by kotlin_test_count. No core:data:database edge (see
    # home_dependency_contract).
    build_fragments=(
        "alias(libs.plugins.convention.kmpComposeLibrary)",
        "alias(libs.plugins.metro)",
        "alias(libs.plugins.paparazzi)",
        'packageOfResClass = "io.github.stslex.workeeper.feature.home.resources"',
        "includeJavax()",
        'implementation(project(":core:core"))',
        'api(project(":core:ui:kit"))',
        'api(project(":core:ui:mvi"))',
        'api(project(":core:ui:navigation"))',
        'api(project(":core:ui:start-mode"))',
        'implementation(project(":core:data:exercise"))',
        'implementation(project(":core:data:dataStore"))',
        "api(libs.cmp.ui)",
        "api(libs.androidx.paging.common)",
        "api(libs.coroutines.core)",
        "api(libs.kotlinx.collections.immutable)",
        "api(libs.kotlinx.datetime)",
        "implementation(libs.androidx.compose.paging)",
        "implementation(libs.cmp.animation)",
        "implementation(libs.cmp.material.icons.extended)",
        "implementation(libs.coroutine.test)",
        "implementation(libs.cmp.ui.test)",
        '"androidHostTestImplementation"(project(":core:ui:golden-harness"))',
        'apply(from = "$rootDir/gradle/golden-gate.gradle.kts")',
    ),
    kotlin_test_count=2,
    # Exact private EN/RU catalog, in file order: (tag, name, EN value, RU value), as §3.4 prints it
    # at exit — P1's eighteen positional plural items.
    catalog=(
        ("string", "feature_home_title", "Home", "Главная"),
        ("string", "feature_home_active_session_label", "Session in progress", "Сессия в процессе"),
        ("string", "feature_home_active_session_progress_format", "%1$d of %2$d done", "%1$d из %2$d"),
        ("string", "feature_home_empty_headline", "Ready when you are", "Готовы тренироваться"),
        ("string", "feature_home_empty_supporting", "Start a training to log your first session", "Начните тренировку, чтобы записать первую сессию"),
        ("string", "feature_home_settings", "Settings", "Настройки"),
        ("string", "feature_home_charts", "Charts", "Прогресс"),
        ("string", "feature_home_start_action", "Start", "Начать"),
        ("string", "feature_home_start_mode_switch", "Choose what to show", "Выбрать, что показывать"),
        ("string", "feature_home_start_mode_forgotten_label", "Longest not done", "Дольше всего не делали"),
        ("string", "feature_home_start_empty_sessions", "No workouts yet", "Ещё ни одной тренировки"),
        ("string", "feature_home_start_empty_tags", "Exercises have no tags yet — tag muscle groups and they will show up here", "Упражнения без тегов — отмечайте группы, и они появятся здесь"),
        ("string", "feature_home_start_never_run", "never yet", "ещё ни разу"),
        ("string", "feature_home_start_groups_footnote", "days since the group’s last workout", "дней с последней тренировки группы"),
        ("string", "feature_home_start_other_training", "Another training", "Другая тренировка"),
        ("string", "feature_home_start_week_day_mon", "Mo", "Пн"),
        ("string", "feature_home_start_week_day_tue", "Tu", "Вт"),
        ("string", "feature_home_start_week_day_wed", "We", "Ср"),
        ("string", "feature_home_start_week_day_thu", "Th", "Чт"),
        ("string", "feature_home_start_week_day_fri", "Fr", "Пт"),
        ("string", "feature_home_start_week_day_sat", "Sa", "Сб"),
        ("string", "feature_home_start_week_day_sun", "Su", "Вс"),
        ("string", "feature_home_recent_adhoc_label", "Ad-hoc workout", "Свободная тренировка"),
        ("string", "feature_home_recent_stats_format", "%1$s · %2$s", "%1$s · %2$s"),
        ("string", "feature_home_picker_title", "Choose a training", "Выберите тренировку"),
        ("string", "feature_home_picker_see_all", "See all templates", "Все шаблоны"),
        ("string", "feature_home_picker_empty", "No templates yet", "Пока нет шаблонов"),
        ("string", "feature_home_picker_start_blank_title", "Start blank workout", "Пустая тренировка"),
        ("string", "feature_home_picker_start_blank_subtitle", "Add exercises as you go", "Добавляйте упражнения по ходу"),
        ("string", "feature_home_conflict_unnamed", "Workout", "Тренировка"),
        ("string", "feature_home_conflict_progress_format", "%1$d of %2$d exercises done", "%1$d из %2$d упражнений выполнено"),
        ("string", "feature_home_paging_loading", "Loading", "Загружаю"),
        ("string", "feature_home_paging_error", "Couldn’t load more", "Не удалось загрузить дальше"),
        ("string", "feature_home_paging_retry", "Retry", "Повторить"),
        ("string", "feature_home_refresh_error", "Couldn’t load the list", "Не удалось загрузить список"),
        (
            "plurals",
            "feature_home_recent_exercises_count",
            (("one", "%1$d exercise"), ("other", "%1$d exercises")),
            (
                ("one", "%1$d упражнение"),
                ("few", "%1$d упражнения"),
                ("many", "%1$d упражнений"),
                ("other", "%1$d упражнений"),
            ),
        ),
        (
            "plurals",
            "feature_home_recent_sets_count",
            (("one", "%1$d set"), ("other", "%1$d sets")),
            (
                ("one", "%1$d подход"),
                ("few", "%1$d подхода"),
                ("many", "%1$d подходов"),
                ("other", "%1$d подходов"),
            ),
        ),
        (
            "plurals",
            "feature_home_start_week_sessions_count",
            (("one", "workout"), ("other", "workouts")),
            (
                ("one", "тренировка"),
                ("few", "тренировки"),
                ("many", "тренировок"),
                ("other", "тренировки"),
            ),
        ),
        (
            "plurals",
            "feature_home_start_days_count",
            (("one", "day"), ("other", "days")),
            (
                ("one", "день"),
                ("few", "дня"),
                ("many", "дней"),
                ("other", "дня"),
            ),
        ),
        (
            "plurals",
            "feature_home_start_days_idle_count",
            (("one", "%1$d day"), ("other", "%1$d days")),
            (
                ("one", "%1$d день"),
                ("few", "%1$d дня"),
                ("many", "%1$d дней"),
                ("other", "%1$d дня"),
            ),
        ),
    ),
    resource_prefix="feature_home_",
    # The mapper and the three copy-reading handlers are deliberately absent: they resolve copy (§12.1).
    payload_files=frozenset({"HomeStore.kt", "BottomSheetState.kt", "PickerTrainingItem.kt", "RecentSessionItem.kt", "StartCardBodyUi.kt"}),
    store_file="mvi/store/HomeStore.kt",
    state_fields=(
        ("activeSession", "ActiveSessionInfo?"),
        ("startCardMode", "StartCardModeUi?"),
        ("startCardBody", "StartCardBodyUi?"),
        ("pagingUiState", "PagingUiState<PagingData<RecentSessionItem>>"),
        ("nowMillis", "Long"),
        ("isActiveLoaded", "Boolean"),
        ("bottomSheet", "BottomSheetState"),
        ("pendingConflict", "ConflictInfo?"),
    ),
    # S1: the mapper keeps the two relative-time reads; ClickHandler and PagingHandler pass it on.
    resource_wrapper_readers=frozenset({
        "mvi/mapper/HomeUiMapper.kt",
        "mvi/handler/ClickHandler.kt",
        "mvi/handler/PagingHandler.kt",
    }),
    preview_subjects={"ui/components/RecentSessionRow.kt": "RecentSessionRow"},
    # The three inherited production suppressions of §0.2; nothing under commonTest.
    suppressions={
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/di/HomeFeature.kt": [
            '"UNCHECKED_CAST"',
        ],
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/domain/HomeInteractorImpl.kt": [
            '"LongParameterList"',
        ],
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/mvi/handler/ClickHandler.kt": [
            '"TooManyFunctions"',
        ],
    },
    # The 70 portable identities of §3.2, the iOS mode-label identity (S2), and the scene of §11.5.
    test_names={
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/domain/HomeInteractorImplTest.kt": [
            "observeActiveSession maps repository ActiveSessionWithStats to domain",
            "observeStartCardReadout delegates to the use case with mode and now",
            "observeStartCardMode maps the stored string to the domain mode",
            "observeStartCardMode falls back to WEEK for an unknown stored value",
            "setStartCardMode forwards the persistence encoding to the data store",
            "the storage encoding and the WEEK default are pinned",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/domain/usecase/ObserveStartCardReadoutUseCaseTest.kt": [
            "WEEK with history wraps the week readout",
            "WEEK with no session ever is the mode's own empty state even at count zero",
            "DAYS_SINCE_LAST measures calendar days and anchors on the last session",
            "DAYS_SINCE_LAST with no session ever is NoSessions",
            "LAGGING_GROUPS maps idle stats to calendar days in repository order",
            "LAGGING_GROUPS with no tagged history is NoTaggedHistory",
            "FORGOTTEN_TRAINING carries days idle and composition",
            "FORGOTTEN_TRAINING never run keeps daysIdle null — HD1's most forgotten thing",
            "FORGOTTEN_TRAINING with no templates is NoTemplates",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/domain/usecase/ObserveWeekReadoutUseCaseTest.kt": [
            "queries the repository with the Monday-first week window around now",
            "counts every session and collapses same-day sessions into one trained day",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/mvi/handler/ClickHandlerTest.kt": [
            "OnSettingsClick consumes OpenSettings navigation and emits HapticClick",
            "OnActiveSessionClick is a no-op when no active session",
            "OnActiveSessionClick consumes OpenLiveWorkoutResume with the session uuid",
            "OnRecentSessionClick consumes OpenPastSession with the session uuid",
            "OnPickerSeeAllClick hides picker and consumes OpenAllTrainings",
            "OnModeLabelClick opens the mode sheet with a haptic",
            "OnModeLabelClick while the training picker is up replaces the sheet",
            "OnModeSelected hides the sheet and persists the MAPPED mode through the interactor",
            "OnModeSheetDismiss hides the sheet without a haptic",
            "OnPickerTrainingSelected hides the picker before resolving conflict",
            "OnPickerTrainingSelected reports the persisted active session progress",
            "OnStartActionClick under a Forgotten body starts THAT training no picker",
            "OnStartActionClick under an Empty body opens the picker instead",
            "OnStartActionClick before the body arrives opens the picker",
            "OnConflictDismiss clears pendingConflict",
            "OnConflictResume consumes OpenLiveWorkoutResume with the active session uuid",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/mvi/handler/CommonHandlerTest.kt": [
            "Init subscribes to the active session and NOT to the paged recent list",
            "Init builds the start card pipeline from the persisted mode",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/mvi/handler/NavigationHandlerTest.kt": [
            "OpenLiveWorkoutResume navigates to LiveWorkout with the active session uuid",
            "OpenLiveWorkoutFresh navigates to LiveWorkout with the training uuid",
            "OpenPastSession navigates to PastSession with the session uuid",
            "OpenSettings navigates to Settings",
            "OpenAllTrainings navigates to AllTrainings bottom-bar destination",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/mvi/mapper/HomeUiMapperTest.kt": [
            "recent mapper uses adhoc label and pluralized stats",
            "recent mapper preserves template name and mixed count labels",
            "picker mapper handles recently used and never used templates",
            "week mapper renders count as digits resolves the unit plural fills trained days",
            "week mapper with no sessions renders zero over seven unfilled pills",
            "week mapper uses the singular unit for one session",
            "days-since readout renders digits unit and the name-date anchor",
            "days-since readout for an adhoc session anchors on the adhoc label",
            "tag-idle readout scales bars against the longest idle and keeps bare counts",
            "tag-idle readout with all groups trained today draws empty bars not NaN",
            "forgotten readout joins days idle and composition",
            "forgotten readout for a never-run template says so instead of a day count",
            "each empty readout carries its own mode's copy",
            "week readout wrapped in the sealed type maps through the week mapper",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/mvi/mapper/StartCardModeMapperTest.kt": [
            "every UI mode maps to its own domain twin",
            "every domain mode maps to its own UI twin",
            "the mapping round-trips over the whole catalog",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/mvi/store/HomeStartCardSeedTest.kt": [
            "init seeds no mode and no body",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/ui/components/HomeListSurfaceTest.kt": [
            "rowsWin",
            "coldOpenIsLoading",
            "coldOpenErrorIsItsOwnVerdict",
            "settledAndEmpty",
            "theEnumIsNarrowedNotCopied",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/ui/components/HomePagingTailKindTest.kt": [
            "appending",
            "failedAppend",
            "exhausted",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/home/ui/components/RecentMetaLineTest.kt": [
            "order",
            "relativeLabelIsFirst",
            "statsAreLast",
            "emptyTokensAreDropped",
        ],
        "src/iosTest/kotlin/io/github/stslex/workeeper/feature/home/ui/components/HomeStartCardModeLabelTest.kt": [
            "theHeadNamesNoModeUntilOneIsKnown",
        ],
        "src/iosTest/kotlin/io/github/stslex/workeeper/feature/home/HomeFeatureSceneIosTest.kt": [
            "resourcesReadoutsListPickerAndActionsRenderAndDispatch",
        ],
    },
    golden_test=(
        "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/home/golden/HomeGoldenTest.kt"
    ),
    # The 21 methods of §3.3, in source order; each renders light and dark (42 PNGs).
    golden_methods=[
        "recentRow",
        "recentRowRussian",
        "pagingLoadingTail",
        "pagingErrorTail",
        "coldOpenError",
        "activeSessionBanner",
        "startCard",
        "startCardDaysSince",
        "startCardTagIdle",
        "startCardForgotten",
        "startCardForgottenNeverRun",
        "startCardEmptyWeek",
        "startCardEmptyDaysSince",
        "startCardEmptyTags",
        "startCardEmptyTemplates",
        "screenList",
        "screenWithActiveSession",
        "screenEmptyRegion",
        "screenColdOpenLoading",
        "screenColdOpenError",
        "screenActiveSessionLoading",
    ],
    # No device test (§11.4): an empty device_test reads no file.
    device_test="",
    device_fragments=(),
    feature_class="HomeFeature",
    graph_interface="HomeGraph",
    graph_function="homeGraph",
    factory_accessor="homeGraphFactory",
    create_method="createHomeGraph",
    store_impl="HomeStoreImpl",
    store_accessor="homeStore",
    feature_file="di/HomeFeature.kt",
    graph_file="ui/HomeGraph.kt",
    identity_test=Path(
        "app/app/src/test/kotlin/io/github/stslex/workeeper/di/HomeExtensionIdentityTest.kt"
    ),
    identity_fragments=(
        "private fun AppGraph.home(): HomeGraph = homeGraphFactory.createHomeGraph()",
    ),
    identity_names=[
        "extension resolves the store through the parent graph",
        "store's app-scoped deps are the SAME instances the parent holds",
    ],
    # Each identity reaches the graph once through the accessor; the helper itself is not counted.
    identity_accessor=r"(?<!AppGraph)\.home\(\)",
    extra_checks=(home_copy_contract, home_preview_contract, home_dependency_contract),
    # S3: the 11 argument lists stay verbatim, pinned by file and exact arguments (§5.4).
    preview_inventory={
        "ui/components/ActiveSessionBanner.kt": [
            'device = "id:pixel_9"',
        ],
        "ui/components/HomeStartCard.kt": [
            'name = "Week — Light"',
            'name = "Week — Dark"',
            'name = "Days since — Dark"',
            'name = "Lagging groups — Dark"',
            'name = "Forgotten — Dark"',
            'name = "Mode unknown — Dark"',
            'name = "Empty — Dark"',
        ],
        "ui/components/TrainingPickerSheet.kt": [
            'name = "Loaded — Light"',
            'name = "Loading — Dark"',
            'name = "Empty — Dark"',
        ],
    },
    # S1: the two relative-time reads, in the mapper.
    resource_wrapper_dates={"mvi/mapper/HomeUiMapper.kt": {"getAbbreviatedRelativeTime": 2}},
)


def exercise_chart_copy_contract(entry: FeatureEntry, common_sources: dict[Path, str]) -> list[str]:
    # D2, D3, F1 and S3 (kmp-phase-7-13-exercise-chart-feature.md §5.2–5.5, §12.1): copy outside
    # composition resolves through the injected StringResolver, the readout's copy is resolved per
    # load before the staleness guard, the scrub assembles it without suspending, ClickHandler
    # reloads through the ChartLoader seam, and the events and UI models keep their shape. Every
    # count is over comment-stripped source.
    failures: list[str] = []
    module = entry.module

    def source_of(relative: str):
        # None only for an absent file: an existing empty file is still checked, and fails.
        path = entry.common_main / relative
        if path not in common_sources:
            failures.append(f"{module}: copy contract cannot read {path.relative_to(entry.root)}")
            return None
        return strip_kotlin_comments(common_sources[path])

    def shown(relative: str) -> Path:
        return (entry.common_main / relative).relative_to(entry.root)

    def require(relative: str, source: str, fragment: str, expected: int) -> None:
        count = source.count(fragment)
        if count != expected:
            times = "once" if expected == 1 else f"{number_word(expected)} times"
            failures.append(
                f"{module}: {shown(relative)} copy contract requires {fragment!r} "
                f"exactly {times}; found {count}"
            )

    def require_parameters(relative: str, source: str, name: str, expected: list) -> None:
        parameters = data_class_parameters(source, name)
        if parameters != expected:
            failures.append(
                f"{module}: {shown(relative)} {name} parameters must be exactly {expected}; "
                f"actual={parameters}"
            )

    # 1. The event and the UI models keep their shape.
    for relative, name, expected in (
        ("mvi/store/ExerciseChartStore.kt", "HapticClick", ["type: HapticFeedbackType"]),
        (
            "mvi/model/ChartReadoutUiModel.kt",
            "ChartReadoutUiModel",
            [
                "metricName: String",
                "isRecord: Boolean",
                "caption: String",
                "value: String",
                "unit: String",
            ],
        ),
        (
            "mvi/model/ChartReadoutCopyUiModel.kt",
            "ChartReadoutCopyUiModel",
            [
                "heaviestWeightName: String",
                "volumePerSessionName: String",
                "volumePerSetName: String",
                "kgUnit: String",
                "repsUnit: String",
                "recordLabel: String",
                "setCountLabels: ImmutableMap<Int, String>",
            ],
        ),
        (
            "mvi/model/ChartFooterStatsUiModel.kt",
            "ChartFooterStatsUiModel",
            [
                "minTitle: String",
                "minValue: String",
                "maxTitle: String",
                "maxValue: String",
                "lastTitle: String",
                "lastValue: String",
                "unit: String?",
            ],
        ),
    ):
        source = source_of(relative)
        if source is not None:
            require_parameters(relative, source, name, expected)

    # 2. S3: the two enums carry no resource id.
    for relative, fragment in (
        ("mvi/model/ChartMetricUiModel.kt", "enum class ChartMetricUiModel {"),
        ("mvi/model/ChartPresetUiModel.kt", "enum class ChartPresetUiModel {"),
    ):
        source = source_of(relative)
        if source is not None:
            require(relative, source, fragment, 1)

    # 3. D3: the readout's copy resolves once per load; the assembly itself never suspends.
    readout_relative = "mvi/mapper/ChartReadoutMapper.kt"
    readout_source = source_of(readout_relative)
    if readout_source is not None:
        for fragment, expected in (
            ("suspend fun toReadoutCopy(", 1),
            ("fun toReadout(", 1),
            ("copy: ChartReadoutCopyUiModel,", 1),
            ("suspend fun toReadout(", 0),
            ("stringResolver.getString(", 6),
            ("stringResolver.getPluralString(", 1),
        ):
            require(readout_relative, readout_source, fragment, expected)

    footer_relative = "mvi/mapper/ExerciseChartUiMapper.kt"
    footer_source = source_of(footer_relative)
    if footer_source is not None:
        for fragment, expected in (
            ("suspend fun ChartFooterStatsDomain.toUi(", 1),
            ("stringResolver.getString(", 4),
            ("stringResolver.getPluralString(", 1),
        ):
            require(footer_relative, footer_source, fragment, expected)

    # 5. D2 and D3: the scrub reads State's copy and reloads through the seam; nothing launches.
    click_relative = "mvi/handler/ClickHandler.kt"
    click_source = source_of(click_relative)
    if click_source is not None:
        for fragment, expected in (
            ("private val chartLoader: ChartLoader,", 1),
            ("private val resourceWrapper: ResourceWrapper,", 1),
            ("val copy = current.readoutCopy ?: return", 1),
            ("ChartReadoutMapper.toReadout(", 1),
            ("chartLoader.loadChart(", 3),
            ("CommonHandler", 0),
            ("StringResolver", 0),
            ("stringResolver", 0),
            ("launch", 0),
        ):
            require(click_relative, click_source, fragment, expected)

    # 6. CommonHandler resolves the copy through the resolver and passes the wrapper on.
    common_relative = "mvi/handler/CommonHandler.kt"
    common_source = source_of(common_relative)
    copy_call = "ChartReadoutMapper.toReadoutCopy(newPoints, stringResolver)"
    if common_source is not None:
        for fragment, expected in (
            ("class CommonHandler @Inject internal constructor(", 1),
            ("override fun loadChart(exercise: ExercisePickerItemUiModel)", 1),
            ("private val resourceWrapper: ResourceWrapper,", 1),
            ("private val stringResolver: StringResolver,", 1),
            (copy_call, 1),
            ("toUi(type, stringResolver)", 1),
            ("ChartReadoutMapper.toReadout(", 1),
            ("getString(", 0),
            ("getPluralString(", 0),
        ):
            require(common_relative, common_source, fragment, expected)

        # 7. F1: the copy resolves before the staleness guard, which precedes the read of `prior`.
        copy_at = common_source.find(copy_call)
        guard_at = common_source.find(
            "if (state.value.requestOf() != request) return@launchDefault"
        )
        prior_at = common_source.find("val prior = state.value")
        if not (0 <= copy_at < guard_at < prior_at):
            failures.append(
                f"{module}: {shown(common_relative)} must resolve the readout copy before the "
                "staleness guard and the read of `prior` (F1)"
            )

    # 8. D2: the seam and its one binding.
    for relative, fragments in (
        (
            "mvi/handler/ChartLoader.kt",
            ("interface ChartLoader {", "fun loadChart(exercise: ExercisePickerItemUiModel)"),
        ),
        ("di/ExerciseChartGraph.kt", ("val CommonHandler.bindChartLoader: ChartLoader",)),
    ):
        source = source_of(relative)
        if source is not None:
            for fragment in fragments:
                require(relative, source, fragment, 1)

    # 9. 7.11's rules 4–7: no CMP top-level reads; mvi/ reads only through the resolver and
    # composes nothing; ui/ reads nothing outside composition; both walks find a file.
    top_level_import = re.compile(
        r"^import org\.jetbrains\.compose\.resources\.(getString|getPluralString)\b", re.MULTILINE
    )
    mvi_prefix = (entry.common_main / "mvi").as_posix() + "/"
    ui_prefix = (entry.common_main / "ui").as_posix() + "/"
    mvi_files = ui_files = 0
    for path, raw in common_sources.items():
        source = strip_kotlin_comments(raw)
        relative = path.relative_to(entry.root)
        for match in top_level_import.finditer(source):
            failures.append(
                f"{module}: {relative} imports CMP's top-level {match.group(1)}; read copy "
                "outside composition through the injected StringResolver"
            )
        posix = path.as_posix()
        if posix.startswith(mvi_prefix):
            mvi_files += 1
            for call in ("getString(", "getPluralString("):
                total = source.count(call)
                through_resolver = source.count(f"stringResolver.{call}")
                if total != through_resolver:
                    failures.append(
                        f"{module}: {relative} reads copy outside the StringResolver: {call!r} "
                        f"{total} time(s), {through_resolver} through stringResolver"
                    )
            for token in ("stringResource(", "pluralStringResource("):
                if token in source:
                    failures.append(
                        f"{module}: {relative} resolves composable copy under mvi/: {token!r}"
                    )
        if posix.startswith(ui_prefix):
            ui_files += 1
            for call in ("getString(", "getPluralString("):
                if call in source:
                    failures.append(
                        f"{module}: {relative} reads copy outside composition under ui/: {call!r}"
                    )
    if mvi_files == 0 or ui_files == 0:
        failures.append(
            f"{module}: copy contract walked {mvi_files} mvi/ and {ui_files} ui/ file(s); "
            "both must be non-empty"
        )
    return failures


def exercise_chart_dependency_contract(
    entry: FeatureEntry,
    common_sources: dict[Path, str],
) -> list[str]:
    # D4 and S1 (§3.5, §10, §12.1): core:data:database is no edge of the module, only the host
    # parity suite reads core:data:database-test's PrRuleFixture, and shared code names no java.*.
    failures: list[str] = []
    module = entry.module
    build_source = (entry.root / "build.gradle.kts").read_text(encoding="utf-8")
    if 'project(":core:data:database")' in build_source:
        failures.append(f"{module}: build must not depend on :core:data:database in any source set")
    parity = (
        entry.root / "src/androidHostTest/kotlin" / entry.package_dir
        / "domain/ChartFolderPrRuleParityTest.kt"
    )
    if not parity.is_file():
        failures.append(
            f"{module}: the host parity suite {parity.relative_to(entry.root)} is missing"
        )
    java_reference = re.compile(r"(?<![\w.])java\.")
    shared_sets = ("src/commonMain/", "src/commonTest/", "src/iosTest/")
    for path in sorted((entry.root / "src").rglob("*.kt")):
        source = strip_kotlin_comments(path.read_text(encoding="utf-8"))
        relative = path.relative_to(entry.root)
        if path != parity and "core.data.database" in source:
            failures.append(
                f"{module}: {relative} must not read core:data:database or its fixtures; only the "
                "host parity suite reads PrRuleFixture (D4)"
            )
        if relative.as_posix().startswith(shared_sets) and java_reference.search(source):
            failures.append(
                f"{module}: {relative} names java.* in shared code; use kotlinx-datetime (S1)"
            )
    return failures


EXERCISE_CHART_ENTRY = FeatureEntry(
    module="feature:exercise-chart",
    short_name="exercise-chart",
    root=Path("feature/exercise-chart"),
    package_dir="io/github/stslex/workeeper/feature/exercise_chart",
    # Every plugin, resource-package, interop, dependency and apply line of §10, each exactly once;
    # kotlin("test") is counted by kotlin_test_count. No core:data:database edge (see
    # exercise_chart_dependency_contract).
    build_fragments=(
        "alias(libs.plugins.convention.kmpComposeLibrary)",
        "alias(libs.plugins.metro)",
        "alias(libs.plugins.paparazzi)",
        'packageOfResClass = "io.github.stslex.workeeper.feature.exercise_chart.resources"',
        "includeJavax()",
        'implementation(project(":core:core"))',
        'implementation(project(":core:ui:kit"))',
        'api(project(":core:ui:mvi"))',
        'api(project(":core:ui:navigation"))',
        'api(project(":core:ui:plan-editor"))',
        'implementation(project(":core:data:exercise"))',
        "api(libs.cmp.ui)",
        "api(libs.coroutines.core)",
        "api(libs.kotlinx.collections.immutable)",
        "api(libs.kotlinx.datetime)",
        "implementation(libs.cmp.animation)",
        "implementation(libs.coroutine.test)",
        "implementation(libs.cmp.ui.test)",
        '"androidHostTestImplementation"(project(":core:ui:golden-harness"))',
        '"androidHostTestImplementation"(project(":core:data:database-test"))',
        '"androidDeviceTestImplementation"(libs.bundles.android.test)',
        '"androidDeviceTestImplementation"(libs.androidx.compose.ui.test.junit4)',
        '"androidDeviceTestImplementation"(platform(libs.androidx.compose.bom))',
        '"androidDeviceTestImplementation"(libs.androidx.compose.ui.test.manifest)',
        '"androidDeviceTestImplementation"(project(":core:ui:test-utils"))',
        'apply(from = "$rootDir/gradle/golden-gate.gradle.kts")',
    ),
    kotlin_test_count=2,
    # Exact private EN/RU catalog, in file order: (tag, name, EN value, RU value), as §3.4 prints it
    # at exit — P1's twelve positional plural items.
    catalog=(
        ("string", "feature_exercise_chart_picker_title", "Select exercise", "Выбрать упражнение"),
        ("string", "feature_exercise_chart_picker_open", "Switch exercise", "Сменить упражнение"),
        ("string", "feature_exercise_chart_picker_search", "Search", "Поиск"),
        ("string", "feature_exercise_chart_picker_no_matches", "Nothing found", "Ничего не найдено"),
        ("string", "feature_exercise_chart_metric_heaviest", "Weight", "Вес"),
        ("string", "feature_exercise_chart_metric_session", "Session", "Сессия"),
        ("string", "feature_exercise_chart_metric_volume", "Set", "Подход"),
        ("string", "feature_exercise_chart_metric_name_heaviest", "Heaviest weight", "Максимальный вес"),
        ("string", "feature_exercise_chart_metric_name_session", "Volume per session", "Объём за сессию"),
        ("string", "feature_exercise_chart_metric_name_volume", "Volume per set", "Объём за подход"),
        ("string", "feature_exercise_chart_unit_kg", "kg", "кг"),
        ("string", "feature_exercise_chart_unit_reps", "reps", "повт."),
        ("string", "feature_exercise_chart_readout_record", "record", "рекорд"),
        (
            "plurals",
            "feature_exercise_chart_readout_sets",
            (("one", "%1$d set"), ("other", "%1$d sets")),
            (
                ("one", "%1$d подход"),
                ("few", "%1$d подхода"),
                ("many", "%1$d подходов"),
                ("other", "%1$d подходов"),
            ),
        ),
        ("string", "feature_exercise_chart_preset_1m", "1M", "1М"),
        ("string", "feature_exercise_chart_preset_3m", "3M", "3М"),
        ("string", "feature_exercise_chart_preset_1y", "1Y", "1Г"),
        ("string", "feature_exercise_chart_preset_all", "All", "Всё"),
        ("string", "feature_exercise_chart_footer_min", "Minimum", "Минимум"),
        ("string", "feature_exercise_chart_footer_max", "Maximum", "Максимум"),
        ("string", "feature_exercise_chart_footer_last", "Last", "Последний"),
        (
            "plurals",
            "feature_exercise_chart_value_reps",
            (("one", "%1$d rep"), ("other", "%1$d reps")),
            (
                ("one", "%1$d повторение"),
                ("few", "%1$d повторения"),
                ("many", "%1$d повторений"),
                ("other", "%1$d повторений"),
            ),
        ),
        ("string", "feature_exercise_chart_empty_no_sessions_title", "No finished workouts yet", "Нет завершённых тренировок"),
        ("string", "feature_exercise_chart_empty_no_sessions_subtitle", "Finish a session — your progress will appear here.", "Завершите тренировку — здесь появится прогресс."),
        ("string", "feature_exercise_chart_empty_no_sessions_cta", "Start a workout", "Начать тренировку"),
        ("string", "feature_exercise_chart_empty_not_found_title", "Exercise unavailable", "Упражнение недоступно"),
        ("string", "feature_exercise_chart_empty_not_found_subtitle", "It may be archived or have no logged sets. Pick another from the list.", "Возможно, оно архивировано или подходов не было. Выберите другое из списка."),
        ("string", "feature_exercise_chart_empty_not_found_cta", "Choose exercise", "Выберите упражнение"),
        ("string", "feature_exercise_chart_error_load_title", "Couldn’t load the chart", "Не удалось загрузить график"),
        ("string", "feature_exercise_chart_error_load_subtitle", "Something went wrong while reading your history. Try again.", "Что-то пошло не так при чтении истории. Попробуйте ещё раз."),
        ("string", "feature_exercise_chart_error_load_cta", "Try again", "Повторить"),
        ("string", "feature_exercise_chart_empty_no_data_title", "Nothing to show yet", "Пока нечего показать"),
        ("string", "feature_exercise_chart_empty_no_data_subtitle", "The chart appears after two recorded sessions with this exercise.", "График появится после двух записанных сессий с этим упражнением."),
    ),
    resource_prefix="feature_exercise_chart_",
    # The two mappers and the two copy-reading handlers are deliberately absent (§12.1).
    payload_files=frozenset({
        "ChartFooterStatsUiModel.kt",
        "ChartMetricUiModel.kt",
        "ChartPointUiModel.kt",
        "ChartPresetUiModel.kt",
        "ChartReadoutCopyUiModel.kt",
        "ChartReadoutUiModel.kt",
        "ExerciseChartStore.kt",
        "ExercisePickerItemUiModel.kt",
    }),
    store_file="mvi/store/ExerciseChartStore.kt",
    state_fields=(
        ("isLoading", "Boolean"),
        ("initialUuid", "String?"),
        ("selectedExercise", "ExercisePickerItemUiModel?"),
        ("recentExercises", "ImmutableList<ExercisePickerItemUiModel>"),
        ("preset", "ChartPresetUiModel"),
        ("metric", "ChartMetricUiModel"),
        ("points", "ImmutableList<ChartPointUiModel>"),
        ("footerStats", "ChartFooterStatsUiModel?"),
        ("activeIndex", "Int?"),
        ("readout", "ChartReadoutUiModel?"),
        ("readoutCopy", "ChartReadoutCopyUiModel?"),
        ("isPickerOpen", "Boolean"),
        ("pickerQuery", "String"),
        ("emptyReason", "EmptyReason?"),
    ),
    # S4: the mapper keeps the one day-month read; the two handlers pass the wrapper on.
    resource_wrapper_readers=frozenset({
        "mvi/handler/ClickHandler.kt",
        "mvi/handler/CommonHandler.kt",
        "mvi/mapper/ChartReadoutMapper.kt",
    }),
    preview_subjects={},
    # The three inherited production suppressions of §0.2; nothing under any test source set.
    suppressions={
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/di/ExerciseChartFeature.kt": [
            '"UNCHECKED_CAST"',
        ],
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ui/ExerciseChartScreen.kt": [
            '"MagicNumber"',
        ],
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ui/components/ChartCanvas.kt": [
            '"MagicNumber"',
        ],
    },
    # The 68 portable identities of §3.2, the 4 host-only parity identities (D4), and the scene
    # of §11.4.
    test_names={
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/ChartFolderTest.kt": [
            "empty history returns empty points and null footer",
            "single set produces single point with min equal max equal last",
            "multiple sessions are sorted by completion time and use each session maximum",
            "two same-day sessions remain separate for every metric",
            "two different-day sessions remain chronological for every metric",
            "setCount counts sets the chart cannot plot",
            "setCount keeps the readout accurate when only one set is plottable",
            "equal same-day values keep both sessions in completion order",
            "weightless exercise plots reps regardless of metric",
            "preset window filters out sets older than start",
            "MONTH_1 filter tracks calendar days across a DST spring-forward",
            "MONTH_1 filter tracks calendar days across a DST fall-back",
            "ALL preset includes very old sets",
            "volume metric multiplies weight by reps",
            "volume tie across sessions preserves both in completion order",
            "volume tie inside one session keeps the earlier position",
            "session volume sums every eligible set of the session",
            "session volume excludes ineligible sets from the sum but not from setCount",
            "same-day session volume retains each session total",
            "session total tie preserves both sessions in completion order",
            "weightless session volume sums reps",
            "session volume footer tracks session totals across days",
            "single point case still produces footer with min equal max",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/handler/ClickHandlerTest.kt": [
            "OnPresetSelect to current preset is no-op",
            "OnPresetSelect changes state KEEPS emptyReason and delegates load",
            "OnMetricSelect changes metric KEEPS emptyReason and delegates load",
            "OnPickerOpen sets isPickerOpen true",
            "OnPickerQueryChange stores the query and the window resets it both ways",
            "OnPickerDismiss sets isPickerOpen false",
            "OnPickerItemSelect to same exercise just dismisses picker",
            "OnPickerItemSelect to different exercise clears emptyReason and triggers load",
            "OnScrub to a new index moves the readout and ticks the haptic",
            "OnScrub can read both sessions completed on the same day",
            "OnScrub to the current index is a no-op — the haptic is a tick per CROSSED point",
            "OnScrub outside the point range is ignored",
            "OnEmptyCtaClick consumes OpenHome navigation",
            "OnBack consumes PopBack navigation",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/handler/CommonHandlerTest.kt": [
            "Init with null and no recents emits NO_FINISHED_SESSIONS and skips loadChart",
            "Init with null and last-trained available loads its chart",
            "Init with explicit uuid not in recents emits EXERCISE_NOT_FOUND and skips loadChart",
            "Init with explicit uuid and empty recents prefers NO_FINISHED_SESSIONS",
            "Init with explicit valid uuid wins over getLastTrainedExerciseUuid",
            "a response whose metric no longer matches the live state is dropped",
            "a response that still matches the live state is applied",
            "loadChart with empty result sets NO_DATA_FOR_EXERCISE",
            "loadChart with a single point is sub-threshold — empty state no readout",
            "loadChart with two same-day sessions plots and scrubs the later session",
            "default state preset is ALL",
            "a throwing recents read resolves to LOAD_FAILED instead of loading forever",
            "a throwing chart read resolves to LOAD_FAILED",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/handler/NavigationHandlerTest.kt": [
            "PopBack triggers popBack",
            "OpenHome navigates to BottomBar Home",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/mapper/ExerciseChartUiMapperTest.kt": [
            "domain point keeps session identity in the UI model",
            "readout for the record point carries the flag and the grouped whole-number value",
            "readout off the record point has two caption parts and no flag",
            "volume record indices keep the earliest point on a value tie",
            "weight record index follows the PR reps tiebreak across sessions",
            "weight record index keeps the earliest session when weight and reps tie",
            "readout is null without an active index",
            "weighted footer values are the readout's grouped form with one shared unit",
            "weightless footer is a reps plural over the value with no separable unit",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/store/ChartContentResolutionTest.kt": [
            "one-point exercise never resolves to Plot across metric and preset switches",
            "zero-point exercise never resolves to Plot across metric and preset switches",
            "two-point exercise does resolve to Plot",
            "switching a resolved empty state never passes through Loading",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ui/components/ChartPointsAnimationTest.kt": [
            "same-day sessions keep distinct animation targets and scrub positions",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ui/components/MetricTabsMotionTest.kt": [
            "indicatorUsesTravel",
            "durationIsUnchanged",
        ],
        "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/ChartFolderPrRuleParityTest.kt": [
            "the record-holding set remains represented by its session point",
            "an ineligible set never becomes the session point",
            "volume metric keeps the shared eligibility even though it ranks differently",
            "session metric keeps the shared eligibility in its sum",
        ],
        "src/iosTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ExerciseChartFeatureSceneIosTest.kt": [
            "resourcesReadoutFooterStatesPickerAndActionsRenderAndDispatch",
        ],
    },
    golden_test=(
        "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/golden/ExerciseChartGoldenTest.kt"
    ),
    # The 15 methods of §3.3, in source order; each renders light and dark (30 PNGs).
    golden_methods=[
        "screenPopulated",
        "screenRecordActive",
        "screenWeightless",
        "screenEmptyNoData",
        "screenEmptyNotFound",
        "screenEmptyNoSessions",
        "screenLoadFailed",
        "canvasMidScrub",
        "canvasRecordActive",
        "pickerSheetContent",
        "readoutPlain",
        "readoutRecord",
        "tabsIndicatorFirst",
        "tabsIndicatorSecond",
        "footerStats",
    ],
    device_test="src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/exercise_chart/ExerciseChartScreenTest.kt",
    device_fragments=(
        "@Smoke",
        "fun chart_rendersReadout_whenReadoutIsPresent()",
        "fun chart_rendersRecordDot_whenReadoutIsRecord()",
        "fun chart_pressNearLeftEdge_dispatchesOnScrubToFirstIndex()",
        "fun chart_pressNearRightEdge_dispatchesOnScrubToLastIndex()",
        "fun chart_sameDaySessions_dispatchesBothScrubIndices()",
        "fun chart_tappingExerciseHeader_dispatchesOnPickerOpen()",
        "fun chart_singlePoint_neverReachesTheCanvas()",
    ),
    feature_class="ExerciseChartFeature",
    graph_interface="ExerciseChartGraph",
    graph_function="exerciseChartGraph",
    factory_accessor="exerciseChartGraphFactory",
    create_method="createExerciseChartGraph",
    store_impl="ExerciseChartStoreImpl",
    store_accessor="exerciseChartStore",
    feature_file="di/ExerciseChartFeature.kt",
    graph_file="ui/ExerciseChartGraph.kt",
    identity_test=Path(
        "app/app/src/test/kotlin/io/github/stslex/workeeper/di/ExerciseChartExtensionIdentityTest.kt"
    ),
    identity_fragments=(
        "private fun AppGraph.exerciseChart(exerciseUuid: String?): ExerciseChartGraph = exerciseChartGraphFactory.createExerciseChartGraph(Screen.ExerciseChart(exerciseUuid = exerciseUuid))",
    ),
    identity_names=[
        "extension resolves the store through the parent graph",
        "store's app-scoped deps are the SAME instances the parent holds",
        "the extension inherits the Default dispatcher key and not the IO one",
        "each extension carries its own route arg into the store state",
        "a null route arg survives the binding and reaches the store as null",
    ],
    # Each identity reaches the graph once through the accessor; the fourth identity's "ex-2"
    # call and the helper itself are deliberately not counted.
    identity_accessor=r'(?<!AppGraph)\.exerciseChart\((?:"ex-1"|null)\)',
    extra_checks=(exercise_chart_copy_contract, exercise_chart_dependency_contract),
    create_args="screen",
    preview_inventory={},
    resource_wrapper_dates={
        "mvi/mapper/ChartReadoutMapper.kt": {"formatDayMonth": 1},
    },
    # D5: all 23 bare previews, pinned per file; every other commonMain file holds none.
    bare_previews={
        "ui/ExerciseChartScreen.kt": 6,
        "ui/components/ChartEmptyState.kt": 3,
        "ui/components/ChartCanvas.kt": 2,
        "ui/components/ChartFooterStats.kt": 2,
        "ui/components/ChartReadout.kt": 2,
        "ui/components/ExerciseHeader.kt": 2,
        "ui/components/ExercisePickerSheet.kt": 2,
        "ui/components/MetricTabs.kt": 2,
        "ui/components/PresetChipsRow.kt": 2,
    },
)


def single_training_copy_contract(entry: FeatureEntry, common_sources: dict[Path, str]) -> list[str]:
    # D2, D3, F2 and S1 (kmp-phase-7-14-single-training-feature.md §5.2–§5.4, §12.1): the editor's
    # constant copy lives in State, the confirm resolves on use and lands only on the menu, the load
    # builds its rows before its State lambda, and copy outside composition resolves through the
    # injected StringResolver. Every count is over comment-stripped source.
    failures: list[str] = []
    module = entry.module

    def source_of(relative: str):
        # None only for an absent file: an existing empty file is still checked, and fails.
        path = entry.common_main / relative
        if path not in common_sources:
            failures.append(f"{module}: copy contract cannot read {path.relative_to(entry.root)}")
            return None
        return strip_kotlin_comments(common_sources[path])

    def shown(relative: str) -> Path:
        return (entry.common_main / relative).relative_to(entry.root)

    def require(relative: str, source: str, fragment: str, expected: int) -> None:
        count = source.count(fragment)
        if count != expected:
            times = "once" if expected == 1 else f"{number_word(expected)} times"
            failures.append(
                f"{module}: {shown(relative)} copy contract requires {fragment!r} "
                f"exactly {times}; found {count}"
            )

    def require_parameters(relative: str, source: str, name: str, expected: list) -> None:
        parameters = data_class_parameters(source, name)
        if parameters != expected:
            failures.append(
                f"{module}: {shown(relative)} {name} parameters must be exactly {expected}; "
                f"actual={parameters}"
            )

    # 1. Payload shapes and 2. D2's defaults, exactly once each.
    store_relative = "mvi/store/SingleTrainingStore.kt"
    store_source = source_of(store_relative)
    if store_source is not None:
        require_parameters(
            store_relative,
            store_source,
            "HapticClick",
            [
                "type: HapticFeedbackType",
            ],
        )
        require_parameters(store_relative, store_source, "ShowArchiveSuccess", ["message: String"])
        require_parameters(store_relative, store_source, "ShowArchiveBlocked", ["message: String"])
        require_parameters(store_relative, store_source, "ShowSaveError", ["message: String"])
        require_parameters(
            store_relative,
            store_source,
            "ShowSetRemovedUndo",
            [
                "message: String",
                "exerciseUuid: String",
                "set: PlanSetUiModel",
                "index: Int",
                "draftEpoch: Int",
            ],
        )
        require_parameters(
            store_relative,
            store_source,
            "ShowExerciseRemovedUndo",
            [
                "message: String",
                "item: TrainingExerciseItem",
                "wasExpanded: Boolean",
                "draftEpoch: Int",
            ],
        )
        require(store_relative, store_source, "editCopy = TrainingEditCopy.EMPTY,", 1)

    dialog_relative = "mvi/store/DialogState.kt"
    dialog_source = source_of(dialog_relative)
    if dialog_source is not None:
        require_parameters(
            dialog_relative,
            dialog_source,
            "PermanentDeleteConfirm",
            [
                "title: String",
                "body: String",
                "impactSummary: String",
                "confirmLabel: String",
            ],
        )
        require_parameters(
            dialog_relative,
            dialog_source,
            "ActiveSessionConflict",
            [
                "sessionUuid: String",
                "activeSessionName: String",
                "progressLabel: String",
            ],
        )

    models_relative = "mvi/model/SingleTrainingUiModels.kt"
    models_source = source_of(models_relative)
    if models_source is not None:
        require_parameters(
            models_relative,
            models_source,
            "TrainingEditCopy",
            [
                "noExercisesError: String",
                "exerciseRemoved: String",
                "setRemoved: String",
            ],
        )
        require(
            models_relative,
            models_source,
            "val EMPTY = TrainingEditCopy(noExercisesError = \"\", exerciseRemoved = \"\", setRemoved = \"\")",
            1,
        )

    # 3. ClickHandler: D2's constant reads and guarded confirm, eight resolver reads, no wrapper.
    click_relative = "mvi/handler/ClickHandler.kt"
    click_source = source_of(click_relative)
    if click_source is not None:
        for fragment in (
            "private val stringResolver: StringResolver,",
            "current.editCopy.noExercisesError",
            "current.editCopy.exerciseRemoved",
            "state.value.editCopy.setRemoved",
            "if (latest.dialogState == DialogState.DetailMenu) {",
            "latest.copy(dialogState = confirm)",
        ):
            require(click_relative, click_source, fragment, 1)
        require(click_relative, click_source, "stringResolver.getString(", 8)
        for fragment in ("ResourceWrapper", "resourceWrapper", "applySnapshotOrPop"):
            require(click_relative, click_source, fragment, 0)

    # 4. CommonHandler: Init's three resolver reads, F2's two rows, and 5. F2's order.
    common_relative = "mvi/handler/CommonHandler.kt"
    common_source = source_of(common_relative)
    if common_source is not None:
        for fragment in (
            "private val resourceWrapper: ResourceWrapper,",
            "private val stringResolver: StringResolver,",
            "TrainingEditCopy(",
            "onSuccess = { copy -> updateState { it.copy(editCopy = copy) } },",
            "val exercises = result.exercises.toExerciseItems()",
            "val pastSessions = result.recentSessions.toHistoryItems()",
        ):
            require(common_relative, common_source, fragment, 1)
        require(common_relative, common_source, "stringResolver.getString(", 3)
        require(common_relative, common_source, "resolveEditCopy()", 2)
        require(common_relative, common_source, "applyLoaded", 0)
        exercises_at = common_source.find("val exercises = result.exercises.toExerciseItems()")
        rows_at = common_source.find("val pastSessions = result.recentSessions.toHistoryItems()")
        items_lambda = common_source.find("updateState { current ->", max(exercises_at, 0))
        rows_lambda = common_source.find("updateState { current ->", max(rows_at, 0))
        if (
            exercises_at < 0
            or rows_at < exercises_at
            or rows_lambda < 0
            or items_lambda != rows_lambda
        ):
            failures.append(
                f"{module}: {shown(common_relative)} must build the exercise items and"
                " the history rows before the load's State lambda (F2)"
            )

    # 6. 7.11's rules 4–7: no top-level CMP read; mvi/ reads only through the resolver.
    top_level_import = re.compile(
        r"^import org\.jetbrains\.compose\.resources\.(getString|getPluralString)\b", re.MULTILINE
    )
    mvi_prefix = (entry.common_main / "mvi").as_posix() + "/"
    ui_prefix = (entry.common_main / "ui").as_posix() + "/"
    mvi_files = ui_files = 0
    for path, raw in common_sources.items():
        source = strip_kotlin_comments(raw)
        relative = path.relative_to(entry.root)
        for match in top_level_import.finditer(source):
            failures.append(
                f"{module}: {relative} imports CMP's top-level {match.group(1)}; read copy "
                "outside composition through the injected StringResolver"
            )
        posix = path.as_posix()
        if posix.startswith(mvi_prefix):
            mvi_files += 1
            for call in ("getString(", "getPluralString("):
                total = source.count(call)
                through_resolver = source.count(f"stringResolver.{call}")
                if total != through_resolver:
                    failures.append(
                        f"{module}: {relative} reads copy outside the StringResolver: {call!r} "
                        f"{total} time(s), {through_resolver} through stringResolver"
                    )
            for token in ("stringResource(", "pluralStringResource("):
                if token in source:
                    failures.append(
                        f"{module}: {relative} resolves composable copy under mvi/: {token!r}"
                    )
        if posix.startswith(ui_prefix):
            ui_files += 1
            for call in ("getString(", "getPluralString("):
                if call in source:
                    failures.append(
                        f"{module}: {relative} reads copy outside composition under ui/: {call!r}"
                    )
    if mvi_files == 0 or ui_files == 0:
        failures.append(
            f"{module}: copy contract walked {mvi_files} mvi/ and {ui_files} ui/ file(s); "
            "both must be non-empty"
        )
    return failures


def single_training_dependency_contract(
    entry: FeatureEntry,
    common_sources: dict[Path, str],
) -> list[str]:
    # §2, §3.5 and §10: core:data:database stays one production edge, read by the domain mapper
    # alone; no test set reads it or core:data:database-test; shared code names no java.*.
    failures: list[str] = []
    module = entry.module
    build_source = (entry.root / "build.gradle.kts").read_text(encoding="utf-8")
    if "project(\":core:data:database-test\")" in build_source:
        failures.append(f"{module}: build must not depend on :core:data:database-test")
    mapper = entry.common_main / "domain/mapper/SingleTrainingDomainMapper.kt"
    if "core.data.database" not in strip_kotlin_comments(common_sources.get(mapper, "")):
        failures.append(
            f"{module}: {mapper.relative_to(entry.root)} "
            "must be the module's one reader of core:data:database"
        )
    java_reference = re.compile(r"(?<![\w.])java\.")
    shared_sets = ("src/commonMain/", "src/commonTest/", "src/iosTest/")
    for path in sorted((entry.root / "src").rglob("*.kt")):
        source = strip_kotlin_comments(path.read_text(encoding="utf-8"))
        relative = path.relative_to(entry.root)
        if path != mapper and "core.data.database" in source:
            failures.append(
                f"{module}: {relative} must not read core:data:database;"
                " only the domain mapper maps its data models"
            )
        if relative.as_posix().startswith(shared_sets) and java_reference.search(source):
            failures.append(f"{module}: {relative} names java.* in shared code")
    return failures


SINGLE_TRAINING_ENTRY = FeatureEntry(
    module="feature:single-training",
    short_name="single-training",
    root=Path("feature/single-training"),
    package_dir="io/github/stslex/workeeper/feature/single_training",
    # Every plugin, resource-package, interop, dependency and apply line of §10, each exactly once;
    # kotlin("test") is counted by kotlin_test_count. The one production core:data:database edge
    # is here; single_training_dependency_contract keeps every other reader out.
    build_fragments=(
        "alias(libs.plugins.convention.kmpComposeLibrary)",
        "alias(libs.plugins.metro)",
        "alias(libs.plugins.paparazzi)",
        "packageOfResClass = \"io.github.stslex.workeeper.feature.single_training.resources\"",
        "includeJavax()",
        "implementation(project(\":core:core\"))",
        "api(project(\":core:ui:kit\"))",
        "api(project(\":core:ui:mvi\"))",
        "api(project(\":core:ui:navigation\"))",
        "api(project(\":core:ui:plan-editor\"))",
        "implementation(project(\":core:data:database\"))",
        "api(project(\":core:data:exercise\"))",
        "api(libs.cmp.ui)",
        "api(libs.coroutines.core)",
        "api(libs.kotlinx.collections.immutable)",
        "implementation(libs.cmp.animation)",
        "implementation(libs.cmp.material.icons.extended)",
        "implementation(libs.coroutine.test)",
        "implementation(libs.cmp.ui.test)",
        "\"androidHostTestImplementation\"(project(\":core:ui:golden-harness\"))",
        "\"androidDeviceTestImplementation\"(libs.bundles.android.test)",
        "\"androidDeviceTestImplementation\"(libs.androidx.compose.ui.test.junit4)",
        "\"androidDeviceTestImplementation\"(platform(libs.androidx.compose.bom))",
        "\"androidDeviceTestImplementation\"(libs.androidx.compose.ui.test.manifest)",
        "\"androidDeviceTestImplementation\"(project(\":core:ui:test-utils\"))",
        "apply(from = \"$rootDir/gradle/golden-gate.gradle.kts\")",
    ),
    kotlin_test_count=2,
    # Exact private EN/RU catalog, in file order: (tag, name, EN value, RU value), as §3.4 prints it
    # at exit — S5's two dead keys gone and P1's six positional plural items.
    catalog=(
        ("string", "feature_training_detail_more", "More", "Ещё"),
        ("string", "feature_training_detail_exercises", "Exercises", "Упражнения"),
        ("string", "feature_training_detail_past_sessions", "Past sessions", "История"),
        ("string", "feature_training_detail_start_session", "Start session", "Начать сессию"),
        ("string", "feature_training_detail_resume_session", "Resume session", "Продолжить сессию"),
        ("string", "feature_training_detail_edit", "Edit", "Изменить"),
        ("string", "feature_training_detail_archive", "Archive", "В архив"),
        ("string", "feature_training_detail_archive_blocked", "Cannot archive — finish active session first", "Нельзя архивировать — завершите активную сессию"),
        ("string", "feature_training_detail_archive_success_format", "‘%1$s’ archived", "«%1$s» в архиве"),
        ("string", "feature_training_detail_permanent_delete", "Delete permanently", "Удалить навсегда"),
        ("string", "feature_training_detail_permanent_delete_title", "Delete ‘%1$s’ permanently?", "Удалить «%1$s» навсегда?"),
        ("string", "feature_training_detail_permanent_delete_body", "This training has no history. This cannot be undone.", "У тренировки нет истории. Это нельзя отменить."),
        ("string", "feature_training_detail_permanent_delete_impact", "No history affected", "История не пострадает"),
        ("string", "feature_training_detail_permanent_delete_confirm", "Delete", "Удалить"),
        ("string", "feature_training_detail_conflict_unnamed", "Workout", "Тренировка"),
        ("string", "feature_training_detail_conflict_progress_format", "%1$d of %2$d exercises done", "%1$d из %2$d упражнений выполнено"),
        (
            "plurals",
            "feature_training_detail_session_count",
            (("one", "%1$d session"), ("other", "%1$d sessions")),
            (
                ("one", "%1$d сессия"),
                ("few", "%1$d сессии"),
                ("many", "%1$d сессий"),
                ("other", "%1$d сессии"),
            ),
        ),
        ("string", "feature_training_edit_close", "Close", "Закрыть"),
        ("string", "feature_training_edit_title_create", "New training", "Новая тренировка"),
        ("string", "feature_training_edit_title_edit", "Edit training", "Изменить тренировку"),
        ("string", "feature_training_edit_label_name", "Name", "Название"),
        ("string", "feature_training_edit_label_description", "Description", "Описание"),
        ("string", "feature_training_edit_placeholder_description", "Optional notes — focus, week, etc.", "Заметки — фокус, неделя и т.д."),
        ("string", "feature_training_edit_label_tags", "Tags", "Теги"),
        ("string", "feature_training_edit_label_exercises_format", "Exercises (%1$d)", "Упражнения (%1$d)"),
        ("string", "feature_training_edit_add_exercise", "Add exercise", "Добавить упражнение"),
        ("string", "feature_training_edit_exercise_removed", "Exercise removed from training", "Упражнение убрано из тренировки"),
        ("string", "feature_training_edit_no_plan", "no plan yet", "плана пока нет"),
        ("string", "feature_training_edit_plan_set_removed", "Set removed", "Подход удалён"),
        ("string", "feature_training_edit_remove_exercise", "Remove exercise", "Удалить упражнение"),
        ("string", "feature_training_edit_drag_handle", "Reorder", "Переместить"),
        ("string", "feature_training_edit_error_name_required", "Name is required", "Введите название"),
        ("string", "feature_training_edit_error_no_exercises", "Add at least one exercise", "Добавьте хотя бы одно упражнение"),
        ("string", "feature_training_picker_title", "Add exercises", "Добавить упражнения"),
        ("string", "feature_training_picker_search_placeholder", "Search exercises…", "Поиск упражнений…"),
        ("string", "feature_training_picker_add_format", "Add (%1$d)", "Добавить (%1$d)"),
        ("string", "feature_training_picker_cancel", "Cancel", "Отмена"),
        ("string", "feature_training_picker_empty", "No exercises match", "Ничего не найдено"),
    ),
    resource_prefix="feature_training_",
    # The tag mapper and the two copy-reading handlers are deliberately absent (§12.1).
    payload_files=frozenset({
        "DialogState.kt",
        "SingleTrainingStore.kt",
        "SingleTrainingUiModels.kt",
    }),
    store_file="mvi/store/SingleTrainingStore.kt",
    state_fields=(
        ("uuid", "String?"),
        ("mode", "Mode"),
        ("draftEpoch", "Int"),
        ("isSaving", "Boolean"),
        ("name", "String"),
        ("nameError", "Boolean"),
        ("description", "String"),
        ("tags", "ImmutableList<AppTagItem>"),
        ("availableTags", "ImmutableList<AppTagItem>"),
        ("tagSearchQuery", "String"),
        ("exercises", "ImmutableList<TrainingExerciseItem>"),
        ("expandedExerciseUuids", "ImmutableSet<String>"),
        ("pendingSetRestores", "ImmutableList<PendingSetRestore>"),
        ("pastSessions", "ImmutableList<HistorySessionItem>"),
        ("historyCount", "Int"),
        ("activeSession", "ActiveSessionDomain?"),
        ("canPermanentlyDelete", "Boolean"),
        ("originalSnapshot", "Snapshot?"),
        ("pickerState", "PickerState"),
        ("dialogState", "DialogState"),
        ("editCopy", "TrainingEditCopy"),
        ("isLoading", "Boolean"),
    ),
    # S1: CommonHandler keeps the one medium date; ClickHandler holds no wrapper.
    resource_wrapper_readers=frozenset({"mvi/handler/CommonHandler.kt"}),
    # S3: the three uiMode pairs, as ThemeMode subjects.
    preview_subjects={
        "ui/components/ExercisePickerSheet.kt": "ExercisePickerSheet",
        "ui/components/TrainingExerciseCard.kt": "TrainingExerciseCard",
        "ui/components/TrainingHistoryRow.kt": "TrainingHistoryRow",
    },
    # The eight inherited production suppressions of §0.2, in seven files; nothing under any test
    # source set.
    suppressions={
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/di/SingleTrainingFeature.kt": [
            "\"UNCHECKED_CAST\"",
        ],
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/domain/SingleTrainingInteractor.kt": [
            "\"TooManyFunctions\"",
        ],
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/domain/SingleTrainingInteractorImpl.kt": [
            "\"TooManyFunctions\", \"LongParameterList\"",
        ],
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/handler/ClickHandler.kt": [
            "\"TooManyFunctions\", \"LongMethod\", \"LargeClass\"",
        ],
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/ui/SingleTrainingGraph.kt": [
            "\"LongMethod\", \"CyclomaticComplexMethod\"",
        ],
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/ui/components/ExercisePickerSheet.kt": [
            "\"LongParameterList\"",
        ],
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/store/SingleTrainingStore.kt": [
            "\"ComplexInterface\"",
            "\"MviActionNamingRule\"",
        ],
    },
    # The 62 portable identities of §3.2, S14's four among them, and the scene of §11.4.
    test_names={
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/handler/ClickHandlerTest.kt": [
            "OnSaveClick with blank name flips nameError — reachable",
            "OnSaveClick with empty exercises emits ShowSaveError — reachable",
            "OnSaveClick with a name and exercises raises neither error",
            "OnEditClick flips into Edit mode",
            "OnPickerConfirm opens the first inserted card only",
            "OnExerciseCardToggle expands the tapped card",
            "OnExerciseCardToggle on the open card collapses it",
            "cards expand independently",
            "OnExercisePlanAction adds a set to the addressed exercise and refreshes its summary",
            "OnExercisePlanAction removing the last set normalizes to null",
            "a plan edit flips hasChanges through the snapshot signature",
            "OnExerciseRemove removes the exercise and reindexes positions",
            "OnExerciseReorder swaps positions on a non-trivial move",
            "OnExerciseReorder with from equal to to is a no-op",
            "OnAddExerciseClick emits haptic",
            "OnConflictDismiss clears the active session conflict dialog",
            "OnStartSessionClick reports the persisted active session progress",
            "OnConflictResume consumes OpenLiveWorkout with active session uuid",
            "OnBackClick closes the dialog before propagating to navigation",
            "entering the editor after a save collapses every card",
            "a refreshed exercise name is not an unsaved change",
            "OnConfirmDiscard restores an exercise the edit removed",
            "OnConfirmDiscard restores the positions a reorder changed",
            "OnConfirmDiscard restores the plan a card edited",
            "remove emits the undo toast and the undo restores item position and expansion",
            "minus set in a card emits the undo toast and the undo restores the row",
            "a stale set undo after save flipped to read edits nothing",
            "a stale set undo does not edit a re-entered draft",
            "a stale exercise undo after save flipped to read edits nothing",
            "a stale exercise undo does not edit a re-entered draft",
            "an undo during the save's write edits nothing",
            "a failed save re-arms the draft's undos",
            "a cancel during the save's write raises nothing and reverts nothing",
            "a confirmed discard during the save's write reverts nothing",
            "Edit entry resets a stuck isSaving",
            "createTag resolving to an already-selected tag does not duplicate the chip",
            "a set undo whose card was already removed waits for the card",
            "stashed set restores die with the draft",
            "a picker re-add discards the stash for the returned card",
            "an exercise undo that finds its card already back discards the stash",
            "a late picker resolution does not duplicate a card the undo restored",
            "the dashed add chip opens the tag picker sheet",
            "dismissing the tag picker clears the query with the sheet",
            "the topbar menu opens as the DetailMenu sheet",
            "dismissing the menu hides it",
            "archive closes the menu before its result lands",
            "permanent delete replaces the menu with its confirm in one write",
            "a menu dismissed before the confirm's copy lands stays dismissed",
            "the save error carries the copy Init resolved",
            "the undo toasts carry the copy Init resolved",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/handler/CommonHandlerTest.kt": [
            "a load that throws clears isLoading or the route is composed on nothing forever",
            "a training that is not there clears isLoading too",
            "the История count is the total not the visible page",
            "a create route never loads so it is never withheld and it opens clean",
            "Init resolves the editor's constant copy into State",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/handler/NavigationHandlerTest.kt": [
            "Back triggers popBack",
            "OpenExerciseDetail navigates to Screen Exercise",
            "OpenSession navigates to Screen PastSession",
            "OpenLiveWorkout navigates to Screen LiveWorkout with both uuids",
            "OpenLiveWorkout passes a null sessionUuid to LiveWorkout when blank",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/store/SingleTrainingStateRouteArgTest.kt": [
            "null uuid yields create-mode initial state with no loading",
            "non-null uuid yields read-mode initial state and starts loading",
        ],
        "src/iosTest/kotlin/io/github/stslex/workeeper/feature/single_training/SingleTrainingFeatureSceneIosTest.kt": [
            "resourcesDetailEditorPickerAndMenuRenderAndDispatch",
        ],
    },
    golden_test=(
        "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/single_training/golden/TrainingDetailGoldenTest.kt"
    ),
    # TrainingDetailGoldenTest's 3 methods of §3.3, in source order; each renders light and dark.
    golden_methods=["detailWithPlans", "detailNoPlanExercise", "detailEmptyHistory"],
    device_test="src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/single_training/SingleTrainingScreenTest.kt",
    device_fragments=(
        "@Smoke",
        "fun cardHead_tapTogglesExpansion()",
        "fun cardHead_removeIsNotSwallowedByTheHeadTap()",
        "fun expandedCard_showsThePlanBodyWithItsSetbar()",
    ),
    feature_class="SingleTrainingFeature",
    graph_interface="SingleTrainingGraph",
    graph_function="singleTrainingsGraph",
    factory_accessor="singleTrainingGraphFactory",
    create_method="createSingleTrainingGraph",
    store_impl="SingleTrainingStoreImpl",
    store_accessor="singleTrainingStore",
    feature_file="di/SingleTrainingFeature.kt",
    graph_file="ui/SingleTrainingGraph.kt",
    identity_test=Path(
        "app/app/src/test/kotlin/io/github/stslex/workeeper/di/SingleTrainingExtensionIdentityTest.kt"
    ),
    identity_fragments=(
        "private fun AppGraph.singleTraining(uuid: String?): SingleTrainingGraph = singleTrainingGraphFactory.createSingleTrainingGraph(Screen.Training(uuid = uuid))",
    ),
    identity_names=[
        "extension resolves the store through the parent graph",
        "store's app-scoped deps are the SAME instances the parent holds",
        "the session subsystem is inherited from the parent, not rebuilt",
        "both qualified dispatchers are inherited and stay two distinct keys",
        "each extension carries its own route arg into the store state",
        "a null route arg survives the binding and reaches the store as null",
    ],
    # Each identity reaches the graph once through the accessor; the fifth identity's "tr-2" call
    # and the helper itself are deliberately not counted.
    identity_accessor=r'(?<!AppGraph)\.singleTraining\((?:"tr-1"|null)\)',
    extra_checks=(single_training_copy_contract, single_training_dependency_contract),
    create_args="screen",
    preview_inventory={},
    resource_wrapper_dates={
        "mvi/handler/CommonHandler.kt": {"formatMediumDate": 1},
    },
    # 7.13's D5: the two bare previews, pinned per file; every other commonMain file holds none.
    bare_previews={
        "ui/TrainingDetailScreen.kt": 1,
        "ui/components/TrainingDetailMenuSheetContent.kt": 1,
    },
    # D4: TrainingEditGoldenTest's three methods, pinned like golden_test.
    extra_golden_tests={
        "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/single_training/golden/TrainingEditGoldenTest.kt": ["editCollapsed", "editExpanded", "editEmpty"],
    },
)

def exercise_copy_contract(entry: FeatureEntry, common_sources: dict[Path, str]) -> list[str]:
    # S2, S3, F2 and S4 (kmp-phase-7-15-exercise-feature.md §B5.2–§B5.4, §B12.1): the click paths'
    # constant copy lives in State, the confirm resolves on use and lands only on the menu sheet,
    # the load builds its rows before its State lambda, and copy outside composition resolves
    # through the injected StringResolver. Every count is over comment-stripped source.
    failures: list[str] = []
    module = entry.module

    def source_of(relative: str):
        # None only for an absent file: an existing empty file is still checked, and fails.
        path = entry.common_main / relative
        if path not in common_sources:
            failures.append(f"{module}: copy contract cannot read {path.relative_to(entry.root)}")
            return None
        return strip_kotlin_comments(common_sources[path])

    def shown(relative: str) -> Path:
        return (entry.common_main / relative).relative_to(entry.root)

    def require(relative: str, source: str, fragment: str, expected: int) -> None:
        count = source.count(fragment)
        if count != expected:
            times = "once" if expected == 1 else f"{number_word(expected)} times"
            failures.append(
                f"{module}: {shown(relative)} copy contract requires {fragment!r} "
                f"exactly {times}; found {count}"
            )

    def require_parameters(relative: str, source: str, name: str, expected: list) -> None:
        parameters = data_class_parameters(source, name)
        if parameters != expected:
            failures.append(
                f"{module}: {shown(relative)} {name} parameters must be exactly {expected}; "
                f"actual={parameters}"
            )

    # 1. Payload shapes and 2. S2's defaults, exactly once each.
    store_relative = "ui/mvi/store/ExerciseStore.kt"
    store_source = source_of(store_relative)
    if store_source is not None:
        require_parameters(store_relative, store_source, "Haptic", ["type: HapticFeedbackType"])
        require_parameters(
            store_relative, store_source, "ShowArchiveSuccess", ["uuid: String", "message: String"]
        )
        require_parameters(store_relative, store_source, "ShowTagLimitReached", ["message: String"])
        require_parameters(
            store_relative,
            store_source,
            "ShowSetRemovedUndo",
            [
                "message: String",
                "set: PlanSetUiModel",
                "index: Int",
                "draftEpoch: Int",
            ],
        )
        require(store_relative, store_source, "clickCopy = ExerciseClickCopy.EMPTY,", 1)

    dialog_relative = "ui/mvi/store/DialogState.kt"
    dialog_source = source_of(dialog_relative)
    if dialog_source is not None:
        for name in ("TypeChangeConfirm", "PermanentDeleteConfirm"):
            require_parameters(
                dialog_relative,
                dialog_source,
                name,
                [
                    "title: String",
                    "body: String",
                    "impactSummary: String",
                    "confirmLabel: String",
                ],
            )
        require_parameters(
            dialog_relative,
            dialog_source,
            "ActiveSessionConflict",
            [
                "sessionUuid: String",
                "activeSessionName: String",
                "progressLabel: String",
            ],
        )

    copy_fields = (
        "typeChangeTitle",
        "typeChangeBody",
        "typeChangeImpact",
        "typeChangeConfirm",
        "setRemoved",
        "tagLimit",
        "permanentDeleteSuccess",
        "undoLabel",
    )
    model_relative = "ui/mvi/model/ExerciseClickCopy.kt"
    model_source = source_of(model_relative)
    if model_source is not None:
        require_parameters(
            model_relative,
            model_source,
            "ExerciseClickCopy",
            [f"{field_name}: String" for field_name in copy_fields],
        )
        require(model_relative, model_source, "val EMPTY = ExerciseClickCopy(", 1)
        for field_name in copy_fields:
            require(model_relative, model_source, f'{field_name} = "",', 1)

    # 3. ClickHandler: S2's constant reads and guarded confirm, nine resolver reads, no wrapper.
    click_relative = "ui/mvi/handler/ClickHandler.kt"
    click_source = source_of(click_relative)
    if click_source is not None:
        for fragment in (
            "private val stringResolver: StringResolver,",
            "val copy = current.clickCopy",
            "title = copy.typeChangeTitle,",
            "body = copy.typeChangeBody,",
            "impactSummary = copy.typeChangeImpact,",
            "confirmLabel = copy.typeChangeConfirm,",
            "message = current.clickCopy.setRemoved,",
            "val message = current.clickCopy.permanentDeleteSuccess",
            "val undoLabel = current.clickCopy.undoLabel",
            "actionLabel = undoLabel,",
            "if (latest.bottomSheetState == BottomSheetState.DetailMenu) {",
            "latest.copy(dialogState = confirm, bottomSheetState = BottomSheetState.Hidden)",
        ):
            require(click_relative, click_source, fragment, 1)
        require(click_relative, click_source, "message = current.clickCopy.tagLimit,", 2)
        require(click_relative, click_source, "stringResolver.getString(", 9)
        for fragment in ("ResourceWrapper", "resourceWrapper", "System.currentTimeMillis"):
            require(click_relative, click_source, fragment, 0)

    # 4. CommonHandler: Init's eight resolver reads, F2's two rows, and 5. F2's order.
    common_relative = "ui/mvi/handler/CommonHandler.kt"
    common_source = source_of(common_relative)
    if common_source is not None:
        history_line = "val recentHistory = result.history.map { it.toUi(resourceWrapper) }.toImmutableList()"
        plan_line = "val adhocPlan = result.adhocPlan?.map { it.toUi() }?.toImmutableList()"
        for fragment in (
            "private val resourceWrapper: ResourceWrapper,",
            "private val stringResolver: StringResolver,",
            "ExerciseClickCopy(",
            "onSuccess = { copy -> updateState { it.copy(clickCopy = copy) } },",
            "undoLabel = stringResolver.getString(Res.string.core_ui_kit_toast_undo),",
            history_line,
            plan_line,
            ").withDraftCarriedFrom(current)",
        ):
            require(common_relative, common_source, fragment, 1)
        require(common_relative, common_source, "stringResolver.getString(", 8)
        require(common_relative, common_source, "resolveClickCopy()", 2)
        require(common_relative, common_source, "applyLoaded", 0)
        history_at = common_source.find(history_line)
        plan_at = common_source.find(plan_line)
        history_lambda = common_source.find("updateStateImmediate { current ->", max(history_at, 0))
        plan_lambda = common_source.find("updateStateImmediate { current ->", max(plan_at, 0))
        if (
            history_at < 0
            or plan_at < history_at
            or plan_lambda < 0
            or history_lambda != plan_lambda
        ):
            failures.append(
                f"{module}: {shown(common_relative)} must build the history rows and the plan"
                " before the load's State lambda (F2)"
            )

    # 6. 7.11's rules 4–7 over this module's layout: no top-level CMP read; ui/mvi/ reads only
    # through the resolver; no other ui/ file reads copy outside composition.
    top_level_import = re.compile(
        r"^import org\.jetbrains\.compose\.resources\.(getString|getPluralString)\b", re.MULTILINE
    )
    mvi_prefix = (entry.common_main / "ui/mvi").as_posix() + "/"
    ui_prefix = (entry.common_main / "ui").as_posix() + "/"
    mvi_files = ui_files = 0
    for path, raw in common_sources.items():
        source = strip_kotlin_comments(raw)
        relative = path.relative_to(entry.root)
        for match in top_level_import.finditer(source):
            failures.append(
                f"{module}: {relative} imports CMP's top-level {match.group(1)}; read copy "
                "outside composition through the injected StringResolver"
            )
        posix = path.as_posix()
        if posix.startswith(mvi_prefix):
            mvi_files += 1
            for call in ("getString(", "getPluralString("):
                total = source.count(call)
                through_resolver = source.count(f"stringResolver.{call}")
                if total != through_resolver:
                    failures.append(
                        f"{module}: {relative} reads copy outside the StringResolver: {call!r} "
                        f"{total} time(s), {through_resolver} through stringResolver"
                    )
            for token in ("stringResource(", "pluralStringResource("):
                if token in source:
                    failures.append(
                        f"{module}: {relative} resolves composable copy under ui/mvi/: {token!r}"
                    )
        elif posix.startswith(ui_prefix):
            ui_files += 1
            for call in ("getString(", "getPluralString("):
                if call in source:
                    failures.append(
                        f"{module}: {relative} reads copy outside composition under ui/: {call!r}"
                    )
    if mvi_files == 0 or ui_files == 0:
        failures.append(
            f"{module}: copy contract walked {mvi_files} ui/mvi/ and {ui_files} other ui/ file(s); "
            "both must be non-empty"
        )
    return failures


def exercise_media_contract(entry: FeatureEntry, common_sources: dict[Path, str]) -> list[str]:
    # §B5.6: Part A's launcher and its four event routes stay in the graph, the camera permission is
    # read once, and the description block's Coil request is image-viewer's chain on
    # LocalPlatformContext. Counts are over comment-stripped source.
    failures: list[str] = []
    module = entry.module
    required = {
        "ui/ExerciseGraph.kt": (
            "val imageSources = rememberImageSourceLauncher(",
            "is Event.NavigateLaunchCamera -> imageSources.launchCamera(event.target.value)",
            "Event.NavigateLaunchGallery -> imageSources.launchGallery()",
            "Event.NavigateRequestCameraPermission -> imageSources.requestCameraPermission()",
            "Event.NavigateOpenAppSettings -> imageSources.openAppSettings()",
        ),
        "ui/mvi/handler/ClickHandler.kt": ("if (cameraPermission.isGranted()) {",),
        "ui/components/ExerciseDescriptionBlock.kt": (
            "import coil3.compose.LocalPlatformContext",
            "ImageRequest.Builder(",
        ),
    }
    for relative, fragments in required.items():
        path = entry.common_main / relative
        source = strip_kotlin_comments(common_sources.get(path, ""))
        for fragment in fragments:
            count = source.count(fragment)
            if count != 1:
                failures.append(
                    f"{module}: {path.relative_to(entry.root)} media contract requires "
                    f"{fragment!r} exactly once; found {count}"
                )
    block = strip_kotlin_comments(
        common_sources.get(entry.common_main / "ui/components/ExerciseDescriptionBlock.kt", "")
    )
    chain = "ImageRequest.Builder(LocalPlatformContext.current).data(it).crossfade(true).build()"
    if compact(block).count(compact(chain)) != 1:
        failures.append(
            f"{module}: Coil request must use exactly one "
            "LocalPlatformContext/data(it)/crossfade(true)/build() chain"
        )
    return failures


def exercise_dependency_contract(
    entry: FeatureEntry,
    common_sources: dict[Path, str],
) -> list[str]:
    # §B3.5 and §B10: core:data:database stays one production edge, read by the domain mapper
    # alone; no test set reads it or core:data:database-test; shared code names no java.*.
    # Ruling of 2026-10-21 (§B19): the domain suite's in-file ExerciseRepository fake has to spell
    # setAdhocPlan's parameter type (7.10 D5's precedent), so that one suite may import
    # PlanSetDataModel once and name it once more, and nothing else from core:data:database.
    failures: list[str] = []
    module = entry.module
    build_source = (entry.root / "build.gradle.kts").read_text(encoding="utf-8")
    if "project(\":core:data:database-test\")" in build_source:
        failures.append(f"{module}: build must not depend on :core:data:database-test")
    mapper = entry.common_main / "domain/mapper/ExerciseDomainMapper.kt"
    domain_suite = (
        entry.root / "src/commonTest/kotlin" / entry.package_dir
        / "domain/ExerciseInteractorImplTest.kt"
    )
    if "core.data.database" not in strip_kotlin_comments(common_sources.get(mapper, "")):
        failures.append(
            f"{module}: {mapper.relative_to(entry.root)} "
            "must be the module's one reader of core:data:database"
        )
    java_reference = re.compile(r"(?<![\w.])java\.")
    shared_sets = ("src/commonMain/", "src/commonTest/", "src/iosTest/")
    for path in sorted((entry.root / "src").rglob("*.kt")):
        source = strip_kotlin_comments(path.read_text(encoding="utf-8"))
        relative = path.relative_to(entry.root)
        if path == domain_suite:
            references = re.findall(r"core\.data\.database[\w.]*", source)
            if references != ["core.data.database.sets.PlanSetDataModel"]:
                failures.append(
                    f"{module}: {relative} may name core:data:database only in one "
                    f"PlanSetDataModel import; found {references}"
                )
            mentions = len(re.findall(r"\bPlanSetDataModel\b", source))
            if mentions != 2:
                failures.append(
                    f"{module}: {relative} must name PlanSetDataModel exactly twice, the import "
                    f"and setAdhocPlan's parameter; found {mentions}"
                )
        elif path != mapper and "core.data.database" in source:
            failures.append(
                f"{module}: {relative} must not read core:data:database;"
                " only the domain mapper maps its data models"
            )
        if relative.as_posix().startswith(shared_sets) and java_reference.search(source):
            failures.append(f"{module}: {relative} names java.* in shared code")
    return failures


def exercise_device_contract(entry: FeatureEntry, common_sources: dict[Path, str]) -> list[str]:
    # D5 (§B11.3): the second device class is pinned like device_test, each fragment exactly once;
    # a missing file reads as empty, so every fragment reports.
    failures: list[str] = []
    relative = (
        "src/androidDeviceTest/kotlin/" + entry.package_dir + "/ExerciseFormBasicsTest.kt"
    )
    path = entry.root / relative
    source = path.read_text(encoding="utf-8") if path.is_file() else ""
    for fragment in (
        "@Smoke",
        "fun f02_saveIsEnabledWithAnEmptyName_soTheBlankNameErrorIsReachable()",
        "fun f02_tappingSaveWithAValidName_dispatchesOnSaveClick()",
    ):
        if source.count(fragment) != 1:
            failures.append(f"{entry.module}: {relative} must keep {fragment!r} exactly once")
    return failures


EXERCISE_ENTRY = FeatureEntry(
    module="feature:exercise",
    short_name="exercise",
    root=Path("feature/exercise"),
    package_dir="io/github/stslex/workeeper/feature/exercise",
    # Every plugin, resource-package, interop, dependency and apply line of §B10, each exactly
    # once; kotlin("test") is counted by kotlin_test_count. The one production core:data:database
    # edge is here; exercise_dependency_contract keeps every other reader out.
    build_fragments=(
        "alias(libs.plugins.convention.kmpComposeLibrary)",
        "alias(libs.plugins.metro)",
        "alias(libs.plugins.paparazzi)",
        "packageOfResClass = \"io.github.stslex.workeeper.feature.exercise.resources\"",
        "includeJavax()",
        "api(project(\":core:core\"))",
        "api(project(\":core:ui:kit\"))",
        "api(project(\":core:ui:mvi\"))",
        "api(project(\":core:ui:navigation\"))",
        "api(project(\":core:ui:plan-editor\"))",
        "implementation(project(\":core:data:database\"))",
        "implementation(project(\":core:data:exercise\"))",
        "api(libs.cmp.ui)",
        "api(libs.coroutines.core)",
        "api(libs.kotlinx.collections.immutable)",
        "implementation(libs.cmp.animation)",
        "implementation(libs.coil.compose)",
        "implementation(libs.coroutine.test)",
        "implementation(libs.cmp.ui.test)",
        "\"androidHostTestImplementation\"(project(\":core:ui:golden-harness\"))",
        "\"androidDeviceTestImplementation\"(libs.bundles.android.test)",
        "\"androidDeviceTestImplementation\"(libs.androidx.compose.ui.test.junit4)",
        "\"androidDeviceTestImplementation\"(platform(libs.androidx.compose.bom))",
        "\"androidDeviceTestImplementation\"(libs.androidx.compose.ui.test.manifest)",
        "\"androidDeviceTestImplementation\"(project(\":core:ui:test-utils\"))",
        "apply(from = \"$rootDir/gradle/golden-gate.gradle.kts\")",
    ),
    kotlin_test_count=2,
    # Exact private EN/RU catalog, in file order: (tag, name, EN value, RU value), as §B3.4 prints
    # it at exit — P1's six positional plural items.
    catalog=(
        ("string", "feature_exercise_detail_recent", "History", "История"),
        ("string", "feature_exercise_detail_track_now", "Log now", "Записать сейчас"),
        ("string", "feature_exercise_detail_default_plan", "Default plan", "План по умолчанию"),
        ("string", "feature_exercise_track_now_default_training_name", "Track now", "Отдельная тренировка"),
        ("string", "feature_exercise_track_now_conflict_unnamed", "Workout", "Тренировка"),
        ("string", "feature_exercise_track_now_conflict_progress_format", "%1$d of %2$d exercises done", "%1$d из %2$d упражнений выполнено"),
        ("string", "feature_exercise_detail_edit", "Edit", "Изменить"),
        ("string", "feature_exercise_detail_action_more", "More actions", "Ещё"),
        ("string", "feature_exercise_detail_archive", "Archive", "В архив"),
        ("string", "feature_exercise_detail_archive_success_format", "‘%1$s’ archived", "«%1$s» в архиве"),
        ("string", "feature_exercise_detail_archive_blocked_title", "Cannot archive", "Нельзя архивировать"),
        ("string", "feature_exercise_detail_archive_blocked_used_in_format", "used in %1$s", "в тренировках: %1$s"),
        ("string", "feature_exercise_detail_archive_blocked_next_step", "Remove it from those trainings first, then archive.", "Сначала уберите его из этих тренировок, затем архивируйте."),
        ("string", "feature_exercise_detail_archive_blocked_ok", "OK", "ОК"),
        ("string", "feature_exercise_detail_archive_undo", "Undo", "Восстановить"),
        ("string", "feature_exercise_detail_permanent_delete", "Delete permanently", "Удалить навсегда"),
        ("string", "feature_exercise_detail_permanent_delete_confirm_title", "Delete ‘%1$s’ permanently?", "Удалить «%1$s» навсегда?"),
        ("string", "feature_exercise_detail_permanent_delete_confirm_body", "This exercise has no history. This cannot be undone.", "У упражнения нет истории. Действие необратимо."),
        ("string", "feature_exercise_detail_permanent_delete_confirm_impact", "No history affected", "История не затронута"),
        ("string", "feature_exercise_detail_permanent_delete_confirm_button", "Delete", "Удалить"),
        ("string", "feature_exercise_detail_permanent_delete_success", "Exercise deleted", "Упражнение удалено"),
        ("string", "feature_exercise_edit_title_create", "New exercise", "Новое упражнение"),
        ("string", "feature_exercise_edit_title_edit", "Edit exercise", "Изменить упражнение"),
        ("string", "feature_exercise_edit_close_description", "Close", "Закрыть"),
        ("string", "feature_exercise_edit_label_name", "Name", "Название"),
        ("string", "feature_exercise_edit_label_tags", "Tags", "Теги"),
        ("string", "feature_exercise_edit_label_description", "Description", "Описание"),
        ("string", "feature_exercise_edit_placeholder_description", "Optional notes — technique, gym-specific details", "Заметки — техника, особенности зала"),
        ("string", "feature_exercise_edit_tag_limit", "Maximum 10 tags per exercise", "Максимум 10 тегов на упражнение"),
        ("string", "feature_exercise_edit_error_name_required", "Name is required", "Введите название"),
        ("string", "feature_exercise_edit_error_name_duplicate", "Exercise with this name already exists", "Упражнение с таким именем уже есть"),
        ("string", "feature_exercise_edit_plan_info_body", "This is the plan for when the exercise is performed on its own — from its card, with the “Log now” button, outside any training.\\n\\nInside a training every exercise has a plan of its own: it is set in the training editor and does not touch this one. If neither plan exists, sets are added right in the session.", "Это план на случай, когда упражнение выполняется само по себе — из его карточки, кнопкой «Записать сейчас», без тренировки.\\n\\nВнутри тренировки план у каждого упражнения свой: он задаётся в редакторе тренировки и этот план не трогает. Если планов нет ни там, ни тут, подходы добавляются прямо в сессии."),
        ("string", "feature_exercise_edit_plan_info_description", "What the default plan is for", "Что такое план по умолчанию"),
        ("string", "feature_exercise_edit_plan_set_type_tooltip", "Tap to cycle: warmup → work → failure → drop", "Нажмите, чтобы переключить: разминка → рабочий → отказ → дроп"),
        ("string", "feature_exercise_edit_plan_type_change_weightless_title", "Switch to weightless?", "Переключить на без веса?"),
        ("string", "feature_exercise_edit_plan_type_change_weightless_body", "Weight values from this exercise’s plans will be cleared. This cannot be undone.", "Значения веса из планов этого упражнения будут очищены. Это нельзя отменить."),
        ("string", "feature_exercise_edit_plan_type_change_weightless_impact", "All plan weights cleared", "Все веса в планах очищены"),
        ("string", "feature_exercise_edit_plan_type_change_weightless_confirm", "Switch", "Переключить"),
        ("string", "feature_exercise_edit_plan_set_removed", "Set removed", "Подход удалён"),
        ("string", "feature_exercise_edit_tags_counter", "%1$d of %2$d", "%1$d из %2$d"),
        ("string", "feature_exercise_image_thumb_description", "Attached exercise photo", "Прикреплённое фото упражнения"),
        ("string", "feature_exercise_image_thumb_open_description", "Open photo full screen", "Открыть фото на весь экран"),
        ("string", "feature_exercise_image_thumb_pick_description", "Add a photo", "Добавить фото"),
        ("string", "feature_exercise_image_placeholder_description", "No exercise photo attached", "Фото упражнения не прикреплено"),
        ("string", "feature_exercise_image_source_dialog_title", "Choose source", "Выберите источник"),
        ("string", "feature_exercise_image_source_camera", "Take photo", "Сделать фото"),
        ("string", "feature_exercise_image_source_gallery", "Choose from gallery", "Выбрать из галереи"),
        ("string", "feature_exercise_image_permission_denied_title", "Camera permission denied", "Доступ к камере запрещён"),
        ("string", "feature_exercise_image_permission_denied_body", "To take a photo, grant camera access in app settings. You can still choose an existing photo from your gallery.", "Чтобы сделать фото, разрешите доступ к камере в настройках приложения. Вы можете выбрать фото из галереи."),
        ("string", "feature_exercise_image_permission_denied_action_settings", "Open settings", "Открыть настройки"),
        ("string", "feature_exercise_image_error_save_failed", "Could not save image — try again", "Не удалось сохранить фото — попробуйте ещё раз"),
        ("string", "feature_exercise_image_error_load_failed", "Could not load image", "Не удалось загрузить фото"),
        ("string", "feature_exercise_image_error_decode_failed", "Image format not supported", "Формат изображения не поддерживается"),
        (
            "plurals",
            "feature_exercise_detail_history_count",
            (("one", "%1$d session"), ("other", "%1$d sessions")),
            (
                ("one", "%1$d сессия"),
                ("few", "%1$d сессии"),
                ("many", "%1$d сессий"),
                ("other", "%1$d сессии"),
            ),
        ),
    ),
    resource_prefix="feature_exercise_",
    # G2: exercise-chart's keys and kit's two type labels share the prefix; they are not ours.
    foreign_resource_prefixes=("feature_exercise_chart_", "feature_exercise_detail_type_"),
    # The mapper and the two copy-reading handlers are deliberately absent (§B12.1).
    payload_files=frozenset({
        "DialogState.kt",
        "ExerciseStore.kt",
        "ExerciseClickCopy.kt",
        "HistoryUiModel.kt",
        "ImageDisplay.kt",
        "ImageErrorType.kt",
        "ImageSourceUiModel.kt",
        "PendingImage.kt",
        "PersonalRecordUiModel.kt",
    }),
    store_file="ui/mvi/store/ExerciseStore.kt",
    state_fields=(
        ("uuid", "String?"),
        ("mode", "Mode"),
        ("draftEpoch", "Int"),
        ("isSaving", "Boolean"),
        ("name", "String"),
        ("nameError", "Boolean"),
        ("nameDuplicateError", "Boolean"),
        ("type", "ExerciseTypeUiModel"),
        ("description", "String"),
        ("tags", "ImmutableList<AppTagItem>"),
        ("availableTags", "ImmutableList<AppTagItem>"),
        ("tagSearchQuery", "String"),
        ("recentHistory", "ImmutableList<HistoryUiModel>"),
        ("historyCount", "Int"),
        ("originalSnapshot", "Snapshot?"),
        ("isLoading", "Boolean"),
        ("canPermanentlyDelete", "Boolean"),
        ("adhocPlan", "ImmutableList<PlanSetUiModel>?"),
        ("pendingTypeChange", "ExerciseTypeUiModel?"),
        ("imagePath", "String?"),
        ("imageLastModified", "Long"),
        ("pendingImage", "PendingImage"),
        ("dialogState", "DialogState"),
        ("clickCopy", "ExerciseClickCopy"),
        ("bottomSheetState", "BottomSheetState"),
        ("personalRecord", "PersonalRecordUiModel?"),
    ),
    # S4: the UI mapper keeps the two dates; CommonHandler hands it the wrapper.
    resource_wrapper_readers=frozenset({"ui/mvi/handler/CommonHandler.kt", "ui/mvi/mapper/ExerciseUiMapper.kt"}),
    # S6: the one uiMode pair, as a ThemeMode subject.
    preview_subjects={"ui/components/ImageSourceSheetContent.kt": "ImageSourceSheetContent"},
    # The seven inherited production suppressions of §B0.2, in six files; nothing under any test
    # source set.
    suppressions={
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/di/ExerciseFeature.kt": [
            "\"UNCHECKED_CAST\"",
        ],
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/ExerciseInteractor.kt": [
            "\"TooManyFunctions\"",
        ],
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/domain/ExerciseInteractorImpl.kt": [
            "\"LongParameterList\", \"TooManyFunctions\"",
        ],
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/ExerciseGraph.kt": [
            "\"LongMethod\", \"CyclomaticComplexMethod\"",
        ],
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/handler/ClickHandler.kt": [
            "\"TooManyFunctions\", \"LargeClass\"",
            "\"LongMethod\"",
        ],
        "src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/store/ExerciseStore.kt": [
            "\"MviActionNamingRule\"",
        ],
    },
    # The 142 portable identities: §B3.2's 133 (S17's six among them), D-01's nine, and the scene of §B11.4.
    test_names={
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/handler/ClickHandlerTest.kt": [
            "OnTypeToggle to WEIGHTLESS with weighted rows raises the confirm instead of switching",
            "OnTypeToggle to WEIGHTLESS with no weights switches immediately and asks nothing",
            "OnTypeChangeConfirm commits the switch and clears the weights it warned about",
            "OnTypeChangeDismiss leaves both the type and the weights alone",
            "OnSaveClick with blank name sets nameError without saving — reachable",
            "OnSaveClick with a name does not set nameError and proceeds",
            "OnEditClick flips mode to Edit and snapshots current state",
            "OnDetailMenuClick opens the detail-menu sheet",
            "OnSheetDismiss hides the detail-menu sheet",
            "OnPlanInfoClick opens the plan-info sheet",
            "OnEditClick from the sheet closes it in the same transition",
            "OnPermanentDeleteMenuClick swaps the sheet for the confirm dialog",
            "OnArchiveMenuClick closes the sheet before the archive result lands",
            "confirm permanent delete defers - nothing runs until the commit",
            "minus set emits the undo toast and the undo restores the row",
            "a stale set undo after save flipped to read edits nothing",
            "a stale set undo does not edit a re-entered draft",
            "an undo restored across a type switch re-enters weightless",
            "an undo during the save's write edits nothing",
            "a duplicate-name save re-arms the draft's undos",
            "a cancel during the save's write raises nothing and reverts nothing",
            "a back during the save's write raises nothing",
            "a confirmed discard during the save's write pops nothing",
            "FlipToReadMode during the save's write flips nothing",
            "Edit entry resets a stuck isSaving",
            "createTag resolving to an already-selected tag does not duplicate the chip",
            "a second create landing on a full draft chips nothing past the cap",
            "a plan value edit emits no undo toast",
            "the dashed add chip opens the tag picker sheet",
            "dismissing the tag picker clears the query with the sheet",
            "OnTagToggle adds tag when not selected",
            "OnTagToggle blocks adding when 10 tags already selected",
            "OnDismissArchiveBlocked is no-op",
            "OnTrackNowClick emits ContextClick haptic",
            "OnTrackNowClick reports the persisted active session progress",
            "OnTrackNowResumeConfirm with no pending conflict is a no-op",
            "OnTrackNowResumeConfirm consumes OpenLiveWorkout with active session uuid",
            "OnTrackNowConflictDismiss clears the active session conflict dialog",
            "OnCancelClick from clean Edit on existing flips to Read mode",
            "OnCancelClick from clean create mode pops back",
            "OnCancelClick from dirty Edit on existing shows FLIP_TO_READ discard dialog",
            "OnConfirmDiscard with POP_SCREEN navigates back",
            "OnConfirmDiscard with FLIP_TO_READ flips mode without popping",
            "OnConfirmDiscard with FLIP_TO_READ restores the plan the edit changed",
            "OnConfirmDiscard with FLIP_TO_READ restores a plan that was absent",
            "OnBackClick in clean Edit on existing flips to Read mode",
            "OnPermanentDeleteMenuClick is no-op when not eligible",
            "OnPermanentDeleteMenuClick surfaces the PermanentDeleteConfirm dialog when eligible",
            "OnEditImageClick opens the image source picker dialog",
            "OnRemoveImageClick stages a RemoveExisting pending image",
            "saving an image removal clears the persisted path before deleting the old file",
            "a rejected image removal keeps the attachment file and the staged draft",
            "cancelling an image removal restores the attachment without deleting its file",
            "OnImageSourceDialogDismiss hides the source dialog",
            "OnPermissionDeniedDialogDismiss hides the permission dialog",
            "OnCameraPermissionDenied surfaces the permission denied dialog",
            "OnPermissionDeniedSettingsClick emits NavigateOpenAppSettings",
            "camera with the permission creates a capture target and launches the camera",
            "camera without the permission asks for it and creates no capture target",
            "gallery launches the photo picker and reads no permission",
            "a granted permission launches the camera on a fresh capture target",
            "OnImageThumbnailClick with committed path consumes OpenImageViewer with the path",
            "Edit mode opens the viewer as editable",
            "Read mode opens the viewer as NOT editable",
            "OnImageThumbnailClick with no image is a no-op",
            "OnAdhocPlanEditorAction OnAddSet appends a default set to the in-memory plan",
            "OnAdhocPlanEditorAction OnSetRemove on the only row normalizes the plan back to null",
            "OnAdhocPlanEditorAction OnSetWeightChange routes through the reducer",
            "OnAdhocPlanEditorAction OnSetRepsChange routes through the reducer",
            "OnAdhocPlanEditorAction OnSetTypeChange routes through the reducer",
            "OnCancelClick from create-mode with edited plan surfaces POP_SCREEN discard dialog",
            "OnBackClick from create-mode with edited plan surfaces POP_SCREEN discard dialog",
            "OnBackClick in create mode with a typed name raises the discard sheet",
            "a menu dismissed before the confirm's copy lands stays dismissed",
            "the type-change confirm carries the copy Init resolved",
            "the set-removed toast carries the copy Init resolved",
            "both tag-limit toasts carry the copy Init resolved",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/handler/CommonHandlerTest.kt": [
            "Init for create mode does not load exercise",
            "Init for read mode kicks off observe + load",
            "a load that throws clears isLoading or the route is composed on nothing forever",
            "a create route never loads so it is never withheld",
            "Init in create mode records the starting values as the baseline",
            "a re-fired Init in create mode keeps the first baseline",
            "a re-fired Init preserves a dirty draft and its staged image removal",
            "ImagePicked sets pendingImage to NewFromUri and hides the source dialog",
            "the load keys the image by its file's modification time",
            "an exercise without an image reads no modification time",
            "ImagePickCancelled hides the source dialog without staging a pending image",
            "a REPLACE request reopens the image source picker",
            "a REMOVE request stages the removal",
            "an unrecognised request name is dropped rather than acted on",
            "every declared request verb resolves to an action",
            "Init resolves the click paths' constant copy into State",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise/domain/ExerciseInteractorImplTest.kt": [
            "countSessions forwards to countSessionsUsing",
            "saveExercise forwards the snapshot uuid to repository on Success",
            "saveExercise propagates the lastAdhocSets payload",
            "saveExercise propagates DuplicateName from repository",
            "archive returns Blocked when active trainings exist",
            "archive returns Success when no active trainings",
            "getImageLastModified forwards to the image storage",
            "canPermanentlyDelete delegates to repository",
            "permanentlyDelete delegates to repository",
            "observePersonalRecord forwards to repository and maps to domain",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/store/ExerciseDirtyStateTest.kt": [
            "a freshly loaded exercise is clean",
            "ONLY the snapshot term - a renamed exercise with no image and no plan edit",
            "ONLY the image term - a staged picture the snapshot cannot see",
            "ONLY the plan term - create mode where there is no snapshot to compare against",
            "create mode with no plan and nothing typed is clean",
            "a plan the baseline has seen reads clean",
            "an inline plan edit the baseline has not seen reads dirty",
            "an empty plan and a null plan are the same plan",
            "a create draft with a typed name reads dirty",
            "a create draft with a typed description reads dirty",
            "a create draft with a switched type reads dirty",
            "a create draft with a selected tag reads dirty",
            "a create draft whose type is switched back reads clean",
            "back is intercepted in create mode only once something is typed",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/handler/NavigationHandlerTest.kt": [
            "Back triggers popBack",
            "OpenSession navigates to Screen PastSession with the session uuid",
            "OpenLiveWorkout navigates to Screen LiveWorkout with the resume session uuid",
            "OpenImageViewer carries the model arg and the caller's own capability",
            "OpenImageViewer forwards a non-editable caller as non-editable",
            "OpenChart navigates to Screen ExerciseChart with the exercise uuid",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/mapper/ExerciseUiMapperTest.kt": [
            "weighted record splits into trimmed weight and reps with the medium date",
            "fractional record weight trims trailing zeros",
            "weightless record carries no weight label",
            "history entry maps day-month date and the tight summary form",
            "history summary caps at five sets and appends an ellipsis",
            "weightless history sets summarize as bare reps",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/handler/ExerciseDeferredDeleteDbTest.kt": [
            "confirming deletes nothing while the snackbar lives",
            "back is consumed only after the snackbar is queued with the real undo label",
            "the window closing commits the delete",
            "undo — or a process death inside the window — leaves the row",
            "the commit outlives the screen that scheduled it",
            "the queued delete carries the message and the undo label Init resolved",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/handler/InputHandlerTest.kt": [
            "OnNameChange updates name and clears nameError",
            "OnNameChange clears nameDuplicateError",
            "OnDescriptionChange truncates to 2000 chars",
            "OnTagSearchChange updates tagSearchQuery",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/store/ExerciseStateRouteArgTest.kt": [
            "null uuid yields create-mode initial state with no loading flag",
            "non-null uuid yields read-mode initial state and starts loading",
        ],
        "src/commonTest/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/handler/PermanentDeleteUndoLabelRuTest.kt": [
            "the queued undo label resolves from the real russian catalog",
        ],
        "src/iosTest/kotlin/io/github/stslex/workeeper/feature/exercise/ExerciseFeatureSceneIosTest.kt": [
            "resourcesDetailEditorSheetsAndActionsRenderAndDispatch",
        ],
    },
    golden_test=(
        "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/exercise/golden/ExerciseDetailGoldenTest.kt"
    ),
    # ExerciseDetailGoldenTest's 15 methods of §B3.3, in source order; each renders light and dark.
    golden_methods=["screenLoaded", "screenEmpty", "screenNoRecord", "screenEmptyHistory", "screenEmptyPlan", "screenImageNoDescription", "screenWeightless", "topbar", "sheetDetailMenu", "sheetDetailMenuDeletable", "recordHero", "recordHeroWeightless", "recordHeroMetaClamped", "historyRow", "historyRowRecord"],
    device_test="src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/exercise/ExerciseScreenTest.kt",
    device_fragments=(
        "@Smoke",
        "fun edit_inCreateMode_startsFromAnEmptyInlinePlan()",
        "fun edit_inCreateMode_rendersPlanRowsWhenAdhocPlanIsPresent()",
        "fun edit_forExistingExercise_editsThePlanInlineToo()",
    ),
    feature_class="ExerciseFeature",
    graph_interface="ExerciseGraph",
    graph_function="exerciseGraph",
    factory_accessor="exerciseGraphFactory",
    create_method="createExerciseGraph",
    store_impl="ExerciseStoreImpl",
    store_accessor="exerciseStore",
    feature_file="di/ExerciseFeature.kt",
    graph_file="ui/ExerciseGraph.kt",
    identity_test=Path(
        "app/app/src/test/kotlin/io/github/stslex/workeeper/di/ExerciseExtensionIdentityTest.kt"
    ),
    identity_fragments=(
        "private fun AppGraph.exercise(uuid: String?): ExerciseGraph = exerciseGraphFactory.createExerciseGraph(Screen.Exercise(uuid = uuid))",
    ),
    identity_names=[
        "extension resolves the store through the parent graph",
        "store's app-scoped deps are the SAME instances the parent holds",
        "both qualified dispatchers are inherited and stay two distinct keys",
        "the camera permission is inherited from the parent graph",
        "each extension carries its own route arg into the store state",
        "a null route arg survives the binding and reaches the store as null",
    ],
    # Each identity reaches the graph once through the accessor; the fifth identity's "ex-2" call
    # and the helper itself are deliberately not counted.
    identity_accessor=r'(?<!AppGraph)\.exercise\((?:"ex-1"|null)\)',
    # The one app device test that composes the graph directly passes the factory (7.9's §6
    # precedent; ruling of 2026-10-21, §B19).
    extra_root_fragments={
        "app/app/src/androidTest/kotlin/io/github/stslex/workeeper/app/ExerciseCreatePersistenceTest.kt": (
            "MetroTestGraphHolder.graph.exerciseGraphFactory",
            "exerciseGraph(factory = factory)",
        ),
    },
    extra_checks=(
        exercise_copy_contract,
        exercise_media_contract,
        exercise_dependency_contract,
        exercise_device_contract,
    ),
    create_args="screen",
    preview_inventory={},
    resource_wrapper_dates={
        "ui/mvi/mapper/ExerciseUiMapper.kt": {"formatDayMonth": 1, "formatMediumDate": 1},
    },
    # 7.13's D5: the 14 bare previews, pinned per file; every other commonMain file holds none.
    bare_previews={
        "ui/ExerciseDetailScreen.kt": 5,
        "ui/ExerciseEditScreen.kt": 3,
        "ui/components/ExerciseDescriptionBlock.kt": 2,
        "ui/components/ExerciseDetailSheets.kt": 1,
        "ui/components/ExerciseHistoryRow.kt": 2,
        "ui/components/PlanInfoSheetContent.kt": 1,
    },
    # 7.14's D4: the Description and Edit golden classes, pinned like golden_test.
    extra_golden_tests={
        "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/exercise/golden/ExerciseDescriptionBlockGoldenTest.kt": ["readWithImage", "readWithPlaceholder", "editable"],
        "src/androidHostTest/kotlin/io/github/stslex/workeeper/feature/exercise/golden/ExerciseEditGoldenTest.kt": ["createEmpty", "editWeighted", "editWeightless", "editBlankNameError", "editDuplicateNameError", "sheetPlanInfo"],
    },
    # G1: exercise reads the image viewer's result back, so it registers through this overload.
    screen_registration="navComponentScreenWithResults",
)

# Every shared feature entry the gate holds to its exact contract.
FEATURE_ENTRIES = (
    ARCHIVE_ENTRY,
    ALL_TRAININGS_ENTRY,
    ALL_EXERCISES_ENTRY,
    PAST_SESSION_ENTRY,
    HOME_ENTRY,
    EXERCISE_CHART_ENTRY,
    SINGLE_TRAINING_ENTRY,
    EXERCISE_ENTRY,
)


# Compose resources fill only positional `%N$d` / `%N$s` (Regex("%(\d+)\$[ds]") in 1.11.1) and
# render anything else literally, so a bare `%d` moved from Android `res` ships as "%d sessions".
# `%%` is no escape there (it renders as "%%"), and `%0$d` matches the regex but reads args[-1].
POSITIONAL_PLACEHOLDER = re.compile(r"%[1-9]\d*\$[ds]")
ANY_PLACEHOLDER = re.compile(r"%%|%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]|%")


def placeholder_tokens(text: str) -> list[str]:
    """Every `%` sequence in [text], read as the longest positional or printf-style token."""
    tokens: list[str] = []
    cursor = 0
    while (start := text.find("%", cursor)) != -1:
        match = POSITIONAL_PLACEHOLDER.match(text, start) or ANY_PLACEHOLDER.match(text, start)
        tokens.append(match.group(0))
        cursor = start + len(match.group(0))
    return tokens


def catalog_values(path: Path) -> list[tuple[str, str]]:
    """(key, text) for every string, plural quantity, and string-array item in [path]."""
    values: list[tuple[str, str]] = []
    for element in ET.parse(path).getroot():
        name = element.attrib.get("name", "")
        if element.tag == "string":
            values.append((name, "".join(element.itertext())))
        else:
            for index, item in enumerate(element.findall("item")):
                label = item.attrib.get("quantity", str(index))
                values.append((f"{name}[{label}]", "".join(item.itertext())))
    return values


def check_compose_resource_placeholders() -> tuple[list[str], list[str]]:
    """Every placeholder in every commonMain Compose catalog is `%N$d` or `%N$s` with N >= 1."""
    failures: list[str] = []
    walked: list[str] = []
    catalogs = [
        path
        for path in source_files("strings.xml")
        if path.parent.name.startswith("values")
        and path.parent.parent.name == "composeResources"
        and path.parent.parent.parent.name == "commonMain"
    ]
    if not catalogs:
        failures.append("compose-resource placeholders: no commonMain composeResources catalog was walked")
    for catalog in catalogs:
        found = [
            (key, token)
            for key, text in catalog_values(catalog)
            for token in placeholder_tokens(text)
        ]
        walked.append(
            f"{catalog.as_posix()}: {len(found)} placeholder(s)"
            + (": " + ", ".join(f"{key}={token}" for key, token in found) if found else "")
        )
        for key, token in found:
            if not POSITIONAL_PLACEHOLDER.fullmatch(token):
                failures.append(
                    "compose-resource placeholder is not %N$d or %N$s with N >= 1: "
                    f"{catalog.as_posix()} key {key} token {token!r}"
                )
    return failures, walked


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
    for entry in FEATURE_ENTRIES:
        failures.extend(check_feature_entry(entry))
    placeholder_failures, placeholder_walk = check_compose_resource_placeholders()
    failures.extend(placeholder_failures)

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
    for entry in FEATURE_ENTRIES:
        print(
            f"  {entry.short_name} catalogs, semantic State, suppressions, previews, "
            "test identities, and explicit factory flow are exact"
        )
    print(
        f"  app:common API edges and {len(EXPECTED_APP_DEPS_READERS)} remaining "
        "Context.appDeps readers are exact"
    )
    print(
        f"  compose-resource placeholders: {len(placeholder_walk)} commonMain catalogs walked, "
        "every placeholder is %N$d or %N$s with N >= 1"
    )
    for line in placeholder_walk:
        print(f"    {line}")


if __name__ == "__main__":
    sys.exit(main())
