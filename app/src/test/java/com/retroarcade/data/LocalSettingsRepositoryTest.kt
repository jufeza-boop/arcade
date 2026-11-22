package com.retroarcade.data

import com.retroarcade.core.ControlMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LocalSettingsRepositoryTest {

    private lateinit var storage: InMemoryKeyValueStorage
    private lateinit var repository: LocalSettingsRepository

    @BeforeEach
    fun setUp() {
        storage = InMemoryKeyValueStorage()
        repository = LocalSettingsRepository(storage)
    }

    @Test
    @DisplayName("Valores por defecto de ajustes: VIRTUAL_PAD y sonido activado (no silenciado)")
    fun testDefaultSettings() {
        assertEquals(ControlMode.VIRTUAL_PAD, repository.getControlMode())
        assertFalse(repository.isMuted())
    }

    @Test
    @DisplayName("Persistencia y modificación de ControlMode")
    fun testControlModePersistence() {
        repository.setControlMode(ControlMode.TOUCH_GESTURES)
        assertEquals(ControlMode.TOUCH_GESTURES, repository.getControlMode())

        repository.setControlMode(ControlMode.VIRTUAL_PAD)
        assertEquals(ControlMode.VIRTUAL_PAD, repository.getControlMode())
    }

    @Test
    @DisplayName("Persistencia y modificación del estado isMuted")
    fun testMutedStatePersistence() {
        repository.setMuted(true)
        assertTrue(repository.isMuted())

        repository.setMuted(false)
        assertFalse(repository.isMuted())
    }

    @Test
    @DisplayName("Recuperación adecuada ante valor desconocido o corrupto en el almacenamiento")
    fun testFallbackOnCorruptedControlMode() {
        storage.putString("setting_control_mode", "UNKNOWN_MODE")
        assertEquals(ControlMode.VIRTUAL_PAD, repository.getControlMode())
    }
}
