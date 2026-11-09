/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.gradle.dokka.lazyload.transformers

import org.jetbrains.dokka.base.templating.AddToNavigationCommand
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RootOnlyNavigationCommandHandlerTest {
    private val outputDir: File = Files.createTempDirectory("nav-handler").toFile()

    @AfterTest
    fun cleanup() {
        outputDir.deleteRecursively()
    }

    private fun fragment(id: String, href: String): Element = Jsoup.parseBodyFragment(
        """<div class="toc--part" id="$id"><div class="toc--row"><a class="toc--link" href="$href">x</a></div></div>""",
    ).body()

    @Test
    fun `modules are merged in key order with rebased hrefs and submenu ids`() {
        val sideMenu = RootOnlyNavigationCommandHandler.assemble(
            mapOf(
                "mod-b" to fragment("b", "index.html"),
                "mod-a" to fragment("a", "pkg/index.html"),
            ),
        )

        assertEquals("sideMenu", sideMenu.className())
        val parts = sideMenu.children()
        assertEquals(listOf("mod-a-nav-submenu", "mod-b-nav-submenu"), parts.map { it.id() })
        assertEquals(listOf("mod-a/pkg/index.html", "mod-b/index.html"), sideMenu.select("a").map { it.attr("href") })
    }

    @Test
    fun `finish writes the assembled tree only at the root`() {
        val handler = RootOnlyNavigationCommandHandler(testContext(outputDir, mapOf("s3" to "services/s3", "core" to "core")))
        val unused = File("unused")

        handler.handleCommandAsTag(AddToNavigationCommand("s3"), fragment("s3-nav", "index.html"), unused, unused)
        handler.handleCommandAsTag(AddToNavigationCommand("core"), fragment("core-nav", "pkg/index.html"), unused, unused)
        handler.handleCommandAsTag(AddToNavigationCommand("unknown"), fragment("x", "index.html"), unused, unused)
        handler.finish(outputDir)

        val root = Jsoup.parse(outputDir.resolve("navigation.html"))
        assertEquals(
            listOf("core/pkg/index.html", "services/s3/index.html"),
            root.select("a").map { it.attr("href") },
        )
        assertFalse(outputDir.resolve("services/s3/navigation.html").exists())
        assertFalse(outputDir.resolve("core/navigation.html").exists())
        assertEquals(listOf("navigation.html"), outputDir.walk().filter { it.isFile }.map { it.name }.toList())
    }

    @Test
    fun `finish with no fragments writes nothing`() {
        RootOnlyNavigationCommandHandler(testContext(outputDir)).finish(outputDir)
        assertTrue(outputDir.listFiles().isNullOrEmpty())
    }
}
