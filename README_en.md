# Terrain Diffusion Next

**Terrain Diffusion Next** is an independent fork of [terrain-diffusion-mc](https://github.com/xandergos/terrain-diffusion-mc) (targeting Minecraft 26.x) that integrates the [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion) (SIGGRAPH '26) diffusion-model terrain generator into Minecraft, and extends it with rivers, beaches, aquifers, ore veins and vanilla structure support.

> 中文版： [README.md](README.md)

## What's new compared to the upstream fork

| Feature | Description |
|---------|-------------|
| **River system** | Port of `RiverCarver` from upstream PR #207: deterministic noise-zero-contour channel carving (cut below sea level so channels actually hold water; O(1) random access, no tile-boundary breaks), 5 configurable parameters |
| **Beach biomes** | Coastline detection (land pixels adjacent to ocean near sea level), mapped to `BEACH` / `SNOWY_BEACH` / `STONY_SHORE` |
| **Aquifers** | Re-enabled `aquifers_enabled` and restored the four vanilla noise-router channels (barrier / fluid level / lava) — cave water bodies and lava layers are back |
| **Ore veins** | Re-enabled `ore_veins_enabled` and restored the three vein channels — large iron/copper vein deposits generate |
| **Structure support** | Biome tags added for the 3 custom biomes (forest_sparse / taiga_sparse / snowy_taiga_sparse); villages, mineshafts, strongholds etc. now generate on their chunks |
| **Localization** | New Simplified Chinese language file; the "Terrain Diffusion Next" world type and settings screens display in Chinese |

## Which version should I use?

Three builds are available on the [Releases](https://github.com/f1owkang/terrain-diffusion-mc/releases) page:

**The CPU build is slow unless you are on MacOS.**

| Build                     | Supports                    | Setup required                          |
|---------------------------| --------------------------- | --------------------------------------- |
| **Windows** (recommended) | Windows with any modern GPU | None                                    |
| **CUDA**                  | NVIDIA GPUs                 | [CUDA + cuDNN install](CUDA_INSTALL.md) |
| **CPU**                   | Everything else             | None                                    |

> **Mac users:** the CPU build automatically uses CoreML for hardware acceleration on Apple Silicon. No extra setup is needed.

Use the `-cuda` build only if you are on Linux, or have an NVIDIA GPU and prefer CUDA (may improve performance).

## Supported Minecraft versions

This mod targets Minecraft **26.x**:
**26.1**, **26.2**, and **26.3**. A single jar declares compatibility with the whole `>=26.1`
range, and the build can produce a jar against any specific target with
`./gradlew build -PmcTarget=261|262|263` (or `./gradlew buildAllMc` for all three).
Minecraft 26.x requires **Java 25**.

> For Minecraft 1.20.1 / 1.21.1 / 1.21.11 (the last obfuscated releases) use the upstream 2.x builds.

## Release status

> **v3.0.0 (current) is an early development release**: the Minecraft 26.x port with rivers, beaches, structures and other features still under active iteration — bugs and rough edges are expected. For a stable experience, use the upstream 2.x builds (Minecraft 1.20.1 / 1.21.1 / 1.21.11, Yarn mappings, Java 21).

## Requirements

- Minecraft with [Fabric](https://fabricmc.net/) and the [Fabric API Mod](https://modrinth.com/mod/fabric-api) installed
- Windows with a GPU OR Linux with an NVIDIA GPU is strongly recommended. CPU inference works but is very slow.
- VRAM (GPU RAM) needed: 1.5GB
- RAM needed: 2.5GB (May need to increase Minecraft's RAM allocation)

## Usage

**If using CUDA build:** First see [CUDA_INSTALL.md](CUDA_INSTALL.md).

1. Download the mod jar from [Releases](https://github.com/f1owkang/terrain-diffusion-mc/releases) for your Minecraft version and place it in your Minecraft `mods/` folder. Make sure the Minecraft version matches.
2. Launch Minecraft, at least once online to download the models (~2.5GB).
3. Create a world, and select the **Terrain Diffusion Next** world type. Click **Customize** to set the `World Scale` (see [Per-world settings](#per-world-settings) below).
4. The mod will search for a land spawn point near the world origin automatically. If the area around (0, 0) is entirely ocean, it may take a moment to find land. Use `/td-explore` (see below) to scout the world further.

## Exploring the World

The mod includes a built-in terrain explorer web UI. Run the `/td-explore` command in-game; it will print a clickable link (e.g. `http://localhost:19801`) that opens an interactive map in your browser. Click the map on the left to open a "detailed view". Click the detailed view to get coordinates in the bottom left. You can also filter for certain climates.

Use the explorer to scout continents, mountains, rivers, islands, and other interesting terrain before venturing out in Minecraft.

## Configuration

Edit `config/terrain-diffusion-mc.properties` (created automatically on first launch, with bilingual Chinese/English comments):

```
# Inference device: "cpu", "gpu", or "auto" (try GPU first then fall back to CPU).
# 推理设备："cpu"、"gpu" 或 "auto"（优先尝试 GPU，失败后回退 CPU）。
inference.device=gpu

# Offload inactive models from VRAM so only one model occupies the GPU at a time.
# 将非活动模型移出显存，使 GPU 同一时刻只驻留一个模型。
inference.offload_models=true

# Validate SHA-256 for pre-existing files in .minecraft/terrain-diffusion-models.
# 对 .minecraft/terrain-diffusion-models 中已有的文件做 SHA-256 校验。
validate_model=true

# Port for the local terrain explorer web UI (started with /td-explore command).
# 本地地形探索器网页界面的端口（通过 /td-explore 命令启动）。
explorer.port=19801

# Terrain generation region side length in blocks. Must be a power of 2.
# 地形生成区域的边长（方块数）。必须是 2 的幂。
tile_size=256

# Spawn search: coarse-pixel region sizes for land detection near (0,0).
# 出生点搜索：在 (0,0) 附近检测陆地的粗像素区域大小。
spawn_search.initial_size=16
spawn_search.max_size=128
```

### Per-world settings

For Terrain Diffusion Next worlds, click **Customize** in world creation and set:

- `World Scale` (integer `1..6`)

This value is saved with the world save and affects:

- how many real-world meters each block represents (`scale=1` => `30m/block`, `scale=2` => `15m/block`, etc.)
- world max height for newly created worlds (assumes tallest point is 10000 real-world meters)
- 2 is recommended for a good balance of scale and playability. Use 1 for smaller, more compressed worlds.
- Lower values put more stress on the GPU (Terrain Diffusion runs more often), while higher values put more stress on the CPU (larger world height). Most modern GPUs will be bottlenecked by the CPU around scale 2 or 3.

## Common Issues

**A dynamic link library (DLL) initialization routine failed**

This can happen for some older Java versions. Minecraft 26.x requires Java 25 or higher. The [latest Microsoft OpenJDK 25](https://learn.microsoft.com/en-us/java/openjdk/download) version is known to work.

**LoadLibrary failed with error 126** *(CUDA build only)*

This is typically due to an improper CUDA or cuDNN installation. See [CUDA_INSTALL.md](CUDA_INSTALL.md) for troubleshooting steps.

**java.lang.IllegalStateException: Failed to load terrain-diffusion models**

This typically indicates an "out of memory" error (the logs should show this as well).
Terrain Diffusion's models take up about 2.5GB of RAM, so make sure to allocate enough RAM to account for this.

**If your issue is still not resolved, please [raise it here](https://github.com/f1owkang/terrain-diffusion-mc/issues/new).**

## Building from Source

An internet connection is required during the build to fetch the pinned model manifest metadata from Hugging Face.

The `-windows` build requires `libs/onnxruntime-dml.jar`, which is provided as part of the repo. See [Building onnxruntime with DirectML](#building-onnxruntime-with-directml) to build from source. 

Build for Windows (DirectML):
```
./gradlew build -PuseDml=true
```

Build for CUDA:
```
./gradlew build -PuseCuda=true
```

Build for CPU (also handles macOS/CoreML automatically):
```
./gradlew build -PuseCpu=true
```

Build all:
```
./gradlew buildAll
```

### Building onnxruntime with DirectML

**Requirements**

- [Windows 10 SDK (10.0.17134.0)](https://developer.microsoft.com/en-us/windows/downloads/sdk-archive/index-legacy) — for Windows 10 version 1803 or newer
- Visual Studio 2017 toolchain — install *Desktop development with C++* from the VS Installer
- Visual Studio 2022 toolchain — same as above
- Python 3.10+: [https://python.org/](https://python.org/)
- CMake 3.28 or higher

Keep both VS toolchains up to date. Full details at the [ONNX Runtime build docs](https://onnxruntime.ai/docs/build/inferencing.html) and the [DirectML EP requirements](https://onnxruntime.ai/docs/execution-providers/DirectML-ExecutionProvider.html#build).

**Steps**

Run all commands from the **Developer Command Prompt for VS 2022**.

```
git clone --recursive https://github.com/Microsoft/onnxruntime.git
cd onnxruntime
.\build.bat --config RelWithDebInfo --build_shared_lib --parallel --compile_no_warning_as_error --skip_submodule_sync --use_dml --build_java --build
```

The built jar appears in `java/build/`. Rename it to `onnxruntime-dml.jar` and place it in `libs/` in this repository.

## Note For Mod Developers

The core of the AI terrain is a three-stage diffusion pipeline (coarse 20-step DPM-Solver++ → latent 2-step flow matching → decoder 1-step). The models output elevation + climate variables; the Minecraft integration is all hand-written rules.

- [BiomeClassifier.java](https://github.com/f1owkang/terrain-diffusion-mc/blob/mc26/src/main/java/com/github/xandergos/terraindiffusionmc/pipeline/BiomeClassifier.java) (~290 lines) — elevation + 4 climate variables → biome rules, including coastline detection (beaches) and carved-channel overrides (river/frozen river)
- [RiverCarver.java](https://github.com/f1owkang/terrain-diffusion-mc/blob/mc26/src/main/java/com/github/xandergos/terraindiffusionmc/pipeline/RiverCarver.java) (ported from upstream PR #207) — deterministic channel carving: noise zero-contours form centre-lines, land is cut below sea level so channels hold water; winding and O(1) random-access, seamless across tiles
- All river parameters live in the `rivers.*` config keys in `config/terrain-diffusion-mc.properties` (frequency / width / depth / max altitude) — no code changes needed

The terrain diversity far outpaces the biome diversity and there's a real opportunity to close that gap. I'm hoping someone goes crazy with it.

## Credits

- [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion) — the diffusion-model terrain generator (SIGGRAPH '26 / InfiniteDiffusion)
- [terrain-diffusion-mc](https://github.com/xandergos/terrain-diffusion-mc) — the upstream mod this project forks
- [infinite-tensor](https://github.com/xandergos/infinite-tensor) — the streaming tile tensor library
