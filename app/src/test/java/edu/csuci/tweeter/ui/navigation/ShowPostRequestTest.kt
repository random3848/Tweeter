package edu.csuci.tweeter.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val FIRST_POST_ID = "first"
private const val SECOND_POST_ID = "second"

class ShowPostRequestTest {
    @Test
    fun send_replacesEarlierRequest() {
        val request = ShowPostRequest()
        request.send(FIRST_POST_ID)

        request.send(SECOND_POST_ID)

        assertEquals(SECOND_POST_ID, request.postId.value)
    }

    @Test
    fun clear_removesRequest() {
        val request = ShowPostRequest()
        request.send(FIRST_POST_ID)

        request.clear()

        assertNull(request.postId.value)
    }
}
