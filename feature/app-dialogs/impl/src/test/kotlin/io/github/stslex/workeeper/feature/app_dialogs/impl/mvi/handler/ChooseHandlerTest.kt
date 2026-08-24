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

/** Pins that `ChooseHandler` emits the choice and does NOT dismiss the dialog. */
internal class ChooseHandlerTest {

    private val observer = mockk<AppDialogObserverImpl>(relaxed = true)
    private val store = mockk<AppDialogHandlerStore>(relaxed = true).apply {
        every { logger } returns mockk<Logger>(relaxed = true)
        every { launchDefault<Unit>(any(), any(), any()) } answers {
            val action = thirdArg<suspend CoroutineScope.() -> Unit>()
            // Run the action body synchronously so the test can verify what it does.
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

        coVerify(exactly = 0) { observer.acknowledgeReaction(any()) }
    }
}
