# Mod-TFG

Monorepo con varios mods de NeoForge alrededor de **Assistance Drone**.

## Módulos

- `assistance-drone`: mod base con la entidad drone y su lógica principal.
- `farmer-drone`: addon de agricultura autónoma.
- `path_lighter_drone`: addon para iluminar rutas automáticamente.
- `pickup-dumper`: addon para vaciar inventario en contenedores cercanos.

## CI automática en cada push

Se añadió un workflow de GitHub Actions que compila todos los módulos en cada `push` y en `pull_request`:

- Workflow: `.github/workflows/build-mods.yml`
- Acción por módulo: `./gradlew build`
- Java: Temurin 21

Esto permite detectar rápido errores de compilación entre addons.

## Versionado automático

Sí, es posible automatizar el incremento de versiones. Sin embargo, en este repositorio **no se automatiza** por ahora por estas razones:

1. Puede publicar versiones sin validar compatibilidad real entre el mod base y sus addons.
2. Puede romper rangos de dependencias (`assistance_drone`) sin revisión humana.
3. En mods de juego suele convenir versionar con criterio semántico y pruebas manuales de gameplay.

Recomendación: mantener versionado manual (con changelog) y usar la CI solo para validar build.
