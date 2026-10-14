package xyz.mpv.rex.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import xyz.mpv.rex.R

class NetworkViewModeTest {

  @Test
  fun `all view modes have valid string resource bindings`() {
    assertEquals(R.string.view_mode_gallery, NetworkViewMode.GALLERY.stringRes)
    assertEquals(R.string.view_mode_grid, NetworkViewMode.GRID.stringRes)
    assertEquals(R.string.view_mode_list, NetworkViewMode.LIST.stringRes)
    assertEquals(R.string.view_mode_detail, NetworkViewMode.DETAIL.stringRes)
  }

  @Test
  fun `enum contains exactly four expected view modes`() {
    val modes = NetworkViewMode.entries.map { it.name }
    assertEquals(listOf("GALLERY", "GRID", "LIST", "DETAIL"), modes)
  }

  @Test
  fun `valueOf parses strings correctly`() {
    assertEquals(NetworkViewMode.GALLERY, NetworkViewMode.valueOf("GALLERY"))
    assertEquals(NetworkViewMode.GRID, NetworkViewMode.valueOf("GRID"))
    assertEquals(NetworkViewMode.LIST, NetworkViewMode.valueOf("LIST"))
    assertEquals(NetworkViewMode.DETAIL, NetworkViewMode.valueOf("DETAIL"))
  }
}
