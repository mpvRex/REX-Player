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
  ) : Preference<T> {
    private val flow = MutableStateFlow(defaultValue)

    override fun key(): String = key
    override fun get(): T = flow.value
    override fun set(value: T) { flow.value = value }
    override fun isSet(): Boolean = true
    override fun delete() { flow.value = defaultValue }
    override fun defaultValue(): T = defaultValue
    override fun changes(): Flow<T> = flow.asStateFlow()
    override fun stateIn(scope: CoroutineScope) = flow.asStateFlow()
  }

  private class InMemoryPreferenceStore : PreferenceStore {
    override fun getString(key: String, defaultValue: String): Preference<String> =
      InMemoryPreference(key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Preference<Long> =
      InMemoryPreference(key, defaultValue)

    override fun getInt(key: String, defaultValue: Int): Preference<Int> =
      InMemoryPreference(key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
      InMemoryPreference(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
      InMemoryPreference(key, defaultValue)

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
      InMemoryPreference(key, defaultValue)

    override fun <T> getObject(
      key: String,
      defaultValue: T,
      serializer: (T) -> String,
      deserializer: (String) -> T,
    ): Preference<T> = InMemoryPreference(key, defaultValue)

    override fun getAll(): Map<String, *> = emptyMap<String, Any>()
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
}
