package xyz.mpv.rex.ui.browser.networkstreaming

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mpv.rex.preferences.NetworkViewMode
import xyz.mpv.rex.ui.browser.cards.CardLayoutConfigProvider

class NetworkCardLayoutConfigTest {

  @Test
  fun `gallery mode maps to single column hero card configuration`() {
    val config = CardLayoutConfigProvider.configFor(NetworkViewMode.GALLERY, screenWidthDp = 400)
    assertTrue("Gallery must use grid container", config.isGridMode)
    assertEquals("Gallery hero must use exactly 1 column", 1, config.gridColumns)
    assertEquals(16f / 9f, config.thumbnailAspectRatio, 0.001f)
    assertTrue("Gallery target dimension must be high-res >= 512px", config.targetDimensionPx >= 512)
    assertTrue("Gallery must display chips", config.showChips)
    assertFalse("Gallery is not detail mode", config.isDetailMode)
  }

  @Test
  fun `grid mode maps to responsive multi-column layout`() {
    val phoneConfig = CardLayoutConfigProvider.configFor(NetworkViewMode.GRID, screenWidthDp = 360)
    assertTrue(phoneConfig.isGridMode)
    assertEquals(2, phoneConfig.gridColumns)
    assertFalse("Grid cells suppress chips to preserve vertical space", phoneConfig.showChips)
    assertEquals(384, phoneConfig.targetDimensionPx)

    val tabletConfig = CardLayoutConfigProvider.configFor(NetworkViewMode.GRID, screenWidthDp = 700)
    assertEquals(3, tabletConfig.gridColumns)

    val desktopConfig = CardLayoutConfigProvider.configFor(NetworkViewMode.GRID, screenWidthDp = 1000)
    assertEquals(4, desktopConfig.gridColumns)
  }

  @Test
  fun `list mode maps to standard row layout with 128dp thumbnail`() {
    val config = CardLayoutConfigProvider.configFor(NetworkViewMode.LIST)
    assertFalse("List mode is row layout, not grid", config.isGridMode)
    assertEquals(1, config.gridColumns)
    assertEquals(128.dp, config.thumbnailSizeDp)
    assertTrue(config.showChips)
    assertFalse(config.isDetailMode)
    assertEquals(256, config.targetDimensionPx)
  }

  @Test
  fun `detail mode maps to compact list row with 40dp thumbnail and single line title`() {
    val config = CardLayoutConfigProvider.configFor(NetworkViewMode.DETAIL)
    assertFalse(config.isGridMode)
    assertEquals(1, config.gridColumns)
    assertEquals(40.dp, config.thumbnailSizeDp)
    assertEquals(1, config.maxTitleLines)
    assertFalse("Detail mode suppresses chip flow rows", config.showChips)
    assertTrue("Detail mode flag is set", config.isDetailMode)
    assertEquals(96, config.targetDimensionPx)
  }

  @Test
  fun `unlimitedNameLines overrides maxTitleLines on gallery, grid, and list but keeps detail at 1`() {
    val gallery = CardLayoutConfigProvider.configFor(NetworkViewMode.GALLERY, unlimitedNameLines = true)
    assertEquals(Int.MAX_VALUE, gallery.maxTitleLines)

    val grid = CardLayoutConfigProvider.configFor(NetworkViewMode.GRID, unlimitedNameLines = true)
    assertEquals(Int.MAX_VALUE, grid.maxTitleLines)

    val list = CardLayoutConfigProvider.configFor(NetworkViewMode.LIST, unlimitedNameLines = true)
    assertEquals(Int.MAX_VALUE, list.maxTitleLines)

    val detail = CardLayoutConfigProvider.configFor(NetworkViewMode.DETAIL, unlimitedNameLines = true)
    assertEquals(1, detail.maxTitleLines)
  }

  @Test
  fun `all enum entries have non-null valid layout mappings`() {
    for (mode in NetworkViewMode.entries) {
      val config = CardLayoutConfigProvider.configFor(mode)
      assertEquals(mode, config.viewMode)
      assertTrue(config.targetDimensionPx > 0)
      assertTrue(config.gridColumns >= 1)
    }
  }
}
