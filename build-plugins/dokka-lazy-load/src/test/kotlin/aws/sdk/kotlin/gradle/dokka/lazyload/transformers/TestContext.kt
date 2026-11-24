/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.gradle.dokka.lazyload.transformers

import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.DokkaConfigurationImpl
import org.jetbrains.dokka.DokkaModuleDescriptionImpl
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.dokka.utilities.DokkaLogger
import org.jetbrains.dokka.utilities.LoggingLevel
import java.io.File
import java.lang.reflect.Proxy

/** A [DokkaContext] that only answers `configuration` and `logger`, which is all these extensions read. */
internal fun testContext(
    outputDir: File,
    modules: Map<String, String> = emptyMap(),
    delayTemplateSubstitution: Boolean = false,
    logger: DokkaLogger = DokkaConsoleLogger(LoggingLevel.WARN),
): DokkaContext {
    val configuration: DokkaConfiguration = DokkaConfigurationImpl(
        outputDir = outputDir,
        modules = modules.map { (name, dir) -> DokkaModuleDescriptionImpl(name, File(dir), emptySet(), File(dir)) },
        delayTemplateSubstitution = delayTemplateSubstitution,
    )
    return Proxy.newProxyInstance(DokkaContext::class.java.classLoader, arrayOf(DokkaContext::class.java)) { _, method, _ ->
        when (method.name) {
            "getConfiguration" -> configuration
            "getLogger" -> logger
            else -> throw UnsupportedOperationException(method.name)
        }
    } as DokkaContext
}
