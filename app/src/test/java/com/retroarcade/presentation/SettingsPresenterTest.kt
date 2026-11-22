package com.retroarcade.presentation

import com.retroarcade.core.ControlMode
import com.retroarcade.data.InMemoryKeyValueStorage
import com.retroarcade.data.LocalSettingsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SettingsPresenterTest {

    private lateinit var settingsRepo: LocalSettingsRepository
    private lateinit var presenter: SettingsPresenter

    @BeforeEach
    fun setUp() {
        val storage = InMemoryKeyValueStorage()
        settingsRepo = LocalSettingsRepository(storage)
        presenter = SettingsPresenter(settingsRepo)
    }

    @Test
    @DisplayName("Alternar modo de control cambia y persiste correctamente")
    fun testToggleControlMode() {
        assertEquals(ControlMode.VIRTUAL_PAD, presenter.getControlMode())

        val newMode = presenter.toggleControlMode()
        assertEquals(ControlMode.TOUCH_GESTURES, newMode)
        assertEquals(ControlMode.TOUCH_GESTURES, presenter.getControlMode())
        assertEquals(ControlMode.TOUCH_GESTURES, settingsRepo.getControlMode())

        val toggledBack = presenter.toggleControlMode()
        assertEquals(ControlMode.VIRTUAL_PAD, toggledBack)
        assertEquals(ControlMode.VIRTUAL_PAD, presenter.getControlMode())
    }

    @Test
    @DisplayName("Alternar estado silenciado cambia y persiste correctamente")
    fun testToggleMuted() {
        assertFalse(presenter.isMuted())

        val muted = presenter.toggleMuted()
        assertTrue(muted)
        assertTrue(presenter.isMuted())
        assertTrue(settingsRepo.isMuted())

        val unmuted = presenter.toggleMuted()
        assertFalse(unmuted)
        assertFalse(presenter.isMuted())
    }
}
