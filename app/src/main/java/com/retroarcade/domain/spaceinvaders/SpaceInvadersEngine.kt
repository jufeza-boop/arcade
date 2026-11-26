package com.retroarcade.domain.spaceinvaders

import com.retroarcade.core.GameStatus
import kotlin.math.abs
import kotlin.random.Random

// ── Modelos de datos ──────────────────────────────────────────────────────────

/** Fila 0 = superior (30 pts), fila 4 = inferior (10 pts). */
data class Alien(
    val row: Int,       // 0-4
    val col: Int,       // 0-10
    var x: Float,
    var y: Float,
    var alive: Boolean = true
)

data class Projectile(
    var x: Float,
    var y: Float,
    val isPlayerShot: Boolean
)

data class Bunker(
    val x: Float,  // centro X
    val y: Float,  // Y base
    var hp: Int = 12
)

data class Ufo(
    var x: Float,
    var y: Float = 1180f,
    val movingRight: Boolean
)

// ── Motor principal ───────────────────────────────────────────────────────────

class SpaceInvadersEngine {

    // Constantes de viewport
    private val viewportW = 720f
    private val viewportH = 1280f

    // Constantes de formación
    private val ALIEN_ROWS = 5
    private val ALIEN_COLS = 11
    private val TOTAL_ALIENS = ALIEN_ROWS * ALIEN_COLS   // 55

    // Tamaños / márgenes
    private val ALIEN_W = 40f
    private val ALIEN_H = 32f
    private val ALIEN_SPACING_X = 44f
    private val ALIEN_SPACING_Y = 48f
    private val FORMATION_START_X = 65f   // X del centro de la columna 0
    private val FORMATION_START_Y = 1070f // Y del centro de la fila 0

    private val LATERAL_MARGIN = 40f

    private val BASE_SPEED = 20f          // px/s horizontal inicial en nivel 1

    // Jugador
    val playerY = 160f
    val playerWidth = 60f
    val playerHeight = 36f
    private val PLAYER_HALF_W = playerWidth / 2f
    private val PLAYER_STEP = 16f
    val playerSpeed = 400f                // px/s para movimiento continuo

    // Proyectiles
    private val PROJ_W = 6f
    private val PROJ_H = 20f
    private val PLAYER_PROJ_SPEED = 850f  // hacia arriba (+Y), ágil y preciso

    // Búnkeres
    private val BUNKER_W = 100f
    private val BUNKER_H = 50f

    // OVNI
    private val UFO_Y = 1180f
    private val UFO_SPEED = 220f
    private val UFO_MIN_INTERVAL = 25f
    private val UFO_MAX_INTERVAL = 35f
    private val UFO_POINTS = 150

    // Puntuación por fila
    private fun pointsForRow(row: Int, currentLevel: Int): Int {
        val base = when (row) {
            0 -> 30
            1, 2 -> 20
            else -> 10
        }
        return base * currentLevel
    }

    // ── Estado mutable ────────────────────────────────────────────────────────

    private val _aliens = mutableListOf<Alien>()
    val aliens: List<Alien> get() = _aliens

    var playerX: Float = viewportW / 2f
        private set

    private val _playerProjectiles = mutableListOf<Projectile>()
    val playerProjectiles: List<Projectile> get() = _playerProjectiles

    private val _enemyProjectiles = mutableListOf<Projectile>()
    val enemyProjectiles: List<Projectile> get() = _enemyProjectiles

    private val _bunkers = mutableListOf<Bunker>()
    val bunkers: List<Bunker> get() = _bunkers

    var ufo: Ufo? = null
        private set

    var score: Int = 0
        private set

    var level: Int = 1
        private set

    var lives: Int = 3
        private set

    var status: GameStatus = GameStatus.RUNNING
        private set

    var isFleetMovingRight: Boolean = true
        private set

    /** Frame de animación de marcha (0 o 1) para sprites clásicos de 2 pasos. */
    var marchAnimFrame: Int = 0
        private set

    val isPlayerInvincible: Boolean
        get() = playerInvincibleTimer > 0f

    // Internals de control
    private var fleetSpeed: Float = BASE_SPEED
    private var marchAnimTimer: Float = 0f
    private var enemyShootTimer: Float = 0f
    private var enemyShootInterval: Float = 1.25f
    private var ufoTimer: Float = randomUfoInterval()
    private var playerInvincibleTimer: Float = 0f  // breve invulnerabilidad tras impacto

    // ── Inicialización ────────────────────────────────────────────────────────

    init {
        buildFormation()
        buildBunkers()
    }

    private fun buildFormation() {
        _aliens.clear()
        for (row in 0 until ALIEN_ROWS) {
            for (col in 0 until ALIEN_COLS) {
                _aliens.add(
                    Alien(
                        row = row,
                        col = col,
                        x = FORMATION_START_X + col * ALIEN_SPACING_X,
                        y = FORMATION_START_Y - row * ALIEN_SPACING_Y
                    )
                )
            }
        }
        isFleetMovingRight = true
        fleetSpeed = BASE_SPEED + (level - 1) * 20f
    }

    private fun buildBunkers() {
        _bunkers.clear()
        listOf(120f, 310f, 500f).forEach { bx ->
            _bunkers.add(Bunker(x = bx, y = 280f))
        }
    }

    private fun randomUfoInterval() = UFO_MIN_INTERVAL + Random.nextFloat() * (UFO_MAX_INTERVAL - UFO_MIN_INTERVAL)

    // ── API pública ───────────────────────────────────────────────────────────

    fun movePlayerLeft() {
        movePlayerBy(-PLAYER_STEP)
    }

    fun movePlayerRight() {
        movePlayerBy(PLAYER_STEP)
    }

    fun movePlayerBy(dx: Float) {
        if (status != GameStatus.RUNNING) return
        val minX = PLAYER_HALF_W + LATERAL_MARGIN
        val maxX = viewportW - PLAYER_HALF_W - LATERAL_MARGIN
        playerX = (playerX + dx).coerceIn(minX, maxX)
    }

    fun movePlayerTo(targetX: Float) {
        if (status != GameStatus.RUNNING) return
        val minX = PLAYER_HALF_W + LATERAL_MARGIN
        val maxX = viewportW - PLAYER_HALF_W - LATERAL_MARGIN
        playerX = targetX.coerceIn(minX, maxX)
    }

    fun playerShoot(): Boolean {
        if (status != GameStatus.RUNNING) return false
        if (_playerProjectiles.isEmpty()) {
            _playerProjectiles.add(
                Projectile(x = playerX, y = playerY + playerHeight, isPlayerShot = true)
            )
            return true
        }
        return false
    }

    fun togglePause() {
        status = when (status) {
            GameStatus.RUNNING -> GameStatus.PAUSED
            GameStatus.PAUSED  -> GameStatus.RUNNING
            GameStatus.GAME_OVER -> GameStatus.GAME_OVER
        }
    }

    fun reset() {
        score = 0
        level = 1
        lives = 3
        status = GameStatus.RUNNING
        playerX = viewportW / 2f
        _playerProjectiles.clear()
        _enemyProjectiles.clear()
        ufo = null
        enemyShootTimer = 0f
        ufoTimer = randomUfoInterval()
        playerInvincibleTimer = 0f
        buildFormation()
        buildBunkers()
    }

    // ── Bucle principal ───────────────────────────────────────────────────────

    fun update(delta: Float) {
        if (status != GameStatus.RUNNING) return

        // 1. Mover proyectiles del jugador hacia arriba
        val playerProjIter = _playerProjectiles.iterator()
        while (playerProjIter.hasNext()) {
            val p = playerProjIter.next()
            p.y += PLAYER_PROJ_SPEED * delta
            if (p.y > viewportH) playerProjIter.remove()
        }

        // 2. Mover proyectiles enemigos hacia abajo (velocidad progresiva por nivel)
        val enemyProjSpeed = 150f + (level - 1) * 35f
        val enemyProjIter = _enemyProjectiles.iterator()
        while (enemyProjIter.hasNext()) {
            val p = enemyProjIter.next()
            p.y -= enemyProjSpeed * delta
            if (p.y < 0f) enemyProjIter.remove()
        }

        // 3. Mover bloque de aliens
        updateFleetMovement(delta)

        // 4. IA de disparo enemigo
        updateEnemyShoot(delta)

        // 5. Colisión proyectiles jugador vs aliens
        checkPlayerProjectilesVsAliens()

        // 6. Colisión proyectiles jugador vs proyectiles enemigos
        checkProjectilesVsProjectiles()

        // 7. Colisión proyectiles y aliens vs búnkeres
        checkProjectilesVsBunkers()
        checkAliensVsBunkers()

        // 8. Colisión proyectiles enemigos vs jugador
        checkEnemyProjectilesVsPlayer(delta)

        // 8. OVNI
        updateUfo(delta)

        // 9. Colisión proyectiles jugador vs OVNI
        checkPlayerProjectilesVsUfo()

        // 10. Victoria
        checkVictory()

        // 11. Derrota
        checkDefeat()
    }

    // ── Movimiento de la flota ────────────────────────────────────────────────

    private fun updateFleetMovement(delta: Float) {
        val aliensAlive = _aliens.count { it.alive }
        if (aliensAlive == 0) return

        // Velocidad aumenta conforme quedan menos aliens
        val speed = currentFleetSpeed(aliensAlive)

        // Alternar frame de animación de marcha proporcionalmente a la velocidad
        val stepInterval = (32f / speed).coerceIn(0.08f, 0.5f)
        marchAnimTimer += delta
        if (marchAnimTimer >= stepInterval) {
            marchAnimTimer = 0f
            marchAnimFrame = 1 - marchAnimFrame
        }

        val dx = if (isFleetMovingRight) speed * delta else -speed * delta

        // Calcular límites actuales de la flota
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        _aliens.filter { it.alive }.forEach { a ->
            val left  = a.x - ALIEN_W / 2f
            val right = a.x + ALIEN_W / 2f
            if (left  < minX) minX = left
            if (right > maxX) maxX = right
        }

        val wouldHitRight = isFleetMovingRight  && (maxX + dx > viewportW - LATERAL_MARGIN)
        val wouldHitLeft  = !isFleetMovingRight && (minX + dx < LATERAL_MARGIN)

        if (wouldHitRight || wouldHitLeft) {
            // Invertir dirección y descender (descenso progresivo según nivel)
            val descent = (8f + (level - 1) * 4f).coerceAtMost(22f)
            isFleetMovingRight = !isFleetMovingRight
            _aliens.forEach { it.y -= descent }
        } else {
            _aliens.forEach { it.x += dx }
        }
    }

    // ── IA de disparo enemigo ─────────────────────────────────────────────────

    private fun updateEnemyShoot(delta: Float) {
        val aliensAlive = _aliens.count { it.alive }
        if (aliensAlive == 0) return

        // Cadencia y límite de disparos progresivos por nivel
        val ratio = aliensAlive.toFloat() / TOTAL_ALIENS.toFloat()
        val baseInterval = (2.2f - (level - 1) * 0.35f).coerceAtLeast(0.75f)
        val dynamicInterval = (baseInterval * (0.65f + 0.35f * ratio)).coerceIn(0.45f, 2.2f)

        enemyShootTimer += delta
        if (enemyShootTimer < dynamicInterval) return
        enemyShootTimer = 0f

        val maxEnemyProj = level.coerceIn(1, 4)
        if (_enemyProjectiles.size >= maxEnemyProj) return

        // Obtener los aliens más bajos de cada columna viva
        val bottomAliens = (0 until ALIEN_COLS).mapNotNull { col ->
            _aliens.filter { it.alive && it.col == col }.minByOrNull { it.y }
        }
        if (bottomAliens.isEmpty()) return

        val shooter = bottomAliens.random()
        _enemyProjectiles.add(
            Projectile(x = shooter.x, y = shooter.y - ALIEN_H / 2f, isPlayerShot = false)
        )
    }

    // ── Colisiones ────────────────────────────────────────────────────────────

    private fun checkPlayerProjectilesVsAliens() {
        val projIter = _playerProjectiles.iterator()
        while (projIter.hasNext()) {
            val p = projIter.next()
            val hit = _aliens.firstOrNull { a ->
                a.alive &&
                abs(p.x - a.x) <= ALIEN_W / 2f + PROJ_W / 2f &&
                abs(p.y - a.y) <= ALIEN_H / 2f + PROJ_H / 2f
            }
            if (hit != null) {
                hit.alive = false
                score += pointsForRow(hit.row, level)
                projIter.remove()
            }
        }
    }

    private fun checkProjectilesVsBunkers() {
        fun checkList(projList: MutableList<Projectile>) {
            val iter = projList.iterator()
            while (iter.hasNext()) {
                val p = iter.next()
                val bunker = _bunkers.firstOrNull { b ->
                    b.hp > 0 &&
                    abs(p.x - b.x) <= BUNKER_W / 2f + PROJ_W / 2f &&
                    p.y >= b.y && p.y <= b.y + BUNKER_H
                }
                if (bunker != null) {
                    bunker.hp = (bunker.hp - 1).coerceAtLeast(0)
                    iter.remove()
                }
            }
        }
        checkList(_playerProjectiles)
        checkList(_enemyProjectiles)
    }

    private fun checkProjectilesVsProjectiles() {
        val pIter = _playerProjectiles.iterator()
        while (pIter.hasNext()) {
            val pp = pIter.next()
            val eIter = _enemyProjectiles.iterator()
            var hit = false
            while (eIter.hasNext()) {
                val ep = eIter.next()
                if (abs(pp.x - ep.x) <= PROJ_W * 1.5f &&
                    pp.y + PROJ_H >= ep.y - PROJ_H &&
                    pp.y <= ep.y
                ) {
                    eIter.remove()
                    hit = true
                    break
                }
            }
            if (hit) pIter.remove()
        }
    }

    private fun checkAliensVsBunkers() {
        for (bunker in _bunkers) {
            if (bunker.hp <= 0) continue
            val touched = _aliens.any { a ->
                a.alive &&
                abs(a.x - bunker.x) <= BUNKER_W / 2f + ALIEN_W / 2f &&
                (a.y - ALIEN_H / 2f) <= bunker.y + BUNKER_H &&
                (a.y + ALIEN_H / 2f) >= bunker.y
            }
            if (touched) {
                bunker.hp = 0
            }
        }
    }

    private fun checkEnemyProjectilesVsPlayer(delta: Float) {
        if (playerInvincibleTimer > 0f) {
            playerInvincibleTimer -= delta
            return
        }
        val iter = _enemyProjectiles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            // El proyectil enemigo ocupa verticalmente [p.y - PROJ_H, p.y]
            if (abs(p.x - playerX) <= playerWidth / 2f + PROJ_W / 2f &&
                p.y >= playerY &&
                (p.y - PROJ_H) <= playerY + playerHeight
            ) {
                lives--
                iter.remove()
                _enemyProjectiles.clear()
                _playerProjectiles.clear()
                playerInvincibleTimer = 2f  // 2 s de invulnerabilidad
                break
            }
        }
    }

    private fun checkPlayerProjectilesVsUfo() {
        val currentUfo = ufo ?: return
        val projIter = _playerProjectiles.iterator()
        while (projIter.hasNext()) {
            val p = projIter.next()
            if (abs(p.x - currentUfo.x) <= 40f && abs(p.y - currentUfo.y) <= 20f) {
                score += UFO_POINTS
                ufo = null
                projIter.remove()
                break
            }
        }
    }

    // ── OVNI ──────────────────────────────────────────────────────────────────

    private fun updateUfo(delta: Float) {
        val currentUfo = ufo
        if (currentUfo == null) {
            ufoTimer -= delta
            if (ufoTimer <= 0f) {
                val movingRight = Random.nextBoolean()
                ufo = Ufo(
                    x = if (movingRight) -40f else viewportW + 40f,
                    movingRight = movingRight
                )
                ufoTimer = randomUfoInterval()
            }
        } else {
            if (currentUfo.movingRight) {
                currentUfo.x += UFO_SPEED * delta
                if (currentUfo.x > viewportW + 40f) ufo = null
            } else {
                currentUfo.x -= UFO_SPEED * delta
                if (currentUfo.x < -40f) ufo = null
            }
        }
    }

    // ── Fin de partida / victoria ─────────────────────────────────────────────

    private fun checkVictory() {
        if (_aliens.none { it.alive }) {
            level++
            _playerProjectiles.clear()
            _enemyProjectiles.clear()
            ufo = null
            enemyShootTimer = 0f
            ufoTimer = randomUfoInterval()
            playerX = viewportW / 2f
            buildFormation()
            buildBunkers()
        }
    }

    private fun checkDefeat() {
        if (lives <= 0) {
            status = GameStatus.GAME_OVER
            return
        }
        // Aliens demasiado cerca del jugador
        val lowestAlienY = _aliens.filter { it.alive }.minOfOrNull { it.y } ?: Float.MAX_VALUE
        if (lowestAlienY < 320f) {
            status = GameStatus.GAME_OVER
        }
    }

    // ── Propiedades internas para tests ──────────────────────────────────────

    internal fun setLivesForTest(v: Int) { lives = v }
    internal fun setStatusForTest(s: GameStatus) { status = s }
    internal fun aliensForTest(): MutableList<Alien> = _aliens
    internal fun enemyProjectilesForTest(): MutableList<Projectile> = _enemyProjectiles
    internal fun playerProjectilesForTest(): MutableList<Projectile> = _playerProjectiles
    internal fun currentFleetSpeed(aliensAlive: Int): Float {
        val levelBase = BASE_SPEED + (level - 1) * 12f
        val perAlienInc = (1.2f + (level - 1) * 0.6f).coerceAtMost(4.5f)
        return levelBase + (TOTAL_ALIENS - aliensAlive) * perAlienInc
    }
}
