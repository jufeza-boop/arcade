package com.retroarcade.core

interface SettingsRepository {
    fun getControlMode(): ControlMode
    fun setControlMode(mode: ControlMode)
    fun isMuted(): Boolean
    fun setMuted(muted: Boolean)
}
