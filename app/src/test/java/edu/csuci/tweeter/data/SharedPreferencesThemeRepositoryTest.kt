package edu.csuci.tweeter.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedPreferencesThemeRepositoryTest {

    @Test
    fun darkMode_defaultIsNull() = runTest {
        val repository = SharedPreferencesThemeRepository(FakeSharedPreferences())

        assertNull(repository.darkMode.first())
    }

    @Test
    fun setDarkMode_savesAndEmits() = runTest {
        val sharedPreferences = FakeSharedPreferences()
        val repository = SharedPreferencesThemeRepository(sharedPreferences)

        repository.setDarkMode(true)
        assertEquals(true, repository.darkMode.first())
        assertEquals(true, sharedPreferences.getBoolean("dark_mode", false))

        repository.setDarkMode(false)
        assertEquals(false, repository.darkMode.first())
        assertEquals(false, sharedPreferences.getBoolean("dark_mode", true))

        repository.setDarkMode(null)
        assertNull(repository.darkMode.first())
        assertEquals(false, sharedPreferences.contains("dark_mode"))
    }
}

private class FakeSharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any>()

    override fun contains(key: String): Boolean = map.containsKey(key)

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        map[key] as? Boolean ?: defValue

    override fun edit(): SharedPreferences.Editor = FakeEditor(map)

    override fun getAll(): Map<String, *> = map

    override fun getString(key: String, defValue: String?): String? =
        map[key] as? String ?: defValue

    override fun getStringSet(key: String, defValue: Set<String>?): Set<String>? =
        map[key] as? Set<String> ?: defValue

    override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
    }
}

private class FakeEditor(private val map: MutableMap<String, Any>) : SharedPreferences.Editor {
    override fun putString(key: String, value: String?): SharedPreferences.Editor {
        if (value != null) map[key] = value else map.remove(key)
        return this
    }

    override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
        if (values != null) map[key] = values else map.remove(key)
        return this
    }

    override fun putInt(key: String, value: Int): SharedPreferences.Editor {
        map[key] = value
        return this
    }

    override fun putLong(key: String, value: Long): SharedPreferences.Editor {
        map[key] = value
        return this
    }

    override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
        map[key] = value
        return this
    }

    override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
        map[key] = value
        return this
    }

    override fun remove(key: String): SharedPreferences.Editor {
        map.remove(key)
        return this
    }

    override fun clear(): SharedPreferences.Editor {
        map.clear()
        return this
    }

    override fun commit(): Boolean = true

    override fun apply() {
    }
}
