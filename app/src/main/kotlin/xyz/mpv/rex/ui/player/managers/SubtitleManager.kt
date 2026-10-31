package xyz.mpv.rex.ui.player.managers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import xyz.mpv.rex.R
import xyz.mpv.rex.repository.wyzie.WyzieErrorKind
import xyz.mpv.rex.repository.wyzie.WyzieException
import xyz.mpv.rex.repository.wyzie.WyzieSearchRepository
import xyz.mpv.rex.repository.wyzie.WyzieSubtitle
import xyz.mpv.rex.repository.wyzie.WyzieTmdbResult
import xyz.mpv.rex.repository.wyzie.WyzieTvShowDetails
import xyz.mpv.rex.repository.wyzie.WyzieSeason
import xyz.mpv.rex.repository.wyzie.WyzieEpisode
import `is`.xyz.mpv.MPVLib
import xyz.mpv.rex.ui.player.resolveUri
import xyz.mpv.rex.ui.player.TrackNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mpv.rex.preferences.SubtitlesPreferences
import xyz.mpv.rex.utils.media.SubtitleEncodingUtils
import java.io.File

/**
 * Manages subtitle state and operations, including local scanning and online search.
 */
class SubtitleManager(
    private val context: Context,
    private val wyzieRepository: WyzieSearchRepository,
    private val subtitlesPreferences: SubtitlesPreferences,
    private val scope: CoroutineScope,
    private val onShowToast: (String) -> Unit
) {
    companion object {
        private const val TAG = "SubtitleManager"
    }

    // ==================== State ====================

    private val _wyzieSearchResults = MutableStateFlow<List<WyzieSubtitle>>(emptyList())
    val wyzieSearchResults = _wyzieSearchResults.asStateFlow()

    private val _mediaSearchResults = MutableStateFlow<List<WyzieTmdbResult>>(emptyList())
    val mediaSearchResults = _mediaSearchResults.asStateFlow()

    private val _selectedTvShow = MutableStateFlow<WyzieTvShowDetails?>(null)
    val selectedTvShow = _selectedTvShow.asStateFlow()

    private val _selectedSeason = MutableStateFlow<WyzieSeason?>(null)
    val selectedSeason = _selectedSeason.asStateFlow()

    private val _seasonEpisodes = MutableStateFlow<List<WyzieEpisode>>(emptyList())
    val seasonEpisodes = _seasonEpisodes.asStateFlow()

    private val _selectedEpisode = MutableStateFlow<WyzieEpisode?>(null)
    val selectedEpisode = _selectedEpisode.asStateFlow()

    private val _isDownloadingSub = MutableStateFlow(false)
    val isDownloadingSub = _isDownloadingSub.asStateFlow()

    private val _isSearchingSub = MutableStateFlow(false)
    val isSearchingSub = _isSearchingSub.asStateFlow()

    private val _isSearchingMedia = MutableStateFlow(false)
    val isSearchingMedia = _isSearchingMedia.asStateFlow()

    private val _isFetchingTvDetails = MutableStateFlow(false)
    val isFetchingTvDetails = _isFetchingTvDetails.asStateFlow()

    private val _isFetchingEpisodes = MutableStateFlow(false)
    val isFetchingEpisodes = _isFetchingEpisodes.asStateFlow()

    private val _isOnlineSectionExpanded = MutableStateFlow(true)
    val isOnlineSectionExpanded = _isOnlineSectionExpanded.asStateFlow()

    /**
     * Why the last online operation returned nothing, or null when it succeeded. The sheet shows
     * this instead of an empty list; previously the reason (missing key, rate limit, network)
     * was only visible in Logcat.
     */
    private val _searchError = MutableStateFlow<WyzieErrorKind?>(null)
    val searchError = _searchError.asStateFlow()

    /** True once a subtitle search completed with zero results (as opposed to no search yet). */
    private val _hasSearched = MutableStateFlow(false)
    val hasSearched = _hasSearched.asStateFlow()

    // Superseded searches are cancelled so a slow response for an old query cannot overwrite
    // the results of a newer one.
    private var searchMediaJob: Job? = null
    private var searchSubtitlesJob: Job? = null
    private var tvDetailsJob: Job? = null
    private var episodesJob: Job? = null

    private val _externalSubtitles = mutableListOf<String>()
    val externalSubtitles: List<String> get() = _externalSubtitles.toList()

    private val mpvPathToUriMap = mutableMapOf<String, String>()
    private val uriStringToUriMap = mutableMapOf<String, Uri>()
    private val uriStringToMpvPathMap = mutableMapOf<String, String>()

    // ==================== Actions ====================

    fun toggleOnlineSection() {
        _isOnlineSectionExpanded.value = !_isOnlineSectionExpanded.value
    }

    fun addSubtitle(uri: Uri, select: Boolean = true, silent: Boolean = false) {
        val uriString = uri.toString()
        if (_externalSubtitles.contains(uriString)) {
            Log.d(TAG, "Subtitle already tracked, skipping: $uriString")
            return
        }

        val fileName = uri.lastPathSegment ?: "subtitle"
        if (!isValidSubtitleFile(fileName)) {
            if (!silent) onShowToast("Invalid subtitle file")
            return
        }

        // Take persistent URI permission for content:// URIs
        if (uri.scheme == "content") {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.i(TAG, "Persistent permission not taken for $uri")
            }
        }

        scope.launch(Dispatchers.IO) {
            runCatching {
                val preferredEncoding = subtitlesPreferences.subtitleEncoding.get()
                val (normalizedPath, _) = SubtitleEncodingUtils.normalizeSubtitleUri(
                    context,
                    uri,
                    preferredEncoding
                )
                val mpvPath = if (normalizedPath.startsWith("content://") || normalizedPath.startsWith("file://")) {
                    uri.resolveUri(context) ?: uri.toString()
                } else {
                    normalizedPath
                }
                val mode = if (select) "select" else "auto"
                
                // Store mapping for reliable physical deletion and re-encoding later
                mpvPathToUriMap[mpvPath] = uriString
                uriStringToUriMap[uriString] = uri
                uriStringToMpvPathMap[uriString] = mpvPath
                
                MPVLib.command("sub-add", mpvPath, mode)

                if (!_externalSubtitles.contains(uriString)) {
                    _externalSubtitles.add(uriString)
                }

                if (!silent) {
                    withContext(Dispatchers.Main) {
                        // ASCII-only on purpose: the old toast embedded the file name, which
                        // was unreadable in some locales and not translatable anyway.
                        onShowToast("Subtitle added")
                    }
                }
            }.onFailure { e ->
                Log.e(TAG, "Failed to add subtitle", e)
                if (!silent) {
                    withContext(Dispatchers.Main) {
                        onShowToast("Failed to load subtitle")
                    }
                }
            }
        }
    }

    fun reloadExternalSubtitlesWithEncoding(newEncoding: String) {
        SubtitleEncodingUtils.applyEncodingChange(context, newEncoding)
    }

    fun removeSubtitle(id: Int, tracks: List<TrackNode>) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val trackToRemove = tracks.firstOrNull { it.id == id }
                
                if (trackToRemove?.external == true && trackToRemove.externalFilename != null) {
                    val mpvPath = trackToRemove.externalFilename
                    val originalUriString = mpvPathToUriMap[mpvPath] ?: mpvPath
                    _externalSubtitles.remove(originalUriString)
                    mpvPathToUriMap.remove(mpvPath)
                    uriStringToUriMap.remove(originalUriString)
                    uriStringToMpvPathMap.remove(originalUriString)

                    if (mpvPath.contains("converted_subtitles")) {
                        File(mpvPath).delete()
                    }
                }
                
                MPVLib.command("sub-remove", id.toString())
                withContext(Dispatchers.Main) {
                    onShowToast("Subtitle removed")
                }
            }.onFailure {
                Log.e(TAG, "Failed to remove subtitle", it)
            }
        }
    }

    fun clearExternalSubtitles() {
        _externalSubtitles.clear()
        mpvPathToUriMap.clear()
        uriStringToUriMap.clear()
        uriStringToMpvPathMap.clear()
        SubtitleEncodingUtils.cleanupConvertedSubtitles(context)
    }

    // ==================== Online Search ====================

    fun hasApiKey(): Boolean = subtitlesPreferences.wyzieApiKey.get().isNotBlank()

    /** Clears a remembered failure, e.g. after the user saved a key or typed a new query. */
    fun clearSearchError() {
        _searchError.value = null
    }

    fun searchMedia(query: String) {
        runMediaLookup(query)
    }

    /**
     * Looks up the TMDb entry and only reports the result. Does NOT start a subtitle search so
     * the sheet can wait for the user to pick a suggestion (pressing search used to fire both at
     * once, which searched series titles as movies).
     */
    fun findMedia(query: String) {
        runMediaLookup(query)
    }

    private fun runMediaLookup(query: String) {
        searchMediaJob?.cancel()
        if (query.isBlank()) {
            _mediaSearchResults.value = emptyList()
            return
        }
        searchMediaJob = scope.launch {
            _isSearchingMedia.value = true
            wyzieRepository.searchMedia(query)
                .onSuccess { results ->
                    _mediaSearchResults.value = results
                }
                .onFailure { error ->
                    if (error is CancellationException) return@launch
                    Log.e(TAG, "Media search failed", error)
                    _mediaSearchResults.value = emptyList()
                    reportError(error)
                }
            _isSearchingMedia.value = false
        }
    }

    fun selectMedia(result: WyzieTmdbResult) {
        _wyzieSearchResults.value = emptyList()
        tvDetailsJob?.cancel()
        if (result.mediaType == "movie") {
            searchSubtitles(result.title, year = result.releaseYear)
            _selectedTvShow.value = null
            _selectedSeason.value = null
            _selectedEpisode.value = null
            _seasonEpisodes.value = emptyList()
        } else {
            fetchTvShowDetails(result.id)
        }
    }

    private fun fetchTvShowDetails(id: Int) {
        tvDetailsJob?.cancel()
        tvDetailsJob = scope.launch {
            _isFetchingTvDetails.value = true
            wyzieRepository.getTvShowDetails(id)
                .onSuccess { details ->
                    val validSeasons = details.seasons.filter { it.season_number > 0 }.sortedBy { it.season_number }
                    _selectedTvShow.value = details.copy(seasons = validSeasons)
                    _selectedSeason.value = null
                    _seasonEpisodes.value = emptyList()
                    // Auto-select the first season so the episode list is reachable, and search
                    // the show without an episode (the backend returns the show's subtitles).
                    validSeasons.firstOrNull()?.let { selectSeason(it) }
                        ?: searchSubtitles(details.name)
                }
                .onFailure { error ->
                    if (error is CancellationException) return@launch
                    Log.e(TAG, "Failed to fetch TV details", error)
                    reportError(error)
                }
            _isFetchingTvDetails.value = false
        }
    }

    fun selectSeason(season: WyzieSeason) {
        val tvShowId = _selectedTvShow.value?.id ?: return
        _selectedSeason.value = season
        episodesJob?.cancel()
        episodesJob = scope.launch {
            _isFetchingEpisodes.value = true
            wyzieRepository.getSeasonEpisodes(tvShowId, season.season_number)
                .onSuccess { episodes ->
                    val validEpisodes = episodes.filter { it.episode_number > 0 }.sortedBy { it.episode_number }
                    _seasonEpisodes.value = validEpisodes
                    _selectedEpisode.value = null
                }
                .onFailure { error ->
                    if (error is CancellationException) return@launch
                    Log.e(TAG, "Failed to fetch episodes", error)
                    reportError(error)
                }
            _isFetchingEpisodes.value = false
        }
    }

    fun selectEpisode(episode: WyzieEpisode, fallbackTitle: String) {
        val tvShowName = _selectedTvShow.value?.name ?: fallbackTitle
        _selectedEpisode.value = episode
        searchSubtitles(tvShowName, episode.season_number, episode.episode_number)
    }

    fun searchSubtitles(query: String, season: Int? = null, episode: Int? = null, year: String? = null) {
        searchSubtitlesJob?.cancel()
        searchSubtitlesJob = scope.launch {
            _isSearchingSub.value = true
            clearSearchError()
            wyzieRepository.search(query, season, episode, year)
                .onSuccess { results ->
                    _wyzieSearchResults.value = results
                    _hasSearched.value = true
                }
                .onFailure { error ->
                    if (error is CancellationException) return@launch
                    Log.e(TAG, "Subtitle search failed", error)
                    _wyzieSearchResults.value = emptyList()
                    _hasSearched.value = false
                    reportError(error)
                }
            _isSearchingSub.value = false
        }
    }

    fun downloadSubtitle(subtitle: WyzieSubtitle, mediaTitle: String) {
        scope.launch {
            _isDownloadingSub.value = true
            wyzieRepository.download(subtitle, mediaTitle)
                .onSuccess { uri ->
                    addSubtitle(uri)
                }
                .onFailure { error ->
                    if (error is CancellationException) return@launch
                    Log.e(TAG, "Subtitle download failed", error)
                    onShowToast("Download failed")
                }
            _isDownloadingSub.value = false
        }
    }

    fun clearMediaSelection() {
        tvDetailsJob?.cancel()
        episodesJob?.cancel()
        _selectedTvShow.value = null
        _selectedSeason.value = null
        _selectedEpisode.value = null
        _seasonEpisodes.value = emptyList()
        _mediaSearchResults.value = emptyList()
        _wyzieSearchResults.value = emptyList()
        _hasSearched.value = false
        clearSearchError()
    }

    private fun reportError(error: Throwable) {
        val kind = (error as? WyzieException)?.kind
            ?: if (error is java.io.IOException) WyzieErrorKind.NetworkError else WyzieErrorKind.ServerError
        _searchError.value = kind
        // The sheet renders these states inline; the toast is a fallback for when it is not.
        if (kind == WyzieErrorKind.MissingApiKey || kind == WyzieErrorKind.InvalidApiKey) {
            onShowToast(context.getString(R.string.subtitle_search_api_key_required_title))
        }
    }

    // ==================== Utilities = ====================

    private fun isValidSubtitleFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".srt") || lower.endsWith(".vtt") ||
                lower.endsWith(".ssa") || lower.endsWith(".ass")
    }
}
