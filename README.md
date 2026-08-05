# Terrain Diffusion Next

[![Minecraft](https://img.shields.io/badge/Minecraft-26.x-brightgreen)](https://github.com/f1owkang/Terrain-Diffusion-Next/releases)
[![Java](https://img.shields.io/badge/Java-25-orange)]()
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE.txt)
[![Contributors](https://img.shields.io/github/contributors/f1owkang/Terrain-Diffusion-Next)](https://github.com/f1owkang/Terrain-Diffusion-Next/graphs/contributors)

**Terrain Diffusion Next** 是 [terrain-diffusion-mc](https://github.com/xandergos/terrain-diffusion-mc) 的独立 fork（面向 Minecraft 26.x），将 [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion)（SIGGRAPH '26）扩散模型地形生成器集成进 Minecraft，并在此基础上扩展了水系、海滩、含水层、矿脉与原版结构支持。

> 英文版： [README_en.md](README_en.md)

## 与原版 fork 相比新增了什么

| 功能 | 说明 |
|------|------|
| **河流系统** | 移植上游 PR #207 的 `RiverCarver`：确定性噪声零等高线雕刻河道（挖至海平面以下真实蓄水，O(1) 随机访问、无 tile 边界断裂），5 项参数可配置 |
| **海滩生物群系** | 海岸带检测（陆地像素邻接海洋且近海平面），映射 `BEACH` / `SNOWY_BEACH` / `STONY_SHORE` |
| **含水层** | 重新启用 `aquifers_enabled`，恢复 barrier / fluid_level / lava 四条原版 noise_router 通道，洞穴内出现水体与熔岩层 |
| **矿脉** | 重新启用 `ore_veins_enabled`，恢复 vein 三条通道，生成大型铁矿/铜矿脉 |
| **结构支持** | 为 3 个自定义生物群系（forest_sparse / taiga_sparse / snowy_taiga_sparse）补齐 biome tag，村庄、矿井、要塞等原版结构可在其区块生成 |
| **界面本地化** | 新增简体中文语言文件，世界创建界面的"地形扩散·Next"世界类型与设置界面中文显示 |

## 我应该用哪个版本？

[Releases](https://github.com/f1owkang/terrain-diffusion-mc/releases) 页面提供三种构建：

**除非你在 MacOS 上，否则 CPU 构建很慢。**

| 构建 | 支持环境 | 需要配置 |
|---------------------------| --------------------------- | --------------------------------------- |
| **Windows**（推荐） | 任意现代显卡的 Windows | 无 |
| **CUDA** | NVIDIA 显卡 | [安装 CUDA + cuDNN](CUDA_INSTALL.md) |
| **CPU** | 其他一切环境 | 无 |

> **Mac 用户：** CPU 构建会在 Apple Silicon 上自动使用 CoreML 进行硬件加速，无需额外配置。

只有当你使用 Linux，或拥有 NVIDIA 显卡且更偏好 CUDA（可能提升性能）时，才使用 `-cuda` 构建。

## 支持的 Minecraft 版本

本 Mod 面向 Minecraft **26.x**：**26.1**、**26.2** 和 **26.3**。单个 jar 声明兼容整个 `>=26.1` 范围，构建时可用
`./gradlew build -PmcTarget=261|262|263` 针对任意特定版本产出 jar（或用 `./gradlew buildAllMc` 一次构建三个版本）。
Minecraft 26.x 需要 **Java 25**。

> 对于 Minecraft 1.20.1 / 1.21.1 / 1.21.11（最后一批混淆版本），请使用上游的 2.x 构建。

## 版本状态

> **v3.0.0（当前）为早期开发版本**：Minecraft 26.x 移植版，河流、海滩、结构等新特性仍在持续迭代，可能存在 bug 与不完善之处，适合尝鲜与测试；需要稳定体验请使用上游 2.x 稳定版（Minecraft 1.20.1 / 1.21.1 / 1.21.11，Yarn 映射、Java 21）。

## 环境要求

- 已安装 [Fabric](https://fabricmc.net/) 和 [Fabric API Mod](https://modrinth.com/mod/fabric-api) 的 Minecraft
- 强烈推荐 Windows 带显卡，或 Linux 带 NVIDIA 显卡。CPU 推理可用但非常慢。
- 显存（GPU 内存）需求：1.5GB
- 内存需求：2.5GB（可能需要调高 Minecraft 的内存分配）

## 使用方法

**如果使用 CUDA 构建：** 请先阅读 [CUDA_INSTALL.md](CUDA_INSTALL.md)。

1. 从 [Releases](https://github.com/f1owkang/terrain-diffusion-mc/releases) 下载与你的 Minecraft 版本匹配的 Mod jar，放入 `mods/` 文件夹。确保 Minecraft 版本一致。
2. 启动 Minecraft，至少在线启动一次以自动下载模型（约 2.5GB）。
3. 创建世界，选择 **地形扩散·Next**（Terrain Diffusion Next）世界类型。点击 **Customize** 设置 `World Scale`（见下方[每世界设置](#每世界设置)）。
4. 本 Mod 会自动在世界原点附近寻找陆地出生点。如果 (0, 0) 附近全是海洋，可能需要一些时间寻找陆地。可使用 `/td-explore`（见下文）进一步侦察世界。

## 探索世界

Mod 内置了一个地形探索网页界面。在游戏内执行 `/td-explore` 命令，会打印一个可点击的链接（如 `http://localhost:19801`），在浏览器中打开交互式地图。点击左侧地图打开"详细视图"。点击详细视图可在左下角获得坐标。还可以按气候筛选。

用探索器提前侦察大陆、山脉、河流、岛屿和其他有趣地形，再动身出发。

## 配置

编辑 `config/terrain-diffusion-mc.properties`（首次启动时自动创建，含中英双备注）：

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

### 每世界设置

对于地形扩散·Next 世界，在世界创建界面点击 **Customize**，设置：

- `World Scale`（整数 `1..6`）

该值随存档一起保存，影响：

- 每个方块代表多少真实世界米（`scale=1` 即 `30m/方块`，`scale=2` 即 `15m/方块`，依此类推）
- 新创建世界的最大高度（假设最高点海拔 10000 真实米）
- 2 是平衡尺度与可玩性的推荐值。想要更小、更紧凑的世界用 1。
- 更小的值对 GPU 压力更大（地形扩散运行更频繁），更大的值对 CPU 压力更大（世界高度更高）。大多数现代 GPU 在 scale 2 或 3 时会成为 CPU 瓶颈。

## 常见问题

**动态链接库（DLL）初始化例程失败**

某些旧版 Java 会出现此问题。Minecraft 26.x 需要 Java 25 或更高版本。已知 [最新版 Microsoft OpenJDK 25](https://learn.microsoft.com/en-us/java/openjdk/download) 可用。

**LoadLibrary failed with error 126** *（仅 CUDA 构建）*

通常由 CUDA 或 cuDNN 安装不当导致。排查步骤见 [CUDA_INSTALL.md](CUDA_INSTALL.md)。

**java.lang.IllegalStateException: Failed to load terrain-diffusion models**

通常表示内存不足（日志中也会显示）。Terrain Diffusion 的模型约占 2.5GB 内存，请确保为 Minecraft 分配了足够内存。

**如果问题仍未解决，请[在此提交 issue](https://github.com/f1owkang/terrain-diffusion-mc/issues/new)。**

## 从源码构建

构建过程需要联网，以从 Hugging Face 拉取固定的模型清单元数据。

`-windows` 构建需要 `libs/onnxruntime-dml.jar`，该文件随仓库提供。如需从源码构建，参见[用 DirectML 构建 onnxruntime](#用-directml-构建-onnxruntime)。

为 Windows（DirectML）构建：
```
./gradlew build -PuseDml=true
```

为 CUDA 构建：
```
./gradlew build -PuseCuda=true
```

为 CPU 构建（自动适配 macOS/CoreML）：
```
./gradlew build -PuseCpu=true
```

构建全部：
```
./gradlew buildAll
```

### 用 DirectML 构建 onnxruntime

**环境要求**

- [Windows 10 SDK (10.0.17134.0)](https://developer.microsoft.com/en-us/windows/downloads/sdk-archive/index-legacy) — 用于 Windows 10 1803 或更新版本
- Visual Studio 2017 工具链 — 在 VS Installer 中安装 *Desktop development with C++*
- Visual Studio 2022 工具链 — 同上
- Python 3.10+：[https://python.org/](https://python.org/)
- CMake 3.28 或更高版本

两个 VS 工具链都要保持最新。完整细节见 [ONNX Runtime 构建文档](https://onnxruntime.ai/docs/build/inferencing.html) 和 [DirectML EP 要求](https://onnxruntime.ai/docs/execution-providers/DirectML-ExecutionProvider.html#build)。

**步骤**

所有命令都在 **VS 2022 的 Developer Command Prompt** 中运行。

```
git clone --recursive https://github.com/Microsoft/onnxruntime.git
cd onnxruntime
.\build.bat --config RelWithDebInfo --build_shared_lib --parallel --compile_no_warning_as_error --skip_submodule_sync --use_dml --build_java --build
```

构建出的 jar 位于 `java/build/`。将其重命名为 `onnxruntime-dml.jar` 并放入本仓库的 `libs/` 目录。

## 给 Mod 开发者的说明

AI 地形的核心是三阶段扩散管线（coarse 20 步 DPM-Solver++ → latent 2 步 flow matching → decoder 1 步），模型输出高程 + 气候变量；与 Minecraft 的集成全靠手写规则。

- [BiomeClassifier.java](https://github.com/f1owkang/terrain-diffusion-mc/blob/mc26/src/main/java/com/github/xandergos/terraindiffusionmc/pipeline/BiomeClassifier.java)（约 290 行）——高程 + 4 气候变量 → 生物群系规则，含海岸带检测（海滩）与河道覆盖（河流/冻结河流）
- [RiverCarver.java](https://github.com/f1owkang/terrain-diffusion-mc/blob/mc26/src/main/java/com/github/xandergos/terraindiffusionmc/pipeline/RiverCarver.java)（移植自上游 PR #207）——确定性河道雕刻：以噪声零等高线为中心线，把陆地挖到海平面以下形成蓄水河道，蜿蜒且 O(1) 随机访问，跨 tile 无缝
- 河流参数全部在 `config/terrain-diffusion-mc.properties` 的 `rivers.*` 配置（频率/宽度/深度/海拔上限），无需改代码

地形多样性远超生物群系多样性，弥合这一差距是实打实的机会。希望有人能把它做到极致。

## 贡献者

本项目是 [terrain-diffusion-mc](https://github.com/xandergos/terrain-diffusion-mc) 的 fork，Terrain Diffusion 生态由以下贡献者共同打造（按贡献数排序）：

<a href="https://github.com/xandergos"><img src="https://github.com/xandergos.png" width="50" height="50" alt="xandergos" title="xandergos" /></a>
<a href="https://github.com/AirRunner"><img src="https://github.com/AirRunner.png" width="50" height="50" alt="AirRunner" title="AirRunner" /></a>
<a href="https://github.com/ThatDamnWittyWhizHard"><img src="https://github.com/ThatDamnWittyWhizHard.png" width="50" height="50" alt="ThatDamnWittyWhizHard" title="ThatDamnWittyWhizHard" /></a>
<a href="https://github.com/ayushsucksaf"><img src="https://github.com/ayushsucksaf.png" width="50" height="50" alt="ayushsucksaf" title="ayushsucksaf" /></a>
<a href="https://github.com/BillGoldenWater"><img src="https://github.com/BillGoldenWater.png" width="50" height="50" alt="BillGoldenWater" title="BillGoldenWater" /></a>
<a href="https://github.com/tlhr"><img src="https://github.com/tlhr.png" width="50" height="50" alt="tlhr" title="tlhr" /></a>
<a href="https://github.com/deforcy"><img src="https://github.com/deforcy.png" width="50" height="50" alt="deforcy" title="deforcy" /></a>
<a href="https://github.com/f1owkang"><img src="https://github.com/f1owkang.png" width="50" height="50" alt="f1owkang" title="f1owkang" /></a>

### 上游项目

- [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion) — 扩散模型地形生成项目（SIGGRAPH '26 / InfiniteDiffusion）
- [terrain-diffusion-mc](https://github.com/xandergos/terrain-diffusion-mc) — 本 fork 的上游 Mod 项目
- [infinite-tensor](https://github.com/xandergos/infinite-tensor) — 流式 tile 张量库
