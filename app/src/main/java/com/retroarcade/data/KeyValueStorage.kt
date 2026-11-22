package com.retroarcade.data

interface KeyValueStorage {
    fun getString(key: String, defaultValue: String = ""): String
    fun putString(key: String, value: String)
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
    fun clear()
}

class InMemoryKeyValueStorage : KeyValueStorage {
    private val stringMap = mutableMapOf<String, String>()
    private val boolMap = mutableMapOf<String, Boolean>()

    override fun getString(key: String, defaultValue: String): String {
        return stringMap[key] ?: defaultValue
    }

    override fun putString(key: String, value: String) {
        stringMap[key] = value
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return boolMap[key] ?: defaultValue
    }

    override fun putBoolean(key: String, value: Boolean) {
        boolMap[key] = value
    }

    override fun remove(key: String) {
        stringMap.remove(key)
        boolMap.remove(key)
    }

    override fun clear() {
        stringMap.clear()
        boolMap.clear()
    }
}
