package edu.csuci.tweeter.ui.feed

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedChoiceTest {
    @Test
    fun forEveryone_following_isNewest() {
        assertEquals(FeedChoice.NEWEST, FeedChoice.FOLLOWING.forEveryone())
    }

    @Test
    fun forEveryone_choiceOfEveryonesPosts_isItself() {
        val everyonesChoices = listOf(FeedChoice.BEST, FeedChoice.NEWEST, FeedChoice.TOP)

        assertEquals(everyonesChoices, everyonesChoices.map(FeedChoice::forEveryone))
    }
}
