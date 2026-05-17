// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.handler

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserAction
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserChoice
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogHandlerStore
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action
import io.github.stslex.workeeper.feature.app_dialogs.impl.observer.AppDialogObserverImpl
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * `ChooseHandler` is a thin pass-through: emit the choice to the observer
 * and do NOT dismiss. The dismiss responsibility lives on the consumer side
 * (per the BLOCKER 2 transient-signal contract). Tests pin both halves.
 */
internal class ChooseHandlerTest {

    private val observer = mockk<AppDialogObserverImpl>(relaxed = true)
    private val store = mockk<AppDialogHandlerStore>(relaxed = true).apply {
        every { logger } returns mockk<Logger>(relaxed = true)
        every { launchDefault<Unit>(any(), any(), any()) } answers {
            val action = thirdArg<suspend CoroutineScope.() -> Unit>()
            // Synchronously run the action body so the test can verify what
            // it does. Mirrors the pattern in
            // feature/past-session/.../InputHandlerTest.kt's TestStore.
            kotlinx.coroutines.runBlocking { action(this) }
            mockk<Job>(relaxed = true)
        }
    }
    private val handler = ChooseHandler(observer, store)

    @Test
    fun `Choose emits the choice to the observer`() = runTest {
        val dialog = AppDialog.RestoreFailure(reason = BackupErrorCode.Unknown)
        handler.invoke(Action.Choose(dialog = dialog, action = AppDialogUserAction.Report))

        coVerify(exactly = 1) {
            observer.emit(AppDialogUserChoice(dialog, AppDialogUserAction.Report))
        }
    }

    @Test
    fun `Choose does NOT dismiss the dialog — the consumer is responsible`() = runTest {
        val dialog = AppDialog.UndoRestoreConfirmation(originalDataDateEpochMs = 1_000L)
        handler.invoke(Action.Choose(dialog = dialog, action = AppDialogUserAction.ConfirmUndo))

        // No acknowledgement / dismiss happens here. The transient-signal
        // contract puts that on the @Singleton observer-side reactor in
        // app/app, which calls acknowledgeReaction(dialog) after its
        // side-effect succeeds. ChooseHandler stays handle-only.
        coVerify(exactly = 0) { observer.acknowledgeReaction(any()) }
    }
}
