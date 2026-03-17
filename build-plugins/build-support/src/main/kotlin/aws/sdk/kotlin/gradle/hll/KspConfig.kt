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
 * Configures project to consume generated code from configured KSP projects
 *
 * FIXME: This is a dirty hack for JVM-only builds which KSP doesn't consider to be "multiplatform".
 *
 * FIXME: Needs to work with Kotlin Native builds as well, old (non-working) code for reference:
 * ```kt
 *      if (project.NATIVE_ENABLED) {
 *         // Configure KSP for multiplatform: https://kotlinlang.org/docs/ksp-multiplatform.html
 *         // https://github.com/google/ksp/issues/963#issuecomment-1894144639
 *         // https://github.com/google/ksp/issues/965
 *         dependencies.add("kspCommonMainMetadata", project(":hll:dynamodb-mapper:dynamodb-mapper-ops-codegen"))
 *
 *         kmpExt.sourceSets.getByName("commonMain") {
 *             kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
 *         }
 *      }
 * ```
 */
fun Project.configureKspCodegen(kspProjects: List<String>) {
    // Depend on KSP projects we want to consume from
    kspProjects.forEach { kspProject ->
        dependencies.add("kspJvm", project(kspProject))
    }

    // Move the generated KSP source from jvm to common
    tasks.register("moveGenSrc") {
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
        dependsOn("moveGenSrc")
    }
    tasks.withType(KotlinCompilationTask::class.java) {
        dependsOn("moveGenSrc")
    }

    // Finally, wire up the generated source to the commonMain source set
    extensions
        .getByType(KotlinMultiplatformExtension::class.java)
        .sourceSets
        .getByName("commonMain") {
            kotlin.srcDir("build/generated/ksp/common/commonMain/kotlin")
        }
}
