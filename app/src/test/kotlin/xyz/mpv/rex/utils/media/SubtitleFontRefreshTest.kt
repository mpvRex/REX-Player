package xyz.mpv.rex.utils.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleFontRefreshTest {
  @Test
  fun primaryCustom_secondaryEmpty_toDefault_needsRebuild() {
    assertTrue(needsRendererRebuild(previousFont = "Osama Font", previousSecondaryFont = "", newFont = ""))
  }

  @Test
  fun primaryEmpty_secondaryCustom_toDefault_needsRebuild() {
    assertTrue(needsRendererRebuild(previousFont = "", previousSecondaryFont = "Osama Font", newFont = ""))
  }

  @Test
  fun bothCustom_toDefault_needsRebuild() {
    assertTrue(needsRendererRebuild(previousFont = "Osama Font", previousSecondaryFont = "Osama Font", newFont = ""))
  }

  @Test
  fun bothEmpty_toDefaultAgain_doesNotRebuild() {
    assertFalse(needsRendererRebuild(previousFont = "", previousSecondaryFont = "", newFont = ""))
  }

  @Test
  fun unreadablePreviousValues_toDefault_areTreatedAsNeedingRebuild() {
    assertTrue(needsRendererRebuild(previousFont = null, previousSecondaryFont = null, newFont = ""))
    assertTrue(needsRendererRebuild(previousFont = null, previousSecondaryFont = "", newFont = ""))
    assertTrue(needsRendererRebuild(previousFont = "", previousSecondaryFont = null, newFont = ""))
  }

  @Test
  fun selectingACustomFont_neverRebuilds() {
    assertFalse(needsRendererRebuild(previousFont = "", previousSecondaryFont = "", newFont = "Osama Font"))
    assertFalse(needsRendererRebuild(previousFont = "Osama Font", previousSecondaryFont = "Osama Font", newFont = "Other Font"))
    assertFalse(needsRendererRebuild(previousFont = "Osama Font", previousSecondaryFont = "", newFont = "Other Font"))
    assertFalse(needsRendererRebuild(previousFont = null, previousSecondaryFont = null, newFont = "Osama Font"))
  }

  @Test
  fun equivalentDir_togglesTrailingSlash() {
    assertEquals("/data/user/0/app/files/fonts", equivalentFontsDir("/data/user/0/app/files/fonts/"))
    assertEquals("/data/user/0/app/files/fonts/", equivalentFontsDir("/data/user/0/app/files/fonts"))
  }

  @Test
  fun equivalentDir_isAlwaysTextuallyDifferent_andAlternates() {
    val original = "/data/user/0/app/files/fonts/"
    val first = equivalentFontsDir(original)!!
    val second = equivalentFontsDir(first)!!
    assertTrue(first != original)
    assertTrue(second != first)
    assertEquals(original, second)
  }

  @Test
  fun equivalentDir_rejectsUnusableValues() {
    assertNull(equivalentFontsDir(null))
    assertNull(equivalentFontsDir(""))
    assertNull(equivalentFontsDir("   "))
    assertNull(equivalentFontsDir("/"))
    assertNull(equivalentFontsDir("//"))
  }
}
