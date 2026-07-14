# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.2] - 2026-07-14

### Added
- **`portal-coords` flag**: Teleports players to specific coordinates (`x,y,z` or `x,y,z,yaw,pitch`) within the same world or a target world.
- **`portal-world` flag**: Teleports players to a world's spawn point on the same server.
- **Cross-server teleport positioning**: When `portal-target` is combined with `portal-world` and/or `portal-coords`, the plugin now forwards the target position to the destination server via BungeeCord `Forward` PluginMessage (`wgportal:teleport` channel). The player is teleported to the exact world/coordinates upon joining the target server.
- **`wgportal:teleport` PluginMessage channel**: Receives pending teleport data across servers for precise cross-server positioning.
- **`PendingTeleport` system**: Stores incoming teleport data per player UUID, applied on `PlayerJoinEvent`.

### Fixed
- `portal-world` and `portal-coords` flags were registered but completely non-functional — their values were never evaluated in the movement handler. Both flags now work as documented.

### Removed
- Hardcoded `"BungeeCord"` channel string — replaced with `BUNGEE_CHANNEL` constant.

## [1.0.0] - 2026-07-13

### Added
- WorldGuard custom flags: `portal-target`, `portal-world`, `portal-coords`, `portal-enabled`
- BungeeCord cross-server teleportation when walking into portal regions
- Configurable cooldown (default 5s) with editable cooldown message supporting `&`-color-codes
- Cooldown notification with remaining seconds displayed in aqua
- Maven build with Paper 1.21 + WorldGuard 7.0.11 API
- GitHub Actions CI/CD (build on push, release on tag)
- MIT license

[1.0.0]: https://github.com/alexanderkochdev/WGPortal/releases/tag/v1.0.0
