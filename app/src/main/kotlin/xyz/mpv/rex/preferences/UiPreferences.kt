package xyz.mpv.rex.preferences

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Consolidated UI settings for easier consumption in Composables and ViewModels
 */
@Immutable
data class UiSettings(
    val unlimitedNameLines: Boolean = false,
    val showVideoThumbnails: Boolean = true,
    val showSizeChip: Boolean = true,
    val showResolutionChip: Boolean = true,
    val showFramerateInResolution: Boolean = false,
    val showProgressBar: Boolean = true,
    val showDateChip: Boolean = false,
    val showTotalDurationChip: Boolean = false,
    val showUnplayedOldVideoLabel: Boolean = true,
    val unplayedOldVideoDays: Int = 7,
    val showNetworkThumbnails: Boolean = false,
    val showFileExtension: Boolean = false
)

/**
 * Provider for unified UI settings
 */
class UiPreferences(
    private val appearancePreferences: AppearancePreferences,
    private val browserPreferences: BrowserPreferences
) {
    /**
     * Observes all relevant UI settings as a single flow
     */
    fun observeUiSettings(): Flow<UiSettings> {
        return combine(
            appearancePreferences.unlimitedNameLines.changes(),
            browserPreferences.showVideoThumbnails.changes(),
            browserPreferences.showSizeChip.changes(),
            browserPreferences.showResolutionChip.changes(),
            browserPreferences.showFramerateInResolution.changes(),
            browserPreferences.showProgressBar.changes(),
            browserPreferences.showDateChip.changes(),
            browserPreferences.showTotalDurationChip.changes(),
            appearancePreferences.showUnplayedOldVideoLabel.changes(),
            appearancePreferences.unplayedOldVideoDays.changes(),
            appearancePreferences.showNetworkThumbnails.changes(),
            browserPreferences.showFileExtension.changes()
            
        ) { values: Array<Any?> ->
            UiSettings(
                unlimitedNameLines = values[0] as Boolean,
                showVideoThumbnails = values[1] as Boolean,
                showSizeChip = values[2] as Boolean,
                showResolutionChip = values[3] as Boolean,
                showFramerateInResolution = values[4] as Boolean,
                showProgressBar = values[5] as Boolean,
                showDateChip = values[6] as Boolean,
                showTotalDurationChip = values[7] as Boolean,
                showUnplayedOldVideoLabel = values[8] as Boolean,
                unplayedOldVideoDays = values[9] as Int,
                showNetworkThumbnails = values[10] as Boolean,
                showFileExtension = values[11] as Boolean
            )
        }
    }
    
    /**
     * Returns current snapshot of UI settings
     */
    fun getUiSettings(): UiSettings {
        return UiSettings(
            unlimitedNameLines = appearancePreferences.unlimitedNameLines.get(),
            showVideoThumbnails = browserPreferences.showVideoThumbnails.get(),
            showSizeChip = browserPreferences.showSizeChip.get(),
            showResolutionChip = browserPreferences.showResolutionChip.get(),
            showFramerateInResolution = browserPreferences.showFramerateInResolution.get(),
            showProgressBar = browserPreferences.showProgressBar.get(),
            showDateChip = browserPreferences.showDateChip.get(),
            showTotalDurationChip = browserPreferences.showTotalDurationChip.get(),
            showUnplayedOldVideoLabel = appearancePreferences.showUnplayedOldVideoLabel.get(),
            unplayedOldVideoDays = appearancePreferences.unplayedOldVideoDays.get(),
            showNetworkThumbnails = appearancePreferences.showNetworkThumbnails.get(),
            showFileExtension = browserPreferences.showFileExtension.get()
        )
    }
}
