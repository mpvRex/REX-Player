package xyz.mpv.rex.domain.ytdl

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import xyz.mpv.rex.addon.ytdl.ipc.IYtDlCallback
import xyz.mpv.rex.addon.ytdl.ipc.IYtDlService
import xyz.mpv.rex.domain.ytdl.ipc.YtdlIpcConverter
import xyz.mpv.rex.domain.ytdl.model.ResolvedPlaylist
import xyz.mpv.rex.domain.ytdl.model.ResolvedStream
import xyz.mpv.rex.domain.ytdl.model.StreamExtractionOptions
import xyz.mpv.rex.domain.ytdl.model.YtdlpStatus
import xyz.mpv.rex.preferences.YtdlPreferences
import xyz.mpv.rex.utils.media.HttpUtils

class YtDlClient(
    private val context: Context,
    private val preferences: YtdlPreferences? = null,
) {
    private val mutex = Mutex()
    private var boundService: IYtDlService? = null
    private var serviceConnection: ServiceConnection? = null

    fun isAddonInstalled(): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    ADDON_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(ADDON_PACKAGE, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Cryptographically verifies that the installed add-on package is signed by
     * the exact same developer certificate as REX Player.
     */
    fun isAddonAuthentic(): Boolean {
        if (!isAddonInstalled()) return false
        val match = context.packageManager.checkSignatures(context.packageName, ADDON_PACKAGE)
        if (match == PackageManager.SIGNATURE_MATCH) {
            return true
        }
        Log.e(TAG, "Add-on signature verification failed (result: $match). Refusing to trust $ADDON_PACKAGE.")
        return false
    }

    fun isKnownWebPlatform(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false

        // 1. Built-in known web platforms
        if (KNOWN_WEB_PLATFORMS.any { platform ->
            host == platform || host.endsWith(".$platform")
        }) {
            return true
        }

        // 2. User-configured custom domains from settings
        val custom = preferences?.getParsedCustomDomains().orEmpty()
        if (custom.any { domain ->
            host == domain || host.endsWith(".$domain")
        }) {
            return true
        }

        return false
    }

    /**
     * Checks if a URL is a web streaming URL (e.g. YouTube, Vimeo, Twitch)
     * that requires the yt-dlp extractor add-on.
     *
     * Direct media files (.mp4, .mkv, .m3u8, etc.), local network streams,
     * and generic streaming URLs (IPTV, Icecast, raw HTTP streams) do NOT require yt-dlp
     * and can be played directly by MPV.
     */
    fun requiresYtdl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false

        // Loopback / local proxies are never processed by yt-dlp
        val host = uri.host?.lowercase() ?: ""
        if (host == "localhost" || host == "127.0.0.1" || host.startsWith("192.168.") || host.startsWith("10.")) {
            return false
        }

        // Direct media extensions (.mp4, .mkv, .m3u8, etc.) can be played directly by MPV
        if (HttpUtils.isDirectMediaUrl(uri)) {
            return false
        }

        // Only known web video platforms require yt-dlp extraction
        return isKnownWebPlatform(url)
    }

    private suspend fun getService(): IYtDlService? = mutex.withLock {
        boundService?.let { return it }

        if (!isAddonInstalled()) {
            Log.w(TAG, "Addon $ADDON_PACKAGE is not installed")
            return null
        }

        if (!isAddonAuthentic()) {
            Log.e(TAG, "Refusing to connect: $ADDON_PACKAGE is not authentic (signature mismatch)")
            return null
        }

        val deferred = CompletableDeferred<IYtDlService?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Log.d(TAG, "Connected to YtDlRemoteService")
                val aidl = IYtDlService.Stub.asInterface(service)
                boundService = aidl
                deferred.complete(aidl)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.d(TAG, "Disconnected from YtDlRemoteService")
                boundService = null
            }
        }

        val intent = Intent(ACTION_BIND).apply {
            setPackage(ADDON_PACKAGE)
        }

        val bound = try {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind to $ADDON_PACKAGE", e)
            false
        }

        if (!bound) {
            return null
        }

        serviceConnection = connection
        return withTimeoutOrNull(BIND_TIMEOUT_MS) { deferred.await() }
    }

    suspend fun isEngineReady(): Boolean = withContext(Dispatchers.IO) {
        val service = getService() ?: return@withContext false
        try {
            service.isReady
        } catch (e: Exception) {
            Log.e(TAG, "Error checking isReady", e)
            false
        }
    }

    suspend fun getStatus(): YtdlpStatus = withContext(Dispatchers.IO) {
        if (!isAddonInstalled()) return@withContext YtdlpStatus.NotInstalled
        val service = getService() ?: return@withContext YtdlpStatus.NotInstalled
        try {
            val bundle = service.status
            YtdlIpcConverter.parseStatus(bundle)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting status", e)
            YtdlpStatus.NotInstalled
        }
    }

    suspend fun resolveStream(
        url: String,
        options: StreamExtractionOptions = StreamExtractionOptions(),
    ): ResolvedStream = withContext(Dispatchers.IO) {
        if (!isAddonInstalled()) {
            return@withContext ResolvedStream.failure("REX Ytdlp is not installed")
        }
        val service = getService()
            ?: return@withContext ResolvedStream.failure("Could not connect to REX Ytdlp")

        // Interface v2 adds audio_tracks_json. Older addons omit it; the parser defaults
        // audioTracks to emptyList(), so this is a diagnostic log, not a hard gate.
        val addonVersion = runCatching { service.addonVersion }.getOrDefault(0)
        if (addonVersion < 2) {
            Log.d(TAG, "Add-on interface v$addonVersion: alternate audio tracks unavailable")
        }

        try {
            val optionsBundle = YtdlIpcConverter.toOptionsBundle(options)
            val resultBundle = service.resolveStream(url, optionsBundle)
            YtdlIpcConverter.parseResolvedStream(resultBundle)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving stream for $url", e)
            ResolvedStream.failure("IPC resolution error: ${e.message}")
        }
    }

    suspend fun extractPlaylist(
        url: String,
        options: StreamExtractionOptions = StreamExtractionOptions(),
    ): ResolvedPlaylist = withContext(Dispatchers.IO) {
        if (!isAddonInstalled()) {
            return@withContext ResolvedPlaylist.failure(url, "REX Ytdlp is not installed")
        }
        val service = getService()
            ?: return@withContext ResolvedPlaylist.failure(url, "Could not connect to REX Ytdlp")

        try {
            val optionsBundle = YtdlIpcConverter.toOptionsBundle(options)
            val resultBundle = service.extractPlaylist(url, optionsBundle)
            YtdlIpcConverter.parseResolvedPlaylist(resultBundle)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting playlist for $url", e)
            ResolvedPlaylist.failure(url, "IPC playlist extraction error: ${e.message}")
        }
    }

    suspend fun runInstall(onLog: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val service = getService() ?: return@withContext false
        val deferred = CompletableDeferred<Boolean>()

        val callback = object : IYtDlCallback.Stub() {
            override fun onLog(message: String?) {
                message?.let(onLog)
            }

            override fun onComplete(success: Boolean, message: String?) {
                message?.let(onLog)
                deferred.complete(success)
            }
        }

        try {
            service.runInstall(callback)
            deferred.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error running install", e)
            false
        }
    }

    suspend fun runUpdate(nightly: Boolean, onLog: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val service = getService() ?: return@withContext false
        val deferred = CompletableDeferred<Boolean>()

        val callback = object : IYtDlCallback.Stub() {
            override fun onLog(message: String?) {
                message?.let(onLog)
            }

            override fun onComplete(success: Boolean, message: String?) {
                message?.let(onLog)
                deferred.complete(success)
            }
        }

        try {
            service.runUpdate(nightly, callback)
            deferred.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error running update", e)
            false
        }
    }

    fun unbind() {
        mutex.tryLock()
        try {
            serviceConnection?.let {
                runCatching { context.unbindService(it) }
            }
            serviceConnection = null
            boundService = null
        } finally {
            runCatching { mutex.unlock() }
        }
    }

    companion object {
        private const val TAG = "YtDlClient"
        const val ADDON_PACKAGE = "xyz.mpv.rex.addon.ytdl"
        const val ACTION_BIND = "xyz.mpv.rex.addon.ytdl.BIND_SERVICE"
        private const val BIND_TIMEOUT_MS = 10_000L

        val KNOWN_WEB_PLATFORMS = setOf(
            "youtube.com",
            "youtu.be",
            "yt.be",
            "youtube-nocookie.com",
            "vimeo.com",
            "twitch.tv",
            "dailymotion.com",
            "dai.ly",
            "tiktok.com",
            "twitter.com",
            "x.com",
            "facebook.com",
            "fb.watch",
            "instagram.com",
            "bilibili.com",
            "b23.tv",
            "soundcloud.com",
            "odysee.com",
            "rumble.com",
            "streamable.com",
            "reddit.com",
            "v.redd.it",
            "nicovideo.jp",
            "kick.com",
        )
    }
}
