package xyz.mpv.rex.preferences

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mpv.rex.preferences.preference.Preference
import xyz.mpv.rex.preferences.preference.PreferenceStore

class PlayerPreferencesTest {

  private class InMemoryPreference<T>(
    private val key: String,
    private val defaultValue: T,
    private val storage: MutableMap<String, Any?>,
  ) : Preference<T> {
    private val flow = MutableStateFlow(get())

    override fun key(): String = key
    @Suppress("UNCHECKED_CAST")
    override fun get(): T = (storage[key] as? T) ?: defaultValue
    override fun set(value: T) {
      storage[key] = value
      flow.value = value
    }
    override fun isSet(): Boolean = storage.containsKey(key)
    override fun delete() {
      storage.remove(key)
      flow.value = defaultValue
    }
    override fun defaultValue(): T = defaultValue
    override fun changes(): Flow<T> = flow.asStateFlow()
    override fun stateIn(scope: CoroutineScope) = flow.asStateFlow()
  }

  private class InMemoryPreferenceStore : PreferenceStore {
    private val storage = mutableMapOf<String, Any?>()

    override fun getString(key: String, defaultValue: String): Preference<String> =
      InMemoryPreference(key, defaultValue, storage)

    override fun getLong(key: String, defaultValue: Long): Preference<Long> =
      InMemoryPreference(key, defaultValue, storage)

    override fun getInt(key: String, defaultValue: Int): Preference<Int> =
      InMemoryPreference(key, defaultValue, storage)

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
      InMemoryPreference(key, defaultValue, storage)

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
      InMemoryPreference(key, defaultValue, storage)

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
      InMemoryPreference(key, defaultValue, storage)

    override fun <T> getObject(
      key: String,
      defaultValue: T,
      serializer: (T) -> String,
      deserializer: (String) -> T,
    ): Preference<T> = InMemoryPreference(key, defaultValue, storage)

    override fun getAll(): Map<String, *> = storage
  }

  @Test
  fun autoplayOnOpen_defaultsToTrue_andPersistsChanges() {
    val store = InMemoryPreferenceStore()
    val preferences = PlayerPreferences(store)

    assertEquals("autoplay_on_open", preferences.autoplayOnOpen.key())
    assertTrue(preferences.autoplayOnOpen.defaultValue())
    assertTrue(preferences.autoplayOnOpen.get())

    preferences.autoplayOnOpen.set(false)
    assertFalse(preferences.autoplayOnOpen.get())

    preferences.autoplayOnOpen.set(true)
    assertTrue(preferences.autoplayOnOpen.get())

    preferences.autoplayOnOpen.set(false)
    assertFalse(preferences.autoplayOnOpen.get())
    preferences.autoplayOnOpen.delete()
    assertTrue(preferences.autoplayOnOpen.get())
  }

  @Test
  fun autoplayOnOpen_and_autoplayNextVideo_operateIndependently() {
    val store = InMemoryPreferenceStore()
    val preferences = PlayerPreferences(store)

    assertTrue(preferences.autoplayOnOpen.get())
    assertTrue(preferences.autoplayNextVideo.get())

    preferences.autoplayOnOpen.set(false)
    assertFalse(preferences.autoplayOnOpen.get())
    assertTrue(preferences.autoplayNextVideo.get())

    preferences.autoplayNextVideo.set(false)
    assertFalse(preferences.autoplayOnOpen.get())
    assertFalse(preferences.autoplayNextVideo.get())

    preferences.autoplayOnOpen.set(true)
    assertTrue(preferences.autoplayOnOpen.get())
    assertFalse(preferences.autoplayNextVideo.get())
  }

  @Test
  fun searchablePreferences_indexesAutoplayOnOpen() {
    val autoplayOnOpenEntry = xyz.mpv.rex.ui.preferences.SearchablePreferences.allPreferences.find {
      it.titleRes == xyz.mpv.rex.R.string.pref_autoplay_on_open_title
    }

    org.junit.Assert.assertNotNull(autoplayOnOpenEntry)
    assertEquals(xyz.mpv.rex.R.string.pref_autoplay_on_open_summary, autoplayOnOpenEntry?.summaryRes)
    assertEquals("Player", autoplayOnOpenEntry?.category)
    assertTrue(autoplayOnOpenEntry?.keywords?.contains("autoplay") == true)
    assertTrue(autoplayOnOpenEntry?.keywords?.contains("open") == true)
  }

  @Test
  fun resumePlaybackMode_defaultsToAlways_andPersistsChanges() {
    val store = InMemoryPreferenceStore()
    val preferences = PlayerPreferences(store)

    assertEquals(xyz.mpv.rex.ui.player.ResumePlaybackMode.Always, preferences.resumePlaybackMode.get())
    assertEquals("resume_playback_mode", preferences.resumePlaybackMode.key())

    preferences.resumePlaybackMode.set(xyz.mpv.rex.ui.player.ResumePlaybackMode.Ask)
    assertEquals(xyz.mpv.rex.ui.player.ResumePlaybackMode.Ask, preferences.resumePlaybackMode.get())

    preferences.resumePlaybackMode.set(xyz.mpv.rex.ui.player.ResumePlaybackMode.Never)
    assertEquals(xyz.mpv.rex.ui.player.ResumePlaybackMode.Never, preferences.resumePlaybackMode.get())

    preferences.resumePlaybackMode.delete()
    assertEquals(xyz.mpv.rex.ui.player.ResumePlaybackMode.Always, preferences.resumePlaybackMode.get())
  }

  @Test
  fun autoResumeOnAsk_defaultsToTrue_andPersistsChanges() {
    val store = InMemoryPreferenceStore()
    val preferences = PlayerPreferences(store)

    assertTrue(preferences.autoResumeOnAsk.get())
    assertEquals("auto_resume_on_ask", preferences.autoResumeOnAsk.key())

    preferences.autoResumeOnAsk.set(false)
    assertFalse(preferences.autoResumeOnAsk.get())

    preferences.autoResumeOnAsk.set(true)
    assertTrue(preferences.autoResumeOnAsk.get())

    preferences.autoResumeOnAsk.delete()
    assertTrue(preferences.autoResumeOnAsk.get())
  }

  @Test
  fun lastMoreSheetTab_defaultsToZero_andPersistsChanges() {
    val store = InMemoryPreferenceStore()
    val preferences = PlayerPreferences(store)

    assertEquals("last_more_sheet_tab", preferences.lastMoreSheetTab.key())
    assertEquals(0, preferences.lastMoreSheetTab.defaultValue())
    assertEquals(0, preferences.lastMoreSheetTab.get())

    preferences.lastMoreSheetTab.set(2)
    assertEquals(2, preferences.lastMoreSheetTab.get())

    preferences.lastMoreSheetTab.set(4)
    assertEquals(4, preferences.lastMoreSheetTab.get())

    preferences.lastMoreSheetTab.delete()
    assertEquals(0, preferences.lastMoreSheetTab.get())
  }

  @Test
  fun longPressSpeedForVideo_defaultsToHoldForMultipleSpeed_andPersistsPerVideo() {
    val store = InMemoryPreferenceStore()
    val preferences = PlayerPreferences(store)

    assertEquals(2f, preferences.holdForMultipleSpeed.get(), 0.001f)
    assertEquals(2f, preferences.longPressSpeedForVideo("video_1").get(), 0.001f)

    preferences.holdForMultipleSpeed.set(3f)
    assertEquals(3f, preferences.longPressSpeedForVideo("video_1").get(), 0.001f)

    preferences.longPressSpeedForVideo("video_1").set(4.5f)
    assertEquals(4.5f, preferences.longPressSpeedForVideo("video_1").get(), 0.001f)

    assertEquals(3f, preferences.longPressSpeedForVideo("video_2").get(), 0.001f)
  }
}

