// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.wear.protocol

/** Stable Phase-1 wire limits shared only by the Android phone and Wear artifacts. */
object WearProtocol {

    const val SCHEMA_VERSION: Int = 1
    const val MAX_DISPLAY_NAME_UTF8_BYTES: Int = 512
    const val MAX_ENVELOPE_BYTES: Int = 16_384
    const val MAX_WEAR_REPS: Int = 999
    const val MAX_WEAR_WEIGHT_HUNDREDTHS_KG: Int = 99_999
}
