package com.github.xandergos.terraindiffusionmc.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.xandergos.terraindiffusionmc.pipeline.InferenceProviderSelector.*;
import static org.junit.jupiter.api.Assertions.*;

class InferenceProviderSelectorTest {
    @Test void linuxCpuCpuUsesOnlyCpu() {
        assertEquals(List.of(Provider.CPU), resolve(BuildVariant.CPU, OperatingSystem.LINUX, "cpu").providers());
    }

    @Test void linuxCpuAutoUsesOnlyCpu() {
        Decision decision = resolve(BuildVariant.CPU, OperatingSystem.LINUX, "auto");
        assertEquals(List.of(Provider.CPU), decision.providers());
        assertFalse(decision.usesAccelerator());
    }

    @Test void linuxCpuGpuFailsActionably() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> resolve(BuildVariant.CPU, OperatingSystem.LINUX, "gpu"));
        assertTrue(error.getMessage().contains("inference.device=gpu"));
    }

    @Test void linuxCudaGpuRequiresCuda() {
        Decision decision = resolve(BuildVariant.CUDA, OperatingSystem.LINUX, "gpu");
        assertEquals(List.of(Provider.CUDA), decision.providers());
        assertTrue(decision.acceleratorRequired());
    }

    @Test void linuxCudaAutoFallsBackFromCudaToCpu() {
        assertEquals(List.of(Provider.CUDA, Provider.CPU),
                resolve(BuildVariant.CUDA, OperatingSystem.LINUX, "auto").providers());
    }

    @Test void linuxCudaCpuUsesOnlyCpu() {
        assertEquals(List.of(Provider.CPU), resolve(BuildVariant.CUDA, OperatingSystem.LINUX, "cpu").providers());
    }

    @Test void windowsDirectMlGpuRequiresDirectMl() {
        Decision decision = resolve(BuildVariant.DML, OperatingSystem.WINDOWS, "gpu");
        assertEquals(List.of(Provider.DIRECTML), decision.providers());
        assertTrue(decision.acceleratorRequired());
    }

    @Test void windowsDirectMlAutoFallsBackToCpu() {
        assertEquals(List.of(Provider.DIRECTML, Provider.CPU),
                resolve(BuildVariant.DML, OperatingSystem.WINDOWS, "auto").providers());
    }

    @Test void macosCpuAutoFallsBackFromCoreMlToCpu() {
        assertEquals(List.of(Provider.COREML, Provider.CPU),
                resolve(BuildVariant.CPU, OperatingSystem.MACOS, "auto").providers());
    }

    @Test void directMlIsRejectedOffWindows() {
        assertThrows(IllegalStateException.class, () -> resolve(BuildVariant.DML, OperatingSystem.LINUX, "auto"));
        assertThrows(IllegalStateException.class, () -> resolve(BuildVariant.DML, OperatingSystem.MACOS, "gpu"));
    }

    @Test void invalidDeviceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> resolve(BuildVariant.CPU, OperatingSystem.LINUX, "opencl"));
    }
}
