# SPEC.md — RetroArcade Offline (Android Nativo + LibGDX)

## 1. Visión General y Restricciones Críticas
Aplicación nativa Android de máquina recreativa arcade **100% offline** con estética **neón retro / pixel art procedural dibujado por código**.
- **Sin conexión:** Prohibido declarar `INTERNET` en `AndroidManifest.xml`. Cero llamadas de red, analíticas o SDKs externos.
- **Orientación:** Bloqueada estrictamente en **Vertical (`portrait`)**.
- **Catálogo (6 juegos clásicos infinitos con dificultad progresiva):**
  1. **Tetris**
  2. **Puzzle Bobble**
  3. **Arkanoid**
  4. **Space Invaders**
  5. **Pac-Man**
  6. **Snake**

---

## 2. Stack Técnico y Arquitectura
- **Lenguaje:** Kotlin (JDK 17+).
- **SDK Android:** `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`.
- **Motor Gráfico y Game Loop:** **LibGDX** (última versión estable) sobre una única `AndroidApplication` gestionando estados mediante `Game` y `Screen` (`MainMenuScreen`, `GamePlayScreen`, `HighScoresScreen`, `SettingsScreen`).
- **Renderizado Procedural (Zero-Asset Graphics):**
  - Prohibido depender de sprites externos `.png` para arrancar.
  - Todo el arte (bloques, burbujas, naves, fantasmas, laberinto, tipografía arcade/HUD y efectos de partículas) se renderiza mediante `ShapeRenderer` (estilo neón vectorial con brillo/bordes) y/o texturas `Pixmap` generadas en tiempo de ejecución.
  - Resolución lógica virtual fija con `FitViewport(720f, 1280f, camera)` para escalar uniformemente en cualquier pantalla vertical sin deformación.
- **Audio Procedural:** Generador de efectos chiptune en memoria (síntesis PCM de ondas cuadradas/ruido blanco para rebote, disparo, línea completada, game over) con interruptor global Mute/Unmute.
- **Persistencia Local:** `Preferences` de LibGDX (respaldado por `SharedPreferences` en Android, formato JSON serializado con `kotlinx.serialization` o Gson) para máxima rapidez y cero boilerplate:
  - Top 10 puntuaciones por cada uno de los 6 juegos (iniciales de 3 letras, puntuación, nivel alcanzado, fecha).
  - Configuración persistente: modo de control global o por juego (`VIRTUAL_PAD` vs `TOUCH_GESTURES`), sonido activo/silenciado.

### Arquitectura Desacoplada (Obligatoria para TDD)
La lógica de cada juego **NO debe importar clases de `com.badlogic.gdx` (ni gráficos ni inputs)**.
```text
app/src/main/java/com/retroarcade/
├── core/                  # Motor base puro Kotlin (GameEngine, InputCommand, Collision2D, ScoreRepository)
├── domain/                # Lógica pura de cada juego (sin dependencias Android/LibGDX)
│   ├── tetris/            # TetrisEngine, Tetromino, BoardGrid
│   ├── puzzlebobble/      # PuzzleBobbleEngine, BubbleGrid, TrajectoryCalc
│   ├── arkanoid/          # ArkanoidEngine, BallPhysics, BrickLayout
│   ├── spaceinvaders/     # SpaceInvadersEngine, WaveFormation, Projectile
│   ├── pacman/            # PacmanEngine, MazeGraph, GhostAI
│   └── snake/             # SnakeEngine, GridSnake, FoodSpawner
├── data/                  # LocalScoreRepository, SettingsRepository
├── presentation/          # ViewModels / Presenters de estado de UI y selección
└── gdx/                   # Renderers LibGDX, FitViewport, VirtualArcadePad, GestureInputMapper, ChiptuneSynth
```

---

## 3. Esquema de Controles (Dual Configurable)
El jugador puede alternar en `SettingsScreen` (y desde el menú de pausa) entre:
1. **Controles Virtuales Arcade (`VIRTUAL_PAD`):** Zona inferior de la pantalla (30% inferior del `FitViewport`) simulando el panel de la recreativa con cruceta/botones direccionales y botones de acción `A` / `B` con feedback visual al pulsar.
2. **Gestos Táctiles Directos (`TOUCH_GESTURES`):** Interacción directa sobre el área de juego:
   - *Tetris:* Tap = rotar, Swipe izq/der = mover, Swipe abajo = caída rápida (*hard drop*).
   - *Puzzle Bobble:* Drag = apuntar trayectoria con línea guía punteada, Release = disparar burbuja.
   - *Arkanoid:* Drag horizontal = mover pala directamente a la coordenada X.
   - *Space Invaders:* Drag horizontal = mover nave + auto-disparo o Tap para disparar.
   - *Pac-Man / Snake:* Swipe en 4 direcciones (con umbral mínimo de píxeles para evitar falsos giros).

---

## 4. Especificación Funcional por Juego (Modo Arcade Infinito)
Todos los juegos comparten: HUD superior (Puntuación actual, Récord del juego, Nivel/Oleada, Vidas/Siguiente pieza), Pausa (`PauseOverlay`) y Pantalla de Game Over con entrada de iniciales si entra en el Top 10.

1. **Tetris:** Tablero estándar 10x20, 7 tetrominós con sistema de rotación SRS básico, previsualización de siguiente pieza, puntuación por 1/2/3/4 líneas simultáneas, aumento de velocidad de caída cada 10 líneas.
2. **Puzzle Bobble:** Cañón inferior con ángulo restringido (-75° a +75°), rejilla hexagonal de burbujas de colores, rebote en paredes laterales, detección de clústeres de ≥3 burbujas iguales (BFS/DFS) + caída por gravedad de burbujas desconectadas del techo, descenso periódico del techo cada X disparos.
3. **Arkanoid:** Pala inferior, bola con ángulo de rebote variable según punto de impacto en la pala, ladrillos de 1 a 3 golpes de resistencia, caída ocasional de cápsulas *power-up* (pala ancha, multibola, láser), regeneración de muros con mayor velocidad al limpiar pantalla.
4. **Space Invaders:** Formación de 5 filas de enemigos con movimiento horizontal sincronizado y descenso al tocar bordes, velocidad inversamente proporcional al número de enemigos vivos, 3 búnkeres destructibles por impactos, OVNI bonus superior periódico.
5. **Pac-Man:** Laberinto clásico simétrico con túneles laterales, puntos normales, 4 píldoras de poder (estado *Frightened* de fantasmas con puntuación encadenada 200/400/800/1600), 4 fantasmas con patrones diferenciados (persecución directa, emboscada, patrulla), incremento de velocidad por nivel.
6. **Snake:** Rejilla discreta 20x28, crecimiento continuo al comer, comida normal + fruta bonus temporal de alto valor, colisión contra paredes (o modo wrap configurable, por defecto paredes mortales), aumento gradual de ticks por segundo.

---

## 5. Plan de Implementación Incremental y TDD

**Regla de Oro para Antigravity:** Ninguna fase se considera completada si `./gradlew testDebugUnitTest` falla o si la lógica de dominio importa paquetes `com.badlogic.gdx.*` o `android.*`.

### Fase 1: Arquitectura Base, Menú, Persistencia y Tetris
- **Entregables:**
  - Configuración de proyecto Gradle (`minSdk 26`, `targetSdk 35`, LibGDX, JUnit5/MockK).
  - `FitViewport(720, 1280)`, `ChiptuneSynth` básico, `LocalScoreRepository` (Top 10).
  - `MainMenuScreen` con selector visual de los 6 juegos (mostrando icono procedural y High Score actual), `SettingsScreen` y `HighScoresScreen`.
  - `TetrisEngine` + `TetrisRenderer` + soporte dual de controles (`VIRTUAL_PAD` y `TOUCH_GESTURES`).
- **Criterios de Aceptación y Tests Unitarios:**
  - Tests de rotación de piezas, colisión con bordes/piezas asentadas, limpieza de líneas múltiples, cálculo de puntuación y detección de Game Over.
  - Persistencia verificada: guardar 12 puntuaciones mantiene estrictamente las 10 mayores ordenadas.

### Fase 2: Puzzle Bobble
- **Entregables:** `PuzzleBobbleEngine` + `PuzzleBobbleRenderer`.
- **Criterios de Aceptación y Tests Unitarios:**
  - Tests de cálculo trigonométrico de disparo y rebote en paredes laterales.
  - Tests de algoritmo BFS para eliminar grupos de ≥3 burbujas conectadas y detección de burbujas huérfanas descolgadas.

### Fase 3: Arkanoid y Space Invaders
- **Entregables:** `ArkanoidEngine` + `ArkanoidRenderer`; `SpaceInvadersEngine` + `SpaceInvadersRenderer`.
- **Criterios de Aceptación y Tests Unitarios:**
  - *Arkanoid:* Tests de reflexión de vector velocidad de bola, reducción de durabilidad de ladrillos y pérdida de vida al caer por el borde inferior.
  - *Space Invaders:* Tests de inversión de dirección de la flota al alcanzar límites laterales, impacto de proyectiles en búnkeres/enemigos y aumento de velocidad al reducir enemigos.

### Fase 4: Pac-Man y Snake
- **Entregables:** `PacmanEngine` + `PacmanRenderer`; `SnakeEngine` + `SnakeRenderer`.
- **Criterios de Aceptación y Tests Unitarios:**
  - *Pac-Man:* Tests de navegación en intersecciones del laberinto, teletransporte por túneles, transición a modo vulnerable al comer píldora de poder y detección de victoria de nivel.
  - *Snake:* Tests de desplazamiento en rejilla, prohibición de giro de 180° instantáneo sobre sí misma, crecimiento de cola y colisión con cuerpo propio/muros.

### Fase 5: Pulido Arcade, Efectos Neón y Verificación Final
- **Entregables:** Efectos de partículas en explosiones/líneas, efecto *scanlines* CRT opcional en pantalla, verificación de ciclo de vida Android (pausa automática en `onPause()` y liberación limpia de recursos en `dispose()`).
- **Criterios de Aceptación y Tests Unitarios:** Ejecución completa de toda la suite de tests unitarios (`./gradlew testDebugUnitTest`) en verde sin warnings críticos en `domain/` y validación manual de los 6 juegos en ambos modos de control (`VIRTUAL_PAD` y `TOUCH_GESTURES`) en modo avión (sin red).