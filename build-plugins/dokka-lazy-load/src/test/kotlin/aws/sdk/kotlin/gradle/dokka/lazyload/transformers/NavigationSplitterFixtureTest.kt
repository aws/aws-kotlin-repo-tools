/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.gradle.dokka.lazyload.transformers

import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.dokka.utilities.LoggingLevel
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Runs [NavigationSplitter.splitNavigation] over hand-written `navigation.html` fixtures in Dokka's markup. */
class NavigationSplitterFixtureTest {
    private val outputDir: File = Files.createTempDirectory("nav-split").toFile()
    private val logger = DokkaConsoleLogger(LoggingLevel.WARN)

    @AfterTest
    fun cleanup() {
        outputDir.deleteRecursively()
    }

    private fun write(path: String, text: String) = outputDir.resolve(path).apply {
        parentFile.mkdirs()
        writeText(text)
    }

    private fun read(path: String): String = outputDir.resolve(path).readText()

    private fun moduleDirs(vararg names: String): List<File> = names.map(outputDir::resolve)

    private fun part(id: String, pageId: String, href: String, label: String, vararg children: String): String {
        val button = if (children.isEmpty()) "" else """<button class="toc--button" aria-expanded="false" aria-label="$label"></button>"""
        val skip = if (children.isEmpty()) "" else """<a class="toc--skip-link" href="">Skip to content</a>"""
        return """<div class="toc--part" id="$id" pageId="$pageId"><div class="toc--row">$button""" +
            """<a href="$href" class="toc--link"><span><span>$label</span></span></a>$skip</div>${children.joinToString("")}</div>"""
    }

    private fun leaf(id: String, pageId: String, href: String, label: String): String = """<div class="toc--part" id="$id" pageId="$pageId"><div class="toc--row"><a href="$href" class="toc--link">""" +
        """<span class="toc--link-grid"><span class="toc--icon function"></span><span><span>$label</span></span></span></a></div></div>"""

    @Test
    fun `aggregate layout emits one nav json per expandable node`() {
        write(
            "navigation.html",
            """<div class="sideMenu">""" +
                part(
                    "a-nav",
                    "a::",
                    "mod-a/index.html",
                    "mod-a",
                    part("a-nav-0", "a::pkg", "mod-a/pkg/index.html", "pkg", leaf("a-nav-0-0", "a::pkg/fn", "mod-a/pkg/fn.html", "fn()")),
                ) +
                part("b-nav", "b::", "mod-b/index.html", "mod-b", leaf("b-nav-0", "b::x", "mod-b/x/index.html", "x")) +
                "</div>",
        )
        write("mod-a/navigation.html", part("a-nav", "a::", "index.html", "mod-a"))

        NavigationSplitter.splitNavigation(outputDir, logger, moduleDirs("mod-a", "mod-b"))

        val shell = read("nav.json")
        assertTrue(""""navId":"a-nav","label":"mod-a","pageId":"a::"""" in shell, shell)
        assertTrue(""""href":"mod-a/index.html"""" in shell && """"navFile":"mod-a/nav.json"""" in shell, shell)

        val pkgRow = read("mod-a/nav.json")
        assertTrue(""""href":"pkg/index.html"""" in pkgRow && """"navFile":"pkg/nav.json"""" in pkgRow, pkgRow)

        val fnRow = read("mod-a/pkg/nav.json")
        assertTrue(""""pageId":"a::pkg/fn"""" in fnRow && """"href":"fn.html"""" in fnRow, fnRow)
        assertTrue(""""iconHtml":"<span class=\"toc--icon function\"></span>"""" in fnRow, fnRow)
        assertTrue(""""hasChildren":false,"navFile":null""" in fnRow, fnRow)
        assertTrue(""""label":"fn()"""" in fnRow, fnRow) // leaf: no button, falls back to link text

        assertFalse(outputDir.resolve("navigation.html").exists())
        assertFalse(outputDir.resolve("mod-a/navigation.html").exists())
    }

    @Test
    fun `single-module layout does not collide with the shell`() {
        // A single-module publication: the module page is the root index.html, so its dir is "" like the shell's.
        write(
            "navigation.html",
            part("m-nav", "m::", "index.html", "m", part("m-nav-0", "m::pkg", "pkg/index.html", "pkg", leaf("m-nav-0-0", "m::pkg/f", "pkg/f.html", "f()"))),
        )

        NavigationSplitter.splitNavigation(outputDir, logger)

        val shell = read("nav.json")
        assertTrue(""""navId":"nav-root"""" in shell && """"navFile":"nav-m-nav.json"""" in shell, shell)
        val module = read("nav-m-nav.json")
        assertTrue(""""navId":"m-nav"""" in module && """"navFile":"pkg/nav.json"""" in module, module)
        assertTrue(""""href":"f.html"""" in read("pkg/nav.json"))
        assertFalse(outputDir.resolve("navigation.html").exists())
    }

    @Test
    fun `a full html page at a delete candidate path is kept`() {
        // A synthetic page named navigation.html in the module dir (the same path as the module's navigation
        // fragment), mentioning both the nav class and the template command. Only the fragment check keeps it.
        write(
            "navigation.html",
            """<div class="sideMenu">""" + part("a-nav", "a::", "mod-a/index.html", "mod-a", leaf("a-nav-0", "a::p", "mod-a/p/index.html", "p")) + "</div>",
        )
        val memberPage = write(
            "mod-a/navigation.html",
            """<!DOCTYPE html><html><body><div class="root"><div class="toc--part">""" +
                "org.jetbrains.dokka.base.templating.AddToNavigationCommand</div></div></body></html>",
        )

        NavigationSplitter.splitNavigation(outputDir, logger, moduleDirs("mod-a"))

        assertTrue(memberPage.exists())
        assertFalse(outputDir.resolve("navigation.html").exists())
    }

    @Test
    fun `a module fragment saved inside its template wrapper is deleted`() {
        write(
            "navigation.html",
            """<div class="sideMenu">""" + part("a-nav", "a::", "mod-a/index.html", "mod-a", leaf("a-nav-0", "a::p", "mod-a/p.html", "p")) + "</div>",
        )
        val moduleFragment = write(
            "mod-a/navigation.html",
            """<html><head></head><body><dokka-template-command data="{&quot;@class&quot;:&quot;""" +
                """org.jetbrains.dokka.base.templating.AddToNavigationCommand&quot;}">""" +
                part("a-nav", "a::", "index.html", "mod-a") + "</dokka-template-command></body></html>",
        )

        NavigationSplitter.splitNavigation(outputDir, logger, moduleDirs("mod-a"))

        assertFalse(moduleFragment.exists())
    }

    @Test
    fun `files outside the output dir are never deleted`() {
        write("navigation.html", """<div class="sideMenu">""" + part("a-nav", "a::", "mod-a/index.html", "mod-a", leaf("a-nav-0", "a::p", "mod-a/p.html", "p")) + "</div>")
        val outside = outputDir.parentFile.resolve("${outputDir.name}-sibling/navigation.html").apply {
            parentFile.mkdirs()
            writeText(part("x-nav", "x::", "index.html", "x"))
        }
        try {
            NavigationSplitter.splitNavigation(outputDir, logger, listOf(outside.parentFile))
            assertTrue(outside.exists())
        } finally {
            outside.parentFile.deleteRecursively()
        }
    }

    @Test
    fun `a nav file outside the output dir fails before anything is written`() {
        write("navigation.html", """<div class="sideMenu">""" + part("a-nav", "a::", "../elsewhere/index.html", "a", leaf("a-nav-0", "a::p", "p.html", "p")) + "</div>")

        assertFailsWith<IllegalStateException> { NavigationSplitter.splitNavigation(outputDir, logger) }
        assertFalse(outputDir.resolve("nav.json").exists())
        assertTrue(outputDir.resolve("navigation.html").exists())
    }

    @Test
    fun `a node without a link gets a null href and keeps its label`() {
        write(
            "navigation.html",
            """<div class="sideMenu"><div class="toc--part" id="a-nav" pageId="a::"><div class="toc--row">""" +
                """<button class="toc--button" aria-expanded="false" aria-label="mod-a"></button><span><span>mod-a</span></span>""" +
                """<a class="toc--skip-link" href="">Skip to content</a></div>""" +
                leaf("a-nav-0", "a::p", "mod-a/p.html", "p") + "</div></div>",
        )

        NavigationSplitter.splitNavigation(outputDir, logger)

        val shell = read("nav.json")
        assertTrue(""""textHtml":"<span><span>mod-a</span></span>","href":null""" in shell, shell)
        assertTrue(""""navFile":"nav-a-nav.json"""" in shell, shell)
        assertTrue(""""href":"mod-a/p.html"""" in read("nav-a-nav.json"))
    }

    @Test
    fun `sanitized and case-only collisions get numbered file names`() {
        write(
            "navigation.html",
            part("x#1", "x::", "index.html", "x", leaf("x-0", "x::p", "p.html", "p")) +
                part("x_1", "y::", "other.html", "y", leaf("y-0", "y::p", "q.html", "q")) +
                part("X_1", "z::", "third.html", "z", leaf("z-0", "z::p", "r.html", "r")),
        )

        NavigationSplitter.splitNavigation(outputDir, logger)

        val shell = read("nav.json")
        assertTrue(""""navFile":"nav-x_1.json"""" in shell, shell)
        assertTrue(""""navFile":"nav-x_1-2.json"""" in shell, shell)
        assertTrue(""""navFile":"nav-X_1-3.json"""" in shell, shell)
        assertTrue(""""href":"q.html"""" in read("nav-x_1-2.json"))
    }

    @Test
    fun `expandable rows keep their icon`() {
        write(
            "navigation.html",
            """<div class="sideMenu"><div class="toc--part" id="a-nav" pageId="a::"><div class="toc--row">""" +
                """<button class="toc--button" aria-label="Foo"></button><a href="mod-a/index.html" class="toc--link">""" +
                """<span class="toc--link-grid"><span class="toc--icon class-kt"></span><span><strike>Foo</strike></span></span></a></div>""" +
                leaf("a-nav-0", "a::p", "mod-a/p.html", "p") + "</div></div>",
        )

        NavigationSplitter.splitNavigation(outputDir, logger)

        val shell = read("nav.json")
        assertTrue(""""iconHtml":"<span class=\"toc--icon class-kt\"></span>"""" in shell, shell)
        assertTrue(""""textHtml":"<span><strike>Foo</strike></span>"""" in shell, shell)
        assertTrue(""""hasChildren":true""" in shell, shell)
    }

    @Test
    fun `an empty root navigation html is still deleted`() {
        write("navigation.html", "")
        NavigationSplitter.splitNavigation(outputDir, logger)
        assertFalse(outputDir.resolve("navigation.html").exists())
        assertTrue(""""children":[]""" in read("nav.json"))
    }

    @Test
    fun `partial module renders are skipped`() {
        write("navigation.html", part("m-nav", "m::", "index.html", "m", leaf("m-0", "m::p", "p.html", "p")))

        NavigationSplitter(testContext(outputDir, delayTemplateSubstitution = true)).invoke()

        assertTrue(outputDir.resolve("navigation.html").exists())
        assertFalse(outputDir.resolve("nav.json").exists())
    }

    @Test
    fun `invoke deletes module fragments named by the configuration`() {
        write("navigation.html", """<div class="sideMenu">""" + part("a-nav", "a::", "grp/a/index.html", "a", leaf("a-0", "a::p", "grp/a/p.html", "p")) + "</div>")
        val fragment = write("grp/a/navigation.html", part("a-nav", "a::", "index.html", "a"))

        NavigationSplitter(testContext(outputDir, modules = mapOf("a" to "grp/a"))).invoke()

        assertFalse(fragment.exists())
        assertTrue(outputDir.resolve("grp/a/nav.json").exists())
    }

    @Test
    fun `navIds are sanitized in collision file names`() {
        write("navigation.html", part("m nav#1", "m::", "index.html", "m", leaf("m-0", "m::p", "p.html", "p")))

        NavigationSplitter.splitNavigation(outputDir, logger)

        assertTrue(""""navFile":"nav-m_nav_1.json"""" in read("nav.json"))
        assertTrue(outputDir.resolve("nav-m_nav_1.json").exists())
    }

    @Test
    fun `missing navigation html is a no-op`() {
        NavigationSplitter.splitNavigation(outputDir, logger)
        assertFalse(outputDir.resolve("nav.json").exists())
    }
}
