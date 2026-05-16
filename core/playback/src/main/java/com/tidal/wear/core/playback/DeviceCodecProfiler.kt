package com.tidal.wear.core.playback

import android.media.MediaCodecInfo
import android.media.MediaCodecList

data class DecoderCapability(
    val name: String,
    val mimeType: String,
    val hardwareAccelerated: Boolean,
    val softwareOnly: Boolean,
)

interface CodecProfiler {
    fun audioDecoders(): List<DecoderCapability>
}

class DeviceCodecProfiler : CodecProfiler {
    override fun audioDecoders(): List<DecoderCapability> {
        return MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .codecInfos
            .asSequence()
            .filterNot(MediaCodecInfo::isEncoder)
            .flatMap { codec ->
                codec.supportedTypes
                    .asSequence()
                    .filter { it.startsWith("audio/") }
                    .map { mimeType ->
                        DecoderCapability(
                            name = codec.name,
                            mimeType = mimeType,
                            hardwareAccelerated = codec.isHardwareAccelerated,
                            softwareOnly = codec.isSoftwareOnly,
                        )
                    }
            }
            .toList()
    }
}

class DebugCodecProfiler : CodecProfiler {
    override fun audioDecoders(): List<DecoderCapability> = listOf(
        DecoderCapability(
            name = "debug.aac.decoder",
            mimeType = "audio/mp4a-latm",
            hardwareAccelerated = true,
            softwareOnly = false,
        ),
        DecoderCapability(
            name = "debug.mp3.decoder",
            mimeType = "audio/mpeg",
            hardwareAccelerated = true,
            softwareOnly = false,
        ),
    )
}
