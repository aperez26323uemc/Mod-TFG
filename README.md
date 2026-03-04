# Mod-TFG

Monorepo con varios mods de NeoForge alrededor de **Assistance Drone**.

## Módulos

- `assistance-drone`: mod base con la entidad drone y su lógica principal.
- `farmer-drone`: addon de agricultura autónoma.
- `path_lighter_drone`: addon para iluminar rutas automáticamente.
- `pickup-dumper`: addon para vaciar inventario en contenedores cercanos.

## CI automática en cada push

Workflow de GitHub Actions que compila todos los módulos.
- Workflow: `.github/workflows/build-mods.yml`
- Acción por módulo: `./gradlew build`
- Java: Temurin 21
