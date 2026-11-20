/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.kmp

import org.jetbrains.kotlin.konan.target.KonanTarget
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanRunNativeTestsTest {
    @Test
    fun hostTestsRunOnlyOnTheSameTarget() {
        assertTrue(canRunNativeTests(KonanTarget.MACOS_ARM64, NativeTestKind.HOST, KonanTarget.MACOS_ARM64))
        assertTrue(canRunNativeTests(KonanTarget.LINUX_X64, NativeTestKind.HOST, KonanTarget.LINUX_X64))
        assertTrue(canRunNativeTests(KonanTarget.MINGW_X64, NativeTestKind.HOST, KonanTarget.MINGW_X64))

        assertFalse(canRunNativeTests(KonanTarget.MACOS_X64, NativeTestKind.HOST, KonanTarget.MACOS_ARM64))
        assertFalse(canRunNativeTests(KonanTarget.LINUX_ARM64, NativeTestKind.HOST, KonanTarget.LINUX_X64))
        assertFalse(canRunNativeTests(KonanTarget.LINUX_X64, NativeTestKind.HOST, KonanTarget.MACOS_ARM64))
    }

    @Test
    fun simulatorTestsRunOnlyOnMacosOfTheSameArchitecture() {
        assertTrue(canRunNativeTests(KonanTarget.IOS_SIMULATOR_ARM64, NativeTestKind.SIMULATOR, KonanTarget.MACOS_ARM64))
        assertTrue(canRunNativeTests(KonanTarget.IOS_X64, NativeTestKind.SIMULATOR, KonanTarget.MACOS_X64))

        assertFalse(canRunNativeTests(KonanTarget.IOS_X64, NativeTestKind.SIMULATOR, KonanTarget.MACOS_ARM64))
        assertFalse(canRunNativeTests(KonanTarget.IOS_SIMULATOR_ARM64, NativeTestKind.SIMULATOR, KonanTarget.MACOS_X64))
        assertFalse(canRunNativeTests(KonanTarget.IOS_X64, NativeTestKind.SIMULATOR, KonanTarget.LINUX_X64))
    }
}
