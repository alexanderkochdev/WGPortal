# WGPortal — Architecture & Development Guide

## Overview

**WGPortal** is a **lightweight** Minecraft Paper 1.21 server plugin (`dev.alexanderkoch.wgportal`) that extends WorldGuard regions with portal-linking capabilities — **no database, no inventory sync, zero bloat**.

## Architecture

### Core Components

```
┌─────────────────────────────────────────┐
│              WGPortal                    │
├─────────────────────────────────────────┤
│  WGPortal.java    ← Plugin + Flags +    │
│                     Events + Regions    │
│                     (single class)      │
├─────────────────────────────────────────┤
│  plugin.yml / config.yml                 │
└─────────────────────────────────────────┘
```

Everything lives in a single, focused class. No unnecessary abstractions.

### Data Flow

```
Player walks into WG region with `portal-enabled` + `portal-target` flags
  → WGPortal.onPlayerMove(PlayerMoveEvent)
  → Cooldown check (configurable, default 5s per player)
  → If on cooldown: player receives message with remaining seconds
  → BungeeCord "Connect" PluginMessage
  → Target server receives player
```

## Build System

- **Build tool**: Maven (`pom.xml`)
- **Java version**: 21
- **Group ID**: `dev.alexanderkoch`
- **Packaging**: Plain JAR (no shading needed — zero runtime dependencies)
- **Build command**: `mvn clean package`
- **Output**: `target/WGPortal-<version>.jar`

### Dependencies (all provided by server)

| Dependency | Version | Scope | Purpose |
|---|---|---|---|
| Paper API | 1.21-R0.1-SNAPSHOT | provided | Bukkit/Paper API |
| WorldGuard Bukkit | 7.0.11 | provided | Region & flag API |
| WorldEdit Bukkit | 7.3.6 | provided | Region operations |

## Development Workflow

1. **Branch**: Work on feature branches, merge to `main`
2. **Build**: `mvn clean package` (produces ~15 KB JAR)
3. **Version**: Update `<version>` in `pom.xml` before release
4. **Changelog**: Keep `CHANGELOG.md` up to date
5. **Commits**: Use [Conventional Commits](https://www.conventionalcommits.org/) format
6. **Release**: Tag `v<version>` on `main`, GitHub Actions builds and publishes

## CI/CD

GitHub Actions (`.github/workflows/build.yml`):
- **On push to `main`**: Builds the JAR, uploads as artifact
- **On tag `v*`**: Builds, creates GitHub Release with JAR attached

## Design Principles

1. **Single-responsibility**: The plugin does ONE thing — WorldGuard portals. Nothing else.
2. **Zero external runtime deps**: Paper API + WorldGuard + WorldEdit (all provided by server) — that's it.
3. **No persistent state**: No database, no files, no inventory sync. Pure event-driven portalling.
4. **Minimal footprint**: ~150 lines of code, ~15 KB JAR.
5. **Cooldown-only rate limiting**: A configurable per-player cooldown (default 5s) prevents accidental double-teleports.

## Configuration

```yaml
# config.yml
cooldown-seconds: 5            # Cooldown between portal teleports (seconds)
cooldown-message: "..."        # Message during cooldown (§b = aqua, %d = seconds)
```

## WorldGuard Custom Flags

| Flag | Type | Default | Description |
|---|---|---|---|
| `portal-target` | String | — | BungeeCord server name to send players to |
| `portal-world` | String | — | Target world name — teleports player to world spawn |
| `portal-coords` | String | — | Target coordinates (`x,y,z` or `x,y,z,yaw,pitch`) — teleports player to exact location |
| `portal-enabled` | StateFlag | ALLOW | Whether this region acts as a portal |

## Naming Conventions

| Element | Convention | Example |
|---|---|---|
| Package | `dev.alexanderkoch.wgportal` | `dev.alexanderkoch.wgportal` |
| Plugin class | `PascalCase` | `WGPortal` |
| Methods | `camelCase` | `onPlayerMove()`, `setupPortalRegions()` |
| Config keys | `kebab-case` | `portal-target`, `server-name` |
| Regions | `PascalCase` | `FarmPortal`, `NetherPortal` |
| Portal coordinates | `x,y,z` or `x,y,z,yaw,pitch` | `100,64,-200`, `100,64,-200,90,0` |
| BungeeCord targets | `kebab-case` | `lobby-farm`, `survival-nether` |
