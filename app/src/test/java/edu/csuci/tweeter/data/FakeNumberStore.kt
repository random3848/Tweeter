package edu.csuci.tweeter.data

/** A [NumberStore] for tests, holding its numbers in memory. */
class FakeNumberStore : NumberStore {
    private val numbers = HashMap<String, Long>()

    override suspend fun getLong(key: String): Long? = numbers[key]

    override suspend fun putLongs(numbers: Map<String, Long>) {
        this.numbers.putAll(numbers)
    }
}
