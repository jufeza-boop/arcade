# AGENTS.md — Reglas Operativas para Antigravity

## 1. Fuente de Verdad y Alcance
- La especificación funcional y técnica del proyecto es **`SPEC.md`**. Revísala antes de modificar código, estructura de paquetes o dependencias.
- Trabaja **siempre en alcance acotado** a la fase o sub-bloque indicado en el prompt. No adelantes juegos ni funcionalidades de fases posteriores salvo que se pida explícitamente.
- Mantén todo el proyecto en **español** en documentación, comentarios relevantes y textos visibles de la UI arcade (salvo identificadores técnicos en código/Kotlin).

---

## 2. Restricciones No Negociables del Producto
- **Offline estricto:** Nunca añadas el permiso `INTERNET` en `AndroidManifest.xml` ni introduzcas llamadas de red, analíticas, telemetría o SDKs remotos.
- **Orientación vertical:** Mantén la orientación bloqueada en `portrait`. No habilites rotación apaisada (`landscape`).
- **Zero-Asset Graphics de partida:** No dependas de archivos `.png`/`.jpg` externos para renderizar juegos. Usa `ShapeRenderer` / `Pixmap` procedural y `FitViewport(720f, 1280f, camera)`.
- **Persistencia local:** Guarda únicamente puntuaciones (Top 10 por juego) y ajustes (`VIRTUAL_PAD` / `TOUCH_GESTURES`, sonido) en local.

---

## 3. Límites de Arquitectura (Crítico)
Respeta estrictamente la separación de capas definida en `SPEC.md`:
- **`core/` y `domain/<juego>/`:** Deben ser **Kotlin puro** (JVM/Android unit tests). **Prohibido importar `com.badlogic.gdx.*` o `android.*`** en estas capas. Toda simulación, colisiones, puntuación, estados de partida y reglas de juego viven aquí.
- **`data/`:** Persistencia local de récords y preferencias desacoplada de las pantallas de renderizado.
- **`presentation/`:** Estado de selección de juegos, ranking, ajustes y coordinación de casos de uso.
- **`gdx/`:** Adaptación a LibGDX (`Screen`, `FitViewport`, `ShapeRenderer`, `Pixmap`, `ChiptuneSynth`, `VirtualArcadePad`, `GestureInputMapper`). Traduce comandos de entrada a `InputCommand` y dibuja el estado del dominio.

---

## 4. Flujo de Trabajo y TDD
1. **Inspecciona primero:** Revisa el estado actual del repositorio y los archivos relevantes antes de editar.
2. **Lógica y tests primero:** Para cada motor de juego (`*Engine`) o repositorio, define primero el modelo/contrato y escribe tests unitarios en `app/src/test/` cubriendo reglas clave, bordes y condiciones de victoria/derrota/límite.
3. **Implementa en pequeño:** Escribe código modular y claro en `domain/` / `core/` / `data/`, y después conecta la capa `gdx/`.
4. **Evita ediciones masivas innecesarias:** No reescribas archivos completos si basta un cambio puntual; no toques código de otros juegos salvo que compartan infraestructura base (`core/`, `data/`, `gdx/`).

---

## 5. Verificación Obligatoria Antes de Cerrar un Paso
- Ejecuta siempre la suite de tests unitarios:
  ```bash
  ./gradlew testDebugUnitTest
  ```
- Verifica que la compilación de debug de Android no falle:
  ```bash
  ./gradlew assembleDebug
  ```
- Comprueba que no se han colado imports de `com.badlogic.gdx` ni `android` dentro de `core/` o `domain/`.
- Al finalizar una tarea, resume brevemente: qué archivos se crearon/modificaron, qué tests se añadieron o ejecutaron y si quedó algún punto pendiente o riesgo.