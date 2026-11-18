package edu.csuci.tweeter.data

/**
 * Rows of a [PostTable] that a screen lists, in the order it lists them, such as the posts naming
 * one animal.
 *
 * Like the table, it's equal only to itself: a screen finds its rows once per table, so UI state
 * holding them compares them in constant time, without comparing rows or posts.
 *
 * @property table The table the rows are of.
 * @property rows The rows, each an index into [table].
 */
class PostRows(val table: PostTable, val rows: IntArray) {
    override fun toString(): String = "PostRows(table=$table, rows=${rows.contentToString()})"

    companion object {
        /** No rows, of a table of no posts. */
        val EMPTY: PostRows = PostRows(PostTable.EMPTY, IntArray(0))

        /** Returns every row of [table], in order. It's a pass over every post. */
        fun everyRowOf(table: PostTable): PostRows =
            PostRows(table, IntArray(table.size) { row -> row })
    }
}
