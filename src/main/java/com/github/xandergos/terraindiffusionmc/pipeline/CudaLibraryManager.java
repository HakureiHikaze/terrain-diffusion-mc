package com.github.xandergos.terraindiffusionmc.pipeline;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * On-demand installer for the CUDA 12 runtime libraries required by the ONNX Runtime
 * CUDA Execution Provider.
 *
 * <p>The Maven Java GPU packages of ONNX Runtime (including 1.28.0) link against CUDA 12
 * shared libraries (libcublasLt.so.12 etc.) no matter that PyPI/NuGet builds switched to
 * CUDA 13. Headless Linux servers usually do not ship these libraries, which makes the CUDA
 * provider fail to load. This manager downloads the official NVIDIA wheels from PyPI (or a
 * configured mirror), extracts the .so files once into the model directory, and preloads
 * them into the JVM so the provider can resolve its dependencies.
 */
public final class CudaLibraryManager {
    private static final Logger LOG = LoggerFactory.getLogger(CudaLibraryManager.class);
    private static final String LIBS_DIR_NAME = "cuda-libs";
    private static final String DOWNLOADS_DIR_NAME = "downloads";
    /** Connect timeout per mirror source (seconds). */
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    /** Timeout to receive the HTTP response headers (seconds). */
    private static final int REQUEST_TIMEOUT_SECONDS = 30;
    private static final String[] SYSTEM_LIB_DIRS = {
            "/usr/lib/x86_64-linux-gnu", "/usr/lib64", "/usr/local/lib", "/usr/lib"
    };

    /** Simple (PEP 503) index path per well-known mirror host; unknown hosts default to "simple". */
    private static String simplePathFor(String host) {
        switch (host) {
            case "mirrors.aliyun.com":
                return "pypi/simple";
            case "mirrors.cloud.tencent.com":
                return "pypi/simple";
            case "repo.huaweicloud.com":
                return "repository/pypi/simple";
            case "mirrors.bfsu.edu.cn":
                return "pypi/web/simple";
            default:
                return "simple";
        }
    }

    /** (PyPI package, .so name) pairs in dependency-load order. */
    private static final String[][] REQUIRED_LIBRARIES = {
            {"nvidia-cuda-runtime-cu12", "libcudart.so.12"},
            {"nvidia-nvjitlink-cu12", "libnvjitlink.so.12"},
            {"nvidia-cublas-cu12", "libcublas.so.12"},
            {"nvidia-cublas-cu12", "libcublasLt.so.12"},
            {"nvidia-cudnn-cu12", "libcudnn.so.9"},
            {"nvidia-cufft-cu12", "libcufft.so.11"},
            {"nvidia-curand-cu12", "libcurand.so.10"},
            {"nvidia-cusparse-cu12", "libcusparse.so.12"},
            {"nvidia-cusolver-cu12", "libcusolver.so.11"},
            {"nvidia-cuda-nvrtc-cu12", "libnvrtc.so.12"},
    };

    private static final Object LOCK = new Object();
    private static final Set<String> LOADED = new HashSet<>();
    private static final AtomicBoolean ATTEMPTED = new AtomicBoolean(false);

    /**
     * Minimum CUDA runtime version required by the ONNX Runtime CUDA provider. The provider
     * links {@code cudaLibraryGetKernel@libcudart.so.12}, a symbol only present in CUDA 12.8+;
     * some mirrors cache older wheels (e.g. 12.6.x), so never accept those.
     */
    private static final String CUDART_MIN_VERSION = "12.9";

    private CudaLibraryManager() {
    }

    /**
     * Attempts to make the CUDA 12 runtime libraries available to the JVM so the ONNX
     * Runtime CUDA Execution Provider can load. No-op and returns false on non-Linux/amd64
     * platforms, when all libraries are already loaded, or after a previous failure.
     */
    public static boolean ensureLoaded() {
        if (!isLinuxAmd64()) {
            return false;
        }
        if (isLoaded()) {
            return true;
        }
        synchronized (LOCK) {
            if (isLoaded()) {
                return true;
            }
            // Attempt the setup at most once per JVM lifetime: a failed attempt would
            // otherwise be retried on every model run (e.g. by the spawn selector on
            // every tile), causing repeated downloads. A server restart retries.
            if (ATTEMPTED.get()) {
                return false;
            }
            ATTEMPTED.set(true);
            try {
                prepareLibraries();
                // JVM System.load uses RTLD_LOCAL, so symbols are invisible to the ONNX
                // Runtime provider when it dlopens (it can reuse the loaded handles but
                // not resolve their symbols -> "undefined symbol" errors). Symlinking the
                // libraries into a system dlopen search directory lets the provider load
                // them itself with global visibility. Falls back to System.load when the
                // process cannot write to system directories (non-root).
                if (installToSystemDirectories(libDir())) {
                    markAllLoaded();
                } else {
                    loadLibraries();
                }
                LOG.info("CUDA 12 runtime libraries loaded into the JVM");
                return true;
            } catch (Exception exception) {
                LOG.warn("Automatic CUDA 12 library setup failed, falling back to the other providers. "
                        + "Restart the server to retry.", exception);
                return false;
            }
        }
    }

    private static boolean isLoaded() {
        for (String[] pair : REQUIRED_LIBRARIES) {
            if (!LOADED.contains(pair[1])) {
                return false;
            }
        }
        return true;
    }

    private static void prepareLibraries() throws IOException, InterruptedException {
        Path libDir = libDir();
        Files.createDirectories(libDir);
        List<String> missingPackages = new ArrayList<>();
        for (String[] pair : REQUIRED_LIBRARIES) {
            Path soPath = libDir.resolve(pair[1]);
            boolean present = Files.exists(soPath);
            // The CUDA provider links cudaLibraryGetKernel@libcudart.so.12 (CUDA 12.8+). Older
            // mirror-sourced cudart files lack it and must be re-downloaded from a newer wheel.
            if (present && "libcudart.so.12".equals(pair[1])
                    && !containsSymbol(soPath, "cudaLibraryGetKernel")) {
                LOG.warn("Existing '{}' lacks cudaLibraryGetKernel (mirror-sourced old build), re-downloading...",
                        pair[1]);
                present = false;
            }
            if (!present && findSystemLibrary(pair[1]) == null) {
                if (!missingPackages.contains(pair[0])) {
                    missingPackages.add(pair[0]);
                }
            }
        }
        if (missingPackages.isEmpty()) {
            createVersionLinks(libDir);
            return;
        }
        LOG.info("CUDA 12 runtime libraries missing from this system. Downloading official NVIDIA wheels "
                + "from PyPI (first run only, about 1.5 GiB, cached in {})...", libDir);
        for (String packageName : missingPackages) {
            downloadAndExtract(packageName, libDir);
        }
        createVersionLinks(libDir);
    }

    private static void downloadAndExtract(String packageName, Path libDir) throws IOException, InterruptedException {
        Path downloadDir = libDir.resolve(DOWNLOADS_DIR_NAME);
        Files.createDirectories(downloadDir);
        // Prefer a previously downloaded wheel that satisfies the version requirement, so a
        // mirror without the required version (or a temporarily unreachable network) does not
        // degrade the installed libraries.
        Path cachedWheel = findCachedWheel(downloadDir, packageName);
        if (cachedWheel != null) {
            extractSharedLibraries(cachedWheel, libDir);
            LOG.info("CUDA 12 library '{}' restored from cached wheel '{}'", packageName, cachedWheel.getFileName());
            return;
        }
        String[] mirrorHosts = TerrainDiffusionConfig.cudaMirrors();
        Exception lastFailure = null;
        for (String mirrorHost : mirrorHosts) {
            try {
                WheelInfo wheel = resolveWheel(packageName, mirrorHost);
                Path wheelPath = downloadDir.resolve(wheel.filename);
                if (!Files.exists(wheelPath) || !sha256Matches(wheelPath, wheel.sha256)) {
                    Files.deleteIfExists(wheelPath);
                    download(wheel.url, wheelPath, wheel.size);
                    if (!sha256Matches(wheelPath, wheel.sha256)) {
                        Files.deleteIfExists(wheelPath);
                        throw new IOException("SHA-256 mismatch for " + wheel.filename);
                    }
                }
                extractSharedLibraries(wheelPath, libDir);
                LOG.info("CUDA 12 library '{}' ready in {}", packageName, libDir);
                return;
            } catch (InterruptedException interruptedException) {
                throw interruptedException;
            } catch (Exception exception) {
                lastFailure = exception;
                if (mirrorHosts.length > 1) {
                    LOG.warn("Failed to obtain '{}' from {}. Switching to next mirror source...",
                            packageName, mirrorHost);
                }
            }
        }
        throw new IOException("Failed to obtain CUDA library package " + packageName, lastFailure);
    }

    /**
     * Resolves the newest satisfying wheel from the mirror's PEP 503 simple index. The simple
     * index (unlike the JSON API) is fully synced on every mirror and serves files from the
     * mirror's own path, so both metadata and download work on all well-known mirrors.
     */
    private static WheelInfo resolveWheel(String packageName, String host) throws IOException, InterruptedException {
        boolean isCudart = "nvidia-cuda-runtime-cu12".equals(packageName);
        String simpleUrl = "https://" + host + "/" + simplePathFor(host) + "/" + packageName + "/";
        HttpClient httpClient = newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(simpleUrl))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " from " + simpleUrl);
        }
        // PEP 503: <a href=".../file.whl#sha256=...">filename</a>
        Pattern pattern = Pattern.compile(
                "href=\"([^\"]*manylinux[^\"]*x86_64[^\"]*\\.whl)[^\"]*\"");
        Matcher matcher = pattern.matcher(response.body());
        String bestHref = null;
        String bestVersion = null;
        while (matcher.find()) {
            String href = matcher.group(1);
            if (isCudart && !href.contains("-" + CUDART_MIN_VERSION + ".")) {
                continue;
            }
            String version = extractVersion(href);
            if (bestVersion == null || isVersionNewer(version, bestVersion)) {
                bestVersion = version;
                bestHref = href;
            }
        }
        if (bestHref != null) {
            return buildWheelInfo(bestHref, simpleUrl);
        }
        throw new IOException("No linux x86_64 wheel found for " + packageName + " on " + host
                + (isCudart ? " (requires cudart >= " + CUDART_MIN_VERSION + ")" : ""));
    }

    /** Extracts the version segment ("12.9.37") from a wheel href/filename. */
    private static String extractVersion(String href) {
        String filename = href.substring(href.lastIndexOf('/') + 1);
        int firstDash = filename.indexOf('-');
        int secondDash = firstDash >= 0 ? filename.indexOf('-', firstDash + 1) : -1;
        return secondDash > firstDash ? filename.substring(firstDash + 1, secondDash) : "";
    }

    /** Numeric segment-wise version comparison: is {@code candidate} newer than {@code current}? */
    private static boolean isVersionNewer(String candidate, String current) {
        String[] candidateParts = candidate.split("\\.");
        String[] currentParts = current.split("\\.");
        int parts = Math.max(candidateParts.length, currentParts.length);
        for (int i = 0; i < parts; i++) {
            int candidatePart;
            int currentPart;
            try {
                candidatePart = i < candidateParts.length ? Integer.parseInt(candidateParts[i]) : 0;
                currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
            } catch (NumberFormatException exception) {
                return false;
            }
            if (candidatePart != currentPart) {
                return candidatePart > currentPart;
            }
        }
        return false;
    }

    /** Parses a simple-index href into a fully resolved download URL plus optional sha256. */
    private static WheelInfo buildWheelInfo(String href, String simpleUrl) throws IOException {
        String sha256 = null;
        int hashIndex = href.indexOf("#sha256=");
        if (hashIndex >= 0) {
            sha256 = href.substring(hashIndex + "#sha256=".length());
            href = href.substring(0, hashIndex);
        }
        String filename = href.substring(href.lastIndexOf('/') + 1);
        try {
            URI base = URI.create(simpleUrl);
            URI resolved = base.resolve(href);
            return new WheelInfo(filename, resolved.toString(), sha256, 0L);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid wheel URL from " + simpleUrl + ": " + href, exception);
        }
    }

    /** Returns a cached wheel satisfying the package's version requirement, or null. */
    private static Path findCachedWheel(Path downloadDir, String packageName) {
        if (!Files.isDirectory(downloadDir)) {
            return null;
        }
        String packagePrefix = packageName.replace('-', '_') + "-";
        boolean isCudart = "nvidia-cuda-runtime-cu12".equals(packageName);
        try (var entries = Files.list(downloadDir)) {
            for (Path candidate : (Iterable<Path>) entries::iterator) {
                String name = candidate.getFileName().toString();
                if (name.startsWith(packagePrefix) && name.endsWith(".whl")
                        && (!isCudart || name.contains("-" + CUDART_MIN_VERSION + "."))) {
                    return candidate;
                }
            }
        } catch (IOException ignored) {
            // Fall through to a fresh download.
        }
        return null;
    }

    /** Cheap ASCII substring scan; .so symbol names live as plain bytes in .dynstr. */
    private static boolean containsSymbol(Path libPath, String symbol) {
        try {
            byte[] bytes = Files.readAllBytes(libPath);
            byte[] needle = symbol.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            outer:
            for (int i = 0; i <= bytes.length - needle.length; i++) {
                for (int j = 0; j < needle.length; j++) {
                    if (bytes[i + j] != needle[j]) {
                        continue outer;
                    }
                }
                return true;
            }
        } catch (IOException exception) {
            LOG.warn("Failed to inspect '{}' for symbol '{}': {}", libPath, symbol, exception.getMessage());
        }
        return false;
    }

    private static void download(String url, Path destination, long expectedSizeBytes) throws IOException, InterruptedException {
        LOG.info("Downloading '{}' (CUDA 12 runtime library) from {}", destination.getFileName(), url);
        HttpClient httpClient = newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Failed to download from " + url + " (HTTP " + response.statusCode() + ")");
        }
        // The PEP 503 simple index carries no size, so take it from the Content-Length
        // response header: progress is reported every 10% for large wheels (100 MiB+).
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(0L);
        try (InputStream responseStream = response.body();
             OutputStream fileOutputStream = Files.newOutputStream(destination)) {
            // Same download policy as the model assets: slow sources (below
            // download.min_speed_kbps for 30s) are abandoned for the next mirror.
            ModelAssetManager.copyWithProgress(responseStream, fileOutputStream,
                    destination.getFileName().toString(), contentLength,
                    TerrainDiffusionConfig.minDownloadSpeedKbps());
        }
    }

    private static void extractSharedLibraries(Path wheelPath, Path libDir) throws IOException {
        try (ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(wheelPath))) {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                String name = entry.getName();
                int slashIndex = name.lastIndexOf('/');
                String baseName = slashIndex >= 0 ? name.substring(slashIndex + 1) : name;
                if (baseName.endsWith(".so") || baseName.contains(".so.")) {
                    Files.copy(zipStream, libDir.resolve(baseName), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * Creates major-version symlinks (libX.so.12 -> libX.so.12.9.2.10) so dlopen can find
     * the exact names the ONNX Runtime provider links against. Falls back to a copy when
     * symlinks are not permitted.
     */
    private static void createVersionLinks(Path libDir) throws IOException {
        try (var entries = Files.list(libDir)) {
            for (Path file : (Iterable<Path>) entries::iterator) {
                String name = file.getFileName().toString();
                int dotIndex = name.lastIndexOf(".so.");
                if (dotIndex < 0) {
                    continue;
                }
                // ".so." spans [dotIndex, dotIndex+3]; the prefix must exclude the trailing dot.
                String prefix = name.substring(0, dotIndex + 3);
                String tail = name.substring(dotIndex + 4);
                // Skip malformed leftover names (e.g. "libX.so." or "libX.so..12" created by
                // earlier buggy versions) instead of crashing on their empty version tail.
                if (tail.isEmpty() || tail.startsWith(".")) {
                    continue;
                }
                String majorVersion = tail.split("\\.")[0];
                linkIfAbsent(file, libDir.resolve(prefix + "." + majorVersion));
                // NVIDIA ships libnvJitLink.so.12 with mixed case; the ONNX Runtime
                // provider links against the all-lowercase libnvjitlink.so.12.
                String lowercaseName = name.toLowerCase(Locale.ROOT);
                if (!lowercaseName.equals(name)) {
                    linkIfAbsent(file, libDir.resolve(lowercaseName));
                }
            }
        }
    }

    private static void linkIfAbsent(Path target, Path link) throws IOException {
        if (Files.exists(link, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (IOException | UnsupportedOperationException exception) {
            Files.copy(target, link, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void loadLibraries() throws IOException {
        Path libDir = libDir();
        for (String[] pair : REQUIRED_LIBRARIES) {
            String soName = pair[1];
            if (LOADED.contains(soName)) {
                continue;
            }
            Path libPath = libDir.resolve(soName);
            if (!Files.exists(libPath)) {
                libPath = findSystemLibrary(soName);
            }
            if (libPath == null) {
                throw new IOException("Missing CUDA library: " + soName);
            }
            try {
                System.load(libPath.toAbsolutePath().toString());
                LOADED.add(soName);
            } catch (Throwable throwable) {
                throw new IOException("Failed to load CUDA library " + soName + ": " + throwable.getMessage(), throwable);
            }
        }
    }

    private static void markAllLoaded() {
        for (String[] pair : REQUIRED_LIBRARIES) {
            LOADED.add(pair[1]);
        }
    }

    /**
     * Symlinks every .so in the library directory into the first writable system dlopen
     * search directory so the ONNX Runtime provider can load them with global symbol
     * visibility. Returns false when no system directory is writable (non-root process).
     */
    private static boolean installToSystemDirectories(Path libDir) throws IOException {
        for (String directory : SYSTEM_LIB_DIRS) {
            Path systemDir = Path.of(directory);
            if (!Files.isWritable(systemDir)) {
                continue;
            }
            try (var entries = Files.list(libDir)) {
                for (Path file : (Iterable<Path>) entries::iterator) {
                    String name = file.getFileName().toString();
                    if (!name.contains(".so")) {
                        continue;
                    }
                    Path target = systemDir.resolve(name);
                    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    try {
                        Files.createSymbolicLink(target, file.toAbsolutePath());
                    } catch (IOException | UnsupportedOperationException exception) {
                        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            LOG.info("Installed CUDA 12 runtime libraries into {} for dlopen resolution", systemDir);
            return true;
        }
        LOG.warn("No writable system library directory found; falling back to System.load (CUDA symbols may "
                + "not be visible to the ONNX Runtime provider).");
        return false;
    }

    private static Path findSystemLibrary(String soName) {
        for (String directory : SYSTEM_LIB_DIRS) {
            Path candidate = Path.of(directory, soName);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isLinuxAmd64() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        return os.contains("linux") && ("amd64".equals(arch) || "x86_64".equals(arch));
    }

    private static Path libDir() {
        return ModelAssetManager.resolveAssetPath(LIBS_DIR_NAME);
    }

    private static boolean sha256Matches(Path file, String expected) throws IOException {
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        MessageDigest digest = createSha256Digest();
        try (InputStream fileStream = Files.newInputStream(file);
             DigestInputStream digestInputStream = new DigestInputStream(fileStream, digest)) {
            byte[] buffer = new byte[64 * 1024];
            while (digestInputStream.read(buffer) != -1) {
                // Streaming digest update happens inside DigestInputStream.
            }
        }
        return toHex(digest.digest()).equalsIgnoreCase(expected);
    }

    private static MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException noSuchAlgorithmException) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", noSuchAlgorithmException);
        }
    }

    private static String toHex(byte[] digestBytes) {
        StringBuilder hexBuilder = new StringBuilder(digestBytes.length * 2);
        for (byte digestByte : digestBytes) {
            hexBuilder.append(String.format("%02x", digestByte));
        }
        return hexBuilder.toString();
    }

    private static HttpClient newHttpClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
    }

    private static final class WheelInfo {
        final String filename;
        final String url;
        final String sha256;
        final long size;

        private WheelInfo(String filename, String url, String sha256, long size) {
            this.filename = filename;
            this.url = url;
            this.sha256 = sha256;
            this.size = size;
        }
    }
}
