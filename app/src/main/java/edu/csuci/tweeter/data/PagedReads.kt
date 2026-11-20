package edu.csuci.tweeter.data

/**
 * Every item a lookup matches, read with [readPageAfter] a page of at most [pageSize] at a time,
 * in the lookup's order: each page starts after the last item of the page before (null for the
 * first page), and a page shorter than [pageSize] is the last. When the items fill their pages
 * exactly, the last page read is empty.
 */
internal suspend fun <T> readEveryPage(
    pageSize: Int,
    readPageAfter: suspend (lastItem: T?) -> List<T>
): List<T> {
    val items = ArrayList<T>()
    var page = readPageAfter(null)
    items += page
    while (page.size == pageSize) {
        page = readPageAfter(page.last())
        items += page
    }
    return items
}
