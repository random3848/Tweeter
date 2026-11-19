package edu.csuci.tweeter.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * A [NumberStore] that keeps its numbers in the app's shared preferences file [name].
 *
 * Reading the file and writing it are disk I/O, so both run on [ioDispatcher]. A write is done
 * when [putLongs] returns, so a background check that ends right after it doesn't lose it.
 *
 * @param context Any context; only its application context is kept.
 */
class SharedPreferencesNumberStore(
    context: Context,
    private val name: String,
    private val ioDispatcher: CoroutineDispatcher
) : NumberStore {
    private val appContext = context.applicationContext

    // Loaded from disk on first use, which is on ioDispatcher.
    private val preferences: SharedPreferences by lazy {
        appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    override suspend fun getLong(key: String): Long? = withContext(ioDispatcher) {
        if (preferences.contains(key)) preferences.getLong(key, 0L) else null
    }

    override suspend fun getLongsWithPrefix(prefix: String): Map<String, Long> =
        withContext(ioDispatcher) {
            buildMap {
                for ((key, value) in preferences.all) {
                    if (key.startsWith(prefix) && value is Long) {
                        put(key, value)
                    }
                }
            }
        }

    override suspend fun putLongs(numbers: Map<String, Long>) {
        withContext(ioDispatcher) {
            preferences.edit(commit = true) {
                for ((key, number) in numbers) {
                    putLong(key, number)
                }
            }
        }
    }

    override suspend fun removeLongs(keys: Collection<String>) {
        withContext(ioDispatcher) {
            preferences.edit(commit = true) {
                for (key in keys) {
                    remove(key)
                }
            }
        }
    }
}
