# WGPortal 🔀

> **Lightweight WorldGuard portal linking for Minecraft Paper networks — powered by WorldGuard regions.**

[![Version](https://img.shields.io/badge/version-1.0.0-blue)](https://github.com/alexanderkochdev/WGPortal/releases)
[![Paper](https://img.shields.io/badge/Paper-1.21–1.22+-green?logo=minecraft)](https://papermc.io)
[![WorldGuard](https://img.shields.io/badge/WorldGuard-7.0.11+-red)](https://enginehub.org/worldguard)
[![Java](https://img.shields.io/badge/Java-21+-orange?logo=java)](https://adoptium.net)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![Build](https://github.com/alexanderkochdev/WGPortal/actions/workflows/build.yml/badge.svg)](https://github.com/alexanderkochdev/WGPortal/actions/workflows/build.yml)

---

## ✨ Features

- **🔗 Portal Regions** — Mark any WorldGuard region as a portal with a single flag
- **🌐 Cross-Server Teleportation** — Send players to any BungeeCord server on region entry
- **📍 Precision Targeting** — Define target world and coordinates per portal region
- **⏱️ Cooldown System** — Configurable per-player cooldown prevents accidental double-teleports (default: 5s)
- **⚡ Zero Bloat** — Single class, ~150 lines, ~15 KB JAR
- **🚫 No Database** — Pure event-driven, no persistent state
- **🔌 1.21 & 1.22 Ready** — Tested on Paper 1.22, compatible with 1.21+
- **🎯 Drag & Drop** — Drop the JAR in `plugins/`, set flags, done

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                       BungeeCord Proxy                           │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              Plugin Messaging (BungeeCord)                │  │
│  │         "Connect" channel → target server name            │  │
│  └──────────────────────────┬────────────────────────────────┘  │
└─────────────────────────────┼────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│   Lobby Server  │  │  Survival Farm  │  │   Minigames     │
│   (Paper)       │  │   (Paper)       │  │   (Paper)       │
│                 │  │                 │  │                 │
│  WGPortal       │  │  WGPortal       │  │  WGPortal       │
│  ┌───────────┐  │  │  ┌───────────┐  │  │  ┌───────────┐  │
│  │ WG Flags: │  │  │  │ WG Flags: │  │  │  │ WG Flags: │  │
│  │ • portal- │  │  │  │ • portal- │  │  │  │ • portal- │  │
│  │   enabled │  │  │  │   enabled │  │  │  │   enabled │  │
│  │ • portal- │  │  │  │ • portal- │  │  │  │ • portal- │  │
│  │   target  │  │  │  │   target  │  │  │  │   target  │  │
│  │ • portal- │  │  │  │ • portal- │  │  │  │ • portal- │  │
│  │   world   │  │  │  │   world   │  │  │  │   world   │  │
│  │ • portal- │  │  │  │ • portal- │  │  │  │ • portal- │  │
│  │   coords  │  │  │  │   coords  │  │  │  │   coords  │  │
│  └───────────┘  │  │  └───────────┘  │  │  └───────────┘  │
│                 │  │                 │  │                 │
│  Regions:       │  │  Regions:       │  │  Regions:       │
│  • LobbyPortal  │  │  • FarmPortal   │  │  • KitPvP1      │
│  • SpawnPortal  │  │  • NetherPortal │  │  • BuildWorld   │
│                 │  │  • EndPortal    │  │                 │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

---

## 🚀 Installation

### Prerequisites

- **BungeeCord** or **Waterfall** (Proxy)
- **Paper** 1.21+ (Backend Servers)
- **WorldGuard** 7.0.11+ (on each backend server)
- **WorldEdit** 7.3.6+ (on each backend server)
- **Java** 21+

### Installation Steps

1. Download `WGPortal-1.0.0.jar`
2. Place it in **every backend server's** `plugins/` folder
3. Start the servers once to generate the default `config.yml`
4. Configure WorldGuard regions with portal flags (see below)
5. Restart or `/reload` — done!

---

## ⚙️ Configuration

### Plugin Config (`plugins/WGPortal/config.yml`)

```yaml
# Cooldown between portal teleports (seconds)
cooldown-seconds: 5

# Message shown to player during cooldown
# §b = aqua, %d = remaining seconds
cooldown-message: "§c⏳ Please wait §b%d §cseconds before using a portal again."
```

### WorldGuard Custom Flags

All flags are created automatically when the plugin loads. No manual flag registration needed.

| Flag | Type | Default | Description |
|---|---|---|---|
| `portal-enabled` | StateFlag | `ALLOW` | Whether this region acts as a portal |
| `portal-target` | String | — | BungeeCord server name to send players to |
| `portal-world` | String | — | Target world name (optional) |
| `portal-coords` | String | — | Target coordinates `x y z` (optional) |

### Example Setup

```bash
# Create a portal region
/rg define FarmPortal -w world
/rg flag FarmPortal portal-enabled allow
/rg flag FarmPortal portal-target survival-farm
/rg flag FarmPortal portal-world world
/rg flag FarmPortal portal-coords "0 67 0"

# Or just the minimum — target server only
/rg flag LobbyPortal portal-enabled allow
/rg flag LobbyPortal portal-target lobby
```

> **💡 Tip:** The `portal-world` and `portal-coords` flags are optional.  
> If omitted, the player connects to the target server's default spawn location.

---

## 🌐 Built for malimala.net

WGPortal is developed and used in production on **[malimala.net](https://malimala.net)** — a German Minecraft network where WorldGuard regions seamlessly link servers together.

> 🎮 **Join us at malimala.net!**  
> We offer Survival, Creative, Minigames, and more.  
> Our portals connect everything — thanks to WGPortal! 😉

---

## 🧑‍💻 Building from Source

```bash
# Clone
git clone https://github.com/alexanderkochdev/WGPortal.git
cd WGPortal

# Build
mvn clean package

# Output:
#   target/WGPortal-1.0.0.jar    ← drop this into plugins/
```

### 🤖 GitHub Actions

Every push to `main` or a `v*` tag automatically builds the plugin.  
Build artifacts are available in the workflow run.

![Build Status](https://github.com/alexanderkochdev/WGPortal/actions/workflows/build.yml/badge.svg)

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.

---

## 👨‍💻 Author

**Alexander Koch**
- 🌐 [alexanderkoch.dev](https://alexanderkoch.dev)
- 🎮 [malimala.net](https://malimala.net) — Minecraft Server
- 🐙 [GitHub @alexanderkochdev](https://github.com/alexanderkochdev)

> 💡 **Want to support the project?** Join [malimala.net](https://malimala.net) and give us a ⭐ on GitHub!
