/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.gradle.hll

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Configures project to consume generated code from configured KSP projects.
 *
 * KSP does not treat a JVM-only build as "multiplatform", so the JVM-generated sources are moved from the
 * JVM output directory into a commonMain-registered directory (see [moveGenSrc] below) and consumed from
 * there. This also supports Kotlin/Native targets: every KSP task that reads the generated commonMain
 * directory is wired to depend on the move, so KMP projects with Native targets build without
 * implicit-dependency validation failures.
 */
fun Project.configureKspCodegen(kspProjects: List<String>) {
    // Depend on KSP projects we want to consume from
    kspProjects.forEach { kspProject ->
        dependencies.add("kspJvm", project(kspProject))
    }

    // Move the generated KSP source from jvm to common
    val moveGenSrc = tasks.register("moveGenSrc") {
        // Can't move source until it's generated
        dependsOn(tasks.named("kspKotlinJvm"))

        // Detecting these paths programmatically is complex; just hardcode them
        val srcDir = file("build/generated/ksp/jvm/jvmMain")
        val destDir = file("build/generated/ksp/common/commonMain")

        inputs.dir(srcDir)
        outputs.dirs(srcDir, destDir)

        doLast {
            if (destDir.exists()) {
                // Clean out the existing destination, otherwise move fails
                require(destDir.deleteRecursively()) { "Failed to delete $destDir before moving from $srcDir" }
            } else {
                // Create the destination directories, otherwise move fails
                require(destDir.mkdirs()) { "Failed to create path $destDir" }
            }

            Files.move(srcDir.toPath(), destDir.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    // Ensure all source jar tasks depend on the generated source move
    tasks.matching { it.name.endsWith("SourcesJar", ignoreCase = true) || it.name == "jvmProcessResources" }.configureEach {
        dependsOn(moveGenSrc)
    }

    // Ensure all Dokka tasks depend on the generated source move
    tasks.matching { it.name.startsWith("dokka", ignoreCase = true) }.configureEach {
        dependsOn(moveGenSrc)
    }

    tasks.withType(KotlinCompilationTask::class.java) {
        dependsOn(moveGenSrc)
    }

    // `moveGenSrc` writes into `build/generated/ksp/common/commonMain`, which is registered below as a
    // commonMain source directory. Every KSP task that processes commonMain (the Kotlin/Native per-target
    // tasks such as `kspKotlinLinuxX64`/`kspKotlinLinuxArm64`, the `kspCommonMainKotlinMetadata` /
    // `kspJvmAndNativeMainKotlinMetadata` tasks, and their test variants) therefore reads that directory as
    // an input. KSP tasks are not `KotlinCompilationTask`s, so the wiring above does not cover them, and
    // Gradle fails with an implicit-dependency validation error for KMP projects that have Native targets.
    // Wire every KSP task to `moveGenSrc` except `kspKotlinJvm`, which produces the input `moveGenSrc`
    // consumes (and which `moveGenSrc` already depends on) -- excluding it avoids a dependency cycle.
    tasks.matching { it.name.startsWith("ksp", ignoreCase = true) && it.name != "kspKotlinJvm" }.configureEach {
        dependsOn(moveGenSrc)
    }

    // Finally, wire up the generated source to the commonMain source set
    extensions
        .getByType(KotlinMultiplatformExtension::class.java)
        .sourceSets
        .getByName("commonMain") {
            kotlin.srcDir("build/generated/ksp/common/commonMain/kotlin")
        }
}
