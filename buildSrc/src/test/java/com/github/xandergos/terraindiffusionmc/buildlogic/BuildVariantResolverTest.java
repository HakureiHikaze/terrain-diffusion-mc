package com.github.xandergos.terraindiffusionmc.buildlogic;

import org.junit.jupiter.api.Test;

import static com.github.xandergos.terraindiffusionmc.buildlogic.BuildVariantResolver.*;
import static org.junit.jupiter.api.Assertions.*;

class BuildVariantResolverTest {
    @Test void noExplicitVariantOnLinuxResolvesCpu() { assertEquals(Variant.CPU, resolve(OperatingSystem.LINUX, false, false, false).variant()); }
    @Test void noExplicitVariantOnWindowsResolvesDirectMl() { assertEquals(Variant.DML, resolve(OperatingSystem.WINDOWS, false, false, false).variant()); }
    @Test void noExplicitVariantOnMacosResolvesCpu() { assertEquals(Variant.CPU, resolve(OperatingSystem.MACOS, false, false, false).variant()); }
    @Test void explicitCpuIsAccepted() { assertEquals(Variant.CPU, resolve(OperatingSystem.LINUX, false, false, true).variant()); }
    @Test void explicitCudaIsAccepted() { assertEquals(Variant.CUDA, resolve(OperatingSystem.LINUX, true, false, false).variant()); }
    @Test void explicitDirectMlOnWindowsIsAccepted() { assertEquals(Variant.DML, resolve(OperatingSystem.WINDOWS, false, true, false).variant()); }
    @Test void directMlIsRejectedOnLinux() { assertThrows(IllegalArgumentException.class, () -> resolve(OperatingSystem.LINUX, false, true, false)); }
    @Test void directMlIsRejectedOnMacos() { assertThrows(IllegalArgumentException.class, () -> resolve(OperatingSystem.MACOS, false, true, false)); }
    @Test void multipleVariantsAreRejected() { assertThrows(IllegalArgumentException.class, () -> resolve(OperatingSystem.WINDOWS, true, true, false)); }
}
