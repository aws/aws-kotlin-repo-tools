/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.gradle.dokka.lazyload.transformers

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [NavigationSplitter]'s pure path/JSON helpers — the logic that decides where each `nav.json`
 * lives and how a child's href/navFile is rebased relative to its parent's directory. Splitting real markup is
 * covered by [NavigationSplitterFixtureTest].
 */
class NavigationSplitterTest {

    @Test
    fun `contentDirOf drops the trailing file segment`() {
        assertEquals("s3", NavigationSplitter.contentDirOf("s3/index.html"))
        assertEquals(
            "s3/aws.sdk.kotlin.services.s3/-s3-client",
            NavigationSplitter.contentDirOf("s3/aws.sdk.kotlin.services.s3/-s3-client/index.html"),
        )
    }

    @Test
    fun `contentDirOf returns empty for a root-level page`() {
        assertEquals("", NavigationSplitter.contentDirOf("index.html"))
    }

    @Test
    fun `contentDirOf strips fragment and query suffixes`() {
        assertEquals("s3/pkg", NavigationSplitter.contentDirOf("s3/pkg/index.html#section"))
        assertEquals("s3/pkg", NavigationSplitter.contentDirOf("s3/pkg/index.html?v=1"))
    }

    @Test
    fun `relativize returns target unchanged from the root dir`() {
        assertEquals("s3/index.html", NavigationSplitter.relativize("", "s3/index.html"))
    }

    @Test
    fun `relativize rebases a sibling href against the node dir`() {
        // A package nav.json at s3/pkg holds a class row whose content is at s3/pkg/-s3-client/index.html.
        assertEquals(
            "-s3-client/index.html",
            NavigationSplitter.relativize("s3/aws.sdk.kotlin.services.s3", "s3/aws.sdk.kotlin.services.s3/-s3-client/index.html"),
        )
    }

    @Test
    fun `relativize climbs out of the node dir when the target is elsewhere`() {
        // A module nav.json at s3 holds a package row; the package's own nav.json is s3/pkg/nav.json, i.e. one
        // level down — but a target that diverges earlier needs ../ segments.
        assertEquals("../ec2/index.html", NavigationSplitter.relativize("s3", "ec2/index.html"))
        assertEquals("pkg/nav.json", NavigationSplitter.relativize("s3", "s3/pkg/nav.json"))
    }

    @Test
    fun `relativize leaves absolute and scheme hrefs unchanged`() {
        assertEquals("https://example.com/a/index.html", NavigationSplitter.relativize("s3/pkg", "https://example.com/a/index.html"))
        assertEquals("/abs/index.html", NavigationSplitter.relativize("s3/pkg", "/abs/index.html"))
    }

    @Test
    fun `relativize normalizes dot segments in the target`() {
        assertEquals("../ec2/index.html", NavigationSplitter.relativize("s3", "s3/../ec2/./index.html"))
    }

    @Test
    fun `normalizeHref keeps a climb above the root`() {
        assertEquals("../x/index.html", NavigationSplitter.normalizeHref("a/../../x/index.html"))
    }

    @Test
    fun `normalizeHref keeps an absolute href absolute`() {
        assertEquals("/x/index.html", NavigationSplitter.normalizeHref("/../x/index.html"))
    }

    @Test
    fun `normalizeHref leaves the fragment and query alone`() {
        assertEquals("a/index.html#/../..", NavigationSplitter.normalizeHref("a/./index.html#/../.."))
        assertEquals("b/index.html?q=../x", NavigationSplitter.normalizeHref("a/../b/index.html?q=../x"))
        assertEquals("a", NavigationSplitter.contentDirOf("a/index.html#/../.."))
    }

    @Test
    fun `contentDirOf ignores slashes inside the fragment`() {
        assertEquals("s3/pkg", NavigationSplitter.contentDirOf("s3/pkg/index.html#a/b"))
    }

    @Test
    fun `relativize keeps a trailing slash and the query`() {
        assertEquals("pkg/", NavigationSplitter.relativize("s3", "s3/pkg/"))
        assertEquals("pkg/index.html?v=1", NavigationSplitter.relativize("s3", "s3/pkg/index.html?v=1"))
    }

    @Test
    fun `relativize normalizes from the root dir too`() {
        assertEquals("b.html", NavigationSplitter.relativize("", "./a/../b.html"))
    }

    @Test
    fun `relativize yields dot when target equals the node dir`() {
        assertEquals(".", NavigationSplitter.relativize("s3/pkg", "s3/pkg"))
    }

    @Test
    fun `jsonString escapes quotes backslashes and control chars`() {
        assertEquals("\"a\\\"b\"", NavigationSplitter.jsonString("a\"b"))
        assertEquals("\"a\\\\b\"", NavigationSplitter.jsonString("a\\b"))
        assertEquals("\"a\\nb\"", NavigationSplitter.jsonString("a\nb"))
        assertEquals("\"a\\tb\"", NavigationSplitter.jsonString("a\tb"))
    }

    @Test
    fun `jsonString preserves html markup verbatim`() {
        val icon = "<span class=\"toc--icon class-kt\"></span>"
        assertEquals("\"<span class=\\\"toc--icon class-kt\\\"></span>\"", NavigationSplitter.jsonString(icon))
    }
}
