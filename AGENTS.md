# WGPortal — Architecture & Development Guide

## Overview

**WGPortal** is a **lightweight** Minecraft Paper 1.21 server plugin (`dev.alexanderkoch.wgportal`) that extends WorldGuard regions with portal-linking capabilities — **no database, no inventory sync, zero bloat**.

## Architecture

### Core Components

```
┌─────────────────────────────────────────────┐
│              WGPortal (Bukkit)               │
├─────────────────────────────────────────────┤
│  WGPortal.java    ← Plugin + Flags +        │
│                     Events + Regions +      │
│                     PluginMessages          │
│                     (single class)          │
├─────────────────────────────────────────────┤
│  Channels: Velocity (out)                   │
│            wgportal:teleport (in/out)       │
│            wgportal:apply (in)              │
├─────────────────────────────────────────────┤
│  plugin.yml / config.yml                     │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│        WGPortalVelocity (Velocity)           │
├─────────────────────────────────────────────┤
│  WGPortalVelocity.java ← Bridge: receives   │
│                          teleport data from  │
│                          source server,      │
│                          forwards on         │
│                          ServerConnectedEvent│
├─────────────────────────────────────────────┤
│  Channels: wgportal:teleport (in)           │
│            wgportal:apply (out)             │
├─────────────────────────────────────────────┤
│  wgportal-velocity/ directory               │
└─────────────────────────────────────────────┘
```

Everything lives in a single, focused class. No unnecessary abstractions.

### PluginMessage Protocol

#### `wgportal:teleport` (Bukkit → Velocity)

Sent by the Bukkit source server to the Velocity proxy when a player enters a portal with `portal-target` + world/coords.

| Direction | Channel | Payload | Purpose |
|---|---|---|---|
| Bukkit Source → Velocity | `wgportal:teleport` (out) | `UUID` + `world` + `coords` | Send pending teleport data to Velocity |

Payload format (UTF strings, via DataOutputStream):
1. Player UUID
2. Target world name (`""` if empty)
3. Target coords (`""` if empty, `x,y,z` or `x,y,z,yaw,pitch` if set)

#### `wgportal:apply` (Velocity → Bukkit Target)

Sent by Velocity to the target server AFTER the player has connected (`ServerConnectedEvent`).

| Direction | Channel | Payload | Purpose |
|---|---|---|---|
| Velocity → Bukkit Target | `wgportal:apply` (via `serverConnection.sendPluginMessage`) | `world` + `coords` | Apply teleport on target server |

Payload format (UTF strings, via DataOutputStream):
1. Target world name (`""` if empty)
2. Target coords (`""` if empty, `x,y,z` or `x,y,z,yaw,pitch` if set)

### Data Flow

```
Player walks into WG region with `portal-enabled` + action flags
  → WGPortal.onPlayerMove(PlayerMoveEvent)
  → Cooldown check (configurable, default 5s per player)
  → If on cooldown: player receives message with remaining seconds
  │
  ├─ portal-target set
  │  │  If portal-world / portal-coords also set:
  │  │    → sendPendingTeleport(player, world, coords)
  │  │      → player.sendPluginMessage("wgportal:teleport") to Velocity
  │  │      → WGPortalVelocity stores PendingTeleport
  │  │  → Velocity Connect PluginMessage
  │  │  → Velocity moves player to target server
  │  │  → WGPortalVelocity.onServerConnected(ServerConnectedEvent)
  │  │    → serverConnection.sendPluginMessage("wgportal:apply", world+coords) to target server
  │  │    → WGPortal (Bukkit): onPluginMessageReceived → teleports immediately
  │
  ├─ portal-coords set (no portal-target)
  │  → Player.teleport(Location) — same server, exact coords
  │
  └─ portal-world set (no portal-target)
     → Player.teleport(World.spawnLocation) — same server, world spawn
```

## Build System

### WGPortal (Bukkit)

- **Build tool**: Maven (`pom.xml`)
- **Java version**: 21
- **Group ID**: `dev.alexanderkoch`
- **Packaging**: Plain JAR (no shading needed — zero runtime dependencies)
- **Build command**: `mvn clean package`
- **Output**: `target/WGPortal-<version>.jar`

#### Dependencies (all provided by server)

| Dependency | Version | Scope | Purpose |
|---|---|---|---|
| Paper API | 1.21-R0.1-SNAPSHOT | provided | Bukkit/Paper API |
| WorldGuard Bukkit | 7.0.11 | provided | Region & flag API |
| WorldEdit Bukkit | 7.3.6 | provided | Region operations |

### WGPortalVelocity (Velocity Proxy)

- **Build tool**: Maven (`pom.xml` in `wgportal-velocity/`)
- **Java version**: 21
- **Group ID**: `dev.alexanderkoch`
- **Packaging**: JAR via maven-shade-plugin
- **Build command**: `mvn clean package -f wgportal-velocity/pom.xml`
- **Output**: `wgportal-velocity/target/WGPortalVelocity-<version>.jar`

#### Dependency (provided by Velocity proxy)

| Dependency | Version | Scope | Purpose |
|---|---|---|---|
| Velocity API | 3.3.0-SNAPSHOT | provided | Velocity proxy API |

## Development Workflow

1. **Branch**: Work on feature branches, merge to `main`
2. **Build**: `mvn clean package` (produces ~15 KB JAR)
3. **Version**: Update `<version>` in `pom.xml` before release
4. **Changelog**: Keep `CHANGELOG.md` up to date
5. **Commits**: Use [Conventional Commits](https://www.conventionalcommits.org/) format
6. **Release**: Tag `v<version>` on `main`, GitHub Actions builds and publishes

## CI/CD

GitHub Actions (`.github/workflows/build.yml`):
- **On push to `main`**: Builds both the Bukkit JAR and the Velocity JAR, uploads both as artifacts
- **On tag `v*`**: Builds both, creates GitHub Release with both JARs attached

## Design Principles

1. **Single-responsibility**: The plugin does ONE thing — WorldGuard portals. Nothing else.
2. **Zero external runtime deps**: Paper API + WorldGuard + WorldEdit (all provided by server) — that's it.
3. **No persistent state**: No database, no files, no inventory sync. Pure event-driven portalling.
4. **Minimal footprint**: ~150 lines of code, ~15 KB JAR.
5. **Cooldown-only rate limiting**: A configurable per-player cooldown (default 5s) prevents accidental double-teleports.
6. **Velocity companion for cross-server positioning**: Reliable cross-server coordinate/world teleportation requires `WGPortalVelocity` on the proxy. Local-only teleports (same server) work without it.

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
