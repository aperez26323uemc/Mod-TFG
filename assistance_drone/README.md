# Assistance Drone

Mod base de NeoForge que añade el drone de asistencia y sistemas comunes para addons.

## Funcionalidades principales

- Entidad drone con IA base.
- Ítems y menús del drone.
- Red y sincronización de estado.
- APIs internas (goals/registro) usadas por addons.

## Build local

```bash
cd assistance_drone
./gradlew build
```

## Relación con addons

Este módulo es la dependencia principal de:

- `farmer_drone`
- `path_lighter_drone`
- `pickup_dumper`
