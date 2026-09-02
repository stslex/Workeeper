// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.annotation.StringRes
import io.github.stslex.workeeper.wear.R

internal enum class WearCopy(@StringRes val resource: Int) {
    READY(R.string.ready),
    LOADING(R.string.loading),
    START_ON_PHONE(R.string.start_workout_on_phone),
    PHONE_UNAVAILABLE(R.string.phone_unavailable),
    REFRESH_REQUIRED(R.string.refresh_required),
    TRANSPORT_ERROR(R.string.transport_error),
    UPDATE_REQUIRED(R.string.update_required),
    ADD_SET_ON_PHONE(R.string.add_set_on_phone),
    EDIT_SET_ON_PHONE(R.string.edit_set_on_phone),
    OPEN_WORKOUT_ON_PHONE(R.string.open_workout_on_phone),
    WORKOUT_COMPLETE(R.string.workout_complete),
    FINISH_ON_PHONE(R.string.finish_on_phone),
}

internal fun WearSurfaceModel.statusCopy(): WearCopy = when (kind) {
    WearSurfaceKind.LOADING -> WearCopy.LOADING
    WearSurfaceKind.NO_SESSION -> WearCopy.START_ON_PHONE
    WearSurfaceKind.ACTIVE -> WearCopy.READY
    WearSurfaceKind.PHONE_ACTION_NO_SETS -> WearCopy.ADD_SET_ON_PHONE
    WearSurfaceKind.PHONE_ACTION_UNSUPPORTED -> WearCopy.EDIT_SET_ON_PHONE
    WearSurfaceKind.PAYLOAD_TOO_LARGE -> WearCopy.OPEN_WORKOUT_ON_PHONE
    WearSurfaceKind.WORKOUT_COMPLETE -> WearCopy.WORKOUT_COMPLETE
    WearSurfaceKind.REFRESH_REQUIRED -> WearCopy.REFRESH_REQUIRED
    WearSurfaceKind.DISCONNECTED -> WearCopy.PHONE_UNAVAILABLE
    WearSurfaceKind.RETRYABLE_ERROR -> WearCopy.TRANSPORT_ERROR
    WearSurfaceKind.PROTOCOL_MISMATCH -> WearCopy.UPDATE_REQUIRED
}
