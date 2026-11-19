package edu.csuci.tweeter.data

/** A [NumberStore] for tests, holding its numbers in memory. */
class FakeNumberStore : NumberStore {
    private val numbers = HashMap<String, Long>()

    override suspend fun getLong(key: String): Long? = numbers[key]

    override suspend fun getLongsWithPrefix(prefix: String): Map<String, Long> =
        numbers.filterKeys { key -> key.startsWith(prefix) }

    override suspend fun putLongs(numbers: Map<String, Long>) {
        this.numbers.putAll(numbers)
    }

    override suspend fun removeLongs(keys: Collection<String>) {
        numbers.keys.removeAll(keys.toSet())
    }
}
