package com.retroarcade.data

import com.retroarcade.core.ControlMode
import com.retroarcade.core.SettingsRepository

class LocalSettingsRepository(
    private val storage: KeyValueStorage
) : SettingsRepository {

    companion object {
        private const val KEY_CONTROL_MODE = "setting_control_mode"
        private const val KEY_MUTED = "setting_muted"
    }

    override fun getControlMode(): ControlMode {
        val raw = storage.getString(KEY_CONTROL_MODE, ControlMode.VIRTUAL_PAD.name)
        return try {
            ControlMode.valueOf(raw)
        } catch (e: IllegalArgumentException) {
            ControlMode.VIRTUAL_PAD
        }
    }

    override fun setControlMode(mode: ControlMode) {
        storage.putString(KEY_CONTROL_MODE, mode.name)
    }

    override fun isMuted(): Boolean {
        return storage.getBoolean(KEY_MUTED, false)
    }

    override fun setMuted(muted: Boolean) {
        storage.putBoolean(KEY_MUTED, muted)
    }
}
