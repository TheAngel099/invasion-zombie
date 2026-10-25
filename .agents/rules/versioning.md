---
trigger: always_on
description: Regla obligatoria de control de versiones
---

# Regla de Versiones (Versioning)

**OBLIGATORIO:** Cada vez que se realice una modificación, corrección de errores, refactorización o actualización general de código en este proyecto, el agente **DEBE** incrementar la versión del plugin en el archivo `build.gradle`.

- Por ejemplo, si la versión actual es `1.0.1`, la nueva debe ser `1.0.2` para correcciones menores, o `1.1.0` si es una característica grande.
- No es necesario editar `plugin.yml` manualmente porque lee la variable directamente de Gradle al compilar (`${project.version}`).
- **MUY IMPORTANTE:** Al compilar, el agente debe asegurarse de usar `gradle clean build` (o borrar manualmente versiones anteriores en `build/libs/`) para que el usuario siempre encuentre un único archivo `.jar` y no se confunda con versiones antiguas.
