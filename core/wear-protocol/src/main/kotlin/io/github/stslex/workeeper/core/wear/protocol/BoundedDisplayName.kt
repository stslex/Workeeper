// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.github.stslex.workeeper.core.wear.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

@Serializable
@JsonClassDiscriminator("kind")
sealed interface BoundedDisplayName {

    @Serializable
    @SerialName("value")
    data class Value(val value: String) : BoundedDisplayName {

        init {
            require(strictUtf8OrNull(value)?.size?.let { it <= WearProtocol.MAX_DISPLAY_NAME_UTF8_BYTES } == true) {
                "Display name must be valid Unicode and fit the protocol byte limit"
            }
        }
    }

    @Serializable
    @SerialName("omitted")
    data class Omitted(val reason: OmissionReason) : BoundedDisplayName

    companion object {

        fun from(raw: String): BoundedDisplayName {
            val encoded = strictUtf8OrNull(raw)
                ?: return Omitted(OmissionReason.INVALID_UNICODE)
            return if (encoded.size <= WearProtocol.MAX_DISPLAY_NAME_UTF8_BYTES) {
                Value(raw)
            } else {
                Omitted(OmissionReason.TOO_LARGE)
            }
        }
    }
}

@Serializable
enum class OmissionReason {
    @SerialName("too_large")
    TOO_LARGE,

    @SerialName("invalid_unicode")
    INVALID_UNICODE,
}

internal fun strictUtf8OrNull(value: String): ByteArray? = runCatching {
    val encoded: ByteBuffer = StandardCharsets.UTF_8
        .newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .encode(java.nio.CharBuffer.wrap(value))
    ByteArray(encoded.remaining()).also(encoded::get)
}.getOrNull()
