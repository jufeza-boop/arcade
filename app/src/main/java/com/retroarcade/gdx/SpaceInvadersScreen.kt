package com.retroarcade.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.ScreenUtils
import com.retroarcade.core.ControlMode
import com.retroarcade.core.GameId
import com.retroarcade.core.GameStatus
import com.retroarcade.core.ScoreEntry
import com.retroarcade.domain.spaceinvaders.Alien
import com.retroarcade.domain.spaceinvaders.Bunker
import com.retroarcade.domain.spaceinvaders.SpaceInvadersEngine

class SpaceInvadersScreen(private val game: RetroArcadeGame) : ScreenAdapter() {

    private data class ExplosionParticle(
        val x: Float,
        val y: Float,
        var timer: Float,
        val color: Color
    )

    private val engine = SpaceInvadersEngine()
    private val audio = ChiptuneAudio(game.settingsRepository)
    private val touchPoint = Vector3()

    private var playerName = "JUGADOR"
    private var isHighScoreSaved = false
    private var blinkTimer = 0f
    private var hitFlashTimer = 0f

    private val explosions = mutableListOf<ExplosionParticle>()
    private var prevAliensAlive = 55
    private var prevLevel = 1

    // Punteros activos para multitouch en Virtual Pad (mover + disparar a la vez)
    private val activePointers = mutableMapOf<Int, ArcadeButton>()

    // ── Virtual Pad (FUEGO pulgar izquierdo, << / >> pulgar derecho) ────────
    private val btnFire = ArcadeButton(30f, 75f, 230f, 100f, "FUEGO", Color.RED) {
        if (engine.playerShoot()) audio.playShoot()
    }
    private val btnLeft = ArcadeButton(360f, 75f, 160f, 100f, "<<", Color.CYAN) {}
    private val btnRight = ArcadeButton(535f, 75f, 155f, 100f, ">>", Color.CYAN) {}

    // ── Pausa / Overlay ──────────────────────────────────────────────────────
    private val btnPause = ArcadeButton(610f, 1200f, 80f, 60f, "||", Color.YELLOW) {
        engine.togglePause()
    }
    private val btnResume = ArcadeButton(210f, 680f, 300f, 75f, "CONTINUAR", Color.GREEN) {
        engine.togglePause()
    }
    private val btnRestart = ArcadeButton(210f, 580f, 300f, 75f, "REINICIAR", Color.YELLOW) {
        resetGame()
    }
    private val btnQuit = ArcadeButton(210f, 480f, 300f, 75f, "SALIR AL MENU", Color.CYAN) {
        game.goToMainMenu()
    }
    private lateinit var btnEditName: ArcadeButton
    private lateinit var btnSaveScore: ArcadeButton
    private val btnRetry = ArcadeButton(140f, 320f, 440f, 75f, "REINTENTAR", Color.YELLOW) {
        resetGame()
    }
    private val btnGameOverMenu = ArcadeButton(140f, 220f, 440f, 75f, "SALIR AL MENU", Color.CYAN) {
        game.goToMainMenu()
    }

    private var pressedButton: ArcadeButton? = null
    private var playedGameOverSound = false

    private fun resetGame() {
        engine.reset()
        isHighScoreSaved = false
        explosions.clear()
        activePointers.clear()
        prevAliensAlive = engine.aliens.count { it.alive }
        prevLevel = engine.level
        hitFlashTimer = 0f
    }

    override fun show() {
        updateGameOverButtons()
        Gdx.input.inputProcessor = object : InputAdapter() {
            private val dragPointers = mutableSetOf<Int>()
            private val touchDownX = mutableMapOf<Int, Float>()
            private val movedInTouch = mutableMapOf<Int, Boolean>()

            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f)
                game.viewport.unproject(touchPoint)

                val hitBtn = getActiveButtons().find { it.contains(touchPoint.x, touchPoint.y) }
                if (hitBtn != null) {
                    activePointers[pointer] = hitBtn
                    pressedButton = hitBtn
                    // Respuesta inmediata al disparar en Virtual Pad
                    if (hitBtn === btnFire && engine.status == GameStatus.RUNNING) {
                        btnFire.onClick()
                    }
                    return true
                }

                if (engine.status == GameStatus.RUNNING &&
                    game.settingsRepository.getControlMode() == ControlMode.TOUCH_GESTURES
                ) {
                    dragPointers.add(pointer)
                    touchDownX[pointer] = touchPoint.x
                    movedInTouch[pointer] = false
                }
                return true
            }

            override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
                touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f)
                game.viewport.unproject(touchPoint)

                if (activePointers.containsKey(pointer)) {
                    val overBtn = getActiveButtons().find { it.contains(touchPoint.x, touchPoint.y) }
                    if (overBtn === btnLeft || overBtn === btnRight) {
                        activePointers[pointer] = overBtn
                        pressedButton = overBtn
                    } else if (overBtn == null) {
                        activePointers.remove(pointer)
                        pressedButton = null
                    }
                    return true
                }

                if (engine.status == GameStatus.RUNNING &&
                    game.settingsRepository.getControlMode() == ControlMode.TOUCH_GESTURES &&
                    dragPointers.contains(pointer)
                ) {
                    val startX = touchDownX[pointer] ?: touchPoint.x
                    if (kotlin.math.abs(touchPoint.x - startX) > 10f) {
                        movedInTouch[pointer] = true
                    }
                    engine.movePlayerTo(touchPoint.x)
                    return true
                }
                return false
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f)
                game.viewport.unproject(touchPoint)

                val btn = activePointers.remove(pointer)
                if (btn != null) {
                    // Los botones de movimiento y fuego ya actuaron de forma continua / en touchDown
                    if (btn !== btnLeft && btn !== btnRight && btn !== btnFire &&
                        btn.contains(touchPoint.x, touchPoint.y)
                    ) {
                        btn.onClick()
                    }
                    if (pressedButton === btn) pressedButton = null
                    return true
                }

                if (dragPointers.remove(pointer)) {
                    val moved = movedInTouch.remove(pointer) ?: false
                    touchDownX.remove(pointer)
                    if (!moved && engine.status == GameStatus.RUNNING) {
                        if (engine.playerShoot()) audio.playShoot()
                    }
                    return true
                }

                pressedButton = null
                return true
            }
        }
    }

    override fun hide() {
        Gdx.input.inputProcessor = null
        activePointers.clear()
    }

    private fun updateGameOverButtons() {
        btnEditName = ArcadeButton(140f, 540f, 440f, 75f, "NOMBRE: $playerName", Color.CYAN) {
            Gdx.input.getTextInput(object : Input.TextInputListener {
                override fun input(text: String) {
                    val clean = text.trim().uppercase().take(8)
                    if (clean.isNotEmpty()) {
                        playerName = clean
                        updateGameOverButtons()
                    }
                }
                override fun canceled() {}
            }, "ESCRIBE TU NOMBRE", playerName, "Maximo 8 caracteres")
        }
        btnSaveScore = ArcadeButton(140f, 440f, 440f, 75f, "GUARDAR RECORD", Color.GREEN) {
            if (!isHighScoreSaved && engine.score > 0) {
                game.scoreRepository.addScore(
                    GameId.SPACE_INVADERS,
                    ScoreEntry(initials = playerName, score = engine.score, level = engine.level)
                )
                isHighScoreSaved = true
                game.goToHighScores(GameId.SPACE_INVADERS)
            }
        }
    }

    private fun getActiveButtons(): List<ArcadeButton> {
        val list = mutableListOf<ArcadeButton>()
        when (engine.status) {
            GameStatus.RUNNING -> {
                list.add(btnPause)
                if (game.settingsRepository.getControlMode() == ControlMode.VIRTUAL_PAD) {
                    list.addAll(listOf(btnLeft, btnRight, btnFire))
                }
            }
            GameStatus.PAUSED -> list.addAll(listOf(btnResume, btnRestart, btnQuit))
            GameStatus.GAME_OVER -> {
                val isHighScore = game.scoreRepository.isHighScore(GameId.SPACE_INVADERS, engine.score)
                if (isHighScore && !isHighScoreSaved && engine.score > 0) {
                    list.add(btnEditName)
                    list.add(btnSaveScore)
                }
                list.addAll(listOf(btnRetry, btnGameOverMenu))
            }
        }
        return list
    }

    override fun render(delta: Float) {
        blinkTimer += delta
        if (hitFlashTimer > 0f) hitFlashTimer = (hitFlashTimer - delta).coerceAtLeast(0f)

        // Movimiento continuo al mantener pulsado << o >> en Virtual Pad
        if (engine.status == GameStatus.RUNNING &&
            game.settingsRepository.getControlMode() == ControlMode.VIRTUAL_PAD
        ) {
            val movingLeft = activePointers.values.any { it === btnLeft }
            val movingRight = activePointers.values.any { it === btnRight }
            if (movingLeft && !movingRight) {
                engine.movePlayerBy(-engine.playerSpeed * delta)
            } else if (movingRight && !movingLeft) {
                engine.movePlayerBy(engine.playerSpeed * delta)
            }
        }

        val aliensBefore = engine.aliens.filter { it.alive }.toList()
        val ufoBefore = engine.ufo
        val livesBefore = engine.lives

        engine.update(delta)

        // Detectar aliens destruidos o cambio de oleada para sonido y explosión
        val aliensAfterCount = engine.aliens.count { it.alive }
        if (engine.level > prevLevel) {
            audio.playClear()
            prevLevel = engine.level
        } else if (aliensAfterCount < prevAliensAlive && aliensAfterCount > 0) {
            audio.playRotate()
            for (a in aliensBefore) {
                if (!a.alive) {
                    explosions.add(ExplosionParticle(a.x, a.y, 0.25f, getAlienColor(a.row)))
                }
            }
        }
        prevAliensAlive = aliensAfterCount

        // Detectar OVNI destruido
        if (ufoBefore != null && engine.ufo == null && ufoBefore.x in 0f..720f) {
            audio.playClear()
            explosions.add(ExplosionParticle(ufoBefore.x, ufoBefore.y, 0.4f, Color.YELLOW))
        }

        // Detectar pérdida de vida
        if (engine.lives < livesBefore) {
            audio.playExplosion()
            hitFlashTimer = 0.25f
            explosions.add(ExplosionParticle(engine.playerX, engine.playerY + 18f, 0.45f, Color.RED))
        }

        // Actualizar partículas de explosión
        val expIter = explosions.iterator()
        while (expIter.hasNext()) {
            val e = expIter.next()
            e.timer -= delta
            if (e.timer <= 0f) expIter.remove()
        }

        if (engine.status == GameStatus.GAME_OVER && !playedGameOverSound) {
            audio.playGameOver()
            playedGameOverSound = true
            updateGameOverButtons()
        } else if (engine.status != GameStatus.GAME_OVER) {
            playedGameOverSound = false
        }

        if (hitFlashTimer > 0f) {
            ScreenUtils.clear(0.18f, 0.02f, 0.02f, 1f)
        } else {
            ScreenUtils.clear(0f, 0f, 0.04f, 1f)
        }

        game.viewport.apply()
        val r = game.shapeRenderer
        r.projectionMatrix = game.camera.combined
        r.begin(ShapeRenderer.ShapeType.Filled)

        drawHUD(r)
        drawGameBoard(r)
        if (engine.status == GameStatus.RUNNING) drawControls(r)

        when (engine.status) {
            GameStatus.PAUSED -> drawPauseOverlay(r)
            GameStatus.GAME_OVER -> drawGameOverOverlay(r)
            else -> {}
        }

        r.end()
    }

    // ── HUD ─────────────────────────────────────────────────────────────────
    private fun drawHUD(r: ShapeRenderer) {
        ArcadeUIHelper.drawNeonBox(r, 30f, 1150f, 240f, 95f, Color(0.03f, 0.05f, 0.12f, 0.95f), Color.GREEN, 3f)
        ArcadeFont.drawText(r, "SCORE", 50f, 1210f, 2.1f, Color.GREEN)
        ArcadeFont.drawText(r, engine.score.toString().padStart(6, '0'), 50f, 1168f, 3.0f, Color.WHITE)

        ArcadeUIHelper.drawNeonBox(r, 285f, 1150f, 160f, 95f, Color(0.03f, 0.05f, 0.12f, 0.95f), Color.YELLOW, 3f)
        ArcadeFont.drawText(r, "OLEADA ${engine.level}", 300f, 1210f, 2.0f, Color.YELLOW)
        val aliensLeft = engine.aliens.count { it.alive }
        ArcadeFont.drawText(r, "ENEMIGOS: $aliensLeft", 300f, 1168f, 1.9f, Color.LIGHT_GRAY)

        // Vidas
        for (i in 0 until engine.lives) {
            drawMiniShip(r, 480f + i * 42f, 1195f, 15f)
        }

        ArcadeUIHelper.drawButton(r, btnPause, btnPause == pressedButton, 2.8f)

        val highScore = game.scoreRepository.getScores(GameId.SPACE_INVADERS).firstOrNull()?.score ?: 0
        ArcadeFont.drawText(r, "RECORD: ${highScore.toString().padStart(6, '0')}", 35f, 1115f, 2.2f, Color.YELLOW)
    }

    // ── Tablero ──────────────────────────────────────────────────────────────
    private fun drawGameBoard(r: ShapeRenderer) {
        // Suelo neón
        r.color = Color(0.1f, 0.9f, 0.1f, 0.6f)
        r.rect(10f, 195f, 700f, 3f)

        // Búnkeres (solo si tienen HP > 0)
        for (bunker in engine.bunkers) {
            if (bunker.hp > 0) drawBunker(r, bunker)
        }

        // Aliens vivos con animación clásica de 2 frames
        val frame = engine.marchAnimFrame
        for (alien in engine.aliens) {
            if (alien.alive) drawAlien(r, alien, frame)
        }

        // OVNI bonus
        engine.ufo?.let { ufo ->
            val ufoColor = if ((blinkTimer % 0.3f) > 0.15f) Color.RED else Color(1f, 0.4f, 0f, 1f)
            r.color = ufoColor
            r.ellipse(ufo.x - 36f, ufo.y - 10f, 72f, 24f)
            r.color = Color(0.3f, 0.3f, 1f, 0.9f)
            r.ellipse(ufo.x - 20f, ufo.y, 40f, 18f)
            ArcadeFont.drawCenteredText(r, "UFO", ufo.x, ufo.y + 8f, 1.8f, Color.WHITE)
        }

        // Proyectiles del jugador (cian neón)
        for (proj in engine.playerProjectiles) {
            r.color = Color.CYAN
            r.rect(proj.x - 3f, proj.y, 6f, 20f)
            r.color = Color.WHITE
            r.rect(proj.x - 1f, proj.y + 2f, 2f, 16f)
        }

        // Proyectiles enemigos (rayo zigzag rojo/amarillo bien visible)
        val zigOffset = if ((blinkTimer % 0.16f) > 0.08f) 3f else -3f
        for (proj in engine.enemyProjectiles) {
            r.color = Color.YELLOW
            r.rect(proj.x - 2f + zigOffset, proj.y - 20f, 5f, 7f)
            r.color = Color.RED
            r.rect(proj.x - 2f - zigOffset, proj.y - 13f, 5f, 7f)
            r.color = Color.YELLOW
            r.rect(proj.x - 2f + zigOffset, proj.y - 6f, 5f, 6f)
        }

        // Explosiones neón
        for (exp in explosions) {
            val spread = (0.4f - exp.timer) * 65f + 8f
            r.color = exp.color
            r.rect(exp.x - spread, exp.y - 2f, 8f, 4f)
            r.rect(exp.x + spread - 8f, exp.y - 2f, 8f, 4f)
            r.rect(exp.x - 2f, exp.y - spread, 4f, 8f)
            r.rect(exp.x - 2f, exp.y + spread - 8f, 4f, 8f)
            r.rect(exp.x - spread * 0.7f, exp.y - spread * 0.7f, 6f, 6f)
            r.rect(exp.x + spread * 0.7f, exp.y + spread * 0.7f, 6f, 6f)
            r.rect(exp.x - spread * 0.7f, exp.y + spread * 0.7f, 6f, 6f)
            r.rect(exp.x + spread * 0.7f, exp.y - spread * 0.7f, 6f, 6f)
        }

        // Nave del jugador (parpadea durante la invulnerabilidad post-impacto)
        val showShip = !engine.isPlayerInvincible || ((blinkTimer % 0.16f) > 0.08f)
        if (showShip && engine.lives > 0) {
            drawPlayerShip(r, engine.playerX, engine.playerY, engine.playerWidth, engine.playerHeight)
        }
    }

    private fun getAlienColor(row: Int): Color = when (row) {
        0 -> Color(0.3f, 1f, 0.3f, 1f)
        1, 2 -> Color(0.3f, 0.75f, 1f, 1f)
        else -> Color(1f, 0.35f, 0.55f, 1f)
    }

    private fun drawAlien(r: ShapeRenderer, alien: Alien, frame: Int) {
        val bodyColor = getAlienColor(alien.row)
        val cx = alien.x
        val cy = alien.y
        val p = 4f // tamaño de píxel procedural

        r.color = bodyColor
        when (alien.row) {
            // Fila 0: Calamar clásico (Squid) - 2 frames
            0 -> {
                r.rect(cx - p, cy + 2 * p, 2 * p, p)
                r.rect(cx - 2 * p, cy + p, 4 * p, p)
                r.rect(cx - 3 * p, cy, 6 * p, p)
                r.rect(cx - 4 * p, cy - p, 8 * p, p)
                if (frame == 0) {
                    r.rect(cx - 3 * p, cy - 2 * p, p, p)
                    r.rect(cx - p, cy - 2 * p, 2 * p, p)
                    r.rect(cx + 2 * p, cy - 2 * p, p, p)
                    r.rect(cx - 4 * p, cy - 3 * p, p, p)
                    r.rect(cx + 3 * p, cy - 3 * p, p, p)
                } else {
                    r.rect(cx - 2 * p, cy - 2 * p, p, p)
                    r.rect(cx + p, cy - 2 * p, p, p)
                    r.rect(cx - 3 * p, cy - 3 * p, p, p)
                    r.rect(cx + 2 * p, cy - 3 * p, p, p)
                }
                r.color = Color.BLACK
                r.rect(cx - 2 * p, cy - p, p, p)
                r.rect(cx + p, cy - p, p, p)
            }
            // Filas 1-2: Cangrejo clásico (Crab) - 2 frames
            1, 2 -> {
                r.rect(cx - 3 * p, cy + 3 * p, p, p)
                r.rect(cx + 2 * p, cy + 3 * p, p, p)
                r.rect(cx - 2 * p, cy + 2 * p, p, p)
                r.rect(cx + p, cy + 2 * p, p, p)
                r.rect(cx - 3 * p, cy + p, 6 * p, p)
                r.rect(cx - 4 * p, cy, 8 * p, p)
                r.rect(cx - 4 * p, cy - p, 8 * p, p)
                if (frame == 0) {
                    r.rect(cx - 5 * p, cy, p, 2 * p)
                    r.rect(cx + 4 * p, cy, p, 2 * p)
                    r.rect(cx - 3 * p, cy - 2 * p, p, p)
                    r.rect(cx + 2 * p, cy - 2 * p, p, p)
                    r.rect(cx - 4 * p, cy - 3 * p, p, p)
                    r.rect(cx + 3 * p, cy - 3 * p, p, p)
                } else {
                    r.rect(cx - 5 * p, cy - 2 * p, p, 2 * p)
                    r.rect(cx + 4 * p, cy - 2 * p, p, 2 * p)
                    r.rect(cx - 2 * p, cy - 2 * p, p, p)
                    r.rect(cx + p, cy - 2 * p, p, p)
                    r.rect(cx - p, cy - 3 * p, 2 * p, p)
                }
                r.color = Color.BLACK
                r.rect(cx - 2 * p, cy, p, p)
                r.rect(cx + p, cy, p, p)
            }
            // Filas 3-4: Pulpo clásico (Octopus) - 2 frames
            else -> {
                r.rect(cx - 2 * p, cy + 2 * p, 4 * p, p)
                r.rect(cx - 4 * p, cy + p, 8 * p, p)
                r.rect(cx - 5 * p, cy, 10 * p, p)
                r.rect(cx - 5 * p, cy - p, 10 * p, p)
                if (frame == 0) {
                    r.rect(cx - 4 * p, cy - 2 * p, 2 * p, p)
                    r.rect(cx - p, cy - 2 * p, 2 * p, p)
                    r.rect(cx + 2 * p, cy - 2 * p, 2 * p, p)
                    r.rect(cx - 5 * p, cy - 3 * p, p, p)
                    r.rect(cx + 4 * p, cy - 3 * p, p, p)
                } else {
                    r.rect(cx - 3 * p, cy - 2 * p, 2 * p, p)
                    r.rect(cx + p, cy - 2 * p, 2 * p, p)
                    r.rect(cx - 4 * p, cy - 3 * p, p, p)
                    r.rect(cx - p, cy - 3 * p, 2 * p, p)
                    r.rect(cx + 3 * p, cy - 3 * p, p, p)
                }
                r.color = Color.BLACK
                r.rect(cx - 3 * p, cy, 2 * p, p)
                r.rect(cx + p, cy, 2 * p, p)
            }
        }
    }

    private fun drawPlayerShip(r: ShapeRenderer, cx: Float, cy: Float, w: Float, h: Float) {
        r.color = Color(0.2f, 0.6f, 1f, 1f)
        r.rect(cx - w / 2f, cy, w, h * 0.55f)
        r.color = Color(0.4f, 0.85f, 1f, 1f)
        r.rect(cx - w / 4f, cy + h * 0.5f, w / 2f, h * 0.45f)
        r.color = Color.CYAN
        r.rect(cx - 4f, cy + h * 0.85f, 8f, h * 0.45f)
    }

    private fun drawMiniShip(r: ShapeRenderer, cx: Float, cy: Float, size: Float) {
        r.color = Color(0.3f, 0.7f, 1f, 1f)
        r.rect(cx - size, cy - size * 0.4f, size * 2f, size * 0.8f)
        r.color = Color.CYAN
        r.rect(cx - 3f, cy + size * 0.4f, 6f, size * 0.5f)
    }

    private fun drawBunker(r: ShapeRenderer, bunker: Bunker) {
        val ratio = (bunker.hp.toFloat() / 12f).coerceIn(0f, 1f)
        val green = 0.25f + ratio * 0.75f
        val red = (1f - ratio) * 0.75f
        r.color = Color(red, green, 0.05f, 0.95f)

        val bx = bunker.x - 50f
        val by = bunker.y
        val bw = 100f
        val bh = 50f

        r.rect(bx, by, bw, bh)
        r.color = Color(0f, 0f, 0.04f, 1f)
        r.rect(bx + 25f, by, 50f, 20f)

        // Muescas visuales de daño según HP perdido
        val damage = 12 - bunker.hp
        if (damage >= 2) r.rect(bx + 15f, by + 35f, 14f, 15f)
        if (damage >= 4) r.rect(bx + 68f, by + 32f, 16f, 18f)
        if (damage >= 6) r.rect(bx + 42f, by + 26f, 16f, 24f)
        if (damage >= 8) r.rect(bx, by + 22f, 22f, 14f)
        if (damage >= 10) r.rect(bx + 78f, by + 18f, 22f, 16f)
    }

    // ── Controles ────────────────────────────────────────────────────────────
    private fun drawControls(r: ShapeRenderer) {
        if (game.settingsRepository.getControlMode() == ControlMode.VIRTUAL_PAD) {
            val leftPressed = activePointers.values.any { it === btnLeft }
            val rightPressed = activePointers.values.any { it === btnRight }
            val firePressed = activePointers.values.any { it === btnFire }
            ArcadeUIHelper.drawButton(r, btnLeft, leftPressed, 3.5f)
            ArcadeUIHelper.drawButton(r, btnRight, rightPressed, 3.5f)
            ArcadeUIHelper.drawButton(r, btnFire, firePressed, 3.0f)
        } else {
            ArcadeFont.drawCenteredText(r, "ARRASTRA: MOVER * TAP: FUEGO", 360f, 108f, 2.0f, Color.GRAY)
        }
    }

    // ── Overlays ─────────────────────────────────────────────────────────────
    private fun drawPauseOverlay(r: ShapeRenderer) {
        r.color = Color(0f, 0f, 0f, 0.75f)
        r.rect(0f, 0f, 720f, 1280f)
        ArcadeUIHelper.drawNeonBox(r, 150f, 430f, 420f, 420f, Color(0.04f, 0.06f, 0.14f, 0.98f), Color.YELLOW, 4f)
        ArcadeFont.drawCenteredText(r, "PAUSA", 360f, 790f, 4.5f, Color.YELLOW)
        ArcadeUIHelper.drawButton(r, btnResume, btnResume == pressedButton, 2.8f)
        ArcadeUIHelper.drawButton(r, btnRestart, btnRestart == pressedButton, 2.8f)
        ArcadeUIHelper.drawButton(r, btnQuit, btnQuit == pressedButton, 2.8f)
    }

    private fun drawGameOverOverlay(r: ShapeRenderer) {
        r.color = Color(0f, 0f, 0f, 0.85f)
        r.rect(0f, 0f, 720f, 1280f)

        val isHighScore = game.scoreRepository.isHighScore(GameId.SPACE_INVADERS, engine.score) && engine.score > 0
        val boxH = if (isHighScore && !isHighScoreSaved) 660f else 460f
        val boxY = if (isHighScore && !isHighScoreSaved) 190f else 350f

        ArcadeUIHelper.drawNeonBox(r, 100f, boxY, 520f, boxH, Color(0.04f, 0.06f, 0.14f, 0.98f), Color.RED, 4f)
        ArcadeFont.drawCenteredText(r, "FIN DE PARTIDA", 360f, boxY + boxH - 60f, 4.0f, Color.RED)
        ArcadeFont.drawCenteredText(r, "PUNTOS: ${engine.score.toString().padStart(6, '0')}", 360f, boxY + boxH - 120f, 2.8f, Color.YELLOW)
        ArcadeFont.drawCenteredText(r, "OLEADA: ${engine.level}", 360f, boxY + boxH - 165f, 2.4f, Color.WHITE)

        if (isHighScore && !isHighScoreSaved) {
            ArcadeFont.drawCenteredText(r, "- NUEVO RECORD TOP 10 -", 360f, boxY + boxH - 220f, 2.4f, Color.GREEN)
            ArcadeFont.drawCenteredText(r, "TOCA PARA EDITAR TU NOMBRE:", 360f, boxY + boxH - 260f, 1.9f, Color.LIGHT_GRAY)
            ArcadeUIHelper.drawButton(r, btnEditName, btnEditName == pressedButton, 2.8f)
            ArcadeUIHelper.drawButton(r, btnSaveScore, btnSaveScore == pressedButton, 2.8f)
        }

        ArcadeUIHelper.drawButton(r, btnRetry, btnRetry == pressedButton, 2.8f)
        ArcadeUIHelper.drawButton(r, btnGameOverMenu, btnGameOverMenu == pressedButton, 2.8f)
    }

    override fun dispose() {
        audio.dispose()
    }
}
