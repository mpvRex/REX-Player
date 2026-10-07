package xyz.mpv.rex.domain.ytdl.model

data class YtdlpStatus(
    val isInstalled: Boolean,
    val version: String? = null,
    val channel: String? = null,
    val commitHash: String? = null,
    val origin: String? = null,
    val variant: String? = null,
) {
    val shortCommitHash: String?
        get() = commitHash?.take(8)

    companion object {
        val NotInstalled = YtdlpStatus(isInstalled = false)
    }
}

data class VideoQuality(
    val id: String,
    val label: String,
    val height: Int = 0,
    val width: Int = 0,
    val fps: Int = 0,
    val codec: String? = null,
    val bitrate: Long = 0L,
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val isDASH: Boolean = false,
    val isAudioOnly: Boolean = false,
) {
    val shortLabel: String
        get() = when {
            height > 0 -> "${height}p"
            label.contains("p", ignoreCase = true) -> {
                val pIndex = label.indexOfAny(charArrayOf('p', 'P'))
                if (pIndex != -1) label.substring(0, pIndex + 1) else label
            }
            else -> label
        }
}

data class AudioTrack(
    val url: String,
    val lang: String? = null,
    val codec: String? = null,
    val bitrate: Long = 0L,
    val label: String? = null,
)

data class ResolvedStream(
    val isSuccess: Boolean,
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val audioTracks: List<AudioTrack> = emptyList(),
    val title: String? = null,
    val durationSeconds: Int = 0,
    val thumbnailUrl: String? = null,
    val uploader: String? = null,
    val httpHeaders: Map<String, String> = emptyMap(),
    val subtitles: Map<String, String> = emptyMap(),
    val availableQualities: List<VideoQuality> = emptyList(),
    val errorMessage: String? = null,
) {
    val isDASH: Boolean
        get() = !videoUrl.isNullOrBlank() && !audioUrl.isNullOrBlank()

    companion object {
        fun failure(message: String): ResolvedStream =
            ResolvedStream(
                isSuccess = false,
                errorMessage = message,
            )
    }
}

data class ResolvedPlaylistEntry(
    val id: String?,
    val url: String,
    val title: String,
    val artist: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
)

data class ResolvedPlaylist(
    val isSuccess: Boolean,
    val sourceUrl: String,
    val title: String? = null,
    val entries: List<ResolvedPlaylistEntry> = emptyList(),
    val errorMessage: String? = null,
) {
    companion object {
        fun failure(sourceUrl: String, message: String): ResolvedPlaylist =
            ResolvedPlaylist(
                isSuccess = false,
                sourceUrl = sourceUrl,
                errorMessage = message,
            )
    }
}

data class StreamExtractionOptions(
    val format: String? = null,
    val userAgent: String? = null,
    val referer: String? = null,
    val proxy: String? = null,
    val cookiesFilePath: String? = null,
    val extractorArgs: String? = null,
    val geoBypass: Boolean = true,
)
