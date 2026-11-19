package edu.csuci.tweeter.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val PAGE_SIZE = 3

class PagedReadsTest {
    private val lastItemsAskedAfter = mutableListOf<Int?>()

    @Test
    fun readEveryPage_nothingToRead_isEmpty() = runTest {
        assertEquals(emptyList<Int>(), readEveryPage(PAGE_SIZE, pagesOf(emptyList())))
    }

    @Test
    fun readEveryPage_fewerThanOnePage_readsOnePage() = runTest {
        val items = listOf(1, 2)

        val read = readEveryPage(PAGE_SIZE, pagesOf(items))

        assertEquals(items, read)
        assertEquals(listOf<Int?>(null), lastItemsAskedAfter)
    }

    @Test
    fun readEveryPage_severalPages_readsEveryItemInOrder() = runTest {
        val items = (1..7).toList()

        assertEquals(items, readEveryPage(PAGE_SIZE, pagesOf(items)))
    }

    @Test
    fun readEveryPage_severalPages_startsEachAfterLastItemOfPageBefore() = runTest {
        readEveryPage(PAGE_SIZE, pagesOf((1..7).toList()))

        assertEquals(listOf(null, 3, 6), lastItemsAskedAfter)
    }

    @Test
    fun readEveryPage_onlyFullPages_endsAtEmptyPage() = runTest {
        val items = (1..6).toList()

        val read = readEveryPage(PAGE_SIZE, pagesOf(items))

        assertEquals(items, read)
        assertEquals(listOf(null, 3, 6), lastItemsAskedAfter)
    }

    // Reads [items] a page at a time, as a query with a cursor would, recording each cursor.
    private fun pagesOf(items: List<Int>): suspend (lastItem: Int?) -> List<Int> = { lastItem ->
        lastItemsAskedAfter += lastItem
        val start = if (lastItem == null) 0 else items.indexOf(lastItem) + 1
        items.drop(start).take(PAGE_SIZE)
    }
}
