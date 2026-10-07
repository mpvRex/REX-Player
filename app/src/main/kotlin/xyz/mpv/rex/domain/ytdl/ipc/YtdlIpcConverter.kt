package xyz.mpv.rex.domain.ytdl.ipc

import android.os.Bundle
import org.json.JSONArray
import xyz.mpv.rex.domain.ytdl.model.ResolvedPlaylist
import xyz.mpv.rex.domain.ytdl.model.ResolvedPlaylistEntry
import xyz.mpv.rex.domain.ytdl.model.AudioTrack
import xyz.mpv.rex.domain.ytdl.model.ResolvedStream
import xyz.mpv.rex.domain.ytdl.model.StreamExtractionOptions
import xyz.mpv.rex.domain.ytdl.model.VideoQuality
import xyz.mpv.rex.domain.ytdl.model.YtdlpStatus

object YtdlIpcConverter {
    // Status Keys
    private const val KEY_IS_INSTALLED = "is_installed"
    private const val KEY_VERSION = "version"
    private const val KEY_CHANNEL = "channel"
    private const val KEY_COMMIT_HASH = "commit_hash"
    private const val KEY_ORIGIN = "origin"
    private const val KEY_VARIANT = "variant"

    // Options Keys
    private const val KEY_OPT_FORMAT = "opt_format"
    private const val KEY_OPT_USER_AGENT = "opt_user_agent"
    private const val KEY_OPT_REFERER = "opt_referer"
    private const val KEY_OPT_PROXY = "opt_proxy"
    private const val KEY_OPT_COOKIES_PATH = "opt_cookies_path"
    private const val KEY_OPT_EXTRACTOR_ARGS = "opt_extractor_args"
    private const val KEY_OPT_GEO_BYPASS = "opt_geo_bypass"

    // Stream Result Keys
    private const val KEY_IS_SUCCESS = "is_success"
    private const val KEY_VIDEO_URL = "video_url"
    private const val KEY_AUDIO_URL = "audio_url"
    private const val KEY_TITLE = "title"
    private const val KEY_DURATION = "duration"
    private const val KEY_THUMBNAIL = "thumbnail"
    private const val KEY_UPLOADER = "uploader"
    private const val KEY_HTTP_HEADERS = "http_headers"
    private const val KEY_SUBTITLES = "subtitles"
    private const val KEY_VIDEO_QUALITIES_JSON = "video_qualities_json"
    private const val KEY_AUDIO_TRACKS_JSON = "audio_tracks_json"
    private const val KEY_ERROR_MESSAGE = "error_message"

    // Playlist Result Keys
    private const val KEY_SOURCE_URL = "source_url"
    private const val KEY_ENTRIES_JSON = "entries_json"

    fun toOptionsBundle(options: StreamExtractionOptions): Bundle = Bundle().apply {
        options.format?.let { putString(KEY_OPT_FORMAT, it) }
        options.userAgent?.let { putString(KEY_OPT_USER_AGENT, it) }
        options.referer?.let { putString(KEY_OPT_REFERER, it) }
        options.proxy?.let { putString(KEY_OPT_PROXY, it) }
        options.cookiesFilePath?.let { putString(KEY_OPT_COOKIES_PATH, it) }
        options.extractorArgs?.let { putString(KEY_OPT_EXTRACTOR_ARGS, it) }
        putBoolean(KEY_OPT_GEO_BYPASS, options.geoBypass)
    }

    fun parseStatus(bundle: Bundle?): YtdlpStatus {
        if (bundle == null) return YtdlpStatus.NotInstalled
        return YtdlpStatus(
            isInstalled = bundle.getBoolean(KEY_IS_INSTALLED, false),
            version = bundle.getString(KEY_VERSION),
            channel = bundle.getString(KEY_CHANNEL),
            commitHash = bundle.getString(KEY_COMMIT_HASH),
            origin = bundle.getString(KEY_ORIGIN),
            variant = bundle.getString(KEY_VARIANT),
        )
    }

    fun parseResolvedStream(bundle: Bundle?): ResolvedStream {
        if (bundle == null) return ResolvedStream.failure("No response from add-on service")

        val headersMap = mutableMapOf<String, String>()
        bundle.getBundle(KEY_HTTP_HEADERS)?.let { hBundle ->
            for (key in hBundle.keySet()) {
                hBundle.getString(key)?.let { headersMap[key] = it }
            }
        }

        val subsMap = mutableMapOf<String, String>()
        bundle.getBundle(KEY_SUBTITLES)?.let { sBundle ->
            for (key in sBundle.keySet()) {
                sBundle.getString(key)?.let { subsMap[key] = it }
            }
        }

        val qualities = mutableListOf<VideoQuality>()
        val qualitiesJsonStr = bundle.getString(KEY_VIDEO_QUALITIES_JSON)
        if (!qualitiesJsonStr.isNullOrBlank()) {
            runCatching {
                val array = JSONArray(qualitiesJsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    qualities.add(
                        VideoQuality(
                            id = obj.getString("id"),
                            label = obj.getString("label"),
                            height = obj.optInt("height", 0),
                            width = obj.optInt("width", 0),
                            fps = obj.optInt("fps", 0),
                            codec = obj.optString("codec").takeIf { it.isNotBlank() && it != "null" },
                            bitrate = obj.optLong("bitrate", 0L),
                            videoUrl = obj.optString("video_url").takeIf { it.isNotBlank() && it != "null" },
                            audioUrl = obj.optString("audio_url").takeIf { it.isNotBlank() && it != "null" },
                            isDASH = obj.optBoolean("is_dash", false),
                            isAudioOnly = obj.optBoolean("is_audio_only", false),
                        )
                    )
                }
            }
        }

        val audioTracks = mutableListOf<AudioTrack>()
        val audioTracksJson = bundle.getString(KEY_AUDIO_TRACKS_JSON)
        if (!audioTracksJson.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(audioTracksJson)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    audioTracks.add(
                        AudioTrack(
                            url = o.getString("url"),
                            lang = o.optString("lang").takeIf { it.isNotBlank() && it != "null" },
                            codec = o.optString("codec").takeIf { it.isNotBlank() && it != "null" },
                            bitrate = o.optLong("bitrate", 0L),
                            label = o.optString("label").takeIf { it.isNotBlank() && it != "null" },
                        )
                    )
                }
            }
        }

        android.util.Log.i("YtdlIpc", "parseResolvedStream: audioTracks=${audioTracks.size}")

        return ResolvedStream(
            isSuccess = bundle.getBoolean(KEY_IS_SUCCESS, false),
            videoUrl = bundle.getString(KEY_VIDEO_URL),
            audioUrl = bundle.getString(KEY_AUDIO_URL),
            audioTracks = audioTracks,
            title = bundle.getString(KEY_TITLE),
            durationSeconds = bundle.getInt(KEY_DURATION, 0),
            thumbnailUrl = bundle.getString(KEY_THUMBNAIL),
            uploader = bundle.getString(KEY_UPLOADER),
            httpHeaders = headersMap,
            subtitles = subsMap,
            availableQualities = qualities,
            errorMessage = bundle.getString(KEY_ERROR_MESSAGE),
        )
    }

    fun parseResolvedPlaylist(bundle: Bundle?): ResolvedPlaylist {
        if (bundle == null) return ResolvedPlaylist.failure("", "No response from add-on service")

        val sourceUrl = bundle.getString(KEY_SOURCE_URL, "")
        val isSuccess = bundle.getBoolean(KEY_IS_SUCCESS, false)
        val title = bundle.getString(KEY_TITLE)
        val errorMsg = bundle.getString(KEY_ERROR_MESSAGE)

        val entries = mutableListOf<ResolvedPlaylistEntry>()
        val jsonStr = bundle.getString(KEY_ENTRIES_JSON)
        if (!jsonStr.isNullOrBlank()) {
            runCatching {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    entries.add(
                        ResolvedPlaylistEntry(
                            id = obj.optString("id").takeIf(String::isNotBlank),
                            url = obj.getString("url"),
                            title = obj.getString("title"),
                            artist = obj.optString("artist").takeIf(String::isNotBlank),
                            thumbnailUrl = obj.optString("thumbnail").takeIf(String::isNotBlank),
                            durationSeconds = obj.optInt("duration", 0),
                        )
                    )
                }
            }
        }

        return ResolvedPlaylist(
            isSuccess = isSuccess,
            sourceUrl = sourceUrl,
            title = title,
            entries = entries,
            errorMessage = errorMsg,
        )
    }
}
