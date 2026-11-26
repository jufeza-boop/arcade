package com.retroarcade.domain.spaceinvaders

import com.retroarcade.core.GameStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SpaceInvadersEngineTest {

    private lateinit var engine: SpaceInvadersEngine

    @BeforeEach
    fun setUp() {
        engine = SpaceInvadersEngine()
    }

    // ── 1. La flota se mueve a la derecha al inicio ───────────────────────────
    @Test
    fun fleetMovesRightInitially() {
        val initialX = engine.aliens.first().x
        engine.update(0.016f)
        assertTrue(engine.aliens.first().x > initialX,
            "El primer alien debería moverse hacia la derecha en el primer tick")
    }

    // ── 2. La flota invierte dirección al llegar al borde derecho ─────────────
    @Test
    fun fleetInvertsDirectionAtRightEdge() {
        // Forzar que la flota llegue al borde derecho actualizando con delta grande
        // La flota comienza en isFleetMovingRight = true
        assertTrue(engine.isFleetMovingRight)

        // Mover la flota hasta que invierta
        var invertedDetected = false
        repeat(500) {
            if (!engine.isFleetMovingRight) { invertedDetected = true; return@repeat }
            engine.update(0.1f)
        }
        assertTrue(invertedDetected || !engine.isFleetMovingRight,
            "La flota debe invertir la dirección al alcanzar el borde derecho")
    }

    // ── 3. Los aliens descienden al invertir la dirección ────────────────────
    @Test
    fun fleetDescendsWhenInvertingDirection() {
        val initialY = engine.aliens.first().y

        // Mover hasta que se produzca una inversión
        var inversions = 0
        var lastMovingRight = engine.isFleetMovingRight
        repeat(1000) {
            engine.update(0.1f)
            if (engine.isFleetMovingRight != lastMovingRight) {
                inversions++
                lastMovingRight = engine.isFleetMovingRight
                return@repeat
            }
        }

        assertTrue(inversions >= 1, "La flota debería haber invertido al menos una vez")
        // El alien más bajo (fila 4) debe estar más abajo que al inicio
        val minY = engine.aliens.minOf { it.y }
        assertTrue(minY < initialY,
            "Los aliens deben haber descendido tras invertir (minY=$minY < initialY=$initialY)")
    }

    // ── 4. El alien muere al ser alcanzado por un proyectil del jugador ───────
    @Test
    fun alienRemovedWhenHitByPlayerProjectile() {
        val target = engine.aliensForTest().first { it.alive }

        // Colocar proyectil exactamente sobre el alien
        engine.playerProjectilesForTest().add(
            Projectile(x = target.x, y = target.y, isPlayerShot = true)
        )

        engine.update(0.016f)

        assertFalse(target.alive, "El alien impactado debe pasar a alive=false")
    }

    // ── 5. El score aumenta al destruir un alien ──────────────────────────────
    @Test
    fun scoreIncreasesWhenAlienDestroyed() {
        val target = engine.aliensForTest().first { it.alive }
        engine.playerProjectilesForTest().add(
            Projectile(x = target.x, y = target.y, isPlayerShot = true)
        )

        engine.update(0.016f)

        assertTrue(engine.score > 0, "El score debe aumentar al destruir un alien")
    }

    // ── 6. Los aliens de la fila superior valen más puntos ───────────────────
    @Test
    fun topRowAliensWorthMorePoints() {
        // Destruir un alien de fila 0 (top)
        val engineA = SpaceInvadersEngine()
        val topAlien = engineA.aliensForTest().first { it.row == 0 && it.alive }
        engineA.playerProjectilesForTest().add(Projectile(x = topAlien.x, y = topAlien.y, isPlayerShot = true))
        engineA.update(0.016f)
        val scoreTop = engineA.score

        // Destruir un alien de fila 4 (bottom)
        val engineB = SpaceInvadersEngine()
        val bottomAlien = engineB.aliensForTest().first { it.row == 4 && it.alive }
        engineB.playerProjectilesForTest().add(Projectile(x = bottomAlien.x, y = bottomAlien.y, isPlayerShot = true))
        engineB.update(0.016f)
        val scoreBottom = engineB.score

        assertTrue(scoreTop > scoreBottom,
            "Aliens de fila 0 deben valer más que los de fila 4 (top=$scoreTop, bottom=$scoreBottom)")
    }

    // ── 7. El búnker pierde HP al recibir un impacto ─────────────────────────
    @Test
    fun bunkerHpDecreasesOnProjectileImpact() {
        val bunker = engine.bunkers.first()
        val initialHp = bunker.hp

        // Disparar un proyectil del jugador directo al búnker
        engine.playerProjectilesForTest().add(
            Projectile(x = bunker.x, y = bunker.y + 10f, isPlayerShot = true)
        )

        engine.update(0.016f)

        assertTrue(bunker.hp < initialHp,
            "El HP del búnker debe reducirse al recibir un impacto (inicial=$initialHp, actual=${bunker.hp})")
    }

    // ── 8. El jugador pierde una vida al ser alcanzado por proyectil enemigo ──
    @Test
    fun playerLosesLifeWhenHitByEnemyProjectile() {
        val initialLives = engine.lives

        // Colocar proyectil enemigo sobre el jugador
        engine.enemyProjectilesForTest().add(
            Projectile(x = engine.playerX, y = engine.playerY + 10f, isPlayerShot = false)
        )

        engine.update(0.016f)

        assertEquals(initialLives - 1, engine.lives,
            "El jugador debe perder 1 vida al recibir un disparo enemigo")
    }

    // ── 9. GAME_OVER cuando los aliens bajan demasiado ───────────────────────
    @Test
    fun gameOverWhenAliensReachBottom() {
        // Bajar todos los aliens por debajo del umbral Y=320f
        engine.aliensForTest().filter { it.alive }.forEach { it.y = 300f }

        engine.update(0.016f)

        assertEquals(GameStatus.GAME_OVER, engine.status,
            "El juego debe terminar cuando los aliens alcanzan Y < 320f")
    }

    // ── 10. GAME_OVER cuando no quedan vidas ────────────────────────────────
    @Test
    fun gameOverWhenNoLivesLeft() {
        // Dejar al jugador con 1 vida y dispararle
        engine.setLivesForTest(1)
        engine.enemyProjectilesForTest().add(
            Projectile(x = engine.playerX, y = engine.playerY + 10f, isPlayerShot = false)
        )

        engine.update(0.016f)

        assertEquals(GameStatus.GAME_OVER, engine.status,
            "El juego debe terminar cuando las vidas llegan a 0")
    }

    // ── 11. La velocidad de la flota aumenta con menos aliens ────────────────
    @Test
    fun speedIncreasesAsFewerAliensRemain() {
        val speedAll = engine.currentFleetSpeed(55)
        val speedFew = engine.currentFleetSpeed(10)
        assertTrue(speedFew > speedAll,
            "La velocidad debe ser mayor con pocos aliens (pocos=$speedFew, todos=$speedAll)")
    }

    // ── 12. El nivel sube cuando todos los aliens mueren ────────────────────
    @Test
    fun nextLevelWhenAllAliensDestroyed() {
        val initialLevel = engine.level

        // Matar a todos los aliens menos uno, luego matarlo con un proyectil
        val aliveAliens = engine.aliensForTest().filter { it.alive }
        aliveAliens.dropLast(1).forEach { it.alive = false }
        val lastAlien = aliveAliens.last()
        engine.playerProjectilesForTest().add(
            Projectile(x = lastAlien.x, y = lastAlien.y, isPlayerShot = true)
        )

        engine.update(0.016f)

        assertEquals(initialLevel + 1, engine.level,
            "El nivel debe incrementarse al destruir a todos los aliens")
    }

    // ── 13. El jugador no puede disparar si ya hay un proyectil en vuelo ─────
    @Test
    fun playerCannotShootIfProjectileInFlight() {
        // Primer disparo
        engine.playerShoot()
        assertEquals(1, engine.playerProjectiles.size, "Debe haber 1 proyectil tras el primer disparo")

        // Intento de segundo disparo
        engine.playerShoot()
        assertEquals(1, engine.playerProjectiles.size,
            "No debe haber un segundo proyectil mientras el primero sigue en vuelo")
    }

    // ── 14. La punta inferior del proyectil enemigo también mata al jugador ──
    @Test
    fun enemyProjectileTipKillsPlayer() {
        val initialLives = engine.lives
        // Proyectil cuya parte superior está por encima de la nave, pero su punta inferior (y - 20f) toca la nave
        val tipTouchingY = engine.playerY + engine.playerHeight + 10f
        engine.enemyProjectilesForTest().add(
            Projectile(x = engine.playerX, y = tipTouchingY, isPlayerShot = false)
        )

        engine.update(0.016f)

        assertEquals(initialLives - 1, engine.lives,
            "La punta del proyectil enemigo debe impactar y quitar una vida al jugador")
    }

    // ── 15. Proyectil del jugador intercepta proyectil enemigo ───────────────
    @Test
    fun playerAndEnemyProjectilesCollide() {
        engine.playerProjectilesForTest().add(Projectile(x = 300f, y = 500f, isPlayerShot = true))
        engine.enemyProjectilesForTest().add(Projectile(x = 300f, y = 525f, isPlayerShot = false))

        engine.update(0.016f)

        assertTrue(engine.playerProjectiles.isEmpty(), "El proyectil del jugador debe destruirse al chocar")
        assertTrue(engine.enemyProjectiles.isEmpty(), "El proyectil enemigo debe destruirse al ser interceptado")
    }

    // ── 16. Aliens destruyen búnkeres al descender sobre ellos ───────────────
    @Test
    fun aliensDestroyBunkersOnContact() {
        val bunker = engine.bunkers.first()
        val alien = engine.aliensForTest().first { it.alive }
        alien.x = bunker.x
        alien.y = bunker.y + 40f

        engine.update(0.016f)

        assertEquals(0, bunker.hp, "El búnker debe destruirse cuando un alien desciende sobre él")
    }
}
