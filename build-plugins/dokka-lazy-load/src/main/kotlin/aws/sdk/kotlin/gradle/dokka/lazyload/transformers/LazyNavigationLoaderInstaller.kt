/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.gradle.dokka.lazyload.transformers

import org.jetbrains.dokka.pages.RendererSpecificResourcePage
import org.jetbrains.dokka.pages.RenderingStrategy
import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.pages.PageTransformer

/**
 * Ships a replacement `scripts/navigation-loader.js` that lazily loads navigation.
 *
 * The base [ScriptsInstaller][org.jetbrains.dokka.base.renderers.html.ScriptsInstaller] registers
 * `scripts/navigation-loader.js` as a [RendererSpecificResourcePage] copied from `dokka-base`. This transformer runs
 * after it, **removes** that page, and adds one with the same output path copied from this plugin's resource
 * [LOADER_RESOURCE_PATH]. Pages keep referencing `scripts/navigation-loader.js`, so they load the lazy loader.
 *
 * If a future Dokka renames or restructures the base page, the filter matches nothing and a warning is logged;
 * check the `LAZY_NAV_LOADER` sentinel in the generated `scripts/navigation-loader.js` after a Dokka upgrade.
 */
public class LazyNavigationLoaderInstaller(
    private val context: DokkaContext,
) : PageTransformer {

    override fun invoke(input: RootPageNode): RootPageNode {
        // In multi-module partial generation, resource pages are added by the aggregator; don't duplicate here.
        if (context.configuration.delayTemplateSubstitution) return input

        val lazyLoaderPage = RendererSpecificResourcePage(
            name = LOADER_OUTPUT_PATH,
            children = emptyList(),
            strategy = RenderingStrategy.Copy(LOADER_RESOURCE_PATH),
        )

        // Remove the base page so ours is the only writer: DefaultRenderer renders pages in parallel, so two pages
        // with the same output path would race.
        val withoutBaseLoader = input.children.filterNot {
            it is RendererSpecificResourcePage && it.name == LOADER_OUTPUT_PATH
        }
        if (withoutBaseLoader.size == input.children.size) {
            context.logger.warn(
                "[LazyNavigationLoaderInstaller] no base $LOADER_OUTPUT_PATH page found to replace; " +
                    "the stock loader may also be written",
            )
        }
        return input.modified(children = withoutBaseLoader + lazyLoaderPage)
    }

    public companion object {
        /** Output path within the generated docs; must match the base ScriptsInstaller entry it replaces. */
        public const val LOADER_OUTPUT_PATH: String = "scripts/navigation-loader.js"

        /**
         * Classpath resource inside THIS plugin's jar. Must be a plugin-unique path, NOT `/dokka/scripts/...`:
         * `dokka-base` ships the same `/dokka/scripts/navigation-loader.js`, and `RenderingStrategy.Copy` resolves the
         * path through dokka-base's classloader, so a shared path could read dokka-base's copy.
         */
        public const val LOADER_RESOURCE_PATH: String = "/dokka-lazy-load/scripts/navigation-loader.js"
    }
}
