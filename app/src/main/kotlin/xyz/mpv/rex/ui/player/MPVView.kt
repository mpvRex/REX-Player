package xyz.mpv.rex.ui.player

import android.content.Context
import android.os.Environment
import android.util.AttributeSet

import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.SurfaceHolder
import xyz.mpv.rex.preferences.AdvancedPreferences
import xyz.mpv.rex.preferences.AudioPreferences
import xyz.mpv.rex.preferences.DecoderPreferences
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.SubtitlesPreferences
import xyz.mpv.rex.domain.anime4k.Anime4KManager
import xyz.mpv.rex.ui.player.PlayerActivity.Companion.TAG
import xyz.mpv.rex.ui.player.controls.components.panels.toColorHexString
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.KeyMapping
import `is`.xyz.mpv.MPVLib
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.reflect.KProperty

class MPVView(
  context: Context,
  attributes: AttributeSet,
) : BaseMPVView(context, attributes),
  KoinComponent {
  private val audioPreferences: AudioPreferences by inject()
  private val playerPreferences: PlayerPreferences by inject()
  private val decoderPreferences: DecoderPreferences by inject()
  private val advancedPreferences: AdvancedPreferences by inject()
  private val subtitlesPreferences: SubtitlesPreferences by inject()
  private val anime4kManager: Anime4KManager by inject()

  var isExiting = false

  /**
   * When the activity handles rotation itself (see `configChanges` in the manifest) the
   * SurfaceView is resized in place, so only [surfaceChanged] fires — not [surfaceCreated].
   * The base class merely pushes the new `android-surface-size` to mpv. While playback is
   * paused mpv's render loop is idle and never draws a frame at the new geometry, so the
   * compositor stretches the previous (now wrongly-sized) buffer and exposes uninitialized
   * regions — the "stretched frame + artifacts" seen on rotation while paused (video and
   * audio album-art alike). Forcing a repaint of the current frame fixes it.
   */
  override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    super.surfaceChanged(holder, format, width, height)
    if (isExiting) return
    // Only needed while paused; during playback the next frame repaints at the new size.
    val paused = runCatching { MPVLib.getPropertyBoolean("pause") }.getOrNull() == true
    if (paused) {
      // A zero-distance exact seek re-decodes and re-renders the current frame at the new
      // surface size without advancing playback. Works for both video and single-frame
      // album art, and (unlike frame-step) is well-behaved at EOF.
      runCatching { MPVLib.command("seek", "0", "relative+exact") }
    }
  }

  fun getVideoOutAspect(): Double? {
    // Try to get aspect from video-params/aspect first
    val rawAspect = MPVLib.getPropertyDouble("video-params/aspect")
    val rotate = MPVLib.getPropertyInt("video-params/rotate") ?: 0

    // If aspect is not available or 0, calculate from width and height
    val finalAspect = if (rawAspect == null || rawAspect < 0.001) {
      val width = runCatching {
        MPVLib.getPropertyInt("width") ?: MPVLib.getPropertyInt("video-params/w") ?: 0
      }.getOrDefault(0)

      val height = runCatching {
        MPVLib.getPropertyInt("height") ?: MPVLib.getPropertyInt("video-params/h") ?: 0
      }.getOrDefault(0)

      if (width > 0 && height > 0) {
        width.toDouble() / height.toDouble()
      } else {
        null
      }
    } else {
      rawAspect
    }

    return finalAspect?.let { aspect ->
      if (aspect <= 0.001) {
        return null
      }
      val isRotated = (rotate % 180 == 90)
      val correctedAspect = if (isRotated) 1.0 / aspect else aspect
      correctedAspect
    }
  }

  class TrackDelegate(
    private val name: String,
  ) {
    operator fun getValue(
      thisRef: Any?,
      property: KProperty<*>,
    ): Int {
      val v = MPVLib.getPropertyString(name)
      // we can get null here for "no" or other invalid value
      return v?.toIntOrNull() ?: -1
    }

    operator fun setValue(
      thisRef: Any?,
      property: KProperty<*>,
      value: Int,
    ) {
      if (value == -1) MPVLib.setPropertyString(name, "no") else MPVLib.setPropertyInt(name, value)
    }
  }

  var sid: Int by TrackDelegate("sid")
  var secondarySid: Int by TrackDelegate("secondary-sid")
  var aid: Int by TrackDelegate("aid")

  override fun initOptions() {
    val profile = decoderPreferences.profile.get()
    MPVLib.setOptionString("profile", profile)
    setVo(if (decoderPreferences.gpuNext.get()) "gpu-next" else "gpu")
    
    // Set GPU API context (Vulkan or OpenGL)
    if (decoderPreferences.useVulkan.get()) {
      MPVLib.setOptionString("gpu-context", "androidvk")
    }

    // Set hwdec with fallback order: HW+ (mediacodec) -> SW (no)
    // mediacodec-copy (HW) is omitted as it often causes Surface-related crashes on certain devices
    MPVLib.setOptionString(
      "hwdec",
      if (decoderPreferences.tryHWDecoding.get()) "mediacodec,no" else "no",
    )
    MPVLib.setOptionString("hwdec-codecs", "all")

    if (decoderPreferences.useYUV420P.get()) {
      MPVLib.setOptionString("vf", "format=yuv420p")
    }
    val logLevel = if (advancedPreferences.verboseLogging.get()) "v" else "warn"
    MPVLib.setOptionString("msg-level", "all=$logLevel")

    MPVLib.setPropertyBoolean("keep-open", true)
    MPVLib.setPropertyBoolean("input-default-bindings", true)
    if (!playerPreferences.autoplayOnOpen.get()) {
      MPVLib.setPropertyBoolean("pause", true)
    }

    MPVLib.setOptionString("tls-verify", "yes")
    MPVLib.setOptionString("tls-ca-file", "${context.filesDir.path}/cacert.pem")
    MPVLib.setOptionString("ytdl", "no")

    val screenshotDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    screenshotDir.mkdirs()
    MPVLib.setOptionString("screenshot-directory", screenshotDir.path)

    VideoFilters.entries.forEach {
      MPVLib.setOptionString(it.mpvProperty, it.preference(decoderPreferences).get().toString())
    }

    MPVLib.setOptionString("speed", playerPreferences.defaultSpeed.get().toString())
    MPVLib.setOptionString("vd-lavc-film-grain", "cpu")

    val preciseSeek = playerPreferences.usePreciseSeeking.get()
    MPVLib.setOptionString("hr-seek", if (preciseSeek) "yes" else "no")
    MPVLib.setOptionString("hr-seek-framedrop", if (preciseSeek) "no" else "yes")

    // Anime4K shader initialization (MUST be in initOptions, not after file load!)
    applyAnime4KShaders()

    setupSubtitlesOptions()
    setupAudioOptions()
  }

  override fun observeProperties() {
    for ((name, format) in observedProps) MPVLib.observeProperty(name, format)
  }

  /**
   * Attaches this view to an ALREADY-INITIALIZED global MPV instance (e.g. one created by
   * [HeadlessPlaybackController]). Unlike [initialize], this does NOT call `MPVLib.create()` /
   * `MPVLib.init()` — it only re-registers the surface callback and property observers so this
   * view can drive the live session and render video once its surface is created.
   */
  fun attachToExistingSession() {
    // Re-enable the GPU VO the headless controller disabled; surfaceCreated() will attach the
    // surface and flip force-window on.
    setVo(if (decoderPreferences.gpuNext.get()) "gpu-next" else "gpu")
    holder.addCallback(this)
    observeProperties()
  }

  override fun surfaceCreated(holder: SurfaceHolder) {
    super.surfaceCreated(holder)
    if (!MPVLifecycleLock.isNativeInitialized) return

    // Audio may have been loaded while vo=null with no video track selected. Unlike vid=auto,
    // selecting the attached-picture track by ID reliably starts its single-frame decoder.
    // ONLY do this if the file has NO real (non-albumart) video tracks (i.e. audio-only files).
    val trackCount = runCatching { MPVLib.getPropertyInt("track-list/count") ?: 0 }.getOrDefault(0)
    var hasRealVideoTrack = false
    var albumArtTrackId: Int? = null

    for (index in 0 until trackCount) {
      val type = runCatching { MPVLib.getPropertyString("track-list/$index/type") }.getOrNull()
      if (type == "video") {
        val isAlbumArt = runCatching { MPVLib.getPropertyBoolean("track-list/$index/albumart") }.getOrNull()
        val id = runCatching { MPVLib.getPropertyInt("track-list/$index/id") }.getOrNull()
        if (isAlbumArt == true) {
          if (albumArtTrackId == null) albumArtTrackId = id
        } else {
          hasRealVideoTrack = true
        }
      }
    }

    if (!hasRealVideoTrack && albumArtTrackId != null) {
      runCatching { MPVLib.setPropertyInt("vid", albumArtTrackId) }
      runCatching { MPVLib.command("seek", "0", "relative+exact") }
    }
  }

  override fun postInitOptions() {
    MPVLifecycleLock.onNativeInitialized()
    when (decoderPreferences.debanding.get()) {
      Debanding.None -> {}
      Debanding.CPU -> MPVLib.command("vf", "add", "@deband:gradfun=radius=12")
      Debanding.GPU -> MPVLib.setOptionString("deband", "yes")
    }

    advancedPreferences.enabledStatisticsPage.get().let {
      if (it in 1..5) {
        MPVLib.command("script-binding", "stats/display-stats-toggle")
        MPVLib.command("script-binding", "stats/display-page-$it")
      }
    }
  }

  @Suppress("ReturnCount", "DEPRECATION")
  fun onKey(event: KeyEvent): Boolean {
    if (event.action == KeyEvent.ACTION_MULTIPLE || KeyEvent.isModifierKey(event.keyCode)) {
      return false
    }

    var mapped = KeyMapping[event.keyCode]
    if (mapped == null) {
      // Fallback to produced glyph
      if (!event.isPrintingKey) {
        return false
      }

      val ch = event.unicodeChar
      if (ch.and(KeyCharacterMap.COMBINING_ACCENT) != 0) {
        return false // dead key
      }
      mapped = ch.toChar().toString()
    }

    if (event.repeatCount > 0) {
      return true
    }

    val mod: MutableList<String> = mutableListOf()
    event.isShiftPressed && mod.add("shift")
    event.isCtrlPressed && mod.add("ctrl")
    event.isAltPressed && mod.add("alt")
    event.isMetaPressed && mod.add("meta")

    val action = if (event.action == KeyEvent.ACTION_DOWN) "keydown" else "keyup"
    mod.add(mapped)
    MPVLib.command(action, mod.joinToString("+"))

    return true
  }

  // Any property consumed via `MPVLib.propX["..."]` must also be listed here.
  // `MPVLib.Property.map` is a process-scoped cache and skips `observeProperty`
  // on hits — so without an entry here the property is never re-registered on
  // fresh native handles created after the previous one was destroyed while the
  // JVM stayed alive (e.g. via MediaPlaybackService).
  private val observedProps =
    mapOf(
      "pause" to MPVLib.MpvFormat.MPV_FORMAT_FLAG,
      "paused-for-cache" to MPVLib.MpvFormat.MPV_FORMAT_FLAG,
      "video-params/aspect" to MPVLib.MpvFormat.MPV_FORMAT_DOUBLE,
      "video-params/w" to MPVLib.MpvFormat.MPV_FORMAT_INT64,
      "video-params/h" to MPVLib.MpvFormat.MPV_FORMAT_INT64,
      "eof-reached" to MPVLib.MpvFormat.MPV_FORMAT_FLAG,
      "time-pos" to MPVLib.MpvFormat.MPV_FORMAT_INT64,
      "duration" to MPVLib.MpvFormat.MPV_FORMAT_INT64,
      "volume-max" to MPVLib.MpvFormat.MPV_FORMAT_INT64,
      "track-list" to MPVLib.MpvFormat.MPV_FORMAT_NODE,
      "chapter-list" to MPVLib.MpvFormat.MPV_FORMAT_NODE,
      "speed" to MPVLib.MpvFormat.MPV_FORMAT_DOUBLE,
      "chapter" to MPVLib.MpvFormat.MPV_FORMAT_INT64,
      "volume" to MPVLib.MpvFormat.MPV_FORMAT_INT64,
      "hwdec-current" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "media-title" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "demuxer-cache-duration" to MPVLib.MpvFormat.MPV_FORMAT_DOUBLE,
      "cache-buffering-state" to MPVLib.MpvFormat.MPV_FORMAT_INT64,
      "audio-delay" to MPVLib.MpvFormat.MPV_FORMAT_DOUBLE,
      "sub-delay" to MPVLib.MpvFormat.MPV_FORMAT_DOUBLE,
      "sub-speed" to MPVLib.MpvFormat.MPV_FORMAT_DOUBLE,
      "sub-scale" to MPVLib.MpvFormat.MPV_FORMAT_DOUBLE,
      "sub-pos" to MPVLib.MpvFormat.MPV_FORMAT_INT64,
      "secondary-sub-scale" to MPVLib.MpvFormat.MPV_FORMAT_DOUBLE,
      "secondary-sub-pos" to MPVLib.MpvFormat.MPV_FORMAT_INT64,
      "sub-bold" to MPVLib.MpvFormat.MPV_FORMAT_FLAG,
      "sub-italic" to MPVLib.MpvFormat.MPV_FORMAT_FLAG,
      "sub-justify" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "sub-font" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "sub-font-size" to MPVLib.MpvFormat.MPV_FORMAT_INT64,
      "sub-border-style" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "sub-outline-size" to MPVLib.MpvFormat.MPV_FORMAT_INT64,
      "sub-shadow-offset" to MPVLib.MpvFormat.MPV_FORMAT_INT64,
      "user-data/mpvex/show_text" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "user-data/mpvex/toggle_ui" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "user-data/mpvex/show_panel" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "user-data/mpvex/set_button_title" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "user-data/mpvex/reset_button_title" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "user-data/mpvex/toggle_button" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "user-data/mpvex/seek_by" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "user-data/mpvex/seek_to" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "user-data/mpvex/seek_by_with_text" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "user-data/mpvex/seek_to_with_text" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "user-data/mpvex/software_keyboard" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
    )

  private fun setupAudioOptions() {
    // Disable MPV's automatic audio selection
    // App will handle track selection manually via TrackSelector to respect user choices
    MPVLib.setOptionString("alang", "")
    MPVLib.setOptionString("audio-delay", (audioPreferences.defaultAudioDelay.get() / 1000.0).toString())
    MPVLib.setOptionString("audio-pitch-correction", audioPreferences.audioPitchCorrection.get().toString())
    MPVLib.setOptionString("volume-max", (audioPreferences.volumeBoostCap.get() + 100).toString())
    
    // Volume normalization using dynamic audio normalization filter
    if (audioPreferences.volumeNormalization.get()) {
      MPVLib.setOptionString("af", "dynaudnorm")
    }
  }

  // Setup
  private fun setupSubtitlesOptions() {
    // Disable MPV's automatic subtitle selection
    // App will handle track selection manually via TrackSelector to respect user choices
    MPVLib.setOptionString("slang", "")
    MPVLib.setOptionString("sub-auto", "no")
    MPVLib.setOptionString("sub-file-paths", "")
    MPVLib.setOptionString("subs-fallback", "no")

    val fontsDirPath = "${context.filesDir.path}/fonts/"
    MPVLib.setOptionString("sub-fonts-dir", fontsDirPath)
    
    // Delay and speed for both primary and secondary
    val subDelay = (subtitlesPreferences.defaultSubDelay.get() / 1000.0).toString()
    val subSpeed = subtitlesPreferences.defaultSubSpeed.get().toString()
    MPVLib.setOptionString("sub-delay", subDelay)
    MPVLib.setOptionString("sub-speed", subSpeed)
    MPVLib.setOptionString("secondary-sub-delay", subDelay)
    MPVLib.setOptionString("secondary-sub-speed", subSpeed)

    val preferredFont = subtitlesPreferences.font.get()
    if (preferredFont.isNotBlank()) {
      MPVLib.setOptionString("sub-font", preferredFont)
      MPVLib.setOptionString("secondary-sub-font", preferredFont)
    }
    // If blank, MPV uses its default font

    if (subtitlesPreferences.overrideAssSubs.get()) {
      MPVLib.setOptionString("sub-ass-override", "force")
      MPVLib.setOptionString("sub-ass-justify", "yes")
    } else {
      MPVLib.setOptionString("sub-ass-override", "no")
    }
    // Force secondary subtitle override so secondary-sub-pos anchors it cleanly at the top without overlapping primary
    MPVLib.setOptionString("secondary-sub-ass-override", "force")

    // Typography and styling for both primary and secondary
    val fontSize = subtitlesPreferences.fontSize.get().toString()
    val bold = if (subtitlesPreferences.bold.get()) "yes" else "no"
    val italic = if (subtitlesPreferences.italic.get()) "yes" else "no"
    val justify = subtitlesPreferences.justification.get().value
    val textColor = subtitlesPreferences.textColor.get().toColorHexString()
    val backgroundColor = subtitlesPreferences.backgroundColor.get().toColorHexString()
    val borderColor = subtitlesPreferences.borderColor.get().toColorHexString()
    val borderSize = subtitlesPreferences.borderSize.get().toString()
    val borderStyle = subtitlesPreferences.borderStyle.get().value
    val shadowOffset = subtitlesPreferences.shadowOffset.get().toString()
    val subPos = subtitlesPreferences.subPos.get().toString()
    val subScale = subtitlesPreferences.subScale.get().toString()
    val secondarySubPos = subtitlesPreferences.secondarySubPos.get().toString()
    val secondarySubScale = subtitlesPreferences.secondarySubScale.get().toString()

    MPVLib.setOptionString("sub-font-size", fontSize)
    MPVLib.setOptionString("sub-bold", bold)
    MPVLib.setOptionString("sub-italic", italic)
    MPVLib.setOptionString("sub-justify", justify)
    MPVLib.setOptionString("sub-color", textColor)
    MPVLib.setOptionString("sub-back-color", backgroundColor)
    MPVLib.setOptionString("sub-border-color", borderColor)
    MPVLib.setOptionString("sub-border-size", borderSize)
    MPVLib.setOptionString("sub-border-style", borderStyle)
    MPVLib.setOptionString("sub-shadow-offset", shadowOffset)
    MPVLib.setOptionString("sub-scale", subScale)
    MPVLib.setOptionString("sub-pos", subPos)
    
    MPVLib.setOptionString("secondary-sub-font-size", fontSize)
    MPVLib.setOptionString("secondary-sub-bold", bold)
    MPVLib.setOptionString("secondary-sub-italic", italic)
    MPVLib.setOptionString("secondary-sub-justify", justify)
    MPVLib.setOptionString("secondary-sub-color", textColor)
    MPVLib.setOptionString("secondary-sub-back-color", backgroundColor)
    MPVLib.setOptionString("secondary-sub-border-color", borderColor)
    MPVLib.setOptionString("secondary-sub-border-size", borderSize)
    MPVLib.setOptionString("secondary-sub-border-style", borderStyle)
    MPVLib.setOptionString("secondary-sub-shadow-offset", shadowOffset)
    MPVLib.setOptionString("secondary-sub-scale", secondarySubScale)
    // Position secondary subtitle at top (default 10) instead of bottom to avoid overlap with primary
    MPVLib.setOptionString("secondary-sub-pos", secondarySubPos)

    val scaleByWindow = if (subtitlesPreferences.scaleByWindow.get()) "yes" else "no"
    MPVLib.setOptionString("sub-scale-by-window", scaleByWindow)
    MPVLib.setOptionString("sub-use-margins", scaleByWindow)
    MPVLib.setOptionString("secondary-sub-scale-by-window", scaleByWindow)
    MPVLib.setOptionString("secondary-sub-use-margins", scaleByWindow)

    val forceLtr = if (subtitlesPreferences.forceLtr.get()) "yes" else "no"
    MPVLib.setOptionString("sub-vsfilter-bidi-compat", forceLtr)
    MPVLib.setOptionString("sub-codepage", "auto:cp1256:utf-8")
  }


  fun applyAnime4KShaders() {
    runCatching {
      val enabled = decoderPreferences.enableAnime4K.get()
      if (!enabled) {
        return
      }
      
      // Anime4K requires the legacy GPU path unless gpu-next is running on Vulkan.
      val gpuNextActive = decoderPreferences.gpuNext.get()
      val useVulkan = decoderPreferences.useVulkan.get()
      if (gpuNextActive && !useVulkan) {
        return  // Abort shader loading to prevent incompatible state
      }
      
      // Initialize shader files if needed - THIS IS CRITICAL!
      if (!anime4kManager.initialize()) {
        return
      }
      
      // Get preferences
      val modeStr = decoderPreferences.anime4kMode.get()
      
      // Check if mode is OFF - if so, don't apply any shaders
      if (modeStr == "OFF") {
        return  // Exit early - user wants it OFF
      }
      
      // Parse user's selected mode
      val mode = try {
          Anime4KManager.Mode.valueOf(modeStr)
      } catch (e: IllegalArgumentException) {
          Anime4KManager.Mode.OFF
      }
      
      val qualityStr = decoderPreferences.anime4kQuality.get()
      val quality = try {
        Anime4KManager.Quality.valueOf(qualityStr)
      } catch (e: IllegalArgumentException) {
        Anime4KManager.Quality.BALANCED
      }
      
      // Get shader chain from manager
      val shaderChain = anime4kManager.getShaderChain(mode, quality)
      
      if (shaderChain.isNotEmpty()) {
        // OpenGL-only tuning should not be pushed onto the Vulkan backend.
        if (!useVulkan) {
          MPVLib.setOptionString("opengl-pbo", "yes")
          MPVLib.setOptionString("opengl-early-flush", "no")
        }
        MPVLib.setOptionString("vd-lavc-dr", "yes")
        
        // Apply shaders (MUST use setOptionString in initOptions!)
        MPVLib.setOptionString("glsl-shaders", shaderChain)
      }
    }.onFailure {
      // Don't crash - just continue without shaders
    }
  }
}
