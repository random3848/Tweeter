package edu.csuci.tweeter.data

import android.content.SharedPreferences
import edu.csuci.tweeter.ui.theme.ThemeChoice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedPreferencesThemeRepositoryTest {

    @Test
    fun themeChoice_nothingSaved_isLight() = runTest {
        val sharedPreferences = FakeSharedPreferences()
        val repository = SharedPreferencesThemeRepository(sharedPreferences)

        val choice = repository.themeChoice.first()

        assertEquals(ThemeChoice.LIGHT, choice)
    }

    @Test
    fun setThemeChoice_dark_isReadAfterRestart() = runTest {
        val sharedPreferences = FakeSharedPreferences()
        val writer = SharedPreferencesThemeRepository(sharedPreferences)
        writer.setThemeChoice(ThemeChoice.DARK)
        val reader = SharedPreferencesThemeRepository(sharedPreferences)

        val choice = reader.themeChoice.first()

        assertEquals(ThemeChoice.DARK, choice)
    }

    @Test
    fun setThemeChoice_light_isReadAfterRestart() = runTest {
        val sharedPreferences = FakeSharedPreferences()
        val writer = SharedPreferencesThemeRepository(sharedPreferences)
        writer.setThemeChoice(ThemeChoice.LIGHT)
        val reader = SharedPreferencesThemeRepository(sharedPreferences)

        val choice = reader.themeChoice.first()

        assertEquals(ThemeChoice.LIGHT, choice)
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
