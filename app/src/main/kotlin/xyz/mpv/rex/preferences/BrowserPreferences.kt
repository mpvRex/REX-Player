package xyz.mpv.rex.preferences

import androidx.annotation.StringRes
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.preference.PreferenceStore
import xyz.mpv.rex.preferences.preference.getEnum

/**
 * Preferences for the video browser (folder and video lists)
 */
class BrowserPreferences(
  preferenceStore: PreferenceStore,
  context: android.content.Context,
) {
  // Folder sorting preferences
  val folderSortType = preferenceStore.getEnum("folder_sort_type", FolderSortType.Title)
  val folderSortOrder = preferenceStore.getEnum("folder_sort_order", SortOrder.Ascending)
  val folderSortOrderTitle = preferenceStore.getEnum("folder_sort_order_title", SortOrder.Ascending)
  val folderSortOrderDuration = preferenceStore.getEnum("folder_sort_order_duration", SortOrder.Descending)
  val folderSortOrderDate = preferenceStore.getEnum("folder_sort_order_date", SortOrder.Descending)
  val folderSortOrderSize = preferenceStore.getEnum("folder_sort_order_size", SortOrder.Descending)
  val folderSortOrderCount = preferenceStore.getEnum("folder_sort_order_count", SortOrder.Descending)

  fun getFolderSortOrder(type: FolderSortType): SortOrder =
    when (type) {
      FolderSortType.Title -> folderSortOrderTitle.get()
      FolderSortType.Duration -> folderSortOrderDuration.get()
      FolderSortType.Date -> folderSortOrderDate.get()
      FolderSortType.Size -> folderSortOrderSize.get()
      FolderSortType.VideoCount -> folderSortOrderCount.get()
    }

  fun saveFolderSortOrder(type: FolderSortType, order: SortOrder) {
    when (type) {
      FolderSortType.Title -> folderSortOrderTitle.set(order)
      FolderSortType.Duration -> folderSortOrderDuration.set(order)
      FolderSortType.Date -> folderSortOrderDate.set(order)
      FolderSortType.Size -> folderSortOrderSize.set(order)
      FolderSortType.VideoCount -> folderSortOrderCount.set(order)
    }
    folderSortOrder.set(order)
  }

  fun setFolderSortType(type: FolderSortType) {
    folderSortType.set(type)
    folderSortOrder.set(getFolderSortOrder(type))
  }

  // Video sorting preferences
  val videoSortType = preferenceStore.getEnum("video_sort_type", VideoSortType.Title)
  val videoSortOrder = preferenceStore.getEnum("video_sort_order", SortOrder.Ascending)
  val videoSortOrderTitle = preferenceStore.getEnum("video_sort_order_title", SortOrder.Ascending)
  val videoSortOrderDuration = preferenceStore.getEnum("video_sort_order_duration", SortOrder.Descending)
  val videoSortOrderDate = preferenceStore.getEnum("video_sort_order_date", SortOrder.Descending)
  val videoSortOrderSize = preferenceStore.getEnum("video_sort_order_size", SortOrder.Descending)

  fun getVideoSortOrder(type: VideoSortType): SortOrder =
    when (type) {
      VideoSortType.Title -> videoSortOrderTitle.get()
      VideoSortType.Duration -> videoSortOrderDuration.get()
      VideoSortType.Date -> videoSortOrderDate.get()
      VideoSortType.Size -> videoSortOrderSize.get()
    }

  fun saveVideoSortOrder(type: VideoSortType, order: SortOrder) {
    when (type) {
      VideoSortType.Title -> videoSortOrderTitle.set(order)
      VideoSortType.Duration -> videoSortOrderDuration.set(order)
      VideoSortType.Date -> videoSortOrderDate.set(order)
      VideoSortType.Size -> videoSortOrderSize.set(order)
    }
    videoSortOrder.set(order)
  }

  fun setVideoSortType(type: VideoSortType) {
    videoSortType.set(type)
    videoSortOrder.set(getVideoSortOrder(type))
  }

  // Network sorting preferences
  val networkSortType = preferenceStore.getEnum("network_sort_type", FolderSortType.Title)
  val networkSortOrder = preferenceStore.getEnum("network_sort_order", SortOrder.Ascending)
  val networkShowHiddenFiles = preferenceStore.getBoolean("network_show_hidden_files", false)
  val networkSortOrderTitle = preferenceStore.getEnum("network_sort_order_title", SortOrder.Ascending)
  val networkSortOrderDate = preferenceStore.getEnum("network_sort_order_date", SortOrder.Descending)
  val networkSortOrderSize = preferenceStore.getEnum("network_sort_order_size", SortOrder.Descending)

  fun getNetworkSortOrder(type: FolderSortType): SortOrder =
    when (type) {
      FolderSortType.Title -> networkSortOrderTitle.get()
      FolderSortType.Date -> networkSortOrderDate.get()
      FolderSortType.Size -> networkSortOrderSize.get()
      else -> SortOrder.Ascending
    }

  fun saveNetworkSortOrder(type: FolderSortType, order: SortOrder) {
    when (type) {
      FolderSortType.Title -> networkSortOrderTitle.set(order)
      FolderSortType.Date -> networkSortOrderDate.set(order)
      FolderSortType.Size -> networkSortOrderSize.set(order)
      else -> {}
    }
    networkSortOrder.set(order)
  }

  fun setNetworkSortType(type: FolderSortType) {
    networkSortType.set(type)
    networkSortOrder.set(getNetworkSortOrder(type))
  }

  val folderViewMode = preferenceStore.getEnum("folder_view_mode", FolderViewMode.AlbumView)

  // Whether the "Filters" section in the sort dialog is expanded. Persisted so it
  // carries over between Folder/Tree/Library views (each has its own dialog slot).
  val sortDialogFiltersExpanded = preferenceStore.getBoolean("sort_dialog_filters_expanded", true)

  private val isTablet = context.resources.configuration.smallestScreenWidthDp >= 600
  val folderGridColumnsPortrait = preferenceStore.getInt("folder_grid_columns_portrait", if (isTablet) 4 else 3)
  val folderGridColumnsLandscape = preferenceStore.getInt("folder_grid_columns_landscape", 5)

  val videoGridColumnsPortrait = preferenceStore.getInt("video_grid_columns_portrait", if (isTablet) 4 else 2)
  val videoGridColumnsLandscape = preferenceStore.getInt("video_grid_columns_landscape", 4)

  // Visibility preferences for video card chips
  val showVideoThumbnails = preferenceStore.getBoolean("show_video_thumbnails", true)
  val showFileExtension = preferenceStore.getBoolean("show_file_extension", false)
  val showSizeChip = preferenceStore.getBoolean("show_size_chip", true)
  // Metadata-dependent chips (disabled by default for better performance)
  val showResolutionChip = preferenceStore.getBoolean("show_resolution_chip", false)
  val showFramerateInResolution = preferenceStore.getBoolean("show_framerate_in_resolution", false)
  val showSubtitleIndicator = preferenceStore.getBoolean("show_subtitle_indicator", false)
  val showProgressBar = preferenceStore.getBoolean("show_progress_bar", true)
  val mediaLayoutMode = preferenceStore.getEnum("media_layout_mode", MediaLayoutMode.LIST)

  // Visibility preferences for folder card chips
  val showTotalVideosChip = preferenceStore.getBoolean("show_total_videos_chip", true)
  // Metadata-dependent chips (disabled by default for better performance)
  val showTotalDurationChip = preferenceStore.getBoolean("show_total_duration_chip", false)
  val showTotalSizeChip = preferenceStore.getBoolean("show_total_size_chip", true)
  val showDateChip = preferenceStore.getBoolean("show_date_chip", false)
  val showFolderPath = preferenceStore.getBoolean("show_folder_path", true)

  // Auto-scroll to last played media preference (like MX Player)
  val autoScrollToLastPlayed = preferenceStore.getBoolean("auto_scroll_to_last_played", false)

  // Watched threshold preference (percentage 1-100)
  val watchedThreshold = preferenceStore.getInt("watched_threshold", 95)

  // Media visibility preferences
  val showAudioFiles = preferenceStore.getBoolean("show_audio_files", false)
  val includeNoMediaContent = preferenceStore.getBoolean("include_no_media_content", false)
  val showTreeViewPath = preferenceStore.getBoolean("show_tree_view_path", false)

  // RexShorts preferences
  val enableShorts = preferenceStore.getBoolean("enable_shorts", true)
  val autoSwipeShorts = preferenceStore.getBoolean("shorts_auto_swipe", false)
  val enableGlassShortsControls = preferenceStore.getBoolean("enable_glass_shorts_controls", false)
  val showShortsBackButton = preferenceStore.getBoolean("show_shorts_back_button", true)
  val includeShortHorizontalVideos = preferenceStore.getBoolean("shorts_include_horizontal", false)
  val maxHorizontalVideoDurationMinutes = preferenceStore.getInt("shorts_max_horizontal_minutes", 1)
  val shortsSourceFolders = preferenceStore.getStringSet("shorts_source_folders", emptySet())
  val lastWatchedShortPath = preferenceStore.getString("shorts_last_watched_path", "")

  // Bottom navigation visibility and ordering preferences
  val enableTabYou = preferenceStore.getBoolean("enable_tab_you", true)
  val enableTabRecents = preferenceStore.getBoolean("enable_tab_recents", true)
  val enableTabPlaylists = preferenceStore.getBoolean("enable_tab_playlists", true)
  val enableTabNetwork = preferenceStore.getBoolean("enable_tab_network", true)
  val bottomNavOrder = preferenceStore.getString("bottom_nav_order", "home,shorts,recents,playlists,network,you")
  val playedNetworkLinks = preferenceStore.getString("played_network_links", "")

  fun getBottomNavOrder(): List<String> {
    val defaultOrder = listOf("home", "shorts", "recents", "playlists", "network", "you")
    val saved = bottomNavOrder.get().split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val missing = defaultOrder.filterNot { it in saved }
    return saved + missing
  }

  fun setBottomNavOrder(order: List<String>) {
    bottomNavOrder.set(order.joinToString(","))
  }

  // Auto-playlist preferences
  val showRecentlyAddedPlaylist = preferenceStore.getBoolean("show_recently_added_playlist", true)
  val showMostPlayedPlaylist = preferenceStore.getBoolean("show_most_played_playlist", true)
}

/**
 * Sort order options
 */
enum class SortOrder(
  @StringRes val stringRes: Int,
) {
  Ascending(R.string.sort_order_ascending),
  Descending(R.string.sort_order_descending),
  ;

  val isAscending: Boolean
    get() = this == Ascending
}

/**
 * Folder sorting options
 */
enum class FolderSortType(
  @StringRes val stringRes: Int,
) {
  Title(R.string.sort_type_title),
  Duration(R.string.sort_type_duration),
  Date(R.string.sort_type_date),
  Size(R.string.sort_type_size),
  VideoCount(R.string.sort_type_count),
  ;

  val displayName: String
    get() =
      when (this) {
        Title -> "Title"
        Duration -> "Duration"
        Date -> "Date"
        Size -> "Size"
        VideoCount -> "Count"
      }
}

/**
 * Video sorting options
 */
enum class VideoSortType(
  @StringRes val stringRes: Int,
) {
  Title(R.string.sort_type_title),
  Duration(R.string.sort_type_duration),
  Date(R.string.sort_type_date),
  Size(R.string.sort_type_size),
  ;

  val displayName: String
    get() =
      when (this) {
        Title -> "Title"
        Duration -> "Duration"
        Date -> "Date"
        Size -> "Size"
      }
}

/**
 * Folder view mode options
 */
enum class FolderViewMode(
  @StringRes val stringRes: Int,
) {
  AlbumView(R.string.view_mode_folder),
  FileManager(R.string.view_mode_tree),
  MediaLibrary(R.string.view_mode_library),
  ;
}

enum class MediaLayoutMode(
  @StringRes val stringRes: Int,
) {
  LIST(R.string.layout_mode_list),
  GRID(R.string.layout_mode_grid),
  ;
}
