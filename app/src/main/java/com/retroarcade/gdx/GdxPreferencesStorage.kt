package com.retroarcade.gdx

import com.badlogic.gdx.Preferences
import com.retroarcade.data.KeyValueStorage

class GdxPreferencesStorage(
    private val preferences: Preferences
) : KeyValueStorage {

    override fun getString(key: String, defaultValue: String): String {
        return preferences.getString(key, defaultValue)
    }

    override fun putString(key: String, value: String) {
        preferences.putString(key, value)
        preferences.flush()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return preferences.getBoolean(key, defaultValue)
    }

    override fun putBoolean(key: String, value: Boolean) {
        preferences.putBoolean(key, value)
        preferences.flush()
    }

    override fun remove(key: String) {
        preferences.remove(key)
        preferences.flush()
    }

    override fun clear() {
        preferences.clear()
        preferences.flush()
    }
}
