@file:Suppress("UnsafeOptInUsageError")

package com.tidal.wear.core.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks

/**
 * The audio representation currently selected by ExoPlayer.
 *
 * Nullable fields are intentional: a manifest or renderer may not provide a value, and this
 * type must never turn an unknown value into a guessed quality label.
 */
data class DeliveredAudioFormat(
    val codec: String? = null,
    val container: String? = null,
    val bitrate: Int? = null,
    val sampleRateHz: Int? = null,
    val channelCount: Int? = null,
    val bitDepth: Int? = null,
)

/** Maps only fields that ExoPlayer explicitly reports on the selected audio format. */
fun Format.toDeliveredAudioFormat(): DeliveredAudioFormat = DeliveredAudioFormat(
    codec = codecs?.takeIf { it.isNotBlank() } ?: sampleMimeType?.takeIf { it.isNotBlank() },
    container = containerMimeType?.takeIf { it.isNotBlank() },
    bitrate = bitrate.takeIf { it > 0 },
    sampleRateHz = sampleRate.takeIf { it > 0 },
    channelCount = channelCount.takeIf { it > 0 },
    bitDepth = pcmEncoding.toBitDepthOrNull(),
)

/** Returns the selected audio track's format, if ExoPlayer has selected one. */
internal fun Tracks.selectedAudioFormat(): Format? = groups
    .asSequence()
    .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
    .flatMap { group ->
        (0 until group.length).asSequence()
            .filter(group::isTrackSelected)
            .map(group::getTrackFormat)
    }
    .firstOrNull()

/** Returns truthful details for the audio format currently selected by this player. */
fun Tracks.selectedDeliveredAudioFormat(): DeliveredAudioFormat? =
    selectedAudioFormat()?.toDeliveredAudioFormat()

private fun Int.toBitDepthOrNull(): Int? = when (this) {
    C.ENCODING_PCM_8BIT -> 8
    C.ENCODING_PCM_16BIT,
    C.ENCODING_PCM_16BIT_BIG_ENDIAN,
    -> 16
    C.ENCODING_PCM_24BIT,
    C.ENCODING_PCM_24BIT_BIG_ENDIAN,
    -> 24
    C.ENCODING_PCM_32BIT,
    C.ENCODING_PCM_32BIT_BIG_ENDIAN,
    C.ENCODING_PCM_FLOAT,
    -> 32
    else -> null
}
