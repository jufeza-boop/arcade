package com.retroarcade.presentation

import com.retroarcade.core.ControlMode
import com.retroarcade.core.SettingsRepository

class SettingsPresenter(
    private val settingsRepository: SettingsRepository
) {
    fun getControlMode(): ControlMode {
        return settingsRepository.getControlMode()
    }

    fun setControlMode(mode: ControlMode) {
        settingsRepository.setControlMode(mode)
    }

    fun toggleControlMode(): ControlMode {
        val newMode = if (getControlMode() == ControlMode.VIRTUAL_PAD) {
            ControlMode.TOUCH_GESTURES
        } else {
            ControlMode.VIRTUAL_PAD
        }
        setControlMode(newMode)
        return newMode
    }

    fun isMuted(): Boolean {
        return settingsRepository.isMuted()
    }

    fun setMuted(muted: Boolean) {
        settingsRepository.setMuted(muted)
    }

    fun toggleMuted(): Boolean {
        val newMuted = !isMuted()
        setMuted(newMuted)
        return newMuted
    }
}
