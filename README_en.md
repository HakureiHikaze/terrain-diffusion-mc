<div align="center">

<img src="site/assets/logo.svg" width="120" height="120" alt="Terrain Diffusion Next">

# 🌍 Terrain Diffusion Next

**Diffusion-model terrain for Minecraft** — AI-crafted mountains, rivers, coasts and ore veins. Every world is one of a kind.

[![Minecraft](https://img.shields.io/badge/Minecraft-26.x-brightgreen?style=flat-square&logo=minecraft)](https://github.com/f1owkang/Terrain-Diffusion-Next/releases)
[![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE.txt)
[![Contributors](https://img.shields.io/github/contributors/f1owkang/Terrain-Diffusion-Next?style=flat-square)](https://github.com/f1owkang/Terrain-Diffusion-Next/graphs/contributors)

**[Quick Start](#quick-start)** · **[Features](#features)** · **[Deployment](#deployment)** · **[Exploration](#exploration)** · **[Configuration](#configuration)** · **[FAQ](#faq)** · **[Building](#building)**

**English · [简体中文](README.md)**

</div>

---

**Terrain Diffusion Next** is an independent fork of [terrain-diffusion-mc](https://github.com/xandergos/terrain-diffusion-mc) (Minecraft **26.x**) integrating the [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion) (SIGGRAPH '26) diffusion-model terrain generator, extended with rivers, beaches, aquifers, ore veins and vanilla structures. It works on both clients and dedicated servers; the CUDA build auto-downloads its runtime libraries.

> [!NOTE]
> This is the English main document; Chinese version: [README.md](README.md). Build details: [BUILDING.md](BUILDING.md); CUDA troubleshooting: [CUDA_INSTALL.md](CUDA_INSTALL.md).

**Supported versions**: Minecraft **26.x** (26.1 / 26.2 / 26.3); one jar covers the whole `>=26.1` range.

> [!NOTE]
> For 1.20.1 / 1.21.1 / 1.21.11 (the last obfuscated releases) use the upstream 2.x builds.

---

## Quick Start

### 1. Install

1. Grab the jar for your version from [Releases](https://github.com/f1owkang/Terrain-Diffusion-Next/releases) and drop it into `mods/`
2. Launch the game (online once) — the AI models (~2.5 GB) auto-download and verify
3. Create a world → pick the "Terrain Diffusion Next" world type
4. Optional: click **Customize** to set the World Scale (see [Exploration](#exploration))

> [!IMPORTANT]
> Requires **Fabric + Fabric API**. The CPU build is slow (except macOS); a GPU build is recommended.

### 2. Pick a build

| Build | Supports | Setup required |
|-------|----------|----------------|
| **Windows** (recommended) | Windows with any modern GPU | None |
| **CUDA** | NVIDIA GPUs | None (runtime libs auto-download, see [CUDA_INSTALL.md](CUDA_INSTALL.md)) |
| **CPU** | Everything else | None (CoreML on Apple Silicon) |

### 3. Requirements

| Item | Requirement |
|------|-------------|
| Java | **25** |
| VRAM | 1.5 GB |
| RAM | 2.5 GB (raise the Minecraft allocation if needed) |

---

## Features

| Feature | Description |
|---------|-------------|
| **AI terrain** | Three-stage diffusion models (coarse → latent → decoder) stream tiles and output seeded, reproducible elevation + climate |
| **River system** | Hybrid (default): D8 paths follow the terrain (halo window for seamless tiles), carved below sea level to hold water; or pure-noise (`rivers.mode=carver`) |
| **Beach biomes** | Coastline detection (land adjacent to ocean near sea level) maps to `BEACH` / `SNOWY_BEACH` / `STONY_SHORE` |
| **Aquifers & veins** | Re-enabled aquifers and ore veins with restored noise-router channels: cave water, lava layers, iron/copper veins |
| **Structure support** | Biome tags for the 3 custom biomes: villages, mineshafts, strongholds generate there |
| **Localization** | Simplified Chinese language file for the world type and settings screens |

---

## Deployment

Works on dedicated 26.x servers; the CUDA build auto-downloads the CUDA 12 runtime libraries (no manual install):

- **Ready in ~15 s**: startup preload skipped, terrain generates on demand
- **Recommended**: `max-tick-time=-1` in `server.properties` (no watchdog kills)
- **Preload chunks**: `/td-preload <radius>` generates surrounding chunks in the background
- **Scouting**: `/td-explore` starts the terrain browser on the server (`http://127.0.0.1:19801`)

> [!WARNING]
> If the server has **no NVIDIA driver** (`libcuda.so.1` missing), the CUDA build silently falls back to CPU (slow). On a GPU-less host use the **CPU build** instead (much smaller, ~50 MB), see [CUDA_INSTALL.md](CUDA_INSTALL.md).

---

## Exploration

**World Scale (per-world)**: click **Customize** when creating a world and set `World Scale` (integer `1..6`), saved with the save:

- Real-world meters per block (`scale=1` => `30m/block`, `scale=2` => `15m/block`)
- **2** is the recommended balance; **1** is denser and more compact, **4-6** amplify the terrain
- Lower scales stress the GPU more, higher scales the CPU more

**Exploring**: run `/td-explore` for the terrain explorer web UI (`http://localhost:19801`) to scout continents, mountains, rivers and islands and mark coordinates. A land spawn near (0, 0) is found automatically.

> [!TIP]
> Finding cool terrain: browse the elevation/climate map with `/td-explore`, note the coordinates of peaks, canyons and archipelagos, then teleport there. New regions generate on demand, ~5 s each.

---

## Configuration

Edit `config/terrain-diffusion-next.properties` (auto-created on first launch, bilingual comments on every option):

```properties
inference.device=gpu                       # Inference device: cpu / gpu / auto
inference.offload_models=true              # Offload inactive models from VRAM
validate_model=true                        # SHA-256 validation of downloaded model files
explorer.port=19801                        # Terrain explorer web UI port (/td-explore)
tile_size=256                              # Terrain region side length (blocks), power of 2
spawn_search.initial_size=16               # Spawn land-search initial region (coarse px)
spawn_search.max_size=128                  # Spawn land-search max region (coarse px)
rivers.enabled=true                        # Enable the river system
rivers.mode=hybrid                         # hybrid (terrain-following) or carver (pure noise)
worldgen.skip_initial_chunk_preload=true   # Skip startup preload, ready faster
download.mirrors=huggingface.co,hf-mirror.com  # Model mirrors, tried in order
```

---

## FAQ

<details>
<summary><b>DLL initialization routine failed</b></summary>

Needs Java 25+; the [latest Microsoft OpenJDK 25](https://learn.microsoft.com/en-us/java/openjdk/download) is known to work.
</details>

<details>
<summary><b>LoadLibrary failed with error 126</b> (CUDA build only)</summary>

Bad CUDA/cuDNN install; see [CUDA_INSTALL.md](CUDA_INSTALL.md).
</details>

<details>
<summary><b>Failed to load terrain-diffusion models</b></summary>

Out of memory. The models take ~2.5 GB; raise the Minecraft allocation.
</details>

<details>
<summary><b>Server killed by the watchdog at startup</b></summary>

Set `max-tick-time=-1` in `server.properties`.
</details>

<details>
<summary><b>Tick lag after teleporting</b></summary>

New terrain needs a few seconds of GPU inference; normal, recovers quickly.
</details>

<details>
<summary><b>Finding cool terrain</b></summary>

Run `/td-explore` to view elevation/climate per coordinate, or analyze the coarse map data to auto-locate peaks and canyons.
</details>

> Issue still not resolved? [Raise it here](https://github.com/f1owkang/Terrain-Diffusion-Next/issues/new).

---

## Building

See [BUILDING.md](BUILDING.md) for builds, DirectML onnxruntime, and mod developer notes.

---

## Contributors

This project is a fork of [terrain-diffusion-mc](https://github.com/xandergos/terrain-diffusion-mc). The Terrain Diffusion ecosystem is built by the following contributors (in no particular order):

<div align="center">

<a href="https://github.com/xandergos"><img src="https://github.com/xandergos.png" width="50" height="50" alt="xandergos"></a>
<a href="https://github.com/AirRunner"><img src="https://github.com/AirRunner.png" width="50" height="50" alt="AirRunner"></a>
<a href="https://github.com/ThatDamnWittyWhizHard"><img src="https://github.com/ThatDamnWittyWhizHard.png" width="50" height="50" alt="ThatDamnWittyWhizHard"></a>
<a href="https://github.com/ayushsucksaf"><img src="https://github.com/ayushsucksaf.png" width="50" height="50" alt="ayushsucksaf"></a>
<a href="https://github.com/BillGoldenWater"><img src="https://github.com/BillGoldenWater.png" width="50" height="50" alt="BillGoldenWater"></a>
<a href="https://github.com/tlhr"><img src="https://github.com/tlhr.png" width="50" height="50" alt="tlhr"></a>
<a href="https://github.com/deforcy"><img src="https://github.com/deforcy.png" width="50" height="50" alt="deforcy"></a>
<a href="https://github.com/f1owkang"><img src="https://github.com/f1owkang.png" width="50" height="50" alt="f1owkang"></a>
<a href="https://github.com/deepseek-ai"><img src="https://github.com/deepseek-ai.png" width="50" height="50" alt="DeepSeek"></a>

</div>

### Upstream projects

- [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion) — the diffusion-model terrain generator (SIGGRAPH '26 / InfiniteDiffusion)
- [terrain-diffusion-mc](https://github.com/xandergos/terrain-diffusion-mc) — the upstream mod this project forks
- [infinite-tensor](https://github.com/xandergos/infinite-tensor) — the streaming tile tensor library

---

<div align="center">

Made with ❤️ by the [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion) community · [MIT](LICENSE.txt)

</div>
