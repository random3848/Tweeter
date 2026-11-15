package edu.csuci.tweeter.ui.navigation

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapCoverTest {
    @Test
    fun hidesMap_beforeFirstCount_isWhatTheCoverWasCreatedWith() {
        assertTrue(MapCover(hidesMapUntilCounted = true).hidesMap)
        assertFalse(MapCover(hidesMapUntilCounted = false).hidesMap)
    }

    @Test
    fun hidesMap_firstCountFindsNoScreen_false() {
        val cover = MapCover(hidesMapUntilCounted = true)

        cover.finishFirstCount()

        assertFalse(cover.hidesMap)
    }

    @Test
    fun hidesMap_screenCounted_true() {
        val cover = MapCover(hidesMapUntilCounted = false)
        cover.addScreen()

        cover.finishFirstCount()

        assertTrue(cover.hidesMap)
    }

    @Test
    fun hidesMap_lastScreenRemoved_false() {
        val cover = MapCover(hidesMapUntilCounted = true)
        cover.addScreen()
        cover.addScreen()
        cover.finishFirstCount()

        cover.removeScreen()
        cover.removeScreen()

        assertFalse(cover.hidesMap)
    }

    @Test
    fun restored_coverThatHidMap_hidesItUntilCounted() {
        val cover = MapCover(hidesMapUntilCounted = false)
        cover.addScreen()
        cover.finishFirstCount()

        val restored = restore(cover)

        assertTrue(restored.hidesMap)
    }

    @Test
    fun restored_coverThatShowedMap_showsItUntilCounted() {
        val cover = MapCover(hidesMapUntilCounted = true)
        cover.finishFirstCount()

        val restored = restore(cover)

        assertFalse(restored.hidesMap)
    }

    // The cover the activity gets back after Android recreates it, from [cover]'s saved state.
    private fun restore(cover: MapCover): MapCover {
        val saved = with(MapCover.Saver) { SaverScope { true }.save(cover) }
        val restored = MapCover.Saver.restore(checkNotNull(saved) { "The cover saved nothing" })
        return checkNotNull(restored) { "The cover wasn't restored" }
    }
}
