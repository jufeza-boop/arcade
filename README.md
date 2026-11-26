# RetroArcade Offline (Android Nativo + LibGDX)

Aplicación nativa para Android de máquina recreativa arcade **100% offline**, con estética **neón retro / pixel art procedural generado por código (Zero-Asset Graphics)** y orientación vertical fija (`portrait`).

---

## 1. Características Principales

- **Offline Estricto:** Sin permisos de red (`INTERNET` no declarado), sin telemetría ni librerías externas de analítica.
- **Orientación Vertical:** Pantalla bloqueada en `portrait` con resolución lógica virtual fija mediante `FitViewport(720f, 1280f, camera)`.
- **Zero-Asset Graphics:** Renderizado visual procedural mediante `ShapeRenderer` y texturas en memoria (`Pixmap`), sin dependencias de imágenes externas `.png`/`.jpg` para arrancar.
- **Audio Procedural:** Síntesis PCM en memoria de ondas cuadradas y ruido chiptune para efectos de sonido retro.
- **Persistencia Local:** Registro del **Top 10** de puntuaciones por juego ordenado descendentemente, más ajustes de modo de control y volumen silenciado (`isMuted`).
- **Sistema Dual de Controles:** Soporte intercambiable entre:
  - `VIRTUAL_PAD`: Cruceta direccional y botones arcade A/B en el tercio inferior de la pantalla.
  - `TOUCH_GESTURES`: Gestos directos en pantalla (swipes, taps, drag continuo y apuntado de trayectoria).

---

## 2. Catálogo de Juegos

La máquina recreativa incluirá 6 juegos clásicos en modo infinito con dificultad progresiva:

1. **Tetris:** Tablero 10x20, 7 tetrominós con rotación SRS, preview de siguiente pieza y caída acelerada.
2. **Puzzle Bobble:** Cañón con puntero (-75° a +75°), rebote en paredes, eliminación por clústeres (≥3) y desprendimiento de burbujas huérfanas.
3. **Arkanoid:** Pala reflectora, física de bola con ángulo según zona de impacto y ladrillos destructibles.
4. **Space Invaders:** Formación de 5 filas de alienígenas con movimiento sincronizado, aceleración progresiva y búnkeres destructibles.
5. **Pac-Man:** Laberinto simétrico con túneles laterales, píldoras de poder, modo *frightened* y 4 fantasmas con IA diferenciada.
6. **Snake:** Rejilla 20x28, crecimiento continuo al comer, comida bonus y colisión contra paredes y cola propia.

---

## 3. Arquitectura del Código

El proyecto sigue una arquitectura desacoplada y orientada a **Test-Driven Development (TDD)**:

```text
app/src/main/java/com/retroarcade/
├── core/                  # Modelos y contratos base en Kotlin puro (sin dependencias Android ni LibGDX)
│   ├── GameId.kt          # Identificadores de los 6 juegos
│   ├── InputCommand.kt    # Abstracción de comandos de entrada (pad y gestos)
│   ├── GameEngine.kt      # Contrato de motor de juego y estado observable
│   ├── ControlMode.kt     # Modos VIRTUAL_PAD y TOUCH_GESTURES
│   ├── ScoreEntry.kt      # Modelo de puntuación con validación y ordenación natural
│   ├── ScoreRepository.kt # Interfaz para el Top 10 de récords
│   └── SettingsRepository.kt # Interfaz para los ajustes del juego
├── domain/                # Motores de simulación y lógica pura de cada juego (Kotlin puro)
├── data/                  # Implementaciones de almacenamiento y repositorios desacoplados
│   ├── KeyValueStorage.kt # Abstracción clave-valor e InMemoryKeyValueStorage para tests JVM
│   ├── LocalScoreRepository.kt # Persistencia Top 10 JSON (descarte a partir del 11.º puesto)
│   └── LocalSettingsRepository.kt # Persistencia de ControlMode e isMuted
├── presentation/          # Gestión de estado de UI y coordinación de navegación
└── gdx/                   # Integración con LibGDX y plataforma Android
    ├── RetroArcadeGame.kt # Game principal con FitViewport y pantalla de inicio
    ├── AndroidLauncher.kt # AndroidApplication (Activity única)
    └── GdxPreferencesStorage.kt # Adaptador de KeyValueStorage sobre Preferences de LibGDX
```

> [!IMPORTANT]
> **Regla de Oro Arquitectónica:**
> Las capas `core/` y `domain/` son **Kotlin puro**. Está estrictamente prohibido importar paquetes de `com.badlogic.gdx.*` o `android.*` en ellas. Toda la física, colisiones, puntuaciones y reglas de negocio deben ser 100% ejecutables y testeables en la JVM estándar.

---

## 4. Requisitos del Entorno

- **JDK:** Java 17 o superior (desarrollado y verificado con JDK 21).
- **Android SDK:** `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`.
- **Gradle:** Wrapper incluido (Gradle 9.4.1 con Android Gradle Plugin 9.2.1).

---

## 5. Instrucciones para Testing y Compilación

### 5.1. Ejecutar la Suite Completa de Tests Unitarios

Los tests unitarios se ejecutan sobre la JVM sin necesidad de emulador ni dispositivo físico:

- **En Windows (PowerShell / CMD):**
  ```powershell
  .\gradlew.bat testDebugUnitTest
  ```

- **En Linux / macOS:**
  ```bash
  ./gradlew testDebugUnitTest
  ```

### 5.2. Ejecutar Tests Específicos

Para ejecutar una clase de test concreta o filtrar métodos:

- **Ejecutar únicamente los tests de récords (`LocalScoreRepositoryTest`):**
  ```powershell
  .\gradlew.bat testDebugUnitTest --tests "com.retroarcade.data.LocalScoreRepositoryTest"
  ```

- **Ejecutar únicamente los tests de ajustes (`LocalSettingsRepositoryTest`):**
  ```powershell
  .\gradlew.bat testDebugUnitTest --tests "com.retroarcade.data.LocalSettingsRepositoryTest"
  ```

- **Ejecutar un método de test específico:**
  ```powershell
  .\gradlew.bat testDebugUnitTest --tests "com.retroarcade.data.LocalScoreRepositoryTest.testTop10CapWith12Scores"
  ```

### 5.3. Ver los Reportes de Tests

Tras la ejecución, Gradle genera un informe detallado con el resultado de cada prueba:

- **Informe HTML para navegador:**
  `app/build/reports/tests/testDebugUnitTest/index.html`
- **Resultados XML (para CI/CD):**
  `app/build/test-results/testDebugUnitTest/`

### 5.4. Verificación de Límites de Arquitectura

Para verificar que no se han introducido dependencias indebidas en `core/` ni `domain/`:

- **En PowerShell:**
  ```powershell
  Select-String -Path "app\src\main\java\com\retroarcade\core\*", "app\src\main\java\com\retroarcade\domain\*" -Pattern "com.badlogic.gdx", "android\."
  ```
  *(El comando no debe arrojar ninguna coincidencia).*

### 5.5. Compilación del APK de Depuración (Debug)

Para compilar la aplicación Android completa y generar el archivo APK:

- **En Windows:**
  ```powershell
  .\gradlew.bat assembleDebug
  ```

- **En Linux / macOS:**
  ```bash
  ./gradlew assembleDebug
  ```

- **Ubicación del APK generado:**
  `app/build/outputs/apk/debug/app-debug.apk`

---

## 6. Estado del Proyecto

| Fase / Bloque | Descripción | Estado |
|---|---|---|
| **Bloque 1A** | Configuración Gradle, Arquitectura Base, Persistencia Top 10 y Esqueleto LibGDX | :white_check_mark: Completado |
| **Bloque 1B** | Menú Principal Arcade, Selector de Juegos, Ajustes y High Scores Screen | :white_check_mark: Completado |
| **Fase 1 (Motor)** | TetrisEngine + TetrisRenderer (SRS, líneas, gravedad) | :white_check_mark: Completado |
| **Fase 2** | PuzzleBobbleEngine + PuzzleBobbleRenderer (trayectoria, BFS) | :white_check_mark: Completado |
| **Fase 3** | Arkanoid y Space Invaders | :white_check_mark: Completado |
| **Fase 4** | Pac-Man y Snake | :white_check_mark: Completado |
| **Fase 5** | Pulido Neón, ChiptuneSynth y Efectos Arcade | :black_square_button: Pendiente |
