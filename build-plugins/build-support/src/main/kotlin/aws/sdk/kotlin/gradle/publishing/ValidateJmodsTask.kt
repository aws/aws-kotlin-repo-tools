/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.publishing

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import kotlin.io.path.Path
import kotlin.io.path.exists

abstract class ValidateJmodsTask : DefaultTask() {
    @TaskAction
    fun validate() {
        val jmodsPath = Path(System.getProperty("java.home"), "jmods")
        logger.info("Checking for presence of jmods in $jmodsPath")

        val filePath = jmodsPath.resolve("java.base.jmod")
        check(filePath.exists()) {
            buildString {
                appendLine("Cannot find jmods in $jmodsPath!")
                append("Hint: Are Java's jmods installed? ")
                append("(e.g., `sudo dnf install java-XX-amazon-corretto-jmods`)")
            }
        }
    }
}
