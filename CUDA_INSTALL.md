# CUDA setup

This guide applies only to the `-cuda` artifact. It works on NVIDIA systems running Linux or Windows. The CPU artifact needs none of these libraries. Linux AMD GPU acceleration is not currently available through this mod; use the CPU artifact.

The CUDA artifact embeds ONNX Runtime's CUDA provider but relies on NVIDIA's CUDA and cuDNN native libraries installed on the system. Match those dependencies to the ONNX Runtime version bundled by the artifact; consult the [ONNX Runtime CUDA requirements](https://onnxruntime.ai/docs/execution-providers/CUDA-ExecutionProvider.html) rather than assuming a toolkit version from a driver banner.

## Linux: Ubuntu and Debian

1. Confirm Java and the NVIDIA driver are visible:

   ```fish
   java -version
   nvidia-smi
   ```

2. Install the CUDA toolkit and cuDNN through NVIDIA's repository instructions or your distribution's packages, using versions compatible with the bundled ONNX Runtime.

3. Start the launcher with the directory containing the CUDA and cuDNN shared libraries available. For a package-managed location, for example:

   ```fish
   set -x LD_LIBRARY_PATH /usr/lib/x86_64-linux-gnu $LD_LIBRARY_PATH
   prism-launcher
   ```

   Prefer the package-managed library directory actually used by your installation. Do not create global compatibility symlinks merely to satisfy the launcher.

## Linux: Arch and CachyOS

1. Confirm the driver and Java:

   ```fish
   java -version
   nvidia-smi
   ```

2. Install NVIDIA's CUDA toolkit and cuDNN with your normal package manager or an isolated NVIDIA toolkit installation. Keep its libraries in a dedicated directory; avoid copying or symlinking them into `/usr/lib`.

3. Launch Prism with that directory first in its library search path. Replace the example with your installed toolkit location:

   ```fish
   set -x LD_LIBRARY_PATH /opt/cuda/lib64 $LD_LIBRARY_PATH
   prism-launcher
   ```

For Prism Launcher, the same setting can be made per instance: **Edit → Settings → Environment Variables**, add `LD_LIBRARY_PATH` with the directory containing `libcudart.so` and `libcudnn.so`. This avoids changing global shell configuration.

## Windows

Install CUDA and cuDNN versions compatible with the bundled ONNX Runtime. Add the directories containing their DLLs to `PATH`, then restart the launcher. The DirectML (`-windows`) artifact does not require CUDA.

## Selecting a provider

Edit `config/terrain-diffusion-mc.properties`:

- `inference.device=cpu` always uses CPU.
- `inference.device=gpu` requires CUDA in the CUDA artifact and fails loudly if native dependencies are unavailable.
- `inference.device=auto` tries CUDA, then uses CPU cleanly if CUDA cannot load.

The log line beginning `Terrain diffusion inference:` reports the operating system, artifact variant, requested mode, and selected provider.

## Troubleshooting

If a Linux CUDA artifact reports that shared libraries cannot load, check that `LD_LIBRARY_PATH` contains the directory holding the required `.so` libraries and that the NVIDIA driver is visible through `nvidia-smi`. If a Windows CUDA artifact cannot load a DLL, check the equivalent directories in `PATH`.

When CUDA is unavailable, choose `inference.device=auto` for CPU fallback or install/use the CPU artifact. CPU inference is supported but substantially slower than a compatible accelerator.
