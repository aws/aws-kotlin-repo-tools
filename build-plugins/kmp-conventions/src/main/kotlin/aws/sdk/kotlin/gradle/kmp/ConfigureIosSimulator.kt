/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.kmp

import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import org.jetbrains.kotlin.konan.target.HostManager

private const val DEFAULT_SIMULATOR_DEVICE_NAME = "iPhone 16"

/**
 * Disables standalone mode in simulator tests since it causes issues with TLS.
 * This means we need to manage the simulator state ourselves (booting, shutting down).
 * https://youtrack.jetbrains.com/issue/KT-38317
 */
public fun Project.configureIosSimulatorTasks() {
    if (!HostManager.hostIsMac) return

    val simulatorDeviceName = project.findProperty("iosSimulatorDevice") as? String ?: DEFAULT_SIMULATOR_DEVICE_NAME
    val xcrun = "/usr/bin/xcrun"

    val bootTask = rootProject.tasks.maybeCreate("bootIosSimulatorDevice", Exec::class.java).apply {
        isIgnoreExitValue = true
        commandLine(xcrun, "simctl", "boot", simulatorDeviceName)

        doLast {
            val result = executionResult.get()
            val code = result.exitValue
            if (code != 148 && code != 149) { // ignore "simulator already running" errors
                result.assertNormalExitValue()
            }
        }
    }

    val shutdownTask = rootProject.tasks.maybeCreate("shutdownIosSimulatorDevice", Exec::class.java).apply {
        isIgnoreExitValue = true
        commandLine(xcrun, "simctl", "shutdown", simulatorDeviceName)

        doLast {
            val result = executionResult.get()
            val code = result.exitValue
            if (code != 148 && code != 149) { // ignore "simulator already shutdown" errors
                result.assertNormalExitValue()
            }
        }
    }

    allprojects {
        val simulatorTasks = tasks.withType<KotlinNativeSimulatorTest>()
        simulatorTasks.configureEach {
            dependsOn(bootTask)
            standalone.set(false)
            device.set(simulatorDeviceName)
        }
        shutdownTask.mustRunAfter(simulatorTasks)
    }
}
