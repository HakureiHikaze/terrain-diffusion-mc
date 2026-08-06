# Terrain Diffusion Next

<p align="center">
  <img src="site/assets/logo.svg" width="120" height="120" alt="Terrain Diffusion Next">
</p>

<p align="center">
  <a href="https://github.com/f1owkang/Terrain-Diffusion-Next/releases"><img src="https://img.shields.io/badge/Minecraft-26.x-brightgreen?style=flat-square" alt="Minecraft 26.x"></a>
  <a href="https://adoptium.net/"><img src="https://img.shields.io/badge/Java-25-orange?style=flat-square" alt="Java 25"></a>
  <a href="LICENSE.txt"><img src="https://img.shields.io/badge/License-MIT-yellow?style=flat-square" alt="License: MIT"></a>
  <a href="https://github.com/f1owkang/Terrain-Diffusion-Next/graphs/contributors"><img src="https://img.shields.io/github/contributors/f1owkang/Terrain-Diffusion-Next?style=flat-square" alt="Contributors"></a>
</p>

<p align="center">
  Diffusion-model terrain for Minecraft — AI-crafted mountains, rivers, coasts and ore veins. Every world is one of a kind.
</p>

<p align="center">
  <a href="https://github.com/f1owkang/Terrain-Diffusion-Next/releases">Download</a> · <a href="#quick-start">Quick start</a> · <a href="#server-deployment">Server deployment</a> · <a href="#configuration">Configuration</a> · <a href="#faq">FAQ</a> · <a href="README.md">中文版</a>
</p>

**Terrain Diffusion Next** is an independent fork of [terrain-diffusion-mc](https://github.com/xandergos/terrain-diffusion-mc) (Minecraft 26.x) integrating the [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion) (SIGGRAPH '26) diffusion-model terrain generator, extended with rivers, beaches, aquifers, ore veins and vanilla structures. Works on clients and dedicated servers (26.x).

## Features

| Feature | Description |
|---------|-------------|
| **AI terrain** | Three-stage diffusion models (coarse → latent → decoder) output seeded, reproducible elevation + climate |
| **River system** | Hybrid (default): D8 paths follow the terrain, carved below sea level; or pure noise (`rivers.mode=carver`) |
| **Beach biomes** | Coastline detection maps to `BEACH` / `SNOWY_BEACH` / `STONY_SHORE` |
| **Aquifers & veins** | Re-enabled aquifers and ore veins: cave water, lava layers, iron/copper veins |
| **Structure support** | Biome tags for the 3 custom biomes: villages, mineshafts, strongholds generate there |
| **Localization** | Simplified Chinese language file for the world type and settings screens |

## Quick start

1. Grab the jar for your version from [Releases](https://github.com/f1owkang/Terrain-Diffusion-Next/releases) into `mods/`
2. Launch the game (online once) — the AI models (~2.5 GB) auto-download and verify
3. Create a world → pick the "Terrain Diffusion Next" world type
4. Optional: Customize → World Scale (below)

**Requires Fabric + Fabric API.** The CPU build is slow unless you are on macOS:

| Build | Supports | Setup required |
|-------|----------|----------------|
| **Windows** (recommended) | Windows with any modern GPU | None |
| **CUDA** | NVIDIA GPUs | None (runtime libs auto-download, see [CUDA_INSTALL.md](CUDA_INSTALL.md)) |
| **CPU** | Everything else | None (CoreML on Apple Silicon) |

**Requirements**: Java 25 · 1.5 GB VRAM · 2.5 GB RAM

## Supported Minecraft versions

Targets Minecraft **26.x** (26.1 / 26.2 / 26.3); one jar covers the whole `>=26.1` range.

> For 1.20.1 / 1.21.1 / 1.21.11 (the last obfuscated releases) use the upstream 2.x builds.

## Server deployment

Works on dedicated 26.x servers; the CUDA build auto-downloads the CUDA 12 runtime libraries (no manual install):

- **Ready in ~15 s**: startup preloads skipped, terrain generates on demand
- **Recommended**: `max-tick-time=-1` in `server.properties` (no watchdog kills)
- **Scouting**: `/td-explore` starts the terrain browser (`http://127.0.0.1:19801`)

## World scale & exploration

**World Scale (per-world)**: click **Customize** when creating a world and set `World Scale` (integer `1..6`), saved with the save:

- Real-world meters per block (`scale=1` => `30m/block`, `scale=2` => `15m/block`)
- 2 is recommended; 1 is denser, 4-6 amplify the terrain
- Lower scales stress the GPU more, higher scales the CPU more

**Exploring**: run `/td-explore` for the terrain explorer web UI (`http://localhost:19801`) to scout continents, mountains, rivers and islands and mark coordinates. A land spawn near (0, 0) is found automatically.

**Finding cool terrain**: browse the elevation map with `/td-explore`, note the coordinates of peaks, canyons and archipelagos, then teleport there — new regions generate on demand, ~5 s each.

## Configuration

Edit `config/terrain-diffusion-next.properties` (auto-created on first launch, bilingual comments on every option):

```
inference.device=gpu           # Inference device: cpu / gpu / auto (GPU first, CPU fallback)
inference.offload_models=true  # Offload inactive models from VRAM
validate_model=true            # SHA-256 validation of downloaded model files
explorer.port=19801            # Port of the terrain explorer web UI (/td-explore)
tile_size=256                  # Terrain region side length in blocks (power of 2)
spawn_search.initial_size=16   # Spawn land-search region size near (0, 0)
spawn_search.max_size=128
rivers.enabled=true            # Enable the river system
rivers.mode=hybrid             # hybrid (terrain-following) or carver (pure noise)
worldgen.skip_initial_chunk_preload=true  # Skip startup preload, ready faster
download.mirrors=huggingface.co,hf-mirror.com  # Model mirrors, tried in order
```

## FAQ

- **DLL initialization routine failed** — needs Java 25+; the [latest Microsoft OpenJDK 25](https://learn.microsoft.com/en-us/java/openjdk/download) is known to work
- **LoadLibrary failed with error 126** (CUDA build only) — bad CUDA/cuDNN install; see [CUDA_INSTALL.md](CUDA_INSTALL.md)
- **Failed to load terrain-diffusion models** — out of memory; the models take ~2.5 GB, raise the allocation
- **Server killed by the watchdog at startup** — set `max-tick-time=-1` in `server.properties`
- **Tick lag after teleporting** — new terrain needs a few seconds of GPU inference; normal, recovers quickly
- **Finding cool terrain** — `/td-explore` shows elevation/climate per coordinate; analyze coarse map data to auto-locate peaks and canyons

**Issue still not resolved? [Raise it here](https://github.com/f1owkang/Terrain-Diffusion-Next/issues/new).**

## Building from source

See [BUILDING.md](BUILDING.md) for builds, DirectML onnxruntime, and mod developer notes.

## Contributors

This project is a fork of [terrain-diffusion-mc](https://github.com/xandergos/terrain-diffusion-mc). The Terrain Diffusion ecosystem is built by the following contributors (in no particular order):

<a href="https://github.com/xandergos"><img src="https://github.com/xandergos.png" width="50" height="50" alt="xandergos"></a>
<a href="https://github.com/AirRunner"><img src="https://github.com/AirRunner.png" width="50" height="50" alt="AirRunner"></a>
<a href="https://github.com/ThatDamnWittyWhizHard"><img src="https://github.com/ThatDamnWittyWhizHard.png" width="50" height="50" alt="ThatDamnWittyWhizHard"></a>
<a href="https://github.com/ayushsucksaf"><img src="https://github.com/ayushsucksaf.png" width="50" height="50" alt="ayushsucksaf"></a>
<a href="https://github.com/BillGoldenWater"><img src="https://github.com/BillGoldenWater.png" width="50" height="50" alt="BillGoldenWater"></a>
<a href="https://github.com/tlhr"><img src="https://github.com/tlhr.png" width="50" height="50" alt="tlhr"></a>
<a href="https://github.com/deforcy"><img src="https://github.com/deforcy.png" width="50" height="50" alt="deforcy"></a>
<a href="https://github.com/f1owkang"><img src="https://github.com/f1owkang.png" width="50" height="50" alt="f1owkang"></a>
<a href="https://github.com/deepseek-ai"><img src="https://github.com/deepseek-ai.png" width="50" height="50" alt="DeepSeek"></a>

### Upstream projects

- [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion) — the diffusion-model terrain generator (SIGGRAPH '26 / InfiniteDiffusion)
- [terrain-diffusion-mc](https://github.com/xandergos/terrain-diffusion-mc) — the upstream mod this project forks
- [infinite-tensor](https://github.com/xandergos/infinite-tensor) — the streaming tile tensor library
